package com.ray.raypicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

// 管理员图片更新
@Data
public class PictureUpdateRequest implements Serializable {

    private static final long serialVersionUID = -6770147289672459608L;
    /**
     * id  
     */  
    private Long id;  
  
    /**  
     * 图片名称  
     */  
    private String name;  
  
    /**  
     * 简介  
     */  
    private String introduction;  
  
    /**  
     * 分类  
     */  
    private String category;  
  
    /**  
     * 标签  
     */  
    private List<String> tags;

}
