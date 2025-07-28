package com.ray.raypicturebackend.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

@Getter
public enum UserRoleEnum {

    USER("用户","user"),
    ADMIN("管理员","admin");

    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    //通过value找到对应的枚举类
    public static UserRoleEnum getEnumByValue(String value) {
        if(ObjectUtil.isEmpty(value)){
            return null;
        }
        for (UserRoleEnum e : UserRoleEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null; // 没找到
    }
}
