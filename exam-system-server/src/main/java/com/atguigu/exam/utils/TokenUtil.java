package com.atguigu.exam.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import jakarta.annotation.PostConstruct;

/**
 * JWT 令牌工具类
 * 用于登录后生成签名令牌，以及在校验拦截器中解析验证令牌
 */
@Component
public class TokenUtil {

    /** 令牌签名密钥，必须通过私密配置或 JWT_SECRET 注入 */
    @Value("${jwt.secret:}")
    private String secret;

    /** 令牌过期时间（毫秒），默认 7 天 */
    @Value("${jwt.expire:604800000}")
    private long expire;

    @PostConstruct
    void validateConfiguration() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 必须配置且至少包含 32 个字符");
        }
        if (expire <= 0) {
            throw new IllegalStateException("jwt.expire 必须为正数");
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成令牌
     * @param userId 用户ID
     * @param username 用户名
     * @param role 角色
     * @return JWT 令牌字符串
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expire))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析令牌，返回 Claims
     * @param token 令牌字符串
     * @return 解析出的 Claims
     * @throws JwtException 令牌无效、过期或签名错误时抛出
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 校验令牌是否有效
     * @param token 令牌字符串
     * @return true=有效，false=无效或过期
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从令牌中获取用户ID
     * @param token 令牌字符串
     * @return 用户ID，解析失败返回 null
     */
    public Long getUserId(String token) {
        try {
            return Long.valueOf(parseToken(token).getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从令牌中获取角色
     * @param token 令牌字符串
     * @return 角色，解析失败返回 null
     */
    public String getRole(String token) {
        try {
            return parseToken(token).get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
