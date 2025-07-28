package com.ray.raypicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.ray.raypicturebackend.exception.ErrorCode;
import com.ray.raypicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Service
public class FilePictureUpload extends PictureUploadTemplate{
    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
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

    @Override
    protected String getOriginFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception{
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }
}
