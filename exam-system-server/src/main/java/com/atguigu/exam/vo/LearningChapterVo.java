package com.atguigu.exam.vo;

import lombok.Data;

@Data
public class LearningChapterVo {
    private Long chapterId;
    private String title;
    private Integer sort;
    private Integer videoCount;
    private Integer requiredVideoCount;
    private Integer completedVideoCount;
    private Boolean completed;
}
