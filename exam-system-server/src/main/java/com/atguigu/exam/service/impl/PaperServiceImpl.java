package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.PaperQuestionMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.AiPaperVo;
import com.atguigu.exam.vo.PaperVo;
import com.atguigu.exam.vo.RuleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.channel.DefaultFileRegion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 试卷服务实现类
 */
@Slf4j
@Service
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {

    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private PaperMapper paperMapper;
    @Autowired
    private PaperQuestionService paperQuestionService;
    @Autowired
    private ExamRecordService examRecordService;

    /**
     * 获取所有试卷信息,支持按名称模糊搜索和状态筛选的试卷列表查询
     * @return
     */
    @Override
    public List<Paper> getAllPapers(String name, String status) {
        LambdaQueryWrapper<Paper> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(ObjectUtils.isNotEmpty(name), Paper::getName, "%"+name+"%");
        queryWrapper.eq(ObjectUtils.isNotEmpty(status), Paper::getStatus, status);
        List<Paper> paperList = list(queryWrapper);
        return paperList;
    }
    /**
     * 根据试卷id试卷详情
     *    试卷对象
     *    题目集合
     *    注意： 题目的选项sort正序
     *    注意： 所有题目根据类型排序
     * @param id 试卷id
     * @return
     */
    @Override
    public Paper customPaperDetailById(Long id) {
        //根据试卷ID进行单表查询，得到试卷的基本信息
        Paper paper = getById(id);
        //判断试卷是否为null
        if (ObjectUtils.isEmpty(paper)) {
            throw new RuntimeException("ID为%s试卷不存在".formatted(id));
        }
        //根据试卷ID在试卷题目关联表中进行题目查询
        List<Question> questionList = questionMapper.customQueryQuestionListByPaperId(id);
        //校验题目列表是否为null
        if (ObjectUtils.isEmpty(questionList)) {
            //如果为null，则将试卷的题目集合设置为空集合
            paper.setQuestions(new ArrayList<Question>());
            log.warn(("ID为%s试卷没有题目，但可以进行试卷编辑，但是不能用于考试").formatted(id));
            return paper;
        }
        log.debug("试卷ID:{} 题目数量:{}", id, questionList == null ? 0 : questionList.size());
        //题目列表中的题目按照选择题、判断题、简答题的形式进行排序
        // 明确题型顺序：选择 -> 判断 -> 手写代码 -> 简答，避免直接按字母序把 CODE 排在判断前面
        questionList.sort((o1, o2) -> Integer.compare(typeRank(o1.getType()), typeRank(o2.getType())));
        log.debug("试卷ID:{} 排序完成", id);
        //将排序后的题目列表设置到试卷对象中
        paper.setQuestions(questionList);
        return paper;
    }
    //手动组卷
    @Override
    public Paper customCreatePaper(PaperVo paperVo) {
        //校验试卷名称是否重复
        //在试卷表中找是否已经拥有该名称的试卷
        LambdaQueryWrapper<Paper> paperLambdaQueryWrapper = new LambdaQueryWrapper<>();
        paperLambdaQueryWrapper.eq(Paper::getName, paperVo.getName());
        if(exists(paperLambdaQueryWrapper)){
            throw new RuntimeException("%s试卷已存在,请重新命名".formatted(paperVo.getName()));
        }
        //根据传入的试卷信息对象进行初步的试卷信息导入
        Paper paper = new Paper();
        //将paperVo参数赋值给paper对象相同属性名的字段中
        BeanUtils.copyProperties(paperVo, paper);
        //将试卷状态设置为"草稿"
        paper.setStatus("DRAFT");
        //根据传入的题目ID和题目分数进行组卷
        //先进行判空
        if (ObjectUtils.isEmpty(paperVo.getQuestions())) {
            //没有题目则设置总分数和题目数为0
            paper.setTotalScore(BigDecimal.ZERO);
            paper.setQuestionCount(0);
            //将试卷对象插入数据库中
            save(paper);
            log.warn("试卷没有题目，但可以进行试卷编辑，但是不能用于考试");
            return paper;
        }
        //有题目则进行组卷
        //设置试卷的题目数
        paper.setQuestionCount(paperVo.getQuestions().size());
        //设置试卷的总分数
        paper.setTotalScore(paperVo.getQuestions().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        //完成试卷的插入 -》 主键回显 paperId
        save(paper);
        //在试卷题目关联表进行试卷和题目的关联，插入试卷ID和对应的题目ID及分数
        //先将paperVo中的题目ID和分数以及试卷ID转换成PaperQuestion对象
        //将题目ID和题目分数组成的map转换成entry键值对set集合，这样就能遍历键值对，再通过映射将每个键值对中的ID和分数映射给PaperQuestion对象的属性，并设置paperID属性
        //最后将映射成PaperQuestion对象的集合转换成List集合完成收集
        List<PaperQuestion> paperQuestionList = paperVo.getQuestions().entrySet().stream()
                .map(entry -> new PaperQuestion(paper.getId().intValue(), entry.getKey().longValue(), entry.getValue()))
                .collect(Collectors.toList());
        //将试卷题目关联列表中的数据批量插入到数据库
        paperQuestionService.saveBatch(paperQuestionList);
        return paper;
    }
    /**
     * 智能组卷
     * @param aiPaperVo
     * @return
     */
    @Override
    public Paper customAiCreatePaper(AiPaperVo aiPaperVo) {
        //校验试卷名称是否重复
        //在试卷表中找是否已经拥有该名称的试卷
        LambdaQueryWrapper<Paper> paperLambdaQueryWrapper = new LambdaQueryWrapper<>();
        paperLambdaQueryWrapper.eq(Paper::getName, aiPaperVo.getName());
        if(exists(paperLambdaQueryWrapper)){
            throw new RuntimeException("%s试卷已存在,请重新命名".formatted(aiPaperVo.getName()));
        }
        //根据传入的智能组卷信息对象进行初步的试卷信息导入
        Paper paper = new Paper();
        //将aiPaperVo参数赋值给paper对象相同属性名的字段中
        BeanUtils.copyProperties(aiPaperVo, paper);
        //将试卷状态设置为"草稿"
        paper.setStatus("DRAFT");
        //对试卷进行初步的保存
        save(paper);
        //在组卷规则下进行题目的选择和在试卷题目关联表中添加数据
        int questionCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        //遍历组卷规则,根据每种题型的分类进行题目查询,再根据个数进行截取，然后修改试卷的题目数和总分数以及向试卷题目关联表中添加数据，实现组卷功能
        for (RuleVo ruleVo : aiPaperVo.getRules()) {
            //1.校验该题型的要求的题目个数
            if(ruleVo.getCount()==0)
            {
                log.info("%s题型不需要题目".formatted(ruleVo.getType()));
            }
            //2.查询当前规则下的题目集合
            LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
            questionLambdaQueryWrapper.eq(Question::getType, ruleVo.getType());
            questionLambdaQueryWrapper.in(ObjectUtils.isNotEmpty(ruleVo.getCategoryIds()), Question::getCategoryId, ruleVo.getCategoryIds());
            List<Question> questionList = questionMapper.selectList(questionLambdaQueryWrapper);
            //3.对查询的题目集合进行判空
            if (ObjectUtils.isEmpty(questionList)) {
                log.info("当前规则%s下没有符合要求的题目".formatted(ruleVo));
                continue;
            }
            //4.获取该规则下的实际题目数量
            int actualCount = Math.min(ruleVo.getCount(), questionList.size());
            //5.对试卷题目数量的累加和分数的累加
            questionCount += actualCount;
            //规则下题目的分数乘以实际题目数量为该规则下的总分数
            totalScore = totalScore.add(BigDecimal.valueOf(ruleVo.getScore()*actualCount));
            //6.查询的满足规则的题目数量可能多于实际规则要求的数量，就将题目集合打乱再取要求的数量实现从中随机抽取的效果
            Collections.shuffle(questionList);
            //对题目集合进行截取
            List<Question> realQuestionList = questionList.subList(0, actualCount);
            //将截取的题目集合转换成PaperQuestion集合，插入到试卷题目关联表中，实现组卷功能
            List<PaperQuestion> paperQuestionList = realQuestionList.stream()
                    .map(question -> new PaperQuestion(paper.getId().intValue(), question.getId().longValue(), BigDecimal.valueOf(ruleVo.getScore())))
                    .collect(Collectors.toList());
            paperQuestionService.saveBatch(paperQuestionList);
        }
        //组卷完成对试卷的属性进行修改，修改试卷题目数和总分数
        paper.setQuestionCount(questionCount);
        paper.setTotalScore(totalScore);
        //更新试卷信息
        updateById(paper);
        return paper;
    }
    /**
     * 更新试卷内信息
     * @param id
     * @param paperVo
     * @return
     */
    @Override
    public Paper customUpdatePaper(Integer id, PaperVo paperVo) {
        //校验，如果试卷为发布状态就不能修改试卷信息
        Paper paper = getById(id);
        if("PUBLISHED".equals(paper.getStatus())){
            throw new RuntimeException("试卷已发布，不能修改");
        }
        //校验试卷名称是否可以修改
        //在试卷表中找同名但是不同ID的试卷
        LambdaQueryWrapper<Paper> paperLambdaQueryWrapper = new LambdaQueryWrapper<>();
        paperLambdaQueryWrapper.eq(Paper::getName, paperVo.getName());
        paperLambdaQueryWrapper.ne(Paper::getId, id);
        if(exists(paperLambdaQueryWrapper)){
            throw new RuntimeException("%s试卷已存在,请重新命名".formatted(paperVo.getName()));
        }
        //将paperVo参数赋值给paper对象相同属性名的字段中
        BeanUtils.copyProperties(paperVo, paper);
        //试卷的包含的题目信息可能改变就需要重新计算分数和题目数
        paper.setTotalScore(paperVo.getQuestions().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        paper.setQuestionCount(paperVo.getQuestions().size());
        //更新试卷信息
        updateById(paper);
        //因为试卷关联的题目改变，所以试卷题目关联表需要更新，直接删除原本的数据再插入新数据
        paperQuestionService.remove(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
        List<PaperQuestion> paperQuestionList = paperVo.getQuestions().entrySet().stream()
                .map(entry -> new PaperQuestion(paper.getId().intValue(), entry.getKey().longValue(), entry.getValue()))
                .collect(Collectors.toList());
        //新的试卷题目关联列表插入到数据库
        paperQuestionService.saveBatch(paperQuestionList);
        return paper;
    }
    /**
     * 根据id修改状态
     * @param id    试卷ID
     * @param status 想要被修改成的试卷状态
     */
    @Override
    public void customUpdatePaperStatus(Integer id, String status) {
        //从发布状态修改为停止状态不需要校验
        //但是从草稿状态或者停止状态修改为发布状态需要校验，试卷题目为空集合就不呢发布
        if("PUBLISHED".equals(status)){
            //校验试卷题目是否为空
            LambdaQueryWrapper<PaperQuestion> paperQuestionLambdaQueryWrapper = new LambdaQueryWrapper<>();
            paperQuestionLambdaQueryWrapper.eq(PaperQuestion::getPaperId, id);
            if(paperQuestionService.count(paperQuestionLambdaQueryWrapper) == 0){
                throw new RuntimeException("试卷题目为空，不能发布");
            }
        }
        //根据试卷ID进行试卷状态的修改
        LambdaUpdateWrapper<Paper> paperLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        paperLambdaUpdateWrapper.eq(Paper::getId, id);
        paperLambdaUpdateWrapper.set(Paper::getStatus, status);
        update(paperLambdaUpdateWrapper);
    }
    /**
     * 根据id删除试卷功能
     * @param id
     */
    @Override
    public void customRemoveId(Integer id) {
        //删除试卷需要判断其状态，发布状态的试卷不能删除
        Paper paper = getById(id);
        if("PUBLISHED".equals(paper.getStatus())){
            throw new RuntimeException("试卷处于发布状态，不能删除");
        }
        //该试卷不能有关联的考试记录，否则不能删除
        LambdaQueryWrapper<ExamRecord> examPaperLambdaQueryWrapper = new LambdaQueryWrapper<>();
        examPaperLambdaQueryWrapper.eq(ExamRecord::getExamId, id);
        if(examRecordService.count(examPaperLambdaQueryWrapper) > 0){
            throw new RuntimeException("试卷有关联的考试记录，不能删除");
        }
        //删除试卷
        removeById(id);
        //删除试卷题目关联表中的数据
        paperQuestionService.remove(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
    }

    /** 题型排序权重：选择 -> 判断 -> 简答 -> 手写代码（先答文字题，再做编程题） */
    private int typeRank(String type) {
        if ("CHOICE".equals(type)) return 0;
        if ("JUDGE".equals(type)) return 1;
        if ("TEXT".equals(type)) return 2;
        if ("CODE".equals(type)) return 3;
        return 4;
    }
}