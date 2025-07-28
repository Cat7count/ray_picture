package com.ray.raypicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;


// 登录后返回的脱敏信息
@Data
public class LoginUserVo implements Serializable {

    private static final long serialVersionUID = 3951745409689803268L;
    /**
     * id
     */
    private Long id;

    /**
     * 账号
     */
    private String userAccount;


    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

}
