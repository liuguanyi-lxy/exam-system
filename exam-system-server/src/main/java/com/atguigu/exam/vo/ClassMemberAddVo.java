package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 手动添加学员请求 Vo（契约 5.2）
 * 只支持已存在用户的 userId，自动建号只在 Excel 导入里做
 */
@Data
@Schema(description = "手动添加学员请求")
public class ClassMemberAddVo {

    @Schema(description = "已存在用户的ID", example = "21")
    @NotNull(message = "userId 不能为空")
    private Long userId;
}
