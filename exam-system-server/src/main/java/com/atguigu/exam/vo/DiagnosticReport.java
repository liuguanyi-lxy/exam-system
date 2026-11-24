package com.atguigu.exam.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DiagnosticReport(Integer score, BigDecimal maxScore, Integer objectiveScore, Integer subjectiveScore,
                               String summary, String confidence, List<QuestionResult> questions,
                               List<PointResult> knowledgePoints, List<PointResult> weakPoints,
                               List<String> recommendations, String retestAdvice, String version) {
    public record QuestionResult(long questionId, String title, String type, String referenceAnswer, String userAnswer,
                                 Integer score, BigDecimal maxScore, String verdict, String reason, boolean reviewNeeded) {}
    public record PointResult(long id, String name, int earned, BigDecimal possible, BigDecimal rate) {}
    public record View(String status, DiagnosticReport report, Long reviewedBy, String reviewNote,
                       LocalDateTime reviewedAt) {}
    public record Evidence(long questionId, String title, String type, BigDecimal possible, String referenceAnswer,
                           String userAnswer, Integer score, Integer correct, String feedback, Long answerId,
                           boolean sourceChanged) {}
    public record Tag(long questionId, long pointId, String name) {}
}
