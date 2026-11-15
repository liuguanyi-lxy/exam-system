package com.atguigu.exam.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TokenUtil单元测试（纯JVM，不依赖Spring上下文）
 */
class TokenUtilTest {

    private static final String SECRET = "test-secret-key-for-unit-test-0123456789abcdef";
    private static final long EXPIRE = 60_000L;

    private TokenUtil tokenUtil;

    @BeforeEach
    void setUp() {
        tokenUtil = new TokenUtil();
        ReflectionTestUtils.setField(tokenUtil, "secret", SECRET);
        ReflectionTestUtils.setField(tokenUtil, "expire", EXPIRE);
    }

    @Test
    void 生成并校验token() {
        String token = tokenUtil.generateToken(1L, "admin", "ADMIN");
        assertTrue(tokenUtil.validateToken(token));
        Claims claims = tokenUtil.parseToken(token);
        assertEquals("admin", claims.get("username", String.class));
        assertEquals("ADMIN", tokenUtil.getRole(token));
        assertEquals(1L, tokenUtil.getUserId(token));
    }

    @Test
    void 篡改的token校验失败() {
        String token = tokenUtil.generateToken(1L, "admin", "ADMIN");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertFalse(tokenUtil.validateToken(tampered));
    }

    @Test
    void 过期token校验失败() {
        ReflectionTestUtils.setField(tokenUtil, "expire", 1L);
        String token = tokenUtil.generateToken(1L, "admin", "ADMIN");
        //等token过期
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(tokenUtil.validateToken(token));
    }

    @Test
    void 空token校验失败() {
        assertFalse(tokenUtil.validateToken(null));
        assertFalse(tokenUtil.validateToken(""));
        assertFalse(tokenUtil.validateToken("not-a-jwt"));
    }

    @Test
    void 密钥不同的token校验失败() {
        String token = tokenUtil.generateToken(1L, "admin", "ADMIN");
        TokenUtil other = new TokenUtil();
        ReflectionTestUtils.setField(other, "secret", "another-secret-key-for-unit-test-0123456789");
        ReflectionTestUtils.setField(other, "expire", EXPIRE);
        assertFalse(other.validateToken(token));
    }
}
