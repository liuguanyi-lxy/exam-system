package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 班级启用/禁用请求 Vo（契约 4.4）
 */
@Data
@Schema(description = "班级状态更新请求")
public class ClassStatusVo {

    @Schema(description = "目标状态", example = "DISABLED", allowableValues = {"ENABLED", "DISABLED"})
    @NotBlank(message = "status 不能为空")
    private String status;
}
