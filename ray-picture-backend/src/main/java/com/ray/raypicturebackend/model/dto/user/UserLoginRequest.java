package com.ray.raypicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest  implements Serializable {

    private static final long serialVersionUID = 7041244899968877294L;
    private String userAccount;
    private String userPassword;
}
