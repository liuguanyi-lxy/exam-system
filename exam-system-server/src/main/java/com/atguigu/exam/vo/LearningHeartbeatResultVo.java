package com.atguigu.exam.vo;

import lombok.Data;

@Data
public class LearningHeartbeatResultVo {
    private Long chapterVideoId;
    private Long videoId;
    private Integer sourceType;
    private Boolean required;
    private Integer completionRatio;
    private Integer watchedSeconds;
    private Integer sessionWatchedSeconds;
    private Integer requiredSeconds;
    private Boolean completed;
    private Boolean chapterCompleted;
    private Boolean chapterCompletedAfterThisVideo;
}
