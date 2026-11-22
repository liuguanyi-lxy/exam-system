package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 错题本分页记录 VO
 *
 * 统计字段来自 answer_record 按题聚合（仅 is_correct=0），
 * 题目详情由 service 按本页 questionId 批量补齐选项与标准答案。
 */
@Data
@Schema(description = "错题本记录")
public class WrongQuestionVo implements Serializable {

    @Schema(description = "题目ID", example = "5")
    private Long questionId;

    @Schema(description = "是否已被标记为已掌握", example = "false")
    private Boolean mastered;

    @Schema(description = "累计答错次数（仅统计 is_correct=0）", example = "3")
    private Integer wrongCount;

    @Schema(description = "最近一次答错时提交的答案", example = "A")
    private String lastWrongAnswer;

    @Schema(description = "最近一次答错所属考试的考试时间（缺失时回退答题记录创建时间）",
            example = "2026-11-10 15:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastWrongTime;

    @Schema(description = "最近一次答错所属考试记录ID", example = "102")
    private Long lastExamRecordId;

    @Schema(description = "最近一次答错所属试卷ID", example = "3")
    private Long lastPaperId;

    @Schema(description = "最近一次答错所属试卷名称", example = "Java 基础期中卷")
    private String lastPaperName;

    @Schema(description = "最近一次答错的 AI 批改评语（客观题为 null）",
            example = "答案基本正确，但缺少关键概念的解释……")
    private String aiCorrection;

    @Schema(description = "题目详情（含解析、选项对错与标准答案）")
    private QuestionItemVo question;

    private static final long serialVersionUID = 1L;
}
