package com.ray.raypicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.ray.raypicturebackend.exception.BusinessException;
import com.ray.raypicturebackend.exception.ErrorCode;
import com.ray.raypicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Service
public class UrlPictureUpload extends PictureUploadTemplate{
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
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
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/jpg");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType), ErrorCode.PARAMS_ERROR,"目前只支持jpg,png,jpeg格式图片");
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

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        String fileName = FileUtil.getName(fileUrl);
        String lowerCase = fileName.toLowerCase();
        boolean hasImageSuffix = lowerCase.endsWith(".jpeg") ||
                lowerCase.endsWith(".jpg") ||
                lowerCase.endsWith(".png") ||
                lowerCase.endsWith(".webp");
        if(!hasImageSuffix){
           fileName  = FileUtil.mainName(fileUrl) + ".png";
        }
        return fileName;
    }

    @Override
    protected void processFile(Object inputSource, File file) {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, file);
    }
}
