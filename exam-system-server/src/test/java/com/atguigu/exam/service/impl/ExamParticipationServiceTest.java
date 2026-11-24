package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.ExamClass;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ExamClassMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.vo.StartExamVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamParticipationServiceTest {
    @Mock private ExamRecordMapper records;
    @Mock private ExamClassMapper assignments;
    @Mock private PaperMapper papers;
    @Mock private UserMapper users;
    @Mock private ClassScopeService classScope;
    @InjectMocks private ExamParticipationService service;

    @BeforeAll
    static void setupMapperMetadata() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(PaperMapper.class);
        configuration.addMapper(ExamRecordMapper.class);
    }

    @Test
    void continueExistingAttemptBeforeNewEligibilityAndRestoreExactRecord() {
        ExamRecord active = new ExamRecord();
        active.setId(510L);
        when(users.lockExamCandidate(11L)).thenReturn(11L);
        when(records.findActiveAttempt(11L, 21)).thenReturn(active);
        var input = input(510L);
        assertSame(active, service.start(input, 11L));
        input.setResumeRecordId(520L);
        assertThrows(IllegalArgumentException.class, () -> service.start(input, 11L));
        assertThrows(IllegalArgumentException.class, () -> {
            when(records.findActiveAttempt(11L, 21)).thenReturn(null);
            service.start(input, 11L);
        });
        verifyNoInteractions(assignments, papers, classScope);
        verify(records, never()).insert(any(ExamRecord.class));
    }

    @Test
    void studentInClassRequiresAssignmentAndDoesNotFallBackOnEmptyScope() {
        published(11L);
        when(classScope.getStudentClassIds(11L)).thenReturn(List.of(31L));
        assertThrows(ClassScopeException.class, () -> service.start(input(null), 11L));
        verify(assignments).findStudentAssignments(11L, 21, true);
        verify(records, never()).insert(any(ExamRecord.class));
    }

    @Test
    void newAttemptPersistsClassAndAssignmentAndCountsAllPriorRows() {
        published(11L);
        when(classScope.getStudentClassIds(11L)).thenReturn(List.of(31L, 32L));
        when(records.countAttempts(11L, 21)).thenReturn(1L);
        when(assignments.findStudentAssignments(11L, 21, true)).thenReturn(
                List.of(assignment(42L, 32L, 2, null, null), assignment(41L, 31L, 2, null, null)));
        var result = service.start(input(null), 11L);
        assertEquals(31L, result.getClassId());
        assertEquals(41L, result.getExamClassId());
        assertEquals(2, result.getAttemptNo());
        assertEquals(11L, result.getUserId());
        var order = inOrder(users, records, assignments);
        order.verify(users).lockExamCandidate(11L);
        order.verify(records).findActiveAttempt(11L, 21);
        order.verify(records).countAttempts(11L, 21);
        order.verify(assignments).findStudentAssignments(11L, 21, true);
        order.verify(records).insert(result);
        when(records.countAttempts(11L, 21)).thenReturn(2L);
        assertThrows(IllegalArgumentException.class, () -> service.start(input(null), 11L));
        verify(records, times(1)).insert(any(ExamRecord.class));
    }

    @Test
    void sameDisplayNameDoesNotAffectMatchingAndLegacyPublishedRemainsAvailable() {
        published(11L);
        when(users.lockExamCandidate(12L)).thenReturn(12L);
        var first = service.start(input(null), 11L);
        var second = service.start(input(null), 12L);
        assertEquals(first.getStudentName(), second.getStudentName());
        assertNotEquals(first.getUserId(), second.getUserId());
        assertNull(first.getClassId());
        assertNull(second.getExamClassId());
        verify(records).findActiveAttempt(11L, 21);
        verify(records).findActiveAttempt(12L, 21);
        verifyNoInteractions(assignments);
    }

    @Test
    void duplicateInsertOnlyReturnsSameUsersActiveRecord() {
        published(11L);
        var winner = new ExamRecord();
        winner.setUserId(11L);
        when(records.findActiveAttempt(11L, 21)).thenReturn(null, winner);
        when(records.insert(any(ExamRecord.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertSame(winner, service.start(input(null), 11L));
        verify(records, times(2)).findActiveAttempt(11L, 21);
    }

    @Test
    void openWindowIncludesBothEdgesAndCapsAttemptsAcrossAvailableAssignments() {
        var now = LocalDateTime.of(2026, 11, 24, 10, 0);
        var low = assignment(41L, 31L, 1, now, now);
        var high = assignment(42L, 32L, 3, now, now);
        var future = assignment(43L, 33L, 4, now.plusSeconds(1), null);
        assertSame(high, ExamParticipationService.requireAvailable(List.of(low, future, high), 2, now));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ExamParticipationService.requireAvailable(List.of(low, high), 3, now)).getMessage().contains("共 3 次"));
        assertThrows(IllegalArgumentException.class, () -> ExamParticipationService.requireAvailable(List.of(high), 0, now.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> ExamParticipationService.requireAvailable(List.of(high), 0, now.plusSeconds(1)));
    }

    @Test
    void assignedStudentListCanStillResumeAfterAssignmentRemoved() {
        when(classScope.getStudentClassIds(11L)).thenReturn(List.of(32L));
        var active = new ExamRecord();
        active.setExamId(21);
        active.setId(510L);
        active.setStatus(ExamStatus.IN_PROGRESS);
        when(records.selectList(any())).thenReturn(List.of(active));
        var stopped = new Paper();
        stopped.setId(21L);
        stopped.setStatus("STOPPED");
        when(papers.selectList(any())).thenAnswer(call -> {
            LambdaQueryWrapper<Paper> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("id IN"));
            assertTrue(query.getParamNameValuePairs().containsValue(21L));
            return List.of(stopped);
        });
        assertEquals(510L, service.list(11L, null).get(0).getResumeRecordId());
        verify(records, never()).countAttemptsByPaper(anyLong());
    }

    @Test
    void attemptSqlIncludesDeletedRecordsAndAssignmentSqlLocksActiveMembership() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(ExamRecordMapper.class);
        configuration.addMapper(ExamClassMapper.class);
        var count = configuration.getMappedStatement(ExamRecordMapper.class.getName() + ".countAttempts")
                .getBoundSql(Map.of("userId", 11L, "examId", 21));
        assertFalse(count.getSql().contains("is_deleted"));
        assertEquals(2, count.getParameterMappings().size());
        var scope = configuration.getMappedStatement(ExamClassMapper.class.getName() + ".findStudentAssignments")
                .getBoundSql(Map.of("userId", 11L, "examId", 21, "lock", true));
        assertTrue(scope.getSql().contains("c.status = 'ENABLED'"));
        assertTrue(scope.getSql().contains("m.role_in_class = 'STUDENT'"));
        assertTrue(scope.getSql().contains("FOR UPDATE"));
    }

    private void published(Long userId) {
        when(users.lockExamCandidate(userId)).thenReturn(userId);
        var paper = new Paper();
        paper.setId(21L);
        paper.setStatus("PUBLISHED");
        when(papers.selectById(21)).thenReturn(paper);
    }

    private StartExamVo input(Long resumeId) {
        var input = new StartExamVo();
        input.setPaperId(21);
        input.setStudentName("同名学员");
        input.setResumeRecordId(resumeId);
        return input;
    }

    private ExamClass assignment(Long id, Long classId, int limit, LocalDateTime start, LocalDateTime end) {
        var item = new ExamClass();
        item.setId(id);
        item.setClassId(classId);
        item.setStartTime(start);
        item.setEndTime(end);
        item.setMaxAttempts(limit);
        return item;
    }
}
