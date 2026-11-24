package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Course;
import com.atguigu.exam.entity.CourseChapter;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ChapterVideoMapper;
import com.atguigu.exam.mapper.CourseChapterMapper;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.CourseMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.vo.CourseRequestVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseCatalogServiceTest {
    @Mock private CourseMapper courseMapper;
    @Mock private CourseChapterMapper chapterMapper;
    @Mock private ChapterVideoMapper chapterVideoMapper;
    @Mock private CourseClassMapper courseClassMapper;
    @Mock private UserMapper userMapper;
    @Mock private ClassScopeService classScope;
    @InjectMocks private CourseCatalogService catalog;

    @Test
    void teacherScopeAndPageBoundsReachQuery() {
        when(classScope.getTeacherClassIds(7L)).thenReturn(List.of(3L, 4L));
        catalog.list(-5, 200, " Java ", "unknown", 7L, "TEACHER");
        verify(courseMapper).pageSummaries(argThat(page -> page.getCurrent() == 1 && page.getSize() == 100),
                eq(false), eq(7L), eq(List.of(3L, 4L)), eq("Java"), isNull());
    }

    @Test
    void mapperKeepsOwnerFilterOnEmptyTeacherScopeAndBindsAssignedIds() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(CourseMapper.class);
        var statement = configuration.getMappedStatement(CourseMapper.class.getName() + ".pageSummaries");
        var parameters = new HashMap<String, Object>();
        parameters.put("admin", false);
        parameters.put("userId", 7L);
        parameters.put("classIds", List.of());
        parameters.put("page", new Page<>(1, 10));
        var empty = statement.getBoundSql(parameters);
        assertTrue(empty.getSql().contains("c.created_by = ?"));
        assertTrue(empty.getSql().contains("AND 1 = 0"));
        assertFalse(empty.getSql().contains("OR EXISTS"));
        parameters.put("classIds", List.of(91L, 92L));
        var scoped = statement.getBoundSql(parameters);
        assertTrue(scoped.getSql().contains("OR EXISTS"));
        assertFalse(scoped.getSql().contains("91"));
        assertEquals(5, scoped.getParameterMappings().size());
        parameters.put("admin", true);
        assertTrue(statement.getBoundSql(parameters).getParameterMappings().isEmpty());
    }

    @Test
    void createAssignsAuthenticatedOwnerAndDefaultsToDraft() {
        when(courseMapper.insert(any(Course.class))).thenAnswer(call -> {
            Course course = call.getArgument(0);
            course.setId(18L);
            return 1;
        });
        CourseRequestVo input = new CourseRequestVo();
        input.setName(" Java ");
        var result = catalog.create(input, 7L, "TEACHER");
        assertEquals(18L, result.getId());
        assertEquals(7L, result.getCreatedBy());
        assertEquals("DRAFT", result.getStatus());
        assertEquals("Java", result.getName());
    }

    @Test
    void nonOwnerCannotUpdatePublishOrDelete() {
        Course course = new Course();
        course.setCreatedBy(9L);
        when(courseMapper.lockForAssignment(18L)).thenReturn(course);
        assertThrows(ClassScopeException.class, () -> catalog.update(18L, new CourseRequestVo(), 7L, "TEACHER"));
        assertThrows(ClassScopeException.class, () -> catalog.updateStatus(18L, "PUBLISHED", 7L, "TEACHER"));
        assertThrows(ClassScopeException.class, () -> catalog.delete(18L, 7L, "TEACHER"));
        verify(courseMapper, never()).update(any(), any());
        verify(courseMapper, never()).deleteById(anyLong());
        verifyNoInteractions(chapterMapper, chapterVideoMapper, courseClassMapper);
    }

    @Test
    void publishingIsIdempotentAndRejectsUnknownStatus() {
        Course course = new Course();
        course.setCreatedBy(7L);
        course.setStatus("PUBLISHED");
        when(courseMapper.lockForAssignment(18L)).thenReturn(course);
        catalog.updateStatus(18L, "PUBLISHED", 7L, "TEACHER");
        assertThrows(IllegalArgumentException.class, () -> catalog.updateStatus(18L, "invalid", 7L, "TEACHER"));
        verify(courseMapper, never()).update(any(), any());
        catalog.updateStatus(18L, "DRAFT", 7L, "TEACHER");
        verify(courseMapper).update(any(Course.class), any());
    }

    @Test
    void deletionCleansOnlyCourseStructureInOrder() {
        var configuration = new MybatisConfiguration();
        Arrays.asList(CourseChapterMapper.class, ChapterVideoMapper.class, CourseClassMapper.class)
                .forEach(configuration::addMapper);
        when(courseMapper.lockForAssignment(18L)).thenReturn(new Course());
        CourseChapter chapter = new CourseChapter();
        chapter.setId(31L);
        when(chapterMapper.selectList(any())).thenReturn(List.of(chapter));
        catalog.delete(18L, 1L, "ADMIN");
        var ordered = inOrder(courseMapper, chapterMapper, chapterVideoMapper, courseClassMapper);
        ordered.verify(courseMapper).lockForAssignment(18L);
        ordered.verify(chapterMapper).selectList(any());
        ordered.verify(chapterVideoMapper).delete(any());
        ordered.verify(chapterMapper).delete(any());
        ordered.verify(courseClassMapper).delete(any());
        ordered.verify(courseMapper).deleteById(18L);
    }
}
