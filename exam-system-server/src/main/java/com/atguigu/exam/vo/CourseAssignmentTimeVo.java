package com.atguigu.exam.vo;

import lombok.Data;

import java.util.Date;

@Data
public class CourseAssignmentTimeVo {
    private Long courseId;
    private Date assignedAt;
}
