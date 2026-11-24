package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClassServiceLifecycleTest {

    private static final Long CLASS_ID = 6L;

    private final ClassServiceImpl service = new ClassServiceImpl();
    private final ClazzMapper clazzMapper = mock(ClazzMapper.class);
    private final ClassMemberMapper memberMapper = mock(ClassMemberMapper.class);

    @BeforeAll
    static void initLambdaColumns() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "class-lifecycle-test");
        TableInfoHelper.initTableInfo(assistant, Clazz.class);
        TableInfoHelper.initTableInfo(assistant, ClassMember.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", clazzMapper);
        ReflectionTestUtils.setField(service, "classMemberMapper", memberMapper);
    }

    @Test
    void newClassIsEnabledWithNoMembers() {
        Clazz request = new Clazz();
        request.setName("  培训一班  ");
        request.setStatus("DISABLED");
        when(clazzMapper.selectCount(any())).thenReturn(0L);
        when(clazzMapper.insert(request)).thenAnswer(invocation -> {
            assertThat(request.getName()).isEqualTo("培训一班");
            assertThat(request.getStatus()).isEqualTo("ENABLED");
            request.setId(CLASS_ID);
            return 1;
        });
        when(clazzMapper.selectById(CLASS_ID)).thenReturn(request);

        Clazz created = service.createClass(request);

        assertThat(created.getId()).isEqualTo(CLASS_ID);
        assertThat(created.getStudentCount()).isZero();
        assertThat(created.getTeacherCount()).isZero();
        verifyNoInteractions(memberMapper);
    }

    @Test
    void classCanBeDisabledThenEnabledWithoutChangingMembership() {
        Clazz existing = new Clazz();
        existing.setId(CLASS_ID);
        existing.setStatus("ENABLED");
        when(clazzMapper.selectById(CLASS_ID)).thenReturn(existing);

        service.updateClassStatus(CLASS_ID, "DISABLED");
        assertThat(existing.getStatus()).isEqualTo("DISABLED");
        service.updateClassStatus(CLASS_ID, "ENABLED");
        assertThat(existing.getStatus()).isEqualTo("ENABLED");

        InOrder writes = inOrder(clazzMapper);
        writes.verify(clazzMapper).selectById(CLASS_ID);
        writes.verify(clazzMapper).updateById(existing);
        writes.verify(clazzMapper).selectById(CLASS_ID);
        writes.verify(clazzMapper).updateById(existing);
        verifyNoInteractions(memberMapper);
    }

    @Test
    void invalidStatusIsRejectedBeforeAccessingMappers() {
        assertThatThrownBy(() -> service.updateClassStatus(CLASS_ID, "UNKNOWN"))
                .hasMessageContaining("status 取值非法");
        verifyNoInteractions(clazzMapper, memberMapper);
    }

    @Test
    void deletionClearsOnlyTargetClassMembershipsBeforeRemovingClass() {
        Clazz existing = new Clazz();
        existing.setId(CLASS_ID);
        when(clazzMapper.selectById(CLASS_ID)).thenReturn(existing);

        service.deleteClass(CLASS_ID);

        ArgumentCaptor<LambdaQueryWrapper<ClassMember>> filter = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        InOrder writes = inOrder(clazzMapper, memberMapper);
        writes.verify(clazzMapper).selectById(CLASS_ID);
        writes.verify(memberMapper).delete(filter.capture());
        writes.verify(clazzMapper).deleteById(CLASS_ID);
        assertThat(filter.getValue().getSqlSegment()).contains("class_id").doesNotContain("role_in_class");
        assertThat(filter.getValue().getParamNameValuePairs().values()).containsExactly(CLASS_ID);
    }
}
