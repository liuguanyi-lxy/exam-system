package com.atguigu.exam.service;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.vo.LoginResponseVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户Service接口
 * 定义用户相关的业务方法
 */
public interface UserService extends IService<User> {

    /**
     * 用户登录：校验用户名密码，返回用户信息和登录令牌
     */
    LoginResponseVo login(String username, String password);

    /**
     * 检查用户是否具有管理员权限（ADMIN 或 TEACHER）
     */
    boolean checkAdmin(Long userId);

    /**
     * 用户注册：默认注册为学生角色
     */
    void register(String username, String password, String realName);
}
