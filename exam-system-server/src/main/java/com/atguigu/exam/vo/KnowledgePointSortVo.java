package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识点批量排序条目 Vo（契约 9.5）
 * 管理页拖拽排序后一次性提交 POST /api/knowledge-points/sort
 */
@Data
@Schema(description = "知识点排序条目")
public class KnowledgePointSortVo {

    @Schema(description = "知识点ID", example = "1")
    private Long id;

    @Schema(description = "排序值", example = "1")
    private Integer sort;
}
