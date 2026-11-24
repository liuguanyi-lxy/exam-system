package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.service.ClassScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 班级数据范围层服务实现
 *
 * 三个查询全部落在 class_members 单表：
 * - getTeacherClassIds/getStudentClassIds 走 idx_member_user(user_id)；
 * - getStudentUserIds 走 uk_class_user_role 最左前缀 class_id；
 * - isTeacherOfClass 走唯一索引前缀等值点查。
 * 实现层只用 LambdaQueryWrapper，无自定义 SQL；返回值按 ID 升序保证测试可断言。
 */
@Slf4j
@Service
public class ClassScopeServiceImpl implements ClassScopeService {

    /** 班内角色：教师（数据范围判定的依据） */
    public static final String ROLE_TEACHER = "TEACHER";
    /** 班内角色：学员 */
    public static final String ROLE_STUDENT = "STUDENT";

    @Autowired
    private ClassMemberMapper classMemberMapper;

    @Override
    public List<Long> getTeacherClassIds(Long userId) {
        return classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                        .eq(ClassMember::getUserId, userId)
                        .eq(ClassMember::getRoleInClass, ROLE_TEACHER)
                        .orderByAsc(ClassMember::getClassId))
                .stream().map(ClassMember::getClassId).toList();
    }

    @Override
    public List<Long> getStudentClassIds(Long userId) {
        return classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                        .eq(ClassMember::getUserId, userId)
                        .eq(ClassMember::getRoleInClass, ROLE_STUDENT)
                        .orderByAsc(ClassMember::getClassId))
                .stream().map(ClassMember::getClassId).toList();
    }

    @Override
    public boolean isTeacherOfClass(Long userId, Long classId) {
        return classMemberMapper.exists(new LambdaQueryWrapper<ClassMember>()
                .eq(ClassMember::getUserId, userId)
                .eq(ClassMember::getRoleInClass, ROLE_TEACHER)
                .eq(ClassMember::getClassId, classId));
    }

    @Override
    public Set<Long> getStudentUserIds(Collection<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            // 空集合不发起查询：调用方拿到的空集合必须走哨兵防呆，绝不退化为全量
            return Collections.emptySet();
        }
        // TreeSet 保证 user_id 升序去重（一人多班只计一次）
        Set<Long> userIds = new TreeSet<>();
        for (ClassMember member : classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                .in(ClassMember::getClassId, classIds)
                .eq(ClassMember::getRoleInClass, ROLE_STUDENT))) {
            userIds.add(member.getUserId());
        }
        return userIds;
    }
}
