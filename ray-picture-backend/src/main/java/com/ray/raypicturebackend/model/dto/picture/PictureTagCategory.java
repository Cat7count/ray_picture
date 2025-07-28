package com.ray.raypicturebackend.model.dto.picture;

import lombok.Data;

import java.util.List;

// 图片标签和图片分类
@Data
public class PictureTagCategory {
    private List<String> tagList; // 标签列表

    private List<String> categoryList; // 分类列表
}
