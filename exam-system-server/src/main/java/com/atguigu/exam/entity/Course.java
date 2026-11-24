package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("courses")
public class Course extends BaseEntity {
    private String name;
    private String description;
    private String coverUrl;
    private String status;
    private Long createdBy;
}
