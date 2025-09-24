package com.ray.raypicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.raypicturebackend.api.aliyunai.AliYunAiApi;
import com.ray.raypicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.ray.raypicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ray.raypicturebackend.exception.BusinessException;
import com.ray.raypicturebackend.exception.ErrorCode;
import com.ray.raypicturebackend.exception.ThrowUtils;
import com.ray.raypicturebackend.manager.CosManager;
import com.ray.raypicturebackend.manager.upload.FilePictureUpload;
import com.ray.raypicturebackend.manager.upload.PictureUploadTemplate;
import com.ray.raypicturebackend.manager.upload.UrlPictureUpload;
import com.ray.raypicturebackend.model.dto.file.UploadPictureResult;
import com.ray.raypicturebackend.model.dto.picture.*;
import com.ray.raypicturebackend.model.entity.Picture;
import com.ray.raypicturebackend.model.entity.Space;
import com.ray.raypicturebackend.model.entity.User;
import com.ray.raypicturebackend.model.enums.PictureReviewStatusEnum;
import com.ray.raypicturebackend.model.vo.PictureVo;
import com.ray.raypicturebackend.model.vo.UserVo;
import com.ray.raypicturebackend.service.PictureService;
import com.ray.raypicturebackend.mapper.PictureMapper;
import com.ray.raypicturebackend.service.SpaceService;
import com.ray.raypicturebackend.service.UserService;
import com.ray.raypicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author Ray
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-07-18 14:30:14
*/
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    UserService userService;

    @Resource
    FilePictureUpload filePictureUpload;

    @Resource
    UrlPictureUpload urlPictureUpload;
    @Resource
    private CosManager cosManager;
    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private AliYunAiApi aliYunAiApi;

    @Override
    public PictureVo uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        Long pictureId = null;
        Long spaceId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
            // 若空间存在，那么现阶段只有空间创建人才能上传
            spaceId = pictureUploadRequest.getSpaceId();
            if (spaceId !=null){
                Space space = spaceService.getById(spaceId);
                ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
                if(!loginUser.getId().equals(space.getUserId())){
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");
                }
                // 校验空间额度
                if (space.getTotalCount() >= space.getMaxCount()){
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间条数不足");
                }
                if(space.getTotalSize() >= space.getMaxSize()){
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间大小不足");
                }
            }
        }
        // 如果是更新图片，需要校验图片是否存在
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 校验上传的spaceId是否与之前一样
            if(spaceId == null){
                if(oldPicture.getSpaceId() != null){ //如为空复用之前的
                    spaceId = oldPicture.getSpaceId();
                }
            }else {
                if(ObjUtil.notEqual(oldPicture.getSpaceId(), spaceId)){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间id不一致");
                }
            }
            // 清理掉之前的图
            clearPictureFile(oldPicture);
        }
        // 上传图片，得到信息
        // 按照用户 id 划分目录 / 安装空间划分目录
        String uploadPathPrefix = null;
        if(spaceId==null){
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 根据inputSource类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        picture.setOriginalUrl(uploadPictureResult.getOriginalUrl());
        picture.setSpaceId(spaceId);
        picture.setPicColor(uploadPictureResult.getPicColor());
        // 补充审核参数
        fillReviewParams(picture, loginUser);
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 开启事务 额度更新和图片上传必须同时成功
        Long finalSpaceId = spaceId;
        Long finalPicId = pictureId;
        Picture finalOldPicture = oldPicture;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                if(finalPicId == null){
                    // 上传照片更新额度
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, finalSpaceId)
                            .setSql("totalSize = totalSize + " + picture.getPicSize())
                            .setSql("totalCount = totalCount + 1")
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                }else{
                    // 更新图片设置额度
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, finalSpaceId)
                            .setSql("totalSize = totalSize + " + (picture.getPicSize() - finalOldPicture.getPicSize()))
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                }
            }
            return picture;
        });

        return PictureVo.objToVo(picture);
    }

    // 将查询请求转化为QueryWrapper对象
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime),"editTime",startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime),"editTime",endEditTime);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId,"spaceId");
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus),"reviewStatus",reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId),"reviewerId",reviewerId);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage),"reviewMessage",reviewMessage);
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\""); //因为数据库中的存的是JSON字符串数组["java","python"] 因此需要把引号也带上
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    // 封装单个照片，主要是为原有图片关联创建用户的信息
    @Override
    public PictureVo getPictureVo(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVo pictureVo = PictureVo.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVo userVo = userService.getUserVo(user);
            pictureVo.setUser(userVo);
        }
        return pictureVo;
    }


     // 分页获取图片封装
     @Override
     public Page<PictureVo> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVo> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVo> pictureVOList = pictureList.stream().map(PictureVo::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet()); // ::的左边是类名右边是方法引用 意义为得到某个类的方法引用
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId)); //得到了user 和userid的映射关系
        // 2. 填充信息  通过映射这里只查了一次数据库，而不是每一次都去查
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVo(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    // 图片校验方法，用于更新和修改图片时进行判断
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);

        if(id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断照片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 判断是否重复审核
        if(oldPicture.getReviewStatus().equals(reviewStatus)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请勿重复审核");
        }
        //更新审核状态
        Picture updatePicture = new Picture(); // 新建一个对象，因为更新操作是通过已有的字段去覆盖
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    // 由于图片上传，用户编辑，管理员更新都需要设置审核状态。封装一个通用的审核状态参数填充函数
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30,ErrorCode.PARAMS_ERROR,"最多抓取30条");
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%25s&mmasync=1",searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败",e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first(); // 得到最外层的div
        if(ObjUtil.isNull(div)){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg"); // 找到其中的img对象且含有ming类名
        int uploadCount = 0; // 记录条数
        for(Element imgElement : imgElementList){
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}",fileUrl);
                continue;
            }
            //处理图片上传的地址，把参数都给清掉避免转移问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if(questionMarkIndex > -1){ // 含有参数
                fileUrl = fileUrl.substring(0, questionMarkIndex); // 右开区间
            }

            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
            if (StrUtil.isBlank(namePrefix)) {
                namePrefix = searchText;
            }
            if (StrUtil.isNotBlank(namePrefix)) {
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }
            try {
                PictureVo pictureVo = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("上传图片成功，id = {}",pictureVo.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("上传图片失败");
                continue;
            }
            if (uploadCount >=count){
                break;
            }
        }
        return uploadCount;
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        cosManager.deleteObject(getRelativePath(oldPicture.getUrl()));
        cosManager.deleteObject(getRelativePath(oldPicture.getOriginalUrl()));
        if (StrUtil.isNotBlank(oldPicture.getThumbnailUrl())){
            cosManager.deleteObject(getRelativePath(oldPicture.getThumbnailUrl()));
        }
    }

    // 得到url的相对路径函数
    @Override
    public String getRelativePath(String urlString){
        // 存在数据库的存储路径都已经被清理过，因此很干净不会有像参数这些东西
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"得到相对路径失败");
        }
        return url.getPath();
    }

    /**
     * 图片编辑或校验时候的操作权限校验
     * @param loginUser 登录用户
     * @param picture 照片
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 根据id删除图片
     * @param pictureId 图片id
     * @param loginUser 登录用户
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR,"额度更新失败");
            }
            return true;
        });
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    /**
     * 用户编辑图片
     * @param pictureEditRequest 用户编辑请求参数
     * @param loginUser 登录用户
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public List<PictureVo> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
            // 1. 校验参数
            ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
            // 2. 校验空间权限
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
            }
            // 3. 查询该空间下所有图片（必须有主色调）
            List<Picture> pictureList = this.lambdaQuery()
                    .eq(Picture::getSpaceId, spaceId)
                    .isNotNull(Picture::getPicColor)
                    .list();
            // 如果没有图片，直接返回空列表
            if (CollUtil.isEmpty(pictureList)) {
                return Collections.emptyList();
            }
            // 将目标颜色转为 Color 对象
            Color targetColor = Color.decode(picColor);
            // 4. 计算相似度并排序
            List<Picture> sortedPictures = pictureList.stream()
                    .sorted(Comparator.comparingDouble(picture -> {
                        // 提取图片主色调
                        String hexColor = picture.getPicColor();
                        // 没有主色调的图片放到最后
                        if (StrUtil.isBlank(hexColor)) {
                            return Double.MAX_VALUE;
                        }
                        Color pictureColor = Color.decode(hexColor);
                        // 越大越相似
                        return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                    }))
                    // 取前 12 个
                    .limit(12)
                    .collect(Collectors.toList());

            // 转换为 PictureVO
            return sortedPictures.stream()
                    .map(PictureVo::objToVo)
                    .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询指定图片，仅选择需要的字段 --> 减小数据库的压力
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();

        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);

        // 5. 批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }
    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    /**
     * 阿里云百炼AI阔图
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 权限校验
        checkPictureAuth(loginUser, picture);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }

}




