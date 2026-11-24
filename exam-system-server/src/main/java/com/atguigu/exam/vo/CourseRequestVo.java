package com.atguigu.exam.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourseRequestVo {
    @NotBlank(message = "课程名称不能为空")
    @Size(max = 100, message = "课程名称不能超过100字")
    private String name;
    @Size(max = 500, message = "课程描述不能超过500字")
    private String description;
    @Size(max = 500, message = "封面地址不能超过500字")
    private String coverUrl;
}
