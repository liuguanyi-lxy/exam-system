package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ChapterVideo;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.Course;
import com.atguigu.exam.entity.CourseChapter;
import com.atguigu.exam.entity.CourseClass;
import com.atguigu.exam.entity.LearningVideoProgress;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.entity.Video;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ChapterVideoMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.CourseChapterMapper;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.CourseMapper;
import com.atguigu.exam.mapper.LearningVideoProgressMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.mapper.VideoMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.ExternalVideoPolicy;
import com.atguigu.exam.vo.ChapterLearningVo;
import com.atguigu.exam.vo.CourseAssignmentTimeVo;
import com.atguigu.exam.vo.LearningChapterVo;
import com.atguigu.exam.vo.LearningVideoVo;
import com.atguigu.exam.vo.MyCourseDetailVo;
import com.atguigu.exam.vo.MyCourseItemVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.atguigu.exam.service.impl.LearningVideoRules.*;

@Service
@RequiredArgsConstructor
public class CourseLearningQueryService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.ofHours(8));
    private final CourseMapper courseMapper;
    private final CourseClassMapper assignments;
    private final CourseChapterMapper chapterMapper;
    private final ChapterVideoMapper linkMapper;
    private final LearningVideoProgressMapper progressMapper;
    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final ClazzMapper clazzMapper;
    private final ClassScopeService classScope;
    private final StudentCourseAccess access;
    private final ExternalVideoPolicy externalPolicy;

    public List<MyCourseItemVo> list(Long studentId) {
        List<Long> classIds = classScope.getStudentClassIds(studentId);
        if (classIds.isEmpty()) return List.of();
        List<CourseAssignmentTimeVo> assigned = assignments.selectAssignedCourseTimes(classIds);
        if (assigned.isEmpty()) return List.of();
        Map<Long, CourseAssignmentTimeVo> times = assigned.stream().collect(
                Collectors.toMap(CourseAssignmentTimeVo::getCourseId, Function.identity()));
        List<Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .in(Course::getId, times.keySet()).eq(Course::getStatus, "PUBLISHED")
                .orderByDesc(Course::getCreateTime, Course::getId));
        if (courses.isEmpty()) return List.of();
        var outline = outline(courses.stream().map(Course::getId).toList());
        var progress = progress(List.of(studentId), outline.videoIds()).getOrDefault(studentId, Map.of());
        var teachers = users(courses.stream().map(Course::getCreatedBy).filter(Objects::nonNull).distinct().toList());
        return courses.stream().map(course -> {
            var rows = chapterRows(outline, course.getId(), progress);
            int completed = completedChapters(rows);
            MyCourseItemVo row = new MyCourseItemVo();
            row.setCourseId(course.getId());
            row.setName(course.getName());
            row.setDescription(course.getDescription());
            row.setCoverUrl(course.getCoverUrl());
            row.setTeacherName(displayName(course.getCreatedBy() == null ? null : teachers.get(course.getCreatedBy())));
            row.setChapterTotal(rows.size());
            row.setChapterCompleted(completed);
            row.setProgressPercent(percent(completed, rows.size()));
            row.setAssignedAt(times.get(course.getId()).getAssignedAt());
            return row;
        }).toList();
    }

    public MyCourseDetailVo detail(Long courseId, Long studentId) {
        Course course = access.requirePublishedAssignment(courseId, studentId);
        var outline = outline(List.of(courseId));
        var rows = chapterRows(outline, courseId, progress(List.of(studentId), outline.videoIds()).getOrDefault(studentId, Map.of()));
        int completed = completedChapters(rows);
        MyCourseDetailVo detail = new MyCourseDetailVo();
        detail.setCourseId(courseId);
        detail.setName(course.getName());
        detail.setDescription(course.getDescription());
        detail.setCoverUrl(course.getCoverUrl());
        detail.setTeacherName(displayName(course.getCreatedBy() == null ? null : userMapper.selectById(course.getCreatedBy())));
        detail.setChapterTotal(rows.size());
        detail.setChapterCompleted(completed);
        detail.setProgressPercent(percent(completed, rows.size()));
        detail.setChapters(rows);
        return detail;
    }

    public ChapterLearningVo chapter(Long courseId, Long chapterId, Long studentId) {
        access.requirePublishedAssignment(courseId, studentId);
        CourseChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !Objects.equals(courseId, chapter.getCourseId())) {
            throw new IllegalArgumentException("章节不存在");
        }
        List<ChapterVideo> links = links(List.of(chapterId));
        List<Long> videoIds = links.stream().map(ChapterVideo::getVideoId).distinct().toList();
        var progress = progress(List.of(studentId), videoIds).getOrDefault(studentId, Map.of());
        Map<Long, Video> videos = videoIds.isEmpty() ? Map.of() : videoMapper.selectBatchIds(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, Function.identity()));
        var result = new ChapterLearningVo();
        result.setChapterId(chapterId);
        result.setCourseId(courseId);
        result.setTitle(chapter.getTitle());
        result.setSort(chapter.getSort());
        result.setCompleted(chapterRow(chapter, links, progress).getCompleted());
        result.setVideos(links.stream().map(link -> videoRow(link, videos.get(link.getVideoId()), progress.get(link.getVideoId()))).toList());
        return result;
    }

    public Map<String, Object> classProgress(Long courseId, Long classId, Long userId, String role) {
        boolean admin = "ADMIN".equals(role);
        if (!admin && (!"TEACHER".equals(role) || !classScope.isTeacherOfClass(userId, classId))) {
            throw new ClassScopeException();
        }
        if (!admin && !assignments.exists(new LambdaQueryWrapper<CourseClass>()
                .eq(CourseClass::getCourseId, courseId).eq(CourseClass::getClassId, classId))) {
            throw new IllegalArgumentException("该课程未分配给该班级");
        }
        Course course = courseMapper.selectById(courseId);
        Clazz clazz = clazzMapper.selectById(classId);
        if (course == null || clazz == null) throw new IllegalArgumentException("课程或班级不存在");
        var outline = outline(List.of(courseId));
        Set<Long> studentIds = classScope.getStudentUserIds(List.of(classId));
        var accounts = users(studentIds);
        var progress = progress(accounts.keySet(), outline.videoIds());
        List<CourseChapter> chapters = outline.chapters().getOrDefault(courseId, List.of());
        List<ChapterVideo> links = outline.links().values().stream().flatMap(Collection::stream).toList();
        long requiredTotal = links.stream().filter(this::required).count();
        var students = studentIds.stream().filter(accounts::containsKey).sorted().map(studentId -> {
            User user = accounts.get(studentId);
            var current = progress.getOrDefault(studentId, Map.of());
            int completed = completedChapters(chapterRows(outline, courseId, current));
            Date lastSeen = current.values().stream().map(LearningVideoProgress::getUpdateTime)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", studentId);
            row.put("username", user.getUsername());
            row.put("realName", user.getRealName());
            row.put("completedVideoCount", links.stream().filter(link -> completed(current.get(link.getVideoId()))).count());
            row.put("completedRequiredVideoCount", links.stream().filter(this::required)
                    .filter(link -> completed(current.get(link.getVideoId()))).count());
            row.put("requiredVideoTotal", requiredTotal);
            row.put("completedChapterCount", completed);
            row.put("chapterTotal", chapters.size());
            row.put("progressPercent", percent(completed, chapters.size()));
            row.put("lastLearnTime", lastSeen == null ? null : TIMESTAMP.format(lastSeen.toInstant()));
            return row;
        }).toList();
        return Map.of("courseId", courseId, "courseName", course.getName(), "classId", classId,
                "className", clazz.getName(), "chapterTotal", chapters.size(), "students", students);
    }

    private Outline outline(Collection<Long> courseIds) {
        List<CourseChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                .in(CourseChapter::getCourseId, courseIds).orderByAsc(CourseChapter::getSort, CourseChapter::getId));
        List<ChapterVideo> links = links(chapters.stream().map(CourseChapter::getId).toList());
        return new Outline(chapters.stream().collect(Collectors.groupingBy(CourseChapter::getCourseId)),
                links.stream().collect(Collectors.groupingBy(ChapterVideo::getChapterId)),
                links.stream().map(ChapterVideo::getVideoId).distinct().toList());
    }

    private List<ChapterVideo> links(Collection<Long> chapterIds) {
        if (chapterIds.isEmpty()) return List.of();
        return linkMapper.selectList(new LambdaQueryWrapper<ChapterVideo>()
                .in(ChapterVideo::getChapterId, chapterIds).orderByAsc(ChapterVideo::getSort, ChapterVideo::getId));
    }

    private Map<Long, Map<Long, LearningVideoProgress>> progress(Collection<Long> userIds, Collection<Long> videoIds) {
        if (userIds.isEmpty() || videoIds.isEmpty()) return Map.of();
        return progressMapper.selectList(new LambdaQueryWrapper<LearningVideoProgress>()
                .in(LearningVideoProgress::getUserId, userIds).in(LearningVideoProgress::getVideoId, videoIds))
                .stream().collect(Collectors.groupingBy(LearningVideoProgress::getUserId,
                        Collectors.toMap(LearningVideoProgress::getVideoId, Function.identity())));
    }

    private Map<Long, User> users(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<LearningChapterVo> chapterRows(Outline outline, Long courseId, Map<Long, LearningVideoProgress> progress) {
        return outline.chapters().getOrDefault(courseId, List.of()).stream().map(chapter -> chapterRow(chapter,
                outline.links().getOrDefault(chapter.getId(), List.of()), progress)).toList();
    }

    private LearningChapterVo chapterRow(CourseChapter chapter, List<ChapterVideo> links, Map<Long, LearningVideoProgress> progress) {
        List<ChapterVideo> required = links.stream().filter(this::required).toList();
        var row = new LearningChapterVo();
        row.setChapterId(chapter.getId());
        row.setTitle(chapter.getTitle());
        row.setSort(chapter.getSort());
        row.setVideoCount(links.size());
        row.setRequiredVideoCount(required.size());
        row.setCompletedVideoCount((int) links.stream().filter(link -> completed(progress.get(link.getVideoId()))).count());
        row.setCompleted(required.stream().allMatch(link -> completed(progress.get(link.getVideoId()))));
        return row;
    }

    private LearningVideoVo videoRow(ChapterVideo link, Video video, LearningVideoProgress progress) {
        var row = new LearningVideoVo();
        row.setChapterVideoId(link.getId());
        row.setVideoId(link.getVideoId());
        row.setSort(link.getSort());
        row.setRequired(required(link));
        row.setCompletionRatio(ratio(link));
        row.setCompleted(completed(progress));
        row.setWatchedSeconds(progress == null ? 0 : progress.getWatchedSeconds());
        row.setCompletedAt(progress == null ? null : progress.getCompletedAt());
        row.setTitle(video == null ? "视频已移除" : video.getTitle());
        if (video == null || !Integer.valueOf(Video.STATUS_PUBLISHED).equals(video.getStatus())
                || video.getFileUrl() == null || video.getFileUrl().isBlank()) return row;
        try {
            String url = isExternal(video) ? externalPolicy.validate(video.getFileUrl()) : video.getFileUrl();
            row.setRequiredSeconds(requiredSeconds(link, video));
            row.setSourceType(isExternal(video) ? Video.SOURCE_TYPE_EXTERNAL : Video.SOURCE_TYPE_UPLOAD);
            row.setFileUrl(url);
            row.setCoverUrl(video.getCoverUrl());
            Integer duration = video.getDuration();
            row.setDuration(duration);
            row.setDurationText(duration == null ? null : String.format(Locale.ROOT, "%d:%02d", duration / 60, duration % 60));
        } catch (IllegalArgumentException ignored) {
            return row;
        }
        return row;
    }

    private boolean required(ChapterVideo link) {
        return Boolean.TRUE.equals(link.getRequired());
    }

    private boolean completed(LearningVideoProgress progress) {
        return progress != null && COMPLETED.equals(progress.getStatus());
    }

    private int completedChapters(List<LearningChapterVo> chapters) {
        return (int) chapters.stream().filter(LearningChapterVo::getCompleted).count();
    }

    private int percent(int completed, int total) {
        return total == 0 ? 0 : (int) (100L * completed / total);
    }

    private String displayName(User user) {
        if (user == null) return null;
        return user.getRealName() == null || user.getRealName().isBlank() ? user.getUsername() : user.getRealName();
    }

    private record Outline(Map<Long, List<CourseChapter>> chapters, Map<Long, List<ChapterVideo>> links, List<Long> videoIds) {
    }
}
