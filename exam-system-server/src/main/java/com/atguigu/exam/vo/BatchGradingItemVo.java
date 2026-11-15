package com.atguigu.exam.vo;

import com.atguigu.exam.entity.Question;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批量AI判卷单项 - 一次批量判卷请求中的一道主观题
 * 仅服务内部使用：把整张试卷的简答/代码题打包成一次AI调用，规避AI账号RPM限流
 */
@Data
@Schema(description = "批量AI判卷单项")
public class BatchGradingItemVo {

    @Schema(description = "题目ID，用于关联判卷结果", example = "12")
    private Long questionId;

    @Schema(description = "题目信息，用于生成判卷提示词")
    private Question question;

    @Schema(description = "学生答案")
    private String userAnswer;

    @Schema(description = "该题满分")
    private Integer maxScore;
}
