package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ChapterVideo;
import com.atguigu.exam.entity.Course;
import com.atguigu.exam.entity.CourseChapter;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.entity.Video;
import com.atguigu.exam.mapper.ChapterVideoMapper;
import com.atguigu.exam.mapper.CourseChapterMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.mapper.VideoMapper;
import com.atguigu.exam.service.ExternalVideoPolicy;
import com.atguigu.exam.vo.ChapterRequestVo;
import com.atguigu.exam.vo.ChapterVideoRequestVo;
import com.atguigu.exam.vo.ChapterVideoUpdateVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class CourseContentService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.ofHours(8));
    private final CourseCatalogService catalog;
    private final CourseChapterMapper chapterMapper;
    private final ChapterVideoMapper linkMapper;
    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final ExternalVideoPolicy externalPolicy;

    public Map<String, Object> outline(Long courseId, Long userId, String role) {
        Course course = catalog.readEditable(courseId, userId, role);
        List<CourseChapter> chapters = chapters(courseId);
        if (chapters.isEmpty()) return Map.of("course", course, "chapters", List.of());
        List<ChapterVideo> links = linkMapper.selectList(new LambdaQueryWrapper<ChapterVideo>()
                .in(ChapterVideo::getChapterId, chapters.stream().map(CourseChapter::getId).toList())
                .orderByAsc(ChapterVideo::getSort, ChapterVideo::getId));
        Map<Long, List<ChapterVideo>> byChapter = links.stream().collect(Collectors.groupingBy(ChapterVideo::getChapterId));
        Map<Long, Video> videos = videos(links);
        return Map.of("course", course, "chapters", chapters.stream().map(chapter -> {
            List<ChapterVideo> items = byChapter.getOrDefault(chapter.getId(), List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", chapter.getId());
            row.put("courseId", courseId);
            row.put("title", chapter.getTitle());
            row.put("description", chapter.getDescription());
            row.put("sort", chapter.getSort());
            row.put("createdAt", formatTime(chapter.getCreateTime()));
            row.put("videoCount", items.size());
            row.put("requiredVideoCount", items.stream().filter(item -> Boolean.TRUE.equals(item.getRequired())).count());
            row.put("videos", items.stream().map(item -> videoRow(item, videos.get(item.getVideoId()))).toList());
            return row;
        }).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public CourseChapter addChapter(Long courseId, ChapterRequestVo input, Long userId, String role) {
        catalog.requireEditable(courseId, userId, role);
        List<CourseChapter> chapters = chapters(courseId);
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(courseId);
        chapter.setTitle(input.getTitle().strip());
        chapter.setDescription(optionalText(input.getDescription()));
        chapter.setSort(chapters.isEmpty() ? 1 : chapters.get(chapters.size() - 1).getSort() + 1);
        chapterMapper.insert(chapter);
        return chapter;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void updateChapter(Long chapterId, ChapterRequestVo input, Long userId, String role) {
        editChapter(chapterId, userId, role);
        chapterMapper.update(new CourseChapter(), new LambdaUpdateWrapper<CourseChapter>()
                .eq(CourseChapter::getId, chapterId).set(CourseChapter::getTitle, input.getTitle().strip())
                .set(CourseChapter::getDescription, optionalText(input.getDescription())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void deleteChapter(Long chapterId, Long userId, String role) {
        editChapter(chapterId, userId, role);
        linkMapper.delete(new LambdaQueryWrapper<ChapterVideo>().eq(ChapterVideo::getChapterId, chapterId));
        chapterMapper.deleteById(chapterId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public CourseChapter moveChapter(Long chapterId, String direction, Long userId, String role) {
        int offset = offset(direction);
        CourseChapter target = editChapter(chapterId, userId, role);
        List<CourseChapter> chapters = chapters(target.getCourseId());
        int index = indexOf(chapters, chapterId, CourseChapter::getId);
        int neighbor = index + offset;
        if (neighbor < 0 || neighbor >= chapters.size()) return target;
        CourseChapter other = chapters.get(neighbor);
        Integer oldSort = target.getSort();
        target.setSort(other.getSort());
        other.setSort(oldSort);
        chapterMapper.updateById(target);
        chapterMapper.updateById(other);
        return target;
    }

    public Map<String, Object> listVideos(Long chapterId, Long userId, String role) {
        CourseChapter chapter = requireChapter(chapterId);
        catalog.readEditable(chapter.getCourseId(), userId, role);
        List<ChapterVideo> items = links(chapterId);
        Map<Long, Video> videos = videos(items);
        return Map.of("chapterVideos", items.stream().map(item -> videoRow(item, videos.get(item.getVideoId()))).toList());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Map<String, Object> addVideo(Long chapterId, ChapterVideoRequestVo input, Long userId, String role) {
        editChapter(chapterId, userId, role);
        List<ChapterVideo> links = links(chapterId);
        Video video = switch (input.getSource()) {
            case "EXIST" -> existingVideo(input.getVideoId());
            case "EXTERNAL" -> createExternalVideo(input, userId);
            default -> throw new IllegalArgumentException("参数错误");
        };
        if (links.stream().anyMatch(link -> Objects.equals(link.getVideoId(), video.getId()))) {
            throw new IllegalArgumentException("该视频已在本章节中");
        }
        ChapterVideo link = new ChapterVideo();
        link.setChapterId(chapterId);
        link.setVideoId(video.getId());
        link.setSort(links.isEmpty() ? 1 : links.get(links.size() - 1).getSort() + 1);
        link.setRequired(!Boolean.FALSE.equals(input.getRequired()));
        link.setCompletionRatio(input.getCompletionRatio() == null ? 70 : input.getCompletionRatio());
        try {
            linkMapper.insert(link);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("该视频已在本章节中");
        }
        return videoRow(link, video);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Map<String, Object> updateVideo(Long linkId, ChapterVideoUpdateVo input, Long userId, String role) {
        ChapterVideo link = editLink(linkId, userId, role);
        link.setRequired(input.getRequired());
        link.setCompletionRatio(input.getCompletionRatio());
        linkMapper.updateById(link);
        return videoRow(link, videoMapper.selectById(link.getVideoId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void deleteVideo(Long linkId, Long userId, String role) {
        editLink(linkId, userId, role);
        linkMapper.deleteById(linkId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ChapterVideo moveVideo(Long linkId, String direction, Long userId, String role) {
        int offset = offset(direction);
        ChapterVideo target = editLink(linkId, userId, role);
        List<ChapterVideo> links = links(target.getChapterId());
        int index = indexOf(links, linkId, ChapterVideo::getId);
        int neighbor = index + offset;
        if (neighbor < 0 || neighbor >= links.size()) return target;
        ChapterVideo other = links.get(neighbor);
        Integer oldSort = target.getSort();
        target.setSort(other.getSort());
        other.setSort(oldSort);
        linkMapper.updateById(target);
        linkMapper.updateById(other);
        return target;
    }

    private CourseChapter editChapter(Long chapterId, Long userId, String role) {
        CourseChapter chapter = requireChapter(chapterId);
        catalog.requireEditable(chapter.getCourseId(), userId, role);
        return requireChapter(chapterId);
    }

    private CourseChapter requireChapter(Long chapterId) {
        CourseChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) throw new IllegalArgumentException("章节不存在");
        return chapter;
    }

    private ChapterVideo editLink(Long linkId, Long userId, String role) {
        ChapterVideo link = requireLink(linkId);
        editChapter(link.getChapterId(), userId, role);
        return requireLink(linkId);
    }

    private ChapterVideo requireLink(Long linkId) {
        ChapterVideo link = linkMapper.selectById(linkId);
        if (link == null) throw new IllegalArgumentException("章节视频不存在");
        return link;
    }

    private List<CourseChapter> chapters(Long courseId) {
        return chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId)
                .orderByAsc(CourseChapter::getSort, CourseChapter::getId));
    }

    private List<ChapterVideo> links(Long chapterId) {
        return linkMapper.selectList(new LambdaQueryWrapper<ChapterVideo>().eq(ChapterVideo::getChapterId, chapterId)
                .orderByAsc(ChapterVideo::getSort, ChapterVideo::getId));
    }

    private Map<Long, Video> videos(List<ChapterVideo> links) {
        List<Long> ids = links.stream().map(ChapterVideo::getVideoId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return videoMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Video::getId, Function.identity()));
    }

    private Video existingVideo(Long id) {
        Video video = id == null ? null : videoMapper.selectById(id);
        if (video == null || !Objects.equals(video.getStatus(), Video.STATUS_PUBLISHED)) {
            throw new IllegalArgumentException("仅可添加已发布的视频");
        }
        if (Objects.equals(video.getSourceType(), Video.SOURCE_TYPE_EXTERNAL)) {
            externalPolicy.validate(video.getFileUrl());
        } else if (video.getDuration() == null || video.getDuration() <= 0) {
            throw new IllegalArgumentException("该视频缺少时长信息，无法自动判定完成，请先在视频中心补充时长");
        }
        return video;
    }

    private Video createExternalVideo(ChapterVideoRequestVo input, Long userId) {
        String title = optionalText(input.getTitle());
        if (title == null) throw new IllegalArgumentException("视频标题不能为空");
        Video video = new Video();
        video.setTitle(title);
        video.setFileUrl(externalPolicy.validate(input.getExternalUrl()));
        video.setSourceType(Video.SOURCE_TYPE_EXTERNAL);
        video.setStatus(Video.STATUS_PUBLISHED);
        video.setUploaderType(Video.UPLOADER_TYPE_ADMIN);
        User instructor = userMapper.selectById(userId);
        String name = instructor == null ? null : optionalText(instructor.getRealName());
        video.setUploaderName(name != null ? name : instructor == null ? "课程教师" : instructor.getUsername());
        video.setViewCount(0L);
        video.setLikeCount(0L);
        videoMapper.insert(video);
        return video;
    }

    private Map<String, Object> videoRow(ChapterVideo link, Video video) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", link.getId());
        row.put("chapterId", link.getChapterId());
        row.put("videoId", link.getVideoId());
        row.put("sort", link.getSort());
        row.put("required", link.getRequired());
        row.put("completionRatio", link.getCompletionRatio());
        row.put("createdAt", formatTime(link.getCreateTime()));
        row.put("title", video == null ? "视频已移除" : video.getTitle());
        row.put("sourceType", video == null ? null : video.getSourceType());
        row.put("fileUrl", video == null ? null : video.getFileUrl());
        row.put("coverUrl", video == null ? null : video.getCoverUrl());
        Integer duration = video == null ? null : video.getDuration();
        row.put("duration", duration);
        row.put("durationText", duration == null ? null : String.format(Locale.ROOT, "%d:%02d", duration / 60, duration % 60));
        return row;
    }

    private int offset(String direction) {
        if ("UP".equals(direction)) return -1;
        if ("DOWN".equals(direction)) return 1;
        throw new IllegalArgumentException("参数错误");
    }

    private <T> int indexOf(List<T> siblings, Long id, Function<T, Long> getId) {
        return IntStream.range(0, siblings.size()).filter(index -> id.equals(getId.apply(siblings.get(index))))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("资源不存在"));
    }

    private String optionalText(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private String formatTime(Date timestamp) {
        return timestamp == null ? null : TIMESTAMP.format(timestamp.toInstant());
    }
}
