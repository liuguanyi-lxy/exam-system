package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class MyCourseItemVo {
    private Long courseId;
    private String name;
    private String description;
    private String coverUrl;
    private String teacherName;
    private Integer chapterTotal;
    private Integer chapterCompleted;
    private Integer progressPercent;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date assignedAt;
}
