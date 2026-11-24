package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.vo.ClassMemberVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClassServiceMemberSummaryTest {

    private static final long CLASS_ID = 4L;
    private static final long STUDENT_ID = 31L;
    private static final long TEACHER_ID = 41L;

    private final ClassServiceImpl service = new ClassServiceImpl();
    private final ClassMemberMapper classMemberMapper = mock(ClassMemberMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final ExamRecordMapper examRecordMapper = mock(ExamRecordMapper.class);

    @BeforeAll
    static void initLambdaColumns() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "class-member-summary-test");
        TableInfoHelper.initTableInfo(assistant, ClassMember.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    @BeforeEach
    void setUp() {
        ClazzMapper clazzMapper = mock(ClazzMapper.class);
        when(clazzMapper.selectById(CLASS_ID)).thenReturn(new Clazz());
        ReflectionTestUtils.setField(service, "baseMapper", clazzMapper);
        ReflectionTestUtils.setField(service, "classMemberMapper", classMemberMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "examRecordMapper", examRecordMapper);
        when(classMemberMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ClassMember> page = invocation.getArgument(0);
            page.setRecords(List.of(member(STUDENT_ID, "STUDENT"), member(TEACHER_ID, "TEACHER")));
            page.setTotal(2);
            return page;
        });
        when(userMapper.selectBatchIds(List.of(STUDENT_ID, TEACHER_ID)))
                .thenReturn(List.of(user(STUDENT_ID), user(TEACHER_ID)));
        when(examRecordMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("user_id", STUDENT_ID, "exam_count", 3L, "last_time", "")));
    }

    @Test
    void includesSummaryForStudentsButNotTeacherMembers() {
        Page<ClassMemberVo> result = service.pageMembers(new Page<>(1, 10), CLASS_ID, null, null, true);

        assertThat(result.getRecords()).filteredOn(row -> STUDENT_ID == row.getUserId())
                .singleElement().satisfies(row -> assertThat(row.getExamCount()).isEqualTo(3));
        assertThat(result.getRecords()).filteredOn(row -> TEACHER_ID == row.getUserId())
                .singleElement().satisfies(row -> assertThat(row.getExamCount()).isNull());
        verify(examRecordMapper).selectMaps(any());
    }

    @Test
    void skipsSummaryQueryWhenPageContainsOnlyTeachers() {
        when(classMemberMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ClassMember> page = invocation.getArgument(0);
            page.setRecords(List.of(member(TEACHER_ID, "TEACHER")));
            page.setTotal(1);
            return page;
        });
        when(userMapper.selectBatchIds(List.of(TEACHER_ID))).thenReturn(List.of(user(TEACHER_ID)));

        Page<ClassMemberVo> result = service.pageMembers(new Page<>(1, 10), CLASS_ID, "TEACHER", null, true);

        assertThat(result.getRecords()).singleElement()
                .satisfies(row -> assertThat(row.getExamCount()).isNull());
        verifyNoInteractions(examRecordMapper);
    }

    @Test
    void keywordWithoutMatchesReturnsEmptyPageWithoutQueryingMemberships() {
        when(userMapper.selectList(any())).thenReturn(List.of());

        Page<ClassMemberVo> result = service.pageMembers(new Page<>(2, 5), CLASS_ID, "STUDENT", "无匹配", true);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        verifyNoInteractions(classMemberMapper, examRecordMapper);
    }

    @Test
    void memberSearchCombinesClassRoleAndMatchingUserIds() {
        User student = user(STUDENT_ID);
        when(userMapper.selectList(any())).thenReturn(List.of(student));
        when(userMapper.selectBatchIds(List.of(STUDENT_ID))).thenReturn(List.of(student));
        when(classMemberMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ClassMember> page = invocation.getArgument(0);
            page.setRecords(List.of(member(STUDENT_ID, "STUDENT")));
            page.setTotal(1);
            return page;
        });

        Page<ClassMemberVo> result = service.pageMembers(new Page<>(1, 5), CLASS_ID, "STUDENT", "  学员  ", false);

        ArgumentCaptor<LambdaQueryWrapper<User>> userFilter = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectList(userFilter.capture());
        assertThat(userFilter.getValue().getSqlSegment()).contains("username LIKE", "OR", "real_name LIKE");
        assertThat(userFilter.getValue().getParamNameValuePairs().values()).containsExactly("%学员%", "%学员%");

        ArgumentCaptor<LambdaQueryWrapper<ClassMember>> memberFilter =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classMemberMapper).selectPage(any(Page.class), memberFilter.capture());
        assertThat(memberFilter.getValue().getSqlSegment()).contains("class_id =", "role_in_class =", "user_id IN");
        assertThat(memberFilter.getValue().getParamNameValuePairs().values())
                .containsExactlyInAnyOrder(CLASS_ID, "STUDENT", STUDENT_ID);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(member -> assertThat(member.getUserId()).isEqualTo(STUDENT_ID));
        verifyNoInteractions(examRecordMapper);
    }

    @Test
    void rejectsAddingGlobalTeacherAsStudent() {
        User teacher = user(TEACHER_ID);
        teacher.setRole("TEACHER");
        when(userMapper.selectById(TEACHER_ID)).thenReturn(teacher);

        assertThatIllegalArgumentException().isThrownBy(() -> service.addStudent(CLASS_ID, TEACHER_ID))
                .withMessageContaining("不是学员角色");
        verifyNoInteractions(classMemberMapper);
    }

    @Test
    void addingAnExistingStudentTwiceInsertsOnlyOneMembership() {
        User student = user(STUDENT_ID);
        student.setRole("STUDENT");
        when(userMapper.selectById(STUDENT_ID)).thenReturn(student);
        when(classMemberMapper.exists(any())).thenReturn(false, true);

        assertThat(service.addStudent(CLASS_ID, STUDENT_ID)).isFalse();
        assertThat(service.addStudent(CLASS_ID, STUDENT_ID)).isTrue();

        ArgumentCaptor<ClassMember> inserted = ArgumentCaptor.forClass(ClassMember.class);
        verify(classMemberMapper).insert(inserted.capture());
        assertThat(inserted.getValue()).usingRecursiveComparison()
                .isEqualTo(member(STUDENT_ID, "STUDENT"));
    }

    @Test
    void teacherAssignmentDeduplicatesIdsAndSkipsExistingMembership() {
        User teacher = user(TEACHER_ID);
        teacher.setRole("TEACHER");
        when(userMapper.selectBatchIds(List.of(TEACHER_ID))).thenReturn(List.of(teacher));
        when(classMemberMapper.selectList(any())).thenReturn(List.of(), List.of(member(TEACHER_ID, "TEACHER")));

        assertThat(service.assignTeachers(CLASS_ID, List.of(TEACHER_ID, TEACHER_ID)))
                .containsEntry("addedCount", 1).containsEntry("teacherIds", List.of(TEACHER_ID));
        assertThat(service.assignTeachers(CLASS_ID, List.of(TEACHER_ID)))
                .containsEntry("addedCount", 0).containsEntry("teacherIds", List.of());

        ArgumentCaptor<ClassMember> inserted = ArgumentCaptor.forClass(ClassMember.class);
        verify(classMemberMapper).insert(inserted.capture());
        assertThat(inserted.getValue()).usingRecursiveComparison()
                .isEqualTo(member(TEACHER_ID, "TEACHER"));
    }

    @Test
    void invalidTeacherInBatchPreventsAllAssignments() {
        User student = user(STUDENT_ID);
        student.setRole("STUDENT");
        User teacher = user(TEACHER_ID);
        teacher.setRole("TEACHER");
        List<Long> ids = List.of(TEACHER_ID, STUDENT_ID);
        when(userMapper.selectBatchIds(ids)).thenReturn(List.of(teacher, student));

        assertThatThrownBy(() -> service.assignTeachers(CLASS_ID, ids))
                .hasMessageContaining("用户 " + STUDENT_ID + " 不是教师角色");
        verifyNoInteractions(classMemberMapper);
    }

    @Test
    void removingAMemberTargetsOnlyTheRequestedClassUserAndRole() {
        service.removeMember(CLASS_ID, STUDENT_ID);
        service.removeTeacher(CLASS_ID, TEACHER_ID);

        ArgumentCaptor<LambdaQueryWrapper<ClassMember>> filter = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classMemberMapper, times(2)).delete(filter.capture());
        assertMembershipFilter(filter.getAllValues().get(0), STUDENT_ID, "STUDENT");
        assertMembershipFilter(filter.getAllValues().get(1), TEACHER_ID, "TEACHER");
        verifyNoInteractions(userMapper);
    }

    private static void assertMembershipFilter(LambdaQueryWrapper<ClassMember> filter, long userId, String role) {
        assertThat(filter.getSqlSegment()).contains("class_id", "user_id", "role_in_class");
        assertThat(filter.getParamNameValuePairs().values()).containsExactlyInAnyOrder(CLASS_ID, userId, role);
    }

    @Test
    void studentCannotReadClassmatesWithoutStudentMembership() {
        when(classMemberMapper.exists(any())).thenReturn(false);

        assertThatThrownBy(() -> service.listClassmates(CLASS_ID, STUDENT_ID))
                .isInstanceOf(ClassScopeException.class);

        verify(classMemberMapper).exists(any());
        verifyNoInteractions(userMapper);
    }

    private static ClassMember member(Long userId, String role) {
        ClassMember member = new ClassMember();
        member.setClassId(CLASS_ID);
        member.setUserId(userId);
        member.setRoleInClass(role);
        return member;
    }

    private static User user(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("user" + userId);
        user.setRole("STUDENT");
        return user;
    }
}
