package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.DiagnosticReport;
import com.atguigu.exam.vo.DiagnosticReport.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DiagnosticReportBuilder {
    private static final BigDecimal WEAK_THRESHOLD = BigDecimal.valueOf(60);
    private static final String VERSION = "recorded-grades-v1";

    public DiagnosticReport build(ExamRecord record, List<Evidence> evidence, List<Tag> tags) {
        Map<Long, List<Tag>> byQuestion = tags.stream().collect(Collectors.groupingBy(Tag::questionId));
        Map<Long, List<Evidence>> grouped = evidence.stream().collect(Collectors.groupingBy(Evidence::questionId,
                LinkedHashMap::new, Collectors.toList()));
        List<QuestionResult> questions = new ArrayList<>();
        Map<Long, PointTotal> totals = new HashMap<>();
        int objective = 0;
        int subjective = 0;
        boolean objectiveComplete = true;
        boolean subjectiveComplete = true;
        BigDecimal possible = BigDecimal.ZERO;
        for (List<Evidence> rows : grouped.values()) {
            Evidence row = rows.get(0);
            QuestionResult result = question(row, rows.size() > 1);
            questions.add(result);
            if (row.possible() != null) possible = possible.add(row.possible());
            if ("CHOICE".equals(row.type()) || "JUDGE".equals(row.type())) {
                if (result.score() == null) objectiveComplete = false;
                else objective += result.score();
            } else {
                if (result.score() == null) subjectiveComplete = false;
                else subjective += result.score();
            }
            if (result.reviewNeeded() || row.possible() == null) continue;
            for (Tag tag : byQuestion.getOrDefault(row.questionId(), List.of())) {
                totals.computeIfAbsent(tag.pointId(), ignored -> new PointTotal(tag)).add(result.score(), row.possible());
            }
        }
        List<PointResult> points = totals.values().stream().map(PointTotal::result).filter(point -> point.rate() != null)
                .sorted(Comparator.comparing(PointResult::rate).thenComparing(PointResult::id)).toList();
        List<PointResult> weak = points.stream().filter(point -> point.rate().compareTo(WEAK_THRESHOLD) < 0).toList();
        boolean incomplete = questions.isEmpty() || record.getScore() == null || record.getScore() != objective + subjective
                || questions.stream().anyMatch(QuestionResult::reviewNeeded) || points.isEmpty()
                || record.getSummary() == null || record.getSummary().contains("AI 评语生成失败");
        var suggestions = weak.stream().map(point -> "复习“" + point.name() + "”对应视频，再完成该知识点基础练习").toList();
        String confidence = incomplete ? "LIMITED" : "NORMAL";
        if (incomplete) {
            // 脱敏口径：置信度受限时不向学生下发参考答案与学员作答
            questions = questions.stream().map(result -> new QuestionResult(result.questionId(), result.title(),
                    result.type(), null, null, result.score(), result.maxScore(), result.verdict(), result.reason(),
                    result.reviewNeeded())).toList();
        }
        return new DiagnosticReport(record.getScore(), possible, objectiveComplete ? objective : null,
                subjectiveComplete ? subjective : null, record.getSummary(), confidence,
                questions, points, weak, suggestions, weak.isEmpty() ? "复习错题后安排阶段复测" : "完成薄弱知识点练习后再复测", VERSION);
    }

    private QuestionResult question(Evidence row, boolean ambiguous) {
        boolean answered = row.answerId() != null;
        boolean validScore = !answered || (row.score() != null && row.score() >= 0 && row.possible() != null
                && BigDecimal.valueOf(row.score()).compareTo(row.possible()) <= 0);
        boolean incomplete = ambiguous || row.sourceChanged() || row.title() == null || row.referenceAnswer() == null
                || row.referenceAnswer().isBlank() || row.possible() == null || !validScore
                || (answered && (row.correct() == null || row.correct() < 0 || row.correct() > 2
                || row.feedback() == null || row.feedback().contains("（错误类型：")));
        Integer score = answered ? row.score() : Integer.valueOf(0);
        if (ambiguous || !validScore) score = null;
        String verdict = incomplete ? "REVIEW" : !answered ? "UNANSWERED" : switch (row.correct()) {
            case 1 -> "CORRECT";
            case 2 -> "PARTIAL";
            default -> "INCORRECT";
        };
        String reason = incomplete ? "评分依据不完整或题目已变更，请教师复核" : answered ? row.feedback() : "未提交该题答案";
        return new QuestionResult(row.questionId(), row.title(), row.type(), row.sourceChanged() ? null : row.referenceAnswer(),
                ambiguous ? null : row.userAnswer(), score, row.possible(), verdict, reason, incomplete);
    }

    private static class PointTotal {
        private final Tag tag;
        private int earned;
        private BigDecimal possible = BigDecimal.ZERO;
        PointTotal(Tag tag) { this.tag = tag; }
        void add(int score, BigDecimal maximum) { earned += score; possible = possible.add(maximum); }
        PointResult result() {
            BigDecimal rate = possible.signum() == 0 ? null : BigDecimal.valueOf(earned).movePointRight(2)
                    .divide(possible, 2, RoundingMode.HALF_UP);
            return new PointResult(tag.pointId(), tag.name(), earned, possible, rate);
        }
    }
}
