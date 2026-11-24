package com.atguigu.exam.controller;

import com.atguigu.exam.common.Result;
import com.atguigu.exam.config.AuthInterceptor;
import com.atguigu.exam.service.StatsService;
import com.atguigu.exam.utils.TokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StatsControllerHealthTest {

    @Mock
    private StatsService statsService;

    @InjectMocks
    private StatsController controller;

    @Test
    void healthyResponseDoesNotIncludeInstitutionStatistics() {
        Result<String> result = controller.testDatabase();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("数据库连接正常");
        assertThat(result.getData()).isNull();
        verify(statsService).checkDatabaseConnection();
        verifyNoMoreInteractions(statsService);
    }

    @Test
    void databaseFailureDoesNotExposeInternalDetails() {
        doThrow(new IllegalStateException("private database details")).when(statsService).checkDatabaseConnection();

        Result<String> result = controller.testDatabase();

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("数据库连接异常");
        assertThat(result.getMessage()).doesNotContain("private database details");
        assertThat(result.getData()).isNull();
    }

    @Test
    void anonymousHealthRequestReturnsOnlyStatusButOverviewRequiresAuthentication() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "tokenUtil", mock(TokenUtil.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(interceptor).build();

        mvc.perform(get("/api/stats/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/stats/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verify(statsService).checkDatabaseConnection();
        verifyNoMoreInteractions(statsService);
    }
}
