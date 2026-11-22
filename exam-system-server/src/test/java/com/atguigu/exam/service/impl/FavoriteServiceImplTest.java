package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionFavorite;
import com.atguigu.exam.mapper.QuestionFavoriteMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.vo.QuestionFavoriteVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 题目收藏单元测试：收藏/取消收藏幂等语义 + 收藏列表分页参数归一化（M4）
 *
 * 不启动 Spring 容器、不连数据库；收藏表为物理删除，无逻辑删除 SQL 干扰。
 */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long QUESTION_ID = 5L;

    @Mock
    private QuestionFavoriteMapper favoriteMapper;
    @Mock
    private QuestionMapper questionMapper;

    private FavoriteServiceImpl favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteServiceImpl();
        // ServiceImpl.save/count/remove 内部调用 baseMapper，注入 mock 避免真库
        ReflectionTestUtils.setField(favoriteService, "baseMapper", favoriteMapper);
        ReflectionTestUtils.setField(favoriteService, "questionMapper", questionMapper);
    }

    @Test
    void 收藏列表_size超上限截断为100() {
        Page<QuestionFavoriteVo> page = queryAndCapturePage(1, 99999);
        assertEquals(1L, page.getCurrent());
        assertEquals(100L, page.getSize(), "size=99999 应被截断为上限 100");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void 收藏列表_size为非正数回退默认条数(int size) {
        Page<QuestionFavoriteVo> page = queryAndCapturePage(1, size);
        assertEquals(10L, page.getSize(), "非正数 size 应回退默认每页 10 条");
    }

    @Test
    void 收藏列表_分页参数为空时取第一页默认条数() {
        Page<QuestionFavoriteVo> page = queryAndCapturePage(null, null);
        assertEquals(1L, page.getCurrent());
        assertEquals(10L, page.getSize());
    }

    @Test
    void 收藏列表_page超上限截断为10000() {
        Page<QuestionFavoriteVo> page = queryAndCapturePage(99999, 10);
        assertEquals(10000L, page.getCurrent(), "超大页码应被截断为上限 10000");
        assertEquals(10L, page.getSize());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void 收藏列表_page为非正数时取第一页(int pageNo) {
        Page<QuestionFavoriteVo> page = queryAndCapturePage(pageNo, 10);
        assertEquals(1L, page.getCurrent(), "非正数页码应回退到第 1 页");
        assertEquals(10L, page.getSize());
    }

    @Test
    void 首次收藏_写入一条收藏记录() {
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(new Question());
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.insert(any(QuestionFavorite.class))).thenReturn(1);

        favoriteService.addFavorite(STUDENT_ID, QUESTION_ID);

        ArgumentCaptor<QuestionFavorite> captor = ArgumentCaptor.forClass(QuestionFavorite.class);
        verify(favoriteMapper).insert(captor.capture());
        assertEquals(STUDENT_ID, captor.getValue().getUserId());
        assertEquals(QUESTION_ID, captor.getValue().getQuestionId());
    }

    @Test
    void 重复收藏_幂等返回且不重复插入() {
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(new Question());
        when(favoriteMapper.selectCount(any())).thenReturn(1L);

        assertDoesNotThrow(() -> favoriteService.addFavorite(STUDENT_ID, QUESTION_ID));

        verify(favoriteMapper, never()).insert(any(QuestionFavorite.class));
    }

    @Test
    void 并发收藏撞唯一索引_吞掉异常按幂等成功() {
        when(questionMapper.selectById(QUESTION_ID)).thenReturn(new Question());
        when(favoriteMapper.selectCount(any())).thenReturn(0L);
        // 模拟并发窗口：两个请求都通过判存，后到者撞 (user_id, question_id) 唯一索引
        when(favoriteMapper.insert(any(QuestionFavorite.class)))
                .thenThrow(new DuplicateKeyException("uk_favorites_user_question"));

        assertDoesNotThrow(() -> favoriteService.addFavorite(STUDENT_ID, QUESTION_ID),
                "唯一索引冲突应被视为已收藏的幂等成功");
        verify(favoriteMapper).insert(any(QuestionFavorite.class));
    }

    @Test
    void 取消不存在的收藏_幂等成功() {
        when(favoriteMapper.delete(any())).thenReturn(0);

        assertDoesNotThrow(() -> favoriteService.removeFavorite(STUDENT_ID, QUESTION_ID),
                "未收藏时删除 0 行应按成功返回");
        verify(favoriteMapper).delete(any());
    }

    @Test
    void 收藏不存在的题目_抛题目不存在() {
        // questionMapper.selectById 默认返回 null，表示题目不存在或已逻辑删除
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> favoriteService.addFavorite(STUDENT_ID, QUESTION_ID));

        assertEquals("题目不存在", exception.getMessage());
        verify(favoriteMapper, never()).selectCount(any());
        verify(favoriteMapper, never()).insert(any(QuestionFavorite.class));
    }

    @Test
    void 收藏题目ID为空_抛参数错误() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> favoriteService.addFavorite(STUDENT_ID, null));

        assertEquals("参数错误：questionId 不能为空", exception.getMessage());
        verifyNoInteractions(questionMapper);
    }

    /**
     * 调用公开分页方法并捕获实际传给 Mapper 的 Page 对象。
     * 本页无记录时 fillAndSanitizeQuestions 直接返回，不会触发选项/分类查询。
     */
    @SuppressWarnings("unchecked")
    private Page<QuestionFavoriteVo> queryAndCapturePage(Integer pageNo, Integer size) {
        favoriteService.pageFavorites(STUDENT_ID, pageNo, size, null, null, null);

        ArgumentCaptor<Page<QuestionFavoriteVo>> captor = ArgumentCaptor.forClass(Page.class);
        verify(favoriteMapper).selectFavoritePage(captor.capture(), eq(STUDENT_ID),
                isNull(), isNull(), isNull());
        return captor.getValue();
    }
}
