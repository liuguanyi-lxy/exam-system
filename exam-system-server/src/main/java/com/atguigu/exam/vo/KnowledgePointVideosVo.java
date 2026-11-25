package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 知识点视频映射请求（M5 第 5 步，契约口径同题目标注 QuestionKnowledgePointVo）
 * 整体替换：videoIds 去重保序；空数组/缺省 = 清空映射
 */
@Data
@Schema(description = "知识点视频映射请求（整体替换）")
public class KnowledgePointVideosVo {

    @Schema(description = "视频ID集合（去重保序；空/缺省=清空映射）", example = "[3, 7]")
    private List<Long> videoIds;
}
