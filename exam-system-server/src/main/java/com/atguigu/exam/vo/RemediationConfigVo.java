package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 课程级补课策略配置请求（M5 第 5 步）
 * 整体替换语义：全部字段必填校验，非法值 IllegalArgumentException 带字段名
 */
@Data
@Schema(description = "课程级补课策略配置请求（整体替换，全部必填）")
public class RemediationConfigVo {

    @Schema(description = "最低掌握度阈值 0-100，复测达到即完成任务", example = "60")
    private Integer minMastery;

    @Schema(description = "基础练习补课题数 1-50", example = "5")
    private Integer practiceCount;

    @Schema(description = "复测题数 1-50", example = "5")
    private Integer retestCount;

    @Schema(description = "是否强制复测：true=复测达标才算完成", example = "true")
    private Boolean forceRetest;

    @Schema(description = "是否允许跳视频：true=可直接做练习/复测", example = "false")
    private Boolean allowSkipVideo;

    @Schema(description = "默认截止天数 1-365", example = "14")
    private Integer dueDays;
}
