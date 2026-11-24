package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.*;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.*;
import com.atguigu.exam.vo.BatchGradingItemVo;
import com.atguigu.exam.vo.ExamRankingVO;
import com.atguigu.exam.vo.GradingResult;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {
    @Autowired
    private PaperService paperService;
    @Autowired
    private AnswerRecordService answerRecordService;
    @Autowired
    private PaperQuestionService paperQuestionService;
    @Autowired
    private AiService aiService;
    @Autowired
    private ExamParticipationService examParticipationService;
    @Autowired
    @Qualifier("examExecutor")
    private ThreadPoolExecutor examExecutor;

    /** 交卷时间宽限秒数：覆盖前端自动交卷与网络延迟带来的少量超时 */
    private static final long SUBMIT_GRACE_SECONDS = 60;

    /**
     * 开始考试业务
     * @param startExamVo 开考参数
     * @param currentUserId 当前登录用户ID，考试记录归属
     * @return
     */
    @Override
    public ExamRecord startExam(StartExamVo startExamVo, Long currentUserId) {
        return examParticipationService.start(startExamVo, currentUserId);
    }
    /**
     * 根据考试记录ID获取考试记录详情,包括其中关联的试卷信息和答题记录信息
     * @param id
     * @return
     */
    @Override
    public ExamRecord customGetExamRecordById(Integer id) {
        //1.获取考试记录信息
        ExamRecord examRecord = getById(id);
        if(examRecord == null){
            log.info("ID为%s考试记录不存在".formatted(id));
            return null;
        }
        //2.获取考试记录关联的试卷信息,试卷信息包含题目、答案、选项
        Paper paper = paperService.customPaperDetailById(examRecord.getExamId().longValue());
        if(paper == null){
            log.info("ID为%s试卷不存在".formatted(examRecord.getExamId()));
        }
        //试卷信息封装给考试记录对象
        examRecord.setPaper(paper);
        //3.获取考试记录关联的答题记录信息
        LambdaQueryWrapper<AnswerRecord> answerRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
        answerRecordLambdaQueryWrapper.eq(AnswerRecord::getExamRecordId, id);
        List<AnswerRecord> answerRecordList = answerRecordService.list(answerRecordLambdaQueryWrapper);
        if(ObjectUtils.isNotEmpty(answerRecordList) && paper != null && paper.getQuestions() != null){
            //试卷对象中的题目列表是按照选择题、判断题、简答题的形式排过序的，获取其题目ID列表
            List<Long> questionIdList = paper.getQuestions().stream().map(Question::getId).collect(Collectors.toList());
            //将答题记录列表按照题目ID列表的顺序进行排序
            answerRecordList.sort((o1, o2) -> questionIdList.indexOf(o1.getQuestionId()) - questionIdList.indexOf(o2.getQuestionId()));
        }
        //答题记录列表封装给考试记录对象
        examRecord.setAnswerRecords(answerRecordList);
        return examRecord;
    }
    /**
     * 提交考试答案
     * 校验：记录存在、归属当前用户、状态为进行中、考试时间未超时（含宽限），
     * 全部通过后才保存答题记录并结束考试；判卷由结果页调用 /grade 接口触发，
     * 避免交卷请求同步等待AI判卷导致前端30秒超时
     * @param examRecordId
     * @param answers
     * @param currentUserId 当前登录用户ID，校验记录归属
     * @param isTeacherOrAdmin 当前用户是否教师/管理员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customSubmitAnswer(Integer examRecordId, List<SubmitAnswerVo> answers, Long currentUserId, boolean isTeacherOrAdmin) {
        //将答题记录保存在试卷记录和题目的关联表，也就答题记录表，记录考试记录的ID、题目ID、学生答案。然后再修改考试记录的考试状态和结束时间
        //1.先取出考试记录完成校验：存在性、归属、状态、时间，全部通过后才写答题记录，避免半提交脏数据
        ExamRecord examRecord = getById(examRecordId);
        if (examRecord == null) {
            throw new RuntimeException("ID为%s的考试记录不存在，提交失败！".formatted(examRecordId));
        }
        if (!isTeacherOrAdmin) ExamRecordScope.requireStudentOwner(currentUserId, examRecord);
        //已交卷/已批阅的记录拒绝重复提交，防止重复插入答题记录
        if (!ExamStatus.IN_PROGRESS.equals(examRecord.getStatus())) {
            throw new RuntimeException("该考试已交卷，请勿重复提交！");
        }
        //服务端时间校验：超过试卷时长+宽限仍未交卷则拒绝，防止无限延时答题
        checkSubmitDeadline(examRecord);

        //2.校验提交的题目全部属于本次考试试卷，并按题目ID去重后保存答题记录
        //安全边界（H1）：旧实现不校验 questionId 归属，任意题库题都能落库；判卷 catch 分支再把
        //"题目不在试卷中"写成 is_correct=0，错题本据此聚合并下发答案解析，构成答案泄露通道。
        //首先要对提交答案列表进行判空,提交答案列表为空就不用保存答题记录
        if(ObjectUtils.isNotEmpty(answers))
        {
            Set<Long> paperQuestionIds = paperQuestionService.list(
                            new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, examRecord.getExamId()))
                    .stream()
                    .map(PaperQuestion::getQuestionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            //同一 questionId 只保留一条答题记录：LinkedHashMap 去重并保留首次出现顺序，后出现的答案覆盖先出现的
            Map<Integer, String> answerByQuestionId = new LinkedHashMap<>();
            for (SubmitAnswerVo answer : answers) {
                if (answer == null || answer.getQuestionId() == null
                        || !paperQuestionIds.contains(answer.getQuestionId().longValue())) {
                    throw new RuntimeException("提交的题目不属于本次考试试卷！");
                }
                answerByQuestionId.put(answer.getQuestionId(), answer.getUserAnswer());
            }
            List<AnswerRecord> answerRecordList = answerByQuestionId.entrySet().stream()
                    .map(entry -> new AnswerRecord(examRecordId, entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
            answerRecordService.saveBatch(answerRecordList);
        }

        //3.修改考试记录的考试状态和结束时间
        examRecord.setStatus(ExamStatus.FINISHED);
        examRecord.setEndTime(LocalDateTime.now());
        //更新对应的考试记录数据
        this.updateById(examRecord);
    }

    /**
     * 服务端交卷时间校验：开始时间 + 试卷时长 + 宽限秒数 之后不允许再交卷
     * 试卷时长缺失或试卷已删除时跳过校验，不阻塞交卷
     */
    private void checkSubmitDeadline(ExamRecord examRecord) {
        if (examRecord.getStartTime() == null || examRecord.getExamId() == null) {
            return;
        }
        Paper paper = paperService.getById(examRecord.getExamId().longValue());
        if (paper == null || paper.getDuration() == null || paper.getDuration() <= 0) {
            return;
        }
        LocalDateTime deadline = examRecord.getStartTime()
                .plusMinutes(paper.getDuration())
                .plusSeconds(SUBMIT_GRACE_SECONDS);
        if (LocalDateTime.now().isAfter(deadline)) {
            log.warn("考试记录{}超时提交被拒绝：截止{}，当前{}",
                    examRecord.getId(), deadline, LocalDateTime.now());
            throw new RuntimeException("考试时间已结束，无法提交答卷！");
        }
    }
    /**
     * ai试卷批阅功能
     * 客观题本地即时判卷；简答题与代码题合并成一次批量AI调用判卷（规避AI账号RPM限流），
     * 批量失败时回退为单题并行判卷；AI 总评另起一次调用
     * @param examRecordId
     * @return
     */
    @Override
    public ExamRecord gradeExam(Integer examRecordId, Long currentUserId, boolean isTeacherOrAdmin) {
        //1.获取考试记录对象，包含其关联的试卷信息和答题记录信息
        ExamRecord examRecord = customGetExamRecordById(examRecordId);
        if (examRecord == null) {
            throw new RuntimeException("ID为%s的考试记录不存在，无法判卷".formatted(examRecordId));
        }
        //归属校验：仅考生本人或教师/管理员可触发判卷，防止他人消耗AI调用
        if (!isTeacherOrAdmin) ExamRecordScope.requireStudentOwner(currentUserId, examRecord);
        if (ExamStatus.IN_PROGRESS.equals(examRecord.getStatus())) {
            throw new IllegalArgumentException("请先提交答卷再判卷");
        }
        //已批阅过的记录直接返回：结果页刷新时不会重复消耗AI调用
        if (ExamStatus.GRADED.equals(examRecord.getStatus())) {
            return examRecord;
        }
        //获取考试记录关联的试卷信息，进行判空，如果为空就是考试对应的试卷已经被删除，无法进行成绩判定
        Paper paper = examRecord.getPaper();
        if(paper == null){
            //试卷为空则给考试记录状态修改成已批阅
            examRecord.setStatus(ExamStatus.GRADED);
            //本次考试评语设置为出错问题
            examRecord.setSummary("考试对应的试卷已被删除，无法进行成绩判定");
            //更新考试记录
            this.updateById(examRecord);
            log.warn("考试记录{}关联的试卷{}已被删除，无法判卷", examRecordId, examRecord.getExamId());
            throw new RuntimeException("考试对应的试卷已被删除，无法进行成绩判定");
        }
        log.info("开始判卷，考试记录ID：{}，试卷ID：{}", examRecordId, paper.getId());
        //2.获取考试记录关联的答题记录信息，进行判空，如果为空就是考生分数为0
        List<AnswerRecord> answerRecordList = examRecord.getAnswerRecords();
        if(ObjectUtils.isEmpty(answerRecordList)){
            //答题记录为空则给考试记录状态修改成已批阅
            examRecord.setStatus(ExamStatus.GRADED);
            //本次考试评语设置为出错问题
            examRecord.setSummary("考生没有作答成绩为0");
            //更新考试记录
            this.updateById(examRecord);
            return examRecord;
        }
        int correctNumber = 0 ; //正确题目数量
        int totalScore = 0; //总得分
        //一次性查出本试卷的题目关联行：既用于取每题分值，也用于校验答题记录的题目归属，避免循环内逐条查库
        List<PaperQuestion> paperQuestionList = paperQuestionService.list(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, paper.getId()));
        Set<Long> paperQuestionIds = paperQuestionList.stream()
                .map(PaperQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        //安全边界（H1）：不属于本试卷的答题记录不参与判卷、不改判状态。
        //旧漏洞会把这类记录写进 catch 分支的 is_correct=0，从而伪造错题并泄露答案解析；
        //这里在判卷前直接过滤，判卷完成后这类记录仍是未判卷（is_correct 为空），不会进入错题本。
        List<AnswerRecord> gradableAnswerRecords = answerRecordList.stream()
                .filter(ar -> ar.getQuestionId() != null && paperQuestionIds.contains(ar.getQuestionId().longValue()))
                .collect(Collectors.toList());
        if (gradableAnswerRecords.size() != answerRecordList.size()) {
            log.warn("考试记录{}存在{}条不属于试卷{}的答题记录，已跳过判卷",
                    examRecordId, answerRecordList.size() - gradableAnswerRecords.size(), paper.getId());
        }
        if (gradableAnswerRecords.isEmpty()) {
            //全部答题记录都不属于本试卷：按未作答处理，不产生分数与错题
            examRecord.setStatus(ExamStatus.GRADED);
            examRecord.setSummary("考生没有作答成绩为0");
            this.updateById(examRecord);
            return examRecord;
        }
        answerRecordList = gradableAnswerRecords;
        List<Question> paperQuestions = paper.getQuestions() == null ? new ArrayList<>() : paper.getQuestions();
        //将试卷包含的题目列表转换成题目ID对应的题目对象形式的map，方便根据题目ID取出对应的题目对象
        Map<Long, Question> questionMap = paperQuestions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        log.debug("试卷题目数量：{}", questionMap == null ? 0 : questionMap.size());
        //本试卷每道题的分值，key为题目ID（题目或分值缺失的行由判卷分支按原有异常兜底处理）
        Map<Long, BigDecimal> scoreMap = paperQuestionList.stream()
                .filter(pq -> pq.getQuestionId() != null && pq.getScore() != null)
                .collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore, (a, b) -> a));
        //3.收集所有需要AI判卷的题目，合并成一次批量判卷请求
        //AI账号常有RPM限制（如每分钟3次），逐题并行发起N次调用必然触发限流；
        //批量判卷 + 总评 = 每次交卷最多2次AI调用
        List<BatchGradingItemVo> aiGradingItems = new ArrayList<>();
        for (AnswerRecord answerRecord : answerRecordList) {
            Question question = questionMap.get(answerRecord.getQuestionId() == null ? null : answerRecord.getQuestionId().longValue());
            if (question == null || !isAiGradedType(question.getType())) {
                continue;
            }
            BigDecimal paperScore = scoreMap.get(answerRecord.getQuestionId().longValue());
            BatchGradingItemVo item = new BatchGradingItemVo();
            item.setQuestionId(answerRecord.getQuestionId().longValue());
            item.setQuestion(question);
            item.setUserAnswer(answerRecord.getUserAnswer());
            item.setMaxScore(paperScore == null ? null : paperScore.intValue());
            aiGradingItems.add(item);
        }
        Map<Long, GradingResult> aiGradingResults = new HashMap<>();
        if (!aiGradingItems.isEmpty()) {
            try {
                aiGradingResults = aiService.gradingSubjectiveBatch(aiGradingItems);
            } catch (Exception e) {
                log.warn("批量AI判卷失败，回退为单题并行判卷。考试记录ID：{}", examRecordId, e);
                aiGradingResults = parallelGradeFallback(aiGradingItems);
            }
        }
        //4.遍历答题记录列表，进行判卷，期间记录正确题目数量和总得分，并且修改每条答题记录的状态，得分情况、是否正确以及ai评语
        for (AnswerRecord answerRecord : answerRecordList) {
            //使用ai判断简答题可能会出错，但是为了保证后面的题目都能进行判断就不报错，捕获异常进行题目对应答题记录的信息修改然后继续循环
            try {
                //1.先获取答题记录对应题目对象（注意：需要将Integer转换为Long）
                Question question = questionMap.get(answerRecord.getQuestionId().longValue());
                if (question == null) {
                    log.warn("题目ID: {} 在试卷中不存在", answerRecord.getQuestionId());
                    throw new RuntimeException("题目数据不存在");
                }

                // 检查题目是否有答案
                if (question.getAnswer() == null || question.getAnswer().getAnswer() == null) {
                    log.warn("题目ID: {} 缺少标准答案", question.getId());
                    throw new RuntimeException("题目缺少标准答案");
                }

                //查询试卷题目关联表中该题目对应的分数
                BigDecimal paperScore = scoreMap.get(answerRecord.getQuestionId().longValue());
                if (paperScore == null) {
                    log.warn("题目ID: {} 在试卷中未设置分数", question.getId());
                    throw new RuntimeException("题目分数未设置");
                }
                Integer score = paperScore.intValue();
                //获取题目的正确答案
                String correctAnswer = question.getAnswer().getAnswer();
                //获取学生答案
                String userAnswer = answerRecord.getUserAnswer();
                //2.判断学生答案是否正确，先分辨题目类型
                if(isAiGradedType(question.getType())){
                    //简答题与代码题：取批量判卷结果；结果缺失视为该题判卷失败，走统一异常分支
                    GradingResult gradingResult = aiGradingResults.get(answerRecord.getQuestionId().longValue());
                    if (gradingResult == null) {
                        throw new RuntimeException("AI判题服务异常，请稍后重试");
                    }
                    //设置该题目对应的得分
                    answerRecord.setScore(gradingResult.getScore());
                    //根据评卷的分数判断该答题正确情况和扣分原因以及反馈
                    if(gradingResult.getScore() == 0){
                        //0分则设置错误
                        answerRecord.setIsCorrect(0);
                        //设置ai扣分原因
                        answerRecord.setAiCorrection(gradingResult.getReason());
                    }else if(gradingResult.getScore() == score){
                        //满分则设置正确
                        answerRecord.setIsCorrect(1);
                        //设置ai反馈
                        answerRecord.setAiCorrection(gradingResult.getFeedback());
                    }else{
                        //基本正确则设置基本正确
                        answerRecord.setIsCorrect(2);
                        //设置ai扣分原因
                        answerRecord.setAiCorrection(gradingResult.getReason());
                    }
                }else{
                    //客观题（选择题/判断题）智能比对：兼容 字母/选项内容/中文/英文 多种答案格式
                    if(isObjectiveAnswerCorrect(question, correctAnswer, userAnswer))
                    {
                        //设置该题目对应的得分
                        answerRecord.setScore(score);
                        //设置该题目的对错,0是错误，1是正确，2是基本正确
                        answerRecord.setIsCorrect(1);
                        answerRecord.setAiCorrection("回答正确");
                    }else{
                        answerRecord.setScore(0);
                        answerRecord.setIsCorrect(0);
                        answerRecord.setAiCorrection("回答错误");
                    }
                }
            }catch (Exception e){
                //判卷出现错误设置该题目得分为0以及错误并设置反馈为判题过程出现问题
                log.error("题目ID: {} 判题过程出现异常", answerRecord.getQuestionId(), e);
                answerRecord.setScore(0);
                answerRecord.setIsCorrect(0);
                String errorMsg = "判题过程出现问题";
                // 提供更详细的错误信息
                if (e instanceof NullPointerException) {
                    errorMsg = "题目数据不完整，请联系管理员";
                } else if (e.getMessage() != null && e.getMessage().contains("kimi")) {
                    errorMsg = "AI判题服务异常，请稍后重试";
                } else if (e.getMessage() != null && e.getMessage().contains("JSON")) {
                    errorMsg = "AI返回结果格式错误";
                }
                answerRecord.setAiCorrection(errorMsg + "（错误类型：" + e.getClass().getSimpleName() + "）");
            }
            //计算总得分和正确题目数量
            totalScore += answerRecord.getScore();
            if(answerRecord.getIsCorrect() == 1){
                correctNumber++;
            }
        }
        //5.更新答题记录列表
        answerRecordService.updateBatchById(answerRecordList);
        //6.根据得分情况、试卷总分数、试卷总题目数、答题正确题目数生成ai评价总结
        // AI 总评失败不能连累成绩入库：先保证分数和状态落库，评语退化为兜底文案
        String summary;
        try {
            summary = aiService.buildSummary(totalScore, paper.getTotalScore().intValue(), paperQuestions.size(), correctNumber);
        } catch (Exception e) {
            log.warn("AI 总评生成失败，使用兜底评语。考试记录ID：{}", examRecordId, e);
            summary = "本次考试共 %d 题，答对 %d 题，得分 %d / %d。AI 评语生成失败，成绩已按客观题判分结果记录。"
                    .formatted(paperQuestions.size(), correctNumber, totalScore, paper.getTotalScore().intValue());
        }
        //7.更新考试记录信息
        examRecord.setSummary(summary);
        examRecord.setScore(totalScore);
        examRecord.setStatus(ExamStatus.GRADED);
        this.updateById(examRecord);
        return examRecord;
    }

    /**
     * 批量判卷失败时的回退路径：按题并行调用单题判卷（保留线程池并发）。
     * 每题结果缺失时以null入表，由主循环统一按判卷失败处理
     */
    private Map<Long, GradingResult> parallelGradeFallback(List<BatchGradingItemVo> items) {
        Map<Integer, CompletableFuture<GradingResult>> futures = new HashMap<>();
        for (BatchGradingItemVo item : items) {
            futures.put(item.getQuestionId().intValue(),
                    CompletableFuture.supplyAsync(() -> safeGradeSubjectiveQuestion(item), examExecutor));
        }
        Map<Long, GradingResult> results = new HashMap<>();
        for (Map.Entry<Integer, CompletableFuture<GradingResult>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey().longValue(), entry.getValue().get(2, TimeUnit.MINUTES));
            } catch (Exception e) {
                log.error("回退单题判卷失败，题目ID: {}", entry.getKey(), e);
            }
        }
        return results;
    }

    /**
     * 线程池内执行的单题AI判卷，任何异常都吞掉并返回null，由主流程统一按判卷失败处理
     */
    private GradingResult safeGradeSubjectiveQuestion(BatchGradingItemVo item) {
        try {
            if (item.getMaxScore() == null) {
                throw new RuntimeException("题目分数未设置");
            }
            return aiService.gradingSubjectiveQuestion(item.getQuestion(), item.getUserAnswer() == null ? "" : item.getUserAnswer(), item.getMaxScore());
        } catch (Exception e) {
            log.error("主观题AI判卷失败，题目ID: {}", item.getQuestionId(), e);
            return null;
        }
    }

    /** 需要 AI 判卷的题型：简答题、手写代码题 */
    private boolean isAiGradedType(String type) {
        return "TEXT".equals(type) || "CODE".equals(type);
    }

    /**
     * 客观题（选择题/判断题）答案智能比对
     * 兼容以下答案格式差异：
     *  - 选择题：标准答案存字母(B)，学生答案可能是选项内容、字母或"B. 内容"
     *  - 多选题：标准答案可能是"AB"或内容列表，学生答案用逗号分隔的内容或字母
     *  - 判断题：标准答案存 TRUE/FALSE，学生答案可能是 正确/错误/对/错
     */
    //包级可见便于单元测试覆盖判卷比对逻辑
    boolean isObjectiveAnswerCorrect(Question question, String correctAnswer, String userAnswer) {
        if (correctAnswer == null || userAnswer == null) return false;
        String user = userAnswer.trim();
        if (user.isEmpty()) return false;

        // 判断题：中文/英文归一化比对
        if ("JUDGE".equals(question.getType())) {
            return normalizeJudgeAnswer(correctAnswer).equals(normalizeJudgeAnswer(user));
        }

        // 选择题
        if ("CHOICE".equals(question.getType())) {
            List<QuestionChoice> choices = question.getChoices();

            // 多选题：多选答案用逗号分隔，需集合比对（顺序无关），覆盖字母/内容各种组合
            if (Boolean.TRUE.equals(question.getMulti())) {
                Set<String> userSet = splitAnswer(user);
                if (userSet.isEmpty()) return false;
                Set<String> correctSet = splitAnswer(correctAnswer);
                int choiceCount = choices == null ? 0 : choices.size();
                boolean userIsLetter = isPureLetterSet(userSet) || isConcatenatedLetters(user, choiceCount);
                boolean correctIsLetter = isPureLetterSet(correctSet) || isConcatenatedLetters(correctAnswer, choiceCount);

                if (userIsLetter && correctIsLetter) {
                    // 两者都是字母（如 "AB" vs "A,B" vs "a、b"）→ 按字符集合比较，兼容连写与大小写
                    return toLetterCharSet(user).equals(toLetterCharSet(correctAnswer));
                } else if (userIsLetter) {
                    // 学生答字母，标准答内容 -> 内容映射到字母比较（字母统一大写）
                    Set<String> expected = new HashSet<>();
                    for (String letter : mapContentsToLetters(correctAnswer, choices)) {
                        expected.add(letter.toUpperCase());
                    }
                    Set<String> actual = new HashSet<>();
                    for (String letter : userSet) {
                        actual.add(letter.toUpperCase());
                    }
                    return actual.equals(expected);
                } else if (correctIsLetter) {
                    // 学生答内容，标准答字母 -> 把正确字母还原成选项内容后，用「包含关系」判断。
                    // 不能按逗号/空格切分：选项文本本身可能带逗号或空格（如"大于 8 就转"），
                    // 一切分就会把一项内容拆成两段，导致全选对也判 0 分。
                    return matchMultiContents(user, correctAnswer, choices);
                } else {
                    // 两者都是选项内容：同样用包含判断，兼容选项文本里的逗号/空格
                    return matchMultiContents(user, correctAnswer, choices);
                }
            }

            // 单选题：逐一尝试各种格式匹配
            // 1) 直接字母比对（忽略大小写，如 "A" vs "a"）
            if (normalizeChoice(correctAnswer).equalsIgnoreCase(normalizeChoice(user))) return true;
            // 2) 标准答案字母 -> 学生答案内容
            if (matchLetterToContent(correctAnswer, user, choices)) return true;
            // 3) 标准答案内容 -> 学生答案字母
            if (matchContentToLetter(correctAnswer, user, choices)) return true;
            // 4) 通过 isCorrect 标记判定：学生答案是否命中唯一正确选项
            return isContentCorrectChoice(user, choices);
        }

        // 兜底：直接比对
        return correctAnswer.equals(userAnswer);
    }


    /**
     * 取选项对应的字母（A/B/C...）。
     *
     * 历史实现用 'A' + choice.getSort()，但题库里存在 sort 从 1 开始的题目
     * （正确项落在 B~E），导致「答案字母」和「前端展示的选项编号」错位、多选题被误判。
     * 这里改为按选项在列表中的位置计算，前提是调用方已按 sort 排好序。
     */
    private char letterOf(List<QuestionChoice> choices, QuestionChoice choice) {
        int index = choices == null ? -1 : choices.indexOf(choice);
        return (char) ('A' + (index < 0 ? 0 : index));
    }

    /**
     * 多选题「学生答选项内容、标准答字母」的比对。
     *
     * 不能用分隔符切分用户答案：选项文本里出现英文/中文逗号、空格时（如 "大于 8 就转"），
     * 切分会把一条选项拆成多条，导致全选对也判错。这里改为判断：
     *   1. 每一个正确选项的内容都必须出现在用户答案里；
     *   2. 未被标准答案选中的选项内容不能出现（若某个错误选项是正确选项的子串，则跳过该项，避免误杀）。
     */
    private boolean matchMultiContents(String userRaw, String letterAnswer, List<QuestionChoice> choices) {
        if (choices == null || choices.isEmpty()) return false;
        // 选项上的 isCorrect 标记是最可信的来源；缺失时才退化为按字母映射
        Set<String> correctContents = new HashSet<>();
        for (QuestionChoice c : choices) {
            if (Boolean.TRUE.equals(c.getIsCorrect()) && c.getContent() != null) {
                correctContents.add(c.getContent());
            }
        }
        if (correctContents.isEmpty()) {
            correctContents = mapLettersToContents(letterAnswer, choices);
        }
        if (correctContents.isEmpty()) return false;

        if (!correctContents.stream().allMatch(userRaw::contains)) return false;

        for (QuestionChoice c : choices) {
            String content = c.getContent();
            if (content == null || content.isBlank() || correctContents.contains(content)) continue;
            // 错误选项是某个正确选项的子串时无法通过包含关系区分，跳过不判
            boolean substringOfCorrect = correctContents.stream().anyMatch(cc -> cc.contains(content));
            if (!substringOfCorrect && userRaw.contains(content)) return false;
        }
        return true;
    }

    /** 判断题答案归一化：正确/对/TRUE/true -> TRUE，错误/错/FALSE/false -> FALSE */
    private String normalizeJudgeAnswer(String ans) {
        if (ans == null) return "";
        String t = ans.trim();
        if ("TRUE".equalsIgnoreCase(t) || "正确".equals(t) || "对".equals(t) || "T".equalsIgnoreCase(t)) return "TRUE";
        if ("FALSE".equalsIgnoreCase(t) || "错误".equals(t) || "错".equals(t) || "F".equalsIgnoreCase(t)) return "FALSE";
        return t;
    }

    /** 选择题答案归一化：去掉前导字母和点，如 "B. xxx" -> "xxx"，保留纯字母 */
    private String normalizeChoice(String ans) {
        if (ans == null) return "";
        String t = ans.trim();
        // 去除前导字母和点（如 "B. Java..."）
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[A-Za-z]+[.、．:：]\\s*(.*)$").matcher(t);
        if (m.matches() && !m.group(1).isEmpty()) return m.group(1).trim();
        return t;
    }

    /** 标准答案是字母(B)，学生答案是内容 -> 匹配 */
    private boolean matchLetterToContent(String letterAnswer, String userContent, List<QuestionChoice> choices) {
        if (choices == null) return false;
        String letters = letterAnswer.trim().toUpperCase();
        // 学生答案可能是 "B. xxx" 或纯内容
        String content = normalizeChoice(userContent);
        for (QuestionChoice c : choices) {
            char cLetter = letterOf(choices, c);
            if (letters.indexOf(cLetter) >= 0 && content.equals(c.getContent())) {
                return true;
            }
        }
        return false;
    }

    /** 标准答案是内容，学生答案是字母(B) -> 匹配 */
    private boolean matchContentToLetter(String contentAnswer, String userLetter, List<QuestionChoice> choices) {
        if (choices == null) return false;
        String content = normalizeChoice(contentAnswer);
        String letters = userLetter.trim().toUpperCase();
        for (QuestionChoice c : choices) {
            char cLetter = letterOf(choices, c);
            if (letters.indexOf(cLetter) >= 0 && content.equals(c.getContent())) {
                return true;
            }
        }
        return false;
    }

    /** 学生答案是否为该题正确选项的内容 */
    private boolean isContentCorrectChoice(String userContent, List<QuestionChoice> choices) {
        if (choices == null) return false;
        String content = normalizeChoice(userContent);
        for (QuestionChoice c : choices) {
            if (Boolean.TRUE.equals(c.getIsCorrect()) && content.equals(c.getContent())) {
                return true;
            }
        }
        return false;
    }

    /** 将答案字符串拆分成集合（按逗号、顿号、空格分隔） */
    private Set<String> splitAnswer(String ans) {
        Set<String> set = new HashSet<>();
        if (ans == null) return set;
        for (String part : ans.split("[,，、\\s]+")) {
            String t = part.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    private boolean isPureLetterSet(Set<String> set) {
        if (set == null || set.isEmpty()) return false;
        for (String s : set) {
            // 必须是「单个字母」才算字母答案。
            // 历史实现用 [A-Za-z]+ 判断，会把 public/private/protected 这类英文选项内容
            // 误判成字母答案，导致多选题被判错。
            if (s == null || s.length() != 1 || !Character.isLetter(s.charAt(0))) return false;
        }
        return true;
    }

    /**
     * 判断是否是「连写的字母答案」，如 AB / acd。
     * 用于兼容接口直接传 "AB" 的情况；因为含分隔符的内容型答案（public,private）不会命中。
     */
    private boolean isConcatenatedLetters(String answer, int choiceCount) {
        if (answer == null) return false;
        String t = answer.replaceAll("\\s+", "");
        if (t.length() <= 1 || t.length() > Math.max(choiceCount, 1)) return false;
        if (!t.matches("[A-Za-z]+")) return false;
        // 每个字母都要落在选项范围内
        for (char c : t.toUpperCase().toCharArray()) {
            if (c - 'A' >= choiceCount) return false;
        }
        return true;
    }

    /** 把字母串拆成大写字母字符集合，如 "A,b" -> {'A','B'}，兼容连写 "AB" */
    private Set<Character> toLetterCharSet(String letters) {
        Set<Character> set = new HashSet<>();
        if (letters == null) return set;
        for (char c : letters.toUpperCase().toCharArray()) {
            if (c >= 'A' && c <= 'Z') set.add(c);
        }
        return set;
    }

    /** 标准答案字母串 -> 选项内容集合 */
    private Set<String> mapLettersToContents(String letters, List<QuestionChoice> choices) {
        Set<String> result = new HashSet<>();
        if (choices == null) return result;
        String up = letters.toUpperCase();
        for (QuestionChoice c : choices) {
            char cLetter = letterOf(choices, c);
            if (up.indexOf(cLetter) >= 0 && c.getContent() != null) {
                result.add(c.getContent());
            }
        }
        return result;
    }

    /** 标准答案内容 -> 选项字母集合 */
    private Set<String> mapContentsToLetters(String contents, List<QuestionChoice> choices) {
        Set<String> result = new HashSet<>();
        if (choices == null) return result;
        Set<String> contentSet = splitAnswer(contents);
        for (QuestionChoice c : choices) {
            if (c.getContent() != null && contentSet.contains(c.getContent())) {
                result.add(String.valueOf(letterOf(choices, c)));
            }
        }
        return result;
    }
}
