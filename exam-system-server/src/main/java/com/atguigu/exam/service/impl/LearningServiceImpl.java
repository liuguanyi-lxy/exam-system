package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ChapterVideo;
import com.atguigu.exam.entity.CourseChapter;
import com.atguigu.exam.entity.LearningVideoProgress;
import com.atguigu.exam.entity.LearningVideoSession;
import com.atguigu.exam.entity.Video;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ChapterVideoMapper;
import com.atguigu.exam.mapper.CourseChapterMapper;
import com.atguigu.exam.mapper.LearningVideoProgressMapper;
import com.atguigu.exam.mapper.LearningVideoSessionMapper;
import com.atguigu.exam.mapper.VideoMapper;
import com.atguigu.exam.service.ExternalVideoPolicy;
import com.atguigu.exam.service.LearningService;
import com.atguigu.exam.service.VideoService;
import com.atguigu.exam.vo.LearningHeartbeatResultVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.atguigu.exam.service.impl.LearningVideoRules.*;

@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements LearningService {
    private static final int DELTA_CAP_SECONDS = 60;
    private final ChapterVideoMapper chapterVideoMapper;
    private final CourseChapterMapper courseChapterMapper;
    private final LearningVideoProgressMapper progressMapper;
    private final LearningVideoSessionMapper sessionMapper;
    private final VideoMapper videoMapper;
    private final StudentCourseAccess access;
    private final ExternalVideoPolicy externalPolicy;
    private final VideoService videoService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public LearningHeartbeatResultVo heartbeat(Long userId, Long chapterVideoId, String sessionId,
                                               Long sequence, Integer viewDuration, HttpServletRequest request) {
        sessionId = normalizeSessionId(sessionId);
        if (sequence == null || sequence < 0 || viewDuration == null || viewDuration < 0) {
            throw new IllegalArgumentException("心跳参数不正确");
        }
        ChapterVideo chapterVideo = requireAccessibleChapterVideo(userId, chapterVideoId);
        Video video = requirePublishedVideo(chapterVideo.getVideoId());
        int threshold = requiredSeconds(chapterVideo, video);
        if (sequence == 0) progressMapper.ensureProgress(userId, video.getId(), chapterVideoId);
        LearningVideoProgress progress = progressMapper.lockProgress(userId, video.getId());
        if (progress == null) throw new IllegalArgumentException("请先开始学习会话");
        LearningVideoSession session = sessionMapper.findSession(progress.getId(), sessionId);
        if (session != null && !Objects.equals(session.getChapterVideoId(), chapterVideoId)) {
            throw new IllegalArgumentException("学习会话不属于当前视频");
        }
        Date now = new Date();
        boolean justCompleted = false;
        if (session == null) {
            if (sequence != 0 || viewDuration != 0) throw new IllegalArgumentException("请先开始学习会话");
            session = new LearningVideoSession();
            session.setProgressId(progress.getId());
            session.setChapterVideoId(chapterVideoId);
            session.setSessionId(sessionId);
            session.setLastSequence(0L);
            session.setWatchedSeconds(0);
            session.setLastSignalAt(now);
            sessionMapper.insert(session);
            videoService.recordVideoView(video.getId(), statisticsId(progress.getId(), sessionId), 0, request);
        } else if (sequence > session.getLastSequence()) {
            if (sequence - session.getLastSequence() != 1) throw new IllegalArgumentException("心跳序号不连续");
            int effective = Math.min(viewDuration, elapsedSeconds(session.getLastSignalAt(), now));
            if (!isExternal(video)) {
                effective = Math.min(effective, elapsedSeconds(progress.getUpdateTime(), now));
            }
            int sessionLimit = isExternal(video) ? EXTERNAL_SECONDS : Integer.MAX_VALUE;
            session.setWatchedSeconds(addCapped(session.getWatchedSeconds(), effective, sessionLimit));
            session.setLastSequence(sequence);
            session.setLastSignalAt(now);
            sessionMapper.updateById(session);
            videoService.recordVideoView(video.getId(), statisticsId(progress.getId(), sessionId), effective, request);
            if (!COMPLETED.equals(progress.getStatus())) {
                int watched = isExternal(video) ? Math.max(safeInt(progress.getWatchedSeconds()), session.getWatchedSeconds())
                        : addCapped(progress.getWatchedSeconds(), effective, Integer.MAX_VALUE);
                progress.setWatchedSeconds(watched);
                if (!isExternal(video) && watched >= threshold) {
                    markComplete(progress, now);
                    justCompleted = true;
                }
                progress.setUpdateTime(now);
                progressMapper.updateById(progress);
            }
        }
        return toResult(userId, chapterVideo, video, progress, session, justCompleted);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public LearningHeartbeatResultVo completeExternal(Long userId, Long chapterVideoId, String sessionId) {
        sessionId = normalizeSessionId(sessionId);
        ChapterVideo chapterVideo = requireAccessibleChapterVideo(userId, chapterVideoId);
        Video video = requirePublishedVideo(chapterVideo.getVideoId());
        if (!isExternal(video)) throw new IllegalArgumentException("站内视频无需手动确认完成");
        LearningVideoProgress progress = progressMapper.lockProgress(userId, video.getId());
        LearningVideoSession session = progress == null ? null : sessionMapper.findSession(progress.getId(), sessionId);
        if (session == null || !Objects.equals(session.getChapterVideoId(), chapterVideoId)) {
            throw new IllegalArgumentException("请先打开视频后再确认完成");
        }
        boolean justCompleted = false;
        if (!COMPLETED.equals(progress.getStatus())) {
            int remaining = EXTERNAL_SECONDS - safeInt(session.getWatchedSeconds());
            if (remaining > 0) {
                throw new IllegalArgumentException("请继续停留满 60 秒后再确认完成（还差 " + remaining + " 秒）");
            }
            Date now = new Date();
            markComplete(progress, now);
            progress.setUpdateTime(now);
            progressMapper.updateById(progress);
            justCompleted = true;
        }
        return toResult(userId, chapterVideo, video, progress, session, justCompleted);
    }

    private ChapterVideo requireAccessibleChapterVideo(Long userId, Long chapterVideoId) {
        ChapterVideo chapterVideo = chapterVideoMapper.selectById(chapterVideoId);
        if (chapterVideo == null) throw new ClassScopeException("课程不存在或未开放");
        CourseChapter chapter = courseChapterMapper.selectById(chapterVideo.getChapterId());
        if (chapter == null) throw new ClassScopeException("课程不存在或未开放");
        access.requirePublishedAssignment(chapter.getCourseId(), userId);
        return chapterVideo;
    }

    private Video requirePublishedVideo(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || video.getStatus() == null || video.getStatus() != Video.STATUS_PUBLISHED
                || video.getFileUrl() == null || video.getFileUrl().isBlank()) {
            throw new IllegalArgumentException("视频不存在或未发布");
        }
        if (isExternal(video)) externalPolicy.validate(video.getFileUrl());
        return video;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || !sessionId.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("学习会话参数不正确");
        }
        return UUID.fromString(sessionId).toString();
    }

    private String statisticsId(Long progressId, String sessionId) {
        return UUID.nameUUIDFromBytes(("learning:" + progressId + ":" + sessionId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private int elapsedSeconds(Date previous, Date now) {
        if (previous == null) return 0;
        return (int) Math.max(0, Math.min(DELTA_CAP_SECONDS, (now.getTime() - previous.getTime()) / 1000));
    }

    private int addCapped(Integer current, int delta, int limit) {
        return (int) Math.min(limit, (long) safeInt(current) + delta);
    }

    private void markComplete(LearningVideoProgress progress, Date now) {
        progress.setStatus(COMPLETED);
        progress.setCompletedAt(now);
    }

    private boolean isChapterCompleted(Long userId, Long chapterId) {
        List<ChapterVideo> requiredVideos = chapterVideoMapper.selectList(new LambdaQueryWrapper<ChapterVideo>()
                .eq(ChapterVideo::getChapterId, chapterId)
                .eq(ChapterVideo::getRequired, true));
        if (requiredVideos.isEmpty()) return true;
        List<Long> videoIds = requiredVideos.stream().map(ChapterVideo::getVideoId).distinct().toList();
        List<LearningVideoProgress> completed = progressMapper.selectList(new LambdaQueryWrapper<LearningVideoProgress>()
                .eq(LearningVideoProgress::getUserId, userId)
                .eq(LearningVideoProgress::getStatus, COMPLETED)
                .in(LearningVideoProgress::getVideoId, videoIds));
        var completedIds = completed.stream().map(LearningVideoProgress::getVideoId).collect(java.util.stream.Collectors.toSet());
        return videoIds.stream().allMatch(completedIds::contains);
    }

    private LearningHeartbeatResultVo toResult(Long userId, ChapterVideo chapterVideo, Video video,
                                                LearningVideoProgress progress, LearningVideoSession session,
                                                boolean justCompleted) {
        boolean chapterCompleted = isChapterCompleted(userId, chapterVideo.getChapterId());
        LearningHeartbeatResultVo result = new LearningHeartbeatResultVo();
        result.setChapterVideoId(chapterVideo.getId());
        result.setVideoId(video.getId());
        result.setSourceType(isExternal(video) ? Video.SOURCE_TYPE_EXTERNAL : Video.SOURCE_TYPE_UPLOAD);
        result.setRequired(Boolean.TRUE.equals(chapterVideo.getRequired()));
        result.setCompletionRatio(ratio(chapterVideo));
        result.setWatchedSeconds(safeInt(progress.getWatchedSeconds()));
        result.setSessionWatchedSeconds(safeInt(session.getWatchedSeconds()));
        result.setRequiredSeconds(requiredSeconds(chapterVideo, video));
        result.setCompleted(COMPLETED.equals(progress.getStatus()));
        result.setChapterCompleted(chapterCompleted);
        result.setChapterCompletedAfterThisVideo(justCompleted && chapterCompleted && Boolean.TRUE.equals(chapterVideo.getRequired()));
        return result;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}
