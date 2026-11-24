package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.Course;
import com.atguigu.exam.entity.CourseClass;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.CourseClassMapper;
import com.atguigu.exam.mapper.CourseMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseAssignmentService {
    private final CourseMapper courseMapper;
    private final CourseClassMapper courseClassMapper;
    private final ClazzMapper clazzMapper;
    private final ClassMemberMapper classMemberMapper;
    private final ClassScopeService classScopeService;

    public Map<String, Object> getAssignments(Long courseId, Long userId, String role) {
        boolean admin = isAdmin(role);
        Course course = requireCourse(courseMapper.selectById(courseId));
        Set<Long> scope = admin ? Set.of() : Set.copyOf(classScopeService.getTeacherClassIds(userId));
        List<CourseClass> assignments = loadAssignments(courseId, admin, scope);
        if (!admin && !Objects.equals(course.getCreatedBy(), userId) && assignments.isEmpty()) {
            throw new ClassScopeException("无权访问该课程");
        }
        if (assignments.isEmpty()) {
            return Map.of("classIds", List.of(), "classes", List.of());
        }

        List<Long> ids = assignments.stream().map(CourseClass::getClassId).toList();
        Map<Long, Clazz> classes = clazzMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity()));
        if (classes.isEmpty()) {
            return Map.of("classIds", List.of(), "classes", List.of());
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : classMemberMapper.selectMaps(new QueryWrapper<ClassMember>()
                .select("class_id AS classId", "COUNT(*) AS studentCount")
                .in("class_id", classes.keySet()).eq("role_in_class", "STUDENT").groupBy("class_id"))) {
            counts.put(((Number) row.get("classId")).longValue(), ((Number) row.get("studentCount")).longValue());
        }
        List<CourseClass> live = assignments.stream().filter(item -> classes.containsKey(item.getClassId())).toList();
        List<Map<String, Object>> details = live.stream().map(item -> {
            Clazz clazz = classes.get(item.getClassId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("classId", clazz.getId());
            row.put("className", clazz.getName());
            row.put("status", clazz.getStatus());
            row.put("studentCount", counts.getOrDefault(clazz.getId(), 0L));
            row.put("assignedAt", item.getCreateTime());
            return row;
        }).toList();
        return Map.of("classIds", live.stream().map(CourseClass::getClassId).toList(), "classes", details);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Long> replaceAssignments(Long courseId, List<Long> requestedIds, Long userId, String role) {
        if (requestedIds == null || requestedIds.stream().anyMatch(classId -> classId == null || classId <= 0)) {
            throw new IllegalArgumentException("班级 ID 必须为正整数");
        }
        boolean admin = isAdmin(role);
        Course course = requireCourse(courseMapper.lockForAssignment(courseId));
        if (!admin && !Objects.equals(course.getCreatedBy(), userId)) {
            throw new ClassScopeException("无权访问该课程");
        }
        Set<Long> target = new LinkedHashSet<>(requestedIds);
        Set<Long> scope = admin ? Set.of() : Set.copyOf(classScopeService.getTeacherClassIds(userId));
        if (!admin && !scope.containsAll(target)) {
            throw new ClassScopeException();
        }
        if (!target.isEmpty() && clazzMapper.selectBatchIds(target).size() != target.size()) {
            throw new IllegalArgumentException("班级不存在");
        }
        List<CourseClass> assignments = loadAssignments(courseId, admin, scope);
        Set<Long> existing = assignments.stream().map(CourseClass::getClassId).collect(Collectors.toSet());
        List<Long> removed = existing.stream().filter(classId -> !target.contains(classId)).toList();
        if (!removed.isEmpty()) {
            courseClassMapper.delete(new LambdaQueryWrapper<CourseClass>()
                    .eq(CourseClass::getCourseId, courseId).in(CourseClass::getClassId, removed));
        }
        for (Long classId : target) {
            if (!existing.contains(classId)) {
                CourseClass assignment = new CourseClass();
                assignment.setCourseId(courseId);
                assignment.setClassId(classId);
                courseClassMapper.insert(assignment);
            }
        }
        return List.copyOf(target);
    }

    private List<CourseClass> loadAssignments(Long courseId, boolean admin, Set<Long> scope) {
        if (!admin && scope.isEmpty()) return List.of();
        return courseClassMapper.selectList(new LambdaQueryWrapper<CourseClass>()
                .eq(CourseClass::getCourseId, courseId)
                .in(!admin, CourseClass::getClassId, scope)
                .orderByAsc(CourseClass::getClassId));
    }

    private static Course requireCourse(Course course) {
        if (course == null) throw new IllegalArgumentException("课程不存在");
        return course;
    }

    private static boolean isAdmin(String role) {
        if ("ADMIN".equals(role)) return true;
        if ("TEACHER".equals(role)) return false;
        throw new ClassScopeException("无权访问该课程");
    }
}
