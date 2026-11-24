package com.atguigu.exam.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourseStatusVo {
    @NotBlank(message = "参数错误")
    private String status;
}
