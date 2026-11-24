package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * projectName: day23_exam-system-server
 *
 * @author: 赵伟风
 * description: 接收题目分页查询的四个参数
 */
@Data
@Schema(description = "接收分页四个核心参数的Vo")
public class QuestionPageVo {
    @Schema(description = "题目分类ID",
            example = "1")
    private Long categoryId;
    @Schema(description = "题目标注知识点ID")
    private Long knowledgePointId;
    @Schema(description = "题目难度等级",
            example = "MEDIUM",
            allowableValues = {"EASY", "MEDIUM", "HARD"})
    private String difficulty;
    @Schema(description = "题目类型",
            example = "CHOICE",
            allowableValues = {"CHOICE", "JUDGE", "TEXT"})
    private String type;
    @Schema(description = "题目关键词",
            example = "Java")
    private String keyword;
}
