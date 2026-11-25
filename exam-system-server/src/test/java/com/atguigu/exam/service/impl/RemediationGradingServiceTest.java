package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.service.AiService;
import com.atguigu.exam.vo.BatchGradingItemVo;
import com.atguigu.exam.vo.GradingResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M5 补课判分组件单测：客观题复用 ExamServiceImpl 包级比对、主观题整批一次 AI 调用
 * （maxScore=100、score>=60 记正确）、AI 异常/结果缺失按未判分兜底不炸整批。
 * 全部 Mock，不连库、不真调 AI。
 */
class RemediationGradingServiceTest {

    private final ExamServiceImpl examService = mock(ExamServiceImpl.class);
    private final AiService aiService = mock(AiService.class);
    private final RemediationGradingService service = new RemediationGradingService(examService, aiService);

    private Question question(long id, String type) {
        var q = new Question();
        q.setId(id);
        q.setTitle("题目" + id);
        q.setType(type);
        var a = new QuestionAnswer();
        a.setQuestionId(id);
        a.setAnswer("B");
        q.setAnswer(a);
        return q;
    }

    @Test
    void objectiveQuestionsCompareLocallyWithoutAi() {
        Question q = question(1L, "CHOICE");
        when(examService.isObjectiveAnswerCorrect(eq(q), eq("B"), eq("B"))).thenReturn(true);
        when(examService.isObjectiveAnswerCorrect(eq(q), eq("B"), eq("A"))).thenReturn(false);

        var results = service.gradeBatch(List.of(q), Map.of(1L, "B"));
        var correct = results.get(1L);
        assertEquals((byte) 1, correct.isCorrect());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(correct.score()));
        assertEquals("回答正确", correct.feedback());

        var results2 = service.gradeBatch(List.of(q), Map.of(1L, "A"));
        var wrong = results2.get(1L);
        assertEquals((byte) 0, wrong.isCorrect());
        assertEquals(0, BigDecimal.ZERO.compareTo(wrong.score()));
        verifyNoInteractions(aiService);
    }

    @Test
    void missingStandardAnswerIsWrongWithoutComparison() {
        Question q = question(2L, "JUDGE");
        q.setAnswer(null);
        var results = service.gradeBatch(List.of(q), Map.of(2L, "TRUE"));
        assertEquals((byte) 0, results.get(2L).isCorrect());
        verifyNoInteractions(examService, aiService);
    }

    @Test
    void subjectiveQuestionsMergeIntoSingleAiCallWithMaxScore100() {
        Question text = question(3L, "TEXT");
        Question code = question(4L, "CODE");
        when(aiService.gradingSubjectiveBatch(anyList())).thenReturn(Map.of(
                3L, new GradingResult(85, "要点覆盖良好", "扣在术语"),
                4L, new GradingResult(40, null, "逻辑不完整")));

        var results = service.gradeBatch(List.of(text, code), Map.of(3L, "我的简答", 4L, "我的代码"));

        // 一次批量调用，两个 item，maxScore 统一 100
        ArgumentCaptor<List<BatchGradingItemVo>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiService, times(1)).gradingSubjectiveBatch(captor.capture());
        List<BatchGradingItemVo> sent = captor.getValue();
        assertEquals(2, sent.size());
        assertTrue(sent.stream().allMatch(i -> Integer.valueOf(100).equals(i.getMaxScore())));

        // score>=60 记正确，feedback 取 AI 反馈
        var passed = results.get(3L);
        assertEquals((byte) 1, passed.isCorrect());
        assertEquals(0, BigDecimal.valueOf(85).compareTo(passed.score()));
        assertEquals("要点覆盖良好", passed.feedback());
        // <60 记错误，feedback 退化取 reason
        var failed = results.get(4L);
        assertEquals((byte) 0, failed.isCorrect());
        assertEquals("逻辑不完整", failed.feedback());
        verifyNoInteractions(examService);
    }

    @Test
    void aiFailureLeavesItemUngradedWithoutFailingBatch() {
        Question text = question(5L, "TEXT");
        Question obj = question(6L, "CHOICE");
        // 第一次调用抛异常按未判分兜底，第二次（缺结果场景）返回空 Map
        when(aiService.gradingSubjectiveBatch(anyList()))
                .thenThrow(new RuntimeException("AI 网关超时"))
                .thenReturn(Map.of());
        when(examService.isObjectiveAnswerCorrect(any(), anyString(), anyString())).thenReturn(true);

        var results = service.gradeBatch(List.of(text, obj), Map.of(5L, "我的简答", 6L, "B"));

        // AI 题按未判分（is_correct/score 为 NULL），同批客观题不受影响
        var ungraded = results.get(5L);
        assertNull(ungraded.isCorrect());
        assertNull(ungraded.score());
        assertEquals(RemediationGradingService.AI_FAILED_FEEDBACK, ungraded.feedback());
        var objective = results.get(6L);
        assertEquals((byte) 1, objective.isCorrect());

        // AI 正常返回但缺该题结果时同样按未判分兜底
        when(aiService.gradingSubjectiveBatch(anyList())).thenReturn(Map.of());
        var missing = service.gradeBatch(List.of(text), Map.of(5L, "我的简答"));
        assertNull(missing.get(5L).isCorrect());
        assertEquals(RemediationGradingService.AI_FAILED_FEEDBACK, missing.get(5L).feedback());
    }
}
