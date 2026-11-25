package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.service.AiService;
import com.atguigu.exam.vo.BatchGradingItemVo;
import com.atguigu.exam.vo.GradingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 补课步骤判分组件（M5）
 *
 * 客观题（CHOICE/JUDGE）：复用 ExamServiceImpl 的包级答案比对 isObjectiveAnswerCorrect
 * （该类未依赖任何补课服务，注入具体类无循环依赖）；
 * 主观题（TEXT/CODE）：整批合并成一次 AI 调用（maxScore 统一 100，score>=60 记正确），
 * AI 异常或结果缺失时该题按"未判分"落库（is_correct=NULL），不连累同批其它题。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemediationGradingService {

    /** AI 判分失败兜底文案：落 ai_feedback，学员可整轮重做 */
    static final String AI_FAILED_FEEDBACK = "AI判分失败可稍后重做";
    private static final BigDecimal FULL_SCORE = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO;
    private static final int AI_MAX_SCORE = 100;
    private static final int AI_PASS_SCORE = 60;

    private final ExamServiceImpl examService;
    private final AiService aiService;

    /** 单题判分结果：isCorrect/score 为 null 表示未判分 */
    public record ItemResult(Byte isCorrect, BigDecimal score, String feedback) {}

    /**
     * 批量判分：客观题本地比对，主观题合并一次 AI 调用。
     * 返回 questionId -> 结果，输入的每道题必有返回（含未判分兜底）。
     */
    public Map<Long, ItemResult> gradeBatch(List<Question> questions, Map<Long, String> answersByQuestionId) {
        Map<Long, ItemResult> results = new HashMap<>();
        List<BatchGradingItemVo> aiItems = new ArrayList<>();
        for (Question q : questions) {
            String userAnswer = answersByQuestionId.get(q.getId());
            if (isAiGradedType(q.getType())) {
                BatchGradingItemVo item = new BatchGradingItemVo();
                item.setQuestionId(q.getId());
                item.setQuestion(q);
                item.setUserAnswer(userAnswer);
                item.setMaxScore(AI_MAX_SCORE);
                aiItems.add(item);
                continue;
            }
            results.put(q.getId(), gradeObjective(q, userAnswer));
        }
        if (!aiItems.isEmpty()) {
            results.putAll(gradeSubjectiveBatch(aiItems));
        }
        return results;
    }

    /** 客观题：题库标准答案 + 包级智能比对（复用考试判卷口径，兼容字母/内容/中英文差异） */
    private ItemResult gradeObjective(Question question, String userAnswer) {
        String correctAnswer = question.getAnswer() == null ? null : question.getAnswer().getAnswer();
        boolean correct = correctAnswer != null
                && examService.isObjectiveAnswerCorrect(question, correctAnswer, userAnswer);
        return correct
                ? new ItemResult((byte) 1, FULL_SCORE, "回答正确")
                : new ItemResult((byte) 0, ZERO_SCORE, "回答错误");
    }

    /**
     * 主观题整批一次 AI 调用：score>=60 记正确；
     * 整批异常或单题结果缺失均按未判分（is_correct=NULL）处理，不抛出。
     */
    private Map<Long, ItemResult> gradeSubjectiveBatch(List<BatchGradingItemVo> items) {
        Map<Long, ItemResult> results = new HashMap<>();
        Map<Long, GradingResult> aiResults;
        try {
            aiResults = aiService.gradingSubjectiveBatch(items);
        } catch (Exception e) {
            log.warn("补课主观题批量AI判分失败，{} 题按未判分处理", items.size(), e);
            aiResults = Map.of();
        }
        for (BatchGradingItemVo item : items) {
            GradingResult r = item.getQuestionId() == null ? null : aiResults.get(item.getQuestionId());
            if (r == null || r.getScore() == null) {
                results.put(item.getQuestionId(), new ItemResult(null, null, AI_FAILED_FEEDBACK));
                continue;
            }
            boolean correct = r.getScore() >= AI_PASS_SCORE;
            String feedback = r.getFeedback() != null ? r.getFeedback() : r.getReason();
            results.put(item.getQuestionId(), new ItemResult(correct ? (byte) 1 : (byte) 0,
                    BigDecimal.valueOf(r.getScore()), feedback));
        }
        return results;
    }

    private static boolean isAiGradedType(String type) {
        return "TEXT".equals(type) || "CODE".equals(type);
    }
}
