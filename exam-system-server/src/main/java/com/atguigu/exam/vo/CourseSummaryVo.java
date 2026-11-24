package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class CourseSummaryVo {
    private Long id;
    private String name;
    private String description;
    private String coverUrl;
    private String status;
    private Long createdBy;
    private String creatorName;
    private long chapterCount;
    private long classCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
