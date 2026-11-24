package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceImplScopeTest {

    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ExamRecordMapper examRecordMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private PaperMapper paperMapper;
    @Mock
    private ClassScopeService classScopeService;

    private StatsServiceImpl statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsServiceImpl();
        ReflectionTestUtils.setField(statsService, "questionMapper", questionMapper);
        ReflectionTestUtils.setField(statsService, "userMapper", userMapper);
        ReflectionTestUtils.setField(statsService, "examRecordMapper", examRecordMapper);
        ReflectionTestUtils.setField(statsService, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(statsService, "paperMapper", paperMapper);
        ReflectionTestUtils.setField(statsService, "classScopeService", classScopeService);
        when(questionMapper.selectCount(any())).thenReturn(4L);
        when(userMapper.selectCount(any())).thenAnswer(invocation -> {
            QueryWrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().contains("id =") ? 0L : 10L;
        });
        when(examRecordMapper.selectCount(any())).thenAnswer(invocation -> {
            QueryWrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().contains("user_id =") ? 0L : 20L;
        });
        when(categoryMapper.selectCount(any())).thenReturn(3L);
        when(paperMapper.selectCount(any())).thenReturn(5L);
    }

    @Test
    void teacherWithNoClassesGetsNoInstitutionUserOrExamCounts() {
        when(classScopeService.getTeacherClassIds(8L)).thenReturn(List.of());
        when(classScopeService.getStudentUserIds(List.of())).thenReturn(Set.of());

        var result = statsService.getSystemStats("TEACHER", 8L);

        assertThat(result.getUserCount()).isZero();
        assertThat(result.getExamCount()).isZero();
        assertThat(result.getTodayExamCount()).isZero();
        ArgumentCaptor<QueryWrapper<User>> users = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<ExamRecord>> exams = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(userMapper).selectCount(users.capture());
        verify(examRecordMapper, times(2)).selectCount(exams.capture());

        assertThat(users.getValue().getSqlSegment()).contains("id =");
        assertThat(users.getValue().getParamNameValuePairs().values()).contains(-1L);
        assertThat(exams.getAllValues()).hasSize(2).allSatisfy(query -> {
            assertThat(query.getSqlSegment()).contains("user_id =");
            assertThat(query.getParamNameValuePairs().values()).contains(-1L);
        });
    }
}
