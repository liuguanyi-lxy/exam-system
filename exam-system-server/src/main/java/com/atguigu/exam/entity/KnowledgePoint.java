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
 * 知识点实体类
 *
 * 平铺全局字典（不做树形），name 数据库唯一（uk_kp_name），
 * 重名会破坏 M4 按知识点聚合的统计口径。
 * 物理删除（不继承 BaseEntity）：删除前由后端校验引用（存在题目标注则拒绝），
 * 逻辑删除旧行会占死唯一键导致"删后同名重建"必撞 DuplicateKey。
 */
@Data
@TableName("knowledge_points")
@Schema(description = "知识点信息")
public class KnowledgePoint {

    @Schema(description = "知识点ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "知识点名称，全局唯一", example = "面向对象")
    private String name;

    @Schema(description = "知识点说明", example = "封装/继承/多态")
    private String description;

    @Schema(description = "排序值，越小越靠前", example = "1")
    private Integer sort;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    // ========== 非数据库字段（管理页分页列表批量聚合填充） ==========

    @Schema(description = "引用该知识点的题目数，分页列表填充")
    @TableField(exist = false)
    private Integer questionCount;
}
