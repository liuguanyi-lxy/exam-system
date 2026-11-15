-- ============================================================
-- 增量脚本：视频观看会话化统计
-- 日期：2026-11-15    目标库：exam_system    要求：MySQL 5.7+
-- 作用：观看上报改为「会话ID + 心跳增量」——一次观看只计一条记录、
--       观看次数只加一，同会话后续心跳只累加时长
-- 执行前请备份数据库！
-- ============================================================

ALTER TABLE video_views
    ADD COLUMN session_id VARCHAR(36) NULL COMMENT '观看会话ID，同会话心跳累加时长不重复计观次' AFTER video_id,
    ADD INDEX idx_video_views_session (session_id);

-- 验证：
-- SHOW COLUMNS FROM video_views LIKE 'session_id';
