package com.atguigu.exam.vo;

/**
 * 补课策略快照（M5）
 *
 * 任务生成时从课程级配置解析后快照进 remediation_tasks.config_snapshot，
 * 后续修改课程配置不影响已生成任务（规格验收 8）。
 * courseId 为空表示走了内置默认值（班级未分配课程或多门课程时的口径，
 * 2026-11-25 用户拍板：班级单课程才生效）。
 */
public record RemediationPolicy(Long courseId, int minMastery, int practiceCount, int retestCount,
                                boolean forceRetest, boolean allowSkipVideo, int dueDays) {

    public static final int DEFAULT_MIN_MASTERY = 60;
    public static final int DEFAULT_PRACTICE_COUNT = 5;
    public static final int DEFAULT_RETEST_COUNT = 5;
    public static final boolean DEFAULT_FORCE_RETEST = true;
    public static final boolean DEFAULT_ALLOW_SKIP_VIDEO = false;
    public static final int DEFAULT_DUE_DAYS = 14;

    public static RemediationPolicy defaults() {
        return new RemediationPolicy(null, DEFAULT_MIN_MASTERY, DEFAULT_PRACTICE_COUNT, DEFAULT_RETEST_COUNT,
                DEFAULT_FORCE_RETEST, DEFAULT_ALLOW_SKIP_VIDEO, DEFAULT_DUE_DAYS);
    }
}
