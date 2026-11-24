package com.atguigu.exam.controller;

import com.atguigu.exam.config.AuthInterceptor;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.exceptionhandlers.GlobalExceptionHandler;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ClassService;
import com.atguigu.exam.utils.TokenUtil;
import com.atguigu.exam.vo.ClassMemberVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassControllerScopeTest {

    private static final long TEACHER_ID = 17L;
    private static final long CLASS_ID = 28L;

    private final ClassScopeService classScopeService = mock(ClassScopeService.class);
    private final ClassService classService = mock(ClassService.class);
    private final ClassController classController = new ClassController();
    private final ClassMemberController classMemberController = new ClassMemberController();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(classController, "classScopeService", classScopeService);
        ReflectionTestUtils.setField(classController, "classService", classService);
        ReflectionTestUtils.setField(classMemberController, "classScopeService", classScopeService);
        ReflectionTestUtils.setField(classMemberController, "classService", classService);
    }

    @Test
    void teacherCannotReadDetailForUnassignedClass() {
        when(classScopeService.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(false);

        assertThatThrownBy(() -> classController.getClassDetail(CLASS_ID, teacherRequest()))
                .isInstanceOf(ClassScopeException.class);

        verify(classService, never()).getClassDetail(any());
    }

    @Test
    void teacherCannotListMembersForUnassignedClass() {
        when(classScopeService.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(false);

        assertThatThrownBy(() -> classMemberController.getMembers(
                CLASS_ID, 1, 10, null, null, false, teacherRequest()))
                .isInstanceOf(ClassScopeException.class);

        verify(classService, never()).pageMembers(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void teacherClassListUsesOnlyAssignedClassIdsAndReturnsEmptyForEmptyScope() {
        when(classScopeService.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of());
        Page<Clazz> emptyPage = new Page<>(1, 10);
        emptyPage.setTotal(0);
        emptyPage.setRecords(List.of());
        when(classService.pageClasses(any(), any(), any(), eq(List.of()))).thenReturn(emptyPage);

        var result = classController.getClassList(1, 10, null, null, teacherRequest());

        assertThat(result.getData().getRecords()).isEmpty();
        verify(classService).pageClasses(any(), any(), any(), eq(List.of()));
    }

    @Test
    void classAndMemberPaginationInputsAreClamped() {
        Page<Clazz> classPage = new Page<>(1, 10);
        when(classService.pageClasses(any(), any(), any(), eq(null))).thenAnswer(invocation -> {
            Page<Clazz> requestedPage = invocation.getArgument(0);
            assertThat(requestedPage.getCurrent()).isEqualTo(10_000);
            assertThat(requestedPage.getSize()).isEqualTo(100);
            return classPage;
        });
        Page<ClassMemberVo> memberPage = new Page<>(1, 10);
        when(classService.pageMembers(any(), eq(CLASS_ID), any(), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    Page<ClassMemberVo> requestedPage = invocation.getArgument(0);
                    assertThat(requestedPage.getCurrent()).isEqualTo(1);
                    assertThat(requestedPage.getSize()).isEqualTo(1);
                    return memberPage;
                });

        classController.getClassList(20_000, 500, null, null, adminRequest());
        classMemberController.getMembers(CLASS_ID, 0, -20, null, null, false, adminRequest());
    }

    @Test
    void teacherCanReadAssignedClassAndReceivesForbiddenOutsideScope() throws Exception {
        MockMvc mvc = classMvc("TEACHER", TEACHER_ID);
        Clazz assigned = new Clazz();
        assigned.setId(CLASS_ID);
        when(classScopeService.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(true);
        when(classService.getClassDetail(CLASS_ID)).thenReturn(assigned);

        mvc.perform(get("/api/classes/{id}", CLASS_ID).header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(CLASS_ID));
        mvc.perform(get("/api/classes/{id}", CLASS_ID + 1).header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        verify(classService, never()).getClassDetail(CLASS_ID + 1);
    }

    @Test
    void teacherCannotDeleteAnAssignedClassOrRemoveItsMembers() throws Exception {
        MockMvc mvc = classMvc("TEACHER", TEACHER_ID);
        when(classScopeService.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(true);

        mvc.perform(delete("/api/classes/{id}", CLASS_ID).header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/classes/{id}/members/{userId}", CLASS_ID, 31L)
                        .header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(classService, classScopeService);
    }

    @Test
    void myClassesUsesAuthenticatedStudentAndRejectsAnonymousRequests() throws Exception {
        long studentId = 31L;
        MockMvc mvc = classMvc("STUDENT", studentId);
        when(classService.listMyClasses(studentId)).thenReturn(List.of());

        mvc.perform(get("/api/my/classes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/my/classes").param("userId", "99").header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(classService).listMyClasses(studentId);
        verify(classService, never()).listMyClasses(99L);
    }

    private MockMvc classMvc(String role, long userId) {
        TokenUtil tokenUtil = mock(TokenUtil.class);
        when(tokenUtil.validateToken("fixture")).thenReturn(true);
        when(tokenUtil.getUserId("fixture")).thenReturn(userId);
        when(tokenUtil.getRole("fixture")).thenReturn(role);
        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "tokenUtil", tokenUtil);
        MyClassController myClassController = new MyClassController();
        ReflectionTestUtils.setField(myClassController, "classService", classService);
        return MockMvcBuilders.standaloneSetup(classController, classMemberController, myClassController)
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequest teacherRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", TEACHER_ID);
        request.setAttribute("userRole", "TEACHER");
        return request;
    }

    private static MockHttpServletRequest adminRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        request.setAttribute("userRole", "ADMIN");
        return request;
    }
}
