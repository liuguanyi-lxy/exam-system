package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ChapterVideo;
import com.atguigu.exam.entity.Course;
import com.atguigu.exam.entity.CourseChapter;
import com.atguigu.exam.entity.CourseClass;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ChapterVideoMapper;
import com.atguigu.exam.mapper.CourseChapterMapper;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.CourseMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.vo.CourseRequestVo;
import com.atguigu.exam.vo.CourseSummaryVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseCatalogService {
    private final CourseMapper courseMapper;
    private final CourseChapterMapper chapterMapper;
    private final ChapterVideoMapper chapterVideoMapper;
    private final CourseClassMapper courseClassMapper;
    private final UserMapper userMapper;
    private final ClassScopeService classScope;

    public Page<CourseSummaryVo> list(int page, int size, String keyword, String status, Long userId, String role) {
        boolean admin = isAdmin(role);
        return courseMapper.pageSummaries(new Page<>(Math.max(1, Math.min(page, 10000)), Math.max(1, Math.min(size, 100))),
                admin, userId, admin ? List.of() : classScope.getTeacherClassIds(userId),
                optionalText(keyword), validStatus(status) ? status : null);
    }

    public CourseSummaryVo create(CourseRequestVo input, Long userId, String role) {
        isAdmin(role);
        Course course = new Course();
        course.setCreatedBy(userId);
        course.setStatus("DRAFT");
        course.setName(input.getName().strip());
        course.setDescription(optionalText(input.getDescription()));
        course.setCoverUrl(optionalText(input.getCoverUrl()));
        courseMapper.insert(course);
        CourseSummaryVo result = new CourseSummaryVo();
        BeanUtils.copyProperties(course, result);
        result.setCreatedAt(course.getCreateTime());
        result.setUpdatedAt(course.getCreateTime());
        User creator = userMapper.selectById(userId);
        if (creator != null) {
            String realName = optionalText(creator.getRealName());
            result.setCreatorName(realName == null ? creator.getUsername() : realName);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long courseId, CourseRequestVo input, Long userId, String role) {
        requireEditable(courseId, userId, role);
        courseMapper.update(new Course(), new LambdaUpdateWrapper<Course>()
                .eq(Course::getId, courseId)
                .set(Course::getName, input.getName().strip())
                .set(Course::getDescription, optionalText(input.getDescription()))
                .set(Course::getCoverUrl, optionalText(input.getCoverUrl())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long courseId, String status, Long userId, String role) {
        if (!validStatus(status)) throw new IllegalArgumentException("参数错误");
        Course course = requireEditable(courseId, userId, role);
        if (!status.equals(course.getStatus())) {
            courseMapper.update(new Course(), new LambdaUpdateWrapper<Course>()
                    .eq(Course::getId, courseId).set(Course::getStatus, status));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long courseId, Long userId, String role) {
        requireEditable(courseId, userId, role);
        List<Long> chapterIds = chapterMapper.selectList(new LambdaQueryWrapper<CourseChapter>()
                .select(CourseChapter::getId).eq(CourseChapter::getCourseId, courseId))
                .stream().map(CourseChapter::getId).toList();
        if (!chapterIds.isEmpty()) {
            chapterVideoMapper.delete(new LambdaQueryWrapper<ChapterVideo>().in(ChapterVideo::getChapterId, chapterIds));
            chapterMapper.delete(new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId));
        }
        courseClassMapper.delete(new LambdaQueryWrapper<CourseClass>().eq(CourseClass::getCourseId, courseId));
        courseMapper.deleteById(courseId);
    }

    public Course requireEditable(Long courseId, Long userId, String role) {
        return requireOwner(courseMapper.lockForAssignment(courseId), userId, role);
    }

    public Course readEditable(Long courseId, Long userId, String role) {
        return requireOwner(courseMapper.selectById(courseId), userId, role);
    }

    private Course requireOwner(Course course, Long userId, String role) {
        boolean admin = isAdmin(role);
        if (course == null) throw new IllegalArgumentException("课程不存在");
        if (!admin && !Objects.equals(course.getCreatedBy(), userId)) {
            throw new ClassScopeException("无权访问该课程");
        }
        return course;
    }

    private boolean isAdmin(String role) {
        if ("ADMIN".equals(role)) return true;
        if ("TEACHER".equals(role)) return false;
        throw new ClassScopeException("无权访问该课程");
    }

    private boolean validStatus(String status) {
        return "DRAFT".equals(status) || "PUBLISHED".equals(status);
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
