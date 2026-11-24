package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.service.LearningService;
import com.atguigu.exam.service.impl.CourseLearningQueryService;
import com.atguigu.exam.vo.ChapterLearningVo;
import com.atguigu.exam.vo.LearningHeartbeatResultVo;
import com.atguigu.exam.vo.LearningHeartbeatVo;
import com.atguigu.exam.vo.MyCourseDetailVo;
import com.atguigu.exam.vo.MyCourseItemVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CourseLearningController {
    private final CourseLearningQueryService queries;
    private final LearningService learning;

    @GetMapping("/api/my/courses")
    @RequireRole("STUDENT")
    public Result<Map<String, List<MyCourseItemVo>>> courses(HttpServletRequest request) {
        return Result.success(Map.of("courses", queries.list(userId(request))));
    }

    @GetMapping("/api/my/courses/{id}")
    @RequireRole("STUDENT")
    public Result<MyCourseDetailVo> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(queries.detail(id, userId(request)));
    }

    @GetMapping("/api/my/courses/{courseId}/chapters/{chapterId}")
    @RequireRole("STUDENT")
    public Result<ChapterLearningVo> chapter(@PathVariable Long courseId, @PathVariable Long chapterId, HttpServletRequest request) {
        return Result.success(queries.chapter(courseId, chapterId, userId(request)));
    }

    @PostMapping("/api/learning/chapter-videos/{id}/heartbeat")
    @RequireRole("STUDENT")
    public Result<LearningHeartbeatResultVo> heartbeat(@PathVariable Long id, @Valid @RequestBody LearningHeartbeatVo input,
            HttpServletRequest request) {
        return Result.success(learning.heartbeat(userId(request), id, input.getSessionId(), input.getSequence(),
                input.getViewDuration(), request));
    }

    @PostMapping("/api/learning/chapter-videos/{id}/external-complete")
    @RequireRole("STUDENT")
    public Result<LearningHeartbeatResultVo> confirmExternal(@PathVariable Long id, @RequestParam String sessionId,
            HttpServletRequest request) {
        return Result.success(learning.completeExternal(userId(request), id, sessionId));
    }

    @GetMapping("/api/courses/{id}/class-progress")
    @RequireRole({"ADMIN", "TEACHER"})
    public Result<Map<String, Object>> classProgress(@PathVariable Long id, @RequestParam Long classId,
            HttpServletRequest request) {
        return Result.success(queries.classProgress(id, classId, userId(request), (String) request.getAttribute("userRole")));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
