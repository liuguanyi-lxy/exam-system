-- ============================================================
-- 增量脚本：视频支持外部链接来源
-- 日期：2026-11-15    目标库：exam_system    要求：MySQL 5.7+
-- 作用：视频增加来源类型，source_type=1 时 file_url 存外部平台链接
--       （如 B 站视频页地址），由前端渲染为内嵌播放器或跳转按钮
-- 执行前请备份数据库！
-- ============================================================

ALTER TABLE videos
    ADD COLUMN source_type TINYINT NOT NULL DEFAULT 0 COMMENT '来源：0-站内上传，1-外部链接' AFTER file_url;

-- 验证：
-- SHOW COLUMNS FROM videos LIKE 'source_type';
