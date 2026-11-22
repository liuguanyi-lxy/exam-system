package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.WrongQuestionService;
import com.atguigu.exam.vo.WrongQuestionVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题本控制器
 *
 * 学生个人学习数据接口：路径不出现 userId，归属一律取 AuthInterceptor 写入的登录上下文。
 * 契约限定仅 STUDENT 可访问（教师/管理员由 @RequireRole 统一返回 403）。
 */
@RestController
@RequestMapping("/api/wrong-questions")
@Tag(name = "错题本", description = "学生本人历史考试错题聚合、标记已掌握与取消已掌握")
@RequireRole({"STUDENT"})
@Slf4j
public class WrongQuestionController {

    @Autowired
    private WrongQuestionService wrongQuestionService;

    /**
     * 分页查询错题本（按题目聚合，最近答错的排前面）
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询错题本",
            description = "只统计本人 is_correct=0 的答题记录，按题目聚合；默认返回待复习的题，masteredOnly=true 时只返回已掌握的题")
    public Result<Page<WrongQuestionVo>> list(
            @Parameter(description = "当前页码，从1开始；超过10000按10000处理", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量；超过100按100处理", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "题型筛选：CHOICE/JUDGE/TEXT/CODE，非法值忽略", example = "CHOICE")
            @RequestParam(required = false) String type,
            @Parameter(description = "题目分类ID，非数字或不存在时忽略", example = "1")
            @RequestParam(required = false) String categoryId,
            @Parameter(description = "难度筛选：EASY/MEDIUM/HARD，非法值忽略", example = "MEDIUM")
            @RequestParam(required = false) String difficulty,
            @Parameter(description = "是否同时返回已掌握的题，默认 false；masteredOnly=true 时忽略本参数", example = "false")
            @RequestParam(defaultValue = "false") Boolean includeMastered,
            @Parameter(description = "是否只返回已掌握的题，默认 false（待复习视图）；true 时服务端过滤并分页", example = "false")
            @RequestParam(defaultValue = "false") Boolean masteredOnly,
            @Parameter(hidden = true) @RequestAttribute("userId") Long userId) {
        Page<WrongQuestionVo> result = wrongQuestionService.pageWrongQuestions(
                userId, page, size, type, difficulty, categoryId, includeMastered, masteredOnly);
        log.info("查询错题本列表：用户{}，第{}页，每页{}条，masteredOnly={}", userId, page, size, masteredOnly);
        return Result.success(result);
    }

    /**
     * 标记已掌握（移出错题本），幂等
     */
    @PostMapping("/{questionId}/mastered")
    @Operation(summary = "标记错题已掌握",
            description = "标记后该题移出错题本默认列表；重复调用幂等成功")
    public Result<Void> markMastered(
            @Parameter(description = "题目ID", example = "5") @PathVariable Long questionId,
            @Parameter(hidden = true) @RequestAttribute("userId") Long userId) {
        wrongQuestionService.markMastered(userId, questionId);
        log.info("用户{}标记错题{}已掌握", userId, questionId);
        return Result.<Void>success("操作成功");
    }

    /**
     * 取消已掌握（恢复进错题本），幂等
     */
    @DeleteMapping("/{questionId}/mastered")
    @Operation(summary = "取消错题已掌握",
            description = "删除已掌握标记，该题重新进入错题本；重复调用幂等成功")
    public Result<Void> unmarkMastered(
            @Parameter(description = "题目ID", example = "5") @PathVariable Long questionId,
            @Parameter(hidden = true) @RequestAttribute("userId") Long userId) {
        wrongQuestionService.unmarkMastered(userId, questionId);
        log.info("用户{}取消错题{}已掌握标记", userId, questionId);
        return Result.<Void>success("操作成功");
    }
}
