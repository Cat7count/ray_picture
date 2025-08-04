package com.ray.raypicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;


@Data
public class PictureUploadRequest implements Serializable {
    private static final long serialVersionUID = 2726628614657596591L;
    private Long id; //接受id因为重复上传图片就是在更新图片
    private String fileUrl;
    private String picName; // 批量上传的时候用来控制名字
    private Long spaceId;
}
