package com.ray.raypicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.raypicturebackend.model.dto.space.SpaceAddRequest;
import com.ray.raypicturebackend.model.dto.space.SpaceQueryRequest;
import com.ray.raypicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.raypicturebackend.model.entity.User;
import com.ray.raypicturebackend.model.vo.SpaceVo;

import javax.servlet.http.HttpServletRequest;

/**
* @author Ray
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-07-28 14:08:54
*/
public interface SpaceService extends IService<Space> {
    /**
     * 校验空间
     */
    void validSpace(Space space, Boolean add);

    /**
     * 根据空间级别填充空间参数
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 创建空间
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    // 分页获取图片封装
    Page<SpaceVo> getSpaceVoPage(Page<Space> spacePage, HttpServletRequest request);

    // 将查询请求转化为QueryWrapper对象
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    // 封装单个空间，主要是为原有空间关联创建用户的信息
    SpaceVo getSpaceVo(Space space, HttpServletRequest request);
}
