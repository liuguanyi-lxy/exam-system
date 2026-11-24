# 契约：M3 班级考试分配与可见性收敛

> 规格见 `docs/specs/m3-exam-classes.md`。无班级学员沿用兼容策略，最终政策待确认。数据范围依赖 M1 `ClassScopeService`，成绩按开考快照优先。

## 1. 数据模型变更

- `exam_classes`：`id`、`exam_id`（试卷 ID）、`class_id`、`start_time`、`end_time`、`max_attempts`（含首考，默认 1）、`status`、逻辑删除；`(exam_id, class_id)` 唯一。
- `exam_records`：`exam_class_id`、`class_id` 为开考快照，旧记录及无班级兼容开考为 `null`；新记录的 `attempt_no` 按账号和试卷编号，计入逻辑删除记录。进行中唯一键按账号而非姓名区分。

## 2. 接口清单

### 2.1 分配管理（ADMIN 全量 / TEACHER 仅负责班级；均走 @RequireRole({"TEACHER","ADMIN"}) + ClassScopeService 校验目标班级）

| 接口 | 说明 | 数据范围 |
|---|---|---|
| `POST /api/exam-classes` | 创建分配。Body：`{examId, classIds:[], startTime?, endTime?, maxAttempts?}`；仅已发布试卷、启用班级；按班级返回 `{classId, assignmentId, status, message}`，成功、已分配和恢复分别为 `CREATED`、`EXISTS`、`REVIVED` | TEACHER-own-class |
| `PUT /api/exam-classes/{id}` | 修改时间窗口、总考试次数；时间省略保留，显式 `null` 清除该侧限制。曾开考（含已删记录）不得改变次数 | TEACHER-own-class |
| `DELETE /api/exam-classes/{id}` | 撤销分配：已有**进行中**考试时拒绝；仅"已结束/已批阅"记录存在时允许（逻辑删分配，历史记录保留） | TEACHER-own-class |
| `GET /api/exam-classes?examId=&classId=&page=&size=` | 分配分页列表，页大小最多 100；按试卷、班级筛选并与教师范围取交集 | TEACHER-own-class |
| `GET /api/exam-classes/{id}` | 分配详情，含班级名、试卷名和各状态人数；与 `students` 聚合同口径 | TEACHER-own-class |

### 2.2 学员考试可见性与开考（修改现有接口，标注改造点）

| 接口 | 说明 | 数据范围 |
|---|---|---|
| `GET /api/papers/list` | 需登录；教师/管理员的机构共享试卷列表不变。学员按班级分配（窗口外也展示状态）；无班学员兼容已发布卷。本人进行中卷始终有 `resumeRecordId` 入口，不受撤销分配或停卷影响 | STUDENT-own-class |
| `GET /api/papers/{id}` | 学员未开考只得到试卷元信息；有本人进行中记录时返回脱敏题目和 `resumeRecordId` | STUDENT-own-class |
| `POST /api/exams/start` | Body：`{paperId, studentName, resumeRecordId?}`。先按账号加锁并核对本人同卷进行中记录。显式续考 ID 失效则拒绝。仅新开考要求试卷已发布；有班学员还须满足启用班级分配、时间窗口、次数上限；成功写入快照 | STUDENT-own-class |
| `POST /api/exams/{id}/submit` | 不重算快照或检查新开考条件；学员按账号归属，教师按记录对应班级，沿用提交时限等保护 | 账号/记录范围 |

学员列表和试卷预览追加 `assigned`、`windowStatus`（`未开始` / `进行中` / `已结束`）、`examStartTime`、`examEndTime`、`attemptLeft`，以及可选 `resumeRecordId`。有续考记录优先显示续考；各窗口使用业务本地时间。

### 2.3 教师成绩视角（增强现有接口）

| 接口 | 说明 | 数据范围 |
|---|---|---|
| `GET /api/exam-records/list`、`GET /api/exam-records/stats` | 可按快照 `classId` 筛选；教师范围与筛选取交集，越范围筛选无数据。列表返回 `className`；不传筛选时，历史无班级快照行按 M1 当前成员范围判定 | TEACHER-own-class |
| `GET /api/exam-classes/{id}/students` | 当前班学员和具有该分配记录的历史学员；按该分配的未删记录聚合 `userId`、`realName`、`attemptCount`、`maxScore`、`latestStatus`，不混入同卷其他分配 | TEACHER-own-class |

详情、删除、判卷、排名和概览同样按记录快照识别教师范围；学员个人详情以账号 ID 为准，不以姓名证明归属。

## 3. 错误码与文案

| 场景 | HTTP | code | message |
|---|---|---|---|
| 开考：未分配 | 403 | 403 | 该考试未分配给你的班级 |
| 开考：窗口外 | 200 | 500 | 考试时间：{start} ~ {end}，当前不可参加 |
| 开考：次数用尽 | 200 | 500 | 考试次数已用完（共 {n} 次） |
| 开考：续考记录失效或不匹配 | 200 | 500 | 需要续考的记录已不可用，请返回考试列表 |
| 修改已开考分配的次数 | 200 | 500 | 已有学员开考，无法修改考试次数 |
| 撤销有进行中考试的分配 | 200 | 500 | 有学员正在考试，无法撤销分配 |
| 教师操作非负责班级 | 403 | 403 | 无权限执行该操作 |

## 4. 前端接入

- 管理端（AdminLayout）：`views/exam/ExamAssign.vue` 分配管理页（列表 + 创建弹窗：选试卷/选班/时间/次数）；入口"考试分配"
- 试卷列表页（PaperList）：每行"分配班级"快捷入口
- 学员端：`ExamList.vue` 展示可参加、未开放、已结束、次数用尽以及续考；`ExamStart.vue` 刷新时核对服务器返回的记录 ID。
- 管理端记录页：`ExamRecordList.vue` 按班级筛选（教师的选项仅为负责班级）。

## 5. 决策记录（YAGNI 边界）

| 决策 | 结论 | 不做的事 |
| --- | --- | --- |
| 分配粒度 | 试卷 × 班级；不做班级×试卷×学员个人例外分配 | 个人补考单独分配（M5 补课复测若需要再加） |
| 成绩口径 | 展示最高分；全部尝试均保留可查看 | 覆盖式成绩 |
| 撤销分配 | 有进行中考试拒绝；仅历史记录允许逻辑删 | 物理删除分配与记录 |
| 无班级学员 | 暂保持已发布卷的旧可见性，待最终确认 | 强制建班 |
| 窗口时区 | `yyyy-MM-dd HH:mm:ss` 本地业务时间，部署时统一为 GMT+8 | 跨时区转换 |
