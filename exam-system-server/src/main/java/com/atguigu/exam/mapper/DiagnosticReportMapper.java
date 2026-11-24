package com.atguigu.exam.mapper;

import com.atguigu.exam.vo.DiagnosticReport.Evidence;
import com.atguigu.exam.vo.DiagnosticReport.Tag;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DiagnosticReportMapper {
    record Row(String status, String reportJson, Long reviewedBy, String reviewNote, LocalDateTime reviewedAt) {}

    @Insert("""
            INSERT INTO ai_exam_reports (exam_record_id) VALUES (#{id})
            ON DUPLICATE KEY UPDATE exam_record_id = VALUES(exam_record_id)
            """)
    int enqueue(@Param("id") int id);

    @Select("""
            SELECT e.id FROM exam_records e LEFT JOIN ai_exam_reports r ON r.exam_record_id = e.id
            WHERE e.status = '已批阅' AND e.is_deleted = 0 AND r.exam_record_id IS NULL
            ORDER BY e.id LIMIT 25
            """)
    List<Integer> missing();

    @Select("""
            SELECT exam_record_id FROM ai_exam_reports WHERE status IN ('PENDING','RUNNING')
              AND available_at <= CURRENT_TIMESTAMP ORDER BY available_at, exam_record_id LIMIT 25
            """)
    List<Integer> due();

    @Update("""
            UPDATE ai_exam_reports SET status = 'RUNNING', attempts = attempts + 1,
              lease_token = #{token}, available_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)
            WHERE exam_record_id = #{id} AND status IN ('PENDING','RUNNING') AND available_at <= CURRENT_TIMESTAMP
            """)
    int claim(@Param("id") int id, @Param("token") String token);

    @Update("""
            UPDATE ai_exam_reports SET status = #{status}, report_json = #{json}, lease_token = NULL
            WHERE exam_record_id = #{id} AND lease_token = #{token} AND status = 'RUNNING'
            """)
    int finish(@Param("id") int id, @Param("token") String token, @Param("status") String status, @Param("json") String json);

    @Update("""
            UPDATE ai_exam_reports SET status = CASE WHEN attempts >= 3 THEN 'FAILED' ELSE 'PENDING' END,
              available_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE), lease_token = NULL
            WHERE exam_record_id = #{id} AND lease_token = #{token} AND status = 'RUNNING'
            """)
    int fail(@Param("id") int id, @Param("token") String token);

    @Update("""
            UPDATE ai_exam_reports SET status = 'PENDING', attempts = 0,
              available_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE)
            WHERE exam_record_id = #{id} AND status = 'FAILED'
            """)
    int retry(@Param("id") int id);

    @Select("""
            SELECT status, report_json, reviewed_by, review_note, reviewed_at FROM ai_exam_reports WHERE exam_record_id = #{id}
            """)
    Row get(@Param("id") int id);

    @Update("""
            UPDATE ai_exam_reports SET reviewed_by = #{userId}, review_note = #{note}, reviewed_at = CURRENT_TIMESTAMP
            WHERE exam_record_id = #{id} AND status IN ('READY','DEGRADED') AND reviewed_by IS NULL
            """)
    int review(@Param("id") int id, @Param("userId") long userId, @Param("note") String note);

    @Select("""
            SELECT pq.question_id, q.title, q.type, pq.score AS possible, qa.answer AS reference_answer,
                a.user_answer, a.score, a.is_correct AS correct, a.ai_correction AS feedback, a.id AS answer_id,
                CASE WHEN pq.update_time > e.start_time OR q.update_time > e.start_time
                       OR qa.update_time > e.start_time OR e.start_time IS NULL THEN 1 ELSE 0 END AS source_changed
            FROM exam_records e JOIN paper_question pq ON pq.paper_id = e.exam_id
              AND pq.create_time <= e.start_time
              AND (pq.is_deleted = 0 OR pq.update_time > e.start_time)
              LEFT JOIN questions q ON q.id = pq.question_id AND q.is_deleted = 0
              LEFT JOIN question_answers qa ON qa.question_id = q.id AND qa.is_deleted = 0
              LEFT JOIN answer_record a ON a.exam_record_id = e.id AND a.question_id = pq.question_id AND a.is_deleted = 0
            WHERE e.id = #{id} AND e.is_deleted = 0 AND e.status = '已批阅' ORDER BY pq.id, a.id
            """)
    List<Evidence> evidence(@Param("id") int id);

    @Select("""
            SELECT DISTINCT pq.question_id, k.id AS point_id, k.name
            FROM paper_question pq JOIN question_knowledge_points qk ON qk.question_id = pq.question_id
              JOIN knowledge_points k ON k.id = qk.knowledge_point_id
            WHERE pq.paper_id = #{paperId} AND pq.is_deleted = 0 ORDER BY pq.question_id, k.id
            """)
    List<Tag> tags(@Param("paperId") int paperId);
}
