package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.AnswerRecordService;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交卷题目归属校验（H1）单元测试
 *
 * 不启动 Spring 容器、不连数据库：ExamServiceImpl 继承的 getById/updateById 走 baseMapper，
 * 因此把 ExamRecordMapper mock 注入到 ServiceImpl.baseMapper；其余协作服务全部 Mockito mock。
 */
@ExtendWith(MockitoExtension.class)
class ExamSubmitAnswerTest {

    private static final Integer EXAM_RECORD_ID = 100;
    private static final Long STUDENT_ID = 7L;
    private static final Integer PAPER_ID = 3;

    @Mock
    private PaperService paperService;
    @Mock
    private PaperQuestionService paperQuestionService;
    @Mock
    private AnswerRecordService answerRecordService;
    @Mock
    private UserService userService;
    @Mock
    private ExamRecordMapper examRecordMapper;

    private ExamServiceImpl examService;

    @BeforeEach
    void setUp() {
        examService = new ExamServiceImpl();
        ReflectionTestUtils.setField(examService, "paperService", paperService);
        ReflectionTestUtils.setField(examService, "paperQuestionService", paperQuestionService);
        ReflectionTestUtils.setField(examService, "answerRecordService", answerRecordService);
        ReflectionTestUtils.setField(examService, "userService", userService);
        // ServiceImpl.getById / updateById 内部调用 baseMapper，注入 mock 避免真库
        ReflectionTestUtils.setField(examService, "baseMapper", examRecordMapper);
    }

    @Test
    void 提交题目不属于本次试卷_抛异常且不落库() {
        ExamRecord record = inProgressRecord(STUDENT_ID);
        when(examRecordMapper.selectById(EXAM_RECORD_ID)).thenReturn(record);
        // 试卷只包含题目 1，提交题目 999 属于越权落库
        when(paperQuestionService.list(ArgumentMatchers.<Wrapper<PaperQuestion>>any())).thenReturn(List.of(paperQuestionOf(1L)));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> examService.customSubmitAnswer(EXAM_RECORD_ID,
                        List.of(answer(999, "A")), STUDENT_ID, false));

        assertTrue(exception.getMessage().contains("不属于"),
                "异常消息应明确提示题目不属于试卷，实际：" + exception.getMessage());
        verify(answerRecordService, never()).saveBatch(anyCollection());
        verify(examRecordMapper, never()).updateById(any(ExamRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 提交题目属于试卷_保存答题记录并结束考试() {
        ExamRecord record = inProgressRecord(STUDENT_ID);
        when(examRecordMapper.selectById(EXAM_RECORD_ID)).thenReturn(record);
        when(paperQuestionService.list(ArgumentMatchers.<Wrapper<PaperQuestion>>any())).thenReturn(List.of(paperQuestionOf(1L), paperQuestionOf(2L)));

        examService.customSubmitAnswer(EXAM_RECORD_ID,
                List.of(answer(2, "B"), answer(1, "A")), STUDENT_ID, false);

        ArgumentCaptor<List<AnswerRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(answerRecordService).saveBatch(captor.capture());
        List<AnswerRecord> savedRecords = captor.getValue();
        assertEquals(2, savedRecords.size());

        Map<Integer, String> savedAnswers = new HashMap<>();
        for (AnswerRecord saved : savedRecords) {
            assertEquals(EXAM_RECORD_ID, saved.getExamRecordId(), "答题记录必须挂在本次考试记录下");
            savedAnswers.put(saved.getQuestionId(), saved.getUserAnswer());
        }
        assertEquals(Map.of(1, "A", 2, "B"), savedAnswers);

        assertEquals(ExamStatus.FINISHED, record.getStatus(), "交卷后状态应变为已结束");
        assertNotNull(record.getEndTime(), "交卷后应写入结束时间");
        verify(examRecordMapper).updateById(record);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 同一题目重复提交_只保留一条答题记录且后提交答案生效() {
        ExamRecord record = inProgressRecord(STUDENT_ID);
        when(examRecordMapper.selectById(EXAM_RECORD_ID)).thenReturn(record);
        when(paperQuestionService.list(ArgumentMatchers.<Wrapper<PaperQuestion>>any())).thenReturn(List.of(paperQuestionOf(1L), paperQuestionOf(2L)));

        examService.customSubmitAnswer(EXAM_RECORD_ID,
                List.of(answer(1, "首次答案"), answer(1, "覆盖后答案"), answer(2, "C")),
                STUDENT_ID, false);

        ArgumentCaptor<List<AnswerRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(answerRecordService).saveBatch(captor.capture());
        List<AnswerRecord> savedRecords = captor.getValue();

        assertEquals(2, savedRecords.size(), "同一 questionId 只能落一条答题记录");
        assertEquals(Integer.valueOf(1), savedRecords.get(0).getQuestionId(), "去重后应保留首次出现顺序");
        assertEquals("覆盖后答案", savedRecords.get(0).getUserAnswer(), "后出现的答案应覆盖先出现的答案");
        assertEquals(Integer.valueOf(2), savedRecords.get(1).getQuestionId());
    }

    @ParameterizedTest
    @ValueSource(strings = {ExamStatus.FINISHED, ExamStatus.GRADED})
    void 已结束或已批阅状态_拒绝重复提交(String status) {
        ExamRecord record = inProgressRecord(STUDENT_ID);
        record.setStatus(status);
        when(examRecordMapper.selectById(EXAM_RECORD_ID)).thenReturn(record);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> examService.customSubmitAnswer(EXAM_RECORD_ID,
                        List.of(answer(1, "A")), STUDENT_ID, false));

        assertTrue(exception.getMessage().contains("已交卷"),
                "异常消息应提示重复交卷，实际：" + exception.getMessage());
        verify(answerRecordService, never()).saveBatch(anyCollection());
        verify(examRecordMapper, never()).updateById(any(ExamRecord.class));
    }

    @Test
    void 非本人考试记录_拒绝提交() {
        ExamRecord record = inProgressRecord(999L);
        when(examRecordMapper.selectById(EXAM_RECORD_ID)).thenReturn(record);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> examService.customSubmitAnswer(EXAM_RECORD_ID,
                        List.of(answer(1, "A")), STUDENT_ID, false));

        assertTrue(exception.getMessage().contains("无权操作"),
                "异常消息应提示无权操作，实际：" + exception.getMessage());
        verify(answerRecordService, never()).saveBatch(anyCollection());
    }

    @Test
    void 历史记录无考生ID且姓名不一致_拒绝提交() {
        ExamRecord record = inProgressRecord(null);
        record.setStudentName("张三");
        when(examRecordMapper.selectById(EXAM_RECORD_ID)).thenReturn(record);

        User currentUser = new User();
        currentUser.setId(STUDENT_ID);
        currentUser.setRealName("李四");
        when(userService.getById(STUDENT_ID)).thenReturn(currentUser);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> examService.customSubmitAnswer(EXAM_RECORD_ID,
                        List.of(answer(1, "A")), STUDENT_ID, false));

        assertTrue(exception.getMessage().contains("无权操作"));
        verify(answerRecordService, never()).saveBatch(anyCollection());
    }

    @Test
    void 教师或管理员代交卷_跳过归属校验() {
        // 记录归属另一个学生，教师/管理员经管理端代交卷时允许操作
        ExamRecord record = inProgressRecord(999L);
        when(examRecordMapper.selectById(EXAM_RECORD_ID)).thenReturn(record);
        when(paperQuestionService.list(ArgumentMatchers.<Wrapper<PaperQuestion>>any())).thenReturn(List.of(paperQuestionOf(1L)));

        examService.customSubmitAnswer(EXAM_RECORD_ID,
                List.of(answer(1, "A")), STUDENT_ID, true);

        verify(answerRecordService).saveBatch(anyCollection());
        assertEquals(ExamStatus.FINISHED, record.getStatus());
    }

    @Test
    void 空答案列表_仅结束考试不保存答题记录() {
        ExamRecord record = inProgressRecord(STUDENT_ID);
        when(examRecordMapper.selectById(EXAM_RECORD_ID)).thenReturn(record);

        examService.customSubmitAnswer(EXAM_RECORD_ID, List.of(), STUDENT_ID, false);

        verify(answerRecordService, never()).saveBatch(anyCollection());
        assertEquals(ExamStatus.FINISHED, record.getStatus());
        assertNotNull(record.getEndTime());
        verify(examRecordMapper).updateById(record);
    }

    private ExamRecord inProgressRecord(Long ownerId) {
        ExamRecord record = new ExamRecord();
        record.setId(EXAM_RECORD_ID.longValue());
        record.setExamId(PAPER_ID);
        record.setUserId(ownerId);
        record.setStudentName("考生");
        record.setStatus(ExamStatus.IN_PROGRESS);
        record.setStartTime(LocalDateTime.now());
        return record;
    }

    private PaperQuestion paperQuestionOf(long questionId) {
        return new PaperQuestion(PAPER_ID, questionId, BigDecimal.ONE);
    }

    private SubmitAnswerVo answer(int questionId, String userAnswer) {
        SubmitAnswerVo vo = new SubmitAnswerVo();
        vo.setQuestionId(questionId);
        vo.setUserAnswer(userAnswer);
        return vo;
    }
}
