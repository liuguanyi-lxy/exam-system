package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.AnswerRecordService;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.vo.ExamRankingVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 考试记录Service实现类
 * 实现考试记录相关的业务逻辑
 */
@Service
@Slf4j
public class ExamRecordServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamRecordService {

    @Autowired
    @Lazy
    private ExamService examService;
    @Autowired
    private AnswerRecordService answerRecordService;
    @Autowired
    private ExamRecordMapper examRecordMapper;
    /**
     * 自定义多条件分页查询获取考试记录详情，每个考试记录对象包含对应的试卷详情以及答题记录
     */
    @Override
    public void customPageGetExamRecord(Page<ExamRecord> page, LambdaQueryWrapper<ExamRecord> examRecordLambdaQueryWrapper) {
        //先获取满足条件的考试记录列表,根据分页条件和查询条件的查询结果会封装到Page对象的records属性中
        page(page, examRecordLambdaQueryWrapper);
        log.info("考试记录分页查询结果:{}", page.getRecords());
        //将考试记录列表根据考试记录ID调用examService自定义方法获取考试记录详情，每个考试记录对象包含对应的试卷详情以及答题记录
        List<ExamRecord> examRecordList = page.getRecords().stream()
                .map(examRecord -> examService.customGetExamRecordById(examRecord.getId().intValue()))
                .filter(examRecord -> examRecord != null)
                .toList();
        //将考试记录详情列表封装到Page对象的records属性中
        page.setRecords(examRecordList);
    }
    /*
    * 自定义根据考试记录ID删除考试记录*/
    @Override
    public void customRemoveById(Integer id) {
        //删除考试记录需要判断考试记录状态，进行中的考试记录不能删除
        ExamRecord examRecord = getById(id);
        if (examRecord.getStatus().equals("进行中")) {
            throw new RuntimeException("进行中的考试记录不能删除！");
        }
        //删除考试记录
        removeById(id);
        //删除对应的答题记录
        answerRecordService.remove(new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId, id));
    }
    /**
     * 查询考试记录排行榜业务,按照分数降序，用时试卷升序排列，返回limit条数据
     * @param paperId
     * @param limit
     * @return
     */
    @Override
    public List<ExamRankingVO> customGetRanking(Integer paperId, Integer limit) {
        return examRecordMapper.customQueryRanking(paperId,limit);
    }
}