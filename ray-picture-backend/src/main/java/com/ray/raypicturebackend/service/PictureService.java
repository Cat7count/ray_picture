package com.ray.raypicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.raypicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ray.raypicturebackend.model.dto.picture.*;
import com.ray.raypicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.raypicturebackend.model.entity.Space;
import com.ray.raypicturebackend.model.entity.User;
import com.ray.raypicturebackend.model.vo.PictureVo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

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

    void checkPictureAuth(User loginUser, Picture picture);

    void deletePicture(long pictureId, User loginUser);

    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    List<PictureVo> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);
}
