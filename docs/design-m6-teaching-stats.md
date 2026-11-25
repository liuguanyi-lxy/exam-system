# 技术设计：M6 教学统计

> 规格：`docs/specs/m6-teaching-stats.md`（2026-11-25 已拍板）。零新表、实时聚合；仅评估增量索引。

## 1. 接口契约

### GET /api/teaching-stats?classId={id}&days={7|30}

权限：`@RequireRole({"TEACHER","ADMIN"})`。教师只能查负责班级（复用 `ClassScopeService.isTeacherOfClass`）；管理员任意班级。无 `classId` 时管理员可省略？——不允许，必须显式传 classId（"全部"由对比接口承担）。

响应 `TeachingStatsVo`：

```json
{
  "classInfo": { "classId": 1, "className": "M5验证班", "studentCount": 2 },
  "active": { "days": 7, "activeCount": 1 },
  "learning": [ { "courseId": 1, "courseName": "x", "videoTotal": 3, "completedTotal": 1, "completionRate": 33 } ],
  "exams": [ {
    "examId": 1, "examName": "x", "passScore": 60, "participantCount": 2, "classStudentCount": 2,
    "participationRate": 100, "avgScore": 45, "passRate": 50,
    "distribution": [ { "range": "0-59", "count": 1 } ],
    "progress": [ { "userId": 4, "realName": "王小明", "firstScore": 25, "latestScore": 20, "delta": -5 } ]
  } ],
  "uncompleted": {
    "examAbsentees": [ { "examId": 1, "userId": 5, "realName": "x" } ],
    "overdueTasks": [ { "userId": 4, "knowledgePoint": "面向对象", "dueTime": "..." } ]
  },
  "topWrongQuestions": [ { "questionId": 1004, "content": "...", "wrongCount": 3, "wrongRate": 60, "chatCount": 2, "knowledgePoints": ["面向对象"] } ],
  "reviewStats": { "reportTotal": 1, "lowConfidenceCount": 0, "reviewedCount": 0, "reviewNoteCount": 0 }
}
```

### GET /api/teaching-stats/compare

权限：`@RequireRole({"TEACHER","ADMIN"})`。教师：负责班级全部；管理员：全部班级。

```json
{ "items": [ { "classId": 1, "className": "x", "avgScore": 45, "passRate": 50, "remediationCompletionRate": 100 } ] }
```

均分口径：各班**全部**考试记录（已批阅）的平均分；通过率同口径；补课完成率 = COMPLETED 任务 ÷ 全部任务。

## 2. 聚合口径（SQL 层）

| 指标 | SQL 口径 |
| --- | --- |
| 班级人数 | `class_members` role='STUDENT'，计未删除 users |
| 活跃 | 近 N 天任一：`exam_records.create_time`（user ∈ 班）/ `learning_video_sessions.last_signal_at` / `remediation_task_items.create_time`（表无 update_time）；UNION 去重计学员数 |
| 课程完成率 | `course_classes`→课程→`chapter_videos` 总数；完成数 = `learning_video_progress` status='COMPLETED' 且 user ∈ 班（video 口径去重） |
| 考试参与率 | `exam_classes` 该班分配 → 每场考试已交卷（status≠进行中）记录的去重学员数 ÷ 班级学员数 |
| 均分/通过率 | `exam_records`（exam_class_id=该班或 class_id=该班）已批阅记录；pass_score 取 `exams.pass_score` |
| 分布 | 分数段按 exam.total_score 归一：0-59/60-69/70-79/80-89/90-100 |
| 进步 | group by user_id,exam_id having count(distinct attempt)>1：latest − first（按 attempt_no） |
| 考试缺考名单 | 分配考试且窗口 `end_time < now`，学员 ∈ 班且无该考试已交卷记录 |
| 补课逾期 | `remediation_tasks` class_id=该班 status='IN_PROGRESS' 且 due_time < now |
| 高频错题 | `answer_record` join `exam_records`（record ∈ 该班已交卷）`is_correct=0` group by question_id TOP10（count 降序）；错误率 = 该题该班作答次数中错误占比；追问次数 = `ai_question_chats` group by question_id；知识点取 `question_knowledge_points` |
| 复核差异 | `ai_exam_reports` join 该班记录：总数、`report_json.confidence ∈ (LIMITED,DEGRADED)` 计数（SQL 用 `JSON_UNQUOTE(JSON_EXTRACT(...))`）、reviewed_by 非空计数、review_note 非空计数 |

全部聚合 SQL 强制带班级/时间条件（class_members 子查询限定 user 集合），禁止全表聚合。

## 3. 实现结构

- `TeachingStatsController`（新）→ `TeachingStatsService` / `Impl` → `TeachingStatsMapper`（@Select 注解聚合 SQL，与 M5 先例一致；行 record 走 arg-name 构造器映射）
- VO：`TeachingStatsVo` + 内嵌静态类；复用 `ClassScopeService`
- 权限：控制器 `@RequireRole`；数据范围服务层校验（教师非负责班级抛 `ClassScopeException` → HTTP 403，与 AuthInterceptor 403 风格一致；班级不存在抛 IllegalArgumentException → 200+code=500，与 M5 一致）
- 索引评估：`answer_record(exam_record_id)` 已有（随 record join）；`learning_video_sessions.last_signal_time` 低基数场景可不加；**先不加增量索引**，真实数据量验证响应时间后再定

## 4. 前端

- 新页 `views/stats/TeachingStats.vue`：班级下拉（教师=负责班级，管理员=全部）+ 区块：概览卡（人数/活跃）/ 课程完成率 / 考试统计（表格+分布条形）/ 未完成清单（缺考/逾期两 Tab）/ 高频错题表 / 复核差异卡 / 班级对比卡
- 路由：教师端 `/teaching-stats`（一级菜单"教学统计"）；管理端 `/admin/teaching-stats`（一级菜单）；共用组件
- 薄弱知识点排行区：同屏内嵌调用 `GET /api/remediation/overview` 展示排行部分

## 5. 测试策略

- `TeachingStatsServiceTest`（Mock Mapper）：范围校验（教师非负责班级拒绝/管理员通过）、口径计算（活跃去重、参与率、进步 delta、错误率、复核计数）
- `TeachingStatsControllerScopeTest`：教师/管理员/学员（403）
- 真实链路：云端造数验证接口数字与 SQL 直查一致 + 浏览器走查
