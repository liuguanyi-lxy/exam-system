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
import com.atguigu.exam.service.VideoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningServiceImplTest {
    private static final String SESSION_ID = "2479fdc8-bd08-45f0-9922-9dce58528f43";
    @Mock private ChapterVideoMapper chapterVideoMapper;
    @Mock private CourseChapterMapper courseChapterMapper;
    @Mock private LearningVideoProgressMapper progressMapper;
    @Mock private LearningVideoSessionMapper sessionMapper;
    @Mock private VideoMapper videoMapper;
    @Mock private StudentCourseAccess access;
    @Mock private ExternalVideoPolicy externalPolicy;
    @Mock private VideoService videoService;
    @InjectMocks private LearningServiceImpl service;

    @Test
    void unauthorizedAssignmentDoesNotReadProgressOrUpdateStatistics() {
        setupChapter();
        when(access.requirePublishedAssignment(40L, 1L)).thenThrow(new ClassScopeException());
        assertThrows(ClassScopeException.class, () -> service.heartbeat(1L, 10L, SESSION_ID, 0L, 0, null));
        verifyNoInteractions(progressMapper, sessionMapper, videoMapper, videoService);
    }

    @Test
    void initMustBeZeroAndRetryDoesNotCreateExtraView() {
        setupVideo(0, 100);
        LearningVideoProgress progress = progress("IN_PROGRESS", 0, past(10));
        when(progressMapper.lockProgress(1L, 20L)).thenReturn(progress);
        when(sessionMapper.findSession(50L, SESSION_ID)).thenReturn(null, null, session(0, 0, past(10)));
        assertThrows(IllegalArgumentException.class, () -> service.heartbeat(1L, 10L, SESSION_ID, 1L, 5, null));
        var started = service.heartbeat(1L, 10L, SESSION_ID, 0L, 0, null);
        var retry = service.heartbeat(1L, 10L, SESSION_ID, 0L, 0, null);
        assertEquals(0, started.getWatchedSeconds());
        assertEquals(0, retry.getSessionWatchedSeconds());
        verify(sessionMapper, times(1)).insert(any(LearningVideoSession.class));
        verify(videoService, times(1)).recordVideoView(eq(20L), anyString(), eq(0), isNull());
    }

    @Test
    void localCompletionUsesServerCappedDeltaAndFreezesAfterCompletion() {
        setupVideo(0, 100);
        LearningVideoProgress progress = progress("IN_PROGRESS", 60, past(15));
        learningSession(progress, session(0, 0, past(45)));
        var result = service.heartbeat(1L, 10L, SESSION_ID, 1L, 600, null);
        assertTrue(result.getCompleted());
        assertTrue(result.getWatchedSeconds() >= 70 && result.getWatchedSeconds() < 100);
        Date completedAt = progress.getCompletedAt();
        assertNotNull(completedAt);
        var retry = service.heartbeat(1L, 10L, SESSION_ID, 1L, 600, null);
        var rewatch = service.heartbeat(1L, 10L, SESSION_ID, 2L, 1, null);
        assertEquals(result.getWatchedSeconds(), retry.getWatchedSeconds());
        assertEquals(result.getWatchedSeconds(), rewatch.getWatchedSeconds());
        assertEquals(completedAt, progress.getCompletedAt());
        verify(progressMapper, times(1)).updateById(progress);
    }

    @Test
    void sequenceGapOrWrongChapterDoesNotAdvanceProgress() {
        setupVideo(0, 100);
        var progress = progress("IN_PROGRESS", 0, past(10));
        var session = session(0, 0, past(10));
        learningSession(progress, session);
        assertThrows(IllegalArgumentException.class, () -> service.heartbeat(1L, 10L, SESSION_ID, 2L, 10, null));
        session.setChapterVideoId(98L);
        assertThrows(IllegalArgumentException.class, () -> service.heartbeat(1L, 10L, SESSION_ID, 1L, 10, null));
        verify(progressMapper, never()).updateById(any(LearningVideoProgress.class));
        verify(sessionMapper, never()).updateById(any(LearningVideoSession.class));
    }

    @Test
    void externalRequiresSixtySecondsInTheSamePersonalSessionAndIsIdempotent() {
        setupVideo(1, null);
        var progress = progress("IN_PROGRESS", 60, past(10));
        var session = session(0, 59, past(10));
        learningSession(progress, session);
        assertThrows(IllegalArgumentException.class, () -> service.completeExternal(1L, 10L, SESSION_ID));
        var heartbeat = service.heartbeat(1L, 10L, SESSION_ID, 1L, 1, null);
        assertEquals(60, heartbeat.getSessionWatchedSeconds());
        assertFalse(heartbeat.getCompleted());
        assertTrue(service.completeExternal(1L, 10L, SESSION_ID).getCompleted());
        Date completedAt = progress.getCompletedAt();
        assertTrue(service.completeExternal(1L, 10L, SESSION_ID).getCompleted());
        assertEquals(completedAt, progress.getCompletedAt());
        verify(progressMapper, times(2)).updateById(progress);
    }

    @Test
    void anotherStudentCannotConfirmUsingTheSameClientSessionId() {
        setupVideo(1, null);
        when(progressMapper.lockProgress(2L, 20L)).thenReturn(progress("IN_PROGRESS", 0, past(10)));
        assertThrows(IllegalArgumentException.class, () -> service.completeExternal(2L, 10L, SESSION_ID));
        verify(progressMapper, never()).updateById(any(LearningVideoProgress.class));
        verifyNoInteractions(videoService);
    }

    private void setupChapter() {
        var link = new ChapterVideo();
        link.setId(10L);
        link.setChapterId(30L);
        link.setVideoId(20L);
        link.setCompletionRatio(70);
        when(chapterVideoMapper.selectById(10L)).thenReturn(link);
        var chapter = new CourseChapter();
        chapter.setId(30L);
        chapter.setCourseId(40L);
        when(courseChapterMapper.selectById(30L)).thenReturn(chapter);
    }

    private void setupVideo(int type, Integer duration) {
        setupChapter();
        var video = new Video();
        video.setId(20L);
        video.setSourceType(type);
        video.setDuration(duration);
        video.setStatus(Video.STATUS_PUBLISHED);
        video.setFileUrl("https://video.example.test/watch");
        when(videoMapper.selectById(20L)).thenReturn(video);
    }

    private void learningSession(LearningVideoProgress progress, LearningVideoSession session) {
        when(progressMapper.lockProgress(1L, 20L)).thenReturn(progress);
        when(sessionMapper.findSession(50L, SESSION_ID)).thenReturn(session);
    }

    private LearningVideoProgress progress(String status, int watched, Date signal) {
        var progress = new LearningVideoProgress();
        progress.setId(50L);
        progress.setStatus(status);
        progress.setWatchedSeconds(watched);
        progress.setUpdateTime(signal);
        return progress;
    }

    private LearningVideoSession session(long sequence, int watched, Date signal) {
        var session = new LearningVideoSession();
        session.setId(60L);
        session.setChapterVideoId(10L);
        session.setWatchedSeconds(watched);
        session.setLastSequence(sequence);
        session.setLastSignalAt(signal);
        return session;
    }

    private Date past(long seconds) {
        return Date.from(Instant.now().minusSeconds(seconds));
    }
}
