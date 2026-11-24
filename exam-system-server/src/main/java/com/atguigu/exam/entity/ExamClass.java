package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** A paper assignment to one class. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_classes")
@Schema(description = "班级考试分配")
public class ExamClass extends BaseEntity {

    @Schema(description = "试卷ID")
    private Long examId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "考试窗口开始时间，null 表示不限制")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @Schema(description = "考试窗口结束时间，null 表示不限制")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    @Schema(description = "总考试次数上限，包含首次")
    private Integer maxAttempts;

    @Schema(description = "分配状态", allowableValues = {"ENABLED", "DISABLED"})
    private String status;
}
