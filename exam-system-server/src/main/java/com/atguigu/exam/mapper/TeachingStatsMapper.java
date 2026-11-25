package com.atguigu.exam.mapper;

import com.atguigu.exam.vo.TeachingStatsVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教学统计只读聚合 Mapper（M6）
 *
 * 全部为班级维度实时聚合 SQL，强制带班级条件：
 * - 学员集合统一用子查询 class_members(class_id=#{classId}, role_in_class='STUDENT') 限定，禁止全表聚合；
 * - 考试记录范围 = user ∈ 班 且 (class_id=#{classId} OR exam_class_id=#{classId})（开考班级快照口径）；
 * - overdue / 窗口结束等时间判定统一在 SQL 内按 DB 时间计算（云端 DB 为 UTC，禁止 JVM now()）；
 * - 行 record 走 arg-name 构造器映射（与 M5 同口径）。
 */
public interface TeachingStatsMapper {

    /** 班级信息 + 学员数（学员须 users 未删除） */
    @Select("""
            SELECT c.id AS classId, c.name AS className,
                   (SELECT COUNT(*) FROM class_members cm
                     JOIN users u ON u.id = cm.user_id AND u.is_deleted = 0
                    WHERE cm.class_id = c.id AND cm.role_in_class = 'STUDENT') AS studentCount
            FROM classes c
            WHERE c.id = #{classId}
            """)
    TeachingStatsVo.ClassInfo classInfo(@Param("classId") long classId);

    /** 近 days 天活跃学员数（考试记录创建 / 视频心跳 / 补课提交，UNION 去重） */
    @Select("""
            SELECT COUNT(DISTINCT uid) FROM (
              SELECT er.user_id AS uid
              FROM exam_records er
              WHERE er.is_deleted = 0
                AND er.create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL #{days} DAY)
                AND er.user_id IN (SELECT user_id FROM class_members
                                    WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
              UNION
              SELECT p.user_id AS uid
              FROM learning_video_sessions s
              JOIN learning_video_progress p ON p.id = s.progress_id
              WHERE s.last_signal_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL #{days} DAY)
                AND p.user_id IN (SELECT user_id FROM class_members
                                   WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
              UNION
              SELECT t.user_id AS uid
              FROM remediation_task_items i
              JOIN remediation_tasks t ON t.id = i.task_id AND t.is_deleted = 0
              WHERE i.create_time >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL #{days} DAY)
                AND t.user_id IN (SELECT user_id FROM class_members
                                   WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
            ) a
            """)
    long activeCount(@Param("classId") long classId, @Param("days") int days);

    /** 班级被分配课程的学习完成率（视频口径：chapter_videos 链接数；完成 = 班内学员 COMPLETED） */
    @Select("""
            SELECT cc.course_id AS courseId, co.name AS courseName,
                   COUNT(DISTINCT cv.id) AS videoTotal,
                   COUNT(DISTINCT CASE WHEN p.status = 'COMPLETED' THEN cv.id END) AS completedTotal
            FROM course_classes cc
            JOIN courses co ON co.id = cc.course_id AND co.is_deleted = 0
            LEFT JOIN course_chapters ch ON ch.course_id = cc.course_id AND ch.is_deleted = 0
            LEFT JOIN chapter_videos cv ON cv.chapter_id = ch.id
            LEFT JOIN learning_video_progress p ON p.video_id = cv.video_id AND p.status = 'COMPLETED'
              AND p.user_id IN (SELECT user_id FROM class_members
                                 WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
            WHERE cc.class_id = #{classId}
            GROUP BY cc.course_id, co.name
            ORDER BY cc.course_id
            """)
    List<TeachingStatsVo.CourseLearningBase> courseLearning(@Param("classId") long classId);

    /** 每场分配考试的参与/均分/通过统计（一次尝试多条记录：人数与通过数按学员去重） */
    @Select("""
            SELECT ec.exam_id AS examId, e.name AS examName, e.pass_score AS passScore,
                   e.total_score AS totalScore,
                   (SELECT COUNT(*) FROM class_members cm
                     JOIN users u ON u.id = cm.user_id AND u.is_deleted = 0
                    WHERE cm.class_id = ec.class_id AND cm.role_in_class = 'STUDENT') AS classStudentCount,
                   COUNT(DISTINCT CASE WHEN er.status != '进行中' THEN er.user_id END) AS participantCount,
                   COUNT(DISTINCT CASE WHEN er.status = '已批阅' THEN er.user_id END) AS gradedCount,
                   AVG(CASE WHEN er.status = '已批阅' THEN er.score END) AS avgScore,
                   (SELECT COUNT(DISTINCT er2.user_id) FROM exam_records er2
                     WHERE er2.exam_id = ec.exam_id AND er2.is_deleted = 0
                       AND er2.status = '已批阅' AND er2.score >= e.pass_score
                       AND (er2.class_id = ec.class_id OR er2.exam_class_id = ec.class_id)) AS passCount
            FROM exam_classes ec
            JOIN exams e ON e.id = ec.exam_id AND e.is_deleted = 0
            LEFT JOIN exam_records er ON er.exam_id = ec.exam_id AND er.is_deleted = 0
              AND (er.class_id = ec.class_id OR er.exam_class_id = ec.class_id)
            WHERE ec.class_id = #{classId} AND ec.status = 'ENABLED'
            GROUP BY ec.id, ec.exam_id, e.name, e.pass_score, e.total_score
            ORDER BY ec.id
            """)
    List<TeachingStatsVo.ExamStatBase> examStats(@Param("classId") long classId);

    /** 多次尝试进步行（已批阅，按 attempt_no 取首/尾分），HAVING 次数>1 */
    @Select("""
            SELECT er.exam_id AS examId, er.user_id AS userId,
                   COALESCE(NULLIF(u.real_name, ''), u.username) AS realName,
                   SUBSTRING_INDEX(GROUP_CONCAT(er.score ORDER BY er.attempt_no ASC, er.id ASC), ',', 1) + 0 AS firstScore,
                   SUBSTRING_INDEX(GROUP_CONCAT(er.score ORDER BY er.attempt_no DESC, er.id DESC), ',', 1) + 0 AS latestScore
            FROM exam_records er
            LEFT JOIN users u ON u.id = er.user_id AND u.is_deleted = 0
            WHERE er.is_deleted = 0 AND er.status = '已批阅' AND er.score IS NOT NULL
              AND er.user_id IN (SELECT user_id FROM class_members
                                  WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
              AND (er.class_id = #{classId} OR er.exam_class_id = #{classId})
            GROUP BY er.exam_id, er.user_id, u.real_name, u.username
            HAVING COUNT(*) > 1
            ORDER BY er.exam_id, er.user_id
            """)
    List<TeachingStatsVo.ProgressBase> examProgress(@Param("classId") long classId);

    /**
     * 分数段分布：每位学员每场考试取最近一次已批阅成绩（GROUP_CONCAT 首位，attempt_no 降序），
     * 按 total_score 归一为百分比后分段（不用窗口函数，保持 MySQL 5.7 兼容）。
     */
    @Select("""
            SELECT examId,
                   CASE WHEN pct >= 90 THEN '90-100'
                        WHEN pct >= 80 THEN '80-89'
                        WHEN pct >= 70 THEN '70-79'
                        WHEN pct >= 60 THEN '60-69'
                        ELSE '0-59' END AS `range`,
                   COUNT(*) AS cnt
            FROM (
              SELECT er.exam_id AS examId,
                     SUBSTRING_INDEX(GROUP_CONCAT(er.score ORDER BY er.attempt_no DESC, er.id DESC), ',', 1)
                       * 100.0 / MAX(e.total_score) AS pct
              FROM exam_records er
              JOIN exams e ON e.id = er.exam_id AND e.is_deleted = 0
              WHERE er.is_deleted = 0 AND er.status = '已批阅' AND er.score IS NOT NULL
                AND e.total_score > 0
                AND er.user_id IN (SELECT user_id FROM class_members
                                    WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
                AND (er.class_id = #{classId} OR er.exam_class_id = #{classId})
              GROUP BY er.exam_id, er.user_id
            ) t
            GROUP BY examId, `range`
            """)
    List<TeachingStatsVo.DistributionRow> examDistribution(@Param("classId") long classId);

    /** 缺考名单：分配考试窗口已结束，班级学员（未删除）无该考试已交卷记录 */
    @Select("""
            SELECT ec.exam_id AS examId, e.name AS examName, cm.user_id AS userId,
                   COALESCE(NULLIF(u.real_name, ''), u.username) AS realName
            FROM exam_classes ec
            JOIN exams e ON e.id = ec.exam_id AND e.is_deleted = 0
            JOIN class_members cm ON cm.class_id = ec.class_id AND cm.role_in_class = 'STUDENT'
            JOIN users u ON u.id = cm.user_id AND u.is_deleted = 0
            LEFT JOIN exam_records er ON er.exam_id = ec.exam_id AND er.user_id = cm.user_id
              AND er.is_deleted = 0 AND er.status != '进行中'
              AND (er.class_id = ec.class_id OR er.exam_class_id = ec.class_id)
            WHERE ec.class_id = #{classId} AND ec.status = 'ENABLED'
              AND ec.end_time IS NOT NULL AND ec.end_time < CURRENT_TIMESTAMP
              AND er.id IS NULL
            ORDER BY ec.exam_id, cm.user_id
            """)
    List<TeachingStatsVo.ExamAbsentee> examAbsentees(@Param("classId") long classId);

    /** 逾期补课任务名单：class_id 归属、IN_PROGRESS 且 due_time 已过（DB 时间口径） */
    @Select("""
            SELECT t.user_id AS userId, COALESCE(NULLIF(u.real_name, ''), u.username) AS realName,
                   kp.name AS knowledgePoint, t.due_time AS dueTime
            FROM remediation_tasks t
            JOIN knowledge_points kp ON kp.id = t.knowledge_point_id
            LEFT JOIN users u ON u.id = t.user_id AND u.is_deleted = 0
            WHERE t.class_id = #{classId} AND t.is_deleted = 0
              AND t.status = 'IN_PROGRESS'
              AND t.due_time IS NOT NULL AND t.due_time < CURRENT_TIMESTAMP
            ORDER BY t.due_time, t.user_id
            """)
    List<TeachingStatsVo.OverdueTask> overdueTasks(@Param("classId") long classId);

    /** 高频错题 TOP 10：班级学员已交卷作答 is_correct=0 按题聚合；附该班作答总次数与追问轮数 */
    @Select("""
            SELECT a.question_id AS questionId, q.title AS content,
                   COUNT(*) AS wrongCount,
                   (SELECT COUNT(*) FROM answer_record a2
                     JOIN exam_records er2 ON er2.id = a2.exam_record_id AND er2.is_deleted = 0
                       AND er2.status != '进行中'
                       AND er2.user_id IN (SELECT user_id FROM class_members
                                            WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
                       AND (er2.class_id = #{classId} OR er2.exam_class_id = #{classId})
                    WHERE a2.is_deleted = 0 AND a2.question_id = a.question_id) AS totalAttempts,
                   (SELECT COUNT(*) FROM ai_question_chats c
                    WHERE c.question_id = a.question_id
                      AND c.user_id IN (SELECT user_id FROM class_members
                                         WHERE class_id = #{classId} AND role_in_class = 'STUDENT')) AS chatCount
            FROM answer_record a
            JOIN exam_records er ON er.id = a.exam_record_id AND er.is_deleted = 0
              AND er.status != '进行中'
              AND er.user_id IN (SELECT user_id FROM class_members
                                  WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
              AND (er.class_id = #{classId} OR er.exam_class_id = #{classId})
            JOIN questions q ON q.id = a.question_id AND q.is_deleted = 0
            WHERE a.is_deleted = 0 AND a.is_correct = 0
            GROUP BY a.question_id, q.title
            ORDER BY wrongCount DESC, a.question_id ASC
            LIMIT 10
            """)
    List<TeachingStatsVo.WrongQuestionBase> topWrongQuestions(@Param("classId") long classId);

    /** 指定题目的知识点名称（高频错题组装用） */
    @Select("""
            <script>
            SELECT qk.question_id AS questionId, kp.name AS name
            FROM question_knowledge_points qk
            JOIN knowledge_points kp ON kp.id = qk.knowledge_point_id
            WHERE qk.question_id IN
            <foreach collection="questionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY qk.question_id, kp.id
            </script>
            """)
    List<TeachingStatsVo.KpName> questionKnowledgePoints(@Param("questionIds") List<Long> questionIds);

    /** 复核差异（报告级）：总数 / 低置信度（confidence ∈ LIMITED,DEGRADED）/ 已复核 / 意见条数 */
    @Select("""
            SELECT COUNT(*) AS reportTotal,
                   COALESCE(SUM(JSON_UNQUOTE(JSON_EXTRACT(r.report_json, '$.confidence'))
                        IN ('LIMITED', 'DEGRADED')), 0) AS lowConfidenceCount,
                   COALESCE(SUM(r.reviewed_by IS NOT NULL), 0) AS reviewedCount,
                   COALESCE(SUM(r.review_note IS NOT NULL AND r.review_note != ''), 0) AS reviewNoteCount
            FROM ai_exam_reports r
            JOIN exam_records er ON er.id = r.exam_record_id AND er.is_deleted = 0
              AND er.user_id IN (SELECT user_id FROM class_members
                                  WHERE class_id = #{classId} AND role_in_class = 'STUDENT')
              AND (er.class_id = #{classId} OR er.exam_class_id = #{classId})
            """)
    TeachingStatsVo.ReviewStats reviewStats(@Param("classId") long classId);

    /** 班级对比行：均分（已批阅记录）/通过率（学员去重，与单班页 examStats 口径一致）/补课完成率，比率 0-100 */
    @Select("""
            <script>
            SELECT cl.id AS classId, cl.name AS className,
                   (SELECT ROUND(AVG(er.score), 1) FROM exam_records er
                     WHERE er.is_deleted = 0 AND er.status = '已批阅'
                       AND er.user_id IN (SELECT m2.user_id FROM class_members m2
                                           WHERE m2.class_id = cl.id AND m2.role_in_class = 'STUDENT')
                       AND (er.class_id = cl.id OR er.exam_class_id = cl.id)) AS avgScore,
                   (SELECT ROUND(COALESCE(
                        (SELECT COUNT(DISTINCT CASE WHEN er2.status = '已批阅' AND er2.score >= e2.pass_score
                                                    THEN er2.user_id END)
                           FROM exam_records er2 JOIN exams e2 ON e2.id = er2.exam_id AND e2.is_deleted = 0
                          WHERE er2.is_deleted = 0
                            AND er2.user_id IN (SELECT m2.user_id FROM class_members m2
                                                 WHERE m2.class_id = cl.id AND m2.role_in_class = 'STUDENT')
                            AND (er2.class_id = cl.id OR er2.exam_class_id = cl.id))
                      / NULLIF((SELECT COUNT(DISTINCT er3.user_id) FROM exam_records er3
                          WHERE er3.is_deleted = 0 AND er3.status = '已批阅'
                            AND er3.user_id IN (SELECT m3.user_id FROM class_members m3
                                                 WHERE m3.class_id = cl.id AND m3.role_in_class = 'STUDENT')
                            AND (er3.class_id = cl.id OR er3.exam_class_id = cl.id)), 0), 0) * 100)
                     AS passRate,
                   (SELECT ROUND(COALESCE(SUM(t.status = 'COMPLETED') / NULLIF(COUNT(*), 0), 0) * 100)
                     FROM remediation_tasks t
                     WHERE t.class_id = cl.id AND t.is_deleted = 0) AS remediationCompletionRate
            FROM classes cl
            WHERE cl.is_deleted = 0
              AND cl.id IN
            <foreach collection="classIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY cl.id
            </script>
            """)
    List<TeachingStatsVo.CompareItem> compareClasses(@Param("classIds") List<Long> classIds);

    /** 全部班级 ID（管理员班级对比用） */
    @Select("""
            SELECT id FROM classes WHERE is_deleted = 0 ORDER BY id
            """)
    List<Long> allClassIds();
}
