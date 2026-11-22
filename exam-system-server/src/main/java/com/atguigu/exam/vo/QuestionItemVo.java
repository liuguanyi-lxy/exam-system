package com.atguigu.exam.vo;

import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 题目信息 VO（错题本 / 收藏列表共用）
 *
 * 与 Question 实体的区别：
 * 1. 只保留列表展示与复习需要的字段，不包含 paperScore、category 完整对象；
 * 2. categoryName 由聚合/关联查询直接得出，省掉前端再查分类接口；
 * 3. 错题本完整下发 analysis / choices.isCorrect / answer（复习需要）；
 *    收藏列表复用同一对象，由 service 按 QuestionSanitizer 口径置空答案与解析。
 */
@Data
@Schema(description = "题目信息（列表展示用）")
public class QuestionItemVo implements Serializable {

    @Schema(description = "题目ID", example = "5")
    private Long id;

    @Schema(description = "题干", example = "以下关于 Java 面向对象编程的说法正确的是？")
    private String title;

    @Schema(description = "题型", example = "CHOICE", allowableValues = {"CHOICE", "JUDGE", "TEXT", "CODE"})
    private String type;

    @Schema(description = "是否多选题，仅选择题有效", example = "false")
    private Boolean multi;

    @Schema(description = "难度", example = "MEDIUM", allowableValues = {"EASY", "MEDIUM", "HARD"})
    private String difficulty;

    @Schema(description = "分类ID", example = "1")
    private Long categoryId;

    @Schema(description = "分类名称", example = "Java 基础")
    private String categoryName;

    @Schema(description = "题目解析（错题本下发，收藏列表不下发）",
            example = "Java 支持封装、继承、多态三大特性……")
    private String analysis;

    @Schema(description = "选项列表；错题本下发 isCorrect，收藏列表置空")
    private List<QuestionChoice> choices;

    @Schema(description = "标准答案与评分关键词；错题本下发，收藏列表不下发")
    private QuestionAnswer answer;

    private static final long serialVersionUID = 1L;
}
