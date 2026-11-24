package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.mapper.ClazzMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassServiceUpdateTest {

    private static final long CLASS_ID = 6L;

    private final ClassServiceImpl service = new ClassServiceImpl();
    private final ClazzMapper clazzMapper = mock(ClazzMapper.class);

    @BeforeAll
    static void initLambdaColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "class-update-test"),
                Clazz.class);
    }

    @BeforeEach
    void setUp() {
        Clazz existing = new Clazz();
        existing.setId(CLASS_ID);
        existing.setName("培训一班");
        existing.setStartDate(LocalDate.of(2026, 1, 1));
        existing.setEndDate(LocalDate.of(2026, 4, 1));
        when(clazzMapper.selectById(CLASS_ID)).thenReturn(existing);
        when(clazzMapper.selectCount(any())).thenReturn(0L);
        when(clazzMapper.update(any(Clazz.class), any())).thenReturn(1);
        ReflectionTestUtils.setField(service, "baseMapper", clazzMapper);
    }

    @Test
    void updateWritesNullDatesSoOptionalDatesCanBeCleared() {
        Clazz request = new Clazz();
        request.setId(CLASS_ID);
        request.setName("培训一班");
        request.setDirection("");
        request.setStartDate(null);
        request.setEndDate(null);

        service.updateClass(request);

        ArgumentCaptor<Clazz> entity = ArgumentCaptor.forClass(Clazz.class);
        ArgumentCaptor<LambdaUpdateWrapper<Clazz>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(clazzMapper).update(entity.capture(), update.capture());
        assertThat(entity.getValue().getStartDate()).isNull();
        assertThat(entity.getValue().getEndDate()).isNull();
        assertThat(entity.getValue().getDirection()).isEmpty();
        assertThat(update.getValue().getSqlSet()).contains("start_date=", "end_date=");
        assertThat(update.getValue().getParamNameValuePairs().values()).containsExactly(null, null);
    }
}
