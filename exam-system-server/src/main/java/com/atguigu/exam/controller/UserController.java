package com.atguigu.exam.controller;


import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import com.atguigu.exam.vo.RegisterRequestVo;
import com.atguigu.exam.vo.UserStatusVo;
import com.atguigu.exam.utils.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * 用户控制器 - 处理用户认证和权限管理相关的HTTP请求
 * 包括用户登录、注册、权限验证等功能
 */
@Slf4j
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/user")  // 用户API路径前缀
@Tag(name = "用户管理", description = "用户相关操作，包括登录认证、注册、权限验证等功能")  // Swagger API分组
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     * @param loginRequestVo 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")  // 处理POST请求
    @Operation(summary = "用户登录", description = "用户通过用户名和密码进行登录验证，返回用户信息和token")  // API描述
    public Result<LoginResponseVo> login(@Valid @RequestBody LoginRequestVo loginRequestVo,
                                         HttpServletRequest request) {
        LoginResponseVo vo = userService.login(loginRequestVo.getUsername(), loginRequestVo.getPassword(),
                IpUtils.getClientIp(request));
        return Result.success(vo);
    }

    /**
     * 用户注册（默认注册为学生角色）
     * @param registerRequestVo 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")  // 处理POST请求
    @Operation(summary = "用户注册", description = "新用户注册，默认分配学生角色")  // API描述
    public Result<Void> register(@Valid @RequestBody RegisterRequestVo registerRequestVo) {
        userService.register(registerRequestVo.getUsername(), registerRequestVo.getPassword(), registerRequestVo.getRealName());
        return Result.success("注册成功");
    }

    /**
     * 检查用户权限
     * @param userId 用户ID
     * @return 权限检查结果
     */
    @GetMapping("/check-admin/{userId}")  // 处理GET请求
    @Operation(summary = "检查管理员权限", description = "验证指定用户是否具有管理员权限")  // API描述
    public Result<Boolean> checkAdmin(
            @Parameter(description = "用户ID") @PathVariable Long userId) {

        return Result.success(userService.checkAdmin(userId));
    }

    /**
     * 重置密码为默认值（ADMIN，契约 7.1）
     * 无请求体；默认密码 123456（验收标准 5）；ADMIN 账号（含自己）拒绝，防自锁；幂等
     */
    @RequireRole({"ADMIN"})
    @PutMapping("/{id}/password/reset")
    @Operation(summary = "重置密码为默认值", description = "将目标账号密码重置为 123456；ADMIN 账号不支持重置；幂等")
    public Result<Map<String, Object>> resetPassword(
            @Parameter(description = "目标用户ID") @PathVariable Long id) {
        userService.resetPasswordToDefault(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", id);
        data.put("defaultPassword", UserService.DEFAULT_PASSWORD);
        log.info("管理员重置用户 {} 密码为默认值", id);
        return Result.success(data, "密码已重置");
    }

    /**
     * 禁用/启用账号（ADMIN，契约 7.2）
     * enabled=true → ACTIVE；false → INACTIVE；禁用后登录失败（message"账号已被禁用"）；幂等
     */
    @RequireRole({"ADMIN"})
    @PutMapping("/{id}/status")
    @Operation(summary = "禁用/启用账号", description = "ADMIN 账号（含自己）不支持禁用；禁用后登录被拒；幂等")
    public Result<Map<String, Object>> updateAccountStatus(
            @Parameter(description = "目标用户ID") @PathVariable Long id,
            @Valid @RequestBody UserStatusVo body) {
        userService.updateAccountStatus(id, Boolean.TRUE.equals(body.getEnabled()));
        User user = userService.getById(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", id);
        data.put("status", user == null ? null : user.getStatus());
        log.info("管理员更新用户 {} 状态：{}", id, data.get("status"));
        return Result.success(data);
    }
}
