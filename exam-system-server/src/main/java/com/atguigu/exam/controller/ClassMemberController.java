package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ClassService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.vo.ClassMemberAddVo;
import com.atguigu.exam.vo.ClassMemberVo;
import com.atguigu.exam.vo.StudentImportResultVo;
import com.atguigu.exam.vo.TeacherAssignVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 班级成员与教师控制器（契约 5/6 节：成员分页/添加/移除、Excel 批量导入与模板、教师分配/移除）
 * 数据范围：ADMIN 全量；TEACHER 仅负责班级（成员列表/详情，越权 → HTTP 403）
 */
@Slf4j
@RestController
@RequestMapping("/api/classes")
@RequireRole({"ADMIN", "TEACHER"})
@Tag(name = "班级成员管理", description = "班级成员分页、添加/移除学员、Excel 批量导入、教师分配与移除")
public class ClassMemberController {

    private static final int MAX_PAGE = 10_000;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private ClassService classService;
    @Autowired
    private ClassScopeService classScopeService;
    @Autowired
    private UserMapper userMapper;

    /**
     * 成员分页列表（ADMIN / TEACHER-own，非负责班级 → HTTP 403）
     * includeSummary=true 时每条附考试摘要（当页批量聚合，禁止循环 N+1）
     */
    @GetMapping("/{id}/members")
    @Operation(summary = "成员分页列表", description = "支持 roleInClass/keyword 筛选；includeSummary=true 附考试次数与最近考试时间")
    public Result<Page<ClassMemberVo>> getMembers(
            @Parameter(description = "班级ID") @PathVariable Long id,
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "班内角色筛选 STUDENT/TEACHER，不传返回全部，非法值忽略") @RequestParam(required = false) String roleInClass,
            @Parameter(description = "用户名/姓名模糊匹配") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否附考试摘要，默认 false") @RequestParam(defaultValue = "false") Boolean includeSummary,
            jakarta.servlet.http.HttpServletRequest request) {
        if (isTeacher(request)) {
            checkTeacherScope(request, id);
        }
        page = normalizePage(page);
        size = normalizePageSize(size);
        Page<ClassMemberVo> result = classService.pageMembers(new Page<>(page, size), id, roleInClass,
                keyword, Boolean.TRUE.equals(includeSummary));
        log.info("查询班级成员列表：classId={} page={} size={} total={}", id, page, size, result.getTotal());
        return Result.success(result);
    }

    /**
     * 手动添加学员（ADMIN）：仅支持已存在用户；已在班内（任一角色）幂等成功
     */
    @RequireRole({"ADMIN"})
    @PostMapping("/{id}/members")
    @Operation(summary = "手动添加学员", description = "已存在用户直接入班；已在班内幂等成功并返回 alreadyInClass=true；自动建号只在 Excel 导入里做")
    public Result<Map<String, Object>> addMember(
            @Parameter(description = "班级ID") @PathVariable Long id,
            @Valid @RequestBody ClassMemberAddVo body) {
        boolean alreadyInClass = classService.addStudent(id, body.getUserId());
        User user = userMapper.selectById(body.getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", body.getUserId());
        data.put("username", user == null ? null : user.getUsername());
        data.put("realName", user == null ? null : user.getRealName());
        data.put("alreadyInClass", alreadyInClass);
        log.info("班级 {} 添加学员 {}：alreadyInClass={}", id, body.getUserId(), alreadyInClass);
        return Result.success(data, "添加成功");
    }

    /**
     * Excel 批量导入学员（ADMIN）：预检整单拒绝 + 入库整体单事务；行级失败不中断整批
     */
    @RequireRole({"ADMIN"})
    @PostMapping("/{id}/members/import")
    @Operation(summary = "Excel 批量导入学员", description = "已存在用户名直接入班不改角色；不存在则自动建号（默认密码 123456、角色 STUDENT）；返回逐行结果")
    public Result<StudentImportResultVo> importMembers(
            @Parameter(description = "班级ID") @PathVariable Long id,
            @Parameter(description = "Excel 文件（.xlsx/.xls，两列：用户名、姓名）") @RequestParam("file") MultipartFile file) {
        StudentImportResultVo result = classService.importStudents(id, file);
        log.info("班级 {} Excel 导入完成：totalRows={} successRows={} createdAccounts={} joinedCount={} failed={}",
                id, result.getTotalRows(), result.getSuccessRows(), result.getCreatedAccounts(),
                result.getJoinedCount(), result.getFailedRows().size());
        return Result.success(result, "导入完成");
    }

    /**
     * 下载学员导入模板（ADMIN）：两列表头"用户名、姓名"，对齐 ExcelUtil.generateTemplate 现有模式
     */
    @RequireRole({"ADMIN"})
    @GetMapping("/members/import-template")
    @Operation(summary = "下载学员导入模板", description = "返回两列（用户名/姓名）的 xlsx 模板文件流")
    public ResponseEntity<byte[]> downloadImportTemplate() throws java.io.IOException {
        byte[] template = ExcelUtil.generateStudentTemplate();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=student_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(template);
    }

    /**
     * 移除学员（ADMIN）：仅删除 role_in_class='STUDENT' 记录，幂等
     */
    @RequireRole({"ADMIN"})
    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "移除学员", description = "仅删除学员关系；教师关系移除走教师接口；幂等")
    public Result<Void> removeMember(
            @Parameter(description = "班级ID") @PathVariable Long id,
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        classService.removeMember(id, userId);
        log.info("班级 {} 移除学员 {}", id, userId);
        return Result.success(null);
    }

    /**
     * 分配教师（ADMIN，可批量）：任一 userId 不存在或非 TEACHER 角色整批失败
     */
    @RequireRole({"ADMIN"})
    @PostMapping("/{id}/teachers")
    @Operation(summary = "分配教师", description = "一班可多师；已是本班教师幂等跳过；非教师角色整批失败并报出问题 ID")
    public Result<Map<String, Object>> assignTeachers(
            @Parameter(description = "班级ID") @PathVariable Long id,
            @Valid @RequestBody TeacherAssignVo body) {
        Map<String, Object> data = classService.assignTeachers(id, body.getUserIds());
        log.info("班级 {} 分配教师：userIds={} 结果 {}", id, body.getUserIds(), data);
        return Result.success(data, "分配成功");
    }

    /**
     * 移除教师（ADMIN）：仅删除 role_in_class='TEACHER' 记录，幂等
     */
    @RequireRole({"ADMIN"})
    @DeleteMapping("/{id}/teachers/{userId}")
    @Operation(summary = "移除教师", description = "仅删除教师关系；幂等")
    public Result<Void> removeTeacher(
            @Parameter(description = "班级ID") @PathVariable Long id,
            @Parameter(description = "教师用户ID") @PathVariable Long userId) {
        classService.removeTeacher(id, userId);
        log.info("班级 {} 移除教师 {}", id, userId);
        return Result.success(null);
    }

    // ==================== 私有工具 ====================

    /** 当前请求是否来自教师角色 */
    private boolean isTeacher(jakarta.servlet.http.HttpServletRequest request) {
        return "TEACHER".equals(String.valueOf(request.getAttribute("userRole")));
    }

    /** TEACHER-own 点查：非负责班级抛 ClassScopeException（全局处理器映射 HTTP 403） */
    private void checkTeacherScope(jakarta.servlet.http.HttpServletRequest request, Long classId) {
        if (!classScopeService.isTeacherOfClass((Long) request.getAttribute("userId"), classId)) {
            log.warn("教师 {} 尝试访问非负责班级 {}", request.getAttribute("userId"), classId);
            throw new ClassScopeException();
        }
    }

    private int normalizePage(Integer page) {
        return page == null ? 1 : Math.min(MAX_PAGE, Math.max(1, page));
    }

    private int normalizePageSize(Integer size) {
        return size == null ? 10 : Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }
}
