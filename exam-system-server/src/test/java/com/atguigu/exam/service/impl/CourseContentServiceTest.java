package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ChapterVideo;
import com.atguigu.exam.entity.Course;
import com.atguigu.exam.entity.CourseChapter;
import com.atguigu.exam.entity.Video;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ChapterVideoMapper;
import com.atguigu.exam.mapper.CourseChapterMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.mapper.VideoMapper;
import com.atguigu.exam.service.ExternalVideoPolicy;
import com.atguigu.exam.vo.ChapterRequestVo;
import com.atguigu.exam.vo.ChapterVideoRequestVo;
import com.atguigu.exam.vo.ChapterVideoUpdateVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseContentServiceTest {
    @Mock private CourseCatalogService catalog;
    @Mock private CourseChapterMapper chapterMapper;
    @Mock private ChapterVideoMapper linkMapper;
    @Mock private VideoMapper videoMapper;
    @Mock private UserMapper userMapper;
    @Mock private ExternalVideoPolicy externalPolicy;
    @InjectMocks private CourseContentService service;

    @Test
    void outlineLoadsLinkedVideosInOneBatchAndRetainsUnavailableEntries() {
        when(catalog.readEditable(9L, 7L, "TEACHER")).thenReturn(new Course());
        when(chapterMapper.selectList(any())).thenReturn(List.of(chapter(10L, 1), chapter(11L, 2)));
        when(linkMapper.selectList(any())).thenReturn(List.of(link(20L, 10L, 30L, 1), link(21L, 11L, 31L, 1)));
        when(videoMapper.selectBatchIds(List.of(30L, 31L))).thenReturn(List.of(localVideo(30L, 60)));
        var result = service.outline(9L, 7L, "TEACHER");
        List<?> chapters = (List<?>) result.get("chapters");
        assertEquals(2, chapters.size());
        var rows = (List<?>) ((Map<?, ?>) chapters.get(1)).get("videos");
        assertEquals("视频已移除", ((Map<?, ?>) rows.get(0)).get("title"));
        verify(videoMapper).selectBatchIds(List.of(30L, 31L));
        verifyNoMoreInteractions(videoMapper);
    }

    @Test
    void outlineDeniesUnauthorizedCourseBeforeReadingChapters() {
        when(catalog.readEditable(9L, 7L, "TEACHER")).thenThrow(new ClassScopeException());
        assertThrows(ClassScopeException.class, () -> service.outline(9L, 7L, "TEACHER"));
        verifyNoInteractions(chapterMapper, linkMapper, videoMapper);
    }

    @Test
    void chapterAppendsAndSwapsOnlyWithNeighbor() {
        CourseChapter first = chapter(10L, 2);
        CourseChapter second = chapter(11L, 5);
        when(chapterMapper.selectList(any())).thenReturn(List.of(first, second));
        when(chapterMapper.selectById(11L)).thenReturn(second);
        ChapterRequestVo input = new ChapterRequestVo();
        input.setTitle(" 新课 ");
        var added = service.addChapter(9L, input, 7L, "TEACHER");
        assertEquals("新课", added.getTitle());
        assertEquals(6, added.getSort());
        assertEquals(5, service.moveChapter(11L, "DOWN", 7L, "TEACHER").getSort());
        verify(chapterMapper, never()).updateById(any(CourseChapter.class));
        assertEquals(2, service.moveChapter(11L, "UP", 7L, "TEACHER").getSort());
        assertEquals(5, first.getSort());
        verify(chapterMapper, times(2)).updateById(any(CourseChapter.class));
    }

    @Test
    void rejectsMissingDurationAndDuplicateBeforeWritingLink() {
        when(chapterMapper.selectById(10L)).thenReturn(chapter(10L, 1));
        when(linkMapper.selectList(any())).thenReturn(List.of(link(20L, 10L, 30L, 1)));
        when(videoMapper.selectById(30L)).thenReturn(localVideo(30L, 60));
        when(videoMapper.selectById(31L)).thenReturn(localVideo(31L, null));
        assertThrows(IllegalArgumentException.class, () -> service.addVideo(10L, existing(30L), 7L, "TEACHER"));
        assertThrows(IllegalArgumentException.class, () -> service.addVideo(10L, existing(31L), 7L, "TEACHER"));
        verify(linkMapper, never()).insert(any(ChapterVideo.class));
    }

    @Test
    void videoMutationChecksParentBeforeWritesAndRemovalNeverDeletesSharedVideo() {
        var videoLink = link(20L, 10L, 30L, 1);
        when(linkMapper.selectById(20L)).thenReturn(videoLink);
        when(chapterMapper.selectById(10L)).thenReturn(chapter(10L, 1));
        doThrow(new ClassScopeException()).when(catalog).requireEditable(9L, 8L, "TEACHER");
        assertThrows(ClassScopeException.class, () -> service.deleteVideo(20L, 8L, "TEACHER"));
        verify(linkMapper, never()).deleteById(anyLong());
        service.deleteVideo(20L, 7L, "TEACHER");
        verify(linkMapper).deleteById(20L);
        when(videoMapper.selectById(30L)).thenReturn(localVideo(30L, 60));
        var result = service.addVideo(10L, existing(30L), 7L, "TEACHER");
        assertEquals(true, result.get("required"));
        assertEquals(70, result.get("completionRatio"));
        verify(videoMapper, never()).deleteById(anyLong());
    }

    @Test
    void updatesOnlyChapterConfiguration() {
        when(linkMapper.selectById(20L)).thenReturn(link(20L, 10L, 30L, 1));
        when(chapterMapper.selectById(10L)).thenReturn(chapter(10L, 1));
        when(videoMapper.selectById(30L)).thenReturn(localVideo(30L, 60));
        var input = new ChapterVideoUpdateVo();
        input.setRequired(false);
        input.setCompletionRatio(88);
        var result = service.updateVideo(20L, input, 7L, "TEACHER");
        assertEquals(false, result.get("required"));
        assertEquals(88, result.get("completionRatio"));
        verify(videoMapper).selectById(30L);
        verifyNoMoreInteractions(videoMapper);
    }

    @Test
    void externalPolicyIsAppliedToNewAndExistingLinks() {
        when(chapterMapper.selectById(10L)).thenReturn(chapter(10L, 1));
        var input = new ChapterVideoRequestVo();
        input.setSource("EXTERNAL");
        input.setTitle("课程导读");
        input.setExternalUrl("https://video.example.test/watch");
        when(externalPolicy.validate(input.getExternalUrl())).thenReturn(input.getExternalUrl());
        when(videoMapper.insert(any(Video.class))).thenAnswer(call -> {
            Video video = call.getArgument(0);
            video.setId(31L);
            return 1;
        });
        var result = service.addVideo(10L, input, 7L, "TEACHER");
        assertEquals(31L, result.get("videoId"));
        assertEquals(Video.SOURCE_TYPE_EXTERNAL, result.get("sourceType"));
        Video existing = localVideo(30L, null);
        existing.setSourceType(Video.SOURCE_TYPE_EXTERNAL);
        existing.setFileUrl("https://other.example.test/watch");
        when(videoMapper.selectById(30L)).thenReturn(existing);
        when(externalPolicy.validate(existing.getFileUrl())).thenThrow(new IllegalArgumentException("不支持"));
        assertThrows(IllegalArgumentException.class, () -> service.addVideo(10L, existing(30L), 7L, "TEACHER"));
        verify(linkMapper, times(1)).insert(any(ChapterVideo.class));
    }

    private CourseChapter chapter(Long id, int sort) {
        var chapter = new CourseChapter();
        chapter.setId(id);
        chapter.setCourseId(9L);
        chapter.setSort(sort);
        return chapter;
    }

    private ChapterVideo link(Long id, Long chapterId, Long videoId, int sort) {
        var link = new ChapterVideo();
        link.setId(id);
        link.setChapterId(chapterId);
        link.setVideoId(videoId);
        link.setSort(sort);
        link.setRequired(true);
        link.setCompletionRatio(70);
        return link;
    }

    private Video localVideo(Long id, Integer duration) {
        var video = new Video();
        video.setId(id);
        video.setTitle("站内课程视频");
        video.setStatus(Video.STATUS_PUBLISHED);
        video.setSourceType(Video.SOURCE_TYPE_UPLOAD);
        video.setDuration(duration);
        return video;
    }

    private ChapterVideoRequestVo existing(Long videoId) {
        var input = new ChapterVideoRequestVo();
        input.setVideoId(videoId);
        input.setSource("EXIST");
        return input;
    }
}
