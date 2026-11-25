package com.atguigu.exam.controller;

import com.atguigu.exam.config.AuthInterceptor;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.exceptionhandlers.GlobalExceptionHandler;
import com.atguigu.exam.mapper.TeachingStatsMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.impl.TeachingStatsServiceImpl;
import com.atguigu.exam.utils.TokenUtil;
import com.atguigu.exam.vo.TeachingStatsVo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 教学统计接口数据范围测试（standalone MockMvc + 真实 AuthInterceptor）：
 * 学员 403、教师越权（ClassScopeException→HTTP 403）、教师带班通过、管理员全量通过。
 */
class TeachingStatsControllerScopeTest {

    private static final long TEACHER_ID = 88L;
    private static final long CLASS_ID = 5L;

    private final ClassScopeService scopes = mock(ClassScopeService.class);
    private final TeachingStatsMapper mapper = mock(TeachingStatsMapper.class);
    private final TeachingStatsServiceImpl service = new TeachingStatsServiceImpl(mapper, scopes);
    private final TeachingStatsController controller = new TeachingStatsController(service);

    private MockMvc mvcFor(String role, long userId) {
        TokenUtil tokenUtil = mock(TokenUtil.class);
        when(tokenUtil.validateToken("fixture")).thenReturn(true);
        when(tokenUtil.getUserId("fixture")).thenReturn(userId);
        when(tokenUtil.getRole("fixture")).thenReturn(role);
        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "tokenUtil", tokenUtil);
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void studentIsRejectedByRoleInterceptor() throws Exception {
        MockMvc mvc = mvcFor("STUDENT", 31L);

        mvc.perform(get("/api/teaching-stats").param("classId", String.valueOf(CLASS_ID))
                        .header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teaching-stats/compare").header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scopes, mapper);
    }

    @Test
    void teacherOutsideClassGetsForbidden() throws Exception {
        when(scopes.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(false);
        MockMvc mvc = mvcFor("TEACHER", TEACHER_ID);

        mvc.perform(get("/api/teaching-stats").param("classId", String.valueOf(CLASS_ID))
                        .header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(mapper);
    }

    @Test
    void teacherInClassGetsStats() throws Exception {
        when(scopes.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(true);
        stubEmptyStats();
        MockMvc mvc = mvcFor("TEACHER", TEACHER_ID);

        mvc.perform(get("/api/teaching-stats").param("classId", String.valueOf(CLASS_ID))
                        .header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classInfo.classId").value(CLASS_ID))
                .andExpect(jsonPath("$.data.classInfo.className").value("M5验证班"))
                .andExpect(jsonPath("$.data.active.days").value(7));
    }

    @Test
    void adminPassesAnyClass() throws Exception {
        stubEmptyStats();
        MockMvc mvc = mvcFor("ADMIN", 1L);

        mvc.perform(get("/api/teaching-stats").param("classId", String.valueOf(CLASS_ID))
                        .header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classInfo.classId").value(CLASS_ID));
    }

    @Test
    void teacherCompareUsesOwnClassesOnly() throws Exception {
        when(scopes.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of(CLASS_ID));
        when(mapper.compareClasses(List.of(CLASS_ID))).thenReturn(List.of(
                new TeachingStatsVo.CompareItem(CLASS_ID, "M5验证班", 45.0, 50, 100)));
        MockMvc mvc = mvcFor("TEACHER", TEACHER_ID);

        mvc.perform(get("/api/teaching-stats/compare").header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].classId").value(CLASS_ID))
                .andExpect(jsonPath("$.data.items[0].passRate").value(50));

        verify(mapper).compareClasses(List.of(CLASS_ID));
    }

    @Test
    void compareReturnsEmptyForTeacherWithoutClass() throws Exception {
        when(scopes.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of());
        MockMvc mvc = mvcFor("TEACHER", TEACHER_ID);

        mvc.perform(get("/api/teaching-stats/compare").header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verifyNoInteractions(mapper);
    }

    @Test
    void invalidDaysFallsToBusinessError() throws Exception {
        when(scopes.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(true);
        MockMvc mvc = mvcFor("TEACHER", TEACHER_ID);

        mvc.perform(get("/api/teaching-stats").param("classId", String.valueOf(CLASS_ID))
                        .param("days", "8").header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("days 仅支持 7 或 30"));
    }

    private void stubEmptyStats() {
        when(mapper.classInfo(CLASS_ID)).thenReturn(new TeachingStatsVo.ClassInfo(CLASS_ID, "M5验证班", 2));
        when(mapper.activeCount(CLASS_ID, 7)).thenReturn(0L);
        when(mapper.courseLearning(CLASS_ID)).thenReturn(List.of());
        when(mapper.examStats(CLASS_ID)).thenReturn(List.of());
        when(mapper.examAbsentees(CLASS_ID)).thenReturn(List.of());
        when(mapper.overdueTasks(CLASS_ID)).thenReturn(List.of());
        when(mapper.topWrongQuestions(CLASS_ID)).thenReturn(List.of());
        when(mapper.reviewStats(CLASS_ID)).thenReturn(new TeachingStatsVo.ReviewStats(0, 0, 0, 0));
    }
}
