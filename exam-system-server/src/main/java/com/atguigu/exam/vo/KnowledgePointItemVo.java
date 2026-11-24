package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识点条目 Vo —— 题目编辑页回显/选择器/标注响应共用的最小结构
 * 不含 description/createTime，避免题目标注响应携带管理字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识点条目（题目侧回显用）")
public class KnowledgePointItemVo {

    @Schema(description = "知识点ID", example = "1")
    private Long id;

    @Schema(description = "知识点名称", example = "面向对象")
    private String name;

    @Schema(description = "排序值", example = "1")
    private Integer sort;
}
