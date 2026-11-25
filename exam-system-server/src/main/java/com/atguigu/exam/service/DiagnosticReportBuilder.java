package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.KnowledgePointVideo;
import com.atguigu.exam.entity.Video;
import com.atguigu.exam.mapper.KnowledgePointVideoMapper;
import com.atguigu.exam.mapper.VideoMapper;
import com.atguigu.exam.vo.DiagnosticReport;
import com.atguigu.exam.vo.DiagnosticReport.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DiagnosticReportBuilder {
    private static final BigDecimal WEAK_THRESHOLD = BigDecimal.valueOf(60);
    private static final String VERSION = "recorded-grades-v1";

    private final KnowledgePointVideoMapper kpVideos;
    private final VideoMapper videos;

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
        var suggestions = buildSuggestions(weak);
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

    /**
     * M5 资源推荐升级：薄弱知识点若在 knowledge_point_videos 有映射且映射视频存在已发布
     * （videos.status=1）记录，建议条目附带具体视频列表（videoId/title/fileUrl/coverUrl，
     * 按 sort 升序去重）；否则 videos=null，回退原文字建议。视频本就是学员可见资源，不下发答案解析。
     */
    private List<Suggestion> buildSuggestions(List<PointResult> weak) {
        if (weak.isEmpty()) return List.of();
        List<Long> pointIds = weak.stream().map(PointResult::id).toList();
        Map<Long, List<Long>> mappings = kpVideos.selectList(new LambdaQueryWrapper<KnowledgePointVideo>()
                        .in(KnowledgePointVideo::getKnowledgePointId, pointIds)
                        .orderByAsc(KnowledgePointVideo::getSort))
                .stream().filter(mapping -> mapping.getVideoId() != null)
                .collect(Collectors.groupingBy(KnowledgePointVideo::getKnowledgePointId, LinkedHashMap::new,
                        Collectors.mapping(KnowledgePointVideo::getVideoId, Collectors.toList())));
        List<Long> mappedVideoIds = mappings.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, Video> published = mappedVideoIds.isEmpty() ? Map.of() : videos.selectList(
                        new LambdaQueryWrapper<Video>().in(Video::getId, mappedVideoIds)
                                .eq(Video::getStatus, Video.STATUS_PUBLISHED))
                .stream().filter(video -> video.getStatus() != null && video.getStatus() == Video.STATUS_PUBLISHED)
                .collect(Collectors.toMap(Video::getId, Function.identity(), (a, b) -> a));
        return weak.stream().map(point -> {
            String text = "复习“" + point.name() + "”对应视频，再完成该知识点基础练习";
            List<VideoItem> items = mappings.getOrDefault(point.id(), List.of()).stream().distinct()
                    .map(published::get).filter(Objects::nonNull)
                    .map(video -> new VideoItem(video.getId(), video.getTitle(), video.getFileUrl(), video.getCoverUrl()))
                    .toList();
            return new Suggestion(text, items.isEmpty() ? null : items);
        }).toList();
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
