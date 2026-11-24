package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.Course;
import com.atguigu.exam.entity.CourseClass;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.CourseMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CourseAssignmentServiceTest {
    private static final long COURSE_ID = 7L;
    private static final long TEACHER_ID = 3L;
    private static final long CLASS_ID = 42L;
    private static final long OTHER_CLASS_ID = 43L;

    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final CourseClassMapper assignmentMapper = mock(CourseClassMapper.class);
    private final ClazzMapper clazzMapper = mock(ClazzMapper.class);
    private final ClassMemberMapper memberMapper = mock(ClassMemberMapper.class);
    private final ClassScopeService scopeService = mock(ClassScopeService.class);
    private final CourseAssignmentService service =
            new CourseAssignmentService(courseMapper, assignmentMapper, clazzMapper, memberMapper, scopeService);

    @BeforeAll
    static void initColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "course-assignment-test"),
                CourseClass.class);
    }

    @Test
    void teacherCannotAssignUnmanagedClass() {
        when(courseMapper.lockForAssignment(COURSE_ID)).thenReturn(course(TEACHER_ID));
        when(scopeService.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of(CLASS_ID));

        assertThatThrownBy(() -> service.replaceAssignments(
                COURSE_ID, List.of(CLASS_ID, OTHER_CLASS_ID), TEACHER_ID, "TEACHER"))
                .isInstanceOf(ClassScopeException.class);
        verifyNoInteractions(assignmentMapper, clazzMapper);
    }

    @Test
    void teacherCannotEditAnotherTeachersCourse() {
        when(courseMapper.lockForAssignment(COURSE_ID)).thenReturn(course(TEACHER_ID + 1));

        assertThatThrownBy(() -> service.replaceAssignments(COURSE_ID, List.of(), TEACHER_ID, "TEACHER"))
                .isInstanceOf(ClassScopeException.class);
        verifyNoInteractions(assignmentMapper, scopeService);
    }

    @Test
    void teacherClearsOnlyAssignmentsToTheirOwnClasses() {
        when(courseMapper.lockForAssignment(COURSE_ID)).thenReturn(course(TEACHER_ID));
        when(scopeService.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of(CLASS_ID));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment(CLASS_ID)));

        assertThat(service.replaceAssignments(COURSE_ID, List.of(), TEACHER_ID, "TEACHER")).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<CourseClass>> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(assignmentMapper).selectList(wrapper.capture());
        verify(assignmentMapper).delete(wrapper.capture());
        for (LambdaQueryWrapper<CourseClass> condition : wrapper.getAllValues()) {
            assertThat(condition.getSqlSegment()).contains("course_id =", "class_id IN");
            assertThat(condition.getParamNameValuePairs().values()).containsExactlyInAnyOrder(COURSE_ID, CLASS_ID);
        }
        verify(assignmentMapper, never()).insert(any(CourseClass.class));
    }

    @Test
    void unchangedAssignmentSetDoesNotRewriteRows() {
        when(courseMapper.lockForAssignment(COURSE_ID)).thenReturn(course(TEACHER_ID));
        Clazz clazz = new Clazz();
        clazz.setId(CLASS_ID);
        when(clazzMapper.selectBatchIds(Set.of(CLASS_ID))).thenReturn(List.of(clazz));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment(CLASS_ID)));

        assertThat(service.replaceAssignments(COURSE_ID, List.of(CLASS_ID, CLASS_ID), 1L, "ADMIN"))
                .containsExactly(CLASS_ID);
        verify(assignmentMapper, never()).insert(any(CourseClass.class));
        verify(assignmentMapper, never()).delete(any());
    }

    @Test
    void teacherCanAddMissingAssignmentToManagedClass() {
        when(courseMapper.lockForAssignment(COURSE_ID)).thenReturn(course(TEACHER_ID));
        when(scopeService.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of(CLASS_ID));
        Clazz clazz = new Clazz();
        clazz.setId(CLASS_ID);
        when(clazzMapper.selectBatchIds(Set.of(CLASS_ID))).thenReturn(List.of(clazz));
        when(assignmentMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.replaceAssignments(COURSE_ID, List.of(CLASS_ID), TEACHER_ID, "TEACHER"))
                .containsExactly(CLASS_ID);
        verify(assignmentMapper).insert(assignment(CLASS_ID));
        verify(assignmentMapper, never()).delete(any());
    }

    @Test
    void teacherWithEmptyScopeDoesNotQueryAssignments() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course(TEACHER_ID));
        when(scopeService.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of());

        assertThat(service.getAssignments(COURSE_ID, TEACHER_ID, "TEACHER")).containsEntry("classIds", List.of());
        verifyNoInteractions(assignmentMapper, clazzMapper, memberMapper);
    }

    @Test
    void assignmentLookupUsesParametersAndEmptyScopeMatchesNothing() {
        MybatisConfiguration config = new MybatisConfiguration();
        config.addMapper(CourseClassMapper.class);
        var statement = config.getMappedStatement(CourseClassMapper.class.getName() + ".selectAssignedCourseTimes");

        BoundSql lookup = statement.getBoundSql(Map.of("classIds", List.of(CLASS_ID, OTHER_CLASS_ID)));
        assertThat(lookup.getSql()).contains("cc.class_id IN", "?").doesNotContain("42", "43");
        assertThat(lookup.getParameterMappings()).hasSize(2);
        assertThat(statement.getBoundSql(Map.of("classIds", List.of())).getSql()).contains("1 = 0");
    }

    private static Course course(long ownerId) {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setCreatedBy(ownerId);
        return course;
    }

    private static CourseClass assignment(long classId) {
        CourseClass assignment = new CourseClass();
        assignment.setCourseId(COURSE_ID);
        assignment.setClassId(classId);
        return assignment;
    }
}
