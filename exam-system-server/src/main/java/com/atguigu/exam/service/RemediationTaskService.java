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
import com.atguigu.exam.vo.DiagnosticReport;
import com.atguigu.exam.vo.RemediationPolicy;
import com.atguigu.exam.vo.RemediationTaskView;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 补课任务链生成服务（M5）
 *
 * 报告就绪（状态 READY，即 confidence NORMAL）且薄弱知识点非空时，
 * 按薄弱知识点逐条生成任务链。幂等由唯一键 uk_task_user_kp_record 兜底：
 * 报告 retry 重算不重建任务；并发生成撞键时 INSERT IGNORE 静默跳过。
 * LIMITED 报告（证据不完整）不生成——与规格验收 1 一致。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemediationTaskService {

    private final RemediationTaskMapper tasks;
    private final RemediationConfigMapper configs;
    private final CourseClassMapper courseClasses;
    private final ObjectMapper json;
    private final MasteryScoreMapper masteryScores;
    private final KnowledgePointVideoMapper kpVideos;
    private final VideoMapper videos;
    private final LearningVideoProgressMapper videoProgress;
    private final ClassScopeService scopes;

    /**
     * 报告写完后生成任务链。返回新建任务条数（已存在的不计入）。
     * 失败时由调用方 catch 只记日志降级：报告状态已落库，retry 报告即可重建任务。
     */
    public int onReportReady(ExamRecord record, DiagnosticReport report) {
        if (record == null || record.getId() == null || record.getUserId() == null) return 0;
        if (report == null || "LIMITED".equals(report.confidence())
                || report.weakPoints() == null || report.weakPoints().isEmpty()) return 0;
        RemediationPolicy policy = resolvePolicy(record.getClassId());
        String snapshot;
        try {
            snapshot = json.writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("补课策略快照序列化失败", e);
        }
        int created = 0;
        for (DiagnosticReport.PointResult weak : report.weakPoints()) {
            created += tasks.insertIgnore(record.getId(), record.getUserId(), record.getClassId(),
                    weak.id(), snapshot, policy.dueDays());
        }
        return created;
    }

    /**
     * 策略归属（2026-11-25 用户拍板）：试卷/考试记录无课程关联，
     * 开考班级只分配一门课程时用该课程配置；多门课程或无课程用内置默认值。
     */
    RemediationPolicy resolvePolicy(Long classId) {
        if (classId == null) return RemediationPolicy.defaults();
        List<Long> courseIds = courseClasses.selectList(
                        new LambdaQueryWrapper<CourseClass>().eq(CourseClass::getClassId, classId))
                .stream().map(CourseClass::getCourseId).filter(Objects::nonNull).distinct().toList();
        if (courseIds.size() != 1) return RemediationPolicy.defaults();
        RemediationConfig config = configs.selectOne(new LambdaQueryWrapper<RemediationConfig>()
                .eq(RemediationConfig::getCourseId, courseIds.get(0)));
        if (config == null) return RemediationPolicy.defaults();
        return new RemediationPolicy(courseIds.get(0),
                intOf(config.getMinMastery(), RemediationPolicy.DEFAULT_MIN_MASTERY),
                intOf(config.getPracticeCount(), RemediationPolicy.DEFAULT_PRACTICE_COUNT),
                intOf(config.getRetestCount(), RemediationPolicy.DEFAULT_RETEST_COUNT),
                flagOf(config.getForceRetest(), RemediationPolicy.DEFAULT_FORCE_RETEST),
                flagOf(config.getAllowSkipVideo(), RemediationPolicy.DEFAULT_ALLOW_SKIP_VIDEO),
                intOf(config.getDueDays(), RemediationPolicy.DEFAULT_DUE_DAYS));
    }

    private static int intOf(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static boolean flagOf(Byte value, boolean fallback) {
        return value == null ? fallback : value != 0;
    }

    // ========== 学员任务链读接口与视频步骤状态机（m5-3） ==========

    /** 我的补课任务列表（status 可空；overdue 由 SQL 按 DB 时间计算） */
    public List<RemediationTaskView.Summary> list(Long userId, String status) {
        return tasks.listForUser(userId, status);
    }

    /**
     * 任务详情：四步状态 + 掌握度 + 生成时策略快照 + 推荐视频（含 M2 完成态）。
     * 步骤门禁口径：videoStepOpen = 无映射视频（自动放行，视同 videoDone=1）
     * || 全部映射视频 COMPLETED || 快照 allowSkipVideo。
     */
    public RemediationTaskView.Detail detail(long taskId, Long userId, String role) {
        RemediationTask task = requireTask(taskId, userId, role);
        RemediationTaskView.Summary summary = tasks.getSummaryById(taskId);
        if (summary == null) throw new ClassScopeException("任务不存在或无权访问");
        RemediationPolicy policy = parsePolicy(task);
        List<Long> videoIds = mappedVideoIds(task.getKnowledgePointId());
        Set<Long> completed = completedVideos(task.getUserId(), videoIds);
        List<RemediationTaskView.VideoItem> items = videoItems(videoIds, completed);
        boolean allCompleted = !videoIds.isEmpty() && completed.containsAll(videoIds);
        String videoState = videoIds.isEmpty() ? "NO_VIDEO" : allCompleted ? "DONE" : "TODO";
        boolean gateOpen = videoIds.isEmpty() || allCompleted || policy.allowSkipVideo();
        return new RemediationTaskView.Detail(summary.id(), summary.knowledgePointId(), summary.knowledgePointName(),
                summary.status(), summary.masteryTotal(), summary.pendingRetest(), summary.dueTime(), summary.overdue(),
                policy, new RemediationTaskView.Steps(videoState, gateOpen), items);
    }

    /**
     * 幂等标记视频步骤完成：有映射视频时须全部 COMPLETED；无映射视频自动放行（视同完成）。
     * 完成后原子 upsert 掌握度（video_done=1 并重算 total）；重复调用结果一致。
     */
    public RemediationTaskView.VideoCompleteResult completeVideo(long taskId, Long userId, String role) {
        RemediationTask task = requireTask(taskId, userId, role);
        if (!"STUDENT".equals(role) || !userId.equals(task.getUserId())) {
            throw new ClassScopeException("仅任务本人可标记视频完成");
        }
        List<Long> videoIds = mappedVideoIds(task.getKnowledgePointId());
        if (!videoIds.isEmpty() && !completedVideos(userId, videoIds).containsAll(videoIds)) {
            throw new IllegalArgumentException("仍有未完成的学习视频，暂不能标记完成");
        }
        masteryScores.upsertVideoDone(userId, task.getKnowledgePointId());
        MasteryScore after = masteryScores.selectOne(new LambdaQueryWrapper<MasteryScore>()
                .eq(MasteryScore::getUserId, userId)
                .eq(MasteryScore::getKnowledgePointId, task.getKnowledgePointId()));
        boolean pending = after == null || after.getPendingRetest() == null || after.getPendingRetest() != 0;
        return new RemediationTaskView.VideoCompleteResult(after == null ? null : after.getTotal(), pending);
    }

    /** 数据范围：学员仅本人；教师限带班（classId 快照）；管理员全量 */
    private RemediationTask requireTask(long taskId, Long userId, String role) {
        RemediationTask task = tasks.selectById(taskId);
        if (task == null || userId == null || role == null) throw new ClassScopeException("任务不存在或无权访问");
        switch (role) {
            case "ADMIN" -> { }
            case "TEACHER" -> {
                if (task.getClassId() == null || !scopes.isTeacherOfClass(userId, task.getClassId())) {
                    throw new ClassScopeException("无权访问该补课任务");
                }
            }
            case "STUDENT" -> {
                if (!userId.equals(task.getUserId())) throw new ClassScopeException("无权访问该补课任务");
            }
            default -> throw new ClassScopeException("无权访问该补课任务");
        }
        return task;
    }

    /**
     * 练习/变式/复测取题与提交的任务归属校验（RemediationStepService 共用）：
     * 在 requireTask 数据范围之上收紧为学员本人。
     */
    RemediationTask requireStudentTask(long taskId, Long userId, String role) {
        RemediationTask task = requireTask(taskId, userId, role);
        if (!"STUDENT".equals(role) || !userId.equals(task.getUserId())) {
            throw new ClassScopeException("仅任务本人可作答补课步骤");
        }
        return task;
    }

    /**
     * 视频门禁（与 detail 的 gateOpen 同一口径）：无映射视频 || 全部映射视频 COMPLETED || 快照 allowSkipVideo。
     * 练习/变式/复测取题与提交前共用，未放行由调用方拒绝。
     */
    boolean videoGateOpen(RemediationTask task, Long userId) {
        List<Long> videoIds = mappedVideoIds(task.getKnowledgePointId());
        if (videoIds.isEmpty()) return true;
        if (completedVideos(userId, videoIds).containsAll(videoIds)) return true;
        return parsePolicy(task).allowSkipVideo();
    }

    private List<Long> mappedVideoIds(Long pointId) {
        if (pointId == null) return List.of();
        return kpVideos.selectList(new LambdaQueryWrapper<KnowledgePointVideo>()
                        .eq(KnowledgePointVideo::getKnowledgePointId, pointId)
                        .orderByAsc(KnowledgePointVideo::getSort))
                .stream().map(KnowledgePointVideo::getVideoId).filter(Objects::nonNull).distinct().toList();
    }

    /** 复用 M2 视频完成口径：learning_video_progress.status = 'COMPLETED' */
    private Set<Long> completedVideos(Long userId, List<Long> videoIds) {
        if (userId == null || videoIds.isEmpty()) return Set.of();
        return videoProgress.selectList(new LambdaQueryWrapper<LearningVideoProgress>()
                        .eq(LearningVideoProgress::getUserId, userId)
                        .in(LearningVideoProgress::getVideoId, videoIds)
                        .eq(LearningVideoProgress::getStatus, "COMPLETED"))
                .stream().map(LearningVideoProgress::getVideoId).collect(Collectors.toSet());
    }

    private List<RemediationTaskView.VideoItem> videoItems(List<Long> videoIds, Set<Long> completed) {
        if (videoIds.isEmpty()) return List.of();
        Map<Long, String> titles = videos.selectBatchIds(videoIds).stream()
                .collect(Collectors.toMap(Video::getId,
                        v -> v.getTitle() == null ? "" : v.getTitle(), (a, b) -> a));
        return videoIds.stream()
                .map(id -> new RemediationTaskView.VideoItem(id, titles.getOrDefault(id, ""), completed.contains(id)))
                .toList();
    }

    /** 快照解析（任务链生成后 config_snapshot 不可变；取题/提交与门禁共用） */
    RemediationPolicy parsePolicy(RemediationTask task) {
        try {
            return json.readValue(task.getConfigSnapshot(), RemediationPolicy.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("补课策略快照无法解析", e);
        }
    }
}
