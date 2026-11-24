package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.ExamClass;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.ExamClassMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ExamClassService;
import com.atguigu.exam.vo.ExamClassVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExamClassServiceImpl implements ExamClassService {

    private static final String ASSIGNMENT_ENABLED = "ENABLED";
    private static final String CLASS_STUDENT = "STUDENT";
    private static final String STUDENT_NOT_STARTED = "未考";
    private static final String STUDENT_SUBMITTED = "已交";
    private static final String ATTEMPTS_LOCKED = "已有学员开考，无法修改考试次数";

    @Autowired
    private ExamClassMapper examClassMapper;
    @Autowired
    private ExamRecordMapper examRecordMapper;
    @Autowired
    private PaperMapper paperMapper;
    @Autowired
    private ClazzMapper clazzMapper;
    @Autowired
    private ClassMemberMapper classMemberMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ClassScopeService classScopeService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    public List<ExamClassVO.CreateResult> createAssignments(ExamClassVO.CreateRequest request,
                                                             Long userId,
                                                             boolean admin) {
        validateWindow(request.getStartTime(), request.getEndTime());
        int maxAttempts = request.getMaxAttempts() == null ? 1 : request.getMaxAttempts();
        validateMaxAttempts(maxAttempts);
        Paper paper = paperMapper.selectById(request.getExamId());
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在");
        }
        if (!"PUBLISHED".equals(paper.getStatus())) {
            throw new IllegalArgumentException("仅已发布试卷可分配");
        }

        List<Long> classIds = request.getClassIds().stream().distinct().toList();
        Map<Long, Clazz> classes = clazzMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity(), (first, ignored) -> first));
        for (Long classId : classIds) {
            Clazz clazz = classes.get(classId);
            if (clazz == null) {
                throw new IllegalArgumentException("班级 " + classId + " 不存在");
            }
            if (!"ENABLED".equals(clazz.getStatus())) {
                throw new IllegalArgumentException("班级 " + classId + " 未启用");
            }
            checkClassScope(userId, classId, admin);
        }

        TransactionTemplate createTransaction = new TransactionTemplate(transactionManager);
        createTransaction.setIsolationLevel(Isolation.READ_COMMITTED.value());
        List<ExamClassVO.CreateResult> results = new ArrayList<>();
        for (Long classId : classIds) {
            ExamClassVO.CreateResult result = createTransaction.execute(status -> createOne(
                    request.getExamId(), classId, request.getStartTime(), request.getEndTime(), maxAttempts));
            results.add(result);
        }
        log.info("处理试卷班级分配：examId={} requested={} results={}", request.getExamId(), classIds.size(), results.size());
        return results;
    }

    private ExamClassVO.CreateResult createOne(Long examId, Long classId, LocalDateTime startTime,
                                                LocalDateTime endTime, int maxAttempts) {
        ExamClassVO.CreateResult result = new ExamClassVO.CreateResult();
        result.setClassId(classId);
        ExamClass existing = examClassMapper.lockByPair(examId, classId);
        if (existing != null) {
            return resolveExisting(result, existing, startTime, endTime, maxAttempts);
        }

        ExamClass assignment = new ExamClass();
        assignment.setExamId(examId);
        assignment.setClassId(classId);
        assignment.setStartTime(startTime);
        assignment.setEndTime(endTime);
        assignment.setMaxAttempts(maxAttempts);
        assignment.setStatus(ASSIGNMENT_ENABLED);
        try {
            if (examClassMapper.insert(assignment) != 1) {
                throw new IllegalStateException("分配创建失败，请重试");
            }
            result.setAssignmentId(assignment.getId());
            result.setStatus("CREATED");
            result.setMessage("分配成功");
            return result;
        } catch (DuplicateKeyException duplicate) {
            ExamClass current = examClassMapper.lockByPair(examId, classId);
            if (current == null) {
                throw new IllegalStateException("分配创建失败，请重试");
            }
            return resolveExisting(result, current, startTime, endTime, maxAttempts);
        }
    }

    private ExamClassVO.CreateResult resolveExisting(ExamClassVO.CreateResult result, ExamClass existing,
                                                      LocalDateTime startTime, LocalDateTime endTime, int maxAttempts) {
        result.setAssignmentId(existing.getId());
        if (!Byte.valueOf((byte) 1).equals(existing.getIsDeleted())) {
            result.setStatus("EXISTS");
            result.setMessage("该班级已分配此试卷");
        } else if (maxAttempts != existing.getMaxAttempts() && examRecordMapper.hasAssignmentAttempts(existing.getId())) {
            result.setStatus("FAILED");
            result.setMessage(ATTEMPTS_LOCKED + "（原分配 " + existing.getMaxAttempts() + " 次）");
        } else {
            if (examClassMapper.reviveDeleted(existing.getExamId(), existing.getClassId(),
                    startTime, endTime, maxAttempts) != 1) {
                throw new IllegalStateException("分配恢复失败，请重试");
            }
            result.setStatus("REVIVED");
            result.setMessage("已恢复原分配");
        }
        return result;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateAssignment(Long id, ExamClassVO.UpdateRequest request, Long userId, boolean admin) {
        ExamClass assignment = requireAssignment(examClassMapper.lockById(id));
        checkClassScope(userId, assignment.getClassId(), admin);

        LocalDateTime startTime = request.isStartTimeProvided() ? request.getStartTime() : assignment.getStartTime();
        LocalDateTime endTime = request.isEndTimeProvided() ? request.getEndTime() : assignment.getEndTime();
        Integer maxAttempts = request.isMaxAttemptsProvided() ? request.getMaxAttempts() : assignment.getMaxAttempts();
        validateWindow(startTime, endTime);
        validateMaxAttempts(maxAttempts);

        if (!maxAttempts.equals(assignment.getMaxAttempts()) && examRecordMapper.hasAssignmentAttempts(id)) {
            throw new IllegalArgumentException(ATTEMPTS_LOCKED);
        }
        if (examClassMapper.updateWindowAndAttempts(id, startTime, endTime, maxAttempts) != 1) {
            throw new IllegalArgumentException("分配不存在");
        }
        log.info("更新考试分配：id={} startTime={} endTime={} maxAttempts={}", id, startTime, endTime, maxAttempts);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revokeAssignment(Long id, Long userId, boolean admin) {
        ExamClass assignment = requireAssignment(examClassMapper.lockById(id));
        checkClassScope(userId, assignment.getClassId(), admin);
        if (hasRecordStatus(id, ExamStatus.IN_PROGRESS)) {
            throw new IllegalArgumentException("有学员正在考试，无法撤销分配");
        }
        if (examClassMapper.deleteById(id) != 1) {
            throw new IllegalArgumentException("分配不存在");
        }
        log.info("撤销考试分配：id={} examId={} classId={}", id, assignment.getExamId(), assignment.getClassId());
    }

    @Override
    public Page<ExamClassVO.Detail> pageAssignments(Page<ExamClassVO.Detail> page,
                                                    Long examId,
                                                    Long classId,
                                                    Long userId,
                                                    boolean admin) {
        LambdaQueryWrapper<ExamClass> wrapper = new LambdaQueryWrapper<ExamClass>()
                .eq(examId != null, ExamClass::getExamId, examId)
                .eq(classId != null, ExamClass::getClassId, classId)
                .orderByDesc(ExamClass::getCreateTime)
                .orderByDesc(ExamClass::getId);
        if (!admin) {
            List<Long> classIds = classScopeService.getTeacherClassIds(userId);
            if (classIds.isEmpty()) {
                page.setTotal(0);
                page.setRecords(List.of());
                return page;
            }
            wrapper.in(ExamClass::getClassId, classIds);
        }
        Page<ExamClass> assignments = examClassMapper.selectPage(new Page<>(page.getCurrent(), page.getSize()), wrapper);
        List<ExamClassVO.Detail> details = toDetails(assignments.getRecords());
        page.setTotal(assignments.getTotal());
        page.setCurrent(assignments.getCurrent());
        page.setSize(assignments.getSize());
        page.setPages(assignments.getPages());
        page.setRecords(details);
        return page;
    }

    @Override
    public ExamClassVO.Detail getAssignment(Long id, Long userId, boolean admin) {
        ExamClass assignment = requireAssignment(examClassMapper.selectById(id));
        checkClassScope(userId, assignment.getClassId(), admin);
        ExamClassVO.Detail detail = toDetails(List.of(assignment)).get(0);
        Map<String, Long> counts = rosterRecords(assignment).values().stream()
                .collect(Collectors.groupingBy(ExamClassServiceImpl::latestStatus, Collectors.counting()));
        detail.setNotStartedCount(counts.getOrDefault(STUDENT_NOT_STARTED, 0L).intValue());
        detail.setInProgressCount(counts.getOrDefault(ExamStatus.IN_PROGRESS, 0L).intValue());
        detail.setSubmittedCount(counts.getOrDefault(STUDENT_SUBMITTED, 0L).intValue());
        detail.setGradedCount(counts.getOrDefault(ExamStatus.GRADED, 0L).intValue());
        return detail;
    }

    @Override
    public List<ExamClassVO.StudentProgress> listStudents(Long id, Long userId, boolean admin) {
        ExamClass assignment = requireAssignment(examClassMapper.selectById(id));
        checkClassScope(userId, assignment.getClassId(), admin);
        Map<Long, List<ExamRecord>> recordsByUser = rosterRecords(assignment);
        if (recordsByUser.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(recordsByUser.keySet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, ignored) -> first));

        List<ExamClassVO.StudentProgress> result = new ArrayList<>();
        for (var item : recordsByUser.entrySet()) {
            Long studentId = item.getKey();
            List<ExamRecord> userRecords = item.getValue();
            User user = users.get(studentId);
            ExamClassVO.StudentProgress progress = new ExamClassVO.StudentProgress();
            progress.setUserId(studentId);
            progress.setRealName(user != null ? user.getRealName()
                    : userRecords.isEmpty() ? null : userRecords.get(0).getStudentName());
            progress.setAttemptCount(userRecords.size());
            progress.setMaxScore(userRecords.stream().filter(record -> ExamStatus.GRADED.equals(record.getStatus()))
                    .map(ExamRecord::getScore).filter(score -> score != null)
                    .max(Integer::compareTo).orElse(null));
            progress.setLatestStatus(latestStatus(userRecords));
            result.add(progress);
        }
        return result;
    }

    private Map<Long, List<ExamRecord>> rosterRecords(ExamClass assignment) {
        Map<Long, List<ExamRecord>> roster = new TreeMap<>();
        for (ClassMember member : classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                .select(ClassMember::getUserId)
                .eq(ClassMember::getClassId, assignment.getClassId())
                .eq(ClassMember::getRoleInClass, CLASS_STUDENT))) {
            roster.putIfAbsent(member.getUserId(), new ArrayList<>());
        }
        for (ExamRecord record : examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .select(ExamRecord::getUserId, ExamRecord::getStudentName, ExamRecord::getStatus, ExamRecord::getScore)
                .eq(ExamRecord::getExamClassId, assignment.getId())
                .isNotNull(ExamRecord::getUserId)
                .orderByDesc(ExamRecord::getCreateTime)
                .orderByDesc(ExamRecord::getId))) {
            roster.computeIfAbsent(record.getUserId(), ignored -> new ArrayList<>()).add(record);
        }
        return roster;
    }

    private List<ExamClassVO.Detail> toDetails(List<ExamClass> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        List<Long> examIds = assignments.stream().map(ExamClass::getExamId).distinct().toList();
        List<Long> classIds = assignments.stream().map(ExamClass::getClassId).distinct().toList();
        Map<Long, Paper> papers = paperMapper.selectBatchIds(examIds).stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity(), (first, ignored) -> first));
        Map<Long, Clazz> classes = clazzMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity(), (first, ignored) -> first));

        List<ExamClassVO.Detail> result = new ArrayList<>();
        for (ExamClass assignment : assignments) {
            ExamClassVO.Detail detail = new ExamClassVO.Detail();
            detail.setId(assignment.getId());
            detail.setExamId(assignment.getExamId());
            detail.setClassId(assignment.getClassId());
            Paper paper = papers.get(assignment.getExamId());
            String paperName = paper == null ? null : paper.getName();
            detail.setPaperName(paperName);
            detail.setExamName(paperName);
            Clazz clazz = classes.get(assignment.getClassId());
            detail.setClassName(clazz == null ? null : clazz.getName());
            detail.setStartTime(assignment.getStartTime());
            detail.setEndTime(assignment.getEndTime());
            detail.setMaxAttempts(assignment.getMaxAttempts());
            detail.setStatus(assignment.getStatus());
            result.add(detail);
        }
        return result;
    }

    private ExamClass requireAssignment(ExamClass assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("分配不存在");
        }
        return assignment;
    }

    private void checkClassScope(Long userId, Long classId, boolean admin) {
        if (!admin && !classScopeService.isTeacherOfClass(userId, classId)) {
            log.warn("教师 {} 尝试访问非负责班级 {} 的考试分配", userId, classId);
            throw new ClassScopeException();
        }
    }

    private boolean hasRecordStatus(Long id, String status) {
        return examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamClassId, id)
                .eq(ExamRecord::getStatus, status)) > 0;
    }

    private void validateWindow(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }
    }

    private void validateMaxAttempts(Integer maxAttempts) {
        if (maxAttempts == null || maxAttempts < 1) {
            throw new IllegalArgumentException("总考试次数必须大于等于 1");
        }
    }

    private static String latestStatus(List<ExamRecord> records) {
        if (records.isEmpty()) {
            return STUDENT_NOT_STARTED;
        }
        String status = records.get(0).getStatus();
        if (ExamStatus.IN_PROGRESS.equals(status)) {
            return ExamStatus.IN_PROGRESS;
        }
        if (ExamStatus.GRADED.equals(status)) {
            return ExamStatus.GRADED;
        }
        return STUDENT_SUBMITTED;
    }
}
