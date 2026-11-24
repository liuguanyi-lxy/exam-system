package com.atguigu.exam.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChapterVideoUpdateVo {
    @NotNull(message = "必学标记不能为空")
    private Boolean required;
    @NotNull(message = "完成比例不能为空")
    @Min(value = 1, message = "完成比例必须在1到100之间")
    @Max(value = 100, message = "完成比例必须在1到100之间")
    private Integer completionRatio;
}
