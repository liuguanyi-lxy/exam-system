package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 题目收藏实体类
 *
 * 行为表，物理删除（不继承 BaseEntity）：
 * 取消收藏直接删行，避免 is_deleted 与 (user_id, question_id) 唯一索引互相占用，
 * 模式与 video_likes 保持一致。
 */
@Data
@TableName("question_favorites")
@Schema(description = "题目收藏记录")
public class QuestionFavorite {

    @Schema(description = "收藏记录ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "收藏人用户ID，关联 users.id", example = "1")
    private Long userId;

    @Schema(description = "题目ID，关联 questions.id", example = "5")
    private Long questionId;

    @Schema(description = "收藏时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
