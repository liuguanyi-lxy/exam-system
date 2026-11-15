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
    void 多选题_选项内容含逗号和空格也能判对() {
        //选项文本里自带中文逗号和空格，按逗号切分会把一项内容拆成两段，历史实现会判错
        Question messy = new Question();
        messy.setType("CHOICE");
        messy.setMulti(true);
        messy.setChoices(List.of(
                choice("可以安全地作为 key，hash 值可缓存", 0, true),
                choice("天然线程安全，不需要额外同步", 1, true),
                choice("大于 8 就转", 2, false)
        ));
        String userAnswer = "可以安全地作为 key，hash 值可缓存,天然线程安全，不需要额外同步";
        assertTrue(examService.isObjectiveAnswerCorrect(messy, "A,B", userAnswer));
        //少选一项要判错
        assertFalse(examService.isObjectiveAnswerCorrect(messy, "A,B", "可以安全地作为 key，hash 值可缓存"));
        //多选了一项错误答案也要判错
        assertFalse(examService.isObjectiveAnswerCorrect(messy, "A,B", userAnswer + ",大于 8 就转"));
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
    void 多选题_纯英文选项内容不能被当成字母答案() {
        //public/private/protected 都是纯字母单词，历史实现会把它们误判成"字母答案"再按字符集合比较，导致判错
        Question englishOptions = new Question();
        englishOptions.setType("CHOICE");
        englishOptions.setMulti(true);
        englishOptions.setChoices(List.of(
                choice("public", 0, true),
                choice("private", 1, true),
                choice("protected", 2, true),
                choice("static", 3, false)
        ));
        assertTrue(examService.isObjectiveAnswerCorrect(englishOptions, "A,B,C", "public,private,protected"));
        assertFalse(examService.isObjectiveAnswerCorrect(englishOptions, "A,B,C", "public,private"));
    }

    @Test
    void 多选题_选项sort从1开始时按位置映射字母() {
        //题库中存在 sort 从 1 开始的题：正确项是第 1/3/4 个，标准答案记为 ACD
        Question oneBasedSort = new Question();
        oneBasedSort.setType("CHOICE");
        oneBasedSort.setMulti(true);
        oneBasedSort.setChoices(List.of(
                choice("封装", 1, true),
                choice("多线程", 2, false),
                choice("继承", 3, true),
                choice("多态", 4, true)
        ));
        assertTrue(examService.isObjectiveAnswerCorrect(oneBasedSort, "ACD", "封装,继承,多态"));
        assertTrue(examService.isObjectiveAnswerCorrect(oneBasedSort, "ACD", "A,C,D"));
        assertFalse(examService.isObjectiveAnswerCorrect(oneBasedSort, "ACD", "封装,多线程"));
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
