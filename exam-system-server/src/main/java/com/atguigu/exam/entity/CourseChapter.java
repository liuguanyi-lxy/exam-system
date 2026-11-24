package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_chapters")
public class CourseChapter extends BaseEntity {
    private Long courseId;
    private String title;
    private String description;
    private Integer sort;
}
