package com.atguigu.exam.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearningHeartbeatVo {
    @NotBlank(message = "参数错误")
    @Size(max = 36, message = "参数错误")
    private String sessionId;
    @NotNull(message = "心跳序号不能为空")
    @PositiveOrZero(message = "心跳序号无效")
    private Long sequence;
    @NotNull(message = "观看时长不能为空")
    @PositiveOrZero(message = "观看时长无效")
    private Integer viewDuration;
}
