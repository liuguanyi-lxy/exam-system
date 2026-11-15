package com.atguigu.exam.config;

import com.atguigu.exam.common.Result;
import com.atguigu.exam.utils.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录认证拦截器
 * 校验所有受保护接口的 Authorization: Bearer <token> 请求头
 * 公开接口（登录注册、首页数据、浏览类 GET 接口）通过白名单放行
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenUtil tokenUtil;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 放行公开接口
        if (isPublicPath(uri, method)) {
            return true;
        }

        // 获取并校验 token
        String token = resolveToken(request);
        if (!tokenUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            // code 统一为 401，与前端响应拦截器判断一致
            Result<Void> error = Result.error(401, "未登录或登录已过期，请重新登录");
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(error));
            log.warn("请求被拒绝：未认证访问 {}", uri);
            return false;
        }

        // 将用户信息放入请求上下文，供后续使用
        request.setAttribute("userId", tokenUtil.getUserId(token));
        request.setAttribute("userRole", tokenUtil.getRole(token));
        return true;
    }

    /** 从 Authorization 头解析 token */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /**
     * 判断是否为公开接口（无需登录）
     * 规则：
     *  1. 登录/注册/权限查询/健康检查 → 全部方法公开
     *  2. 静态文件 → 公开
     *  3. 浏览类数据接口 → 仅 GET 公开，写操作需登录
     */
    private boolean isPublicPath(String uri, String method) {
        // 认证与权限接口
        if (uri.equals("/api/user/login") || uri.equals("/api/user/register")) return true;
        if (uri.startsWith("/api/user/check-admin")) return true;
        // 健康检查
        if (uri.equals("/api/stats/test")) return true;
        // 静态文件
        if (uri.startsWith("/files")) return true;

        // 仅 GET 公开的数据浏览接口
        if ("GET".equalsIgnoreCase(method)) {
            return isPublicGetPath(uri);
        }
        return false;
    }

    /** 浏览类公开 GET 接口 */
    private boolean isPublicGetPath(String uri) {
        // 首页数据
        if (uri.startsWith("/api/banners/active")) return true;
        if (uri.startsWith("/api/notices/active") || uri.startsWith("/api/notices/latest")) return true;
        // 题目浏览：热门题目只返回题干（已脱敏），可用于首页
        if (uri.startsWith("/api/questions/popular")) return true;
        // 题库列表和单题详情包含答案/解析，必须登录后再按角色脱敏，不再匿名放行
        // 试卷列表可以公开（不含题目），试卷详情含题目与答案，要求登录
        if (uri.equals("/api/papers/list")) return true;
        // 排行榜
        if (uri.startsWith("/api/exam-records/ranking")) return true;
        // 视频浏览与分类
        if (uri.startsWith("/api/videos")) return true;
        if (uri.startsWith("/api/video-categories")) return true;
        if (uri.startsWith("/api/categories")) return true;
        return false;
    }
}
