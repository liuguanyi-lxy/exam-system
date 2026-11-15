package com.atguigu.exam.service;


import com.atguigu.exam.entity.Question;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.GradingResult;
import com.atguigu.exam.vo.QuestionImportVo;

import java.util.List;

/**
 * AI 服务接口
 * 通过 OpenAI 兼容接口生成题目、判卷简答题、生成考试总评（默认接 DeepSeek）
 */
public interface AiService {


    /**
     * 生成ai评语
     * @param totalScore
     * @param maxScore
     * @param questionCount
     * @param correctCount
     * @return
     */
    String buildSummary(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount);


    /**
     * 使用ai,进行简答题判断，返回判题结果
     * @param question
     * @param userAnswer
     * @param maxScore
     * @return
     */
     GradingResult gradingSubjectiveQuestion(Question question, String userAnswer, Integer maxScore);

    /**
     * 批量AI判卷：把多道简答/代码题合并成一次AI调用，规避AI账号RPM限流（如每分钟3次）
     * @param items 判卷单项列表（题目+学生答案+满分）
     * @return questionId -> 判卷结果；AI未返回的题目不在结果中，由调用方按判卷失败处理
     */
    java.util.Map<Long, GradingResult> gradingSubjectiveBatch(java.util.List<com.atguigu.exam.vo.BatchGradingItemVo> items);


    /**
     * 根据前台传递的上下文环境，生成对应的提示词
     * @param request
     * @return
     */
    String buildPrompt(AiGenerateRequestVo request);

    /**
     * 封装调用kimi模型，传入提示词，带3次重试，失败抛RuntimeException
     * @param prompt
     * @return 返回生成题目json 结果 / choices / message / content
     */
    String callAiChat(String prompt);

    /**
     * ai题目信息生成
     * @param request
     * @return
     */
    List<QuestionImportVo> aiGenerateQuestions(AiGenerateRequestVo request);
}
