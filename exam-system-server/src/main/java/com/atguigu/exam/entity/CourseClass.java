package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 课程-班级分配实体类
 *
 * M1 仅建实体与 Mapper，无任何业务读写代码（契约第 3 节 course_classes 无接口），
 * 防止 M2 接线时重复造轮子；course_id 关联的 courses 表 M2 才创建。
 * 物理删除：分配/取消分配可反复，唯一索引 uk_course_class(course_id, class_id) 幂等兜底，
 * 模式与 class_members 保持一致。
 */
@Data
@TableName("course_classes")
@Schema(description = "课程-班级分配记录（M1 仅建表实体，M2 接线）")
public class CourseClass {

    @Schema(description = "分配记录ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "课程ID，关联 courses.id（courses 表 M2 建）", example = "1")
    private Long courseId;

    @Schema(description = "班级ID，关联 classes.id", example = "1")
    private Long classId;

    @Schema(description = "分配时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
