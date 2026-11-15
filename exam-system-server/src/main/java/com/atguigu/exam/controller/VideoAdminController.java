package com.atguigu.exam.controller;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.Video;
import com.atguigu.exam.service.VideoService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 管理端视频控制器
 * 处理管理员相关的视频功能
 */
@RestController
@RequestMapping("/api/admin/videos")
@RequireRole({"TEACHER", "ADMIN"}) // 视频管理全接口仅教师/管理员
@Tag(name = "视频管理(管理端)", description = "管理端视频相关操作，包括视频管理、审核、统计等功能")
public class VideoAdminController {

    @Autowired
    private VideoService videoService;

    /**
     * 管理端分页获取视频列表
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param status 状态筛选（可选）
     * @param uploaderType 上传者类型筛选（可选）
     * @param keyword 搜索关键字（可选）
     * @return 视频分页数据
     */
    @GetMapping
    @Operation(summary = "获取视频列表(管理端)", description = "管理端分页获取视频列表，支持多条件筛选")
    public Result<IPage<Video>> getVideosForAdmin(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "状态：0-待审核，1-已发布，2-已拒绝，3-已下架") @RequestParam(required = false) Integer status,
            @Parameter(description = "上传者类型：1-用户投稿，2-管理员上传") @RequestParam(required = false) Integer uploaderType,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {
        IPage<Video> result = videoService.getVideosForAdmin(page, size, status, uploaderType, keyword);
        return Result.success(result);
    }

    /**
     * 管理员上传视频
     * @param title 视频标题
     * @param description 视频描述
     * @param categoryId 分类ID
     * @param tags 标签
     * @param uploaderName 上传者名称
     * @param duration 视频时长（秒）
     * @param videoFile 视频文件
     * @param coverFile 封面文件（可选）
     * @return 上传结果
     */
    @PostMapping("/upload")
    @Operation(summary = "管理员上传视频", description = "管理员直接上传视频，无需审核直接发布；支持站内上传与外部平台链接（sourceType=1 时传 externalUrl）两种来源")
    public Result<Map<String, Object>> uploadVideoByAdmin(
            @Parameter(description = "视频标题") @RequestParam String title,
            @Parameter(description = "视频描述") @RequestParam(required = false) String description,
            @Parameter(description = "分类ID") @RequestParam Long categoryId,
            @Parameter(description = "标签") @RequestParam(required = false) String tags,
            @Parameter(description = "上传者名称") @RequestParam String uploaderName,
            @Parameter(description = "视频时长（秒），外链投稿可选") @RequestParam(required = false) Integer duration,
            @Parameter(description = "来源类型：0-站内上传（默认），1-外部链接") @RequestParam(required = false) Integer sourceType,
            @Parameter(description = "外部视频链接，sourceType=1 时必填") @RequestParam(required = false) String externalUrl,
            @Parameter(description = "视频文件，sourceType=0 时必填") @RequestParam(required = false) MultipartFile videoFile,
            @Parameter(description = "封面文件") @RequestParam(required = false) MultipartFile coverFile,
            jakarta.servlet.http.HttpServletRequest request) {

        // 构建视频对象
        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setCategoryId(categoryId);
        video.setTags(tags);
        video.setUploaderName(uploaderName);
        video.setDuration(duration);
        video.setSourceType(sourceType == null ? Video.SOURCE_TYPE_UPLOAD : sourceType);
        if (video.getSourceType() == Video.SOURCE_TYPE_EXTERNAL) {
            video.setFileUrl(externalUrl);
        }

        // 从登录上下文取当前管理员ID
        Long adminId = (Long) request.getAttribute("userId");

        Map<String, Object> result = videoService.uploadVideoByAdmin(video, videoFile, coverFile, adminId);
        return Result.success(result);
    }

    /**
     * 审核视频
     * @param videoId 视频ID
     * @param status 审核状态（1-通过，2-拒绝）
     * @param reason 审核原因（拒绝时必填）
     * @return 操作结果
     */
    @PostMapping("/{videoId}/audit")
    @Operation(summary = "审核视频", description = "管理员审核用户投稿的视频，可以通过或拒绝")
    public Result<Void> auditVideo(
            @Parameter(description = "视频ID") @PathVariable Long videoId,
            @Parameter(description = "审核状态：1-通过，2-拒绝") @RequestParam Integer status,
            @Parameter(description = "审核原因（拒绝时必填）") @RequestParam(required = false) String reason,
            jakarta.servlet.http.HttpServletRequest request) {

        // 从登录上下文取当前管理员ID
        Long adminId = (Long) request.getAttribute("userId");

        videoService.auditVideo(videoId, status, reason, adminId);
        return Result.success(null);
    }

    /**
     * 下架视频
     * @param videoId 视频ID
     * @return 操作结果
     */
    @PostMapping("/{videoId}/offline")
    @Operation(summary = "下架视频", description = "管理员将已发布的视频下架")
    public Result<Void> offlineVideo(
            @Parameter(description = "视频ID") @PathVariable Long videoId,
            jakarta.servlet.http.HttpServletRequest request) {

        // 从登录上下文取当前管理员ID
        Long adminId = (Long) request.getAttribute("userId");

        videoService.offlineVideo(videoId, adminId);
        return Result.success(null);
    }

    /**
     * 删除视频
     * @param videoId 视频ID
     * @return 操作结果
     */
    @DeleteMapping("/{videoId}")
    @Operation(summary = "删除视频", description = "管理员删除视频（危险操作，会删除所有相关数据）")
    public Result<Void> deleteVideo(
            @Parameter(description = "视频ID") @PathVariable Long videoId) {
        
        videoService.deleteVideo(videoId);
        return Result.success(null);
    }

    /**
     * 获取视频统计数据
     * @return 统计数据
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取视频统计", description = "获取视频相关的统计数据，用于仪表板展示")
    public Result<Map<String, Object>> getVideoStatistics() {
        Map<String, Object> statistics = videoService.getVideoStatistics();
        return Result.success(statistics);
    }

    /**
     * 获取视频详细统计数据
     * @param videoId 视频ID
     * @param days 统计天数，默认30天
     * @return 详细统计数据
     */
    @GetMapping("/{videoId}/stats")
    @Operation(summary = "获取视频详细统计", description = "获取指定视频的详细统计数据，包括观看、点赞等趋势")
    public Result<Map<String, Object>> getVideoDetailStats(
            @Parameter(description = "视频ID") @PathVariable Long videoId,
            @Parameter(description = "统计天数，默认30天") @RequestParam(defaultValue = "30") Integer days) {
        
        Map<String, Object> stats = videoService.getVideoDetailStats(videoId, days);
        return Result.success(stats);
    }
} 