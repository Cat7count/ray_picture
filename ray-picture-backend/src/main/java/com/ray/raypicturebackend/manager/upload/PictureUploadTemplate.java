package com.ray.raypicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.ray.raypicturebackend.config.CosClientConfig;
import com.ray.raypicturebackend.exception.BusinessException;
import com.ray.raypicturebackend.exception.ErrorCode;
import com.ray.raypicturebackend.exception.ThrowUtils;
import com.ray.raypicturebackend.manager.CosManager;
import com.ray.raypicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

// 运用设计方法中的模板方法模式 即若流程一摸一样只是细节不同的情况，定义一个抽象类提供通用的业务处理逻辑，并将不同的部分定义为抽象方法，由子类具体实现
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    protected CosManager cosManager;

    @Resource
    protected CosClientConfig cosClientConfig;


    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 校验图片
        validPicture(inputSource);
        // 拼接图片上传地址 // 上传文件名为：年月日 + uuid + 文件后缀
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginFilename(inputSource);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid, FileUtil.getSuffix(originalFilename));
        String uploadFilePath = String.format("/%s/%s", uploadPathPrefix,uploadFilename);
        // 封装返回结果
        File file = null;

        try {
            // 创建临时文件
            file = File.createTempFile(uploadFilePath,null);
            // 处理文件来源
            processFile(inputSource,file);
            // 上传至对象存储
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadFilePath, file);
            System.out.println("hello");
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults(); // 返回webp图的封装结果
            List<CIObject> objectList = processResults.getObjectList();
            if(CollUtil.isNotEmpty(objectList)){
                CIObject compressCiObject = objectList.get(0); // 只有一个处理所以取0就行
                // 缩率图默认等于压缩图
                CIObject thumbnailCiObject = compressCiObject;
                if(objectList.size() > 1){  // 有缩率图才得到结果
                    thumbnailCiObject = objectList.get(1);
                }
                return buildResult(originalFilename,compressCiObject,thumbnailCiObject,uploadFilePath); // 返回webp图封装结果
            }
            // 返回原图封装结果
            return buildResult(originalFilename,file,uploadFilePath,imageInfo);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"上传失败");
        } finally {
            deleteTemplate(file);
        }
    }

    protected abstract void validPicture(Object inputSource);

    protected abstract String getOriginFilename(Object inputSource);

    protected abstract void processFile(Object inputSource, File file) throws Exception;

    private UploadPictureResult buildResult(String originalFilename,File file,String uploadFilePath,ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        double picScale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicWidth(width);
        uploadPictureResult.setPicHeight(height);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setUrl(cosClientConfig.getHost() + uploadFilePath);
        // 设置图片主色调
        String imageAve = cosManager.getImageAve(uploadFilePath);
        ThrowUtils.throwIf(StrUtil.isEmpty(imageAve),ErrorCode.OPERATION_ERROR,"获取图片主色调失败");
        uploadPictureResult.setPicColor(imageAve);
        return uploadPictureResult;
    }

    private UploadPictureResult buildResult(String originalFilename,CIObject compressCiObject,CIObject thumbnailCiObject,String uploadFilePath) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int width = compressCiObject.getWidth();
        int height = compressCiObject.getHeight();
        double picScale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicWidth(width);
        uploadPictureResult.setPicHeight(height);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressCiObject.getFormat());
        uploadPictureResult.setPicSize(compressCiObject.getSize().longValue());
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressCiObject.getKey());
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        uploadPictureResult.setOriginalUrl(cosClientConfig.getHost() + uploadFilePath);
        // 设置图片主色调
        String imageAve = cosManager.getImageAve(uploadFilePath);
        ThrowUtils.throwIf(StrUtil.isEmpty(imageAve),ErrorCode.OPERATION_ERROR,"获取图片主色调失败");
        uploadPictureResult.setPicColor(imageAve);
        return uploadPictureResult;
    }

    // 删除临时文件
    public void deleteTemplate(File file){
        if(file == null){
            return;
        }
        boolean deleteResult = file.delete();
        if(!deleteResult){
            log.error("临时文件删除失败 " + file.getAbsolutePath());
        }
    }
}
