package com.atguigu.exam.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DiagnosticReport(Integer score, BigDecimal maxScore, Integer objectiveScore, Integer subjectiveScore,
                               String summary, String confidence, List<QuestionResult> questions,
                               List<PointResult> knowledgePoints, List<PointResult> weakPoints,
                               List<Suggestion> recommendations, String retestAdvice, String version) {
    public record QuestionResult(long questionId, String title, String type, String referenceAnswer, String userAnswer,
                                 Integer score, BigDecimal maxScore, String verdict, String reason, boolean reviewNeeded) {}
    public record PointResult(long id, String name, int earned, BigDecimal possible, BigDecimal rate) {}
    /**
     * M5 升级后的复习建议条目：text 保留原文字建议；videos 为该知识点已发布的推荐视频
     * （无映射或映射视频均未发布时为 null，回退纯文字，旧报告语义不变）。
     * 兼容旧存量报告 JSON：recommendations 历史上是纯字符串数组，经 delegating creator
     * 反序列化为 videos=null 的条目，旧数据读取不失败。
     */
    public record Suggestion(String text, List<VideoItem> videos) {
        @JsonCreator(mode = Mode.DELEGATING)
        public static Suggestion fromLegacyText(String text) {
            return new Suggestion(text, null);
        }
    }
    /** 推荐视频条目：videoId + 标题 + 播放入口 fileUrl；coverUrl 可空 */
    public record VideoItem(Long videoId, String title, String fileUrl, String coverUrl) {}
    public record View(String status, DiagnosticReport report, Long reviewedBy, String reviewNote,
                       LocalDateTime reviewedAt) {}
    public record Evidence(long questionId, String title, String type, BigDecimal possible, String referenceAnswer,
                           String userAnswer, Integer score, Integer correct, String feedback, Long answerId,
                           boolean sourceChanged) {}
    public record Tag(long questionId, long pointId, String name) {}
}
