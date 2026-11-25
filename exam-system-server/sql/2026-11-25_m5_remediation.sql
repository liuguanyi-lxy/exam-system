-- M5 补课任务链与掌握度（草稿，尚未执行；执行前先查目标库当前状态，本脚本不可重复执行）
-- 依据：docs/design-m5-remediation.md 第 1 节
-- 库：exam_system

-- 1) 知识点↔视频映射（教师维护；报告真实资源推荐也用它）
CREATE TABLE knowledge_point_videos (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  knowledge_point_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  UNIQUE KEY uk_kp_video (knowledge_point_id, video_id)
) COMMENT '知识点-视频映射（M5）';

-- 2) 课程级补课策略（教师配置；任务生成时快照到任务）
CREATE TABLE remediation_configs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  min_mastery INT NOT NULL DEFAULT 60,          -- 最低掌握度阈值
  practice_count INT NOT NULL DEFAULT 5,        -- 补课题数（基础练习）
  retest_count INT NOT NULL DEFAULT 5,          -- 复测题数
  force_retest TINYINT NOT NULL DEFAULT 1,      -- 强制复测
  allow_skip_video TINYINT NOT NULL DEFAULT 0,  -- 允许跳视频
  due_days INT NOT NULL DEFAULT 14,             -- 默认截止天数
  is_deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  UNIQUE KEY uk_course_config (course_id)
) COMMENT '课程级补课策略（M5）';

-- 3) 补课任务链（每学员每薄弱知识点每来源报告一条）
CREATE TABLE remediation_tasks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exam_record_id BIGINT NOT NULL,               -- 来源报告
  user_id BIGINT NOT NULL,
  class_id BIGINT NULL,                          -- 开考班级快照（教师范围用）
  knowledge_point_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS/COMPLETED
  config_snapshot JSON NOT NULL,                 -- 生成时策略快照
  due_time DATETIME NULL,                        -- 截止时间（仅提示）
  is_deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  UNIQUE KEY uk_task_user_kp_record (user_id, knowledge_point_id, exam_record_id),
  INDEX idx_task_class (class_id),
  INDEX idx_task_user (user_id, status)
) COMMENT '补课任务链（M5）';

-- 4) 任务步骤作答明细（基础练习/变式题/复测共用；视频完成复用 M2 进度，不入此表）
CREATE TABLE remediation_task_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  step VARCHAR(20) NOT NULL,                     -- PRACTICE/VARIATION/RETEST
  round_no INT NOT NULL DEFAULT 1,               -- 重做轮次（练习/变式可重做，复测可重做）
  question_id BIGINT NOT NULL,
  user_answer TEXT NULL,
  is_correct TINYINT NULL,
  score DECIMAL(6,2) NULL,
  ai_feedback TEXT NULL,
  create_time DATETIME,
  UNIQUE KEY uk_item_task_step_q_round (task_id, step, question_id, round_no),
  INDEX idx_item_task (task_id, step)
) COMMENT '补课步骤作答明细（M5）';

-- 5) 掌握度（每学员每知识点一条，upsert）
CREATE TABLE mastery_scores (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  knowledge_point_id BIGINT NOT NULL,
  video_done TINYINT NOT NULL DEFAULT 0,
  practice_accuracy DECIMAL(5,2) NULL,           -- 最近一次练习正确率 %
  retest_score DECIMAL(5,2) NULL,                -- 最近一次复测得分 %
  pending_retest TINYINT NOT NULL DEFAULT 1,     -- 未复测=临时掌握度
  total DECIMAL(5,2) NOT NULL DEFAULT 0,         -- 0-100 折算
  last_retest_time DATETIME NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  UNIQUE KEY uk_mastery_user_kp (user_id, knowledge_point_id)
) COMMENT '知识点掌握度（M5）';
