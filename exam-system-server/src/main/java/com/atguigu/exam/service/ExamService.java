package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.ExamRankingVO;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 考试服务接口
 */
public interface ExamService extends IService<ExamRecord> {
    /**
     * 开始考试业务
     * @param startExamVo
     * @return
     */
    ExamRecord startExam(StartExamVo startExamVo);

    /**
     * 获取考试记录详情,包括其中关联的试卷信息
     * @param id
     * @return
     */
    ExamRecord customGetExamRecordById(Integer id);

    /**
     * 提交考试答案：只保存答题记录并结束考试（事务内），判卷由 /grade 接口触发
     * @param examRecordId
     * @param answers
     */
    void customSubmitAnswer(Integer examRecordId, List<SubmitAnswerVo> answers);

    /**
     * ai试卷批阅功能（客观题本地判卷，简答题并行调用AI，已批阅的记录直接返回）
     * @param examRecordId
     * @return
     */
    ExamRecord gradeExam(Integer examRecordId);
}
