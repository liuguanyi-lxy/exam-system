package com.atguigu.exam.controller;

import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.ExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * M7 考前答案泄露回归用例（规格 docs/specs/m7-test-deploy.md A1）。
 *
 * 口径：学生查考试记录详情，成绩未出来之前（进行中/已交卷未批阅）不得下发
 * question.answer / analysis / choices[].isCorrect；已批阅后完整返回（结果页展示解析）；
 * 教师/管理员不受脱敏限制。
 */
@ExtendWith(MockitoExtension.class)
class ExamRecordDetailSanitizeTest {

    private static final Long STUDENT_ID = 7L;
    private static final Integer RECORD_ID = 100;

    @Mock
    private ExamService examService;
    @Mock
    private ExamRecordService examRecordService;
    @Mock
    private ClassScopeService classScopeService;

    private ExamRecordController controller;

    @BeforeEach
    void setUp() {
        controller = new ExamRecordController();
        ReflectionTestUtils.setField(controller, "examService", examService);
        ReflectionTestUtils.setField(controller, "examRecordService", examRecordService);
        ReflectionTestUtils.setField(controller, "classScopeService", classScopeService);
    }

    @Test
    void inProgressRecordHidesAnswersFromStudent() {
        assertSanitizedForStatus("进行中");
    }

    @Test
    void finishedButUngradedRecordHidesAnswersFromStudent() {
        assertSanitizedForStatus("已结束");
    }

    @Test
    void gradedRecordShowsAnswersToStudentForReview() {
        ExamRecord record = recordWithPaper("已批阅");
        when(examService.customGetExamRecordById(RECORD_ID)).thenReturn(record);

        Result<ExamRecord> result = controller.getExamRecordById(RECORD_ID, request(STUDENT_ID));

        assertThat(result.getCode()).isEqualTo(200);
        Question q = result.getData().getPaper().getQuestions().get(0);
        assertThat(q.getAnswer()).as("已批阅后结果页需要展示正确答案，不得脱敏").isNotNull();
        assertThat(q.getAnalysis()).as("已批阅后解析可见").isNotNull();
        assertThat(q.getChoices().get(0).getIsCorrect()).as("已批阅后选项对错可见").isNotNull();
    }

    @Test
    void teacherSeesUngradedRecordWithoutSanitizing() {
        ExamRecord record = recordWithPaper("已结束");
        when(examService.customGetExamRecordById(RECORD_ID)).thenReturn(record);
        // TEACHER 分支先走 getById + 负责班级学员归属校验
        when(examRecordService.getById(RECORD_ID)).thenReturn(record);
        when(classScopeService.getTeacherClassIds(5L)).thenReturn(List.of(1L));
        when(classScopeService.getStudentUserIds(anyList())).thenReturn(Set.of(STUDENT_ID));

        Result<ExamRecord> result = controller.getExamRecordById(RECORD_ID, teacherRequest());

        assertThat(result.getCode()).isEqualTo(200);
        Question q = result.getData().getPaper().getQuestions().get(0);
        assertThat(q.getAnswer()).as("教师查看未批阅记录保留完整答案").isNotNull();
        assertThat(q.getAnalysis()).isNotNull();
    }

    @Test
    void studentCannotReadOthersUngradedRecord() {
        // 脱敏的前提是归属校验：先确保非本人记录在脱敏逻辑之前就被拒绝
        ExamRecord record = recordWithPaper("进行中");
        record.setUserId(999L);
        when(examService.customGetExamRecordById(RECORD_ID)).thenReturn(record);

        assertThatThrownBy(() -> controller.getExamRecordById(RECORD_ID, request(STUDENT_ID)))
                .isInstanceOf(ClassScopeException.class);
    }

    private void assertSanitizedForStatus(String status) {
        ExamRecord record = recordWithPaper(status);
        when(examService.customGetExamRecordById(RECORD_ID)).thenReturn(record);

        Result<ExamRecord> result = controller.getExamRecordById(RECORD_ID, request(STUDENT_ID));

        assertThat(result.getCode()).isEqualTo(200);
        Question q = result.getData().getPaper().getQuestions().get(0);
        assertThat(q.getAnswer()).as("未批阅记录不得下发标准答案").isNull();
        assertThat(q.getAnalysis()).as("未批阅记录不得下发解析").isNull();
        assertThat(q.getChoices().get(0).getIsCorrect()).as("未批阅记录不得下发选项对错").isNull();
    }

    /** 构造带试卷与一道含完整答案的选择题的考试记录，归属 STUDENT_ID */
    private ExamRecord recordWithPaper(String status) {
        QuestionChoice choice = new QuestionChoice();
        choice.setContent("选项A");
        choice.setSort(1);
        choice.setIsCorrect(true);

        Question question = new Question();
        question.setId(1L);
        question.setTitle("单选题题干");
        question.setType("CHOICE");
        question.setAnalysis("解析内容");
        question.setChoices(List.of(choice));
        QuestionAnswer answer = new QuestionAnswer();
        answer.setAnswer("选项A");
        question.setAnswer(answer);

        Paper paper = new Paper();
        paper.setId(3L);
        paper.setName("测试试卷");
        paper.setQuestions(List.of(question));

        ExamRecord record = new ExamRecord();
        record.setId(RECORD_ID.longValue());
        record.setStatus(status);
        record.setUserId(STUDENT_ID);
        record.setPaper(paper);
        return record;
    }

    private MockHttpServletRequest request(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        request.setAttribute("userRole", "STUDENT");
        return request;
    }

    private MockHttpServletRequest teacherRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 5L);
        request.setAttribute("userRole", "TEACHER");
        return request;
    }
}
