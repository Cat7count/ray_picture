package com.ray.raypicturebackend.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

@Getter
public enum PictureReviewStatusEnum {

    REVIEWING("待审核",0),
    PASS("通过",1),
    REJECT("拒绝",2);

    private final String text;
    private final int value;

    PictureReviewStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    // 根据value得到枚举类
    public static PictureReviewStatusEnum getEnumByValue(Integer value){
        if(ObjectUtil.isEmpty(value)){
            return null;
        }
        for(PictureReviewStatusEnum e : PictureReviewStatusEnum.values()){
            if(e.getValue() == value){
                return e;
            }
        }
        return null;
    }
}
