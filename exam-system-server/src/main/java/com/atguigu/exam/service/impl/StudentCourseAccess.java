package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Course;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentCourseAccess {
    private final CourseMapper courseMapper;
    private final CourseClassMapper assignments;

    public Course requirePublishedAssignment(Long courseId, Long studentId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || !"PUBLISHED".equals(course.getStatus())
                || !assignments.hasActiveStudentAssignment(courseId, studentId)) {
            throw new ClassScopeException("课程不存在或未开放");
        }
        return course;
    }
}
