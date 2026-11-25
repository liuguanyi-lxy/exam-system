package com.atguigu.exam.service;

import com.atguigu.exam.entity.CourseClass;
import com.atguigu.exam.entity.KnowledgePoint;
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
import com.atguigu.exam.vo.RemediationConfigVo;
import com.atguigu.exam.vo.RemediationTeacherView;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教师/管理员侧补课服务（M5 第 5 步）：课程级策略配置、知识点视频映射维护、班级进度概览。
 *
 * 权限口径：
 * - 策略配置：ADMIN 全量；TEACHER 须是至少一个"分配了该课程"的班级的教师
 *   （course_classes ∩ class_members(role_in_class='TEACHER') 取交集）；
 * - 映射维护：角色由 @RequireRole 挡（TEACHER/ADMIN），口径同 QuestionController 题目标注；
 * - 进度概览：ADMIN 全量；TEACHER 须 isTeacherOfClass（复用 ExamRecordController 数据范围模式）。
 */
@Service
@RequiredArgsConstructor
public class RemediationTeacherService {

    private final RemediationConfigMapper configs;
    private final CourseClassMapper courseClasses;
    private final KnowledgePointVideoMapper kpVideos;
    private final KnowledgePointMapper knowledgePoints;
    private final VideoMapper videos;
    private final RemediationTaskMapper tasks;
    private final ClassScopeService scopes;

    // ========== 课程级补课策略配置 ==========

    /**
     * 整体替换保存课程级策略（upsert，唯一键 uk_course_config 兜底），返回落库后的完整配置。
     * 先做课程归属校验再做值校验：对无权课程不泄露任何校验细节。
     */
    public RemediationConfig saveConfig(long courseId, RemediationConfigVo body, Long userId, String role) {
        requireCourseScope(courseId, userId, role);
        validateConfig(body);
        RemediationConfig config = new RemediationConfig();
        config.setCourseId(courseId);
        config.setMinMastery(body.getMinMastery());
        config.setPracticeCount(body.getPracticeCount());
        config.setRetestCount(body.getRetestCount());
        config.setForceRetest((byte) (body.getForceRetest() ? 1 : 0));
        config.setAllowSkipVideo((byte) (body.getAllowSkipVideo() ? 1 : 0));
        config.setDueDays(body.getDueDays());
        configs.upsert(config);
        return configs.selectOne(new LambdaQueryWrapper<RemediationConfig>()
                .eq(RemediationConfig::getCourseId, courseId));
    }

    /** ADMIN 全量；TEACHER 须带分配该课程的班级，否则越权（交集为空即拒绝） */
    private void requireCourseScope(long courseId, Long userId, String role) {
        if ("ADMIN".equals(role)) return;
        Set<Long> teacherClassIds = new HashSet<>(scopes.getTeacherClassIds(userId));
        boolean owns = courseClasses.selectList(new LambdaQueryWrapper<CourseClass>()
                        .eq(CourseClass::getCourseId, courseId))
                .stream().map(CourseClass::getClassId).filter(Objects::nonNull)
                .anyMatch(teacherClassIds::contains);
        if (!owns) throw new ClassScopeException("无权配置该课程的补课策略");
    }

    /** 整体替换语义：全部必填；非法值 IllegalArgumentException 带字段名 */
    private static void validateConfig(RemediationConfigVo body) {
        if (body == null) throw new IllegalArgumentException("配置内容不能为空");
        requireRange(body.getMinMastery(), 0, 100, "minMastery");
        requireRange(body.getPracticeCount(), 1, 50, "practiceCount");
        requireRange(body.getRetestCount(), 1, 50, "retestCount");
        require(body.getForceRetest() != null, "forceRetest 必填（布尔）");
        require(body.getAllowSkipVideo() != null, "allowSkipVideo 必填（布尔）");
        requireRange(body.getDueDays(), 1, 365, "dueDays");
    }

    private static void requireRange(Integer value, int min, int max, String field) {
        require(value != null && value >= min && value <= max, field + " 须为 " + min + "-" + max + " 的整数");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    // ========== 知识点视频映射维护 ==========

    /**
     * 整体替换知识点视频映射：videoIds 去重保序、可空（清空）；
     * 任一视频不存在整批拒绝（事务回滚）；替换 = 物理删除旧映射 + 重插（sort 按入参顺序 0..n）。
     * 口径同 QuestionController.setQuestionKnowledgePoints（TEACHER/ADMIN，空集合清空）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<RemediationTeacherView.KpVideoItem> replacePointVideos(long pointId, List<Long> videoIds,
                                                                       Long operatorId) {
        if (knowledgePoints.selectById(pointId) == null) {
            throw new IllegalArgumentException("知识点不存在");
        }
        // 去重保序（null 元素剔除）；null 请求体视为清空全部映射
        List<Long> distinct = videoIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(videoIds.stream().filter(Objects::nonNull).toList()));
        Map<Long, Video> videoMap = distinct.isEmpty() ? Map.of()
                : videos.selectBatchIds(distinct).stream()
                        .collect(Collectors.toMap(Video::getId, Function.identity(), (a, b) -> a));
        List<Long> missing = distinct.stream().filter(id -> !videoMap.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("视频不存在：" + missing);
        }
        // 必须物理删除：@TableLogic 的逻辑删除行仍占唯一键 uk_kp_video，重插同对必撞 DuplicateKey
        kpVideos.deleteByKnowledgePointId(pointId);
        List<RemediationTeacherView.KpVideoItem> items = new ArrayList<>();
        int sort = 0;
        for (Long videoId : distinct) {
            KnowledgePointVideo link = new KnowledgePointVideo();
            link.setKnowledgePointId(pointId);
            link.setVideoId(videoId);
            link.setSort(sort);
            link.setCreatedBy(operatorId);
            kpVideos.insert(link);
            Video video = videoMap.get(videoId);
            items.add(new RemediationTeacherView.KpVideoItem(videoId,
                    video.getTitle() == null ? "" : video.getTitle(), sort));
            sort++;
        }
        return items;
    }

    // ========== 班级补课进度概览 ==========

    /**
     * 班级补课进度概览：汇总（总数/已完成/已逾期，由学员明细聚合）+ 学员明细 + 薄弱知识点排行。
     * overdue 口径与 listForUser 一致，SQL 内按 DB 时间计算。
     */
    public RemediationTeacherView.Overview overview(long classId, Long userId, String role) {
        if (!"ADMIN".equals(role) && !scopes.isTeacherOfClass(userId, classId)) {
            throw new ClassScopeException("无权查看该班级的补课进度概览");
        }
        List<RemediationTeacherView.StudentRow> students = tasks.overviewStudents(classId);
        List<RemediationTeacherView.PointRow> weakPoints = tasks.overviewWeakPoints(classId);
        long total = 0;
        long completed = 0;
        long overdue = 0;
        for (RemediationTeacherView.StudentRow row : students) {
            total += row.total();
            completed += row.completed();
            overdue += row.overdue();
        }
        return new RemediationTeacherView.Overview(total, completed, overdue, students, weakPoints);
    }
}
