package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 补课任务链实体类（M5）
 *
 * 每学员每薄弱知识点每来源报告一条任务链（唯一索引 uk_task_user_kp_record），
 * 报告 retry 幂等兜底；逻辑删除（继承 BaseEntity）。
 * config_snapshot 为生成时策略快照（JSON 字符串），后续改配置不影响已生成任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("remediation_tasks")
@Schema(description = "补课任务链")
public class RemediationTask extends BaseEntity {

    @Schema(description = "任务ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "来源报告 exam_record_id", example = "12")
    private Long examRecordId;

    @Schema(description = "学员用户ID，关联 users.id", example = "1")
    private Long userId;

    @Schema(description = "开考班级快照ID（教师数据范围用），可空", example = "3")
    private Long classId;

    @Schema(description = "薄弱知识点ID，关联 knowledge_points.id", example = "1")
    private Long knowledgePointId;

    @Schema(description = "任务状态", allowableValues = {"IN_PROGRESS", "COMPLETED"})
    private String status;

    @Schema(description = "生成时策略快照（JSON 字符串）")
    private String configSnapshot;

    @Schema(description = "截止时间（仅提示与筛选，null 表示不限制）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dueTime;
}
