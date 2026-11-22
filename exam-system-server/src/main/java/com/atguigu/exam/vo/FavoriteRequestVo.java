package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 收藏题目请求体
 */
@Data
@Schema(description = "收藏题目请求参数")
public class FavoriteRequestVo implements Serializable {

    @Schema(description = "要收藏的题目ID", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long questionId;

    private static final long serialVersionUID = 1L;
}
