package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.impl.CourseAssignmentService;
import com.atguigu.exam.vo.CourseClassesVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/courses/{id}/classes")
@RequireRole({"ADMIN", "TEACHER"})
@RequiredArgsConstructor
public class CourseAssignmentController {
    private final CourseAssignmentService assignmentService;

    @GetMapping
    public Result<Map<String, Object>> getAssignments(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(assignmentService.getAssignments(
                id, (Long) request.getAttribute("userId"), (String) request.getAttribute("userRole")));
    }

    @PutMapping
    public Result<Map<String, Object>> replaceAssignments(@PathVariable Long id,
                                                          @Valid @RequestBody CourseClassesVo body,
                                                          HttpServletRequest request) {
        return Result.success(Map.of("classIds", assignmentService.replaceAssignments(
                id, body.getClassIds(), (Long) request.getAttribute("userId"), (String) request.getAttribute("userRole"))));
    }
}
