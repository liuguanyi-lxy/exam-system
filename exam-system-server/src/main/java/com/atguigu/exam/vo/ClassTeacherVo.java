package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 班级教师条目 Vo —— 管理端班级详情使用（含 username）
 * 学员端"我的班级"不暴露登录名，另用 MyClassTeacherVo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "班级教师信息（管理端）")
public class ClassTeacherVo {

    @Schema(description = "教师用户ID", example = "10")
    private Long userId;

    @Schema(description = "登录名", example = "teacher1")
    private String username;

    @Schema(description = "姓名", example = "王老师")
    private String realName;
}
