package com.atguigu.exam.controller;

import com.atguigu.exam.entity.KnowledgePoint;
import com.atguigu.exam.service.KnowledgePointService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgePointControllerPageTest {

    @Test
    void pageAndSizeAreClampedBeforeCallingService() {
        KnowledgePointService service = mock(KnowledgePointService.class);
        KnowledgePointController controller = new KnowledgePointController();
        ReflectionTestUtils.setField(controller, "knowledgePointService", service);
        when(service.pageList(any(), isNull())).thenAnswer(invocation -> {
            Page<KnowledgePoint> page = invocation.getArgument(0);
            assertThat(page.getCurrent()).isEqualTo(10_000);
            assertThat(page.getSize()).isEqualTo(100);
            return page;
        });

        controller.getKnowledgePointList(Integer.MAX_VALUE, Integer.MAX_VALUE, null);
    }
}
