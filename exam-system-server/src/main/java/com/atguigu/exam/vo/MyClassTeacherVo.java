package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 班级教师条目 Vo —— 学员端"我的班级"使用（最小暴露口径，不下发 username）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "班级教师信息（学员端，不含登录名）")
public class MyClassTeacherVo {

    @Schema(description = "教师用户ID", example = "10")
    private Long userId;

    @Schema(description = "姓名", example = "王老师")
    private String realName;
}
