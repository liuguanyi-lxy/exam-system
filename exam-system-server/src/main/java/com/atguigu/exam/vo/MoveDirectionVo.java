package com.atguigu.exam.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MoveDirectionVo {
    @NotBlank(message = "参数错误")
    private String direction;
}
