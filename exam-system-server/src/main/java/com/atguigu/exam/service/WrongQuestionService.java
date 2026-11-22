package com.atguigu.exam.service;

import com.atguigu.exam.vo.WrongQuestionVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 错题本 Service
 *
 * 错题数据不物化：实时从 answer_record JOIN exam_records 按当前登录人 userId 派生；
 * "已掌握"状态由 wrong_question_mastered 标记表承载。
 */
public interface WrongQuestionService {

    /**
     * 分页查询当前登录人的错题本（按题目聚合，最近答错的排前面）。
     *
     * @param userId          当前登录人ID（归属定界的唯一依据）
     * @param page            页码，从1开始；超过 10000 按 10000 处理
     * @param size            每页条数；超过 100 按 100 处理
     * @param type            题型筛选，非法值忽略
     * @param difficulty      难度筛选，非法值忽略
     * @param categoryId      分类筛选，非数字/不存在时忽略
     * @param includeMastered true 时同时返回已掌握的题，由 mastered 字段区分（masteredOnly=true 时本参数被忽略）
     * @param masteredOnly    true 时只返回已掌握的题（服务端过滤，供"已掌握"页签分页使用）
     */
    Page<WrongQuestionVo> pageWrongQuestions(Long userId, Integer page, Integer size,
                                             String type, String difficulty, String categoryId,
                                             Boolean includeMastered, Boolean masteredOnly);

    /**
     * 标记已掌握（移出错题本），幂等；重复标记只刷新 mastered_time。
     */
    void markMastered(Long userId, Long questionId);

    /**
     * 取消已掌握（恢复进错题本），幂等；无标记时同样成功。
     */
    void unmarkMastered(Long userId, Long questionId);
}
