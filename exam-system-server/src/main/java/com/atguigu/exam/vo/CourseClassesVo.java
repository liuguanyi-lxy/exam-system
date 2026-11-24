package com.atguigu.exam.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CourseClassesVo {
    @NotNull(message = "班级列表不能为空")
    private List<Long> classIds;
}
