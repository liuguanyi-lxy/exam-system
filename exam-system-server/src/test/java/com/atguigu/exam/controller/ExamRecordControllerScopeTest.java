package com.atguigu.exam.controller;

import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.service.PaperService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamRecordControllerScopeTest {

    @Mock
    private ExamService examService;
    @Mock
    private ExamRecordService examRecordService;
    @Mock
    private PaperService paperService;
    @Mock
    private ClassScopeService classScopeService;

    private ExamRecordController controller;

    @BeforeAll
    static void initLambdaColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "exam-record-scope-test"),
                ExamRecord.class);
    }

    @BeforeEach
    void setUp() {
        controller = new ExamRecordController();
        ReflectionTestUtils.setField(controller, "examService", examService);
        ReflectionTestUtils.setField(controller, "examRecordService", examRecordService);
        ReflectionTestUtils.setField(controller, "paperService", paperService);
        ReflectionTestUtils.setField(controller, "classScopeService", classScopeService);
    }

    @Test
    void teacherPaperStatsUseSnapshotFirstAndLegacyMembershipOnlyWhenNoSnapshot() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        when(paperService.getById(42)).thenReturn(paper(42));
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L, 8L));
        when(classScopeService.getStudentUserIds(List.of(7L, 8L))).thenReturn(Set.of(11L, 12L));
        when(examRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Result<Map<String, Object>> result = controller.getPaperStats(42, null, request);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<LambdaQueryWrapper<ExamRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(examRecordService).list(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("exam_id =", "AND (class_id IN",
                "OR (class_id IS NULL AND user_id IN");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains(42, 7L, 8L, 11L, 12L);
    }

    @Test
    void teacherWithoutResponsibleStudentsGetsEmptyScopedQuery() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        when(paperService.getById(42)).thenReturn(paper(42));
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of());
        when(classScopeService.getStudentUserIds(List.of())).thenReturn(Set.of());
        when(examRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        controller.getPaperStats(42, null, request);

        ArgumentCaptor<LambdaQueryWrapper<ExamRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(examRecordService).list(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("exam_id =", "user_id =");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains(42, -1L);
    }

    @Test
    void teacherCanIncludeHistoryWhenResponsibleClassHasNoCurrentStudents() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of());

        controller.getExamRecords(1, 10, 42, 7L, null, null, null, null, null, request);

        ArgumentCaptor<LambdaQueryWrapper<ExamRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(examRecordService).customPageGetExamRecord(any(), query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("class_id =", "class_id IN")
                .doesNotContain("user_id", " OR ");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains(42, 7L);
    }

    @Test
    void requestedForeignClassDoesNotReplaceTeacherScope() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of(11L));

        controller.getExamRecords(1, 10, 42, 8L, null, null, null, null, null, request);

        ArgumentCaptor<LambdaQueryWrapper<ExamRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(examRecordService).customPageGetExamRecord(any(), query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("class_id =", "AND (class_id IN",
                "OR (class_id IS NULL AND user_id IN");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains(42, 8L, 7L, 11L);
    }

    @Test
    void adminPaperStatsRemainUnfilteredByClassMembership() {
        MockHttpServletRequest request = request("ADMIN", 1L);
        when(paperService.getById(42)).thenReturn(paper(42));
        when(examRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        controller.getPaperStats(42, null, request);

        ArgumentCaptor<LambdaQueryWrapper<ExamRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(examRecordService).list(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("exam_id =");
        assertThat(query.getValue().getSqlSegment()).doesNotContain("user_id", "class_id");
        verifyNoInteractions(classScopeService);
    }

    @Test
    void teacherRankingIsLimitedToStudentsInResponsibleClasses() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of(11L, 12L));
        when(examRecordService.customGetRanking(42, 10, List.of(7L), Set.of(11L, 12L), null)).thenReturn(List.of());

        controller.getExamRanking(42, 10, request);

        verify(examRecordService).customGetRanking(42, 10, List.of(7L), Set.of(11L, 12L), null);
    }

    @Test
    void studentRankingIncludesOnlyOwnClassAndSelf() {
        MockHttpServletRequest request = request("STUDENT", 11L);
        when(classScopeService.getStudentClassIds(11L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of(11L, 12L));
        when(examRecordService.customGetRanking(42, null, List.of(7L), Set.of(11L, 12L), 11L)).thenReturn(List.of());

        controller.getExamRanking(42, null, request);

        verify(examRecordService).customGetRanking(42, null, List.of(7L), Set.of(11L, 12L), 11L);
    }

    @Test
    void studentWithoutClassCanSeeOnlyOwnRankingRecord() {
        MockHttpServletRequest request = request("STUDENT", 11L);
        when(classScopeService.getStudentClassIds(11L)).thenReturn(List.of());
        when(classScopeService.getStudentUserIds(List.of())).thenReturn(Set.of());
        when(examRecordService.customGetRanking(42, null, List.of(), Set.of(), 11L)).thenReturn(List.of());

        controller.getExamRanking(42, null, request);

        verify(examRecordService).customGetRanking(42, null, List.of(), Set.of(), 11L);
    }

    @Test
    void teacherWithoutResponsibleStudentsGetsEmptyRankingWithoutQuery() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of());
        when(classScopeService.getStudentUserIds(List.of())).thenReturn(Set.of());

        Result<List<com.atguigu.exam.vo.ExamRankingVO>> result = controller.getExamRanking(null, null, request);

        assertThat(result.getData()).isEmpty();
        verifyNoInteractions(examRecordService);
    }

    @Test
    void examRecordPaginationInputsAreClamped() {
        controller.getExamRecords(Integer.MAX_VALUE, Integer.MAX_VALUE, null, null, null, null,
                null, null, null, request("ADMIN", 1L));

        ArgumentCaptor<Page<ExamRecord>> page = ArgumentCaptor.forClass(Page.class);
        verify(examRecordService).customPageGetExamRecord(page.capture(), any(LambdaQueryWrapper.class));
        assertThat(page.getValue().getCurrent()).isEqualTo(10_000);
        assertThat(page.getValue().getSize()).isEqualTo(100);
    }

    @Test
    void teacherCannotReadRecordOutsideResponsibleClasses() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        ExamRecord record = new ExamRecord();
        record.setUserId(99L);
        when(examRecordService.getById(42)).thenReturn(record);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of(11L));

        assertThatThrownBy(() -> controller.getExamRecordById(42, request))
                .isInstanceOf(ClassScopeException.class);
        verifyNoInteractions(examService);
    }

    @Test
    void teacherCannotDeleteRecordOutsideResponsibleClasses() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        ExamRecord record = new ExamRecord();
        record.setUserId(99L);
        when(examRecordService.getById(42)).thenReturn(record);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of(11L));

        assertThatThrownBy(() -> controller.deleteExamRecord(42, request))
                .isInstanceOf(ClassScopeException.class);
        verify(examRecordService, never()).customRemoveById(42);
        verifyNoInteractions(examService);
    }

    @Test
    void newClassTeacherCannotReadOrDeleteAnotherClassesSnapshot() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        ExamRecord record = new ExamRecord();
        record.setUserId(11L);
        record.setClassId(8L);
        when(examRecordService.getById(42)).thenReturn(record);
        when(classScopeService.isTeacherOfClass(5L, 8L)).thenReturn(false);

        assertThatThrownBy(() -> controller.getExamRecordById(42, request))
                .isInstanceOf(ClassScopeException.class);
        assertThatThrownBy(() -> controller.deleteExamRecord(42, request))
                .isInstanceOf(ClassScopeException.class);
        verify(classScopeService, never()).getTeacherClassIds(5L);
        verify(examRecordService, never()).customRemoveById(42);
        verifyNoInteractions(examService);
    }

    @Test
    void originalClassTeacherCanReadSnapshotAfterStudentMoves() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        ExamRecord record = new ExamRecord();
        record.setUserId(11L);
        record.setClassId(7L);
        when(examRecordService.getById(42)).thenReturn(record);
        when(classScopeService.isTeacherOfClass(5L, 7L)).thenReturn(true);
        when(examService.customGetExamRecordById(42)).thenReturn(record);

        assertThat(controller.getExamRecordById(42, request).getData()).isSameAs(record);
        verify(classScopeService, never()).getTeacherClassIds(5L);
    }

    @Test
    void legacyRecordWithUserIdUsesCurrentMembershipForTeacherScope() {
        MockHttpServletRequest request = request("TEACHER", 5L);
        ExamRecord record = new ExamRecord();
        record.setUserId(11L);
        when(examRecordService.getById(42)).thenReturn(record);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of(11L));
        when(examService.customGetExamRecordById(42)).thenReturn(record);

        assertThat(controller.getExamRecordById(42, request).getData()).isSameAs(record);
    }

    @Test
    void adminCanReadSnapshotIrrespectiveOfClass() {
        MockHttpServletRequest request = request("ADMIN", 1L);
        ExamRecord record = new ExamRecord();
        record.setUserId(11L);
        record.setClassId(7L);
        when(examService.customGetExamRecordById(42)).thenReturn(record);

        assertThat(controller.getExamRecordById(42, request).getData()).isSameAs(record);
        verifyNoInteractions(classScopeService);
    }

    private MockHttpServletRequest request(String role, Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userRole", role);
        request.setAttribute("userId", userId);
        return request;
    }

    private Paper paper(int id) {
        Paper paper = new Paper();
        paper.setId((long) id);
        paper.setName("Scope test");
        return paper;
    }
}
