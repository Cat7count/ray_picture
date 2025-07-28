package com.ray.raypicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 86802958914590508L;

    private String userAccount;
    private String userPassword;
    private String checkPassword;
}
