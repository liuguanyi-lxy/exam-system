package com.atguigu.exam.service;

import com.atguigu.exam.entity.CourseClass;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.KnowledgePointVideo;
import com.atguigu.exam.entity.LearningVideoProgress;
import com.atguigu.exam.entity.MasteryScore;
import com.atguigu.exam.entity.RemediationConfig;
import com.atguigu.exam.entity.RemediationTask;
import com.atguigu.exam.entity.Video;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.KnowledgePointVideoMapper;
import com.atguigu.exam.mapper.LearningVideoProgressMapper;
import com.atguigu.exam.mapper.MasteryScoreMapper;
import com.atguigu.exam.mapper.RemediationConfigMapper;
import com.atguigu.exam.mapper.RemediationTaskMapper;
import com.atguigu.exam.mapper.VideoMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.vo.DiagnosticReport;
import com.atguigu.exam.vo.RemediationPolicy;
import com.atguigu.exam.vo.RemediationTaskView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M5 任务生成/任务链读接口单测：LIMITED/无薄弱点跳过、逐薄弱点生成、报告 retry 幂等、
 * 班级单课程才生效的快照口径（2026-11-25 拍板）、视频步骤门禁与幂等标记、越权拒绝。
 * 全部 Mock，不连库。
 */
class RemediationTaskServiceTest {
    private final RemediationTaskMapper tasks = mock(RemediationTaskMapper.class);
    private final RemediationConfigMapper configs = mock(RemediationConfigMapper.class);
    private final CourseClassMapper courseClasses = mock(CourseClassMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final MasteryScoreMapper masteryScores = mock(MasteryScoreMapper.class);
    private final KnowledgePointVideoMapper kpVideos = mock(KnowledgePointVideoMapper.class);
    private final VideoMapper videos = mock(VideoMapper.class);
    private final LearningVideoProgressMapper videoProgress = mock(LearningVideoProgressMapper.class);
    private final ClassScopeService scopes = mock(ClassScopeService.class);
    private final RemediationTaskService service = new RemediationTaskService(tasks, configs, courseClasses, json,
            masteryScores, kpVideos, videos, videoProgress, scopes);

    private ExamRecord record(Long classId) {
        var r = new ExamRecord();
        r.setId(7L);
        r.setUserId(11L);
        r.setClassId(classId);
        return r;
    }

    private CourseClass courseClass(long courseId) {
        var cc = new CourseClass();
        cc.setCourseId(courseId);
        cc.setClassId(1L);
        return cc;
    }

    private RemediationConfig config(long courseId, Integer min, Integer practice, Integer retest,
                                     Byte force, Byte skip, Integer dueDays) {
        var c = new RemediationConfig();
        c.setCourseId(courseId);
        c.setMinMastery(min);
        c.setPracticeCount(practice);
        c.setRetestCount(retest);
        c.setForceRetest(force);
        c.setAllowSkipVideo(skip);
        c.setDueDays(dueDays);
        return c;
    }

    private DiagnosticReport report(String confidence, long... weakPointIds) {
        var weak = java.util.Arrays.stream(weakPointIds)
                .mapToObj(id -> new DiagnosticReport.PointResult(id, "知识点" + id, 0, BigDecimal.TEN, BigDecimal.ZERO))
                .toList();
        return new DiagnosticReport(60, BigDecimal.TEN, null, null, null, confidence,
                List.of(), List.of(), weak, List.of(), null, "test");
    }

    @Test
    void skipsLimitedEmptyWeakOrMissingIdentity() {
        assertEquals(0, service.onReportReady(record(1L), report("LIMITED", 1)));
        assertEquals(0, service.onReportReady(record(1L), report("NORMAL")));
        assertEquals(0, service.onReportReady(record(1L), null));
        var noUser = record(1L);
        noUser.setUserId(null);
        assertEquals(0, service.onReportReady(noUser, report("NORMAL", 1)));
        verifyNoInteractions(tasks);
    }

    @Test
    void generatesOneTaskPerWeakPointAndIsRetrySafe() throws Exception {
        when(courseClasses.selectList(any())).thenReturn(List.of(courseClass(3L)));
        when(configs.selectOne(any())).thenReturn(config(3L, 70, 4, 6, (byte) 1, (byte) 1, 10));
        when(tasks.insertIgnore(eq(7L), eq(11L), eq(1L), eq(1L), anyString(), eq(10))).thenReturn(1, 0);
        when(tasks.insertIgnore(eq(7L), eq(11L), eq(1L), eq(2L), anyString(), eq(10))).thenReturn(1, 0);

        assertEquals(2, service.onReportReady(record(1L), report("NORMAL", 1, 2)));
        // 报告 retry 重算：唯一键兜底已存在的任务不再新建
        assertEquals(0, service.onReportReady(record(1L), report("NORMAL", 1, 2)));

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(tasks, times(4)).insertIgnore(eq(7L), eq(11L), eq(1L), anyLong(), snapshot.capture(), eq(10));
        var policy = json.readValue(snapshot.getValue(), RemediationPolicy.class);
        assertEquals(3L, policy.courseId());
        assertEquals(70, policy.minMastery());
        assertEquals(4, policy.practiceCount());
        assertEquals(6, policy.retestCount());
        assertTrue(policy.forceRetest());
        assertTrue(policy.allowSkipVideo());
        assertEquals(10, policy.dueDays());
    }

    @Test
    void fallsBackToDefaultsWhenClassHasZeroOrMultipleCoursesOrNoConfig() throws Exception {
        // 无课程分配
        when(courseClasses.selectList(any())).thenReturn(List.of());
        service.onReportReady(record(1L), report("NORMAL", 1));
        assertDefaults();

        // 多门课程分配
        when(courseClasses.selectList(any())).thenReturn(List.of(courseClass(3L), courseClass(4L)));
        service.onReportReady(record(1L), report("NORMAL", 1));
        assertDefaults();

        // 无班学员
        service.onReportReady(record(null), report("NORMAL", 1));
        assertDefaults();

        // 单课程但未配置
        when(courseClasses.selectList(any())).thenReturn(List.of(courseClass(3L)));
        when(configs.selectOne(any())).thenReturn(null);
        service.onReportReady(record(1L), report("NORMAL", 1));
        assertDefaults();
    }

    private void assertDefaults() throws Exception {
        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(tasks, atLeastOnce()).insertIgnore(eq(7L), eq(11L), any(), eq(1L), snapshot.capture(), anyInt());
        var policy = json.readValue(snapshot.getValue(), RemediationPolicy.class);
        assertNull(policy.courseId());
        assertEquals(RemediationPolicy.DEFAULT_MIN_MASTERY, policy.minMastery());
        assertEquals(RemediationPolicy.DEFAULT_PRACTICE_COUNT, policy.practiceCount());
        assertEquals(RemediationPolicy.DEFAULT_RETEST_COUNT, policy.retestCount());
        assertTrue(policy.forceRetest());
        assertFalse(policy.allowSkipVideo());
        assertEquals(RemediationPolicy.DEFAULT_DUE_DAYS, policy.dueDays());
        clearInvocations(tasks);
    }

    // ========== m5-3 任务链读接口 / 视频步骤门禁 / 越权 ==========

    private RemediationTask task(Long classId, String snapshot) {
        var t = new RemediationTask();
        t.setId(9L);
        t.setExamRecordId(7L);
        t.setUserId(11L);
        t.setClassId(classId);
        t.setKnowledgePointId(1L);
        t.setStatus("IN_PROGRESS");
        t.setConfigSnapshot(snapshot);
        return t;
    }

    private String snapshot(boolean allowSkip) throws Exception {
        return json.writeValueAsString(new RemediationPolicy(3L, 60, 5, 5, true, allowSkip, 14));
    }

    private KnowledgePointVideo kpVideo(long videoId) {
        var kv = new KnowledgePointVideo();
        kv.setKnowledgePointId(1L);
        kv.setVideoId(videoId);
        kv.setSort(0);
        return kv;
    }

    private LearningVideoProgress progress(long videoId) {
        var p = new LearningVideoProgress();
        p.setUserId(11L);
        p.setVideoId(videoId);
        p.setStatus("COMPLETED");
        return p;
    }

    private Video video(long id, String title) {
        var v = new Video();
        v.setId(id);
        v.setTitle(title);
        return v;
    }

    private RemediationTaskView.Summary summary() {
        return new RemediationTaskView.Summary(9L, 1L, "面向对象", "IN_PROGRESS",
                new BigDecimal("52.00"), Boolean.TRUE, null, false);
    }

    @Test
    void detailVideoGateCoversNoMappingSkipAndCompletion() throws Exception {
        when(tasks.selectById(9L)).thenReturn(task(1L, snapshot(false)));
        when(tasks.getSummaryById(9L)).thenReturn(summary());

        // 无映射视频：NO_VIDEO 且放行（视同 videoDone=1）
        when(kpVideos.selectList(any())).thenReturn(List.of());
        var noVideo = service.detail(9L, 11L, "STUDENT");
        assertEquals("NO_VIDEO", noVideo.steps().video());
        assertTrue(noVideo.steps().gateOpen());
        assertTrue(noVideo.videos().isEmpty());
        assertEquals("面向对象", noVideo.knowledgePointName());

        // 有映射但未全部完成、快照不允许跳视频：TODO 且上锁
        when(kpVideos.selectList(any())).thenReturn(List.of(kpVideo(101L), kpVideo(102L)));
        when(videoProgress.selectList(any())).thenReturn(List.of(progress(101L)));
        when(videos.selectBatchIds(any())).thenReturn(List.of(video(101L, "封装入门"), video(102L, "继承进阶")));
        var locked = service.detail(9L, 11L, "STUDENT");
        assertEquals("TODO", locked.steps().video());
        assertFalse(locked.steps().gateOpen());
        assertTrue(locked.videos().get(0).completed());
        assertFalse(locked.videos().get(1).completed());
        assertEquals("封装入门", locked.videos().get(0).title());

        // 快照允许跳视频：仍 TODO 但放行
        when(tasks.selectById(9L)).thenReturn(task(1L, snapshot(true)));
        var skipped = service.detail(9L, 11L, "STUDENT");
        assertEquals("TODO", skipped.steps().video());
        assertTrue(skipped.steps().gateOpen());

        // 全部完成：DONE 且放行
        when(tasks.selectById(9L)).thenReturn(task(1L, snapshot(false)));
        when(videoProgress.selectList(any())).thenReturn(List.of(progress(101L), progress(102L)));
        var done = service.detail(9L, 11L, "STUDENT");
        assertEquals("DONE", done.steps().video());
        assertTrue(done.steps().gateOpen());
    }

    @Test
    void accessControlRejectsForeignTasksAndForeignClasses() throws Exception {
        when(tasks.selectById(9L)).thenReturn(task(1L, snapshot(false)));

        // 学员访问他人任务
        assertThrows(ClassScopeException.class, () -> service.detail(9L, 22L, "STUDENT"));
        // 教师非带班
        when(scopes.isTeacherOfClass(88L, 1L)).thenReturn(false);
        assertThrows(ClassScopeException.class, () -> service.detail(9L, 88L, "TEACHER"));

        // 带班教师可看详情，但不可标记视频完成
        when(scopes.isTeacherOfClass(88L, 1L)).thenReturn(true);
        when(tasks.getSummaryById(9L)).thenReturn(summary());
        when(kpVideos.selectList(any())).thenReturn(List.of());
        assertEquals(9L, service.detail(9L, 88L, "TEACHER").id());
        assertThrows(ClassScopeException.class, () -> service.completeVideo(9L, 88L, "TEACHER"));
        verifyNoInteractions(masteryScores);
    }

    @Test
    void completeVideoRequiresAllVideosAndIsIdempotent() throws Exception {
        when(tasks.selectById(9L)).thenReturn(task(1L, snapshot(false)));

        // 有映射未全部完成：拒绝标记
        when(kpVideos.selectList(any())).thenReturn(List.of(kpVideo(101L), kpVideo(102L)));
        when(videoProgress.selectList(any())).thenReturn(List.of(progress(101L)));
        assertThrows(IllegalArgumentException.class, () -> service.completeVideo(9L, 11L, "STUDENT"));
        verifyNoInteractions(masteryScores);

        // 全部完成：原子 upsert 标记并返回最新掌握度
        when(videoProgress.selectList(any())).thenReturn(List.of(progress(101L), progress(102L)));
        var after = new MasteryScore();
        after.setTotal(new BigDecimal("80.00"));
        after.setPendingRetest((byte) 1);
        when(masteryScores.selectOne(any())).thenReturn(after);
        var result = service.completeVideo(9L, 11L, "STUDENT");
        verify(masteryScores).upsertVideoDone(11L, 1L);
        assertEquals(new BigDecimal("80.00"), result.masteryTotal());
        assertTrue(result.pendingRetest());

        // 无映射视频：自动放行（视同完成），同样走幂等标记
        clearInvocations(masteryScores);
        when(kpVideos.selectList(any())).thenReturn(List.of());
        service.completeVideo(9L, 11L, "STUDENT");
        verify(masteryScores).upsertVideoDone(11L, 1L);
    }
}
