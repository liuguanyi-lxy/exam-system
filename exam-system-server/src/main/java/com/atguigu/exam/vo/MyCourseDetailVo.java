package com.atguigu.exam.vo;

import lombok.Data;

import java.util.List;

@Data
public class MyCourseDetailVo {
    private Long courseId;
    private String name;
    private String description;
    private String coverUrl;
    private String teacherName;
    private Integer chapterTotal;
    private Integer chapterCompleted;
    private Integer progressPercent;
    private List<LearningChapterVo> chapters;
}
