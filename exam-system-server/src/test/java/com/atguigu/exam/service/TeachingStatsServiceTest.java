package com.atguigu.exam.service;

import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.TeachingStatsMapper;
import com.atguigu.exam.service.impl.TeachingStatsServiceImpl;
import com.atguigu.exam.vo.TeachingStatsVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M6 教学统计服务单测（全 Mock，不连库）：
 * days 校验、数据范围（教师越权拒绝/管理员放行）、口径计算
 * （完成率/参与率/通过率/分布补段/进步 delta/错误率/知识点归组/复核透传）、对比班级集合。
 */
class TeachingStatsServiceTest {

    private static final long TEACHER_ID = 88L;
    private static final long CLASS_ID = 5L;

    private final TeachingStatsMapper mapper = mock(TeachingStatsMapper.class);
    private final ClassScopeService scopes = mock(ClassScopeService.class);
    private final TeachingStatsServiceImpl service = new TeachingStatsServiceImpl(mapper, scopes);

    private void stubClassInfo() {
        when(mapper.classInfo(CLASS_ID)).thenReturn(new TeachingStatsVo.ClassInfo(CLASS_ID, "M5验证班", 2));
    }

    // ========== 1. 参数与数据范围 ==========

    @Test
    void daysOnlyAllowsSevenOrThirty() {
        stubClassInfo();
        stubAllAggregatesEmpty();
        assertThrows(IllegalArgumentException.class,
                () -> service.stats(CLASS_ID, 8, 1L, "ADMIN"));
        assertThrows(IllegalArgumentException.class,
                () -> service.stats(CLASS_ID, null, 1L, "ADMIN"));
        assertEquals(7, service.stats(CLASS_ID, 7, 1L, "ADMIN").active().days());
        assertEquals(30, service.stats(CLASS_ID, 30, 1L, "ADMIN").active().days());
    }

    @Test
    void teacherOutsideClassIsRejected() {
        when(scopes.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(false);
        assertThrows(ClassScopeException.class, () -> service.stats(CLASS_ID, 7, TEACHER_ID, "TEACHER"));
        // 越权班级不泄露存在性：任何聚合查询都不发起
        verifyNoInteractions(mapper);
    }

    @Test
    void missingClassIsRejectedForAdmin() {
        when(mapper.classInfo(CLASS_ID)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.stats(CLASS_ID, 7, 1L, "ADMIN"));
        verify(mapper).classInfo(CLASS_ID);
    }

    // ========== 2. 聚合口径 ==========

    @Test
    void statsAssemblesRatesAndDeltasForAdmin() {
        stubClassInfo();
        when(mapper.activeCount(CLASS_ID, 7)).thenReturn(1L);
        when(mapper.courseLearning(CLASS_ID)).thenReturn(List.of(
                new TeachingStatsVo.CourseLearningBase(3L, "M5验证课程", 3, 1)));
        when(mapper.examStats(CLASS_ID)).thenReturn(List.of(
                new TeachingStatsVo.ExamStatBase(1L, "期中考试", 60, 100, 2, 1, 1, 45.5, 1L)));
        when(mapper.examDistribution(CLASS_ID)).thenReturn(List.of(
                new TeachingStatsVo.DistributionRow(1L, "0-59", 1L)));
        when(mapper.examProgress(CLASS_ID)).thenReturn(List.of(
                new TeachingStatsVo.ProgressBase(1L, 4L, "王小明", 25, 20)));
        when(mapper.examAbsentees(CLASS_ID)).thenReturn(List.of(
                new TeachingStatsVo.ExamAbsentee(1L, "期中考试", 5L, "赵六")));
        when(mapper.overdueTasks(CLASS_ID)).thenReturn(List.of(
                new TeachingStatsVo.OverdueTask(4L, "王小明", "面向对象",
                        LocalDateTime.of(2026, 11, 20, 0, 0))));
        when(mapper.topWrongQuestions(CLASS_ID)).thenReturn(List.of(
                new TeachingStatsVo.WrongQuestionBase(1004L, "封装题", 2, 3, 5)));
        when(mapper.questionKnowledgePoints(List.of(1004L))).thenReturn(List.of(
                new TeachingStatsVo.KpName(1004L, "面向对象")));
        when(mapper.reviewStats(CLASS_ID)).thenReturn(
                new TeachingStatsVo.ReviewStats(3, 1, 1, 2));

        TeachingStatsVo vo = service.stats(CLASS_ID, 7, 1L, "ADMIN");

        assertEquals("M5验证班", vo.classInfo().className());
        assertEquals(2, vo.classInfo().studentCount());
        assertEquals(1, vo.active().activeCount());
        // 课程完成率 1/3 → 33（向下取整）
        assertEquals(33, vo.learning().get(0).completionRate());
        // 考试：参与率 1/2 → 50；通过率 1/1 → 100；均分透传
        TeachingStatsVo.ExamStat exam = vo.exams().get(0);
        assertEquals(50, exam.participationRate());
        assertEquals(100, exam.passRate());
        assertEquals(45.5, exam.avgScore());
        // 分布补齐五段固定顺序，0-59 有 1 人，其余 0
        assertEquals(5, exam.distribution().size());
        assertEquals("0-59", exam.distribution().get(0).range());
        assertEquals(1, exam.distribution().get(0).count());
        assertEquals(0, exam.distribution().get(1).count());
        assertEquals("90-100", exam.distribution().get(4).range());
        // 进步 delta = 最近 − 首次 = 20 − 25 = −5
        assertEquals(-5, exam.progress().get(0).delta());
        assertEquals(25, exam.progress().get(0).firstScore());
        assertEquals(20, exam.progress().get(0).latestScore());
        // 未完成清单透传
        assertEquals(1, vo.uncompleted().examAbsentees().size());
        assertEquals(1, vo.uncompleted().overdueTasks().size());
        assertEquals("面向对象", vo.uncompleted().overdueTasks().get(0).knowledgePoint());
        // 高频错题：错误率 2/3 → 66（向下取整），知识点归组
        TeachingStatsVo.WrongQuestion wrong = vo.topWrongQuestions().get(0);
        assertEquals(66, wrong.wrongRate());
        assertEquals(5, wrong.chatCount());
        assertEquals(List.of("面向对象"), wrong.knowledgePoints());
        // 复核差异透传
        assertEquals(3, vo.reviewStats().reportTotal());
        assertEquals(1, vo.reviewStats().lowConfidenceCount());
        assertEquals(1, vo.reviewStats().reviewedCount());
        assertEquals(2, vo.reviewStats().reviewNoteCount());
    }

    @Test
    void teacherInClassGetsSameAssembly() {
        when(scopes.isTeacherOfClass(TEACHER_ID, CLASS_ID)).thenReturn(true);
        stubClassInfo();
        stubAllAggregatesEmpty();

        TeachingStatsVo vo = service.stats(CLASS_ID, 7, TEACHER_ID, "TEACHER");

        assertEquals("M5验证班", vo.classInfo().className());
        assertTrue(vo.exams().isEmpty());
        assertTrue(vo.topWrongQuestions().isEmpty());
        verify(mapper).activeCount(CLASS_ID, 7);
    }

    // ========== 3. 班级对比 ==========

    @Test
    void compareUsesTeacherClassesForTeacher() {
        when(scopes.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of(CLASS_ID, 6L));
        when(mapper.compareClasses(List.of(CLASS_ID, 6L))).thenReturn(List.of(
                new TeachingStatsVo.CompareItem(CLASS_ID, "M5验证班", 45.0, 50, 100),
                new TeachingStatsVo.CompareItem(6L, "二班", null, 0, 0)));

        var view = service.compare(TEACHER_ID, "TEACHER");

        verify(mapper).compareClasses(List.of(CLASS_ID, 6L));
        assertEquals(2, view.items().size());
        assertEquals(50, view.items().get(0).passRate());
        assertEquals(100, view.items().get(0).remediationCompletionRate());
    }

    @Test
    void compareUsesAllClassesForAdmin() {
        when(mapper.allClassIds()).thenReturn(List.of(CLASS_ID));
        when(mapper.compareClasses(List.of(CLASS_ID))).thenReturn(List.of(
                new TeachingStatsVo.CompareItem(CLASS_ID, "M5验证班", 45.0, 50, 100)));

        var view = service.compare(1L, "ADMIN");

        verify(mapper).allClassIds();
        assertEquals(1, view.items().size());
    }

    @Test
    void compareReturnsEmptyWithoutQueryWhenTeacherHasNoClass() {
        when(scopes.getTeacherClassIds(TEACHER_ID)).thenReturn(List.of());

        assertTrue(service.compare(TEACHER_ID, "TEACHER").items().isEmpty());

        verifyNoInteractions(mapper);
    }

    // ========== 造数辅助 ==========

    private void stubAllAggregatesEmpty() {
        when(mapper.courseLearning(CLASS_ID)).thenReturn(List.of());
        when(mapper.examStats(CLASS_ID)).thenReturn(List.of());
        when(mapper.examAbsentees(CLASS_ID)).thenReturn(List.of());
        when(mapper.overdueTasks(CLASS_ID)).thenReturn(List.of());
        when(mapper.topWrongQuestions(CLASS_ID)).thenReturn(List.of());
        when(mapper.reviewStats(CLASS_ID)).thenReturn(new TeachingStatsVo.ReviewStats(0, 0, 0, 0));
    }
}
