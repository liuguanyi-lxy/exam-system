package com.atguigu.exam.service;

import com.atguigu.exam.vo.TeachingStatsVo;

/**
 * 教学统计服务（M6）：班级聚合统计 + 班级横向对比。
 *
 * 数据范围口径：TEACHER 仅负责班级（复用 ClassScopeService.isTeacherOfClass，越权抛 ClassScopeException→403）；
 * ADMIN 全量。对比接口：教师返回负责班级集合，管理员返回全部班级。
 */
public interface TeachingStatsService {

    /** 允许的活跃统计窗口（天） */
    int[] ALLOWED_DAYS = {7, 30};

    /** 班级教学统计聚合：一次返回概览/课程完成率/考试统计/未完成清单/高频错题/复核差异 */
    TeachingStatsVo stats(long classId, Integer days, Long userId, String role);

    /** 班级横向对比：各班均分/通过率/补课完成率 */
    TeachingStatsVo.CompareView compare(Long userId, String role);
}
