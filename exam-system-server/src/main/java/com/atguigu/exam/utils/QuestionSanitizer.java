package com.atguigu.exam.utils;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionChoice;

import java.util.List;

/**
 * 题目答案脱敏工具。
 *
 * 背景：试卷详情、题库列表这类接口会把 Question 实体整份返回，
 * 其中包含 answer（标准答案）、analysis（解析）、choices[].isCorrect（选项对错）。
 * 学生端和匿名访问方拿到这些字段就等于拿到了答案，考试形同开卷。
 *
 * 约定：
 *  - 学生角色 / 匿名访问：只返回题干与选项内容，标准答案、解析、选项对错一律置空
 *  - 教师 / 管理员：保留完整信息（组卷、编辑、批阅都要用）
 */
public final class QuestionSanitizer {

    private QuestionSanitizer() {
    }

    /** 去掉单题的答案、解析、选项对错标记 */
    public static void stripAnswer(Question question) {
        if (question == null) return;
        question.setAnswer(null);
        question.setAnalysis(null);
        stripChoiceFlags(question.getChoices());
    }

    /** 批量脱敏 */
    public static void stripAnswer(List<Question> questions) {
        if (questions == null) return;
        for (Question question : questions) {
            stripAnswer(question);
        }
    }

    private static void stripChoiceFlags(List<QuestionChoice> choices) {
        if (choices == null) return;
        for (QuestionChoice choice : choices) {
            choice.setIsCorrect(null);
        }
    }
}
