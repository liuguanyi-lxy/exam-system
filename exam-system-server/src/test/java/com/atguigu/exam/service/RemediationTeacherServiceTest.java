package com.atguigu.exam.service;

import com.atguigu.exam.entity.CourseClass;
import com.atguigu.exam.entity.KnowledgePointVideo;
import com.atguigu.exam.entity.RemediationConfig;
import com.atguigu.exam.entity.Video;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.KnowledgePointMapper;
import com.atguigu.exam.mapper.KnowledgePointVideoMapper;
import com.atguigu.exam.mapper.RemediationConfigMapper;
import com.atguigu.exam.mapper.RemediationTaskMapper;
import com.atguigu.exam.mapper.VideoMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.vo.RemediationConfigVo;
import com.atguigu.exam.vo.RemediationTeacherView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M5 第 5 步教师/管理员侧单测：策略配置值校验与课程归属权限、映射维护
 * （存在性校验/空列表清空/去重保序/物理删除）、概览越权拒绝与聚合数字。
 * 全部 Mock，不连库。
 */
class RemediationTeacherServiceTest {
    private final RemediationConfigMapper configs = mock(RemediationConfigMapper.class);
    private final CourseClassMapper courseClasses = mock(CourseClassMapper.class);
    private final KnowledgePointVideoMapper kpVideos = mock(KnowledgePointVideoMapper.class);
    private final KnowledgePointMapper knowledgePoints = mock(KnowledgePointMapper.class);
    private final VideoMapper videos = mock(VideoMapper.class);
    private final RemediationTaskMapper tasks = mock(RemediationTaskMapper.class);
    private final ClassScopeService scopes = mock(ClassScopeService.class);
    private final RemediationTeacherService service = new RemediationTeacherService(
            configs, courseClasses, kpVideos, knowledgePoints, videos, tasks, scopes);

    private RemediationConfigVo body(Integer minMastery, Integer practice, Integer retest,
                                     Boolean force, Boolean skip, Integer dueDays) {
        var b = new RemediationConfigVo();
        b.setMinMastery(minMastery);
        b.setPracticeCount(practice);
        b.setRetestCount(retest);
        b.setForceRetest(force);
        b.setAllowSkipVideo(skip);
        b.setDueDays(dueDays);
        return b;
    }

    private RemediationConfigVo validBody() {
        return body(60, 5, 5, true, false, 14);
    }

    private CourseClass courseClass(Long classId) {
        var cc = new CourseClass();
        cc.setCourseId(3L);
        cc.setClassId(classId);
        return cc;
    }

    private Video video(long id, String title) {
        var v = new Video();
        v.setId(id);
        v.setTitle(title);
        return v;
    }

    // ========== 1. 课程级补课策略配置 ==========

    @Test
    void configValidationRejectsIllegalValues() {
        // minMastery 越界 / 缺省
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(3L, body(101, 5, 5, true, false, 14), 1L, "ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(3L, body(null, 5, 5, true, false, 14), 1L, "ADMIN"));
        // practiceCount / retestCount / dueDays 越界
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(3L, body(60, 0, 5, true, false, 14), 1L, "ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(3L, body(60, 5, 51, true, false, 14), 1L, "ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(3L, body(60, 5, 5, true, false, 366), 1L, "ADMIN"));
        // 布尔字段缺省与请求体缺失
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(3L, body(60, 5, 5, null, false, 14), 1L, "ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(3L, body(60, 5, 5, true, null, 14), 1L, "ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(3L, null, 1L, "ADMIN"));
        // 非法值不得触发任何写库
        verify(configs, never()).upsert(any());
    }

    @Test
    void configMessageCarriesFieldName() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.saveConfig(3L, body(101, 5, 5, true, false, 14), 1L, "ADMIN"));
        assertTrue(e.getMessage().contains("minMastery"));
        var e2 = assertThrows(IllegalArgumentException.class,
                () -> service.saveConfig(3L, body(60, 5, 5, true, false, 0), 1L, "ADMIN"));
        assertTrue(e2.getMessage().contains("dueDays"));
    }

    @Test
    void configRejectedWhenTeacherHasNoCourseClass() {
        // 教师带班与该课程分配班级无交集
        when(scopes.getTeacherClassIds(88L)).thenReturn(List.of(1L, 2L));
        when(courseClasses.selectList(any())).thenReturn(List.of(courseClass(9L), courseClass(10L)));
        var e = assertThrows(ClassScopeException.class,
                () -> service.saveConfig(3L, validBody(), 88L, "TEACHER"));
        assertTrue(e.getMessage().contains("无权配置该课程的补课策略"));
        // 教师无任何带班
        when(scopes.getTeacherClassIds(99L)).thenReturn(List.of());
        assertThrows(ClassScopeException.class, () -> service.saveConfig(3L, validBody(), 99L, "TEACHER"));
        verify(configs, never()).upsert(any());
    }

    @Test
    void configUpsertsForAdminAndScopedTeacher() {
        var saved = new RemediationConfig();
        saved.setCourseId(3L);
        saved.setMinMastery(60);
        saved.setPracticeCount(5);
        saved.setRetestCount(5);
        saved.setForceRetest((byte) 1);
        saved.setAllowSkipVideo((byte) 0);
        saved.setDueDays(14);
        when(configs.selectOne(any())).thenReturn(saved);

        // ADMIN 放行并 upsert
        RemediationConfig result = service.saveConfig(3L, validBody(), 1L, "ADMIN");
        ArgumentCaptor<RemediationConfig> captor = ArgumentCaptor.forClass(RemediationConfig.class);
        verify(configs).upsert(captor.capture());
        var written = captor.getValue();
        assertEquals(3L, written.getCourseId());
        assertEquals(60, written.getMinMastery());
        assertEquals(5, written.getPracticeCount());
        assertEquals(5, written.getRetestCount());
        assertEquals((byte) 1, written.getForceRetest());
        assertEquals((byte) 0, written.getAllowSkipVideo());
        assertEquals(14, written.getDueDays());
        assertSame(saved, result);

        // TEACHER 与该课程分配班级有交集：放行
        clearInvocations(configs);
        when(scopes.getTeacherClassIds(88L)).thenReturn(List.of(2L, 7L));
        when(courseClasses.selectList(any())).thenReturn(List.of(courseClass(7L)));
        service.saveConfig(3L, validBody(), 88L, "TEACHER");
        verify(configs).upsert(any(RemediationConfig.class));
    }

    // ========== 2. 知识点视频映射维护 ==========

    @Test
    void replaceVideosRejectsMissingPointOrVideo() {
        // 知识点不存在
        when(knowledgePoints.selectById(1L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.replacePointVideos(1L, List.of(101L), 44L));
        verifyNoInteractions(videos);
        verify(kpVideos, never()).deleteByKnowledgePointId(anyLong());

        // 视频缺失：报错带缺失 ID，整批不生效（不物理删除）
        when(knowledgePoints.selectById(1L)).thenReturn(new com.atguigu.exam.entity.KnowledgePoint());
        when(videos.selectBatchIds(any())).thenReturn(List.of(video(101L, "封装入门")));
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.replacePointVideos(1L, List.of(101L, 102L, 103L), 44L));
        assertTrue(e.getMessage().contains("102"));
        assertTrue(e.getMessage().contains("103"));
        verify(kpVideos, never()).deleteByKnowledgePointId(anyLong());
        verify(kpVideos, never()).insert(any(KnowledgePointVideo.class));
    }

    @Test
    void replaceVideosClearsAndReinsertsInOrder() {
        when(knowledgePoints.selectById(1L)).thenReturn(new com.atguigu.exam.entity.KnowledgePoint());
        when(videos.selectBatchIds(anyCollection())).thenReturn(
                List.of(video(101L, "封装入门"), video(102L, "继承进阶")));

        // 空列表 = 清空映射：仅物理删除，不插入
        assertTrue(service.replacePointVideos(1L, List.of(), 44L).isEmpty());
        verify(kpVideos).deleteByKnowledgePointId(1L);
        verify(kpVideos, never()).insert(any(KnowledgePointVideo.class));

        // null 请求体同样清空
        clearInvocations(kpVideos);
        assertTrue(service.replacePointVideos(1L, null, 44L).isEmpty());
        verify(kpVideos).deleteByKnowledgePointId(1L);

        // 正常替换：去重保序（101,102,101 → 101,102），sort 0..n，created_by=操作人
        clearInvocations(kpVideos);
        var items = service.replacePointVideos(1L, List.of(101L, 102L, 101L), 44L);
        verify(kpVideos).deleteByKnowledgePointId(1L);
        ArgumentCaptor<KnowledgePointVideo> captor = ArgumentCaptor.forClass(KnowledgePointVideo.class);
        verify(kpVideos, times(2)).insert(captor.capture());
        var first = captor.getAllValues().get(0);
        var second = captor.getAllValues().get(1);
        assertEquals(1L, first.getKnowledgePointId());
        assertEquals(101L, first.getVideoId());
        assertEquals(0, first.getSort());
        assertEquals(44L, first.getCreatedBy());
        assertEquals(102L, second.getVideoId());
        assertEquals(1, second.getSort());
        assertEquals(44L, second.getCreatedBy());
        assertEquals(2, items.size());
        assertEquals(101L, items.get(0).videoId());
        assertEquals("封装入门", items.get(0).title());
        assertEquals(0, items.get(0).sort());
        assertEquals(102L, items.get(1).videoId());
        assertEquals("继承进阶", items.get(1).title());
        assertEquals(1, items.get(1).sort());
    }

    // ========== 3. 班级补课进度概览 ==========

    @Test
    void overviewRejectsTeacherOutsideClass() {
        when(scopes.isTeacherOfClass(88L, 5L)).thenReturn(false);
        assertThrows(ClassScopeException.class, () -> service.overview(5L, 88L, "TEACHER"));
        verifyNoInteractions(tasks);
    }

    @Test
    void overviewAggregatesSummaryForAdminAndTeacher() {
        var rows = List.of(
                new RemediationTeacherView.StudentRow(11L, "张三", 3, 2, 1),
                new RemediationTeacherView.StudentRow(12L, "李四", 2, 1, 0));
        var points = List.of(
                new RemediationTeacherView.PointRow(1L, "面向对象", 3),
                new RemediationTeacherView.PointRow(2L, "封装", 2));
        when(tasks.overviewStudents(5L)).thenReturn(rows);
        when(tasks.overviewWeakPoints(5L)).thenReturn(points);

        // ADMIN 全量
        var admin = service.overview(5L, 1L, "ADMIN");
        assertEquals(5, admin.total());
        assertEquals(3, admin.completed());
        assertEquals(1, admin.overdue());
        assertEquals(2, admin.students().size());
        assertEquals("张三", admin.students().get(0).userName());
        assertEquals(11L, admin.students().get(0).userId());
        assertEquals(2, admin.weakPoints().size());
        assertEquals("面向对象", admin.weakPoints().get(0).knowledgePointName());
        assertEquals(3, admin.weakPoints().get(0).taskCount());
        verify(tasks).overviewStudents(5L);
        verify(tasks).overviewWeakPoints(5L);

        // TEACHER 带班放行，同一聚合口径
        clearInvocations(tasks);
        when(scopes.isTeacherOfClass(88L, 5L)).thenReturn(true);
        var teacher = service.overview(5L, 88L, "TEACHER");
        assertEquals(5, teacher.total());
        assertEquals(3, teacher.completed());
        assertEquals(1, teacher.overdue());
        assertEquals(2, teacher.students().size());
    }
}
