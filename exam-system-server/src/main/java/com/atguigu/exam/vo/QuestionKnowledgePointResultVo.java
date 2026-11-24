package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "题目知识点标注结果")
public record QuestionKnowledgePointResultVo(Long questionId, List<KnowledgePointItemVo> knowledgePoints) {
}
