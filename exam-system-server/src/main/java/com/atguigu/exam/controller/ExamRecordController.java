package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ExamRecordService;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.utils.QuestionSanitizer;
import com.atguigu.exam.vo.ExamRankingVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 考试记录控制器 - 处理考试记录管理相关的HTTP请求
 * 包括考试记录查询、分页展示、成绩排行榜等功能
 */
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/exam-records")  // 考试记录API路径前缀
@Tag(name = "考试记录管理", description = "考试记录相关操作，包括记录查询、成绩管理、排行榜展示等功能")  // Swagger API分组
@Slf4j
public class ExamRecordController {

    private static final int MAX_PAGE = 10_000;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private ExamService examService;
    @Autowired
    private ExamRecordService examRecordService;
    @Autowired
    private PaperService paperService;
    @Autowired
    private com.atguigu.exam.service.UserService userService;
    @Autowired
    private ClassScopeService classScopeService;
    /**
     * 分页查询考试记录
     * 学生角色强制只返回自己的记录；教师仅返回负责班级学员记录；管理员可看全部
     */
    @GetMapping("/list")  // 处理GET请求
    @Operation(summary = "分页查询考试记录", description = "支持多条件筛选的考试记录分页查询；学生只能查询自己的记录，教师/管理员可查询全部")  // API描述
    public Result<Page<ExamRecord>> getExamRecords(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量", example = "20") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "试卷ID筛选条件") @RequestParam(required = false) Integer paperId,
            @Parameter(description = "学生姓名筛选条件") @RequestParam(required = false) String studentName,
            @Parameter(description = "学号筛选条件") @RequestParam(required = false) String studentNumber,
            @Parameter(description = "考试状态，0-进行中，1-已完成，2-已批阅") @RequestParam(required = false) Integer status,
            @Parameter(description = "开始日期，格式：yyyy-MM-dd") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期，格式：yyyy-MM-dd") @RequestParam(required = false) String endDate,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        boolean teacherOrAdmin = isTeacherOrAdmin(request);
        page = normalizePage(page);
        size = normalizePageSize(size);
        //分页查询的Page对象,根据传入的参数指定当前页和每页显示数量
        Page<ExamRecord> examRecordPage = new Page<>(page, size);
        // 添加分页查询的条件
        LambdaQueryWrapper<ExamRecord> examRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
        //按试卷筛选：试卷列表页点「考生成绩」进来时需要
        examRecordLambdaQueryWrapper.eq(paperId != null, ExamRecord::getExamId, paperId);
        examRecordLambdaQueryWrapper.like(studentName != null, ExamRecord::getStudentName, studentName);
        //学生角色后端强制按本人 userId 过滤，前端传什么筛选条件都只看得到自己的记录
        Long currentUserId = (Long) request.getAttribute("userId");
        String currentRole = String.valueOf(request.getAttribute("userRole"));
        if ("TEACHER".equals(currentRole)) {
            java.util.Set<Long> visibleStudentIds = currentUserId == null
                    ? java.util.Collections.emptySet()
                    : classScopeService.getStudentUserIds(classScopeService.getTeacherClassIds(currentUserId));
            if (visibleStudentIds.isEmpty()) {
                examRecordLambdaQueryWrapper.eq(ExamRecord::getUserId, -1L);
            } else {
                examRecordLambdaQueryWrapper.in(ExamRecord::getUserId, visibleStudentIds);
            }
        } else if (!teacherOrAdmin) {
            examRecordLambdaQueryWrapper.eq(ExamRecord::getUserId, currentUserId == null ? -1L : currentUserId);
        }
        // 将状态码转换为状态字符串
        if (status != null) {
            String statusStr = switch (status) {
                case 0 -> "进行中";
                case 1 -> "已完成";
                case 2 -> "已批阅";
                default -> null;
            };
            examRecordLambdaQueryWrapper.eq(statusStr != null, ExamRecord::getStatus, statusStr);
        }
        examRecordLambdaQueryWrapper.between(startDate != null && endDate != null, ExamRecord::getCreateTime, startDate, endDate);
        //按照考试开始时间降序排列,后面的考试记录先显示
        examRecordLambdaQueryWrapper.orderByDesc(ExamRecord::getCreateTime);
        examRecordService.customPageGetExamRecord(examRecordPage, examRecordLambdaQueryWrapper);
        //学生视角下，未批阅记录的答案/解析不随列表下发（与详情接口的脱敏规则一致）
        if (!teacherOrAdmin && examRecordPage.getRecords() != null) {
            examRecordPage.getRecords().forEach(r -> {
                if (!ExamStatus.GRADED.equals(r.getStatus()) && r.getPaper() != null) {
                    QuestionSanitizer.stripAnswer(r.getPaper().getQuestions());
                }
            });
        }
        log.info("查询考试记录列表：paperId={}，当前页 {} 条，共 {} 条", paperId,
                examRecordPage.getRecords() == null ? 0 : examRecordPage.getRecords().size(), examRecordPage.getTotal());
        return Result.success(examRecordPage);
    }

    /**
     * 某张试卷的成绩统计（参考人次、平均分、最高/最低分、及格率、分数分布）
     * 试卷列表页的「考生成绩」用它出概览
     */
    @RequireRole({"TEACHER", "ADMIN"})
    @GetMapping("/stats")
    @Operation(summary = "试卷成绩统计", description = "按试卷统计参考人次、平均分、最高最低分、及格率与分数段分布")
    public Result<java.util.Map<String, Object>> getPaperStats(
            @Parameter(description = "试卷ID") @RequestParam Integer paperId,
            jakarta.servlet.http.HttpServletRequest request) {
        Paper paper = paperService.getById(paperId);
        if (paper == null) {
            return Result.error("试卷不存在");
        }
        LambdaQueryWrapper<ExamRecord> recordsQuery =
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, paperId);
        String role = String.valueOf(request.getAttribute("userRole"));
        if ("TEACHER".equals(role)) {
            java.util.Set<Long> visibleStudentIds = getTeacherStudentIds(request);
            if (visibleStudentIds.isEmpty()) {
                recordsQuery.eq(ExamRecord::getUserId, -1L);
            } else {
                recordsQuery.in(ExamRecord::getUserId, visibleStudentIds);
            }
        }
        java.util.List<ExamRecord> records = examRecordService.list(recordsQuery);

        int total = records.size();
        // 只统计已批阅的记录，避免把进行中的 0 分算进平均分
        java.util.List<ExamRecord> graded = records.stream()
                .filter(r -> ExamStatus.GRADED.equals(r.getStatus()) && r.getScore() != null).toList();
        double totalScore = paper.getTotalScore() == null ? 100 : paper.getTotalScore().doubleValue();

        double avg = graded.stream().mapToInt(ExamRecord::getScore).average().orElse(0);
        int max = graded.stream().mapToInt(ExamRecord::getScore).max().orElse(0);
        int min = graded.stream().mapToInt(ExamRecord::getScore).min().orElse(0);
        long pass = graded.stream().filter(r -> r.getScore() >= totalScore * 0.6).count();

        // 分数段分布：不及格 / 及格 / 良好 / 优秀
        int[] buckets = new int[4];
        for (ExamRecord r : graded) {
            double rate = totalScore == 0 ? 0 : r.getScore() / totalScore;
            if (rate < 0.6) buckets[0]++;
            else if (rate < 0.8) buckets[1]++;
            else if (rate < 1.0) buckets[2]++;
            else buckets[3]++;
        }

        java.util.Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("paperId", paperId);
        stats.put("paperName", paper.getName());
        stats.put("totalScore", totalScore);
        stats.put("totalRecords", total);
        stats.put("gradedRecords", graded.size());
        stats.put("average", Math.round(avg * 10) / 10.0);
        stats.put("max", max);
        stats.put("min", min);
        stats.put("passRate", graded.isEmpty() ? 0 : Math.round(pass * 1000.0 / graded.size()) / 10.0);
        stats.put("distribution", buckets);
        log.info("试卷 {} 成绩统计：参考 {} 人次，已批阅 {} 人次，平均分 {}", paperId, total, graded.size(), stats.get("average"));
        return Result.success(stats);
    }

    /**
     * 根据ID获取考试记录详情
     */
    @GetMapping("/{id}")  // 处理GET请求
    @Operation(summary = "获取考试记录详情", description = "根据记录ID获取考试记录的详细信息；学生只能查看自己的记录，未批阅记录对学生脱敏")  // API描述
    public Result<ExamRecord> getExamRecordById(
            @Parameter(description = "考试记录ID") @PathVariable Integer id,
            jakarta.servlet.http.HttpServletRequest request) {
        if ("TEACHER".equals(String.valueOf(request.getAttribute("userRole")))) {
            ExamRecord record = examRecordService.getById(id);
            if (record == null) {
                return Result.error("考试记录不存在");
            }
            checkTeacherRecordScope(record, request);
        }
        ExamRecord examRecord = examService.customGetExamRecordById(id);
        if (examRecord == null) {
            return Result.error("考试记录不存在");
        }
        boolean teacherOrAdmin = isTeacherOrAdmin(request);
        //归属校验：学生只能查看自己的考试记录
        if (!teacherOrAdmin) {
            checkRecordOwner(examRecord, request);
        }
        // 成绩出来之前（进行中/已交卷未批阅）不能让学生从记录详情里看到答案和解析；
        // 批阅之后结果页需要展示逐题解析与整体评价，完整返回
        boolean graded = ExamStatus.GRADED.equals(examRecord.getStatus());
        if (!graded && !teacherOrAdmin && examRecord.getPaper() != null) {
            QuestionSanitizer.stripAnswer(examRecord.getPaper().getQuestions());
        }
        log.info("查询考试记录详情成功，记录ID：{}，状态：{}", id, examRecord.getStatus());
        return Result.success(examRecord);
    }

    /**
     * 考试记录归属校验：优先比对 user_id；
     * 历史记录 user_id 为空时退化为 student_name 与当前用户姓名比对
     */
    private void checkRecordOwner(ExamRecord examRecord, jakarta.servlet.http.HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (examRecord.getUserId() != null) {
            if (!examRecord.getUserId().equals(currentUserId)) {
                log.warn("用户{}尝试查看不属于自己的考试记录{}", currentUserId, examRecord.getId());
                throw new RuntimeException("无权查看该考试记录！");
            }
            return;
        }
        com.atguigu.exam.entity.User user = userService.getById(currentUserId);
        if (user == null || !examRecord.getStudentName().equals(user.getRealName())) {
            throw new RuntimeException("无权查看该考试记录！");
        }
    }

    private void checkTeacherRecordScope(ExamRecord record, jakarta.servlet.http.HttpServletRequest request) {
        Long userId = record.getUserId();
        if (userId == null || !getTeacherStudentIds(request).contains(userId)) {
            throw new ClassScopeException("无权访问该考试记录");
        }
    }

    private java.util.Set<Long> getTeacherStudentIds(jakarta.servlet.http.HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return java.util.Collections.emptySet();
        }
        return classScopeService.getStudentUserIds(classScopeService.getTeacherClassIds(currentUserId));
    }

    /** 当前请求是否来自教师/管理员 */
    private boolean isTeacherOrAdmin(jakarta.servlet.http.HttpServletRequest request) {
        Object role = request.getAttribute("userRole");
        String r = role == null ? "" : String.valueOf(role);
        return "ADMIN".equals(r) || "TEACHER".equals(r);
    }

    private int normalizePage(Integer page) {
        return page == null ? 1 : Math.min(MAX_PAGE, Math.max(1, page));
    }

    private int normalizePageSize(Integer size) {
        return size == null ? 20 : Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    /**
     * 删除考试记录
     */
    @DeleteMapping("/{id}")  // 处理DELETE请求
    @Operation(summary = "删除考试记录", description = "根据ID删除指定的考试记录；学生只能删除自己的记录，教师/管理员可删除全部")  // API描述
    public Result<Void> deleteExamRecord(
            @Parameter(description = "考试记录ID") @PathVariable Integer id,
            jakarta.servlet.http.HttpServletRequest request) {
            if ("TEACHER".equals(String.valueOf(request.getAttribute("userRole")))) {
                ExamRecord examRecord = examRecordService.getById(id);
                if (examRecord != null) {
                    checkTeacherRecordScope(examRecord, request);
                }
            } else if (!isTeacherOrAdmin(request)) {
                ExamRecord examRecord = examService.customGetExamRecordById(id);
                if (examRecord != null) {
                    checkRecordOwner(examRecord, request);
                }
            }
            examRecordService.customRemoveById(id);
            log.info("删除考试记录成功！id:{}",id);
            return Result.success("删除考试记录成功！id:" + id);
    }

    /**
     * 获取考试排行榜 - 优化版本
     * 使用SQL关联查询，一次性获取所有需要的数据，性能提升数百倍
     * 
     * @param paperId 试卷ID，可选参数
     * @param limit 显示数量限制，可选参数
     * @return 排行榜列表
     */
    @GetMapping("/ranking")  // 处理GET请求
    @RequireRole({"ADMIN", "TEACHER", "STUDENT"})
    @Operation(summary = "获取考试排行榜", description = "获取考试成绩排行榜，支持按试卷筛选和限制显示数量，使用优化的SQL关联查询提升性能")  // API描述
    public Result<List<ExamRankingVO>> getExamRanking(
            @Parameter(description = "试卷ID，可选，不传则显示所有试卷的排行") @RequestParam(required = false) Integer paperId,
            @Parameter(description = "显示数量限制，可选，不传则返回所有记录") @RequestParam(required = false) Integer limit,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String role = String.valueOf(request.getAttribute("userRole"));
        java.util.Collection<Long> visibleStudentIds = null;
        if ("TEACHER".equals(role) || "STUDENT".equals(role)) {
            Long userId = (Long) request.getAttribute("userId");
            List<Long> classIds = userId == null ? List.of()
                    : "TEACHER".equals(role)
                    ? classScopeService.getTeacherClassIds(userId)
                    : classScopeService.getStudentClassIds(userId);
            java.util.Set<Long> studentIds = classScopeService.getStudentUserIds(classIds);
            if ("STUDENT".equals(role) && userId != null) {
                studentIds = new java.util.TreeSet<>(studentIds);
                studentIds.add(userId);
            }
            if (studentIds.isEmpty()) {
                return Result.success(List.of());
            }
            visibleStudentIds = studentIds;
        }
        // 使用优化的查询方法，避免N+1查询；教师和学员仅查看班级范围内成绩。
        List<ExamRankingVO> examRankingVOS = examRecordService.customGetRanking(paperId, limit, visibleStudentIds);
        log.info("查询：{}试卷下的{}条数据成功！数据为：{}",paperId,limit,examRankingVOS);
        return Result.success(examRankingVOS);
    }
}
