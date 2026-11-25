package com.atguigu.exam.service;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.config.properties.AiProperties;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.DiagnosticQuestionChatMapper;
import com.atguigu.exam.mapper.DiagnosticQuestionChatMapper.Context;
import com.atguigu.exam.mapper.DiagnosticQuestionChatMapper.Row;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.vo.QuestionChatRequest;
import com.atguigu.exam.vo.QuestionChatRound;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionChatServiceTest {
    private final DiagnosticQuestionChatMapper chats = mock(DiagnosticQuestionChatMapper.class);
    private final AiService ai = mock(AiService.class);
    private final AiProperties aiProperties = new AiProperties();
    private final ExamRecordMapper records = mock(ExamRecordMapper.class);
    private final ClassScopeService scopes = mock(ClassScopeService.class);
    private final DiagnosticAccess access = new DiagnosticAccess(records, scopes);
    private final QuestionChatService service = new QuestionChatService(access, chats, ai, aiProperties);

    private final UUID requestId = UUID.randomUUID();

    private ExamRecord graded() {
        var record = new ExamRecord();
        record.setStatus(ExamStatus.GRADED);
        record.setUserId(11L);
        record.setExamId(3);
        record.setScore(6);
        return record;
    }

    private Row row(String status) {
        return new Row(1L, 11L, requestId.toString(), 1, "为什么错", status.equals("READY") ? "解答" : null,
                status, "test-model", "question-chat-v1", null, LocalDateTime.of(2026, 11, 24, 10, 0));
    }

    private Context context(String referenceAnswer) {
        return new Context(1, 5, "为什么错", "题目", "TEXT", referenceAnswer, "学员答案", 2, "评分依据");
    }

    private QuestionChatRequest request() {
        return new QuestionChatRequest(requestId, "为什么错");
    }

    @Test
    void unauthorizedRolesAndUngradedRecordsCannotAskOrList() {
        var record = graded();
        when(records.selectById(1)).thenReturn(record);
        assertThrows(ClassScopeException.class, () -> service.ask(1, 5, 11L, "TEACHER", request()));
        record.setStatus(ExamStatus.FINISHED);
        assertThrows(IllegalArgumentException.class, () -> service.ask(1, 5, 11L, "STUDENT", request()));
        assertThrows(ClassScopeException.class, () -> service.list(1, 5, 31L, "TEACHER"));
        record.setStatus(ExamStatus.GRADED);
        record.setClassId(21L);
        assertThrows(ClassScopeException.class, () -> service.ask(1, 5, 12L, "STUDENT", request()));
        assertThrows(ClassScopeException.class, () -> service.list(1, 5, 31L, "TEACHER"));
        verifyNoInteractions(chats);
    }

    @Test
    void askRejectsForeignQuestionsRoundLimitPendingAndCooldown() {
        var record = graded();
        when(records.selectById(1)).thenReturn(record);
        when(chats.countQuestionInPaper(3, 5L)).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.ask(1, 5, 11L, "STUDENT", request()));

        when(chats.countQuestionInPaper(3, 5L)).thenReturn(1);
        when(chats.countRounds(1, 5L)).thenReturn(QuestionChatService.MAX_ROUNDS);
        assertThrows(IllegalArgumentException.class, () -> service.ask(1, 5, 11L, "STUDENT", request()));

        when(chats.countRounds(1, 5L)).thenReturn(1);
        when(chats.countPending(1, 5L)).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> service.ask(1, 5, 11L, "STUDENT", request()));

        when(chats.countPending(1, 5L)).thenReturn(0);
        when(chats.countRecent(1, 5L)).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> service.ask(1, 5, 11L, "STUDENT", request()));
        verify(chats, never()).insert(anyLong(), anyInt(), anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    void askInsertsPendingRoundAndReplaysSameRequestId() {
        var record = graded();
        when(records.selectById(1)).thenReturn(record);
        when(chats.countQuestionInPaper(3, 5L)).thenReturn(1);
        when(chats.countRounds(1, 5L)).thenReturn(0);
        when(chats.countPending(1, 5L)).thenReturn(0);
        when(chats.countRecent(1, 5L)).thenReturn(0);
        when(chats.insert(11L, 1, 5L, requestId.toString(), 1, "为什么错")).thenReturn(1);
        when(chats.byRequest(1, 5L, requestId.toString())).thenReturn(row("PENDING"));

        var round = service.ask(1, 5, 11L, "STUDENT", request());
        assertEquals("PENDING", round.status());
        assertEquals(1, round.roundNo());
        assertEquals("question-chat-v1", round.promptVersion());

        when(chats.insert(11L, 1, 5L, requestId.toString(), 1, "为什么错")).thenReturn(0);
        when(chats.byRequest(1, 5L, requestId.toString())).thenReturn(row("READY"));
        assertEquals("READY", service.ask(1, 5, 11L, "STUDENT", request()).status());
    }

    @Test
    void roundNumberConflictRetriesWithRecountedRound() {
        var record = graded();
        when(records.selectById(1)).thenReturn(record);
        when(chats.countQuestionInPaper(3, 5L)).thenReturn(1);
        when(chats.countRounds(1, 5L)).thenReturn(0, 1);
        when(chats.countPending(1, 5L)).thenReturn(0);
        when(chats.countRecent(1, 5L)).thenReturn(0);
        when(chats.insert(11L, 1, 5L, requestId.toString(), 1, "为什么错")).thenReturn(0);
        when(chats.insert(11L, 1, 5L, requestId.toString(), 2, "为什么错")).thenReturn(1);
        when(chats.byRequest(1, 5L, requestId.toString())).thenReturn(null, row("PENDING"));

        assertEquals("PENDING", service.ask(1, 5, 11L, "STUDENT", request()).status());
        verify(chats, times(2)).insert(anyLong(), anyInt(), anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    void generateUsesClaimTokenValidatesContextAndStripsAnswer() {
        when(chats.claim(eq(1L), anyString())).thenReturn(0);
        service.generate(1L);
        verifyNoInteractions(ai);

        when(chats.claim(eq(2L), anyString())).thenReturn(1);
        when(chats.context(2L)).thenReturn(context(null));
        service.generate(2L);
        verify(chats).fail(eq(2L), anyString());
        verify(ai, never()).callAiChat(anyString());

        when(chats.claim(eq(3L), anyString())).thenReturn(1);
        when(chats.context(3L)).thenReturn(context("参考答案"));
        when(chats.list(1, 5L)).thenReturn(List.of(row("READY")));
        when(ai.callAiChat(anyString())).thenReturn("  解析内容  ");
        aiProperties.setModel("test-model");
        service.generate(3L);
        verify(chats).finish(eq(3L), anyString(), eq("解析内容"), eq("test-model"), eq("question-chat-v1"));

        when(chats.claim(eq(4L), anyString())).thenReturn(1);
        when(chats.context(4L)).thenReturn(context("参考答案"));
        when(ai.callAiChat(anyString())).thenReturn(" ");
        service.generate(4L);
        verify(chats).fail(eq(4L), anyString());

        when(chats.claim(eq(5L), anyString())).thenReturn(1);
        when(chats.context(5L)).thenReturn(context("参考答案"));
        when(ai.callAiChat(anyString())).thenThrow(new RuntimeException("AI 服务暂时不可用"));
        service.generate(5L);
        verify(chats).fail(eq(5L), anyString());
    }

    @Test
    void promptBindsQuestionFactsAndKeepsOnlyRecentReadyHistory() {
        var context = context("参考答案");
        List<QuestionChatRound> history = List.of(
                new QuestionChatRound("r1", 1, "问1", "答1", "READY", null, null, null, null),
                new QuestionChatRound("r2", 2, "问2", "答2", "READY", null, null, null, null),
                new QuestionChatRound("r3", 3, "问3", "答3", "FAILED", null, null, null, null),
                new QuestionChatRound("r4", 4, "问4", "答4", "READY", null, null, null, null));
        String prompt = service.prompt(context, history);
        assertTrue(prompt.contains("安全要求"));
        assertTrue(prompt.contains("题目【TEXT】题目"));
        assertTrue(prompt.contains("参考答案：参考答案"));
        assertTrue(prompt.contains("学员作答：学员答案"));
        assertTrue(prompt.contains("该题得分：2"));
        assertTrue(prompt.contains("判分反馈：评分依据"));
        assertTrue(prompt.contains("学员本次追问：为什么错"));
        assertTrue(prompt.contains("问1") && prompt.contains("问2") && prompt.contains("问4"));
        assertFalse(prompt.contains("问3"));
    }
}
