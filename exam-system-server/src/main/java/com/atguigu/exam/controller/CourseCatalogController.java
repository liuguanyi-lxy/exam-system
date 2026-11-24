package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.impl.CourseCatalogService;
import com.atguigu.exam.vo.CourseRequestVo;
import com.atguigu.exam.vo.CourseStatusVo;
import com.atguigu.exam.vo.CourseSummaryVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@RequireRole({"ADMIN", "TEACHER"})
@RequiredArgsConstructor
public class CourseCatalogController {
    private final CourseCatalogService catalog;

    @GetMapping("/list")
    public Result<Page<CourseSummaryVo>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status, HttpServletRequest request) {
        return Result.success(catalog.list(page, size, keyword, status, userId(request), role(request)));
    }

    @PostMapping
    public Result<CourseSummaryVo> create(@Valid @RequestBody CourseRequestVo input, HttpServletRequest request) {
        return Result.success(catalog.create(input, userId(request), role(request)));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CourseRequestVo input,
            HttpServletRequest request) {
        catalog.update(id, input, userId(request), role(request));
        return Result.success(null, "课程已更新");
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody CourseStatusVo input,
            HttpServletRequest request) {
        catalog.updateStatus(id, input.getStatus(), userId(request), role(request));
        return Result.success(null, "状态已更新");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        catalog.delete(id, userId(request), role(request));
        return Result.success(null, "课程已删除");
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private String role(HttpServletRequest request) {
        return (String) request.getAttribute("userRole");
    }
}
