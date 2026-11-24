package com.atguigu.exam.service;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.vo.LoginResponseVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户Service接口
 * 定义用户相关的业务方法
 */
public interface UserService extends IService<User> {

    /** 管理员重置/导入建号使用的默认密码（验收标准 2/5） */
    String DEFAULT_PASSWORD = "123456";

    /**
     * 用户登录：校验用户名密码，返回用户信息和登录令牌
     */
    LoginResponseVo login(String username, String password);

    /**
     * 登录并按来源地址执行失败次数限制。
     */
    LoginResponseVo login(String username, String password, String clientIp);

    /**
     * 检查用户是否具有管理员权限（ADMIN 或 TEACHER）
     */
    boolean checkAdmin(Long userId);

    /**
     * 用户注册：默认注册为学生角色
     */
    void register(String username, String password, String realName);

    /**
     * 管理员将目标账号密码重置为默认值 DEFAULT_PASSWORD（契约 7.1）。
     * 目标不存在 → "用户不存在"；目标是 ADMIN（含管理员重置自己）→ "管理员账号不支持重置"（防自锁）；幂等
     */
    void resetPasswordToDefault(Long userId);

    /**
     * 管理员禁用/启用账号（契约 7.2）。
     * 目标不存在 → "用户不存在"；目标是 ADMIN（含自己）→ "管理员账号不支持禁用"；
     * 禁用后 /api/user/login 拒登（message"账号已被禁用"）；幂等；已发 token 不强制失效
     */
    void updateAccountStatus(Long userId, boolean enabled);
}
