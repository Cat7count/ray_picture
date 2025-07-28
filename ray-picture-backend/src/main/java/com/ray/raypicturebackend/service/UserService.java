package com.ray.raypicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ray.raypicturebackend.model.dto.user.UserQueryRequest;
import com.ray.raypicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.raypicturebackend.model.vo.LoginUserVo;
import com.ray.raypicturebackend.model.vo.UserVo;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author Ray
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-07-16 11:33:21
*/
public interface UserService extends IService<User> {
    public long userRegister(String userAccount, String userPassword,String checkPassword);

    public LoginUserVo userLogin(String userAccount, String userPassword, HttpServletRequest request);

    public User getLoginUser(HttpServletRequest request);

    public boolean userLogout(HttpServletRequest request);

    public String getEncryptPassword(String userPassword);

    public LoginUserVo getLoginUserVo(User user);

    public UserVo getUserVo(User user);

    public List<UserVo> getUserVoList(List<User> userList);

    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    public boolean isAdmin(User user);
}
