package com.atguigu.exam.service;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ExamRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiagnosticAccess {
    private final ExamRecordMapper records;
    private final ClassScopeService scopes;

    public ExamRecord requireSubmitted(int recordId, Long userId, String role) {
        ExamRecord record = records.selectById(recordId);
        if (record == null || userId == null || role == null) throw new ClassScopeException("无权查看该诊断");
        switch (role) {
            case "ADMIN" -> { }
            case "TEACHER" -> ExamRecordScope.requireTeacherRecord(scopes, userId, record);
            case "STUDENT" -> ExamRecordScope.requireStudentOwner(userId, record);
            default -> throw new ClassScopeException("无权查看该诊断");
        }
        if (!ExamStatus.FINISHED.equals(record.getStatus()) && !ExamStatus.GRADED.equals(record.getStatus())) {
            throw new IllegalArgumentException("交卷后可查看诊断");
        }
        return record;
    }
}
