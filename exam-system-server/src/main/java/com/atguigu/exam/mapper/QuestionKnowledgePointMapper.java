package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.QuestionKnowledgePoint;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题目-知识点标注Mapper接口
 * 继承MyBatis Plus的BaseMapper；题目标注整体替换与按知识点筛题共用
 */
public interface QuestionKnowledgePointMapper extends BaseMapper<QuestionKnowledgePoint> {
    @Insert({"<script>",
            "INSERT INTO question_knowledge_points (question_id, knowledge_point_id, created_by) VALUES",
            "<foreach collection='knowledgePointIds' item='pointId' separator=','>",
            "(#{questionId}, #{pointId}, #{operatorId,jdbcType=BIGINT})",
            "</foreach>",
            "</script>"})
    int insertLinks(@Param("questionId") Long questionId,
                    @Param("knowledgePointIds") List<Long> knowledgePointIds,
                    @Param("operatorId") Long operatorId);
}
