package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识点-视频映射实体类（M5）
 *
 * 教师维护的多对多映射表，逻辑删除（继承 BaseEntity）：
 * 同一知识点可挂多个视频，唯一索引 uk_kp_video(knowledge_point_id, video_id) 幂等兜底。
 * 报告资源推荐与补课任务"推荐视频"步骤共用本表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_point_videos")
@Schema(description = "知识点-视频映射记录")
public class KnowledgePointVideo extends BaseEntity {

    @Schema(description = "映射记录ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "知识点ID，关联 knowledge_points.id", example = "1")
    private Long knowledgePointId;

    @Schema(description = "视频ID，关联 videos.id", example = "3")
    private Long videoId;

    @Schema(description = "推荐顺序，小在前", example = "0")
    private Integer sort;

    @Schema(description = "维护人 users.id（教师/管理员），系统场景可空", example = "10")
    private Long createdBy;
}
