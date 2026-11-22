package com.atguigu.exam.service.impl;

import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.vo.WrongQuestionVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * 错题本分页参数归一化（M4）单元测试
 *
 * 通过公开方法 pageWrongQuestions 观察真正下发到 Mapper 的 Page 参数，
 * 不启动 Spring 容器；分类/筛选参数传 null 时不会访问其他 Mapper。
 */
@ExtendWith(MockitoExtension.class)
class WrongQuestionServiceImplTest {

    private static final Long STUDENT_ID = 7L;

    @Mock
    private AnswerRecordMapper answerRecordMapper;

    private WrongQuestionServiceImpl wrongQuestionService;

    @BeforeEach
    void setUp() {
        wrongQuestionService = new WrongQuestionServiceImpl();
        ReflectionTestUtils.setField(wrongQuestionService, "answerRecordMapper", answerRecordMapper);
    }

    @Test
    void 错题本_size超上限截断为100() {
        Page<WrongQuestionVo> page = queryAndCapturePage(1, 99999);
        assertEquals(1L, page.getCurrent());
        assertEquals(100L, page.getSize(), "size=99999 应被截断为上限 100");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void 错题本_size为非正数回退默认条数(int size) {
        Page<WrongQuestionVo> page = queryAndCapturePage(1, size);
        assertEquals(10L, page.getSize(), "非正数 size 应回退默认每页 10 条");
    }

    @Test
    void 错题本_分页参数为空时取第一页默认条数() {
        Page<WrongQuestionVo> page = queryAndCapturePage(null, null);
        assertEquals(1L, page.getCurrent());
        assertEquals(10L, page.getSize());
    }

    @Test
    void 错题本_page超上限截断为10000() {
        Page<WrongQuestionVo> page = queryAndCapturePage(99999, 10);
        assertEquals(10000L, page.getCurrent(), "超大页码应被截断为上限 10000");
        assertEquals(10L, page.getSize());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void 错题本_page为非正数时取第一页(int pageNo) {
        Page<WrongQuestionVo> page = queryAndCapturePage(pageNo, 10);
        assertEquals(1L, page.getCurrent(), "非正数页码应回退到第 1 页");
        assertEquals(10L, page.getSize());
    }

    /**
     * 调用公开分页方法并捕获实际传给 Mapper 的 Page 对象。
     * 本页无记录时 fillQuestionDetails 直接返回，不会触发选项/答案/分类查询。
     */
    @SuppressWarnings("unchecked")
    private Page<WrongQuestionVo> queryAndCapturePage(Integer pageNo, Integer size) {
        wrongQuestionService.pageWrongQuestions(STUDENT_ID, pageNo, size,
                null, null, null, false, false);

        ArgumentCaptor<Page<WrongQuestionVo>> captor = ArgumentCaptor.forClass(Page.class);
        verify(answerRecordMapper).selectWrongQuestionPage(captor.capture(), eq(STUDENT_ID),
                isNull(), isNull(), isNull(), eq(false));
        return captor.getValue();
    }
}
