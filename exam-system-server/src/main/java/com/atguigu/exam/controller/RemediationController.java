package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.RemediationConfig;
import com.atguigu.exam.service.RemediationStepService;
import com.atguigu.exam.service.RemediationTaskService;
import com.atguigu.exam.service.RemediationTeacherService;
import com.atguigu.exam.vo.RemediationConfigVo;
import com.atguigu.exam.vo.RemediationStepView;
import com.atguigu.exam.vo.RemediationTaskView;
import com.atguigu.exam.vo.RemediationTeacherView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 补课任务链接口（M5 学员端）
 * 教师/管理员侧：详情可读（数据范围校验）；策略配置与进度概览见下方第 5 步接口。
 * 练习/变式/复测取题与提交仅学员本人（步骤服务内复用任务归属校验 + 视频门禁）。
 */
@RestController
@RequestMapping("/api/remediation")
@RequiredArgsConstructor
public class RemediationController {

    private final RemediationTaskService tasks;
    private final RemediationStepService steps;
    private final RemediationTeacherService teacher;

    /** 我的补课任务列表（可按状态筛选 IN_PROGRESS/COMPLETED；overdue 由 SQL 按 DB 时间计算） */
    @GetMapping("/tasks")
    @RequireRole("STUDENT")
    public Result<List<RemediationTaskView.Summary>> list(@RequestAttribute("userId") Long userId,
                                                          @RequestParam(required = false) String status) {
        return Result.success(tasks.list(userId, status));
    }

    /** 任务详情：四步状态 + 掌握度 + 快照策略 + 推荐视频（学员本人/带班教师/管理员） */
    @GetMapping("/tasks/{id}")
    @RequireRole({"STUDENT", "TEACHER", "ADMIN"})
    public Result<RemediationTaskView.Detail> detail(@PathVariable long id,
                                                     @RequestAttribute("userId") Long userId,
                                                     @RequestAttribute("userRole") String role) {
        return Result.success(tasks.detail(id, userId, role));
    }

    /** 幂等标记视频步骤完成：须完成全部映射视频；无映射视频自动放行（视同完成） */
    @PostMapping("/tasks/{id}/video/complete")
    @RequireRole("STUDENT")
    public Result<RemediationTaskView.VideoCompleteResult> completeVideo(@PathVariable long id,
                                                                         @RequestAttribute("userId") Long userId,
                                                                         @RequestAttribute("userRole") String role) {
        return Result.success(tasks.completeVideo(id, userId, role));
    }

    // ========== 练习/变式/复测 取题与提交（M5 第 4 步） ==========

    /** 基础练习取题（脱敏不下发答案解析）：当前轮余题直接返回，余题空则进入下一轮新选题 */
    @GetMapping("/tasks/{id}/practice")
    @RequireRole("STUDENT")
    public Result<RemediationStepView.PracticePaper> practice(@PathVariable long id,
                                                              @RequestAttribute("userId") Long userId,
                                                              @RequestAttribute("userRole") String role) {
        return Result.success(steps.paper(id, userId, role, RemediationStepService.STEP_PRACTICE));
    }

    /** 变式题取题：排除基础练习已用题，题池不足时回退全池并标 insufficient */
    @GetMapping("/tasks/{id}/variation")
    @RequireRole("STUDENT")
    public Result<RemediationStepView.PracticePaper> variation(@PathVariable long id,
                                                               @RequestAttribute("userId") Long userId,
                                                               @RequestAttribute("userRole") String role) {
        return Result.success(steps.paper(id, userId, role, RemediationStepService.STEP_VARIATION));
    }

    /** 复测取题：按快照 retestCount 从题池随机出题 */
    @GetMapping("/tasks/{id}/retest")
    @RequireRole("STUDENT")
    public Result<RemediationStepView.PracticePaper> retest(@PathVariable long id,
                                                            @RequestAttribute("userId") Long userId,
                                                            @RequestAttribute("userRole") String role) {
        return Result.success(steps.paper(id, userId, role, RemediationStepService.STEP_RETEST));
    }

    /** 基础练习批量提交：客观题本地比对 + 主观题一次 AI 判分，联动练习正确率与任务完成判定 */
    @PostMapping("/tasks/{id}/practice")
    @RequireRole("STUDENT")
    public Result<RemediationStepView.SubmitResult> submitPractice(@PathVariable long id,
                                                                   @RequestAttribute("userId") Long userId,
                                                                   @RequestAttribute("userRole") String role,
                                                                   @RequestBody RemediationStepView.SubmitRequest request) {
        return Result.success(steps.submit(id, userId, role, RemediationStepService.STEP_PRACTICE, request));
    }

    /** 变式题批量提交：判分口径同练习，联动练习正确率（不触发任务完成判定） */
    @PostMapping("/tasks/{id}/variation")
    @RequireRole("STUDENT")
    public Result<RemediationStepView.SubmitResult> submitVariation(@PathVariable long id,
                                                                    @RequestAttribute("userId") Long userId,
                                                                    @RequestAttribute("userRole") String role,
                                                                    @RequestBody RemediationStepView.SubmitRequest request) {
        return Result.success(steps.submit(id, userId, role, RemediationStepService.STEP_VARIATION, request));
    }

    /** 复测批量提交：score>=60 记正确，按折算掌握度对照快照阈值判定任务完成 */
    @PostMapping("/tasks/{id}/retest")
    @RequireRole("STUDENT")
    public Result<RemediationStepView.SubmitResult> submitRetest(@PathVariable long id,
                                                                 @RequestAttribute("userId") Long userId,
                                                                 @RequestAttribute("userRole") String role,
                                                                 @RequestBody RemediationStepView.SubmitRequest request) {
        return Result.success(steps.submit(id, userId, role, RemediationStepService.STEP_RETEST, request));
    }

    // ========== 教师/管理员侧（M5 第 5 步） ==========

    /** 课程级补课策略配置（整体替换 upsert）：ADMIN 全量；TEACHER 须带分配该课程的班级 */
    @PutMapping("/config/{courseId}")
    @RequireRole({"TEACHER", "ADMIN"})
    public Result<RemediationConfig> saveConfig(@PathVariable long courseId,
                                                @RequestBody RemediationConfigVo body,
                                                @RequestAttribute("userId") Long userId,
                                                @RequestAttribute("userRole") String role) {
        return Result.success(teacher.saveConfig(courseId, body, userId, role), "补课策略已保存");
    }

    /** 班级补课进度概览：汇总 + 学员明细 + 薄弱知识点排行（TEACHER 限带班） */
    @GetMapping("/overview")
    @RequireRole({"TEACHER", "ADMIN"})
    public Result<RemediationTeacherView.Overview> overview(@RequestParam long classId,
                                                            @RequestAttribute("userId") Long userId,
                                                            @RequestAttribute("userRole") String role) {
        return Result.success(teacher.overview(classId, userId, role));
    }
}
