package com.ray.raypicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadByBatchRequest implements Serializable {

    private static final long serialVersionUID = -8125499403535490885L;

    private String searchText;

    private Integer count = 10; // 默认抓取十条

    private String namePrefix; // 记录一下抓取图片的前缀，避免名字全是又url决定
}
