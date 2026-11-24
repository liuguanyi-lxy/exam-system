package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.ExamRankingVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 考试记录Service接口
 * 定义考试记录相关的业务方法
 */
public interface ExamRecordService extends IService<ExamRecord> {
    /**
     * 自定义获取考试记录详情，分页查询考试记录，支持多条件筛选的考试记录分页查询，包括按姓名、状态、时间范围等筛选
     */
    void customPageGetExamRecord(Page<ExamRecord> page, LambdaQueryWrapper<ExamRecord> examRecordLambdaQueryWrapper);
    /**
     * 删除考试记录
     * @param id
     */
    void customRemoveById(Integer id);

    /**
     * 查询排行榜业务
     * @param paperId
     * @param limit
     * @return
     */
    List<ExamRankingVO> customGetRanking(Integer paperId, Integer limit, java.util.Collection<Long> userIds);
}
