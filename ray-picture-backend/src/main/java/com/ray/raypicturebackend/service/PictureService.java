package com.ray.raypicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.raypicturebackend.model.dto.picture.PictureQueryRequest;
import com.ray.raypicturebackend.model.dto.picture.PictureReviewRequest;
import com.ray.raypicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.ray.raypicturebackend.model.dto.picture.PictureUploadRequest;
import com.ray.raypicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.raypicturebackend.model.entity.User;
import com.ray.raypicturebackend.model.vo.PictureVo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author Ray
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-07-18 14:30:14
*/
public interface PictureService extends IService<Picture> {
    PictureVo uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User user);

    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    PictureVo getPictureVo(Picture picture, HttpServletRequest request);

    Page<PictureVo> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    void validPicture(Picture picture);

    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    void fillReviewParams(Picture picture, User loginUser);

    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    void clearPictureFile(Picture oldPicture);

    String getRelativePath(String urlString);
}
