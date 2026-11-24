package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("chapter_videos")
public class ChapterVideo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Long videoId;
    private Boolean required;
    private Integer completionRatio;
    private Integer sort;
    private Date createTime;
}
