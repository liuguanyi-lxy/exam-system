package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 分配教师请求 Vo（契约 6.1，可批量，一班可多师）
 */
@Data
@Schema(description = "分配教师请求")
public class TeacherAssignVo {

    @Schema(description = "教师用户ID列表（非空且去重）", example = "[10, 11]")
    @NotEmpty(message = "userIds 不能为空")
    private List<Long> userIds;
}
