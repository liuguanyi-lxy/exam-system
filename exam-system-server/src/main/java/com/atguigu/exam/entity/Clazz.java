package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.atguigu.exam.vo.ClassTeacherVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 班级实体类
 *
 * 命名说明：Class 是 Java 保留字，实体命名 Clazz，@TableName 映射 classes 表。
 * 管理员管理域主数据，走 BaseEntity 逻辑删除（与 questions/exam_records 同模式）。
 * name 不做数据库唯一（跨期复用班名是真实需求），重名校验由应用层在创建/更新时执行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("classes")
@Schema(description = "班级信息")
public class Clazz extends BaseEntity {

    @Schema(description = "班级名称", example = "Java 培训一班")
    private String name;

    @Schema(description = "培训方向，如：Java 后端 / Web 前端", example = "Java 后端")
    private String direction;

    @Schema(description = "开班日期，yyyy-MM-dd，null 表示未定", example = "2026-12-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @Schema(description = "结班日期，yyyy-MM-dd，null 表示未定；须不早于开班日期", example = "2027-03-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Schema(description = "班级状态", example = "ENABLED", allowableValues = {"ENABLED", "DISABLED"})
    private String status;

    // ========== 非数据库字段（列表/详情组装） ==========

    @Schema(description = "学员数（role_in_class=STUDENT），列表/详情批量聚合填充")
    @TableField(exist = false)
    private Integer studentCount;

    @Schema(description = "教师数，列表/详情批量聚合填充")
    @TableField(exist = false)
    private Integer teacherCount;

    @Schema(description = "班级教师列表（仅详情接口下发）")
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ClassTeacherVo> teachers;
}
