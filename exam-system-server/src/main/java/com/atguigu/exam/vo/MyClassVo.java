package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 学员"我的班级"条目 Vo
 * 返回学员所属的全部班级（含已停用，由 status 区分——验收标准 4），全量不分页
 */
@Data
@Schema(description = "我的班级条目")
public class MyClassVo {

    @Schema(description = "班级ID（进同学列表用）", example = "1")
    private Long classId;

    @Schema(description = "班级名称", example = "Java 培训一班")
    private String name;

    @Schema(description = "培训方向", example = "Java 后端")
    private String direction;

    @Schema(description = "开班日期 yyyy-MM-dd", example = "2026-12-01")
    private String startDate;

    @Schema(description = "结班日期 yyyy-MM-dd", example = "2027-03-01")
    private String endDate;

    @Schema(description = "班级状态 ENABLED/DISABLED（禁用时学员班级页显示已停用）", example = "ENABLED")
    private String status;

    @Schema(description = "本人入班时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date joinedAt;

    @Schema(description = "班级学员数", example = "3")
    private Integer studentCount;

    @Schema(description = "班级教师列表（不下发 username，学员端最小暴露）")
    private List<MyClassTeacherVo> teachers;
}
