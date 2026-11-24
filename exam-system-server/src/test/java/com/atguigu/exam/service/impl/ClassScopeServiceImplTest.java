package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassScopeServiceImplTest {

    @Mock
    private ClassMemberMapper classMemberMapper;
    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ClassMember>> query;

    private ClassScopeServiceImpl service;

    @BeforeAll
    static void initLambdaColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "class-scope-test"),
                ClassMember.class);
    }

    @BeforeEach
    void setUp() {
        service = new ClassScopeServiceImpl();
        ReflectionTestUtils.setField(service, "classMemberMapper", classMemberMapper);
    }

    @Test
    void getTeacherClassIdsSelectsTeacherMembershipsOrderedByClassId() {
        when(classMemberMapper.selectList(any())).thenReturn(List.of(member(10L, 5L), member(20L, 5L)));

        assertThat(service.getTeacherClassIds(5L)).containsExactly(10L, 20L);

        verify(classMemberMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("user_id =", "role_in_class =", "ORDER BY class_id ASC");
        assertThat(query.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(5L, "TEACHER");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getStudentUserIdsReturnsEmptyWithoutQueryWhenNoClassIds(List<Long> classIds) {
        assertThat(service.getStudentUserIds(classIds)).isEmpty();

        verifyNoInteractions(classMemberMapper);
    }

    @Test
    void getStudentUserIdsDeduplicatesAndSortsStudentsAcrossClasses() {
        when(classMemberMapper.selectList(any())).thenReturn(List.of(
                member(10L, 31L), member(20L, 16L), member(20L, 31L), member(10L, 7L)));

        assertThat(service.getStudentUserIds(List.of(10L, 20L))).containsExactly(7L, 16L, 31L);

        verify(classMemberMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("class_id IN", "role_in_class =");
        assertThat(query.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(10L, 20L, "STUDENT");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isTeacherOfClassReturnsMapperExistenceResult(boolean exists) {
        when(classMemberMapper.exists(any())).thenReturn(exists);

        assertThat(service.isTeacherOfClass(5L, 10L)).isEqualTo(exists);

        verify(classMemberMapper).exists(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("user_id =", "role_in_class =", "class_id =");
        assertThat(query.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(5L, "TEACHER", 10L);
        verifyNoMoreInteractions(classMemberMapper);
    }

    private static ClassMember member(Long classId, Long userId) {
        ClassMember member = new ClassMember();
        member.setClassId(classId);
        member.setUserId(userId);
        return member;
    }
}
