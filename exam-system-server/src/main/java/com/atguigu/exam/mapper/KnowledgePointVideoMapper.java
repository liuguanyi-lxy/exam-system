package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.KnowledgePointVideo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 知识点-视频映射 Mapper（M5）
 * 映射维护与报告/任务的视频推荐查询共用
 */
public interface KnowledgePointVideoMapper extends BaseMapper<KnowledgePointVideo> {

    /**
     * 整体替换前物理删除该知识点的全部映射。
     * 故意不用 MP 的 delete(wrapper)（会被 @TableLogic 改写为 UPDATE is_deleted=1）：
     * 表有唯一键 uk_kp_video(knowledge_point_id, video_id)，逻辑删除行仍占唯一键，
     * 删后再加同对映射必撞 DuplicateKey，故整体替换必须物理删除后重插。
     */
    @Delete("DELETE FROM knowledge_point_videos WHERE knowledge_point_id = #{pointId}")
    int deleteByKnowledgePointId(@Param("pointId") long pointId);
}
