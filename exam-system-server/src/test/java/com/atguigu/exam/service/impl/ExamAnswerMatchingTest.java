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
    // 单选题支持字母答案大小写归一化
    void singleChoiceMatchesLetterAnswersCaseInsensitively() {
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "A", "a"));
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "A", "A"));
    }

    @Test
    // 标准答案是字母时可匹配学生提交的选项内容
    void singleChoiceMatchesOptionTextAgainstLetterAnswer() {
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "A", "Java"));
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "A", "A. Java"));
    }

    @Test
    // 标准答案是选项内容时可匹配学生提交的字母
    void singleChoiceMatchesLetterAgainstOptionTextAnswer() {
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "Java", "A"));
    }

    @Test
    // 缺少标准答案字段时使用选项的 isCorrect 标记判定
    void singleChoiceUsesCorrectMarkWhenAnswerTextMissing() {
        //标准答案字段缺失时，学生答对正确选项的内容也应判对
        assertTrue(examService.isObjectiveAnswerCorrect(singleChoice, "Java", "Java"));
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "Java", "Python"));
    }

    @Test
    // 单选题提交错误字母或内容应判错
    void singleChoiceRejectsWrongAnswer() {
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "A", "B"));
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "A", "Python"));
    }

    @Test
    // 多选题选项内容含逗号和空格时仍应正确判定
    void multipleChoiceHandlesCommasAndSpacesInOptionText() {
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
    // 多选题字母集合比对不受答案顺序影响
    void multipleChoiceLetterSetComparisonIgnoresOrder() {
        assertTrue(examService.isObjectiveAnswerCorrect(multiChoice, "AB", "A,B"));
        assertTrue(examService.isObjectiveAnswerCorrect(multiChoice, "A,B", "BA"));
        assertFalse(examService.isObjectiveAnswerCorrect(multiChoice, "AB", "A,B,C"));
    }

    @Test
    // 多选题按选项内容比对且不受顺序影响
    void multipleChoiceComparesOptionTextWithoutOrder() {
        assertTrue(examService.isObjectiveAnswerCorrect(multiChoice, "A内容,B内容", "B内容、A内容"));
    }

    @Test
    // 纯英文选项内容不能误判成字母答案
    void multipleChoiceDoesNotTreatEnglishOptionTextAsLetters() {
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
    // sort 从 1 开始时按选项位置映射字母
    void multipleChoiceMapsOneBasedSortToLetters() {
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
    // 判断题支持中英文、真假值和字母形式归一化
    void judgeNormalizesChineseEnglishAndBooleanAnswers() {
        assertTrue(examService.isObjectiveAnswerCorrect(judge, "TRUE", "正确"));
        assertTrue(examService.isObjectiveAnswerCorrect(judge, "TRUE", "true"));
        assertTrue(examService.isObjectiveAnswerCorrect(judge, "正确", "T"));
        assertTrue(examService.isObjectiveAnswerCorrect(judge, "FALSE", "错"));
        assertFalse(examService.isObjectiveAnswerCorrect(judge, "TRUE", "FALSE"));
    }

    @Test
    // 空答案应判错
    void emptyAnswerIsIncorrect() {
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "A", null));
        assertFalse(examService.isObjectiveAnswerCorrect(singleChoice, "A", "  "));
        assertFalse(examService.isObjectiveAnswerCorrect(judge, "TRUE", null));
    }
}
