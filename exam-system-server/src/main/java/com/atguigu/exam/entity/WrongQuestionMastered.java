package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 错题已掌握标记实体类
 *
 * 物理删除（不继承 BaseEntity）：取消掌握直接删行。
 * 错题本查询通过 LEFT JOIN 本表判断题目是否已被移出：
 * mastered_time >= 最近一次答错时间 视为已掌握，否则（标记后又答错）自动回到错题本。
 */
@Data
@TableName("wrong_question_mastered")
@Schema(description = "错题已掌握标记")
public class WrongQuestionMastered {

    @Schema(description = "标记记录ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "学生用户ID，关联 users.id", example = "1")
    private Long userId;

    @Schema(description = "题目ID，关联 questions.id", example = "5")
    private Long questionId;

    @Schema(description = "标记掌握时间，重复标记时更新为最新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime masteredTime;

    @Schema(description = "首次标记时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
