package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ClassService;
import com.atguigu.exam.vo.ClassStatusVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班级控制器（契约 4 节：班级分页列表/创建/更新/启停/详情/删除）
 * 数据范围：ADMIN 全量；TEACHER 仅负责班级（列表过滤返回子集，单资源越权 403）；STUDENT 调用 → 403
 */
@Slf4j
@RestController
@RequestMapping("/api/classes")
@RequireRole({"ADMIN", "TEACHER"})
@Tag(name = "班级管理", description = "班级分页列表、创建、更新、启用/禁用、详情与删除")
public class ClassController {

    private static final int MAX_PAGE = 10_000;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private ClassService classService;
    @Autowired
    private ClassScopeService classScopeService;

    /**
     * 班级分页列表：ADMIN 全量；TEACHER 自动过滤为负责班级（含已禁用的负责班级，status 字段区分）
     */
    @GetMapping("/list")
    @Operation(summary = "班级分页列表", description = "ADMIN 返回全部；TEACHER 仅返回自己负责的班级；学生走 /api/my/classes")
    public Result<Page<Clazz>> getClassList(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示数量", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "班级名称模糊筛选") @RequestParam(required = false) String name,
            @Parameter(description = "状态筛选 ENABLED/DISABLED，非法值忽略") @RequestParam(required = false) String status,
            jakarta.servlet.http.HttpServletRequest request) {
        page = normalizePage(page);
        size = normalizePageSize(size);
        Page<Clazz> result = new Page<>(page, size);
        if (isTeacher(request)) {
            // TEACHER-own：列表类接口自动按负责班级过滤（返回子集，不报错）
            List<Long> scopeClassIds = classScopeService.getTeacherClassIds(currentUserId(request));
            result = classService.pageClasses(result, name, status, scopeClassIds);
        } else {
            result = classService.pageClasses(result, name, status, null);
        }
        log.info("查询班级列表：page={} size={} total={}", page, size, result.getTotal());
        return Result.success(result);
    }

    /**
     * 创建班级（ADMIN）
     */
    @RequireRole({"ADMIN"})
    @PostMapping
    @Operation(summary = "创建班级", description = "名称全局唯一（与未删除班级不重名），日期可选，状态固定 ENABLED")
    public Result<Clazz> createClass(@Valid @RequestBody Clazz clazz) {
        Clazz created = classService.createClass(clazz);
        log.info("创建班级成功：id={} name={}", created.getId(), created.getName());
        return Result.success(created, "创建成功");
    }

    /**
     * 更新班级（ADMIN）
     */
    @RequireRole({"ADMIN"})
    @PutMapping("/{id}")
    @Operation(summary = "更新班级", description = "名称必填且唯一性排除自身；班级不存在返回业务失败")
    public Result<Clazz> updateClass(
            @Parameter(description = "班级ID") @PathVariable Long id,
            @Valid @RequestBody Clazz clazz) {
        clazz.setId(id);
        Clazz updated = classService.updateClass(clazz);
        log.info("更新班级成功：id={} name={}", id, updated.getName());
        return Result.success(updated);
    }

    /**
     * 启用/禁用班级（ADMIN）：禁用只影响可见性标记，学员"我的班级"仍显示并标记已停用（验收标准 4）
     */
    @RequireRole({"ADMIN"})
    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用班级", description = "幂等；禁用不改数据范围，仅影响管理侧操作与前端展示")
    public Result<Void> updateClassStatus(
            @Parameter(description = "班级ID") @PathVariable Long id,
            @Valid @RequestBody ClassStatusVo body) {
        classService.updateClassStatus(id, body.getStatus());
        log.info("班级 {} 状态更新为 {}", id, body.getStatus());
        return Result.success(null);
    }

    /**
     * 班级详情（ADMIN / TEACHER-own，非负责班级 → HTTP 403）：本体 + 计数 + 教师列表
     */
    @GetMapping("/{id}")
    @Operation(summary = "班级详情", description = "含教师列表与成员计数；成员走分页子资源，详情响应不膨胀")
    public Result<Clazz> getClassDetail(
            @Parameter(description = "班级ID") @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        // TEACHER-own：单资源越权 → ClassScopeException → HTTP 403（验收标准 3）
        if (isTeacher(request)) {
            checkTeacherScope(request, id);
        }
        return Result.success(classService.getClassDetail(id));
    }

    /**
     * 删除班级（ADMIN）：逻辑删除，成员/教师关系级联物理清理，users 账号不受影响
     */
    @RequireRole({"ADMIN"})
    @DeleteMapping("/{id}")
    @Operation(summary = "删除班级", description = "逻辑删除；成员关系级联清理，账号不受影响")
    public Result<Void> deleteClass(@Parameter(description = "班级ID") @PathVariable Long id) {
        classService.deleteClass(id);
        log.info("删除班级成功：id={}", id);
        return Result.success(null);
    }

    // ==================== 私有工具 ====================

    /** 当前请求是否来自教师角色 */
    private boolean isTeacher(jakarta.servlet.http.HttpServletRequest request) {
        return "TEACHER".equals(String.valueOf(request.getAttribute("userRole")));
    }

    private Long currentUserId(jakarta.servlet.http.HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private int normalizePage(Integer page) {
        return page == null ? 1 : Math.min(MAX_PAGE, Math.max(1, page));
    }

    private int normalizePageSize(Integer size) {
        return size == null ? 10 : Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    /** TEACHER-own 点查：非负责班级抛 ClassScopeException（全局处理器映射 HTTP 403） */
    private void checkTeacherScope(jakarta.servlet.http.HttpServletRequest request, Long classId) {
        if (!classScopeService.isTeacherOfClass(currentUserId(request), classId)) {
            log.warn("教师 {} 尝试访问非负责班级 {}", currentUserId(request), classId);
            throw new ClassScopeException();
        }
    }
}
