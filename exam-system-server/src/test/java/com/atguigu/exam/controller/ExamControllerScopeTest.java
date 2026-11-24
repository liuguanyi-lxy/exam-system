package com.atguigu.exam.controller;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.ExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamControllerScopeTest {

    @Mock
    private ExamService examService;
    @Mock
    private ExamRecordService examRecordService;
    @Mock
    private ClassScopeService classScopeService;

    private ExamController controller;

    @BeforeEach
    void setUp() {
        controller = new ExamController();
        ReflectionTestUtils.setField(controller, "examService", examService);
        ReflectionTestUtils.setField(controller, "examRecordService", examRecordService);
        ReflectionTestUtils.setField(controller, "classScopeService", classScopeService);
    }

    @Test
    void teacherCannotGradeRecordOutsideResponsibleClasses() {
        ExamRecord record = new ExamRecord();
        record.setUserId(99L);
        when(examRecordService.getById(42)).thenReturn(record);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of(11L, 12L));

        assertThatThrownBy(() -> controller.gradeExam(42, 5L, "TEACHER"))
                .isInstanceOf(ClassScopeException.class);

        verify(examService, never()).gradeExam(42, 5L, true);
    }

    @Test
    void teacherCanGradeRecordFromResponsibleClass() {
        ExamRecord record = new ExamRecord();
        record.setUserId(11L);
        when(examRecordService.getById(42)).thenReturn(record);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(7L));
        when(classScopeService.getStudentUserIds(List.of(7L))).thenReturn(Set.of(11L, 12L));

        controller.gradeExam(42, 5L, "TEACHER");

        verify(examService).gradeExam(42, 5L, true);
    }
}
