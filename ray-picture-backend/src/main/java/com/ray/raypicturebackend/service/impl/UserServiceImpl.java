package com.ray.raypicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.raypicturebackend.exception.ErrorCode;
import com.ray.raypicturebackend.exception.ThrowUtils;
import com.ray.raypicturebackend.model.dto.user.UserQueryRequest;
import com.ray.raypicturebackend.model.entity.User;
import com.ray.raypicturebackend.model.enums.UserRoleEnum;
import com.ray.raypicturebackend.model.vo.LoginUserVo;
import com.ray.raypicturebackend.model.vo.UserVo;
import com.ray.raypicturebackend.service.UserService;
import com.ray.raypicturebackend.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.stream.Collectors;

import static com.ray.raypicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
* @author Ray
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-07-16 11:33:21
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 参数校验
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount,userPassword,checkPassword), ErrorCode.PARAMS_ERROR,"参数为空");
        ThrowUtils.throwIf(userAccount.length() <4, ErrorCode.PARAMS_ERROR,"用户账号过短");
        ThrowUtils.throwIf(userPassword.length() <8|| checkPassword.length()<8, ErrorCode.PARAMS_ERROR,"用户密码过短");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR,"两次输入的密码不一致");
        // 是否重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.count(queryWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR,"账号重复");
        //加密
        String encryptPassword = getEncryptPassword(userPassword);
        //插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("哈基米");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean result = this.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR,"注册失败，数据库操作失败");
        return user.getId();
    }

    @Override
    public LoginUserVo userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 校验
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount,userPassword), ErrorCode.PARAMS_ERROR,"参数为空");
        ThrowUtils.throwIf(userAccount.length() <4, ErrorCode.PARAMS_ERROR,"账号错误");
        ThrowUtils.throwIf(userPassword.length() <8, ErrorCode.PARAMS_ERROR,"密码错误");
        // 加密
        String encryptPassword = getEncryptPassword(userPassword);
        //查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.getOne(queryWrapper);
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR,"用户不存在或密码错误");
        // 查到用户后记录登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return getLoginUserVo(user);
    }

    // 得到当前的登录对象
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object obj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) obj;
        ThrowUtils.throwIf(currentUser == null || currentUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 这是服务器中缓存的对象，但是有可能用户已经更新了一些信息
        Long id = currentUser.getId();
        User latestUser = this.getById(id);
        ThrowUtils.throwIf(latestUser == null, ErrorCode.NOT_LOGIN_ERROR);
        return latestUser;
    }

    // 用户注销
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 只有登录了才能注销
        Object obj = request.getSession().getAttribute(USER_LOGIN_STATE);
        ThrowUtils.throwIf(obj == null, ErrorCode.OPERATION_ERROR,"未登录");
        // 可以注销
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }


    // 加密函数
    @Override
    public String getEncryptPassword(String userPassword){
        final String salt = "ray";
        return DigestUtils.md5DigestAsHex((salt + userPassword).getBytes());
    }

    // 返回登录脱敏对象
    @Override
    public LoginUserVo getLoginUserVo(User user){
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR,"想要脱敏的登录对象为空");
        LoginUserVo loginUserVo = new LoginUserVo();
        BeanUtils.copyProperties(user,loginUserVo);
        return loginUserVo;
    }

    // 用户脱敏
    @Override
    public UserVo getUserVo(User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR,"想要脱敏的用户对象为空");
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user,userVo);
        return userVo;
    }

    //脱敏用户列表
    @Override
    public List<UserVo> getUserVoList(List<User> userList) {
        ThrowUtils.throwIf(CollUtil.isEmpty(userList), ErrorCode.PARAMS_ERROR,"想要脱敏的用户列表为空");
        return userList.stream().map((this::getUserVo)).collect(Collectors.toList());
    }

    // 将分页的请求参数封装为QueryWrapper对象
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR,"分页请求参数为空");
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField); //第二个参数true为升序，false为降序
        return queryWrapper;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }
}




