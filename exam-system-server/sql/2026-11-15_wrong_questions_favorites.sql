-- ============================================================
-- 增量脚本：错题本（派生）+ 题目收藏
-- 日期：2026-11-15    目标库：exam_system    要求：MySQL 5.7+
-- 作用：错题本不做物化表，由 answer_record JOIN exam_records 按 user_id
--       实时派生（判卷链零改动）；本脚本只补齐派生查询所需的索引，
--       并新建两张行为标记表：题目收藏、错题已掌握标记
-- 依赖：2026-11-15_exam_records_user_id.sql 已执行（exam_records.user_id 列
--       与 idx_exam_records_user 索引），执行前请先跑其验证语句确认
-- 执行前请备份数据库！
-- ============================================================

-- ------------------------------------------------------------
-- 1) answer_record：派生查询第二跳的复合索引
--    派生链：idx_exam_records_user 先按学生定位考试记录，
--    再按每条 exam_record_id 等值探测答题记录，并用 is_correct 在索引内
--    过滤掉答对/未判卷行，减少回表
--    注意：仓库内无建表 DDL，无法确认 exam_record_id 是否已有单列索引；
--    若已存在完全相同索引会报 Duplicate key name，无害可跳过该步
-- ------------------------------------------------------------
ALTER TABLE answer_record
    ADD INDEX idx_answer_record_exam_correct (exam_record_id, is_correct);

-- ------------------------------------------------------------
-- 2) 题目收藏表：user_id + question_id 唯一，物理删除
--    不加 is_deleted：逻辑删除后旧行仍占用唯一键，取消收藏再收藏必撞唯一索引
--    （MySQL 5.7 无函数索引，无法像 uk_exam_active_student_paper 那样用生成列规避）；
--    收藏是纯用户偏好，无审计需求，与 video_likes 物理删除风格对齐。
--    唯一索引同时承担：重复收藏幂等兜底（配合后端 DuplicateKeyException 处理）、
--    "是否已收藏"点查、按 user_id 前缀列出收藏列表
-- ------------------------------------------------------------
CREATE TABLE question_favorites (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '收藏人，关联 users.id',
    question_id BIGINT   NOT NULL COMMENT '题目ID，关联 questions.id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorites_user_question (user_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目收藏表（学生维度，物理删除）';

-- ------------------------------------------------------------
-- 3) 错题已掌握标记表：错题本派生查询 LEFT JOIN 的移出状态
--    过滤语义：mm.id IS NULL OR mm.mastered_time < 最近一次错误时间
--    即标记掌握后移出错题本，之后再错同题（新错误时间晚于标记时间）自动回归；
--    取消掌握 = 物理删行；重复标记 = 后端更新 mastered_time（唯一索引兜底并发）
-- ------------------------------------------------------------
CREATE TABLE wrong_question_mastered (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT   NOT NULL COMMENT '学生，关联 users.id',
    question_id   BIGINT   NOT NULL COMMENT '题目ID，关联 questions.id',
    mastered_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标记掌握时间，重复标记时更新为最新时间',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次标记时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mastered_user_question (user_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题已掌握标记表（物理删除，取消掌握即删行）';

-- 验证：
-- 前置依赖应返回 1（user_id 索引已存在）：
-- SHOW INDEX FROM exam_records WHERE Key_name = 'idx_exam_records_user';
-- 新索引应返回 1：
-- SHOW INDEX FROM answer_record WHERE Key_name = 'idx_answer_record_exam_correct';
-- 两张新表应各返回一行建表语句：
-- SHOW CREATE TABLE question_favorites;
-- SHOW CREATE TABLE wrong_question_mastered;
-- 派生查询执行计划：exam_records 应走 idx_exam_records_user，
-- answer_record 应走 idx_answer_record_exam_correct（把 1 换成真实学生ID）：
-- EXPLAIN SELECT ar.question_id, COUNT(*) wrong_count, MAX(ar.create_time) last_wrong_time
--         FROM answer_record ar
--         JOIN exam_records er ON er.id = ar.exam_record_id
--         WHERE er.is_deleted = 0 AND ar.is_deleted = 0
--           AND er.user_id = 1 AND ar.is_correct = 0
--         GROUP BY ar.question_id;
