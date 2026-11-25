package com.atguigu.exam.vo;

import java.util.List;

/**
 * 教师/管理员侧补课视图（M5 第 5 步）
 *
 * StudentRow/PointRow 由 mapper SQL 直接映射（与 RemediationTaskView.Summary 同口径，
 * arg-name 构造器映射 + 下划线转驼峰）；Overview 在 service 层聚合汇总数字。
 */
public final class RemediationTeacherView {

    private RemediationTeacherView() {
    }

    /** 知识点视频映射项（整体替换后回显；sort 按入参顺序 0..n） */
    public record KpVideoItem(long videoId, String title, int sort) {}

    /** 概览·学员明细行（SQL GROUP BY user_id；overdue 口径与 listForUser 一致，SQL 内按 DB 时间算） */
    public record StudentRow(Long userId, String userName, long total, long completed, long overdue) {}

    /** 概览·薄弱知识点排行行（按任务总数降序） */
    public record PointRow(Long knowledgePointId, String knowledgePointName, long taskCount) {}

    /** 班级补课进度概览：汇总数字由学员明细聚合得出（分组覆盖全部任务行） */
    public record Overview(long total, long completed, long overdue,
                           List<StudentRow> students, List<PointRow> weakPoints) {}
}
