-- ============================================================
-- 优化脚本：并发安全唯一索引
-- 日期：2026-09-29    目标库：exam_system    要求：MySQL 5.7+
-- 作用：从数据库层面兜底两类并发写脏数据问题（配合后端 DuplicateKeyException 处理）
-- 执行前请备份数据库！
-- ============================================================

-- ------------------------------------------------------------
-- 1) exam_records：同一学生对同一试卷，最多一条"进行中"的考试记录
--    思路：生成列只在 status='进行中' 时非 NULL，唯一索引不约束 NULL，
--    因此历史"已结束/已批阅"记录可以有多条（支持重考），只拦截并发的进行中记录
-- ------------------------------------------------------------

-- 1.1 先清理历史脏数据：同一学生同一试卷存在多条"进行中"时，只保留最新一条，其余按已结束处理
UPDATE exam_records er
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY student_name, exam_id ORDER BY start_time DESC) AS rn
        FROM exam_records
        WHERE status = '进行中' AND is_deleted = 0
    ) t
    WHERE t.rn > 1
) dup ON er.id = dup.id
SET er.status = '已结束',
    er.end_time = COALESCE(er.end_time, NOW());

-- 1.2 添加生成列 + 唯一索引
ALTER TABLE exam_records
    ADD COLUMN active_flag VARCHAR(120)
        GENERATED ALWAYS AS (IF(status = '进行中', CONCAT(student_name, ':', exam_id), NULL)) STORED,
    ADD UNIQUE KEY uk_exam_active_student_paper (active_flag);

-- ------------------------------------------------------------
-- 2) video_likes：同一 IP 对同一视频只能有一条点赞记录
-- ------------------------------------------------------------

-- 2.1 先清理历史重复点赞（保留最早一条）
DELETE vl FROM video_likes vl
JOIN (
    SELECT MIN(id) AS keep_id, video_id, user_ip
    FROM video_likes
    GROUP BY video_id, user_ip
    HAVING COUNT(*) > 1
) d ON vl.video_id = d.video_id
   AND vl.user_ip = d.user_ip
   AND vl.id > d.keep_id;

-- 2.2 添加唯一索引
ALTER TABLE video_likes
    ADD UNIQUE KEY uk_video_userip (video_id, user_ip);

-- 验证：以下两条应分别返回 1（有唯一索引）
-- SHOW INDEX FROM exam_records WHERE Key_name = 'uk_exam_active_student_paper';
-- SHOW INDEX FROM video_likes WHERE Key_name = 'uk_video_userip';
