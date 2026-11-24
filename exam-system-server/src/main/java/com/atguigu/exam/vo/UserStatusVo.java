package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 账号禁用/启用请求 Vo（契约 7.2）
 */
@Data
@Schema(description = "账号状态更新请求")
public class UserStatusVo {

    @Schema(description = "true 启用（ACTIVE），false 禁用（INACTIVE）", example = "false")
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
