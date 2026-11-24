package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.LearningVideoSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LearningVideoSessionMapper extends BaseMapper<LearningVideoSession> {
    @Select("""
            SELECT * FROM learning_video_sessions
            WHERE progress_id = #{progressId} AND session_id = #{sessionId}
            """)
    LearningVideoSession findSession(@Param("progressId") Long progressId, @Param("sessionId") String sessionId);
}
