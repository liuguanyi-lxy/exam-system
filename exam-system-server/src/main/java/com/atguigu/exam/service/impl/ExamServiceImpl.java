package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.*;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.*;
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
import java.util.List;
import java.util.Map;
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
    private KimiAiService kimiAiService;
    @Autowired
    @Qualifier("examExecutor")
    private ThreadPoolExecutor examExecutor;
    /**
     * 开始考试业务
     * @param startExamVo
     * @return
     */
    @Override
    public ExamRecord startExam(StartExamVo startExamVo) {
        //创建一个考试记录对象并存储到数据库中
        //1.校验该学生是否有相同试卷ID的考试正在进行，存在则查询对应的试卷对象赋值给考试记录对象的属性然后直接返回
        LambdaQueryWrapper<ExamRecord> examRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
        examRecordLambdaQueryWrapper.eq(ExamRecord::getStudentName, startExamVo.getStudentName());
        examRecordLambdaQueryWrapper.eq(ExamRecord::getExamId, startExamVo.getPaperId());
        examRecordLambdaQueryWrapper.eq(ExamRecord::getStatus, ExamStatus.IN_PROGRESS);
        //getOne传入false：存在多条进行中记录时取第一条而不是抛异常，避免历史脏数据导致无法开考
        ExamRecord examRecord = this.getOne(examRecordLambdaQueryWrapper, false);
        if (examRecord != null) {
            log.info("%s同学的该考试正在进行中，请勿重复考试".formatted(startExamVo.getStudentName()));
            return examRecord;
        }
        //2.不存在则创建新的考试记录对象并存储到数据库中
        ExamRecord record = new ExamRecord();
        record.setStudentName(startExamVo.getStudentName());
        record.setExamId(startExamVo.getPaperId());
        record.setStatus(ExamStatus.IN_PROGRESS);
        record.setWindowSwitches(0);
        record.setStartTime(LocalDateTime.now());//使用的是本地的时区时间
        //进行试卷记录保存
        this.save(record);
        //返回试卷记录，即包含其中的试卷信息给前端
        return record;
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
     * 只负责保存答题记录并结束考试，判卷由结果页调用 /grade 接口触发，
     * 避免交卷请求同步等待AI判卷导致前端30秒超时
     * @param examRecordId
     * @param answers
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customSubmitAnswer(Integer examRecordId, List<SubmitAnswerVo> answers) {
        //将答题记录保存在试卷记录和题目的关联表，也就答题记录表，记录考试记录的ID、题目ID、学生答案。然后再修改考试记录的考试状态和结束时间
        //1.将前端传入的提交答案列表保存到答题记录表
        //首先要对提交答案列表进行判空,提交答案列表为空就不用保存答题记录
        if(ObjectUtils.isNotEmpty(answers))
        {
            //将传入的提交答案列表映射成答题记录列表
            List<AnswerRecord> answerRecordList = answers.stream().map(answer ->
                //将流中的每个元素拿出来进行映射成新的东西，也就是答题记录对象
                new AnswerRecord(examRecordId, answer.getQuestionId(), answer.getUserAnswer())
            ).collect(Collectors.toList());
            answerRecordService.saveBatch(answerRecordList);
        }

        //2.修改考试记录的考试状态和结束时间
        ExamRecord examRecord = getById(examRecordId);
        if (examRecord == null) {
            throw new RuntimeException("ID为%s的考试记录不存在，提交失败！".formatted(examRecordId));
        }
        examRecord.setStatus(ExamStatus.FINISHED);
        examRecord.setEndTime(LocalDateTime.now());
        //更新对应的考试记录数据
        this.updateById(examRecord);
    }
    /**
     * ai试卷批阅功能
     * 客观题本地即时判卷，简答题的AI调用先并行提交到共享线程池再统一等待，
     * 总耗时约等于一次AI调用，而不是简答题数量 × 单次调用时长
     * @param examRecordId
     * @return
     */
    @Override
    public ExamRecord gradeExam(Integer examRecordId) {
        //1.获取考试记录对象，包含其关联的试卷信息和答题记录信息
        ExamRecord examRecord = customGetExamRecordById(examRecordId);
        if (examRecord == null) {
            throw new RuntimeException("ID为%s的考试记录不存在，无法判卷".formatted(examRecordId));
        }
        //已批阅过的记录直接返回：结果页刷新时不会重复消耗AI调用
        if (ExamStatus.GRADED.equals(examRecord.getStatus())) {
            return examRecord;
        }
        //获取考试记录关联的试卷信息，进行判空，如果为空就是考试对应的试卷已经被删除，无法进行成绩判定
        Paper paper = examRecord.getPaper();
        log.info("开始判卷，试卷信息为：{}",paper);
        if(paper == null){
            //试卷为空则给考试记录状态修改成已批阅
            examRecord.setStatus(ExamStatus.GRADED);
            //本次考试评语设置为出错问题
            examRecord.setSummary("考试对应的试卷已被删除，无法进行成绩判定");
            //更新考试记录
            this.updateById(examRecord);
            throw new RuntimeException("考试对应的试卷已被删除，无法进行成绩判定");
        }
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
        List<Question> paperQuestions = paper.getQuestions() == null ? new ArrayList<>() : paper.getQuestions();
        //将试卷包含的题目列表转换成题目ID对应的题目对象形式的map，方便根据题目ID取出对应的题目对象
        Map<Long, Question> questionMap = paperQuestions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        log.info("试卷题目列表为：{}",questionMap);
        //一次性查出本试卷每道题的分值，key为题目ID，避免在循环里逐条查库
        Map<Long, BigDecimal> scoreMap = paperQuestionService.list(
                        new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, paper.getId())).stream()
                .filter(pq -> pq.getQuestionId() != null && pq.getScore() != null)
                .collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore, (a, b) -> a));
        //3.先把所有简答题的AI判卷任务并行提交到共享线程池
        Map<Integer, CompletableFuture<GradingResult>> aiFutures = new HashMap<>();
        for (AnswerRecord answerRecord : answerRecordList) {
            Question question = questionMap.get(answerRecord.getQuestionId() == null ? null : answerRecord.getQuestionId().longValue());
            if (question == null || !"TEXT".equals(question.getType())) {
                continue;
            }
            BigDecimal paperScore = scoreMap.get(answerRecord.getQuestionId().longValue());
            Integer maxScore = paperScore == null ? null : paperScore.intValue();
            aiFutures.put(answerRecord.getQuestionId(),
                    CompletableFuture.supplyAsync(() -> safeGradeTextQuestion(question, answerRecord.getUserAnswer(), maxScore), examExecutor));
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
                if("TEXT".equals(question.getType())){
                    //简答题使用AI进行评卷，从并行任务中取回判卷结果，最多等待2分钟
                    GradingResult gradingResult;
                    try {
                        gradingResult = aiFutures.get(answerRecord.getQuestionId()).get(2, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.error("题目ID: {} AI判卷任务执行失败", answerRecord.getQuestionId(), e);
                        gradingResult = null;
                    }
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
        String summary = kimiAiService.buildSummary(totalScore, paper.getTotalScore().intValue(), paperQuestions.size(), correctNumber);
        //7.更新考试记录信息
        examRecord.setSummary(summary);
        examRecord.setScore(totalScore);
        examRecord.setStatus(ExamStatus.GRADED);
        this.updateById(examRecord);
        return examRecord;
    }

    /**
     * 线程池内执行的简答题AI判卷，任何异常都吞掉并返回null，由主流程统一按判卷失败处理
     */
    private GradingResult safeGradeTextQuestion(Question question, String userAnswer, Integer maxScore) {
        try {
            if (maxScore == null) {
                throw new RuntimeException("题目分数未设置");
            }
            return kimiAiService.gradingTextQuestion(question, userAnswer == null ? "" : userAnswer, maxScore);
        } catch (Exception e) {
            log.error("简答题AI判卷失败，题目ID: {}", question.getId(), e);
            return null;
        }
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
                boolean userIsLetter = isPureLetterSet(userSet);
                boolean correctIsLetter = isPureLetterSet(correctSet);

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
                    // 学生答内容，标准答字母 -> 字母映射到内容比较
                    return userSet.equals(mapLettersToContents(correctAnswer, choices));
                } else {
                    // 两者都是选项内容，直接比较
                    return userSet.equals(correctSet);
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
            char cLetter = (char) ('A' + c.getSort());
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
            char cLetter = (char) ('A' + c.getSort());
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
        for (String s : set) {
            if (!s.matches("[A-Za-z]+")) return false;
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
            char cLetter = (char) ('A' + c.getSort());
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
                result.add(String.valueOf((char) ('A' + c.getSort())));
            }
        }
        return result;
    }
}