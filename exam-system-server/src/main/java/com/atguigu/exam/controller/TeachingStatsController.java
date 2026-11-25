package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.TeachingStatsService;
import com.atguigu.exam.vo.TeachingStatsVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教学统计接口（M6）
 *
 * 统一"教学统计"页聚合数据源：
 * - GET /api/teaching-stats?classId=&days=：单班聚合一次返回（概览/课程完成率/考试统计/未完成/高频错题/复核差异）；
 * - GET /api/teaching-stats/compare：班级对比（均分/通过率/补课完成率），教师限负责班级、管理员全量。
 * 薄弱知识点排行不在此实现：前端同屏内嵌复用 GET /api/remediation/overview（M5）。
 */
@RestController
@RequestMapping("/api/teaching-stats")
@RequiredArgsConstructor
public class TeachingStatsController {

    private final TeachingStatsService service;

    /** 班级教学统计聚合（days 默认 7，仅允许 7/30；TEACHER 限负责班级，越权 403） */
    @GetMapping
    @RequireRole({"TEACHER", "ADMIN"})
    public Result<TeachingStatsVo> stats(@RequestParam long classId,
                                         @RequestParam(defaultValue = "7") Integer days,
                                         @RequestAttribute("userId") Long userId,
                                         @RequestAttribute("userRole") String role) {
        return Result.success(service.stats(classId, days, userId, role));
    }

    /** 班级横向对比（教师 = 负责班级集合，管理员 = 全部班级） */
    @GetMapping("/compare")
    @RequireRole({"TEACHER", "ADMIN"})
    public Result<TeachingStatsVo.CompareView> compare(@RequestAttribute("userId") Long userId,
                                                       @RequestAttribute("userRole") String role) {
        return Result.success(service.compare(userId, role));
    }
}
