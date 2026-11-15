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
     * @param startExamVo 开考参数（试卷ID、考生姓名）
     * @param currentUserId 当前登录用户ID，用于考试记录归属
     * @return
     */
    ExamRecord startExam(StartExamVo startExamVo, Long currentUserId);

    /**
     * 获取考试记录详情,包括其中关联的试卷信息
     * @param id
     * @return
     */
    ExamRecord customGetExamRecordById(Integer id);

    /**
     * 提交考试答案：校验记录归属、进行中状态与考试时间后保存答题记录并结束考试（事务内），判卷由 /grade 接口触发
     * @param examRecordId 考试记录ID
     * @param answers 答题列表
     * @param currentUserId 当前登录用户ID，校验记录归属
     * @param isTeacherOrAdmin 当前用户是否教师/管理员（管理员代操作场景放行）
     */
    void customSubmitAnswer(Integer examRecordId, List<SubmitAnswerVo> answers, Long currentUserId, boolean isTeacherOrAdmin);

    /**
     * ai试卷批阅功能（客观题本地判卷，简答题并行调用AI，已批阅的记录直接返回）
     * 校验记录归属：仅考生本人或教师/管理员可触发判卷
     * @param examRecordId
     * @param currentUserId 当前登录用户ID
     * @param isTeacherOrAdmin 当前用户是否教师/管理员
     * @return
     */
    ExamRecord gradeExam(Integer examRecordId, Long currentUserId, boolean isTeacherOrAdmin);
}
