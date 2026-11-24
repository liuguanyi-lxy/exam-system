package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 班级成员成员 Vo —— 成员分页列表元素（契约 5.1）
 * examCount/lastExamTime 仅 includeSummary=true 时填充（当页批量聚合 exam_records，禁止循环 N+1）
 */
@Data
@Schema(description = "班级成员信息")
public class ClassMemberVo {

    @Schema(description = "用户ID（移除/重置密码/禁用操作的定位键）", example = "21")
    private Long userId;

    @Schema(description = "登录名", example = "student1")
    private String username;

    @Schema(description = "姓名", example = "张三")
    private String realName;

    @Schema(description = "全局角色（users.role）", example = "STUDENT")
    private String role;

    @Schema(description = "账号状态 ACTIVE/INACTIVE", example = "ACTIVE")
    private String accountStatus;

    @Schema(description = "班内角色 STUDENT/TEACHER", example = "STUDENT")
    private String roleInClass;

    @Schema(description = "入班时间")
    private java.util.Date joinedAt;

    @Schema(description = "仅 includeSummary=true 下发：历史考试记录总数（本人全量口径）", example = "2")
    private Integer examCount;

    @Schema(description = "仅 includeSummary=true 下发：最近一次考试时间，无记录为 null")
    private String lastExamTime;
}
