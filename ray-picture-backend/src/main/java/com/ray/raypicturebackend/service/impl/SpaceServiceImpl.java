package com.ray.raypicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.raypicturebackend.exception.BusinessException;
import com.ray.raypicturebackend.exception.ErrorCode;
import com.ray.raypicturebackend.exception.ThrowUtils;
import com.ray.raypicturebackend.model.dto.space.SpaceAddRequest;
import com.ray.raypicturebackend.model.dto.space.SpaceQueryRequest;
import com.ray.raypicturebackend.model.entity.Space;
import com.ray.raypicturebackend.model.entity.User;
import com.ray.raypicturebackend.model.enums.SpaceLevelEnum;
import com.ray.raypicturebackend.model.vo.SpaceVo;
import com.ray.raypicturebackend.model.vo.UserVo;
import com.ray.raypicturebackend.service.SpaceService;
import com.ray.raypicturebackend.mapper.SpaceMapper;
import com.ray.raypicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
* @author Ray
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-07-28 14:08:54
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private UserService userService;

    private Map<Long,Object> lockMap = new ConcurrentHashMap<>();

    @Override
    public void validSpace(Space space, Boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 创建空间的校验和更改照片的校验并不相同
        if (add){
            if(StrUtil.isBlank(spaceName)){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间名称不能为空");
            }
            if(spaceLevel == null){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间级别不能为空");
            }
        }
        // 修改数据时
        if(spaceLevel!=null&&spaceLevelEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间级别不存在");
        }
        if(StrUtil.isNotBlank(spaceName)&&spaceName.length()>30){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间名称过长");
        }
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        // 优先以管理员指定的数值为主
        if(spaceLevelEnum != null){
            long maxCount = spaceLevelEnum.getMaxCount();
            long maxSize = spaceLevelEnum.getMaxSize();
            if(space.getMaxCount()==null){
                space.setMaxCount(maxCount);
            }
            if(space.getMaxSize()==null){
                space.setMaxSize(maxSize);
            }
        }
    }

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())){
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null){
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 填充空间参数
        fillSpaceBySpaceLevel(space);
        // 空间校验
        validSpace(space,true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 权限校验
        if(SpaceLevelEnum.COMMON.getValue()!=spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限创建指定的空间");
        }
        // 因为一个用户可能点击多次该操作且每个用户只能创建一个空间因此需要进行加锁操作
        Object lock = lockMap.computeIfAbsent(userId, k -> new Object()); //为每一个userId分配一个唯一的锁对象
        synchronized (lock) {
            try{
                Long newSpaceId = transactionTemplate.execute(status -> {
                    boolean exists = this.lambdaQuery().eq(Space::getUserId, userId).exists();
                    ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR,"每个用户只能创建一个空间");
                    //写入数据库
                    boolean result = this.save(space);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                    return space.getId();
                });
                return Optional.ofNullable(newSpaceId).orElse(-1L);
            }finally {
                lockMap.remove(userId); //防止内存溢出
            }
        }
    }

    // 分页获取图片封装
    @Override
    public Page<SpaceVo> getSpaceVoPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVo> spaceVoPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVoPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVo> spaceVoList = spaceList.stream().map(SpaceVo::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet()); // ::的左边是类名右边是方法引用 意义为得到某个类的方法引用
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId)); //得到了user 和userid的映射关系
        // 2. 填充信息  通过映射这里只查了一次数据库，而不是每一次都去查
        spaceVoList.forEach(spaceVo -> {
            Long userId = spaceVo.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVo.setUser(userService.getUserVo(user));
        });
        spaceVoPage.setRecords(spaceVoList);
        return spaceVoPage;
    }

    // 将查询请求转化为QueryWrapper对象
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel),"spaceLevel",spaceLevel);
        queryWrapper.like(StrUtil.isNotBlank(spaceName),"spaceName",spaceName);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    // 封装单个空间，主要是为原有空间关联创建用户的信息
    @Override
    public SpaceVo getSpaceVo(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVo spaceVo = SpaceVo.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVo userVo = userService.getUserVo(user);
            spaceVo.setUser(userVo);
        }
        return spaceVo;
    }
}




