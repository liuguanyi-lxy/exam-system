package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ChapterVideo;
import com.atguigu.exam.entity.Course;
import com.atguigu.exam.entity.CourseChapter;
import com.atguigu.exam.entity.LearningVideoProgress;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ChapterVideoMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.CourseChapterMapper;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.CourseMapper;
import com.atguigu.exam.mapper.LearningVideoProgressMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.mapper.VideoMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ExternalVideoPolicy;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseLearningQueryServiceTest {
    @Mock private CourseMapper courseMapper;
    @Mock private CourseClassMapper assignments;
    @Mock private CourseChapterMapper chapterMapper;
    @Mock private ChapterVideoMapper linkMapper;
    @Mock private LearningVideoProgressMapper progressMapper;
    @Mock private VideoMapper videoMapper;
    @Mock private UserMapper userMapper;
    @Mock private ClazzMapper clazzMapper;
    @Mock private ClassScopeService classScope;
    @Mock private StudentCourseAccess access;
    @Mock private ExternalVideoPolicy externalPolicy;
    @InjectMocks private CourseLearningQueryService service;

    @BeforeAll
    static void initializeMapperMetadata() {
        new MybatisConfiguration().addMapper(LearningVideoProgressMapper.class);
    }

    @Test
    void noClassMeansNoAssignmentsAndNoProgressQueries() {
        when(classScope.getStudentClassIds(1L)).thenReturn(List.of());
        assertTrue(service.list(1L).isEmpty());
        verifyNoInteractions(assignments, courseMapper, progressMapper);
    }

    @Test
    void unpublishedAndDisabledClassCannotUseStudentDetailAccess() {
        var scope = new StudentCourseAccess(courseMapper, assignments);
        var course = new Course();
        course.setStatus("DRAFT");
        when(courseMapper.selectById(40L)).thenReturn(course);
        assertThrows(ClassScopeException.class, () -> scope.requirePublishedAssignment(40L, 1L));
        verifyNoInteractions(assignments);
        course.setStatus("PUBLISHED");
        assertThrows(ClassScopeException.class, () -> scope.requirePublishedAssignment(40L, 1L));
        verify(assignments).hasActiveStudentAssignment(40L, 1L);
    }

    @Test
    void directScopeQueryRequiresEnabledClassAndStudentMembership() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(CourseClassMapper.class);
        var statement = configuration.getMappedStatement(CourseClassMapper.class.getName() + ".hasActiveStudentAssignment");
        var sql = statement.getBoundSql(Map.of("courseId", 40L, "userId", 1L));
        assertTrue(sql.getSql().contains("c.is_deleted = 0 AND c.status = 'ENABLED'"));
        assertTrue(sql.getSql().contains("cm.role_in_class = 'STUDENT'"));
        assertEquals(List.of("courseId", "userId"), sql.getParameterMappings().stream().map(item -> item.getProperty()).toList());
    }

    @Test
    void detailUsesOnlyOwnProgressAndIgnoresOptionalVideoForCompletion() {
        when(access.requirePublishedAssignment(40L, 1L)).thenReturn(new Course());
        var first = chapter(10L);
        var second = chapter(11L);
        when(chapterMapper.selectList(any())).thenReturn(List.of(first, second));
        when(linkMapper.selectList(any())).thenReturn(List.of(link(10L, 100L, true),
                link(10L, 101L, false), link(11L, 200L, true)));
        when(progressMapper.selectList(any())).thenAnswer(call -> {
            LambdaQueryWrapper<LearningVideoProgress> scope = call.getArgument(0);
            assertTrue(scope.getSqlSegment().contains("user_id IN"));
            assertTrue(scope.getParamNameValuePairs().containsValue(1L));
            var learned = new LearningVideoProgress();
            learned.setUserId(1L);
            learned.setVideoId(100L);
            learned.setStatus("COMPLETED");
            return List.of(learned);
        });
        var result = service.detail(40L, 1L);
        assertEquals(50, result.getProgressPercent());
        assertTrue(result.getChapters().get(0).getCompleted());
        assertFalse(result.getChapters().get(1).getCompleted());
    }

    @Test
    void teacherCannotReadUnrelatedClassProgress() {
        assertThrows(ClassScopeException.class, () -> service.classProgress(40L, 4L, 3L, "TEACHER"));
        verifyNoInteractions(assignments, clazzMapper, progressMapper);
    }

    @Test
    void chapterCannotLeakAcrossCourses() {
        var unrelated = chapter(10L);
        unrelated.setCourseId(50L);
        when(chapterMapper.selectById(10L)).thenReturn(unrelated);
        assertThrows(IllegalArgumentException.class, () -> service.chapter(40L, 10L, 1L));
        verifyNoInteractions(linkMapper, videoMapper, progressMapper);
    }

    private CourseChapter chapter(Long id) {
        var chapter = new CourseChapter();
        chapter.setId(id);
        chapter.setCourseId(40L);
        return chapter;
    }

    private ChapterVideo link(Long chapterId, Long videoId, boolean required) {
        var link = new ChapterVideo();
        link.setChapterId(chapterId);
        link.setVideoId(videoId);
        link.setRequired(required);
        return link;
    }
}
