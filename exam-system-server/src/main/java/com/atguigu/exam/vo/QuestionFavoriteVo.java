package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收藏列表分页记录 VO
 *
 * question 字段按 API 契约做脱敏：不下发正确答案、解析与选项对错，
 * 避免学生把题库全量收藏后绕过题目接口的角色脱敏。
 */
@Data
@Schema(description = "题目收藏记录")
public class QuestionFavoriteVo implements Serializable {

    @Schema(description = "题目ID", example = "8")
    private Long questionId;

    @Schema(description = "收藏时间", example = "2026-11-12 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime favoriteTime;

    @Schema(description = "题目详情（脱敏版：不含答案、解析与选项对错）")
    private QuestionItemVo question;

    private static final long serialVersionUID = 1L;
}
