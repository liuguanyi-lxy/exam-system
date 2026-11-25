package com.atguigu.exam.service;

import com.atguigu.exam.entity.MasteryScore;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.entity.RemediationTask;
import com.atguigu.exam.entity.RemediationTaskItem;
import com.atguigu.exam.mapper.MasteryScoreMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.mapper.RemediationTaskItemMapper;
import com.atguigu.exam.mapper.RemediationTaskMapper;
import com.atguigu.exam.service.impl.RemediationGradingService;
import com.atguigu.exam.vo.RemediationPolicy;
import com.atguigu.exam.vo.RemediationStepView;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 补课步骤（基础练习 PRACTICE / 变式题 VARIATION / 复测 RETEST）取题与提交判分服务（M5）
 *
 * 轮次模型：round = 该任务该步骤 max(round_no)；取题时余题（user_answer IS NULL）直接返回，
 * 余题为空则 round+1 重新选题并在取题时 INSERT IGNORE 落 items（幂等靠唯一键兜底）。
 * 提交按 (task_id, step, question_id, round_no) 且 user_answer IS NULL 更新，重复提交不重复计分。
 * 掌握度联动走 MasteryScoreMapper 原子 upsert；任务完成判定幂等（仅 IN_PROGRESS 流转）。
 */
@Service
@RequiredArgsConstructor
public class RemediationStepService {

    public static final String STEP_PRACTICE = "PRACTICE";
    public static final String STEP_VARIATION = "VARIATION";
    public static final String STEP_RETEST = "RETEST";

    private final RemediationTaskService taskService;
    private final RemediationTaskMapper tasks;
    private final RemediationTaskItemMapper items;
    private final QuestionMapper questions;
    private final QuestionChoiceMapper questionChoices;
    private final QuestionAnswerMapper questionAnswers;
    private final MasteryScoreMapper masteryScores;
    private final RemediationGradingService grading;
    private final TransactionTemplate transactionTemplate;

    /** 选题结果：questions 为新轮选题，insufficient=true 表示变式题池不足已回退全池 */
    private record Selection(List<Question> questions, boolean insufficient) {}

    // ========== 取题 ==========

    /**
     * 取题（脱敏，绝不下发答案/解析/选项 isCorrect）：
     * 当前轮有余题直接返回；否则 round+1 重新选题并落 items。
     */
    @Transactional(rollbackFor = Exception.class)
    public RemediationStepView.PracticePaper paper(long taskId, Long userId, String role, String step) {
        RemediationTask task = taskService.requireStudentTask(taskId, userId, role);
        requireGateOpen(task, userId);
        RemediationPolicy policy = taskService.parsePolicy(task);

        int round = currentRound(taskId, step);
        List<RemediationTaskItem> remaining = unanswered(taskId, step, round);
        if (!remaining.isEmpty()) {
            return new RemediationStepView.PracticePaper(step, round, false, sanitizeAll(orderedQuestions(remaining)));
        }

        int nextRound = round + 1;
        Selection selection = selectQuestions(task, policy, step);
        if (selection.questions().isEmpty()) {
            throw new IllegalArgumentException("该知识点暂无可用练习题");
        }
        for (Question q : selection.questions()) {
            items.insertIgnore(taskId, step, nextRound, q.getId());
        }
        // 池 SQL 只查题目行不带选项，须按 id 补全选项/答案后再脱敏下发（否则选项恒为空）
        List<Question> loaded = loadQuestions(selection.questions().stream()
                .map(Question::getId).toList());
        return new RemediationStepView.PracticePaper(step, nextRound, selection.insufficient(),
                sanitizeAll(loaded));
    }

    // ========== 提交判分 ==========

    /**
     * 批量提交：判分、按轮幂等落 items、联动掌握度、判定任务完成。
     * 响应只含每题对错与反馈，绝不下发答案/解析。
     *
     * 事务边界（AI review 修复）：AI 远程调用（主观题判分）在事务外执行，
     * 避免慢 AI 占住 DB 连接与事务；落库（submitAnswer + 掌握度 upsert + 完成判定）
     * 经 TransactionTemplate 收敛为单事务，语义与整体事务一致。
     */
    public RemediationStepView.SubmitResult submit(long taskId, Long userId, String role, String step,
                                                   RemediationStepView.SubmitRequest request) {
        RemediationTask task = taskService.requireStudentTask(taskId, userId, role);
        boolean gateOpen = requireGateOpen(task, userId);
        RemediationPolicy policy = taskService.parsePolicy(task);
        if (request == null || request.answers() == null || request.answers().isEmpty()) {
            throw new IllegalArgumentException("作答内容不能为空");
        }

        int round = currentRound(taskId, step);
        Set<Long> pendingIds = unanswered(taskId, step, round).stream()
                .map(RemediationTaskItem::getQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (pendingIds.isEmpty()) {
            throw new IllegalArgumentException("当前没有待作答的题目");
        }
        Map<Long, String> answerMap = new LinkedHashMap<>();
        for (RemediationStepView.Answer a : request.answers()) {
            if (a == null || a.questionId() == null) {
                throw new IllegalArgumentException("作答题目ID不能为空");
            }
            if (!pendingIds.contains(a.questionId())) {
                throw new IllegalArgumentException("题目不在本轮待答列表中：" + a.questionId());
            }
            answerMap.putIfAbsent(a.questionId(), a.answer() == null ? "" : a.answer());
        }

        // AI 判分在事务外（远程调用不占 DB 连接）；幂等由落库段 WHERE user_answer IS NULL 兜底
        Map<Long, RemediationGradingService.ItemResult> graded =
                grading.gradeBatch(loadQuestions(answerMap.keySet()), answerMap);

        return transactionTemplate.execute(status -> persistGraded(task, policy, step, round, answerMap,
                graded, taskId, gateOpen));
    }

    /** 落库段（单事务）：按轮幂等写作答、联动掌握度、判定任务完成。totalCount=0 时不联动（见内注） */
    private RemediationStepView.SubmitResult persistGraded(RemediationTask task, RemediationPolicy policy,
                                                           String step, int round, Map<Long, String> answerMap,
                                                           Map<Long, RemediationGradingService.ItemResult> graded,
                                                           long taskId, boolean gateOpen) {
        List<RemediationStepView.QuestionResult> results = new ArrayList<>();
        int totalCount = 0;
        int correctCount = 0;
        for (Map.Entry<Long, String> entry : answerMap.entrySet()) {
            long questionId = entry.getKey();
            RemediationGradingService.ItemResult r = graded.get(questionId);
            // WHERE user_answer IS NULL：并发重复提交后到者更新 0 行，不重复计分
            int updated = items.submitAnswer(taskId, step, round, questionId, entry.getValue(),
                    r == null ? null : r.isCorrect(), r == null ? null : r.score(),
                    r == null ? null : r.feedback());
            if (updated == 0) {
                continue;
            }
            totalCount++;
            Boolean correct = r == null || r.isCorrect() == null ? null : r.isCorrect() == 1;
            if (Boolean.TRUE.equals(correct)) {
                correctCount++;
            }
            results.add(new RemediationStepView.QuestionResult(questionId, correct,
                    r == null ? "判分异常" : r.feedback()));
        }
        BigDecimal accuracy = accuracyOf(correctCount, totalCount);

        boolean taskCompleted = false;
        BigDecimal masteryTotal;
        // 并发下整轮均为重复提交（0 行）时跳过联动：accuracy=0 只是"本轮无新计分"，
        // 若照常 upsert 会把既有掌握度/复测分错误清零（AI review 修复）；回显 DB 当前值
        if (totalCount == 0) {
            masteryTotal = readMasteryTotal(task);
        } else if (STEP_RETEST.equals(step)) {
            masteryScores.upsertRetestScore(task.getUserId(), task.getKnowledgePointId(), accuracy);
            // 完成判定口径：以 upsert 后 DB 折算的 total 对照快照 minMastery
            masteryTotal = readMasteryTotal(task);
            if (masteryTotal != null && masteryTotal.compareTo(BigDecimal.valueOf(policy.minMastery())) >= 0) {
                tasks.markCompleted(taskId);
                taskCompleted = true;
            }
        } else {
            masteryScores.upsertPracticeAccuracy(task.getUserId(), task.getKnowledgePointId(), accuracy);
            masteryTotal = readMasteryTotal(task);
            // forceRetest=false 且视频口径已放行（提交入口已校验 gateOpen）→ 练习完成即判完成
            // （复测未达阈值保持 IN_PROGRESS 可 round+1 重做；变式只联动掌握度不判完成）
            if (STEP_PRACTICE.equals(step) && !policy.forceRetest() && gateOpen) {
                tasks.markCompleted(taskId);
                taskCompleted = true;
            }
        }
        return new RemediationStepView.SubmitResult(step, round, results,
                new RemediationStepView.Round(correctCount, totalCount,
                        totalCount == 0 ? 0 : accuracy.intValue()),
                taskCompleted, masteryTotal);
    }

    // ========== 内部辅助 ==========

    /** 视频门禁（复用 detail 的 gateOpen 口径），返回是否放行供完成判定复用 */
    private boolean requireGateOpen(RemediationTask task, Long userId) {
        boolean open = taskService.videoGateOpen(task, userId);
        if (!open) {
            throw new IllegalArgumentException("请先完成视频学习");
        }
        return open;
    }

    private int currentRound(long taskId, String step) {
        Integer max = items.maxRound(taskId, step);
        return max == null ? 0 : max;
    }

    private List<RemediationTaskItem> unanswered(long taskId, String step, int round) {
        return items.selectList(new LambdaQueryWrapper<RemediationTaskItem>()
                .eq(RemediationTaskItem::getTaskId, taskId)
                .eq(RemediationTaskItem::getStep, step)
                .eq(RemediationTaskItem::getRoundNo, round)
                .isNull(RemediationTaskItem::getUserAnswer)
                .orderByAsc(RemediationTaskItem::getQuestionId));
    }

    /**
     * 选题：练习/复测全池随机；变式排除本任务全部 PRACTICE 已用题，
     * 可用题不足时回退全池并标 insufficient（口径 2）。
     */
    private Selection selectQuestions(RemediationTask task, RemediationPolicy policy, String step) {
        long pointId = task.getKnowledgePointId();
        if (STEP_VARIATION.equals(step)) {
            int need = policy.practiceCount();
            List<Long> usedPracticeIds = items.selectList(new LambdaQueryWrapper<RemediationTaskItem>()
                            .eq(RemediationTaskItem::getTaskId, task.getId())
                            .eq(RemediationTaskItem::getStep, STEP_PRACTICE))
                    .stream().map(RemediationTaskItem::getQuestionId).filter(Objects::nonNull).distinct().toList();
            if (items.countPoolQuestions(pointId, usedPracticeIds) >= need) {
                return new Selection(items.selectPoolQuestions(pointId, usedPracticeIds, need), false);
            }
            return new Selection(items.selectPoolQuestions(pointId, List.of(), need), true);
        }
        int need = STEP_RETEST.equals(step) ? policy.retestCount() : policy.practiceCount();
        return new Selection(items.selectPoolQuestions(pointId, List.of(), need), false);
    }

    /** 正确占比 * 100（0-100，HALF_UP 取整），空轮返回 0 */
    private static BigDecimal accuracyOf(int correctCount, int totalCount) {
        if (totalCount == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(correctCount * 100L)
                .divide(BigDecimal.valueOf(totalCount), 0, RoundingMode.HALF_UP);
    }

    /** upsert 后读回掌握度 total（完成判定口径：以 DB 折算结果为准） */
    private BigDecimal readMasteryTotal(RemediationTask task) {
        MasteryScore after = masteryScores.selectOne(new LambdaQueryWrapper<MasteryScore>()
                .eq(MasteryScore::getUserId, task.getUserId())
                .eq(MasteryScore::getKnowledgePointId, task.getKnowledgePointId()));
        return after == null ? null : after.getTotal();
    }

    /** 装配完整题目（选项按 sort 升序 + 标准答案），供判分与 AI 提示词使用，不外发 */
    private List<Question> loadQuestions(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return List.of();
        Map<Long, List<QuestionChoice>> choiceMap = questionChoices.selectList(new LambdaQueryWrapper<QuestionChoice>()
                        .in(QuestionChoice::getQuestionId, distinct)
                        .orderByAsc(QuestionChoice::getSort))
                .stream().filter(c -> c.getQuestionId() != null)
                .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        Map<Long, QuestionAnswer> answerMap = questionAnswers.selectList(new LambdaQueryWrapper<QuestionAnswer>()
                        .in(QuestionAnswer::getQuestionId, distinct))
                .stream().filter(a -> a.getQuestionId() != null)
                .collect(Collectors.toMap(QuestionAnswer::getQuestionId, Function.identity(), (a, b) -> a));
        List<Question> loaded = questions.selectBatchIds(distinct);
        for (Question q : loaded) {
            q.setChoices(choiceMap.getOrDefault(q.getId(), List.of()));
            q.setAnswer(answerMap.get(q.getId()));
        }
        return loaded;
    }

    /** 余题按明细顺序返回（逻辑删除的题目被 selectBatchIds 过滤后跳过） */
    private List<Question> orderedQuestions(List<RemediationTaskItem> remaining) {
        Map<Long, Question> byId = loadQuestions(remaining.stream()
                        .map(RemediationTaskItem::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity(), (a, b) -> a));
        return remaining.stream()
                .map(i -> byId.get(i.getQuestionId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 脱敏：仅题干/类型/多选标记/选项内容与序号，绝无 answer/analysis/isCorrect */
    private List<RemediationStepView.SanitizedQuestion> sanitizeAll(List<Question> qs) {
        return qs.stream().map(q -> new RemediationStepView.SanitizedQuestion(q.getId(), q.getTitle(), q.getType(),
                        q.getMulti(),
                        q.getChoices() == null ? List.of() : q.getChoices().stream()
                                .map(c -> new RemediationStepView.Choice(c.getContent(), c.getSort()))
                                .toList()))
                .toList();
    }
}
