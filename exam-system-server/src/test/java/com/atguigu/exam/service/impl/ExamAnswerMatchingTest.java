package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionChoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客观题判卷比对逻辑单元测试（纯JVM，不依赖Spring上下文和数据库）
 */
class ExamAnswerMatchingTest {

    private ExamServiceImpl examService;
    private Question singleChoice;
    private Question multiChoice;
    private Question judge;

    @BeforeEach
    void setUp() {
        examService = new ExamServiceImpl();

        singleChoice = new Question();
        singleChoice.setType("CHOICE");
        singleChoice.setMulti(false);
        QuestionChoice a = choice("Java", 0, true);
        QuestionChoice b = choice("Python", 1, false);
        QuestionChoice c = choice("Go", 2, false);
        singleChoice.setChoices(List.of(a, b, c));

        multiChoice = new Question();
        multiChoice.setType("CHOICE");
        multiChoice.setMulti(true);
        QuestionChoice m1 = choice("A内容", 0, true);
        QuestionChoice m2 = choice("B内容", 1, true);
        QuestionChoice m3 = choice("C内容", 2, false);
        multiChoice.setChoices(List.of(m1, m2, m3));

        judge = new Question();
        judge.setType("JUDGE");
    }

    private QuestionChoice choice(String content, int sort, boolean correct) {
        QuestionChoice questionChoice = new QuestionChoice();
        questionChoice.setContent(content);
        questionChoice.setSort(sort);
        questionChoice.setIsCorrect(correct);
        return questionChoice;
    }

    @Test
    void 单选题_字母答字母() {
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "A", "a"));
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "A", "A"));
    }

    @Test
    void 单选题_标准答字母_学生答内容() {
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "A", "Java"));
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "A", "A. Java"));
    }

    @Test
    void 单选题_标准答内容_学生答字母() {
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "Java", "A"));
    }

    @Test
    void 单选题_通过isCorrect标记判定() {
        //标准答案字段缺失时，学生答对正确选项的内容也应判对
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "Java", "Java"));
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "Java", "Python"));
    }

    @Test
    void 单选题_答错() {
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "A", "B"));
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "A", "Python"));
    }

    @Test
    void 多选题_字母集合比对顺序无关() {
        assertTrue(examService.isObjectiveAnswerCorrect(multiChoice, "AB", "A,B"));
        assertTrue(examService.isObjectiveAnswerCorrect(multiChoice, "A,B", "BA"));
        assertFalse(examService.isObjectiveAnswerCorrect(multiChoice, "AB", "A,B,C"));
    }

    @Test
    void 多选题_内容比对() {
        assertTrue(examService.isObjectiveAnswerCorrect(multiChoice, "A内容,B内容", "B内容、A内容"));
    }

    @Test
    void 判断题_中英文归一化() {
        assertTrue(examService.isObjectiveAnswerCorrect(judge, "TRUE", "正确"));
        assertTrue(examService.isObjectiveAnswerCorrect(judge, "TRUE", "true"));
        assertTrue(examService.isObjectiveAnswerCorrect(judge, "正确", "T"));
        assertTrue(examService.isObjectiveAnswerCorrect(judge, "FALSE", "错"));
        assertFalse(examService.isObjectiveAnswerCorrect(judge, "TRUE", "FALSE"));
    }

    @Test
    void 空答案判错() {
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "A", null));
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "A", "  "));
        assertFalse(examService.isObjectiveAnswerCorrect(judge, "TRUE", null));
    }
}
