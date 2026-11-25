package com.atguigu.exam.service;

import com.atguigu.exam.common.ExamStatus;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.DiagnosticReportMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.vo.DiagnosticReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiagnosticReportService {
    private final DiagnosticAccess access;
    private final DiagnosticReportMapper reports;
    private final ExamRecordMapper records;
    private final DiagnosticReportBuilder builder;
    private final RemediationTaskService remediation;
    private final ObjectMapper json;

    public DiagnosticReport.View get(int recordId, Long userId, String role) {
        ExamRecord record = access.requireSubmitted(recordId, userId, role);
        if (!ExamStatus.GRADED.equals(record.getStatus())) return pending("WAITING_FOR_GRADE");
        var row = reports.get(recordId);
        if (row == null) {
            reports.enqueue(recordId);
            return pending("PENDING");
        }
        try {
            var report = row.reportJson() == null ? null : json.readValue(row.reportJson(), DiagnosticReport.class);
            return new DiagnosticReport.View("RUNNING".equals(row.status()) ? "PENDING" : row.status(), report,
                    row.reviewedBy(), row.reviewNote(), row.reviewedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("报告数据无法读取，请联系管理员");
        }
    }

    public void generate(int recordId) {
        String token = UUID.randomUUID().toString();
        if (reports.claim(recordId, token) != 1) return;
        try {
            ExamRecord record = records.selectById(recordId);
            if (record == null || !ExamStatus.GRADED.equals(record.getStatus())) {
                reports.finish(recordId, token, "FAILED", null);
                return;
            }
            var report = builder.build(record, reports.evidence(recordId), reports.tags(record.getExamId()));
            reports.finish(recordId, token, "LIMITED".equals(report.confidence()) ? "DEGRADED" : "READY",
                    json.writeValueAsString(report));
            // 报告已落库后再生成补课任务链；失败只记日志降级，retry 报告即可重建
            try {
                remediation.onReportReady(record, report);
            } catch (Exception taskFailure) {
                log.warn("考试记录 {} 补课任务生成失败（报告已就绪）", recordId, taskFailure);
            }
        } catch (Exception failure) {
            log.warn("考试记录 {} 生成诊断失败，类型 {}", recordId, failure.getClass().getSimpleName());
            reports.fail(recordId, token);
        }
    }

    @Transactional
    public DiagnosticReport.View review(int id, Long userId, String role, String note) {
        if (!"TEACHER".equals(role) && !"ADMIN".equals(role)) throw new ClassScopeException("无权复核诊断");
        access.requireSubmitted(id, userId, role);
        if (note == null || note.isBlank() || note.length() > 1000) throw new IllegalArgumentException("复核意见限 1 至 1000 字");
        if (reports.review(id, userId, note.strip()) == 0) {
            var current = reports.get(id);
            if (current == null || !userId.equals(current.reviewedBy()) || !note.strip().equals(current.reviewNote())) {
                throw new IllegalArgumentException("报告待生成或已有复核意见");
            }
        }
        return get(id, userId, role);
    }

    public DiagnosticReport.View retry(int id, Long userId, String role) {
        access.requireSubmitted(id, userId, role);
        reports.retry(id);
        return get(id, userId, role);
    }

    private DiagnosticReport.View pending(String state) {
        return new DiagnosticReport.View(state, null, null, null, null);
    }
}
