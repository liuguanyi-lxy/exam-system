package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class LearningVideoVo {
    private Long chapterVideoId;
    private Long videoId;
    private Integer sort;
    private String title;
    private Integer sourceType;
    private String fileUrl;
    private String coverUrl;
    private Integer duration;
    private String durationText;
    private Boolean required;
    private Integer completionRatio;
    private Integer requiredSeconds;
    private Integer watchedSeconds;
    private Boolean completed;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date completedAt;
}
