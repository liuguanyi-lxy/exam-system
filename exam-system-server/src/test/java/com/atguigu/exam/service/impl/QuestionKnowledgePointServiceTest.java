package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.KnowledgePoint;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionKnowledgePoint;
import com.atguigu.exam.mapper.KnowledgePointMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionKnowledgePointMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.vo.KnowledgePointItemVo;
import com.atguigu.exam.vo.QuestionPageVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionKnowledgePointServiceTest {

    private static final Long QUESTION_ID = 5L;
    private static final Long OPERATOR_ID = 9L;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionAnswerMapper questionAnswerMapper;
    @Mock
    private QuestionChoiceMapper questionChoiceMapper;
    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<QuestionKnowledgePoint>> linkCondition;
    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<Question>> questionCondition;

    private QuestionServiceImpl questionService;

    @BeforeAll
    static void initLambdaColumns() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "question-knowledge-test");
        TableInfoHelper.initTableInfo(assistant, Question.class);
        TableInfoHelper.initTableInfo(assistant, QuestionKnowledgePoint.class);
    }

    @BeforeEach
    void setUp() {
        questionService = new QuestionServiceImpl();
        ReflectionTestUtils.setField(questionService, "baseMapper", questionMapper);
        ReflectionTestUtils.setField(questionService, "questionMapper", questionMapper);
        ReflectionTestUtils.setField(questionService, "questionAnswerMapper", questionAnswerMapper);
        ReflectionTestUtils.setField(questionService, "questionChoiceMapper", questionChoiceMapper);
        ReflectionTestUtils.setField(questionService, "knowledgePointMapper", knowledgePointMapper);
        ReflectionTestUtils.setField(questionService, "questionKnowledgePointMapper", questionKnowledgePointMapper);
        KnowledgePointServiceImpl knowledgePointService = new KnowledgePointServiceImpl();
        ReflectionTestUtils.setField(knowledgePointService, "baseMapper", knowledgePointMapper);
        ReflectionTestUtils.setField(knowledgePointService, "questionKnowledgePointMapper", questionKnowledgePointMapper);
        ReflectionTestUtils.setField(questionService, "knowledgePointService", knowledgePointService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"knowledgePointIds\":null}"})
    // 普通更新省略或传入 null 时不修改原有知识点标注
    void updateKeepsExistingKnowledgePointsWhenPayloadOmitsValue(String json) throws JsonProcessingException {
        Question question = editableQuestion(json);
        when(questionMapper.updateById(question)).thenReturn(1);

        questionService.customUpdateQuestion(question);

        verify(questionMapper).updateById(question);
        verify(questionAnswerMapper).insert(question.getAnswer());
        verifyNoInteractions(knowledgePointMapper, questionKnowledgePointMapper);
    }

    @Test
    // 普通更新传空数组时只删除该题的原有知识点标注
    void updateWithEmptyArrayDeletesExistingKnowledgePointLinks() throws JsonProcessingException {
        Question question = editableQuestion("{\"knowledgePointIds\":[]}");
        when(questionMapper.updateById(question)).thenReturn(1);
        when(questionKnowledgePointMapper.selectList(any()))
                .thenReturn(List.of(link(QUESTION_ID, 11L), link(QUESTION_ID, 12L)));

        questionService.customUpdateQuestion(question);

        InOrder writes = inOrder(questionMapper, questionKnowledgePointMapper);
        writes.verify(questionMapper).updateById(question);
        writes.verify(questionKnowledgePointMapper).selectList(any());
        writes.verify(questionKnowledgePointMapper).delete(linkCondition.capture());
        LambdaQueryWrapper<QuestionKnowledgePoint> deletion = linkCondition.getValue();
        assertThat(deletion.getSqlSegment()).contains("question_id =", "knowledge_point_id IN");
        assertThat(deletion.getParamNameValuePairs().values()).containsExactlyInAnyOrder(QUESTION_ID, 11L, 12L);
        assertThat(question.getKnowledgePoints()).isEmpty();
        verifyNoMoreInteractions(questionKnowledgePointMapper);
        verifyNoInteractions(knowledgePointMapper);
    }

    @Test
    // 普通创建使用新增题目的 ID 保存知识点标注并回显
    void createSavesKnowledgePointLinksByNewQuestionId() throws JsonProcessingException {
        Question question = editableQuestion("{\"knowledgePointIds\":[11]}");
        question.setId(null);
        when(questionMapper.insert(question)).thenAnswer(invocation -> {
            question.setId(QUESTION_ID);
            return 1;
        });
        when(knowledgePointMapper.selectBatchIds(Set.of(11L))).thenReturn(List.of(point(11L, "集合", 1)));
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of());

        questionService.customSaveQuestion(question);

        verify(questionKnowledgePointMapper).insertLinks(QUESTION_ID, List.of(11L), null);
        assertThat(question.getKnowledgePoints()).containsExactly(new KnowledgePointItemVo(11L, "集合", 1));
    }

    @Test
    // 普通更新包含不存在知识点时拒绝，并且不调用关联 Mapper
    void updateRejectsMissingKnowledgePointWithoutLinkWrites() throws JsonProcessingException {
        Question question = editableQuestion("{\"knowledgePointIds\":[11,99]}");
        when(questionMapper.updateById(question)).thenReturn(1);
        when(knowledgePointMapper.selectBatchIds(Set.of(11L, 99L))).thenReturn(List.of(point(11L, "集合", 1)));

        var error = assertThrows(IllegalArgumentException.class, () -> questionService.customUpdateQuestion(question));

        assertThat(error).hasMessage("知识点不存在");
        verifyNoInteractions(questionKnowledgePointMapper);
    }

    @Test
    // 专用替换拿到题目锁后发现无效知识点时不调用关联 Mapper
    void replaceRejectsMissingKnowledgePointWithoutLinkWrites() {
        when(questionMapper.lockActiveQuestion(QUESTION_ID)).thenReturn(QUESTION_ID);
        when(knowledgePointMapper.selectBatchIds(Set.of(11L, 99L))).thenReturn(List.of(point(11L, "集合", 1)));

        var error = assertThrows(IllegalArgumentException.class,
                () -> questionService.replaceQuestionKnowledgePoints(QUESTION_ID, List.of(11L, 99L), OPERATOR_ID));

        assertThat(error).hasMessage("知识点不存在");
        verify(questionMapper).lockActiveQuestion(QUESTION_ID);
        verifyNoInteractions(questionKnowledgePointMapper);
    }

    @Test
    // 重复替换相同集合时不重复写入且回显一致
    void repeatedReplacementWithSameSetDoesNotRepeatWrites() {
        when(questionMapper.lockActiveQuestion(QUESTION_ID)).thenReturn(QUESTION_ID);
        when(knowledgePointMapper.selectBatchIds(Set.of(11L, 12L)))
                .thenReturn(List.of(point(11L, "集合", 2), point(12L, "变量", 1)));
        when(questionKnowledgePointMapper.selectList(any()))
                .thenReturn(List.of(link(QUESTION_ID, 11L), link(QUESTION_ID, 13L)))
                .thenReturn(List.of(link(QUESTION_ID, 11L), link(QUESTION_ID, 12L)));

        var first = questionService.replaceQuestionKnowledgePoints(QUESTION_ID, List.of(11L, 12L, 12L), OPERATOR_ID);
        var second = questionService.replaceQuestionKnowledgePoints(QUESTION_ID, List.of(12L, 11L), OPERATOR_ID);

        assertThat(first).containsExactly(new KnowledgePointItemVo(12L, "变量", 1), new KnowledgePointItemVo(11L, "集合", 2));
        assertThat(second).isEqualTo(first);
        InOrder writes = inOrder(questionMapper, questionKnowledgePointMapper);
        writes.verify(questionMapper).lockActiveQuestion(QUESTION_ID);
        writes.verify(questionKnowledgePointMapper).selectList(any());
        writes.verify(questionKnowledgePointMapper).delete(linkCondition.capture());
        writes.verify(questionKnowledgePointMapper).insertLinks(QUESTION_ID, List.of(12L), OPERATOR_ID);
        writes.verify(questionMapper).lockActiveQuestion(QUESTION_ID);
        writes.verify(questionKnowledgePointMapper).selectList(any());
        verifyNoMoreInteractions(questionKnowledgePointMapper);
        LambdaQueryWrapper<QuestionKnowledgePoint> deletion = linkCondition.getValue();
        assertThat(deletion.getSqlSegment()).contains("question_id =", "knowledge_point_id IN");
        assertThat(deletion.getParamNameValuePairs().values()).containsExactlyInAnyOrder(QUESTION_ID, 13L);
    }

    @Test
    // 列表按知识点关联的题号筛选，并批量回显本页的全部标注
    void listFiltersByKnowledgePointAndLoadsAnnotationsForAllRecords() {
        Question first = new Question();
        first.setId(QUESTION_ID);
        Question second = new Question();
        second.setId(6L);
        Page<Question> page = new Page<>(1, 10);
        QuestionPageVo filter = new QuestionPageVo();
        filter.setKnowledgePointId(11L);
        when(knowledgePointMapper.selectById(11L)).thenReturn(point(11L, "集合", 1));
        when(questionKnowledgePointMapper.selectList(any()))
                .thenReturn(List.of(link(QUESTION_ID, 11L), link(6L, 11L)))
                .thenReturn(List.of(link(QUESTION_ID, 12L), link(QUESTION_ID, 11L), link(6L, 11L)));
        when(questionMapper.selectPage(same(page), any())).thenAnswer(invocation -> page.setRecords(List.of(first, second)));
        when(knowledgePointMapper.selectBatchIds(List.of(12L, 11L)))
                .thenReturn(List.of(point(12L, "变量", 2), point(11L, "集合", 1)));

        questionService.customPageQuery(page, filter);

        InOrder reads = inOrder(questionKnowledgePointMapper, questionMapper, knowledgePointMapper);
        reads.verify(questionKnowledgePointMapper).selectList(linkCondition.capture());
        reads.verify(questionMapper).selectPage(eq(page), questionCondition.capture());
        reads.verify(questionKnowledgePointMapper).selectList(linkCondition.capture());
        reads.verify(knowledgePointMapper).selectBatchIds(List.of(12L, 11L));
        verify(questionKnowledgePointMapper, org.mockito.Mockito.times(2)).selectList(any());
        verify(knowledgePointMapper).selectById(11L);
        verify(knowledgePointMapper).selectBatchIds(List.of(12L, 11L));
        verifyNoMoreInteractions(questionKnowledgePointMapper, knowledgePointMapper);

        LambdaQueryWrapper<QuestionKnowledgePoint> filterLinks = linkCondition.getAllValues().get(0);
        assertThat(filterLinks.getSqlSegment()).contains("knowledge_point_id =");
        assertThat(filterLinks.getParamNameValuePairs().values()).containsExactly(11L);
        assertThat(questionCondition.getValue().getSqlSegment()).contains("id IN");
        assertThat(questionCondition.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(QUESTION_ID, 6L);
        LambdaQueryWrapper<QuestionKnowledgePoint> pageLinks = linkCondition.getAllValues().get(1);
        assertThat(pageLinks.getSqlSegment()).contains("question_id IN");
        assertThat(pageLinks.getParamNameValuePairs().values()).containsExactlyInAnyOrder(QUESTION_ID, 6L);
        var collection = new KnowledgePointItemVo(11L, "集合", 1);
        var variable = new KnowledgePointItemVo(12L, "变量", 2);
        assertThat(first.getKnowledgePoints()).containsExactly(collection, variable);
        assertThat(second.getKnowledgePoints()).containsExactly(collection);
    }

    private static Question editableQuestion(String json) throws JsonProcessingException {
        Question question = JSON.readValue(json, Question.class);
        question.setId(QUESTION_ID);
        question.setTitle("集合的声明方式");
        question.setType("TEXT");
        question.setAnswer(new QuestionAnswer());
        return question;
    }

    private static QuestionKnowledgePoint link(Long questionId, Long knowledgePointId) {
        QuestionKnowledgePoint link = new QuestionKnowledgePoint();
        link.setQuestionId(questionId);
        link.setKnowledgePointId(knowledgePointId);
        return link;
    }

    private static KnowledgePoint point(Long id, String name, int sort) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setName(name);
        point.setSort(sort);
        return point;
    }
}
