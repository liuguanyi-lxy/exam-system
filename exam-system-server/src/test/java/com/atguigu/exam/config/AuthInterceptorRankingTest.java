package com.atguigu.exam.config;

import com.atguigu.exam.controller.ExamRecordController;
import com.atguigu.exam.utils.TokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorRankingTest {

    @Test
    void anonymousRankingRequestIsRejectedByAuthenticationInterceptor() throws Exception {
        TokenUtil tokenUtil = mock(TokenUtil.class);
        when(tokenUtil.validateToken(null)).thenReturn(false);
        AuthInterceptor interceptor = new AuthInterceptor();
        org.springframework.test.util.ReflectionTestUtils.setField(interceptor, "tokenUtil", tokenUtil);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/exam-records/ranking");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Method rankingMethod = ExamRecordController.class.getMethod(
                "getExamRanking", Integer.class, Integer.class, jakarta.servlet.http.HttpServletRequest.class);

        boolean allowed = interceptor.preHandle(request, response,
                new HandlerMethod(new ExamRecordController(), rankingMethod));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
