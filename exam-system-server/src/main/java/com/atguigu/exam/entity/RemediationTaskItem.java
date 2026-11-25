package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 补课任务步骤作答明细实体类（M5）
 *
 * 基础练习 PRACTICE / 变式题 VARIATION / 复测 RETEST 共用的追加式明细表，
 * 物理删除口径（不继承 BaseEntity）：作答历史只增不改删。
 * 唯一索引 uk_item_task_step_q_round(task_id, step, question_id, round_no)
 * 兜底并发双击不产生双记录；重做 = round_no+1 新行。
 * 视频步骤完成复用 M2 学习进度判定，不入此表。
 */
@Data
@TableName("remediation_task_items")
@Schema(description = "补课步骤作答明细")
public class RemediationTaskItem {

    @Schema(description = "明细ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "任务ID，关联 remediation_tasks.id", example = "1")
    private Long taskId;

    @Schema(description = "步骤类型", allowableValues = {"PRACTICE", "VARIATION", "RETEST"})
    private String step;

    @Schema(description = "重做轮次，从 1 开始", example = "1")
    private Integer roundNo;

    @Schema(description = "题目ID，关联 questions.id", example = "5")
    private Long questionId;

    @Schema(description = "学员作答")
    private String userAnswer;

    @Schema(description = "是否正确：1/0，未判分为 null", example = "1")
    private Byte isCorrect;

    @Schema(description = "得分（复测按题目分值，练习按对错）")
    private BigDecimal score;

    @Schema(description = "AI 判分反馈")
    private String aiFeedback;

    @Schema(description = "提交时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
