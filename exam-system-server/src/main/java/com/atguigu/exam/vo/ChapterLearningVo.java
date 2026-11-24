package com.atguigu.exam.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChapterLearningVo {
    private Long chapterId;
    private Long courseId;
    private String title;
    private Integer sort;
    private Boolean completed;
    private List<LearningVideoVo> videos;
}
