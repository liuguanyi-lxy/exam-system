-- ============================================================
-- 增量脚本：M3 班级考试分配与可见性收敛
-- 日期：2026-11-23    目标库：exam_system    要求：MySQL 5.7+
-- 作用：新建 exam_classes（试卷×班级分配表）；exam_records 补
--       exam_class_id / class_id / attempt_no 快照，调整进行中唯一键
--       快照在新开考（startExam）时写入，续考不修改
-- 依赖：2026-09-29 唯一索引、2026-11-15 考试记录 user_id、
--       2026-11-21_m1_classes_knowledge.sql 已执行（classes /
--       class_members 表在），执行前请先跑其验证语句确认
-- 历史数据说明：三个快照列对历史记录
--       保持 NULL，不回填——分配关系当时不存在，回填只能伪造数据；
--       NULL 表示无分配快照；旧记录仅在教师总览中按已关联账号的当前班级
--       归属处理，不纳入明确班级筛选；空账号记录不按姓名猜测归属
-- 执行前请备份数据库！
-- 执行前先查目标库当前状态（以下表和快照列均应不存在）：
--   SHOW TABLES LIKE 'exam_classes';
--   SHOW COLUMNS FROM exam_records LIKE 'exam_class_id';
--   SHOW COLUMNS FROM exam_records LIKE 'class_id';
--   SHOW COLUMNS FROM exam_records LIKE 'attempt_no';
-- 核对旧生成列 active_flag、旧唯一键及 user_id 已存在，并确认新索引名未被占用：
--   SHOW CREATE TABLE exam_records;
-- 历史活动记录按用户与试卷不得重复；下列预检若返回记录，先人工核对，不执行脚本。
--   SELECT user_id, exam_id, COUNT(*) FROM exam_records
--   WHERE status = '进行中' AND is_deleted = 0 AND user_id IS NOT NULL
--   GROUP BY user_id, exam_id HAVING COUNT(*) > 1;
-- ============================================================

-- ------------------------------------------------------------
-- 1) 考试班级分配表：试卷 × 班级粒度，一张试卷可分配多个班级
--    逻辑删除（BaseEntity）：撤销分配留痕；撤销后重新分配同卷同班
--    会撞唯一键（逻辑删旧行占键），由后端"复活"SQL 处理
--    （UPDATE ... SET is_deleted=0 WHERE exam_id=? AND class_id=? AND is_deleted=1）
--    start_time/end_time 可空 = 该侧不限制：start_time IS NULL 视为已开始，
--    end_time IS NULL 视为不结束；先后关系由应用层校验（不依赖 CHECK，保持 5.7 兼容）
--    max_attempts 是"总考试次数上限"（含首次）：默认 1 = 只考一次，2 = 可重考一次；
--    已有人开考后不可修改次数上限（后端校验）
--    status 对齐 classes 表的 VARCHAR 枚举风格：禁用分配对学员等价于不存在
-- ------------------------------------------------------------
CREATE TABLE exam_classes (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    exam_id      BIGINT      NOT NULL COMMENT '试卷ID，关联 paper.id（注意 paper 表为单数，见 Paper 实体 @TableName）',
    class_id     BIGINT      NOT NULL COMMENT '班级ID，关联 classes.id',
    start_time   DATETIME    NULL COMMENT '考试窗口开始时间，NULL 表示不限制开始（立即生效）',
    end_time     DATETIME    NULL COMMENT '考试窗口结束时间，NULL 表示不限制结束；start_time <= end_time 由应用层校验',
    max_attempts INT         NOT NULL DEFAULT 1 COMMENT '总考试次数上限（含首次），默认1=只考一次；已有人开考后不可修改',
    status       VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用 DISABLED-禁用；禁用分配对学员等价于不存在',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted   TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已撤销（BaseEntity @TableLogic）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_class (exam_id, class_id),
    KEY idx_exam_class_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试班级分配表（试卷×班级，逻辑删除，撤销后重新分配走后端复活流程）';

-- ------------------------------------------------------------
-- 2) 班级、分配、尝试序号持久化；唯一索引改为按账号判断进行中
--    exam_class_id：开考那一刻的分配快照，定格后班级调整/分配撤销不影响
--    历史记录（完成优先）；无班级学员开考为 NULL
--    尝试序号计入逻辑删除的记录，历史记录不回填
--    替换旧的姓名生成列及索引，按用户 ID 分开同名账号
-- ------------------------------------------------------------
ALTER TABLE exam_records
    ADD COLUMN exam_class_id BIGINT NULL COMMENT '开考时的分配快照ID，关联 exam_classes.id；历史记录与无班级学员为 NULL，不回填，不受后续班级调整影响',
    ADD COLUMN class_id BIGINT NULL COMMENT '开考时班级快照；成员迁移或分配撤销不修改',
    ADD COLUMN attempt_no INT NULL COMMENT '同学员同卷尝试序号；逻辑删除不退次数；历史记录不回填',
    ADD INDEX idx_exam_records_assignment (exam_class_id),
    ADD INDEX idx_exam_records_class_snapshot (class_id),
    ADD INDEX idx_exam_records_user_paper (user_id, exam_id),
    DROP INDEX uk_exam_active_student_paper,
    DROP COLUMN active_flag,
    ADD COLUMN active_attempt_key VARCHAR(120) GENERATED ALWAYS AS (
        CASE
            WHEN status = '进行中' AND is_deleted = 0 THEN
                CONCAT(IF(user_id IS NOT NULL, CONCAT('u:', user_id), CONCAT('n:', student_name)), ':', exam_id)
            ELSE NULL
        END
    ) STORED,
    ADD UNIQUE KEY uk_exam_active_account_paper (active_attempt_key);

-- 验证：
-- 新表应返回一行建表语句，且包含 uk_exam_class 与 idx_exam_class_class 两个索引：
-- SHOW CREATE TABLE exam_classes;
-- 核对快照、活动唯一键及查询索引：
-- SHOW COLUMNS FROM exam_records LIKE 'exam_class_id';
-- SHOW COLUMNS FROM exam_records LIKE 'attempt_no';
-- SHOW COLUMNS FROM exam_records LIKE 'class_id';
-- SHOW INDEX FROM exam_records WHERE Key_name = 'idx_exam_records_assignment';
-- SHOW INDEX FROM exam_records WHERE Key_name = 'idx_exam_records_class_snapshot';
-- SHOW INDEX FROM exam_records WHERE Key_name = 'uk_exam_active_account_paper';
-- 前置依赖应返回 1（attempt 计数与学员聚合的入口索引，本脚本不重复创建）：
-- SHOW INDEX FROM exam_records WHERE Key_name = 'idx_exam_records_user';
-- 热路径执行计划（把 1 换成真实试卷/班级/用户ID）：
-- startExam 班级成员校验应走 uk_exam_class（type=ref 或 range）：
-- EXPLAIN SELECT id, max_attempts FROM exam_classes
--         WHERE exam_id = 1 AND class_id IN (1, 2) AND is_deleted = 0 AND status = 'ENABLED';
-- 学员可见性第一步应走 idx_exam_class_class：
-- EXPLAIN SELECT id, exam_id, start_time, end_time, max_attempts FROM exam_classes
--         WHERE class_id = 1 AND is_deleted = 0 AND status = 'ENABLED';
-- 撤销分配探测应走 idx_exam_records_assignment：
-- EXPLAIN SELECT COUNT(*) FROM exam_records
--         WHERE exam_class_id = 1 AND status = '进行中' AND is_deleted = 0;
-- attempt 计数使用 (user_id, exam_id) 索引：
-- EXPLAIN SELECT COUNT(*) FROM exam_records
--         WHERE user_id = 1 AND exam_id = 1;
