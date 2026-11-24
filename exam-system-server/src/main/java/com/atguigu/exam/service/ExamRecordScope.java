package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.exception.ClassScopeException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.List;
import java.util.Set;

public record ExamRecordScope(List<Long> classIds, Set<Long> legacyStudentIds) {
    public static ExamRecordScope teacher(ClassScopeService scopes, Long teacherId) {
        if (teacherId == null) return new ExamRecordScope(List.of(), Set.of());
        List<Long> classes = scopes.getTeacherClassIds(teacherId);
        return new ExamRecordScope(classes, scopes.getStudentUserIds(classes));
    }

    public static void requireTeacherRecord(ClassScopeService scopes, Long teacherId, ExamRecord record) {
        boolean allowed = teacherId != null && (record.getClassId() != null
                ? scopes.isTeacherOfClass(teacherId, record.getClassId())
                : record.getUserId() != null && teacher(scopes, teacherId).legacyStudentIds().contains(record.getUserId()));
        if (!allowed) throw new ClassScopeException("无权访问该考试记录");
    }

    public static void requireStudentOwner(Long userId, ExamRecord record) {
        if (userId == null || !userId.equals(record.getUserId())) {
            throw new ClassScopeException("无权操作该考试记录");
        }
    }

    public void filter(LambdaQueryWrapper<ExamRecord> query) {
        if (classIds.isEmpty()) {
            query.eq(ExamRecord::getUserId, -1L);
            return;
        }
        query.and(scoped -> scoped.in(ExamRecord::getClassId, classIds).or(!legacyStudentIds.isEmpty(),
                legacy -> legacy.isNull(ExamRecord::getClassId).in(ExamRecord::getUserId, legacyStudentIds)));
    }

    public void filter(QueryWrapper<ExamRecord> query) {
        if (classIds.isEmpty()) {
            query.eq("user_id", -1L);
            return;
        }
        query.and(scoped -> scoped.in("class_id", classIds).or(!legacyStudentIds.isEmpty(),
                legacy -> legacy.isNull("class_id").in("user_id", legacyStudentIds)));
    }
}
