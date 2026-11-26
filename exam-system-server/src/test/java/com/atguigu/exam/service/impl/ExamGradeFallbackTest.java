package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.AiService;
import com.atguigu.exam.service.AnswerRecordService;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.vo.BatchGradingItemVo;
import com.atguigu.exam.vo.GradingResult;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M7 AI 判卷降级链路回归用例（规格 docs/specs/m7-test-deploy.md A2）。
 *
 * 链路：批量 AI 判卷失败 → 回退单题并行判卷；单题仍失败 → 该题标记判卷失败
 * （ai_correction 可识别）且不阻断整卷出分，其余题正常判卷。
 */
@ExtendWith(MockitoExtension.class)
class ExamGradeFallbackTest {

    private static final Integer RECORD_ID = 200;
    private static final Long CHOICE_QID = 1L;
    private static final Long TEXT_QID = 2L;

    @Mock
    private PaperQuestionService paperQuestionService;
    @Mock
    private AnswerRecordService answerRecordService;
    @Mock
    private AiService aiService;
    @Mock
    private ExamRecordMapper examRecordMapper;

    private ExamServiceImpl service;
    private ExamRecord record;

    @BeforeAll
    static void initLambdaColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "exam-grade-fallback-test"),
                ExamRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "exam-grade-fallback-test-pq"),
                PaperQuestion.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new ExamServiceImpl());
        ReflectionTestUtils.setField(service, "paperQuestionService", paperQuestionService);
        ReflectionTestUtils.setField(service, "answerRecordService", answerRecordService);
        ReflectionTestUtils.setField(service, "aiService", aiService);
        ReflectionTestUtils.setField(service, "baseMapper", examRecordMapper);
        // 回退路径用真实线程池执行单题判卷（同步短队列即可）
        ReflectionTestUtils.setField(service, "examExecutor",
                new ThreadPoolExecutor(2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()));

        record = new ExamRecord();
        record.setId(RECORD_ID.longValue());
        record.setExamId(3);
        record.setUserId(7L);
        record.setStatus("已结束");

        Paper paper = new Paper();
        paper.setId(3L);
        paper.setName("降级测试卷");
        paper.setTotalScore(BigDecimal.valueOf(20));
        Question choice = question(CHOICE_QID, "CHOICE", "Java 中哪个是关键字？", "A",
                List.of(choice("instanceof", false), choice("goto", true)));
        Question text = question(TEXT_QID, "TEXT", "简述封装的含义", "将状态与行为绑定并隐藏内部细节", null);
        paper.setQuestions(List.of(choice, text));
        record.setPaper(paper);
        record.setAnswerRecords(List.of(
                answerRecord(CHOICE_QID.intValue(), "goto"),
                answerRecord(TEXT_QID.intValue(), "封装就是藏起来")));
        doReturn(record).when(service).customGetExamRecordById(RECORD_ID);

        // 试卷题目分值关联行
        PaperQuestion pq1 = new PaperQuestion();
        pq1.setPaperId(3);
        pq1.setQuestionId(CHOICE_QID);
        pq1.setScore(BigDecimal.TEN);
        PaperQuestion pq2 = new PaperQuestion();
        pq2.setPaperId(3);
        pq2.setQuestionId(TEXT_QID);
        pq2.setScore(BigDecimal.TEN);
        when(paperQuestionService.list(any(Wrapper.class))).thenReturn(List.of(pq1, pq2));
    }

    @Test
    void batchFailureFallsBackToSingleQuestionGradingAndScoresPaper() {
        // 批量判卷整体失败 → 回退单题并行判卷成功
        doThrow(new RuntimeException("AI 服务限流")).when(aiService).gradingSubjectiveBatch(any());
        when(aiService.gradingSubjectiveQuestion(any(Question.class), anyString(), anyInt()))
                .thenReturn(new GradingResult(8, "要点基本覆盖", "缺少数据隐藏的举例"));

        ExamRecord graded = service.gradeExam(RECORD_ID, 7L, true);

        assertThat(graded.getStatus()).isEqualTo("已批阅");
        assertThat(graded.getScore()).isEqualTo(18); // 客观题 10 + 简答 8
        Map<Integer, AnswerRecord> byQid = capturedAnswers();
        assertThat(byQid.get(TEXT_QID.intValue()).getScore()).isEqualTo(8);
        assertThat(byQid.get(TEXT_QID.intValue()).getIsCorrect()).isEqualTo(2); // 部分正确
        assertThat(byQid.get(TEXT_QID.intValue()).getAiCorrection()).isEqualTo("缺少数据隐藏的举例");
        assertThat(byQid.get(CHOICE_QID.intValue()).getIsCorrect()).isEqualTo(1);
    }

    @Test
    void singleQuestionFailureMarksErrorWithoutBlockingPaper() {
        // 批量失败 + 单题也失败：该题标记判卷失败，客观题正常出分，整卷不抛异常
        doThrow(new RuntimeException("AI 服务限流")).when(aiService).gradingSubjectiveBatch(any());
        doThrow(new RuntimeException("连接超时")).when(aiService)
                .gradingSubjectiveQuestion(any(Question.class), anyString(), anyInt());
        when(aiService.buildSummary(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn("AI 总评");

        ExamRecord graded = service.gradeExam(RECORD_ID, 7L, true);

        assertThat(graded.getStatus()).isEqualTo("已批阅");
        assertThat(graded.getScore()).isEqualTo(10); // 只有客观题得分
        Map<Integer, AnswerRecord> byQid = capturedAnswers();
        assertThat(byQid.get(TEXT_QID.intValue()).getScore()).isZero();
        assertThat(byQid.get(TEXT_QID.intValue()).getIsCorrect()).isZero();
        assertThat(byQid.get(TEXT_QID.intValue()).getAiCorrection())
                .as("判卷失败的题必须留下可识别的 ai_correction，供人工复核筛查")
                .contains("判题过程出现问题").contains("错误类型");
        assertThat(byQid.get(CHOICE_QID.intValue()).getScore()).isEqualTo(10);
    }

    @Test
    void summaryFailureDoesNotBlockScorePersistence() {
        // AI 总评失败：分数与状态照常落库，评语退化为兜底文案
        doThrow(new RuntimeException("AI 服务限流")).when(aiService).gradingSubjectiveBatch(any());
        doThrow(new RuntimeException("连接超时")).when(aiService)
                .gradingSubjectiveQuestion(any(Question.class), anyString(), anyInt());
        doThrow(new RuntimeException("AI 服务不可用")).when(aiService).buildSummary(anyInt(), anyInt(), anyInt(), anyInt());

        ExamRecord graded = service.gradeExam(RECORD_ID, 7L, true);

        assertThat(graded.getScore()).isEqualTo(10);
        assertThat(graded.getStatus()).isEqualTo("已批阅");
        assertThat(graded.getSummary()).contains("AI 评语生成失败");
    }

    private Map<Integer, AnswerRecord> capturedAnswers() {
        ArgumentCaptor<List<AnswerRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(answerRecordService).updateBatchById(captor.capture());
        return captor.getValue().stream()
                .collect(Collectors.toMap(AnswerRecord::getQuestionId, Function.identity()));
    }

    private Question question(Long id, String type, String title, String answer, List<QuestionChoice> choices) {
        Question q = new Question();
        q.setId(id);
        q.setType(type);
        q.setTitle(title);
        q.setChoices(choices);
        QuestionAnswer qa = new QuestionAnswer();
        qa.setAnswer(answer);
        q.setAnswer(qa);
        return q;
    }

    private QuestionChoice choice(String content, boolean correct) {
        QuestionChoice c = new QuestionChoice();
        c.setContent(content);
        c.setSort(1);
        c.setIsCorrect(correct);
        return c;
    }

    private AnswerRecord answerRecord(Integer questionId, String userAnswer) {
        return new AnswerRecord(RECORD_ID, questionId, userAnswer);
    }
}
