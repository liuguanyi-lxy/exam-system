package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.CacheConstants;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.PaperQuestionMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.vo.QuestionImportVo;
import com.atguigu.exam.vo.QuestionPageVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 题目Service实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    @Autowired
    private QuestionChoiceMapper questionChoiceMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private PaperQuestionMapper paperQuestionMapper;
    @Autowired
    @Qualifier("examExecutor")
    private ThreadPoolExecutor examExecutor;
    //懒加载注入自身代理：批量导入时通过代理调用事务方法，避免同类直调绕过@Transactional
    @Autowired
    @Lazy
    private QuestionService self;
    // 自定义分页查询
    @Override
    public void customPageQuery(Page<Question> questionPage, QuestionPageVo questionPageVo) {
        // 构建查询条件
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
        //调用ObjectUtils工具类的isEmpty方法，判断传入的QuestionPageVo对象中的属性值是否为空，不为空才添加到查询条件中
        boolean hasCategoryId = !ObjectUtils.isEmpty(questionPageVo.getCategoryId());
        boolean hasDifficulty = !ObjectUtils.isEmpty(questionPageVo.getDifficulty());
        boolean hasType = !ObjectUtils.isEmpty(questionPageVo.getType());
        boolean hasKeyword = !ObjectUtils.isEmpty(questionPageVo.getKeyword());
        // 根据传入的参数，添加查询条件
        questionLambdaQueryWrapper.eq(hasCategoryId, Question::getCategoryId, questionPageVo.getCategoryId());
        questionLambdaQueryWrapper.eq(hasDifficulty, Question::getDifficulty, questionPageVo.getDifficulty());
        questionLambdaQueryWrapper.eq(hasType, Question::getType, questionPageVo.getType());
        questionLambdaQueryWrapper.like(hasKeyword, Question::getTitle, questionPageVo.getKeyword());
        //多条件分页查询结果要按照时间倒序排序，优先显示最近创建的题目
        questionLambdaQueryWrapper.orderByDesc(Question::getCreateTime);
        //根据分页条件和筛选条件进行分页查询
        page(questionPage, questionLambdaQueryWrapper);
        //查询结果question列表会添加到questionPage对象中，然后再进行question对象中选项和答案属性的填充
        List<Question> questionList = questionPage.getRecords();
        fillQuestionChoiceAndAnswer(questionList);
    }

    @Override
    public Question customDetailQuestion(Long id) {
        //查询题目详情
        Question question = questionMapper.customGetById(id);
        if(question== null){
            throw new RuntimeException("查询题目详情失败,题目可能被提前删除");
        }
        //查询题目详情一次就需要对其进行一次热题更新，根据访问题目次数进行排序
        /*不阻塞主流程
        用户查询题目详情是主要需求，必须快速返回
        更新访问次数是辅助功能（用于热门题目排序），可以后台慢慢执行
        如果同步执行，Redis 操作的网络延迟会影响接口响应时间
        使用全局共享线程池而不是 new Thread，避免频繁请求时线程创建开销*/
        examExecutor.execute(() -> incrementQuestion(id));
        return question;
    }
    @Transactional(rollbackFor = Exception.class)
    //自定义保存题目
    @Override
    public void customSaveQuestion(Question question) {
        //插入前端传入的题目信息
        //同一类型下的题目名称不应该相同
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
        questionLambdaQueryWrapper.eq(Question::getTitle, question.getTitle());
        questionLambdaQueryWrapper.eq(Question::getType, question.getType());
        boolean exists = exists(questionLambdaQueryWrapper);
        if (exists) {
            throw new RuntimeException("在%s下，存在%s 名称的题目！保存失败！".formatted(question.getType(), question.getTitle()));
        }
        boolean save = save(question);
        if (!save) {//保存失败
            throw new RuntimeException("在%s下，存在%s 名称的题目！保存失败！".formatted(question.getType(), question.getTitle()));
        }
        //根据题目对象获取答案对象，并且设置保存题目后回显的id
        QuestionAnswer answer = question.getAnswer();
        if (answer == null) {
            throw new RuntimeException("题目%s缺少答案信息，保存失败！".formatted(question.getTitle()));
        }
        answer.setQuestionId(question.getId());
        //判断题目是否为判断题，决定是否添加选项对象到选项表
        if ("CHOICE".equals(question.getType())) {
            List<QuestionChoice> choices = question.getChoices();
            if (choices == null) {
                throw new RuntimeException("选择题%s缺少选项信息，保存失败！".formatted(question.getTitle()));
            }
            //选项答案字符串
            StringBuilder sb = new StringBuilder();
            //对选项列表进行循环，获取其中的选项对象，然后设置选项对应的题目ID，再调用questionCHOICEMapper的insert方法进行保存
            //并且对每个选项的isCorrect属性进行判断，如果为true，则对答案字符串进行拼接
            for (int i = 0; i < choices.size(); i++) {
                QuestionChoice questionChoice = choices.get(i);
                // 清除ID，让数据库自动生成新的ID
                questionChoice.setId(null);
                questionChoice.setQuestionId(question.getId());
                questionChoice.setSort(i);
                questionChoiceMapper.insert(questionChoice);
                if(questionChoice.getIsCorrect()){
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    //答案拼接
                    sb.append((char)('A'+i));
                }
            }
            answer.setAnswer(sb.toString());
        }
        //保存答案对象
        questionAnswerMapper.insert(answer);
    }
    //自定义修改题目及其答案选项信息
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customUpdateQuestion(Question question) {
        //题目的校验,题目不同id名称不能相同
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
        questionLambdaQueryWrapper.eq(Question::getTitle, question.getTitle());
        questionLambdaQueryWrapper.ne(Question::getId, question.getId());
        boolean exists = exists(questionLambdaQueryWrapper);
        if (exists) {
            throw new RuntimeException("在%s下,存在%s名称的题目！修改失败！".formatted(question.getType(), question.getTitle()));
        }
        //保存题目信息
        boolean update = updateById(question);
        if (!update) {
            throw new RuntimeException("题目%s修改失败！".formatted(question.getTitle()));
        }
        //保存答案信息
        QuestionAnswer answer = question.getAnswer();
        if (answer == null) {
            throw new RuntimeException("题目%s缺少答案信息，修改失败！".formatted(question.getTitle()));
        }
        //判断题目是否为选择题，如果是，则对选项进行重新保存
        if ("CHOICE".equals(question.getType())) {
            List<QuestionChoice> choices = question.getChoices();
            if (choices == null) {
                throw new RuntimeException("选择题%s缺少选项信息，修改失败！".formatted(question.getTitle()));
            }
            //根据题目ID删除该题目下的所有原选项
            LambdaQueryWrapper<QuestionChoice> questionChoiceLambdaQueryWrapper = new LambdaQueryWrapper<>();
            questionChoiceLambdaQueryWrapper.eq(QuestionChoice::getQuestionId, question.getId());
            questionChoiceMapper.delete(questionChoiceLambdaQueryWrapper);
            //选项答案字符串
            StringBuilder sb = new StringBuilder();
            //对选项列表进行循环，获取其中的选项对象，然后设置选项对应的题目ID，再调用questionCHOICEMapper的insert方法进行保存
            //并且对每个选项的isCorrect属性进行判断，如果为true，则对答案字符串进行拼接
            for (int i = 0; i < choices.size(); i++) {
                QuestionChoice questionChoice = choices.get(i);
                // 清除ID，让数据库自动生成新的ID
                questionChoice.setId(null);
                questionChoice.setQuestionId(question.getId());
                questionChoice.setSort(i);
                questionChoiceMapper.insert(questionChoice);
                if(questionChoice.getIsCorrect()){
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    //答案拼接
                    sb.append((char)('A'+i));
                }
            }
            answer.setAnswer(sb.toString());
        }
        //答案的修改对选择题、判断题、简答题都生效：
        //按题目ID查出答案行再更新，不依赖前端回传答案表主键
        answer.setQuestionId(question.getId());
        QuestionAnswer existAnswer = questionAnswerMapper.selectOne(
                new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId, question.getId()));
        if (existAnswer != null) {
            answer.setId(existAnswer.getId());
            questionAnswerMapper.updateById(answer);
        } else {
            questionAnswerMapper.insert(answer);
        }
    }
    //自定义删除题目信息
    @Override
    @Transactional
    public void customRemoveQuestionById(Long id) {
        //判断试卷题目关联表，存在题目则删除失败
        LambdaQueryWrapper<PaperQuestion> paperQuestionLambdaQueryWrapper = new LambdaQueryWrapper<>();
        paperQuestionLambdaQueryWrapper.eq(PaperQuestion::getQuestionId, id);
        boolean exists = paperQuestionMapper.exists(paperQuestionLambdaQueryWrapper);
        if (exists) {
            throw new RuntimeException("该题目已被试卷引用，删除失败！");
        }
        //删除题目
        boolean remove = removeById(id);
        if (!remove) {
            throw new RuntimeException("题目ID为%s删除失败！".formatted(id));
        }
        //删除子表答案和选项,根据题目ID删除
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId, id));
        questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId, id));
    }
    //将传入文件进行解析成JAVA对象并且保存题目列表
    @Override
    public String importExeclBatchQuestions(MultipartFile file) throws IOException {
        //校验 传入的文件是否为null或者为空
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("导入的题目文件不能为空！");
        }
        //获取文件名
        String filename = file.getOriginalFilename();
        //校验传入的文件名是否以xls和xlsx结尾
        if (!filename.endsWith(".xls") && !filename.endsWith(".xlsx")) {
            throw new RuntimeException("文件格式不正确！必须是以xls和xlsx结尾");
        }
        //使用ExcelUtils工具对传入的文件进行解析
        List<QuestionImportVo> questionImportVos = ExcelUtil.parseExcel(file);
        //调用批量导入题目业务,记录成功保存的题目数量
        int successCount = importBatchQuestions(questionImportVos);
        String result="批量导入题目接口调用成功，一共:%s道题目需要导入，成功导入%s道题目".formatted(questionImportVos.size(),successCount);
        return result;
    }
    //批量导入题目业务,根据传入的题目导入列表进行题目保存
    public int importBatchQuestions(List<QuestionImportVo> questionImportVos) {
        int successCount = 0;
        //遍历题目列表并且try-catch对题目进行自定义保存，并统计成功保存题目数
        for (QuestionImportVo questionImportVo : questionImportVos) {
            try {
                //将QuestionImportVo对象转换成Question对象
                Question question =convertQuestionImportVoToQuestion(questionImportVo);
                //通过代理对象调用，让customSaveQuestion上的@Transactional生效（同类this调用会绕过事务代理）
                self.customSaveQuestion(question);
                successCount++;
            } catch (Exception e) {
                log.error("导入题目失败，题目内容为：{}，原因：{}", questionImportVo.getTitle(), e.getMessage());
            }
        }
        return successCount;
    }
    /**
     * 将QuestionImportVo对象转换成Question对象
     * @param questionImportVo
     * @return
     */
    private Question convertQuestionImportVoToQuestion(QuestionImportVo questionImportVo) {
        Question question = new Question();
        //使用BeanUtils的复制相同属性名的值的方法，将questionImportVo对象对应属性复制到Question对象中
        BeanUtils.copyProperties(questionImportVo, question);
        //判断题目类型是否为选择题，是就对选项进行赋值
        if("CHOICE".equals(questionImportVo.getType())) {
            //使用ArrayList获取有序的选项列表
            List<QuestionChoice> questionChoices = new ArrayList<>(questionImportVo.getChoices().size());
            //遍历questionImportVo的选项集合，复制其中choiceImportDto选项对象的对应值到QuestionChoice对象中，然后添加到选项集合中
            for (QuestionImportVo.ChoiceImportDto choiceImportDto : questionImportVo.getChoices()) {
                QuestionChoice questionChoice = new QuestionChoice();
                //使用BeanUtils的复制相同属性名的值的方法，将choiceImportDto对象对应属性复制到QuestionChoice对象中
                BeanUtils.copyProperties(choiceImportDto, questionChoice);
                questionChoices.add(questionChoice);
            }
            //将转换后的选项集合添加到question对象中
            question.setChoices(questionChoices);
        }
        //不管是不是选择题都得对答案赋值
        QuestionAnswer questionAnswer = new QuestionAnswer();
        //判断题需要将true和false大写，否则无法识别
        if("JUDGE".equals(questionImportVo.getType())) {
            questionAnswer.setAnswer(questionImportVo.getAnswer().toUpperCase());
        }else{
            questionAnswer.setAnswer(questionImportVo.getAnswer());
        }
        questionAnswer.setKeywords(questionImportVo.getKeywords());
        question.setAnswer(questionAnswer);
        return question;
    }
    //    将传入的文件转换成JAVA对应的vo对象
    @Override
    public List<QuestionImportVo> preViewExcel(MultipartFile file) throws IOException {
        //校验 传入的文件是否为null或者为空
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空！");
        }
        //获取文件名
        String filename = file.getOriginalFilename();
        //校验传入的文件名是否以xls和xlsx结尾
        if (!filename.endsWith(".xls") && !filename.endsWith(".xlsx")) {
            throw new RuntimeException("文件格式不正确！必须是以xls和xlsx结尾");
        }
        //使用ExcelUtils工具对传入的文件进行解析
        List<QuestionImportVo> questionImportVos = ExcelUtil.parseExcel(file);
        return questionImportVos;
    }

    //自定义查询热门题目列表
    @Override
    public List<Question> customFindPopularQuestions(Integer size) {
        //定义热门题目集合
        List<Question> popularQuestion = new ArrayList<>();
        //定义接收id的集合
        List<Long> ids = new ArrayList<>();
        //从Redis库中的zest中根据分数的倒序获取指定数量的题目id和对应的分数组成键值对的对象set集合
        try {
            Set<ZSetOperations.TypedTuple<Object>> questionIds = redisUtils.zReverseRangeWithScores(CacheConstants.POPULAR_QUESTIONS_KEY, 0, size - 1);
            //遍历获取到的题目id和分数
            if (questionIds != null && questionIds.size() > 0) {
                //由于Redis中zReverseRangeWithScores已经按分数降序返回，直接使用即可
                List<Long> idsList = questionIds.stream()
                        .map(o -> Long.valueOf(o.getValue().toString()))
                        .collect(Collectors.toList());
                //将排序后的id集合赋值给ids
                ids = idsList;
                log.debug("从Redis中获取到的热门题目id集合为：{}", ids);
                //根据热门题目id列表查询对应的题目列表
                for(Long id:ids){
                    Question question = getById(id);
                    if (question != null) {
                        popularQuestion.add(question);
                    }
                }
            }
        } catch (Exception e) {
            // Redis 不可用时降级：直接从数据库查询，不影响热门题目展示
            log.warn("Redis 获取热门题目失败，降级为数据库查询：{}", e.getMessage());
            popularQuestion = new ArrayList<>();
            ids = new ArrayList<>();
        }
        log.debug("查询到的热门题目列表为：{}", popularQuestion);
        //检查获取到的题目列表的数量是否满足需求，不足则需要补充
        int diff= size-popularQuestion.size();
        if(diff>0){
            //从非热门题目中按照时间倒序加limit+diff获取题目列表
            LambdaQueryWrapper<Question> questionLambdaQueryWrapper = new LambdaQueryWrapper<>();
            questionLambdaQueryWrapper.notIn(!ids.isEmpty(), Question::getId, ids);
            questionLambdaQueryWrapper.orderByDesc(Question::getCreateTime);
            //last是给查询条件最后添加一个SQL片段，限制获取的题目数
            questionLambdaQueryWrapper.last("limit "+diff);
            List<Question> questions = list(questionLambdaQueryWrapper);
            log.debug("从非热门题目中获取到的题目列表为：{}", questions);
            //对查询的补充题目列表进行判空，如果非空则添加到热门题目列表中
            if(questions!=null && questions.size()>0)
            {
                popularQuestion.addAll(questions);
            }
        }
        //对最后的热门题目列表的答案和选项进行填充
        fillQuestionChoiceAndAnswer(popularQuestion);
        return popularQuestion;
    }

    //自定义随机抽取题目，支持分类和难度过滤
    @Override
    public List<Question> customRandomQuestions(Integer count, Long categoryId, String difficulty) {
        int limit = count == null || count <= 0 ? 10 : Math.min(count, 50);
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Question::getCategoryId, categoryId);
        }
        if (difficulty != null && !difficulty.isBlank()) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        List<Question> all = list(wrapper);
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        // 随机打乱后截取
        Collections.shuffle(all);
        List<Question> result = all.subList(0, Math.min(limit, all.size()));
        // 填充答案和选项
        fillQuestionChoiceAndAnswer(result);
        return result;
    }

    //刷新热门题目缓存：重置所有题目访问计数
    @Override
    public int refreshPopularQuestions() {
        try {
            // 1. 清空旧的热门缓存
            redisUtils.delete(CacheConstants.POPULAR_QUESTIONS_KEY);
            // 2. 为所有题目设置初始计数 0
            List<Question> all = list();
            if (all != null && !all.isEmpty()) {
                for (Question q : all) {
                    redisUtils.zAdd(CacheConstants.POPULAR_QUESTIONS_KEY, q.getId(), 0);
                }
            }
            log.info("热门题目缓存刷新完成，共处理 {} 道题目", all == null ? 0 : all.size());
            return all == null ? 0 : all.size();
        } catch (Exception e) {
            // Redis 不可用时降级：仅记录日志，不影响接口调用
            log.warn("刷新热门题目缓存失败（Redis 不可用），已降级：{}", e.getMessage());
            return 0;
        }
    }

    //定义进行题目访问次数增长的方法
    //异步方法
    private void incrementQuestion(Long questionId){
        try {
            Double score = redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY,questionId,1);
            log.info("完成{}题目分数累计，累计后分数为：{}",questionId,score);
        } catch (Exception e) {
            // Redis 不可用时静默降级，不影响题目详情查询
            log.warn("Redis 访问计数失败（已忽略）：{}", e.getMessage());
        }
    }

    //填充question对象的选项和答案属性
    private void fillQuestionChoiceAndAnswer(List<Question> questionList) {
        //1. 非空判断
        if (questionList == null || questionList.size() == 0) {
            log.debug("没有查询对应的问题集合数据！！");
            return;
        }
        //2. 查询所有答案和选项
        //优化查询本次题目的答案和选项
        //查询本地题目集合对应的id集合！！
        List<Long> ids = questionList.stream().map(Question::getId).collect(Collectors.toList());
        //查询本次题目的选项集合
        List<QuestionChoice> questionChoiceList =
                questionChoiceMapper.selectList(new LambdaQueryWrapper<QuestionChoice>().in(QuestionChoice::getQuestionId,ids));
        //查询本次题目的答案
        List<QuestionAnswer> questionAnswers =
                questionAnswerMapper.selectList(new LambdaQueryWrapper<QuestionAnswer>().in(QuestionAnswer::getQuestionId,ids));
        //3. 答案和选项进行map转化
        Map<Long, List<QuestionChoice>> questionChoiceMap =
                questionChoiceList.stream().collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        Map<Long, QuestionAnswer> answerMap =
                questionAnswers.stream().collect(Collectors.toMap(QuestionAnswer::getQuestionId, a -> a));
        //4. 循环问题集合，进行选项和答案配置
        questionList.forEach(question -> {
            //每个题目一定答案
            question.setAnswer(answerMap.get(question.getId()));
            //选择题才有选项
            if ("CHOICE".equals(question.getType())){
                List<QuestionChoice> questionChoices = questionChoiceMap.get(question.getId());
                if (questionChoices != null) {
                    questionChoices.sort(Comparator.comparingInt(QuestionChoice::getSort));
                    question.setChoices(questionChoices);
                }
            }
        });
    }
}