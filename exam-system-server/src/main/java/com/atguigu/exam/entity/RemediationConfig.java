package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程级补课策略实体类（M5）
 *
 * 每课程一条配置（唯一索引 uk_course_config(course_id)），逻辑删除（继承 BaseEntity）。
 * 教师配置；任务生成时快照到 remediation_tasks.config_snapshot，
 * 生成后修改本表不影响已生成任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("remediation_configs")
@Schema(description = "课程级补课策略")
public class RemediationConfig extends BaseEntity {

    @Schema(description = "配置ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "课程ID，关联 courses.id", example = "1")
    private Long courseId;

    @Schema(description = "最低掌握度阈值（0-100），复测达到即完成任务", example = "60")
    private Integer minMastery;

    @Schema(description = "基础练习补课题数", example = "5")
    private Integer practiceCount;

    @Schema(description = "复测题数", example = "5")
    private Integer retestCount;

    @Schema(description = "是否强制复测：1=复测达标才算完成，0=视频+练习完成即完成", example = "1")
    private Byte forceRetest;

    @Schema(description = "是否允许跳视频：1=可直接做练习/复测，0=未完成视频不能进入练习", example = "0")
    private Byte allowSkipVideo;

    @Schema(description = "默认截止天数（仅提示与筛选，不强制失效）", example = "14")
    private Integer dueDays;
}
