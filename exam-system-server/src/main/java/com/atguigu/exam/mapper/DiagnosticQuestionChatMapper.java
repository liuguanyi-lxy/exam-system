package com.atguigu.exam.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DiagnosticQuestionChatMapper {
    record Row(long id, Long userId, String requestId, int roundNo, String message, String answer, String status,
               String modelVersion, String promptVersion, String reviewStatus, LocalDateTime createdAt) {}

    record Context(int examRecordId, long questionId, String message, String title, String type,
                   String referenceAnswer, String userAnswer, Integer score, String feedback) {}

    String COLUMNS = """
            id, user_id AS userId, request_id AS requestId, round_no AS roundNo, message, answer, status,
            model_version AS modelVersion, prompt_version AS promptVersion, review_status AS reviewStatus, created_at AS createdAt
            """;

    @Insert("""
            INSERT IGNORE INTO ai_question_chats (user_id, exam_record_id, question_id, request_id, round_no, message)
            VALUES (#{userId}, #{recordId}, #{questionId}, #{requestId}, #{roundNo}, #{message})
            """)
    int insert(@Param("userId") long userId, @Param("recordId") int recordId, @Param("questionId") long questionId,
               @Param("requestId") String requestId, @Param("roundNo") int roundNo, @Param("message") String message);

    @Select("SELECT " + COLUMNS + " FROM ai_question_chats WHERE exam_record_id = #{recordId} AND question_id = #{questionId} AND request_id = #{requestId}")
    Row byRequest(@Param("recordId") int recordId, @Param("questionId") long questionId, @Param("requestId") String requestId);

    @Select("SELECT " + COLUMNS + " FROM ai_question_chats WHERE exam_record_id = #{recordId} AND question_id = #{questionId} ORDER BY round_no, id")
    List<Row> list(@Param("recordId") int recordId, @Param("questionId") long questionId);

    @Select("SELECT COUNT(*) FROM ai_question_chats WHERE exam_record_id = #{recordId} AND question_id = #{questionId}")
    int countRounds(@Param("recordId") int recordId, @Param("questionId") long questionId);

    @Select("SELECT COUNT(*) FROM ai_question_chats WHERE exam_record_id = #{recordId} AND question_id = #{questionId} AND status = 'PENDING'")
    int countPending(@Param("recordId") int recordId, @Param("questionId") long questionId);

    @Select("SELECT MAX(created_at) FROM ai_question_chats WHERE exam_record_id = #{recordId} AND question_id = #{questionId}")
    LocalDateTime latestCreatedAt(@Param("recordId") int recordId, @Param("questionId") long questionId);

    @Select("""
            SELECT COUNT(*) FROM paper_question pq JOIN questions q ON q.id = pq.question_id AND q.is_deleted = 0
            WHERE pq.paper_id = #{paperId} AND pq.question_id = #{questionId} AND pq.is_deleted = 0
            """)
    int countQuestionInPaper(@Param("paperId") int paperId, @Param("questionId") long questionId);

    @Select("""
            SELECT c.id FROM ai_question_chats c JOIN exam_records e ON e.id = c.exam_record_id AND e.is_deleted = 0
            WHERE c.status IN ('PENDING','RUNNING') AND c.available_at <= CURRENT_TIMESTAMP ORDER BY c.id LIMIT 25
            """)
    List<Long> due();

    @Select("""
            SELECT c.exam_record_id AS examRecordId, c.question_id AS questionId, c.message,
                q.title, q.type, qa.answer AS referenceAnswer, a.user_answer AS userAnswer,
                a.score AS score, a.ai_correction AS feedback
            FROM ai_question_chats c
            JOIN paper_question pq ON pq.paper_id = (SELECT exam_id FROM exam_records WHERE id = c.exam_record_id)
              AND pq.question_id = c.question_id AND pq.is_deleted = 0
            JOIN questions q ON q.id = pq.question_id AND q.is_deleted = 0
            LEFT JOIN question_answers qa ON qa.question_id = q.id AND qa.is_deleted = 0
            LEFT JOIN answer_record a ON a.exam_record_id = c.exam_record_id AND a.question_id = c.question_id AND a.is_deleted = 0
            WHERE c.id = #{id}
            ORDER BY pq.id, a.id LIMIT 1
            """)
    Context context(@Param("id") long id);

    @Update("""
            UPDATE ai_question_chats SET status = 'RUNNING', attempts = attempts + 1, lease_token = #{token},
              available_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 2 MINUTE)
            WHERE id = #{id} AND status IN ('PENDING','RUNNING') AND available_at <= CURRENT_TIMESTAMP
            """)
    int claim(@Param("id") long id, @Param("token") String token);

    @Update("""
            UPDATE ai_question_chats SET status = 'READY', answer = #{answer}, model_version = #{model},
              prompt_version = #{prompt}, lease_token = NULL, answered_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND lease_token = #{token} AND status = 'RUNNING'
            """)
    int finish(@Param("id") long id, @Param("token") String token, @Param("answer") String answer,
               @Param("model") String model, @Param("prompt") String prompt);

    @Update("""
            UPDATE ai_question_chats SET status = CASE WHEN attempts >= 3 THEN 'FAILED' ELSE 'PENDING' END,
              available_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE), lease_token = NULL
            WHERE id = #{id} AND lease_token = #{token} AND status = 'RUNNING'
            """)
    int fail(@Param("id") long id, @Param("token") String token);
}
