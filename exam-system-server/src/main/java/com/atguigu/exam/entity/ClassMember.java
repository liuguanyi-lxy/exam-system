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
 * 班级成员实体类
 *
 * 行为表，物理删除（不继承 BaseEntity）：移除成员后重新入班是正常操作，
 * 逻辑删除旧行会占死唯一键 uk_class_user_role(class_id, user_id, role_in_class)，
 * 模式与 question_favorites / video_likes 保持一致。
 * 一人可入多班；一班可多师；教师与学员均落本表，靠 role_in_class 区分。
 */
@Data
@TableName("class_members")
@Schema(description = "班级成员记录")
public class ClassMember {

    @Schema(description = "成员记录ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "班级ID，关联 classes.id", example = "1")
    private Long classId;

    @Schema(description = "成员用户ID，关联 users.id", example = "21")
    private Long userId;

    @Schema(description = "班内角色", example = "STUDENT", allowableValues = {"STUDENT", "TEACHER"})
    private String roleInClass;

    @Schema(description = "入班时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
