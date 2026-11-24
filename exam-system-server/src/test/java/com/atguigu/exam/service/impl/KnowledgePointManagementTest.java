package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.KnowledgePoint;
import com.atguigu.exam.entity.QuestionKnowledgePoint;
import com.atguigu.exam.mapper.KnowledgePointMapper;
import com.atguigu.exam.mapper.QuestionKnowledgePointMapper;
import com.atguigu.exam.vo.KnowledgePointSortVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgePointManagementTest {

    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private QuestionKnowledgePointMapper linkMapper;
    private KnowledgePointServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgePointServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", knowledgePointMapper);
        ReflectionTestUtils.setField(service, "questionKnowledgePointMapper", linkMapper);
    }

    @Test
    void createsKnowledgePointWithTrimmedNameAndZeroReferences() {
        KnowledgePoint point = new KnowledgePoint();
        point.setName("  集合  ");
        point.setSort(4);
        when(knowledgePointMapper.insert(point)).thenAnswer(invocation -> {
            point.setId(12L);
            return 1;
        });
        when(knowledgePointMapper.selectById(12L)).thenReturn(point);

        KnowledgePoint created = service.createKnowledgePoint(point);

        assertThat(created.getName()).isEqualTo("集合");
        assertThat(created.getQuestionCount()).isZero();
        verify(knowledgePointMapper).insert(point);
    }

    @Test
    void updateReturnsCurrentReferenceCount() {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(12L);
        point.setName("集合操作");
        when(knowledgePointMapper.selectById(12L)).thenReturn(point);
        when(knowledgePointMapper.updateById(point)).thenReturn(1);
        when(linkMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThat(service.updateKnowledgePoint(point).getQuestionCount()).isEqualTo(2);
        verify(knowledgePointMapper).updateById(point);
    }

    @Test
    void referencedKnowledgePointCannotBeDeleted() {
        when(knowledgePointMapper.selectById(12L)).thenReturn(new KnowledgePoint());
        when(linkMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteKnowledgePoint(12L))
                .hasMessageContaining("2 道题");
        verify(knowledgePointMapper, never()).deleteById(12L);
    }

    @Test
    void unreferencedKnowledgePointCanBeDeleted() {
        when(knowledgePointMapper.selectById(12L)).thenReturn(new KnowledgePoint());
        when(linkMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.deleteKnowledgePoint(12L);

        verify(knowledgePointMapper).deleteById(12L);
    }

    @Test
    void sortingRejectsNullRowBeforeReadingOrWriting() {
        KnowledgePointSortVo valid = new KnowledgePointSortVo();
        valid.setId(12L);
        valid.setSort(1);

        assertThatThrownBy(() -> service.sortBatch(Arrays.asList(valid, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("排序条目必须包含知识点 ID 和排序值");
        verifyNoInteractions(knowledgePointMapper, linkMapper);
    }

    @Test
    void sortingRejectsMissingSortValueBeforeReadingOrWriting() {
        KnowledgePointSortVo incomplete = new KnowledgePointSortVo();
        incomplete.setId(12L);

        assertThatThrownBy(() -> service.sortBatch(List.of(incomplete)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("排序条目必须包含知识点 ID 和排序值");
        verifyNoInteractions(knowledgePointMapper, linkMapper);
    }
}
