package com.atguigu.exam.service.impl;

import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.TeachingStatsMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.TeachingStatsService;
import com.atguigu.exam.vo.TeachingStatsVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学统计服务实现（M6）：全部实时聚合（TeachingStatsMapper），零新表。
 *
 * 组装口径：
 * - 比率统一整数百分比（向下取整，100L*x/y，与 M2 percent 口径一致），分母为 0 时记 0；
 * - 分数段固定输出 0-59/60-69/70-79/80-89/90-100 五段（无数据的段补 0）；
 * - 进步 delta = 最近一次得分 − 首次得分（正负区分）；
 * - 错误率 = 该题该班作答总次数中错误占比；知识点按题目归组。
 */
@Service
@RequiredArgsConstructor
public class TeachingStatsServiceImpl implements TeachingStatsService {

    /** 分数段（升序展示，含边界） */
    private static final String[] RANGES = {"0-59", "60-69", "70-79", "80-89", "90-100"};

    private final TeachingStatsMapper mapper;
    private final ClassScopeService scopes;

    @Override
    public TeachingStatsVo stats(long classId, Integer days, Long userId, String role) {
        int window = requireDays(days);
        // 先范围校验再做班级存在性校验：对无权班级不泄露存在性细节（M5 requireCourseScope 同口径）
        if (!"ADMIN".equals(role) && !scopes.isTeacherOfClass(userId, classId)) {
            throw new ClassScopeException("无权查看该班级的教学统计");
        }
        TeachingStatsVo.ClassInfo classInfo = mapper.classInfo(classId);
        if (classInfo == null) {
            throw new IllegalArgumentException("班级不存在");
        }

        List<TeachingStatsVo.CourseLearning> learning = mapper.courseLearning(classId).stream()
                .map(row -> new TeachingStatsVo.CourseLearning(row.courseId(), row.courseName(),
                        row.videoTotal(), row.completedTotal(),
                        percent(row.completedTotal(), row.videoTotal())))
                .toList();

        List<TeachingStatsVo.ExamStat> exams = assembleExams(classId);

        TeachingStatsVo.Uncompleted uncompleted = new TeachingStatsVo.Uncompleted(
                mapper.examAbsentees(classId), mapper.overdueTasks(classId));

        List<TeachingStatsVo.WrongQuestion> wrongQuestions = assembleWrongQuestions(classId);

        return new TeachingStatsVo(classInfo,
                new TeachingStatsVo.Active(window, mapper.activeCount(classId, window)),
                learning, exams, uncompleted, wrongQuestions, mapper.reviewStats(classId));
    }

    @Override
    public TeachingStatsVo.CompareView compare(Long userId, String role) {
        List<Long> classIds = "ADMIN".equals(role)
                ? mapper.allClassIds()
                : scopes.getTeacherClassIds(userId);
        // 空集合哨兵：教师无负责班级（或异常角色）直接空结果，禁止空 IN 直传
        if (classIds == null || classIds.isEmpty()) {
            return new TeachingStatsVo.CompareView(List.of());
        }
        return new TeachingStatsVo.CompareView(mapper.compareClasses(classIds));
    }

    // ========== 组装 ==========

    /** 考试统计：基础行 + 分布（按 examId 归组，补齐五段）+ 进步（按 examId 归组） */
    private List<TeachingStatsVo.ExamStat> assembleExams(long classId) {
        List<TeachingStatsVo.ExamStatBase> bases = mapper.examStats(classId);
        if (bases.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TeachingStatsVo.Distribution>> distributions = new LinkedHashMap<>();
        for (TeachingStatsVo.DistributionRow row : mapper.examDistribution(classId)) {
            distributions.computeIfAbsent(row.examId(), k -> new ArrayList<>())
                    .add(new TeachingStatsVo.Distribution(row.range(), row.cnt()));
        }
        Map<Long, List<TeachingStatsVo.Progress>> progresses = new LinkedHashMap<>();
        for (TeachingStatsVo.ProgressBase row : mapper.examProgress(classId)) {
            Integer first = row.firstScore();
            Integer latest = row.latestScore();
            progresses.computeIfAbsent(row.examId(), k -> new ArrayList<>())
                    .add(new TeachingStatsVo.Progress(row.userId(), row.realName(), first, latest,
                            first == null || latest == null ? 0 : latest - first));
        }
        List<TeachingStatsVo.ExamStat> exams = new ArrayList<>(bases.size());
        for (TeachingStatsVo.ExamStatBase base : bases) {
            long graded = base.gradedCount();
            exams.add(new TeachingStatsVo.ExamStat(base.examId(), base.examName(), base.passScore(),
                    base.totalScore(), base.classStudentCount(), base.participantCount(), graded,
                    percent(base.participantCount(), base.classStudentCount()), base.avgScore(),
                    percent(base.passCount(), graded),
                    fillRanges(distributions.getOrDefault(base.examId(), List.of())),
                    progresses.getOrDefault(base.examId(), List.of())));
        }
        return exams;
    }

    /** 五段固定顺序输出，缺省段补 0 */
    private static List<TeachingStatsVo.Distribution> fillRanges(List<TeachingStatsVo.Distribution> rows) {
        Map<String, Long> byRange = new LinkedHashMap<>();
        for (TeachingStatsVo.Distribution row : rows) {
            byRange.merge(row.range(), row.count(), Long::sum);
        }
        List<TeachingStatsVo.Distribution> filled = new ArrayList<>(RANGES.length);
        for (String range : RANGES) {
            filled.add(new TeachingStatsVo.Distribution(range, byRange.getOrDefault(range, 0L)));
        }
        return filled;
    }

    /** 高频错题：错误率 = 错误次数 ÷ 该班该题作答总次数；知识点按题目归组 */
    private List<TeachingStatsVo.WrongQuestion> assembleWrongQuestions(long classId) {
        List<TeachingStatsVo.WrongQuestionBase> bases = mapper.topWrongQuestions(classId);
        if (bases.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = bases.stream().map(TeachingStatsVo.WrongQuestionBase::questionId).toList();
        Map<Long, List<String>> points = new LinkedHashMap<>();
        for (TeachingStatsVo.KpName row : mapper.questionKnowledgePoints(questionIds)) {
            points.computeIfAbsent(row.questionId(), k -> new ArrayList<>()).add(row.name());
        }
        return bases.stream().map(base -> new TeachingStatsVo.WrongQuestion(
                base.questionId(), base.content(), base.wrongCount(),
                percent(base.wrongCount(), base.totalAttempts()), base.chatCount(),
                points.getOrDefault(base.questionId(), List.of()))).toList();
    }

    // ========== 校验与工具 ==========

    /** 活跃窗口仅允许 7/30（规格默认决策） */
    private static int requireDays(Integer days) {
        if (days == null || Arrays.stream(ALLOWED_DAYS).noneMatch(d -> d == days)) {
            throw new IllegalArgumentException("days 仅支持 7 或 30");
        }
        return days;
    }

    /** 整数百分比（向下取整），分母为 0 时记 0 */
    private static int percent(long numerator, long denominator) {
        return denominator <= 0 ? 0 : (int) (100L * numerator / denominator);
    }
}
