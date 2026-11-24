package com.atguigu.exam.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChapterVideoRequestVo {
    @NotBlank(message = "参数错误")
    private String source;
    private Long videoId;
    @Size(max = 100, message = "视频标题不能超过100字")
    private String title;
    @Size(max = 500, message = "外链地址不能超过500字")
    private String externalUrl;
    private Boolean required;
    @Min(value = 1, message = "完成比例必须在1到100之间")
    @Max(value = 100, message = "完成比例必须在1到100之间")
    private Integer completionRatio;
}
