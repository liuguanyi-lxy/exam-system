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
 * 题目-知识点标注实体类
 *
 * 多对多行为表，物理删除（不继承 BaseEntity）：标注可反复增删，
 * 唯一索引 uk_qkp_question_kp(question_id, knowledge_point_id) 幂等兜底。
 * created_by 记录标注人（M4 诊断结论依赖标注质量，出问题能定位到人），
 * 可空——系统批量补标注/无登录上下文场景不填。
 * 删除题目时本表行残留（无外键），JOIN 过滤自然失效，口径同错题本对已删题。
 */
@Data
@TableName("question_knowledge_points")
@Schema(description = "题目-知识点标注记录")
public class QuestionKnowledgePoint {

    @Schema(description = "标注记录ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "题目ID，关联 questions.id", example = "5")
    private Long questionId;

    @Schema(description = "知识点ID，关联 knowledge_points.id", example = "1")
    private Long knowledgePointId;

    @Schema(description = "标注人 users.id（教师/管理员），系统补标注时为空", example = "10")
    private Long createdBy;

    @Schema(description = "标注时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
