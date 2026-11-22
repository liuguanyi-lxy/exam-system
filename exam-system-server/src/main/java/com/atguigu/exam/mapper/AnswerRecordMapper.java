package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.vo.WrongQuestionVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

/**
 * 答题记录 Mapper
 *
 * @Entity com.atguigu.exam.entity.AnswerRecord
 */
public interface AnswerRecordMapper extends BaseMapper<AnswerRecord> {

    /**
     * 错题本分页查询：按 question_id 聚合本人 is_correct=0 的答题记录。
     *
     * @param page            分页对象（MyBatis-Plus 分页拦截器首参）
     * @param userId          当前登录人ID，归属定界唯一依据
     * @param type            题型筛选，已归一化，null 表示不过滤
     * @param difficulty      难度筛选，已归一化，null 表示不过滤
     * @param categoryId      分类筛选，已校验存在，null 表示不过滤
     * @param includeMastered true 时同时返回已掌握的题，由记录 mastered 字段区分
     */
    Page<WrongQuestionVo> selectWrongQuestionPage(Page<WrongQuestionVo> page,
                                                  @Param("userId") Long userId,
                                                  @Param("type") String type,
                                                  @Param("difficulty") String difficulty,
                                                  @Param("categoryId") Long categoryId,
                                                  @Param("includeMastered") Boolean includeMastered);

    /**
     * 已掌握错题分页查询：与错题本同结构，反向过滤"标记时间不早于最近一次答错时间"的题。
     * 错题本接口 masteredOnly=true 时走本方法；保留独立方法便于后续扩展独立的已掌握列表接口。
     */
    Page<WrongQuestionVo> selectMasteredQuestionPage(Page<WrongQuestionVo> page,
                                                     @Param("userId") Long userId,
                                                     @Param("type") String type,
                                                     @Param("difficulty") String difficulty,
                                                     @Param("categoryId") Long categoryId);
}
