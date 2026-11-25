package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识点掌握度实体类（M5）
 *
 * 每学员每知识点一条（唯一索引 uk_mastery_user_kp），逻辑删除（继承 BaseEntity）。
 * 折算口径：total = videoDone*20 + practiceAccuracy*0.3 + retestScore*0.5；
 * 未复测时 total = videoDone*20/50 + practiceAccuracy*30/50（pending_retest=1）。
 * 复测完成以最近一次复测得分为主口径，不做时间衰减；
 * 更新走原子 UPDATE，避免读改写并发丢失。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mastery_scores")
@Schema(description = "知识点掌握度")
public class MasteryScore extends BaseEntity {

    @Schema(description = "掌握度记录ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "学员用户ID，关联 users.id", example = "1")
    private Long userId;

    @Schema(description = "知识点ID，关联 knowledge_points.id", example = "1")
    private Long knowledgePointId;

    @Schema(description = "视频步骤是否完成：1/0", example = "0")
    private Byte videoDone;

    @Schema(description = "最近一次练习正确率（0-100），未练习为 null")
    private BigDecimal practiceAccuracy;

    @Schema(description = "最近一次复测得分（0-100），未复测为 null")
    private BigDecimal retestScore;

    @Schema(description = "待复测标记：1=临时掌握度（视频+练习折算），0=已复测", example = "1")
    private Byte pendingRetest;

    @Schema(description = "折算掌握度（0-100）")
    private BigDecimal total;

    @Schema(description = "最近一次复测时间，未复测为 null")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastRetestTime;
}
