package com.ray.raypicturebackend.common;

import lombok.Data;

import java.io.Serializable;

//删除接受参数通用类
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}

