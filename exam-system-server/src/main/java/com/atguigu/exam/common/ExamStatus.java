package com.atguigu.exam.common;

/**
 * 考试记录状态常量
 * 对应 exam_records.status 字段的取值，避免魔法字符串散落在代码各处
 */
public class ExamStatus {

    /** 进行中 */
    public static final String IN_PROGRESS = "进行中";

    /** 已结束（已交卷，未出分） */
    public static final String FINISHED = "已结束";

    /** 已批阅（已出分，前端结果页依据该状态判断是否需要触发AI判卷） */
    public static final String GRADED = "已批阅";

    private ExamStatus() {
    }
}
