package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.LearningVideoProgress;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface LearningVideoProgressMapper extends BaseMapper<LearningVideoProgress> {
    @Insert("""
            INSERT INTO learning_video_progress (user_id, video_id, chapter_video_id)
            VALUES (#{userId}, #{videoId}, #{chapterVideoId})
            ON DUPLICATE KEY UPDATE id = id
            """)
    void ensureProgress(@Param("userId") Long userId, @Param("videoId") Long videoId,
                        @Param("chapterVideoId") Long chapterVideoId);

    @Select("""
            SELECT * FROM learning_video_progress WHERE user_id = #{userId} AND video_id = #{videoId} FOR UPDATE
            """)
    LearningVideoProgress lockProgress(@Param("userId") Long userId, @Param("videoId") Long videoId);
}
