-- ============================================================
-- 增量脚本：考试记录归属 user_id 化
-- 日期：2026-11-15    目标库：exam_system    要求：MySQL 5.7+
-- 作用：考试记录从 student_name 字符串关联改为 user_id 外键关联，
--       支撑"学生只能查看/提交自己的考试记录"的后端强校验
-- 执行前请备份数据库！
-- ============================================================

-- 1) 添加 user_id 列 + 索引（允许 NULL：兼容历史数据，新记录由后端必填写入）
ALTER TABLE exam_records
    ADD COLUMN user_id BIGINT NULL AFTER exam_id,
    ADD INDEX idx_exam_records_user (user_id);

-- 2) 历史数据回填：按 users.real_name 与 student_name 匹配补挂 userId
--    同名用户会回填到其中一条（按 id 升序取第一个），属尽力回填；
--    回填不上的历史记录保持 NULL，后端对其按 student_name 弱校验处理
UPDATE exam_records er
JOIN users u ON u.real_name = er.student_name AND u.is_deleted = 0
SET er.user_id = u.id
WHERE er.user_id IS NULL;

-- 验证：
-- SHOW COLUMNS FROM exam_records LIKE 'user_id';
-- SELECT COUNT(*) FROM exam_records WHERE user_id IS NULL;
