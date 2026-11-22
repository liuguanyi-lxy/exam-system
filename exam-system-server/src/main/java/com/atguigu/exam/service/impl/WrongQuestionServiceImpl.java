package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.entity.WrongQuestionMastered;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.mapper.WrongQuestionMasteredMapper;
import com.atguigu.exam.service.WrongQuestionService;
import com.atguigu.exam.utils.QuestionFilterUtil;
import com.atguigu.exam.vo.QuestionItemVo;
import com.atguigu.exam.vo.WrongQuestionVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 错题本 Service 实现
 *
 * 派生查询零改动判卷链：answer_record.is_correct=0 是唯一事实来源，
 * 判卷落库即可见，未判卷行 is_correct 为 NULL 被天然过滤。
 */
@Slf4j
@Service
public class WrongQuestionServiceImpl extends ServiceImpl<WrongQuestionMasteredMapper, WrongQuestionMastered>
        implements WrongQuestionService {

    @Autowired
    private AnswerRecordMapper answerRecordMapper;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionChoiceMapper questionChoiceMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Autowired
    private ExamRecordMapper examRecordMapper;
    @Autowired
    private CategoryMapper categoryMapper;

    /** 分页参数上限：防止一次拉取过多含答案解析的错题数据 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_NO = 10000;
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public Page<WrongQuestionVo> pageWrongQuestions(Long userId, Integer page, Integer size,
                                                    String type, String difficulty, String categoryId,
                                                    Boolean includeMastered, Boolean masteredOnly) {
        Page<WrongQuestionVo> resultPage = new Page<>(normalizePage(page), normalizeSize(size));
        Long normalizedCategoryId = normalizeCategoryId(categoryId);
        String normalizedType = QuestionFilterUtil.normalizeType(type);
        String normalizedDifficulty = QuestionFilterUtil.normalizeDifficulty(difficulty);
        if (Boolean.TRUE.equals(masteredOnly)) {
            // 已掌握视图：标记存在且标记时间不早于最近一次答错时间（与默认视图互斥）
            answerRecordMapper.selectMasteredQuestionPage(resultPage, userId,
                    normalizedType, normalizedDifficulty, normalizedCategoryId);
        } else {
            answerRecordMapper.selectWrongQuestionPage(resultPage, userId,
                    normalizedType, normalizedDifficulty, normalizedCategoryId,
                    Boolean.TRUE.equals(includeMastered));
        }
        // 本页题目一次性批量补齐选项与标准答案，避免逐题查询造成 N+1
        fillQuestionDetails(resultPage.getRecords());
        log.info("查询错题本：用户{}，当前页 {} 条，共 {} 条，includeMastered={}，masteredOnly={}", userId,
                resultPage.getRecords() == null ? 0 : resultPage.getRecords().size(),
                resultPage.getTotal(), includeMastered, masteredOnly);
        return resultPage;
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

    @Override
    public void markMastered(Long userId, Long questionId) {
        if (questionId == null) {
            throw new RuntimeException("题目不存在");
        }
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new RuntimeException("题目不存在");
        }
        // 只允许移出自己错题本里真实存在答错记录的题，避免给任意题目刷标记制造垃圾数据
        if (!hasWrongRecord(userId, questionId)) {
            throw new RuntimeException("该题不在你的错题本中");
        }

        LambdaQueryWrapper<WrongQuestionMastered> wrapper = new LambdaQueryWrapper<WrongQuestionMastered>()
                .eq(WrongQuestionMastered::getUserId, userId)
                .eq(WrongQuestionMastered::getQuestionId, questionId);
        WrongQuestionMastered existing = getOne(wrapper, false);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            // 重复标记幂等：只把 mastered_time 刷新到最新，保证"标记后又答错"的回归判断以最后一次标记为准
            existing.setMasteredTime(now);
            updateById(existing);
            log.info("用户{}重复标记题目{}已掌握，刷新标记时间", userId, questionId);
            return;
        }

        WrongQuestionMastered mark = new WrongQuestionMastered();
        mark.setUserId(userId);
        mark.setQuestionId(questionId);
        mark.setMasteredTime(now);
        try {
            save(mark);
        } catch (DuplicateKeyException e) {
            // 并发双请求撞 uk_mastered_user_question：另一请求已插入，回查更新即可，按幂等成功处理
            log.warn("用户{}并发标记题目{}已掌握，改为更新已存在标记", userId, questionId);
            WrongQuestionMastered concurrent = getOne(wrapper, false);
            if (concurrent == null) {
                throw new RuntimeException("标记已掌握失败，请稍后重试");
            }
            concurrent.setMasteredTime(now);
            updateById(concurrent);
        }
        log.info("用户{}标记题目{}已掌握", userId, questionId);
    }

    @Override
    public void unmarkMastered(Long userId, Long questionId) {
        // 物理删除；无标记时影响 0 行，按幂等成功返回
        boolean removed = remove(new LambdaQueryWrapper<WrongQuestionMastered>()
                .eq(WrongQuestionMastered::getUserId, userId)
                .eq(WrongQuestionMastered::getQuestionId, questionId));
        log.info("用户{}取消题目{}已掌握标记，删除结果：{}", userId, questionId, removed);
    }

    /**
     * 当前登录人在历史考试中是否有该题的 is_correct=0 记录。
     * 先取本人考试记录ID，再按 (exam_record_id in ..., question_id, is_correct=0) 点查；
     * exam_records / answer_record 的逻辑删除由 MyBatis-Plus 自动过滤。
     */
    private boolean hasWrongRecord(Long userId, Long questionId) {
        List<ExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .select(ExamRecord::getId)
                .eq(ExamRecord::getUserId, userId));
        if (records == null || records.isEmpty()) {
            return false;
        }
        List<Long> examRecordIds = records.stream()
                .map(ExamRecord::getId)
                .filter(Objects::nonNull)
                .toList();
        if (examRecordIds.isEmpty()) {
            return false;
        }
        return answerRecordMapper.exists(new LambdaQueryWrapper<AnswerRecord>()
                .in(AnswerRecord::getExamRecordId, examRecordIds)
                .eq(AnswerRecord::getQuestionId, questionId)
                .eq(AnswerRecord::getIsCorrect, 0));
    }

    /** 分类不存在或参数非法时返回 null，让 SQL 忽略该筛选条件 */
    private Long normalizeCategoryId(String categoryId) {
        Long id = QuestionFilterUtil.parseCategoryId(categoryId);
        if (id == null) {
            return null;
        }
        return categoryMapper.selectById(id) == null ? null : id;
    }

    /** 按本页题目ID批量补齐选项与标准答案（2 次查询覆盖整页，杜绝 N+1） */
    private void fillQuestionDetails(List<WrongQuestionVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> questionIds = records.stream()
                .map(WrongQuestionVo::getQuestionId)
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
        List<QuestionAnswer> answerList = questionAnswerMapper.selectList(
                new LambdaQueryWrapper<QuestionAnswer>()
                        .in(QuestionAnswer::getQuestionId, questionIds));

        Map<Long, List<QuestionChoice>> choiceMap = choiceList.stream()
                .filter(choice -> choice.getQuestionId() != null)
                .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        Map<Long, QuestionAnswer> answerMap = answerList.stream()
                .filter(answer -> answer.getQuestionId() != null)
                .collect(Collectors.toMap(QuestionAnswer::getQuestionId, a -> a, (a, b) -> a));

        for (WrongQuestionVo record : records) {
            QuestionItemVo question = record.getQuestion();
            if (question == null) {
                question = new QuestionItemVo();
                question.setId(record.getQuestionId());
                record.setQuestion(question);
            }
            question.setAnswer(answerMap.get(question.getId()));
            if ("CHOICE".equals(question.getType())) {
                question.setChoices(choiceMap.getOrDefault(question.getId(), Collections.emptyList()));
            } else {
                question.setChoices(new ArrayList<>());
            }
        }
    }
}
