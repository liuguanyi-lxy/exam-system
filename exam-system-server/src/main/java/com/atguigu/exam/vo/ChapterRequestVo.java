package com.atguigu.exam.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChapterRequestVo {
    @NotBlank(message = "章节名称不能为空")
    @Size(max = 100, message = "章节名称不能超过100字")
    private String title;
    @Size(max = 500, message = "章节描述不能超过500字")
    private String description;
}
