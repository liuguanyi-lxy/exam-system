package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.RemediationTask;
import com.atguigu.exam.vo.RemediationTaskView;
import com.atguigu.exam.vo.RemediationTeacherView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 补课任务链 Mapper（M5）
 * 任务生成（幂等唯一键）、学员任务查询与教师概览共用
 */
public interface RemediationTaskMapper extends BaseMapper<RemediationTask> {

    /**
     * 幂等插入任务链：唯一键 uk_task_user_kp_record(user_id, knowledge_point_id, exam_record_id)
     * 兜底并发双击与报告 retry 重算，已存在则不重建（沿用 M4 追问 INSERT IGNORE 先例）。
     * due_time 在 SQL 内用 CURRENT_TIMESTAMP 推算（云端 DB 为 UTC，禁止 JVM 侧 now() 比较）。
     */
    @Insert("""
            INSERT IGNORE INTO remediation_tasks
              (exam_record_id, user_id, class_id, knowledge_point_id, status, config_snapshot,
               due_time, create_time, update_time)
            VALUES (#{recordId}, #{userId}, #{classId,jdbcType=BIGINT}, #{pointId}, 'IN_PROGRESS',
                    #{snapshot}, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{dueDays} DAY),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insertIgnore(@Param("recordId") long recordId, @Param("userId") long userId,
                     @Param("classId") Long classId, @Param("pointId") long pointId,
                     @Param("snapshot") String snapshot, @Param("dueDays") int dueDays);

    /** 学员任务列表：JOIN 知识点名 + 掌握度；overdue 在 SQL 内按 DB 时间计算（UTC 口径，禁止 JVM now()） */
    @Select("""
            SELECT t.id, t.knowledge_point_id, kp.name AS knowledge_point_name, t.status,
                   m.total AS mastery_total, m.pending_retest, t.due_time,
                   (t.due_time IS NOT NULL AND CURRENT_TIMESTAMP > t.due_time
                    AND t.status = 'IN_PROGRESS') AS overdue
            FROM remediation_tasks t
            JOIN knowledge_points kp ON kp.id = t.knowledge_point_id
            LEFT JOIN mastery_scores m ON m.user_id = t.user_id
              AND m.knowledge_point_id = t.knowledge_point_id AND m.is_deleted = 0
            WHERE t.is_deleted = 0 AND t.user_id = #{userId}
              AND (#{status} IS NULL OR t.status = #{status})
            ORDER BY t.create_time DESC, t.id DESC
            """)
    List<RemediationTaskView.Summary> listForUser(@Param("userId") long userId, @Param("status") String status);

    /** 单任务摘要（详情接口复用同一 JOIN 口径，保证 overdue 判定一致） */
    @Select("""
            SELECT t.id, t.knowledge_point_id, kp.name AS knowledge_point_name, t.status,
                   m.total AS mastery_total, m.pending_retest, t.due_time,
                   (t.due_time IS NOT NULL AND CURRENT_TIMESTAMP > t.due_time
                    AND t.status = 'IN_PROGRESS') AS overdue
            FROM remediation_tasks t
            JOIN knowledge_points kp ON kp.id = t.knowledge_point_id
            LEFT JOIN mastery_scores m ON m.user_id = t.user_id
              AND m.knowledge_point_id = t.knowledge_point_id AND m.is_deleted = 0
            WHERE t.is_deleted = 0 AND t.id = #{taskId}
            """)
    RemediationTaskView.Summary getSummaryById(@Param("taskId") long taskId);

    /** 任务完成判定（幂等）：仅 IN_PROGRESS 可流转到 COMPLETED，重复调用/已完成任务更新 0 行 */
    @Update("""
            UPDATE remediation_tasks
            SET status = 'COMPLETED', update_time = CURRENT_TIMESTAMP
            WHERE id = #{taskId} AND status = 'IN_PROGRESS' AND is_deleted = 0
            """)
    int markCompleted(@Param("taskId") long taskId);

    // ========== 教师侧班级进度概览（M5 第 5 步） ==========

    /**
     * 班级学员任务明细（GROUP BY user_id）：姓名取 real_name，缺省回退 username。
     * overdue 在 SQL 内按 DB 时间计算（云端 DB 为 UTC，禁止 JVM now()），口径与 listForUser 一致：
     * due_time 非空且 CURRENT_TIMESTAMP > due_time 且 status='IN_PROGRESS'。
     */
    @Select("""
            SELECT t.user_id AS userId,
                   COALESCE(NULLIF(u.real_name, ''), u.username) AS userName,
                   COUNT(*) AS total,
                   SUM(t.status = 'COMPLETED') AS completed,
                   SUM(t.due_time IS NOT NULL AND CURRENT_TIMESTAMP > t.due_time
                       AND t.status = 'IN_PROGRESS') AS overdue
            FROM remediation_tasks t
            LEFT JOIN users u ON u.id = t.user_id AND u.is_deleted = 0
            WHERE t.class_id = #{classId} AND t.is_deleted = 0
            GROUP BY t.user_id, u.real_name, u.username
            ORDER BY t.user_id
            """)
    List<RemediationTeacherView.StudentRow> overviewStudents(@Param("classId") long classId);

    /** 薄弱知识点排行：按任务总数降序（同知识点多条任务链合并计数），id 升序稳定次序 */
    @Select("""
            SELECT t.knowledge_point_id AS knowledgePointId, kp.name AS knowledgePointName,
                   COUNT(*) AS taskCount
            FROM remediation_tasks t
            JOIN knowledge_points kp ON kp.id = t.knowledge_point_id
            WHERE t.class_id = #{classId} AND t.is_deleted = 0
            GROUP BY t.knowledge_point_id, kp.name
            ORDER BY taskCount DESC, t.knowledge_point_id ASC
            """)
    List<RemediationTeacherView.PointRow> overviewWeakPoints(@Param("classId") long classId);
}
