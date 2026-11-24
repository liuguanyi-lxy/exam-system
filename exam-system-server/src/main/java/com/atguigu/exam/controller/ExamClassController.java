package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.ExamClassService;
import com.atguigu.exam.vo.ExamClassVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exam-classes")
@RequireRole({"TEACHER", "ADMIN"})
public class ExamClassController {

    @Autowired
    private ExamClassService examClassService;

    @PostMapping
    public Result<List<ExamClassVO.CreateResult>> create(@Valid @RequestBody ExamClassVO.CreateRequest request,
                                                          HttpServletRequest httpRequest) {
        return Result.success(examClassService.createAssignments(request, userId(httpRequest), isAdmin(httpRequest)));
    }

    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id,
                                 @Valid @RequestBody ExamClassVO.UpdateRequest request,
                                 HttpServletRequest httpRequest) {
        examClassService.updateAssignment(id, request, userId(httpRequest), isAdmin(httpRequest));
        return Result.success("考试分配已更新");
    }

    @DeleteMapping("/{id}")
    public Result<String> revoke(@PathVariable Long id, HttpServletRequest request) {
        examClassService.revokeAssignment(id, userId(request), isAdmin(request));
        return Result.success("考试分配已撤销");
    }

    @GetMapping
    public Result<Page<ExamClassVO.Detail>> list(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size,
            HttpServletRequest request) {
        Page<ExamClassVO.Detail> result = examClassService.pageAssignments(
                new Page<>(Math.max(1, page), Math.min(100, Math.max(1, size))),
                examId, classId, userId(request), isAdmin(request));
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<ExamClassVO.Detail> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(examClassService.getAssignment(id, userId(request), isAdmin(request)));
    }

    @GetMapping("/{id}/students")
    public Result<List<ExamClassVO.StudentProgress>> students(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(examClassService.listStudents(id, userId(request), isAdmin(request)));
    }

    private Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (value instanceof Number number) return number.longValue();
        if (value != null) return Long.valueOf(value.toString());
        throw new IllegalStateException("当前登录用户信息缺失");
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equals(String.valueOf(request.getAttribute("userRole")));
    }
}
