package com.atguigu.exam.service;

import com.atguigu.exam.entity.MasteryScore;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.entity.RemediationTask;
import com.atguigu.exam.entity.RemediationTaskItem;
import com.atguigu.exam.mapper.MasteryScoreMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.mapper.RemediationTaskItemMapper;
import com.atguigu.exam.mapper.RemediationTaskMapper;
import com.atguigu.exam.service.impl.RemediationGradingService;
import com.atguigu.exam.vo.RemediationPolicy;
import com.atguigu.exam.vo.RemediationStepView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M5 步骤（练习/变式/复测）取题与提交单测：视频门禁、轮次推进、变式排除与不足回退、
 * 客观题判分正确率联动、AI 未判分兜底、复测阈值完成、forceRetest=false 练习即完成、
 * 重复提交不重复计分（WHERE user_answer IS NULL 语义）、越权题目拒绝。全部 Mock，不连库。
 */
class RemediationStepTest {

    private static final long TASK_ID = 9L;
    private static final long USER_ID = 11L;
    private static final long POINT_ID = 1L;

    private final RemediationTaskService taskService = mock(RemediationTaskService.class);
    private final RemediationTaskMapper tasks = mock(RemediationTaskMapper.class);
    private final RemediationTaskItemMapper items = mock(RemediationTaskItemMapper.class);
    private final QuestionMapper questions = mock(QuestionMapper.class);
    private final QuestionChoiceMapper questionChoices = mock(QuestionChoiceMapper.class);
    private final QuestionAnswerMapper questionAnswers = mock(QuestionAnswerMapper.class);
    private final MasteryScoreMapper masteryScores = mock(MasteryScoreMapper.class);
    private final RemediationGradingService grading = mock(RemediationGradingService.class);
    /** 无副作用事务管理器：落库段经 TransactionTemplate 单事务，单测中直通执行 */
    private final TransactionTemplate transactionTemplate = new TransactionTemplate(new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    });
    private final RemediationStepService service = new RemediationStepService(taskService, tasks, items,
            questions, questionChoices, questionAnswers, masteryScores, grading, transactionTemplate);

    private final ObjectMapper json = new ObjectMapper();

    // ---------- 公共夹具 ----------

    private RemediationTask task() {
        var t = new RemediationTask();
        t.setId(TASK_ID);
        t.setExamRecordId(7L);
        t.setUserId(USER_ID);
        t.setClassId(1L);
        t.setKnowledgePointId(POINT_ID);
        t.setStatus("IN_PROGRESS");
        t.setConfigSnapshot("{}");
        return t;
    }

    private RemediationPolicy policy(boolean forceRetest) {
        return new RemediationPolicy(3L, 60, 5, 5, forceRetest, false, 14);
    }

    private void stubAccess(RemediationTask task, RemediationPolicy policy, boolean gateOpen) {
        when(taskService.requireStudentTask(TASK_ID, USER_ID, "STUDENT")).thenReturn(task);
        when(taskService.videoGateOpen(task, USER_ID)).thenReturn(gateOpen);
        when(taskService.parsePolicy(task)).thenReturn(policy);
    }

    private RemediationTaskItem item(long questionId, int round) {
        var i = new RemediationTaskItem();
        i.setTaskId(TASK_ID);
        i.setStep("PRACTICE");
        i.setRoundNo(round);
        i.setQuestionId(questionId);
        return i;
    }

    private Question choiceQuestion(long id) {
        var q = new Question();
        q.setId(id);
        q.setTitle("题目" + id);
        q.setType("CHOICE");
        q.setMulti(false);
        var c = new QuestionChoice();
        c.setQuestionId(id);
        c.setContent("选项A");
        c.setSort(0);
        q.setChoices(List.of(c));
        var a = new QuestionAnswer();
        a.setQuestionId(id);
        a.setAnswer("B");
        q.setAnswer(a);
        return q;
    }

    private void stubQuestions(List<Question> loaded) {
        when(questions.selectBatchIds(anyCollection())).thenReturn(loaded);
        // loadQuestions 会按 mapper 结果重设题目上的选项/答案，需同步给出
        List<QuestionChoice> allChoices = loaded.stream()
                .filter(q -> q.getChoices() != null)
                .flatMap(q -> q.getChoices().stream()).toList();
        List<QuestionAnswer> allAnswers = loaded.stream()
                .map(Question::getAnswer).filter(java.util.Objects::nonNull).toList();
        when(questionChoices.selectList(any())).thenReturn(allChoices);
        when(questionAnswers.selectList(any())).thenReturn(allAnswers);
    }

    private MasteryScore mastery(String total) {
        var m = new MasteryScore();
        m.setUserId(USER_ID);
        m.setKnowledgePointId(POINT_ID);
        m.setTotal(new BigDecimal(total));
        m.setPendingRetest((byte) 1);
        return m;
    }

    // ---------- 门禁 ----------

    @Test
    void gateRejectsPaperAndSubmit() {
        stubAccess(task(), policy(true), false);
        var request = new RemediationStepView.SubmitRequest(List.of(new RemediationStepView.Answer(101L, "A")));

        IllegalArgumentException paperError = assertThrows(IllegalArgumentException.class,
                () -> service.paper(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE));
        assertEquals("请先完成视频学习", paperError.getMessage());
        IllegalArgumentException submitError = assertThrows(IllegalArgumentException.class,
                () -> service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, request));
        assertEquals("请先完成视频学习", submitError.getMessage());
        verifyNoInteractions(items, masteryScores, grading);
    }

    @Test
    void paperThrowsWhenPoolEmpty() {
        stubAccess(task(), policy(true), true);
        when(items.maxRound(TASK_ID, "PRACTICE")).thenReturn(0);
        when(items.selectList(any())).thenReturn(List.of());
        when(items.selectPoolQuestions(eq(POINT_ID), any(), eq(5))).thenReturn(List.of());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.paper(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE));
        assertEquals("该知识点暂无可用练习题", e.getMessage());
        verify(items, never()).insertIgnore(anyLong(), anyString(), anyInt(), anyLong());
    }

    // ---------- 轮次推进 ----------

    @Test
    void paperReturnsRemainingUnansweredInSameRound() {
        stubAccess(task(), policy(true), true);
        when(items.maxRound(TASK_ID, "PRACTICE")).thenReturn(1);
        when(items.selectList(any())).thenReturn(List.of(item(101L, 1), item(102L, 1)));
        stubQuestions(List.of(choiceQuestion(101L), choiceQuestion(102L)));

        var paper = service.paper(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE);
        assertEquals(1, paper.roundNo());
        assertFalse(paper.insufficient());
        assertEquals(2, paper.questions().size());
        assertEquals(101L, paper.questions().get(0).id());
        assertEquals("CHOICE", paper.questions().get(0).type());
        // 脱敏：只下发选项内容与序号，Choice 视图不含 isCorrect；feedback 只含对错评价
        assertEquals("选项A", paper.questions().get(0).choices().get(0).content());
        assertEquals(0, paper.questions().get(0).choices().get(0).sort());
        verify(items, never()).insertIgnore(anyLong(), anyString(), anyInt(), anyLong());
    }

    @Test
    void paperAdvancesRoundAndInsertsWhenNoRemaining() {
        stubAccess(task(), policy(true), true);
        when(items.maxRound(TASK_ID, "PRACTICE")).thenReturn(1);
        when(items.selectList(any())).thenReturn(List.of());
        when(items.selectPoolQuestions(eq(POINT_ID), eq(List.of()), eq(5)))
                .thenReturn(List.of(choiceQuestion(201L), choiceQuestion(202L)));
        stubQuestions(List.of(choiceQuestion(201L), choiceQuestion(202L)));

        var paper = service.paper(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE);
        assertEquals(2, paper.roundNo());
        assertFalse(paper.insufficient());
        // 新选题路径同样按 id 补全选项后再脱敏下发（池 SQL 不带选项）
        assertEquals("选项A", paper.questions().get(0).choices().get(0).content());
        verify(items).insertIgnore(TASK_ID, "PRACTICE", 2, 201L);
        verify(items).insertIgnore(TASK_ID, "PRACTICE", 2, 202L);
    }

    @Test
    void retestUsesRetestCountWithoutExclusion() {
        stubAccess(task(), policy(true), true);
        when(items.maxRound(TASK_ID, "RETEST")).thenReturn(0);
        when(items.selectList(any())).thenReturn(List.of());
        when(items.selectPoolQuestions(eq(POINT_ID), eq(List.of()), eq(5)))
                .thenReturn(List.of(choiceQuestion(301L)));
        stubQuestions(List.of(choiceQuestion(301L)));

        var paper = service.paper(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_RETEST);
        assertEquals(1, paper.roundNo());
        assertFalse(paper.insufficient());
        verify(items).insertIgnore(TASK_ID, "RETEST", 1, 301L);
        verify(items, never()).maxRound(TASK_ID, "PRACTICE");
    }

    // ---------- 变式排除与不足回退 ----------

    @Test
    void variationExcludesPracticeUsedQuestionsWhenSufficient() {
        stubAccess(task(), policy(true), true);
        when(items.maxRound(TASK_ID, "VARIATION")).thenReturn(0);
        // 第一次 selectList：当前轮余题（空）；第二次：本任务 PRACTICE 已用题
        when(items.selectList(any())).thenReturn(List.of(), List.of(item(101L, 1), item(102L, 1)));
        List<Long> used = List.of(101L, 102L);
        when(items.countPoolQuestions(POINT_ID, used)).thenReturn(8);
        when(items.selectPoolQuestions(POINT_ID, used, 5)).thenReturn(List.of(choiceQuestion(401L)));
        stubQuestions(List.of(choiceQuestion(401L)));

        var paper = service.paper(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_VARIATION);
        assertFalse(paper.insufficient());
        verify(items).selectPoolQuestions(POINT_ID, used, 5);
    }

    @Test
    void variationFallsBackToFullPoolWithInsufficientFlag() {
        stubAccess(task(), policy(true), true);
        when(items.maxRound(TASK_ID, "VARIATION")).thenReturn(0);
        when(items.selectList(any())).thenReturn(List.of(), List.of(item(101L, 1)));
        List<Long> used = List.of(101L);
        when(items.countPoolQuestions(POINT_ID, used)).thenReturn(2);
        when(items.selectPoolQuestions(POINT_ID, List.of(), 5))
                .thenReturn(List.of(choiceQuestion(501L), choiceQuestion(502L)));
        stubQuestions(List.of(choiceQuestion(501L), choiceQuestion(502L)));

        var paper = service.paper(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_VARIATION);
        assertTrue(paper.insufficient());
        assertEquals(2, paper.questions().size());
        verify(items, never()).selectPoolQuestions(eq(POINT_ID), eq(used), anyInt());
    }

    // ---------- 提交判分与掌握度联动 ----------

    private void stubPending(int round, long... questionIds) {
        when(items.maxRound(TASK_ID, "PRACTICE")).thenReturn(round);
        when(items.selectList(any())).thenReturn(java.util.Arrays.stream(questionIds)
                .mapToObj(id -> item(id, round)).toList());
    }

    @Test
    void submitGradesObjectiveAndLinksPracticeAccuracy() {
        stubAccess(task(), policy(true), true);
        stubPending(1, 101L, 102L);
        stubQuestions(List.of(choiceQuestion(101L), choiceQuestion(102L)));
        when(grading.gradeBatch(anyList(), anyMap())).thenReturn(Map.of(
                101L, new RemediationGradingService.ItemResult((byte) 1, BigDecimal.valueOf(100), "回答正确"),
                102L, new RemediationGradingService.ItemResult((byte) 0, BigDecimal.ZERO, "回答错误")));
        when(items.submitAnswer(anyLong(), anyString(), anyInt(), anyLong(), any(), any(), any(), any()))
                .thenReturn(1);
        when(masteryScores.selectOne(any())).thenReturn(mastery("50.00"));

        var request = new RemediationStepView.SubmitRequest(List.of(
                new RemediationStepView.Answer(101L, "A"),
                new RemediationStepView.Answer(102L, "C")));
        var result = service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, request);

        assertEquals(1, result.roundNo());
        assertEquals(2, result.round().totalCount());
        assertEquals(1, result.round().correctCount());
        assertEquals(50, result.round().accuracy());
        assertTrue((Boolean) result.results().get(0).correct());
        assertFalse((Boolean) result.results().get(1).correct());
        // 绝不下发答案/解析：feedback 只含对错评价
        assertEquals("回答错误", result.results().get(1).feedback());
        verify(items).submitAnswer(TASK_ID, "PRACTICE", 1, 101L, "A",
                (byte) 1, BigDecimal.valueOf(100), "回答正确");
        verify(items).submitAnswer(TASK_ID, "PRACTICE", 1, 102L, "C",
                (byte) 0, BigDecimal.ZERO, "回答错误");
        verify(masteryScores).upsertPracticeAccuracy(eq(USER_ID), eq(POINT_ID), eq(BigDecimal.valueOf(50)));
        // forceRetest=true：练习完成不判任务完成
        verify(tasks, never()).markCompleted(anyLong());
        assertFalse(result.taskCompleted());
        assertEquals(new BigDecimal("50.00"), result.masteryTotal());
    }

    @Test
    void submitSurvivesAiUngradedItem() {
        stubAccess(task(), policy(true), true);
        stubPending(1, 101L, 102L);
        stubQuestions(List.of(choiceQuestion(101L), choiceQuestion(102L)));
        // 101 客观题判对；102 AI 判分失败（is_correct 留 NULL），整批不炸
        when(grading.gradeBatch(anyList(), anyMap())).thenReturn(Map.of(
                101L, new RemediationGradingService.ItemResult((byte) 1, BigDecimal.valueOf(100), "回答正确"),
                102L, new RemediationGradingService.ItemResult(null, null, "AI判分失败可稍后重做")));
        when(items.submitAnswer(anyLong(), anyString(), anyInt(), anyLong(), any(), any(), any(), any()))
                .thenReturn(1);
        when(masteryScores.selectOne(any())).thenReturn(mastery("40.00"));

        var request = new RemediationStepView.SubmitRequest(List.of(
                new RemediationStepView.Answer(101L, "A"),
                new RemediationStepView.Answer(102L, "我的作答")));
        var result = service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, request);

        // 正确率：已判分 1 对 / 已提交 2 题 = 50
        assertEquals(1, result.round().correctCount());
        assertEquals(2, result.round().totalCount());
        assertEquals(50, result.round().accuracy());
        assertNull(result.results().get(1).correct());
        assertEquals("AI判分失败可稍后重做", result.results().get(1).feedback());
        verify(items).submitAnswer(TASK_ID, "PRACTICE", 1, 102L, "我的作答",
                null, null, "AI判分失败可稍后重做");
    }

    @Test
    void retestLinksScoreAndCompletesOnlyAboveThreshold() {
        RemediationTask t = task();
        stubAccess(t, policy(true), true);
        when(items.maxRound(TASK_ID, "RETEST")).thenReturn(2);
        when(items.selectList(any())).thenReturn(List.of(item(101L, 2)));
        stubQuestions(List.of(choiceQuestion(101L)));
        when(grading.gradeBatch(anyList(), anyMap())).thenReturn(Map.of(
                101L, new RemediationGradingService.ItemResult((byte) 1, BigDecimal.valueOf(100), "回答正确")));
        when(items.submitAnswer(anyLong(), anyString(), anyInt(), anyLong(), any(), any(), any(), any()))
                .thenReturn(1);

        // 达阈值：total 80 >= 60 → markCompleted
        when(masteryScores.selectOne(any())).thenReturn(mastery("80.00"));
        var request = new RemediationStepView.SubmitRequest(List.of(new RemediationStepView.Answer(101L, "A")));
        var done = service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_RETEST, request);
        verify(masteryScores).upsertRetestScore(eq(USER_ID), eq(POINT_ID), eq(BigDecimal.valueOf(100)));
        verify(tasks).markCompleted(TASK_ID);
        assertTrue(done.taskCompleted());

        // 未达阈值：total 50 < 60 → 保持 IN_PROGRESS（可 round+1 重做）
        clearInvocations(tasks);
        when(masteryScores.selectOne(any())).thenReturn(mastery("50.00"));
        var notDone = service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_RETEST, request);
        verify(tasks, never()).markCompleted(anyLong());
        assertFalse(notDone.taskCompleted());
    }

    @Test
    void practiceCompletesTaskWhenForceRetestDisabled() {
        stubAccess(task(), policy(false), true);
        stubPending(1, 101L);
        stubQuestions(List.of(choiceQuestion(101L)));
        when(grading.gradeBatch(anyList(), anyMap())).thenReturn(Map.of(
                101L, new RemediationGradingService.ItemResult((byte) 1, BigDecimal.valueOf(100), "回答正确")));
        when(items.submitAnswer(anyLong(), anyString(), anyInt(), anyLong(), any(), any(), any(), any()))
                .thenReturn(1);
        when(masteryScores.selectOne(any())).thenReturn(mastery("40.00"));

        var request = new RemediationStepView.SubmitRequest(List.of(new RemediationStepView.Answer(101L, "A")));
        var result = service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, request);
        verify(masteryScores).upsertPracticeAccuracy(eq(USER_ID), eq(POINT_ID), eq(BigDecimal.valueOf(100)));
        verify(tasks).markCompleted(TASK_ID);
        assertTrue(result.taskCompleted());
    }

    @Test
    void duplicateSubmitDoesNotDoubleCount() {
        stubAccess(task(), policy(true), true);
        stubPending(1, 101L, 102L);
        stubQuestions(List.of(choiceQuestion(101L), choiceQuestion(102L)));
        when(grading.gradeBatch(anyList(), anyMap())).thenReturn(Map.of(
                101L, new RemediationGradingService.ItemResult((byte) 1, BigDecimal.valueOf(100), "回答正确"),
                102L, new RemediationGradingService.ItemResult((byte) 1, BigDecimal.valueOf(100), "回答正确")));
        // 101 已被并发提交抢先（user_answer IS NULL 不命中 → 0 行），102 正常落库
        when(items.submitAnswer(eq(TASK_ID), eq("PRACTICE"), eq(1), eq(101L), any(), any(), any(), any()))
                .thenReturn(0);
        when(items.submitAnswer(eq(TASK_ID), eq("PRACTICE"), eq(1), eq(102L), any(), any(), any(), any()))
                .thenReturn(1);
        when(masteryScores.selectOne(any())).thenReturn(mastery("40.00"));

        var request = new RemediationStepView.SubmitRequest(List.of(
                new RemediationStepView.Answer(101L, "A"),
                new RemediationStepView.Answer(102L, "A")));
        var result = service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, request);
        // 抢先的 101 不重复计分：本轮只统计成功落库的 102
        assertEquals(1, result.round().totalCount());
        assertEquals(1, result.round().correctCount());
        assertEquals(100, result.round().accuracy());
    }

    @Test
    void fullyDuplicateSubmitSkipsMasteryUpsertInsteadOfZeroingIt() {
        // 并发下整轮题目均被抢先提交（全部 0 行）：不得以 accuracy=0 覆盖既有掌握度
        stubAccess(task(), policy(true), true);
        stubPending(1, 101L);
        stubQuestions(List.of(choiceQuestion(101L)));
        when(grading.gradeBatch(anyList(), anyMap())).thenReturn(Map.of(
                101L, new RemediationGradingService.ItemResult((byte) 1, BigDecimal.valueOf(100), "回答正确")));
        when(items.submitAnswer(anyLong(), anyString(), anyInt(), anyLong(), any(), any(), any(), any()))
                .thenReturn(0);
        when(masteryScores.selectOne(any())).thenReturn(mastery("76.00"));

        var request = new RemediationStepView.SubmitRequest(List.of(new RemediationStepView.Answer(101L, "A")));
        var result = service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, request);
        assertEquals(0, result.round().totalCount());
        assertEquals(0, result.round().accuracy());
        // 不清零：既不联动掌握度，也不触发完成判定
        verify(masteryScores, never()).upsertPracticeAccuracy(anyLong(), anyLong(), any());
        verify(tasks, never()).markCompleted(anyLong());
        // 回显 DB 当前掌握度而非 0
        assertEquals(new BigDecimal("76.00"), result.masteryTotal());
    }

    @Test
    void submitRejectsForeignQuestionOrEmptyOrNoPending() {
        stubAccess(task(), policy(true), true);
        stubPending(1, 101L);

        // 不在本轮待答集合内的题目拒绝
        var foreign = new RemediationStepView.SubmitRequest(List.of(new RemediationStepView.Answer(999L, "A")));
        IllegalArgumentException foreignError = assertThrows(IllegalArgumentException.class,
                () -> service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, foreign));
        assertTrue(foreignError.getMessage().contains("999"));

        // 空作答拒绝
        var empty = new RemediationStepView.SubmitRequest(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, empty));
        verify(grading, never()).gradeBatch(anyList(), anyMap());

        // 无待答题目（重复提交整轮）拒绝
        when(items.selectList(any())).thenReturn(List.of());
        var normal = new RemediationStepView.SubmitRequest(List.of(new RemediationStepView.Answer(101L, "A")));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(TASK_ID, USER_ID, "STUDENT", RemediationStepService.STEP_PRACTICE, normal));
        verify(masteryScores, never()).upsertPracticeAccuracy(anyLong(), anyLong(), any());
    }
}
