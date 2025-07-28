package com.ray.raypicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.ray.raypicturebackend.config.CosClientConfig;
import com.ray.raypicturebackend.exception.BusinessException;
import com.ray.raypicturebackend.exception.ErrorCode;
import com.ray.raypicturebackend.exception.ThrowUtils;
import com.ray.raypicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
@Deprecated //已经没用啦!!!
public class FileManager {
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    // 上传文件方法
    public UploadPictureResult uploadPicture(MultipartFile multipartFile,String uploadPathPrefix) {
        // 校验图片
        validPicture(multipartFile);
        // 拼接图片上传地址 // 上传文件名为：年月日 + uuid + 文件后缀
        String uuid = RandomUtil.randomString(16);
        String originalFilename = multipartFile.getOriginalFilename();
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid,FileUtil.getSuffix(originalFilename));
        String uploadFilePath = String.format("/%s/%s", uploadPathPrefix,uploadFilename);
        // 封装返回结果
        File file = null;

        try {
            file = File.createTempFile(uploadFilePath,null);
            multipartFile.transferTo(file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadFilePath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 封装返回结果
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
            return uploadPictureResult;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"上传失败");
        } finally {
            deleteTemplate(file);
        }
    }

    // 校验图片
    public void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR,"文件不能为空");
        // 1.校验文件大小
        long fileSize = multipartFile.getSize();// 以字节为单位
        final long ONE_MB = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > ONE_MB * 2, ErrorCode.PARAMS_ERROR,"文件大小不能超过2M");
        // 2.校验文件后缀
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许上传的文件后缀
        final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp");
        ThrowUtils.throwIf(!ALLOWED_EXTENSIONS.contains(suffix), ErrorCode.PARAMS_ERROR,"文件格式错误");
    }

    // 校验URL
    public void validPicture(String fileUrl){
        ThrowUtils.throwIf(fileUrl == null, ErrorCode.PARAMS_ERROR,"文件url不能为空");

        try {
            // 1. 验证Url的格式
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件url格式不正确");
        }
        // 2. 验证Url的协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://")||fileUrl.startsWith("https://")), ErrorCode.PARAMS_ERROR,"仅支持HTTP或HTTPS协议的文件地址");

        // 3. 发送HEAD请求验证文件是否存在 该请求只返回头信息
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD,fileUrl).execute();

            // 没有正常返回相应请求
            if(response.getStatus()!= HttpStatus.HTTP_OK){
                return;
            }

            // 4. 校验文件类型
            String contentType = response.header("Content-Type");
            if(StrUtil.isNotBlank(contentType)){
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType), ErrorCode.PARAMS_ERROR,"文件类型错误");
            }

            // 5. 校验文件大小
            String contentLengthStr = response.header("Content-Length");
            if(StrUtil.isNotBlank(contentLengthStr)){
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long TWO_MB = 2 * 1024 * 1024L;
                    ThrowUtils.throwIf(contentLength > TWO_MB, ErrorCode.PARAMS_ERROR,"文件大小不能超过2MB");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件大小格式错误");
                }
            }
        } finally {
            if(response != null) {
                response.close(); // 释放资源
            }
        }
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
