package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.utils.TokenUtil;
import com.atguigu.exam.vo.LoginResponseVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户Service实现类
 * 实现用户登录、权限校验、注册等业务逻辑
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private TokenUtil tokenUtil;

    @Override
    public LoginResponseVo login(String username, String password) {
        // 1. 根据用户名查询用户
        User user = baseMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        // 2. 用户不存在或密码不匹配，统一提示，避免泄露账号是否存在
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 3. 校验账号状态
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        // 4. 构造登录响应（token 使用 JWT 签名令牌）
        LoginResponseVo vo = new LoginResponseVo();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setToken(tokenUtil.generateToken(user.getId(), user.getUsername(), user.getRole()));
        return vo;
    }

    @Override
    public boolean checkAdmin(Long userId) {
        User user = baseMapper.selectById(userId);
        return user != null && ("ADMIN".equals(user.getRole()) || "TEACHER".equals(user.getRole()));
    }

    @Override
    public void register(String username, String password, String realName) {
        // 1. 检查用户名是否已存在
        Long count = baseMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count != null && count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 创建用户，默认学生角色、启用状态
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName != null && !realName.isBlank() ? realName : username);
        user.setRole("STUDENT");
        user.setStatus("ACTIVE");
        baseMapper.insert(user);
    }
}
