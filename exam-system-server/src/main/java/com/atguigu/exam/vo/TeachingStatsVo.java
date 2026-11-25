package com.atguigu.exam.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教学统计视图（M6）
 *
 * SQL 行 record 由 mapper 直接映射（arg-name 构造器映射 + 下划线转驼峰，与 M5 同口径）；
 * 比率类字段（completionRate/participationRate/passRate/wrongRate/delta）在 service 层计算。
 * 响应结构见 docs/design-m6-teaching-stats.md 第 1 节。
 */
public record TeachingStatsVo(
        ClassInfo classInfo,
        Active active,
        List<CourseLearning> learning,
        List<ExamStat> exams,
        Uncompleted uncompleted,
        List<WrongQuestion> topWrongQuestions,
        ReviewStats reviewStats) {

    /** 班级基本信息卡：学员数 = class_members role_in_class='STUDENT' 且 users 未删除 */
    public record ClassInfo(long classId, String className, long studentCount) {}

    /** 活跃概览：近 days 天内有任一学习行为（考试/视频心跳/补课提交）的去重学员数 */
    public record Active(int days, long activeCount) {}

    /** 课程完成率 SQL 行（无 completionRate，service 组装） */
    public record CourseLearningBase(long courseId, String courseName, long videoTotal, long completedTotal) {}

    /** 课程学习完成率：视频总数 = chapter_videos 链接数；完成 = 班内学员 COMPLETED（video 口径去重） */
    public record CourseLearning(long courseId, String courseName, long videoTotal,
                                 long completedTotal, int completionRate) {}

    /** 单场分配考试统计：参与率 = 去重交卷学员 ÷ 班级学员；通过率 = 通过学员 ÷ 已批阅学员 */
    public record ExamStat(long examId, String examName, Integer passScore, Integer totalScore,
                           long classStudentCount, long participantCount, long gradedCount,
                           int participationRate, Double avgScore, int passRate,
                           List<Distribution> distribution, List<Progress> progress) {}

    /** 分数段分布（按 exam.total_score 归一为百分比后分 0-59/60-69/70-79/80-89/90-100） */
    public record Distribution(String range, long count) {}

    /** 分数段 SQL 行（examId+段名 → 计数），service 按 examId 归组后转 Distribution */
    public record DistributionRow(long examId, String range, long cnt) {}

    /** 单场考试统计 SQL 行（无 distribution/progress，service 组装） */
    public record ExamStatBase(long examId, String examName, Integer passScore, Integer totalScore,
                               long classStudentCount, long participantCount, long gradedCount,
                               Double avgScore, long passCount) {}

    /** 高频错题 SQL 行（无知识点列表，service 组装） */
    public record WrongQuestionBase(long questionId, String content, long wrongCount,
                                    long totalAttempts, long chatCount) {}

    /** 题目知识点 SQL 行 */
    public record KpName(long questionId, String name) {}

    /** 多次尝试进步 SQL 行（无 delta，service 计算） */
    public record ProgressBase(long examId, long userId, String realName,
                               Integer firstScore, Integer latestScore) {}

    /** 多次尝试进步行：delta = 最近一次得分 − 首次得分，正负区分 */
    public record Progress(long userId, String realName, Integer firstScore,
                           Integer latestScore, int delta) {}

    /** 未完成清单：缺考名单 + 逾期补课任务名单（两个 Tab 展示） */
    public record Uncompleted(List<ExamAbsentee> examAbsentees, List<OverdueTask> overdueTasks) {}

    /** 分配考试窗口已结束仍未交卷的学员 */
    public record ExamAbsentee(long examId, String examName, long userId, String realName) {}

    /** 补课任务已逾期未完成（status=IN_PROGRESS 且 due_time < DB 当前时间） */
    public record OverdueTask(long userId, String realName, String knowledgePoint, LocalDateTime dueTime) {}

    /** 高频错题 TOP：错误率 = 该题该班作答总次数中错误占比；chatCount = 班级学员对该题追问轮数 */
    public record WrongQuestion(long questionId, String content, long wrongCount, int wrongRate,
                                long chatCount, List<String> knowledgePoints) {}

    /** AI 与人工复核差异（报告级）：低置信度 = report_json.confidence ∈ (LIMITED, DEGRADED) */
    public record ReviewStats(long reportTotal, long lowConfidenceCount, long reviewedCount, long reviewNoteCount) {}

    /** 班级对比项：均分/通过率（已批阅口径）/补课完成率，比率 0-100 整数 */
    public record CompareItem(long classId, String className, Double avgScore,
                              int passRate, int remediationCompletionRate) {}

    /** 班级对比响应：教师 = 负责班级集合，管理员 = 全部班级 */
    public record CompareView(List<CompareItem> items) {}
}
