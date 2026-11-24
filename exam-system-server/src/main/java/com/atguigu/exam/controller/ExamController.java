package com.atguigu.exam.controller;


import com.atguigu.exam.common.Result;
import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.ExamRecordScope;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 考试控制器 - 处理考试流程相关的HTTP请求
 * 包括开始考试、提交答案、AI批阅、成绩查询等功能
 */
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/exams")  // 考试API路径前缀
@Slf4j
@Tag(name = "考试管理", description = "考试流程相关操作，包括开始考试、答题提交、AI批阅、成绩查询等功能")  // Swagger API分组
public class ExamController {

    @Autowired
    private ExamService examService;
    @Autowired
    private ExamRecordService examRecordService;
    @Autowired
    private ClassScopeService classScopeService;
    /**
     * 点击开始考试 - 创建新的考试记录
     * @param startExamVo 开始考试请求DTO
     * @return 考试记录
     */
    @PostMapping("/start")  // 处理POST请求
    @RequireRole("STUDENT")
    @Operation(summary = "开始考试", description = "学生开始考试，创建考试记录（归属当前登录用户），前端再调用查询试卷详情接口才会并返回试卷内容")  // API描述
    public Result<ExamRecord> startExam(@Valid @RequestBody StartExamVo startExamVo,
            @RequestAttribute("userId") Long userId) {
        ExamRecord examRecord =  examService.startExam(startExamVo, userId);
        log.info("开始考试，考试对象创建成功！{}",examRecord);
        return Result.success(examRecord, "考试开始成功");
    }

    /**
     * 提交答案 - 学生提交考试答案
     * @param examRecordId 考试记录ID，开始考试后，后端会返回一个初步的考试记录对象，调用提交考试答案接口再将该考试记录的ID作为参数传入到后端，对指定的答题记录和考试记录的数据进行添加修改
     *                     其中考试记录和题目的关联表进行数据添加，标志某个考试记录中的某个题目的答题情况
     * @param answers      答题列表，前端会记录学生答题的数据，然后序列化后用SubmitAnswerVo对象接收
     */
    @PostMapping("/{examRecordId}/submit")  // 处理POST请求
    @Operation(summary = "提交考试答案", description = "学生提交考试答案，校验记录归属、进行中状态与考试时间后结束考试；AI判卷由结果页调用 /grade 接口触发")  // API描述
    public Result<Void> submitAnswers(
            @Parameter(description = "考试记录ID") @PathVariable Integer examRecordId,
            @RequestBody List<SubmitAnswerVo> answers,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("userRole") String role) {
            if ("TEACHER".equals(role)) {
                ExamRecord record = examRecordService.getById(examRecordId);
                if (record != null) ExamRecordScope.requireTeacherRecord(classScopeService, userId, record);
            }
            examService.customSubmitAnswer(examRecordId, answers, userId, isTeacherOrAdmin(role));
            log.info("提交答案接口调用成功！");
            return Result.success("答案提交成功");
    }

    /**
     * AI自动批阅 - 触发试卷智能批阅
     * @param examRecordId 考试记录ID
     */
    @PostMapping("/{examRecordId}/grade")  // 处理POST请求
    @Operation(summary = "AI自动批阅", description = "使用AI技术自动批阅试卷，特别是简答题的智能评分,返回批阅后的考试记录；已批阅的记录直接返回不重复判卷；仅考生本人或教师/管理员可触发")  // API描述
    public Result<ExamRecord> gradeExam(
            @Parameter(description = "考试记录ID") @PathVariable Integer examRecordId,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("userRole") String role) {
             if ("TEACHER".equals(role)) {
                 ExamRecord record = examRecordService.getById(examRecordId);
                 if (record != null) ExamRecordScope.requireTeacherRecord(classScopeService, userId, record);
             }
             ExamRecord examRecord = examService.gradeExam(examRecordId, userId, isTeacherOrAdmin(role));
             return Result.success(examRecord, "试卷批阅完成");
    }

    /** 当前请求角色是否教师/管理员 */
    private boolean isTeacherOrAdmin(String role) {
        return "ADMIN".equals(role) || "TEACHER".equals(role);
    }

}
