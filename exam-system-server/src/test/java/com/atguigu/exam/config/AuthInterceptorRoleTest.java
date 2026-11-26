package com.atguigu.exam.config;

import com.atguigu.exam.controller.RemediationController;
import com.atguigu.exam.service.RemediationStepService;
import com.atguigu.exam.service.RemediationTaskService;
import com.atguigu.exam.service.RemediationTeacherService;
import com.atguigu.exam.utils.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * M7 A4：@RequireRole 角色注解拦截机制回归。
 *
 * 之前只有匿名拦截测试；这里钉住角色不足必须 403 且请求终止（返回 false），
 * 角色匹配/无注解接口必须放行并写入 userId/userRole 请求属性。
 * HandlerMethod 直接绑定真实控制器方法，注解读取走方法级（RemediationController 均为方法级注解）。
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorRoleTest {

    @Mock
    private TokenUtil tokenUtil;

    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthInterceptor();
        ReflectionSet.field(interceptor, "tokenUtil", tokenUtil);
    }

    @Test
    void studentIsForbiddenOnTeacherOnlyEndpoint() throws Exception {
        HandlerMethod handler = handler("overview", long.class, Long.class, String.class);
        MockHttpServletRequest request = request("/api/remediation/overview", "GET", "STUDENT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean pass = interceptor.preHandle(request, response, handler);

        assertThat(pass).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("无权限执行该操作");
    }

    @Test
    void teacherPassesTeacherOnlyEndpointAndContextIsFilled() throws Exception {
        HandlerMethod handler = handler("overview", long.class, Long.class, String.class);
        MockHttpServletRequest request = request("/api/remediation/overview", "GET", "TEACHER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean pass = interceptor.preHandle(request, response, handler);

        assertThat(pass).isTrue();
        assertThat(request.getAttribute("userRole")).isEqualTo("TEACHER");
        assertThat(request.getAttribute("userId")).isEqualTo(5L);
    }

    @Test
    void studentPassesStudentOnlyEndpoint() throws Exception {
        HandlerMethod handler = handler("list", Long.class, String.class);
        MockHttpServletRequest request = request("/api/remediation/tasks", "GET", "STUDENT");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    @Test
    void adminPassesTeacherOnlyEndpoint() throws Exception {
        HandlerMethod handler = handler("overview", long.class, Long.class, String.class);
        MockHttpServletRequest request = request("/api/remediation/overview", "GET", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
    }

    // ========== helpers ==========

    /** RemediationController 依赖全部下沉 service 层，测试只关心注解与角色比对，依赖传 null */
    private HandlerMethod handler(String methodName, Class<?>... paramTypes) throws Exception {
        RemediationController bean = new RemediationController(null, null, null);
        Method method = RemediationController.class.getMethod(methodName, paramTypes);
        return new HandlerMethod(bean, method);
    }

    private MockHttpServletRequest request(String uri, String method, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer test-token");
        when(tokenUtil.validateToken("test-token")).thenReturn(true);
        when(tokenUtil.getRole("test-token")).thenReturn(role);
        when(tokenUtil.getUserId("test-token")).thenReturn("TEACHER".equals(role) ? 5L : 7L);
        return request;
    }

    /** 字段私有且无 setter，测试内一次性注入 */
    private static final class ReflectionSet {
        static void field(Object target, String name, Object value) {
            try {
                var f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
