package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.entity.QuestionFavorite;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionFavoriteMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.FavoriteService;
import com.atguigu.exam.utils.QuestionFilterUtil;
import com.atguigu.exam.vo.QuestionFavoriteVo;
import com.atguigu.exam.vo.QuestionItemVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 题目收藏 Service 实现
 *
 * 收藏行物理删除；重复收藏靠 (user_id, question_id) 唯一索引 + DuplicateKeyException 兜底，
 * 与 ExamServiceImpl.startExam 处理唯一索引并发冲突的写法一致。
 */
@Slf4j
@Service
public class FavoriteServiceImpl extends ServiceImpl<QuestionFavoriteMapper, QuestionFavorite>
        implements FavoriteService {

    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionChoiceMapper questionChoiceMapper;
    @Autowired
    private CategoryMapper categoryMapper;

    /** 分页参数上限：防止一次拉取过多收藏数据 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_NO = 10000;
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public void addFavorite(Long userId, Long questionId) {
        if (questionId == null) {
            throw new RuntimeException("参数错误：questionId 不能为空");
        }
        if (questionMapper.selectById(questionId) == null) {
            throw new RuntimeException("题目不存在");
        }
        // 先判存给前端友好语义；并发兜底交给唯一索引
        long existing = count(new LambdaQueryWrapper<QuestionFavorite>()
                .eq(QuestionFavorite::getUserId, userId)
                .eq(QuestionFavorite::getQuestionId, questionId));
        if (existing > 0) {
            log.info("用户{}已收藏题目{}，幂等返回", userId, questionId);
            return;
        }
        QuestionFavorite favorite = new QuestionFavorite();
        favorite.setUserId(userId);
        favorite.setQuestionId(questionId);
        try {
            save(favorite);
        } catch (DuplicateKeyException e) {
            // 并发双请求撞 uk_favorites_user_question：另一请求已插入，按"已收藏"幂等成功
            log.warn("用户{}并发收藏题目{}，唯一索引兜底为幂等成功", userId, questionId);
        }
        log.info("用户{}收藏题目{}成功", userId, questionId);
    }

    @Override
    public void removeFavorite(Long userId, Long questionId) {
        // 物理删除；未收藏时影响 0 行，按幂等成功返回
        boolean removed = remove(new LambdaQueryWrapper<QuestionFavorite>()
                .eq(QuestionFavorite::getUserId, userId)
                .eq(QuestionFavorite::getQuestionId, questionId));
        log.info("用户{}取消收藏题目{}，删除结果：{}", userId, questionId, removed);
    }

    @Override
    public Page<QuestionFavoriteVo> pageFavorites(Long userId, Integer page, Integer size,
                                                  String type, String difficulty, String categoryId) {
        Page<QuestionFavoriteVo> resultPage = new Page<>(normalizePage(page), normalizeSize(size));
        baseMapper.selectFavoritePage(resultPage, userId,
                QuestionFilterUtil.normalizeType(type),
                QuestionFilterUtil.normalizeDifficulty(difficulty),
                normalizeCategoryId(categoryId));
        // 本页题目批量补齐选项后统一脱敏，避免逐题查询造成 N+1
        fillAndSanitizeQuestions(resultPage.getRecords());
        log.info("查询收藏列表：用户{}，当前页 {} 条，共 {} 条", userId,
                resultPage.getRecords() == null ? 0 : resultPage.getRecords().size(),
                resultPage.getTotal());
        return resultPage;
    }

    @Override
    public boolean isFavorited(Long userId, Long questionId) {
        if (questionId == null) {
            return false;
        }
        // 题目不存在或已逻辑删除时，收藏状态按未收藏返回（契约 5.4：状态查询不报错）
        if (questionMapper.selectById(questionId) == null) {
            return false;
        }
        return count(new LambdaQueryWrapper<QuestionFavorite>()
                .eq(QuestionFavorite::getUserId, userId)
                .eq(QuestionFavorite::getQuestionId, questionId)) > 0;
    }

    /** 页码归一化：非正数按 1，超过上限按上限截断（不抛错） */
    private static int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return Math.min(page, MAX_PAGE_NO);
    }

    /** 每页条数归一化：非正数按默认值，超过上限按上限截断（不抛错） */
    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** 分类不存在或参数非法时返回 null，让 SQL 忽略该筛选条件 */
    private Long normalizeCategoryId(String categoryId) {
        Long id = QuestionFilterUtil.parseCategoryId(categoryId);
        if (id == null) {
            return null;
        }
        return categoryMapper.selectById(id) == null ? null : id;
    }

    /**
     * 批量补齐选项并按 QuestionSanitizer 等价口径脱敏：
     * 不下发标准答案、解析与选项对错，防止收藏列表成为题库脱敏的绕过通道。
     */
    private void fillAndSanitizeQuestions(List<QuestionFavoriteVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> questionIds = records.stream()
                .map(QuestionFavoriteVo::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return;
        }
        List<QuestionChoice> choiceList = questionChoiceMapper.selectList(
                new LambdaQueryWrapper<QuestionChoice>()
                        .in(QuestionChoice::getQuestionId, questionIds)
                        .orderByAsc(QuestionChoice::getSort));
        Map<Long, List<QuestionChoice>> choiceMap = choiceList.stream()
                .filter(choice -> choice.getQuestionId() != null)
                .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));

        for (QuestionFavoriteVo record : records) {
            QuestionItemVo question = record.getQuestion();
            if (question == null) {
                question = new QuestionItemVo();
                question.setId(record.getQuestionId());
                record.setQuestion(question);
            }
            // 收藏列表不下发答案解析
            question.setAnalysis(null);
            question.setAnswer(null);
            if ("CHOICE".equals(question.getType())) {
                List<QuestionChoice> choices = choiceMap.getOrDefault(question.getId(), Collections.emptyList());
                for (QuestionChoice choice : choices) {
                    choice.setIsCorrect(null);
                }
                question.setChoices(choices);
            } else {
                question.setChoices(new ArrayList<>());
            }
        }
    }
}
