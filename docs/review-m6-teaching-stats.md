# AI Review：M6 教学统计（feat/m6-teaching-stats 未提交变更）

> 审查范围：TeachingStatsVo/Mapper（@Select 聚合 SQL）/Service/Controller、2 个测试类、前端 TeachingStats.vue + api/teachingStats.js + 路由/菜单。对照 `docs/specs/m6-teaching-stats.md` 验收标准 1-10 与 `docs/design-m6-teaching-stats.md` 逐条核对。审查过程中已直接修复确凿缺陷并重跑全量测试。

## 结论

整体实现质量高：数据范围（范围校验先于存在性校验 → `ClassScopeException`→HTTP 403，与 AuthInterceptor 403 风格一致）、空集合哨兵（compare 教师 0 带班直接空结果不发查询）、record arg-name 构造器映射（`application.yaml` 已开启，全部组件名与 SQL 列别名核对一致）、中文 status 值口径（`进行中/已完成/已批阅`）、时间判定全部 SQL 内 `CURRENT_TIMESTAMP`（无 JVM now()）、SQL 全部 `#{}` 参数化、聚合 SQL 全部带 `class_members` 班级子查询——均与规格一致。发现 1 个 P0 数据正确性 + 1 个 P1 口径不一致缺陷，已修复；3 项 P2 建议中 2 项已修、1 项（文档偏差）已回写设计文档。

## 问题清单

| 编号 | 严重度 | 文件:位置 | 描述 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | P0 | `TeachingStatsMapper.java` courseLearning | **videoTotal 重复计数**：`learning_video_progress` 有 `uk_progress_user_video(user_id, video_id)`，LEFT JOIN 后任一视频被 ≥2 名班内学员完成即放大行数（3 视频 × 2 学员全完成 → videoTotal=6、completedTotal=3 → 完成率 50%，实际 100%），违反验收标准 3 | **已修复**：`videoTotal` 改 `COUNT(DISTINCT cv.id)` |
| 2 | P1 | `TeachingStatsMapper.java` compareClasses | **对比页通过率与单班页口径不一致**：原为记录级（`SUM(score>=pass_score)/COUNT(*)`），单班页 examStats 为学员去重级（DISTINCT user_id）；多次尝试学员（先挂后过）两处数字对不上，违反验收标准 8 | **已修复**：对比页改学员去重口径（任意一次已批阅达标学员 ÷ 已批阅学员），与单班页一致 |
| 3 | P2 | `TeachingStatsMapper.java` examDistribution | ROW_NUMBER 窗口函数为全项目 Mapper 首次使用，而 DEPLOY.md 声明 MySQL 5.7 可用（5.7 无窗口函数会直接报错） | **已修复**：改 GROUP_CONCAT(SUBSTRING_INDEX) 取每学员每考试最近一次成绩，5.7 兼容，语义不变 |
| 4 | P2 | `TeachingStatsMapper.java` examAbsentees | 已删除学员（users.is_deleted=1）仍进缺考名单且 realName=null（前端空白），与班级人数口径（排除已删）不一致 | **已修复**：users 改 INNER JOIN |
| 5 | P2 | `TeachingStats.vue` 复核差异卡 | reportTotal=0 时 0 值网格与"暂无诊断报告"空态并存 | **已修复**：空态时隐藏网格 |
| 6 | P2 | `docs/design-m6-teaching-stats.md` | 文档与实现偏差：XML 聚合 SQL（实为 @Select，同 M5 先例）；越权 IllegalArgumentException→200+500（实为 ClassScopeException→403）；活跃口径 `remediation_task_items.update_time`（表无该列，实为 create_time）、`learning_video_sessions.last_signal_time`（实列名 last_signal_at） | **已回写设计文档**（实现依据充分：表结构与 M5 先例） |

## 规格验收核对（通过项摘要）

1. **数据范围**：stats 教师 `isTeacherOfClass` 越权→403（有 MockMvc 单测，verifyNoInteractions(mapper)）；管理员任意班级；范围校验先于班级存在性校验（不泄露存在性）。✓
2. **班级对比**：教师=负责班级集合（getTeacherClassIds），管理员=allClassIds；空集合哨兵直接空结果；通过率口径修复后与单班页一致。✓
3. **聚合条件**：全部 SQL 带 `class_members(role_in_class='STUDENT')` 子查询限定学员集合；考试记录范围 user∈班 且 (class_id 或 exam_class_id=该班)；无全表聚合。✓
4. **活跃口径**：考试记录 create_time / 视频心跳 last_signal_at / 补课提交 create_time 三源 UNION 去重；days 仅 7/30（有单测）。✓
5. **考试统计**：参与率（去重交卷学员÷班级学员）、通过率（去重通过学员÷已批阅学员）、均分（已批阅记录 AVG）、分布五段固定顺序补 0（最近一次已批阅成绩按 total_score 归一）、进步 delta=最近−首次（GROUP_CONCAT 按 attempt_no 取首尾，HAVING 次数>1）。✓
6. **未完成清单**：缺考=窗口结束（end_time<DB 当前时间）且无已交卷记录（含已删除学员排除）；逾期补课=class_id 归属+IN_PROGRESS+due_time 过期，时间判定全在 SQL 内。✓
7. **高频错题**：is_correct=0 按题聚合 LIMIT 10；错误率=该题该班作答总次数中错误占比（含正确作答的分母子查询）；追问热度=ai_question_chats 班级学员轮数；知识点 question_knowledge_points 归组。✓
8. **复核差异**：JSON_UNQUOTE(JSON_EXTRACT(report_json,'$.confidence')) IN (LIMITED,DEGRADED)；reviewed_by/review_note 非空计数；报告 join 该班记录。✓
9. **薄弱知识点**：未新建接口，前端同屏内嵌复用 GET /api/remediation/overview。✓
10. **前端**：班级下拉复用 getClassList 翻页全量（与 M5 同口径）；request 统一解包；除零/空态齐全；路由 `/admin/teaching-stats` + 一级菜单（`/admin` 布局 roles:['ADMIN','TEACHER'] 共用，满足拍板决策 1）；build 通过。

## 验证结果

- 后端：`mvn -o -Dmaven.resources.skip=true -Dtest=!ExamSystemServerApplicationTests test` → **Tests run: 236, Failures: 0, Errors: 0, Skipped: 0**（基线 221 + 本次新增 15），BUILD SUCCESS。
- 前端：`npm run build` → **✓ built in 31.52s** 通过（chunk 体积告警为既有历史问题）。
- 零新表、零增量 SQL；真实链路验证（云端造数核对 SQL 直查数字 + 浏览器走查）在 PR merge 后按验收标准 9 执行。
