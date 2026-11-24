package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.ExamClass;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.ExamClassMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.vo.ExamClassVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamClassServiceImplTest {

    @Mock private ExamClassMapper examClassMapper;
    @Mock private ExamRecordMapper examRecordMapper;
    @Mock private PaperMapper paperMapper;
    @Mock private ClazzMapper clazzMapper;
    @Mock private ClassMemberMapper classMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private ClassScopeService classScopeService;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private ExamClassServiceImpl service;

    @BeforeAll
    static void setupMapperMetadata() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(ExamClassMapper.class);
        configuration.addMapper(ClassMemberMapper.class);
        configuration.addMapper(ExamRecordMapper.class);
    }

    @Test
    void snapshotRosterIncludesDepartedStudentsAndIgnoresUngradedScore() {
        stubSnapshotRoster();
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        var students = service.listStudents(41L, 9L, false);

        assertEquals(List.of(11L, 12L, 13L), students.stream().map(ExamClassVO.StudentProgress::getUserId).toList());
        assertEquals("未考", students.get(0).getLatestStatus());
        assertEquals(0, students.get(0).getAttemptCount());
        assertNull(students.get(0).getMaxScore());
        assertEquals("已交", students.get(1).getLatestStatus());
        assertNull(students.get(1).getMaxScore());
        assertEquals("历史学员", students.get(2).getRealName());
        assertEquals(2, students.get(2).getAttemptCount());
        assertEquals(86, students.get(2).getMaxScore());
        assertEquals(ExamStatus.IN_PROGRESS, students.get(2).getLatestStatus());
    }

    @Test
    void detailCountsUseSameSnapshotRosterAsStudentList() {
        stubSnapshotRoster();

        var detail = service.getAssignment(41L, 9L, false);

        assertEquals(1, detail.getNotStartedCount());
        assertEquals(1, detail.getSubmittedCount());
        assertEquals(1, detail.getInProgressCount());
        assertEquals(0, detail.getGradedCount());
        verifyNoInteractions(userMapper);
    }

    @Test
    void teacherWithNoClassesOrWrongClassCannotReadOrMutateAssignment() {
        when(examClassMapper.selectById(41L)).thenReturn(assignment(false));
        when(examClassMapper.lockById(41L)).thenReturn(assignment(false));

        var page = service.pageAssignments(new Page<>(1, 10), null, null, 9L, false);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        assertThrows(ClassScopeException.class, () -> service.getAssignment(41L, 9L, false));
        assertThrows(ClassScopeException.class, () -> service.listStudents(41L, 9L, false));
        assertThrows(ClassScopeException.class,
                () -> service.updateAssignment(41L, new ExamClassVO.UpdateRequest(), 9L, false));
        assertThrows(ClassScopeException.class, () -> service.revokeAssignment(41L, 9L, false));
        verify(examClassMapper, never()).selectPage(any(), any());
        verifyNoInteractions(examRecordMapper, classMemberMapper, userMapper);
    }

    @Test
    void changingAttemptsChecksAllRecordsAfterTakingAssignmentLock() {
        when(examClassMapper.lockById(41L)).thenReturn(assignment(false));
        when(examRecordMapper.hasAssignmentAttempts(41L)).thenReturn(true);
        var request = new ExamClassVO.UpdateRequest();
        request.setMaxAttempts(5);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateAssignment(41L, request, 9L, true));

        assertEquals("已有学员开考，无法修改考试次数", error.getMessage());
        var order = inOrder(examClassMapper, examRecordMapper);
        order.verify(examClassMapper).lockById(41L);
        order.verify(examRecordMapper).hasAssignmentAttempts(41L);
        verify(examClassMapper, never()).updateWindowAndAttempts(any(), any(), any(), any());
    }

    @Test
    void clearingWindowDoesNotCheckHistoryWhenAttemptsUnchanged() {
        var existing = assignment(false);
        var before = LocalDateTime.of(2026, 11, 24, 10, 0);
        var after = before.plusHours(3);
        existing.setStartTime(before);
        existing.setEndTime(after);
        when(examClassMapper.lockById(41L)).thenReturn(existing);
        when(examClassMapper.updateWindowAndAttempts(41L, null, after, 2)).thenReturn(1);
        var request = new ExamClassVO.UpdateRequest();
        request.setStartTime(null);

        service.updateAssignment(41L, request, 9L, true);

        verify(examClassMapper).updateWindowAndAttempts(41L, null, after, 2);
        verifyNoInteractions(examRecordMapper);
    }

    @Test
    void revokeUsesAssignmentSnapshotAndRequiresNoActiveAttempts() {
        when(examClassMapper.lockById(41L)).thenReturn(assignment(false));
        when(examRecordMapper.selectCount(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ExamRecord> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("exam_class_id"));
            assertTrue(sql.contains("status"));
            assertTrue(query.getParamNameValuePairs().containsValue(41L));
            assertTrue(query.getParamNameValuePairs().containsValue(ExamStatus.IN_PROGRESS));
            return 1L;
        }).thenReturn(0L);

        assertThrows(IllegalArgumentException.class, () -> service.revokeAssignment(41L, 9L, true));
        verify(examClassMapper, never()).deleteById(41L);
        when(examClassMapper.deleteById(41L)).thenReturn(1);
        service.revokeAssignment(41L, 9L, true);
        verify(examClassMapper).deleteById(41L);
    }

    @Test
    void revivalDoesNotChangeAnAttemptedAssignmentsCeiling() {
        var request = createRequest();
        when(examClassMapper.lockByPair(21L, 31L)).thenReturn(assignment(true));
        when(examRecordMapper.hasAssignmentAttempts(41L)).thenReturn(true);
        request.setMaxAttempts(3);

        var results = service.createAssignments(request, 9L, true);

        assertEquals(1, results.size());
        assertEquals("FAILED", results.get(0).getStatus());
        assertTrue(results.get(0).getMessage().contains("原分配 2 次"));
        verify(examClassMapper, never()).reviveDeleted(any(), any(), any(), any(), any());
    }

    @Test
    void revivalWithUnchangedCeilingReusesOriginalAssignmentId() {
        var request = createRequest();
        when(examClassMapper.lockByPair(21L, 31L)).thenReturn(assignment(true));
        when(examClassMapper.reviveDeleted(21L, 31L, null, null, 2)).thenReturn(1);

        var result = service.createAssignments(request, 9L, true).get(0);

        assertEquals("REVIVED", result.getStatus());
        assertEquals(41L, result.getAssignmentId());
        verifyNoInteractions(examRecordMapper);
    }

    @Test
    void concurrentInsertWinnerReturnsExistingAssignment() {
        var request = createRequest();
        when(examClassMapper.lockByPair(21L, 31L)).thenReturn(null, assignment(false));
        when(examClassMapper.insert(any(ExamClass.class))).thenThrow(new DuplicateKeyException("duplicate"));

        var result = service.createAssignments(request, 9L, true).get(0);

        assertEquals("EXISTS", result.getStatus());
        assertEquals(41L, result.getAssignmentId());
        verify(examClassMapper, never()).reviveDeleted(any(), any(), any(), any(), any());
    }

    private void stubSnapshotRoster() {
        when(examClassMapper.selectById(41L)).thenReturn(assignment(false));
        when(classScopeService.isTeacherOfClass(9L, 31L)).thenReturn(true);
        var onlyCurrent = new ClassMember();
        onlyCurrent.setUserId(11L);
        var submitted = new ClassMember();
        submitted.setUserId(12L);
        when(classMemberMapper.selectList(any())).thenReturn(List.of(onlyCurrent, submitted));
        when(examRecordMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ExamRecord> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("exam_class_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(41L));
            return List.of(record(13L, ExamStatus.IN_PROGRESS, 0), record(13L, ExamStatus.GRADED, 86),
                    record(12L, ExamStatus.FINISHED, 96));
        });
    }

    private ExamClassVO.CreateRequest createRequest() {
        var paper = new Paper();
        paper.setStatus("PUBLISHED");
        when(paperMapper.selectById(21L)).thenReturn(paper);
        var clazz = new Clazz();
        clazz.setId(31L);
        clazz.setStatus("ENABLED");
        when(clazzMapper.selectBatchIds(List.of(31L))).thenReturn(List.of(clazz));
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel());
            return new SimpleTransactionStatus();
        });
        var request = new ExamClassVO.CreateRequest();
        request.setExamId(21L);
        request.setClassIds(List.of(31L, 31L));
        request.setMaxAttempts(2);
        return request;
    }

    private static ExamClass assignment(boolean deleted) {
        var assignment = new ExamClass();
        assignment.setId(41L);
        assignment.setExamId(21L);
        assignment.setClassId(31L);
        assignment.setMaxAttempts(2);
        assignment.setIsDeleted((byte) (deleted ? 1 : 0));
        return assignment;
    }

    private static ExamRecord record(long studentId, String status, int score) {
        var record = new ExamRecord();
        record.setUserId(studentId);
        record.setStatus(status);
        record.setScore(score);
        record.setStudentName("历史学员");
        return record;
    }
}
