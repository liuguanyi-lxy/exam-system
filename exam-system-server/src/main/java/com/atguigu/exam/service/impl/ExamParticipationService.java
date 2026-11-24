package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.ExamClass;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ExamClassMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.vo.ExamAttemptCountVo;
import com.atguigu.exam.vo.StartExamVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamParticipationService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final String NOT_STARTED = "未开始";
    private static final String OPEN = "进行中";
    private static final String ENDED = "已结束";
    private static final DateTimeFormatter WINDOW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Comparator<ExamClass> CAPACITY_ORDER = Comparator
            .comparing(ExamClass::getMaxAttempts, Comparator.reverseOrder()).thenComparing(ExamClass::getId);
    private final ExamRecordMapper records;
    private final ExamClassMapper assignments;
    private final PaperMapper papers;
    private final UserMapper users;
    private final ClassScopeService classScope;

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ExamRecord start(StartExamVo input, Long userId) {
        Integer paperId = input.getPaperId();
        if (users.lockExamCandidate(userId) == null) throw new ClassScopeException("学员账号不存在");
        ExamRecord active = records.findActiveAttempt(userId, paperId);
        if (input.getResumeRecordId() != null
                && (active == null || !Objects.equals(input.getResumeRecordId(), active.getId()))) {
            throw new IllegalArgumentException("需要续考的记录已不可用，请返回考试列表");
        }
        if (active != null) return active;

        requirePublished(papers.selectById(paperId));
        ExamClass selected = null;
        long used = records.countAttempts(userId, paperId);
        if (!classScope.getStudentClassIds(userId).isEmpty()) {
            List<ExamClass> eligible = assignments.findStudentAssignments(userId, paperId, true);
            selected = requireAvailable(eligible, used, LocalDateTime.now());
        }
        var record = new ExamRecord();
        record.setUserId(userId);
        record.setStudentName(input.getStudentName().strip());
        record.setExamId(paperId);
        record.setStatus(ExamStatus.IN_PROGRESS);
        record.setWindowSwitches(0);
        record.setStartTime(LocalDateTime.now());
        record.setAttemptNo(Math.toIntExact(used + 1));
        if (selected != null) {
            record.setClassId(selected.getClassId());
            record.setExamClassId(selected.getId());
        }
        try {
            records.insert(record);
        } catch (DuplicateKeyException exception) {
            ExamRecord existing = records.findActiveAttempt(userId, paperId);
            if (existing != null) return existing;
            throw new IllegalArgumentException("开考请求冲突，请稍后重试");
        }
        return record;
    }

    public List<Paper> list(Long userId, String name) {
        boolean legacy = classScope.getStudentClassIds(userId).isEmpty();
        Map<Long, List<ExamClass>> byPaper = legacy ? Map.of() : assignments.findStudentAssignments(userId, null, false)
                .stream().collect(Collectors.groupingBy(ExamClass::getExamId));
        var active = records.selectList(new LambdaQueryWrapper<ExamRecord>().select(ExamRecord::getId, ExamRecord::getExamId)
                .eq(ExamRecord::getUserId, userId).eq(ExamRecord::getStatus, ExamStatus.IN_PROGRESS)
                .orderByDesc(ExamRecord::getId)).stream().collect(Collectors.toMap(record -> record.getExamId().longValue(),
                Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        var visibleIds = new LinkedHashSet<>(byPaper.keySet());
        visibleIds.addAll(active.keySet());
        if (!legacy && visibleIds.isEmpty()) return List.of();
        String keyword = name == null ? "" : name.strip();
        var query = new LambdaQueryWrapper<Paper>().like(!keyword.isEmpty(), Paper::getName, keyword)
                .orderByDesc(Paper::getId);
        if (!legacy) query.in(Paper::getId, visibleIds);
        query.and(where -> where.eq(Paper::getStatus, PUBLISHED)
                .or(!active.isEmpty(), allowed -> allowed.in(Paper::getId, active.keySet())));
        List<Paper> visible = papers.selectList(query);
        Map<Long, Long> attempts = byPaper.isEmpty() ? Map.of() : records.countAttemptsByPaper(userId).stream()
                .collect(Collectors.toMap(ExamAttemptCountVo::getExamId, ExamAttemptCountVo::getUsedAttempts));
        LocalDateTime now = LocalDateTime.now();
        visible.forEach(paper -> {
            ExamRecord current = active.get(paper.getId());
            if (current != null) paper.setResumeRecordId(current.getId());
            decorate(paper, byPaper.getOrDefault(paper.getId(), List.of()), attempts.getOrDefault(paper.getId(), 0L), now);
        });
        return visible;
    }

    public Paper studentPreview(Long userId, Integer paperId) {
        Paper paper = papers.selectById(paperId);
        if (paper == null) throw new ClassScopeException("考试不存在或不可参加");
        ExamRecord active = records.findActiveAttempt(userId, paperId);
        if (active != null) {
            paper.setResumeRecordId(active.getId());
            return paper;
        }
        requirePublished(paper);
        if (classScope.getStudentClassIds(userId).isEmpty()) return paper;
        List<ExamClass> eligible = assignments.findStudentAssignments(userId, paperId, false);
        if (eligible.isEmpty()) throw new ClassScopeException("该考试未分配给你的班级");
        decorate(paper, eligible, records.countAttempts(userId, paperId), LocalDateTime.now());
        return paper;
    }

    static ExamClass requireAvailable(List<ExamClass> choices, long used, LocalDateTime now) {
        if (choices.isEmpty()) throw new ClassScopeException("该考试未分配给你的班级");
        ExamClass selected = select(choices, now);
        if (!OPEN.equals(windowStatus(selected, now))) {
            throw new IllegalArgumentException("考试时间：" + formatLimit(selected.getStartTime()) + " ~ "
                    + formatLimit(selected.getEndTime()) + "，当前不可参加");
        }
        if (used >= selected.getMaxAttempts()) {
            throw new IllegalArgumentException("考试次数已用完（共 " + selected.getMaxAttempts() + " 次）");
        }
        return selected;
    }

    private static void decorate(Paper paper, List<ExamClass> choices, long used, LocalDateTime now) {
        paper.setAssigned(!choices.isEmpty());
        if (choices.isEmpty()) return;
        ExamClass selected = select(choices, now);
        paper.setWindowStatus(windowStatus(selected, now));
        paper.setExamStartTime(selected.getStartTime());
        paper.setExamEndTime(selected.getEndTime());
        paper.setAttemptLeft((int) Math.max(0L, selected.getMaxAttempts() - used));
    }

    private static ExamClass select(List<ExamClass> choices, LocalDateTime now) {
        return choices.stream().filter(item -> OPEN.equals(windowStatus(item, now))).min(CAPACITY_ORDER)
                .orElseGet(() -> choices.stream().filter(item -> NOT_STARTED.equals(windowStatus(item, now)))
                        .min(Comparator.comparing(ExamClass::getStartTime).thenComparing(CAPACITY_ORDER))
                        .orElseGet(() -> choices.stream().min(Comparator
                                .comparing(ExamClass::getEndTime, Comparator.reverseOrder()).thenComparing(CAPACITY_ORDER))
                                .orElseThrow()));
    }

    private static String windowStatus(ExamClass assignment, LocalDateTime now) {
        if (assignment.getStartTime() != null && now.isBefore(assignment.getStartTime())) return NOT_STARTED;
        if (assignment.getEndTime() != null && now.isAfter(assignment.getEndTime())) return ENDED;
        return OPEN;
    }

    private static String formatLimit(LocalDateTime time) {
        return time == null ? "不限" : WINDOW_FORMAT.format(time);
    }

    private void requirePublished(Paper paper) {
        if (paper == null || !PUBLISHED.equals(paper.getStatus())) {
            throw new ClassScopeException("考试不存在或未发布");
        }
    }
}
