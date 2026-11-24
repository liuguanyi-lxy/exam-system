package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.ChapterVideo;
import com.atguigu.exam.entity.CourseChapter;
import com.atguigu.exam.service.impl.CourseContentService;
import com.atguigu.exam.vo.ChapterRequestVo;
import com.atguigu.exam.vo.ChapterVideoRequestVo;
import com.atguigu.exam.vo.ChapterVideoUpdateVo;
import com.atguigu.exam.vo.MoveDirectionVo;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequireRole({"ADMIN", "TEACHER"})
@RequiredArgsConstructor
public class CourseContentController {
    private final CourseContentService content;

    @GetMapping("/courses/{id}/chapters")
    public Result<Map<String, Object>> outline(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(content.outline(id, userId(request), role(request)));
    }

    @PostMapping("/courses/{id}/chapters")
    public Result<CourseChapter> addChapter(@PathVariable Long id, @Valid @RequestBody ChapterRequestVo input,
            HttpServletRequest request) {
        return Result.success(content.addChapter(id, input, userId(request), role(request)));
    }

    @PutMapping("/chapters/{id}")
    public Result<Void> updateChapter(@PathVariable Long id, @Valid @RequestBody ChapterRequestVo input,
            HttpServletRequest request) {
        content.updateChapter(id, input, userId(request), role(request));
        return Result.success("章节已更新");
    }

    @DeleteMapping("/chapters/{id}")
    public Result<Void> deleteChapter(@PathVariable Long id, HttpServletRequest request) {
        content.deleteChapter(id, userId(request), role(request));
        return Result.success("章节已删除");
    }

    @PutMapping("/chapters/{id}/move")
    public Result<CourseChapter> moveChapter(@PathVariable Long id, @Valid @RequestBody MoveDirectionVo input,
            HttpServletRequest request) {
        return Result.success(content.moveChapter(id, input.getDirection(), userId(request), role(request)));
    }

    @GetMapping("/chapters/{id}/videos")
    public Result<Map<String, Object>> videos(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(content.listVideos(id, userId(request), role(request)));
    }

    @PostMapping("/chapters/{id}/videos")
    public Result<Map<String, Object>> addVideo(@PathVariable Long id, @Valid @RequestBody ChapterVideoRequestVo input,
            HttpServletRequest request) {
        return Result.success(content.addVideo(id, input, userId(request), role(request)));
    }

    @PutMapping("/chapter-videos/{id}")
    public Result<Map<String, Object>> updateVideo(@PathVariable Long id, @Valid @RequestBody ChapterVideoUpdateVo input,
            HttpServletRequest request) {
        return Result.success(content.updateVideo(id, input, userId(request), role(request)));
    }

    @DeleteMapping("/chapter-videos/{id}")
    public Result<Void> deleteVideo(@PathVariable Long id, HttpServletRequest request) {
        content.deleteVideo(id, userId(request), role(request));
        return Result.success("视频已从章节移除");
    }

    @PutMapping("/chapter-videos/{id}/move")
    public Result<ChapterVideo> moveVideo(@PathVariable Long id, @Valid @RequestBody MoveDirectionVo input,
            HttpServletRequest request) {
        return Result.success(content.moveVideo(id, input.getDirection(), userId(request), role(request)));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private String role(HttpServletRequest request) {
        return (String) request.getAttribute("userRole");
    }
}
