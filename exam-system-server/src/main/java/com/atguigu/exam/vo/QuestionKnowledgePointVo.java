package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 题目知识点标注请求 Vo（契约 10.1 整体替换）
 * 空数组 = 清空全部标注
 */
@Data
@Schema(description = "题目标注知识点请求")
public class QuestionKnowledgePointVo {

    @Schema(description = "目标知识点集合（整体替换）", example = "[1, 3]")
    private List<Long> knowledgePointIds;
}
