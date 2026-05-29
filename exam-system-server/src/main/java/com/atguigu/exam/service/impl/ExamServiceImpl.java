package com.atguigu.exam.service.impl;

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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
        examRecordLambdaQueryWrapper.eq(ExamRecord::getStatus,"进行中");
        ExamRecord examRecord = this.getOne(examRecordLambdaQueryWrapper);
        if (examRecord != null) {
            log.info("%s同学的该考试正在进行中，请勿重复考试".formatted(startExamVo.getStudentName()));
            return examRecord;
        }
        //2.不存在则创建新的考试记录对象并存储到数据库中
        ExamRecord record = new ExamRecord();
        record.setStudentName(startExamVo.getStudentName());
        record.setExamId(startExamVo.getPaperId());
        record.setStatus("进行中");
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
        if(ObjectUtils.isNotEmpty(answerRecordList)){
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
     * @param examRecordId
     * @param answers
     */
    @Override
    public void customSubmitAnswer(Integer examRecordId, List<SubmitAnswerVo> answers) throws InterruptedException {
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
        examRecord.setStatus("已结束");
        examRecord.setEndTime(LocalDateTime.now());
        //更新对应的考试记录数据
        this.updateById(examRecord);
        //调用判卷接口
        gradeExam(examRecordId);
    }
    /**
     * ai试卷批阅功能
     * @param examRecordId
     * @return
     */
    @Override
    public ExamRecord gradeExam(Integer examRecordId) throws InterruptedException {
        //1.获取考试记录对象，包含其关联的试卷信息和答题记录信息
        ExamRecord examRecord = customGetExamRecordById(examRecordId);
        //获取考试记录关联的试卷信息，进行判空，如果为空就是考试对应的试卷已经被删除，无法进行成绩判定
        Paper paper = examRecord.getPaper();
        log.info("开始判卷，试卷信息为：{}",paper);
        if(paper == null){
            //试卷为空则给考试记录状态修改成已批阅
            examRecord.setStatus("已批阅");
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
            examRecord.setStatus("已批阅");
            //本次考试评语设置为出错问题
            examRecord.setSummary("考生没有作答成绩为0");
            //更新考试记录
            this.updateById(examRecord);
            return examRecord;
        }
        int correctNumber = 0 ; //正确题目数量
        int totalScore = 0; //总得分
        //将试卷包含的题目列表转换成题目ID对应的题目对象形式的map，方便根据题目ID取出对应的题目对象
        Map<Long, Question> questionMap = paper.getQuestions().stream().collect(Collectors.toMap(Question::getId, q -> q));
        log.info("试卷题目列表为：{}",questionMap);
        //3.遍历答题记录列表，进行判卷，期间记录正确题目数量和总得分，并且修改每条答题记录的状态，得分情况、是否正确以及ai评语
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
                
                //查询试卷题目关联表中该题目对应的分数（需要同时指定paperId和questionId确保唯一性）
                LambdaQueryWrapper<PaperQuestion> paperQuestionLambdaQueryWrapper = new LambdaQueryWrapper<>();
                paperQuestionLambdaQueryWrapper.eq(PaperQuestion::getPaperId, paper.getId()).eq(PaperQuestion::getQuestionId, question.getId());
                PaperQuestion paperQuestion = paperQuestionService.getOne(paperQuestionLambdaQueryWrapper);
                if (paperQuestion == null || paperQuestion.getScore() == null) {
                    log.warn("题目ID: {} 在试卷中未设置分数", question.getId());
                    throw new RuntimeException("题目分数未设置");
                }
                Integer score = paperQuestion.getScore().intValue();
                //获取题目的正确答案
                String CorrectAnswer = question.getAnswer().getAnswer();
                //获取学生答案
                String userAnswer = answerRecord.getUserAnswer();
                //2.判断学生答案是否正确,先分辨题目类型
                if(!"TEXT".equals(question.getType())){
                    //如果题目类型不是简答题直接进行答案比对
                    if(CorrectAnswer.equals(userAnswer))
                    {
                        //设置该题目对应的得分
                        answerRecord.setScore(score);
                        //设置该题目的对错,0是错误，1是正确，2是基本正确
                        answerRecord.setIsCorrect(1);
                    }else{
                        //简答题需要进行ai评卷，用GradingResult接收判卷结果
                        GradingResult gradingResult = kimiAiService.gradingTextQuestion(question, userAnswer, score);
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
        //4.更新答题记录列表
        answerRecordService.updateBatchById(answerRecordList);
        //5.根据得分情况、试卷总分数、试卷总题目数、答题正确题目数生成ai评价总结
        String summary = kimiAiService.buildSummary(totalScore, paper.getTotalScore().intValue(), paper.getQuestions().size(), correctNumber);
        //6.更新考试记录信息
        examRecord.setSummary(summary);
        examRecord.setScore(totalScore);
        examRecord.setStatus("已批阅");
        this.updateById(examRecord);
        return examRecord;
    }
}