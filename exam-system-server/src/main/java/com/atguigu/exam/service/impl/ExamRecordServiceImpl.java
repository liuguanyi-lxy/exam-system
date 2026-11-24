package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.mapper.ClazzMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    @Autowired
    private ClazzMapper clazzMapper;
    @Autowired
    @Lazy
    private com.atguigu.exam.service.PaperService paperService;
    /**
     * 自定义多条件分页查询考试记录列表。
     * 列表页只需要试卷名称/总分等基础信息，批量查一次试卷表即可；
     * 不再逐条拉取整卷题目明细与答题记录（修复原实现的 N+1：每页 20 条记录要发起 40+ 次查询）
     */
    @Override
    public void customPageGetExamRecord(Page<ExamRecord> page, LambdaQueryWrapper<ExamRecord> examRecordLambdaQueryWrapper) {
        //先获取满足条件的考试记录列表,根据分页条件和查询条件的查询结果会封装到Page对象的records属性中
        page(page, examRecordLambdaQueryWrapper);
        List<ExamRecord> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return;
        }
        //按本页记录涉及的试卷ID批量取试卷基础信息（不含题目明细），挂到记录的 paper 字段供列表展示
        List<Long> paperIds = records.stream()
                .map(r -> r.getExamId() == null ? null : r.getExamId().longValue())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, com.atguigu.exam.entity.Paper> paperMap = paperIds.isEmpty() ? Map.of()
                : paperService.listByIds(paperIds).stream()
                        .collect(java.util.stream.Collectors.toMap(com.atguigu.exam.entity.Paper::getId, p -> p, (a, b) -> a));
        List<Long> classIds = records.stream().map(ExamRecord::getClassId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> classNames = classIds.isEmpty() ? Map.of() : clazzMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getName));
        for (ExamRecord record : records) {
            record.setPaper(record.getExamId() == null ? null : paperMap.get(record.getExamId().longValue()));
            if (record.getClassId() != null) record.setClassName(classNames.get(record.getClassId()));
        }
        log.info("考试记录分页查询：{} 条记录，批量关联试卷 {} 份", records.size(), paperMap.size());
    }
    /*
    * 自定义根据考试记录ID删除考试记录*/
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customRemoveById(Integer id) {
        //删除考试记录需要判断考试记录状态，进行中的考试记录不能删除
        ExamRecord examRecord = getById(id);
        if (examRecord == null) return;
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
    public List<ExamRankingVO> customGetRanking(Integer paperId, Integer limit, Collection<Long> classIds,
                                               Collection<Long> userIds, Long selfId) {
        return examRecordMapper.customQueryRanking(paperId, limit, classIds, userIds, selfId);
    }
}
