package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.utils.TokenUtil;
import com.atguigu.exam.vo.LoginResponseVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户Service实现类
 * 实现用户登录、权限校验、注册、管理员账号管理等业务逻辑
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_FAILURE_WINDOW_SECONDS = 15 * 60L;
    private static final String ACCOUNT_FAILURE_PREFIX = "auth:login:fail:account:";
    private static final String IP_FAILURE_PREFIX = "auth:login:fail:ip:";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private TokenUtil tokenUtil;

    @Override
    public LoginResponseVo login(String username, String password) {
        return login(username, password, "unknown");
    }

    @Override
    public LoginResponseVo login(String username, String password, String clientIp) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        String accountKey = ACCOUNT_FAILURE_PREFIX + normalizedUsername;
        String ipKey = IP_FAILURE_PREFIX + normalizedIp;

        if (isBlocked(accountKey) || isBlocked(ipKey)) {
            throw new RuntimeException("登录失败次数过多，请 15 分钟后再试");
        }

        // 1. 根据用户名查询用户
        User user = baseMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        // 2. 用户不存在或密码不匹配，统一提示，避免泄露账号是否存在
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            recordLoginFailure(accountKey, ipKey);
            throw new RuntimeException("用户名或密码错误");
        }

        // 3. 校验账号状态：INACTIVE 拒登（契约第 11 节，文案与契约一致；验收标准 5 的前置）
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用");
        }

        redisUtils.delete(accountKey);
        // 4. 构造登录响应（token 使用 JWT 签名令牌）
        LoginResponseVo vo = new LoginResponseVo();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setToken(tokenUtil.generateToken(user.getId(), user.getUsername(), user.getRole()));
        return vo;
    }

    private boolean isBlocked(String key) {
        Object value = redisUtils.get(key);
        return value != null && Long.parseLong(value.toString()) >= MAX_LOGIN_FAILURES;
    }

    private void recordLoginFailure(String accountKey, String ipKey) {
        redisUtils.increment(accountKey, LOGIN_FAILURE_WINDOW_SECONDS);
        redisUtils.increment(ipKey, LOGIN_FAILURE_WINDOW_SECONDS);
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

    @Override
    public void resetPasswordToDefault(Long userId) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // ADMIN 保护（含管理员重置自己）：防自锁（契约 7.1）
        if ("ADMIN".equals(user.getRole())) {
            throw new RuntimeException("管理员账号不支持重置");
        }
        // 幂等：重复重置返回成功
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        baseMapper.updateById(user);
        log.info("管理员将用户 {} 密码重置为默认值", userId);
    }

    @Override
    public void updateAccountStatus(Long userId, boolean enabled) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // ADMIN 保护（含自己）：禁用/启用不得作用于 ADMIN 账号（契约 7.2）
        if ("ADMIN".equals(user.getRole())) {
            throw new RuntimeException("管理员账号不支持禁用");
        }
        // 幂等：重复设置同值返回成功；已发 token 不强制失效（决策表 #7）
        user.setStatus(enabled ? "ACTIVE" : "INACTIVE");
        baseMapper.updateById(user);
        log.info("管理员将用户 {} 状态更新为 {}", userId, user.getStatus());
    }
}
