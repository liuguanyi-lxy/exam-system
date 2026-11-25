# 设计：M5 补课任务链与掌握度

> 依据：`docs/specs/m5-remediation.md`（已拍板：独立轻量复测 / 权重 20/30/50 / 不衰减 / 全自动触发）。复用 M2 学习进度、M4 判分引擎与数据范围层，不新建判分模型。
> 补充拍板（2026-11-25）：补课策略配置为课程级，但试卷/考试记录无课程关联——**开考班级经 course_classes 只分配一门课程时用该课程配置，多门课程或无课程用内置默认值**（阈值 60 / 补课 5 / 复测 5 / 强制复测 / 不跳视频 / 14 天）。实现见 `RemediationTaskService.resolvePolicy`。

## 1. 表设计（增量 SQL，草稿不执行）

```sql
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
);

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
);

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
);

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
);

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
);
```

要点：
- **幂等键**：任务 `uk(user, kp, record)`（报告 retry 不重建）；步骤 `uk(task, step, question, round)`（并发双击唯一键兜底）；掌握度 `uk(user, kp)` upsert。
- **选题去重**：变式题 = 同知识点题库排除基础练习已用题；题量不足全量给出并标记。
- **掌握度折算**：`total = videoDone*20 + accuracy*0.3 + retest*0.5`（复测缺位时 `total = videoDone*20/50 + accuracy*30/50`，`pending_retest=1`）。
- **视频完成**：复用 M2 `learning_video_sessions` 进度口径，任务只读该表判定，不重复存储。

## 2. API 契约

**学员（本人）**
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/remediation/tasks` | 我的任务列表（状态筛选、逾期标记） |
| GET | `/api/remediation/tasks/{id}` | 详情：四步状态+掌握度+快照配置 |
| POST | `/api/remediation/tasks/{id}/video/complete` | 幂等标记视频步骤完成（须完成全部映射视频） |
| GET | `/api/remediation/tasks/{id}/practice` | 取基础练习题（脱敏：无答案） |
| POST | `/api/remediation/tasks/{id}/practice` | 提交作答 → 判分 → 更新掌握度 |
| GET/POST | `/api/remediation/tasks/{id}/variation` | 变式题（同上，排除已用题） |
| GET/POST | `/api/remediation/tasks/{id}/retest` | 复测（同上；提交后终判掌握度） |

**教师/管理员**
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| PUT | `/api/knowledge-points/{id}/videos` | 维护知识点视频映射（数据范围内） |
| PUT | `/api/remediation/config/{courseId}` | 课程级策略配置 |
| GET | `/api/remediation/overview?classId=` | 班级进度概览（完成率/逾期/薄弱排行）+ 明细 |

**系统**
- 报告 worker 生成报告成功（READY/DEGRADED 且 weakPoints 非空）后，同事务内按知识点生成任务链（幂等）；`LIMITED` 不生成。

## 3. 实现顺序（小步可验证）

1. 迁移 SQL 草稿 + 5 个 entity/mapper（含唯一键）
2. 任务生成：报告 worker 联动 + 幂等单测
3. 学员任务链读接口 + 步骤状态机（跳视频门禁）
4. 练习/变式/复测提交 + 判分联动 + 掌握度 upsert（并发单测）
5. 教师配置 + 映射维护 + 班级概览（数据范围）
6. M4 报告资源推荐升级 + 删除 `CourseService.java`
7. 前端：补课中心页 + 任务链交互 + 报告推荐展示
8. 全量回归 + 构建 + 浏览器验证

## 4. 风险与对策

| 风险 | 对策 |
| --- | --- |
| 判分引擎按整卷设计，单知识点小卷适配 | 复用 `AiGradingService` 单题判分路径；复测 5 题内不开整卷评语 |
| 任务生成在 worker 事务内，失败拖垮报告 | 任务生成失败仅记日志降级（报告仍 READY），定时补偿不引入——retry 报告即可重建 |
| 并发首次生成/首次提交 | 唯一键 + INSERT IGNORE 模式（沿用 M4 chat 先例），冲突重读 |
| 掌握度 upsert 并发丢失更新 | `UPDATE ... WHERE user_id=? AND kp=?` 原子更新而非读改写 |
| 知识点题库题量不足 | 全量给出并在响应标记 `insufficient=true`，前端提示 |
| 教师课程归属与数据范围 | 配置与映射维护走 `@RequireRole` + 课程归属校验（沿用 ExamRecordController 模式） |
