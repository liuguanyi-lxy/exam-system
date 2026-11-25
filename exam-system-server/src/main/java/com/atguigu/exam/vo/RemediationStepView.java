package com.atguigu.exam.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 补课步骤（练习/变式/复测）取题与提交视图（M5）
 *
 * 脱敏口径：SanitizedQuestion 绝不下发 answer/analysis/选项 isCorrect，
 * 学生端唯一例外场景仍是错题本（与本视图无关）。
 */
public final class RemediationStepView {

    /** 取题试卷：roundNo 当前轮次；insufficient=true 表示变式题池不足已回退全池 */
    public record PracticePaper(String step, int roundNo, boolean insufficient, List<SanitizedQuestion> questions) {}

    /** 脱敏题目：仅题干/类型/多选标记与选项内容（不含 isCorrect），绝无 answer/analysis */
    public record SanitizedQuestion(long id, String title, String type, Boolean multi, List<Choice> choices) {}

    /** 选项脱敏视图 */
    public record Choice(String content, Integer sort) {}

    /** 批量提交请求 */
    public record SubmitRequest(List<Answer> answers) {}

    /** 单题作答 */
    public record Answer(Long questionId, String answer) {}

    /** 每题判分结果：correct 为 null 表示 AI 未判分（可稍后重做）；绝不下发答案/解析 */
    public record QuestionResult(long questionId, Boolean correct, String feedback) {}

    /** 本轮统计：accuracy = 正确占比 * 100（0-100 整数） */
    public record Round(int correctCount, int totalCount, int accuracy) {}

    /** 提交结果：taskCompleted=true 任务链已判完成；masteryTotal 为本次联动后的掌握度 */
    public record SubmitResult(String step, int roundNo, List<QuestionResult> results,
                               Round round, boolean taskCompleted, BigDecimal masteryTotal) {}
}
