package com.atguigu.exam.service;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.DiagnosticReportMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.vo.DiagnosticReport;
import com.atguigu.exam.vo.DiagnosticReport.Evidence;
import com.atguigu.exam.vo.DiagnosticReport.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiagnosticReportTest {
    private final DiagnosticReportBuilder builder = new DiagnosticReportBuilder();
    private final DiagnosticReportMapper reports = mock(DiagnosticReportMapper.class);
    private final ExamRecordMapper records = mock(ExamRecordMapper.class);
    private final ClassScopeService scopes = mock(ClassScopeService.class);
    private final DiagnosticAccess access = new DiagnosticAccess(records, scopes);
    private final DiagnosticReportService service = new DiagnosticReportService(access, reports, records, builder, new ObjectMapper());

    @Test
    void scoresAndRecommendationsUseRecordedGradesAndWeightedKnowledgePointScores() throws Exception {
        var graded = graded(6);
        var tags = List.of(new Tag(1, 5, "基础"), new Tag(2, 5, "基础"), new Tag(2, 6, "专题"));
        var report = builder.build(graded, List.of(evidence(1,"CHOICE",4,4,false), evidence(2,"TEXT",2,6,false)), tags);
        assertEquals(6, report.score());
        assertEquals(4, report.objectiveScore());
        assertEquals(2, report.subjectiveScore());
        assertEquals("NORMAL", report.confidence());
        assertEquals(new BigDecimal("60.00"), report.knowledgePoints().get(1).rate());
        assertEquals(new BigDecimal("33.33"), report.weakPoints().get(0).rate());
        assertTrue(report.recommendations().get(0).contains("专题"));
        ObjectMapper json = new ObjectMapper();
        assertEquals(report, json.readValue(json.writeValueAsString(report), DiagnosticReport.class));
    }

    @Test
    void limitedConfidenceStripsReferenceAndUserAnswersForAllQuestions() {
        var graded = graded(6);
        graded.setSummary(null);
        var report = builder.build(graded, List.of(evidence(1,"CHOICE",4,4,false)), List.of(new Tag(1,5,"基础")));
        assertEquals("LIMITED", report.confidence());
        assertTrue(report.questions().stream().allMatch(q -> q.referenceAnswer() == null && q.userAnswer() == null));
    }

    @Test
    void changedOrAmbiguousEvidenceIsFlaggedAndDoesNotContributeToWeakPointRanking() {
        var changed = evidence(1,"TEXT",0,5,true);
        var duplicate = evidence(2,"CHOICE",1,1,false);
        var report = builder.build(graded(0), List.of(changed,duplicate,duplicate), List.of(new Tag(1,1,"基础")));
        assertEquals("LIMITED", report.confidence());
        assertNull(report.questions().get(0).referenceAnswer());
        assertNull(report.questions().get(1).score());
        assertTrue(report.weakPoints().isEmpty());
    }

    @Test
    void wrongOwnerUnsubmittedAndCrossClassTeacherCannotEnqueue() {
        var record = graded(6);
        when(records.selectById(1)).thenReturn(record);
        record.setStatus(ExamStatus.IN_PROGRESS);
        assertThrows(ClassScopeException.class, () -> service.get(1,12L,"STUDENT"));
        assertThrows(IllegalArgumentException.class, () -> service.get(1,11L,"STUDENT"));
        record.setStatus(ExamStatus.GRADED);
        record.setClassId(21L);
        assertThrows(ClassScopeException.class, () -> service.get(1,31L,"TEACHER"));
        verifyNoInteractions(reports);
    }

    @Test
    void finishedReportWaitsForGradeAndAuthorizedReadBackfillsMissingJobs() {
        var record = graded(6);
        when(records.selectById(1)).thenReturn(record);
        record.setStatus(ExamStatus.FINISHED);
        assertEquals("WAITING_FOR_GRADE", service.get(1,11L,"STUDENT").status());
        verifyNoInteractions(reports);
        record.setStatus(ExamStatus.GRADED);
        assertEquals("PENDING", service.get(1,11L,"STUDENT").status());
        verify(reports).enqueue(1);
    }

    @Test
    void onlyWinningWorkerGeneratesAndFailuresUseTokenBoundRetry() {
        when(reports.claim(eq(1), anyString())).thenReturn(0,1);
        service.generate(1);
        verifyNoInteractions(records);
        when(records.selectById(1)).thenReturn(graded(6));
        when(reports.evidence(1)).thenThrow(new IllegalStateException());
        service.generate(1);
        verify(reports).fail(eq(1), anyString());
        verify(reports, never()).finish(anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void sameReviewCanBeReplayedButCannotOverwriteAnotherReview() {
        var graded = graded(6);
        when(records.selectById(1)).thenReturn(graded);
        when(reports.get(1)).thenReturn(new DiagnosticReportMapper.Row("READY", null, 21L, "核对完成", null));
        assertEquals(21L,service.review(1,21L,"ADMIN","核对完成").reviewedBy());
        assertThrows(IllegalArgumentException.class, () -> service.review(1,22L,"ADMIN","改意见"));
        assertThrows(ClassScopeException.class, () -> service.review(1,11L,"STUDENT","改意见"));
    }

    private ExamRecord graded(int score) {
        var record = new ExamRecord();
        record.setStatus(ExamStatus.GRADED);
        record.setUserId(11L);
        record.setExamId(3);
        record.setScore(score);
        record.setSummary("复习主观题");
        return record;
    }

    private Evidence evidence(long id, String type, int earned, int possible, boolean changed) {
        return new Evidence(id, "题目", type, BigDecimal.valueOf(possible),"标准答案","学员答案",earned,
                earned == possible ? 1 : 2, "评分依据",id,changed);
    }
}
