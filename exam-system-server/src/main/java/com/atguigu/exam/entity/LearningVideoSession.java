package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("learning_video_sessions")
public class LearningVideoSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long progressId;
    private Long chapterVideoId;
    private String sessionId;
    private Long lastSequence;
    private Integer watchedSeconds;
    private Date lastSignalAt;
}
