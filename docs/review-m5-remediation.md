# AI Review：M5 补课任务链与掌握度（feat/m5-remediation 未提交变更）

> 审查范围：SQL 草稿、5 组 entity/mapper、任务生成、学员任务链接口、练习/变式/复测判分、教师配置/概览、报告推荐升级、Vue3 前端三页及路由/布局/成绩页改动。对照 `docs/specs/m5-remediation.md` 验收标准 1-9 与 `docs/design-m5-remediation.md` 逐条核对。审查过程中已直接修复确凿缺陷并重跑全量测试。

## 结论

整体实现质量高：幂等口径（唯一键 + INSERT IGNORE + `WHERE user_answer IS NULL`）、数据范围（学员本人/教师带班/管理员全量）、脱敏（SanitizedQuestion 绝无 answer/analysis/isCorrect）、时间口径（overdue/due_time/last_retest_time 全部 SQL 内 `CURRENT_TIMESTAMP`，无 JVM now() 对比 DB 时间）、配置快照（门禁/取题/完成判定全部读快照）均与规格一致。发现 2 个确凿缺陷（1 个 P1 数据正确性 + 1 个 P2 事务边界），已修复；另记录 7 项 P3 建议不修。

## 问题清单

| 编号 | 严重度 | 文件:行 | 描述 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | P1 | `RemediationStepService.java` submit/persistGraded | **并发整轮重复提交会把掌握度清零**：提交的题全部被并发请求抢先落库时，`submitAnswer` 全部更新 0 行 → totalCount=0、accuracy=0，原实现仍无条件 `upsertPracticeAccuracy/upsertRetestScore(0)`，既有掌握度/最近复测分被错误覆盖为 0（原测试只覆盖了"部分 0 行"场景） | **已修复**：totalCount=0 时跳过掌握度联动与完成判定，回显 DB 当前 total；新增单测 `fullyDuplicateSubmitSkipsMasteryUpsertInsteadOfZeroingIt` |
| 2 | P2 | `RemediationStepService.java` submit | **AI 远程调用在事务内**：submit 整体 `@Transactional`，主观题批量 AI 判分（可能数秒）期间占住 DB 连接与事务；与 M4 判卷先例（`ExamServiceImpl.gradeExam` 无事务注解）不一致，并发提交时易耗尽连接池 | **已修复**：submit 改为非事务方法，校验/读题/AI 判分在事务外，落库段（submitAnswer 循环 + 掌握度 upsert + 完成判定）经 `TransactionTemplate` 收敛为单事务，语义不变；测试夹具同步接入直通事务管理器 |
| 3 | P3 | `RemediationStepService.java` submit | 允许部分提交：一次提交少于本轮待答题数时，accuracy 按已提交子集计算并覆盖掌握度，之后补交再覆盖一次。规格未明确"一次交全"口径，且幂等保护使数据无损坏 | 建议不修（记录口径） |
| 4 | P3 | `RemediationStepService.java`、`MasteryScoreMapper` | 复测"得分"用对错计数正确率%，非按题目分值加权得分率（items.score 存了单题分值但未参与折算）。独立小卷默认题数等分场景下两口径近似 | 建议不修（设计简化口径，规格验收 5 只要求"最近结果覆盖"） |
| 5 | P3 | `RemediationCenter.vue` masteryColor | 列表页掌握度颜色硬编码 60 阈值，与任务快照 minMastery 不一致（详情页已用快照） | 建议不修（仅展示口径，后端完成判定以快照为准） |
| 6 | P3 | `RemediationPractice.vue` rr-done 文案 | taskCompleted 提示固定"复测达标，补课任务已完成"，forceRetest=false 时由练习完成触发，文案不准确 | 建议不修（文案问题，可在用户 review 反馈时调整） |
| 7 | P3 | `RemediationTaskView.Summary.dueTime` 等 | dueTime 为 DB UTC 字面量，前端直接展示偏 8 小时。沿用项目现有 entity `@JsonFormat GMT+8` 序列化模式，属全局时间口径问题非本次变更引入 | 不修（全局口径，与既有页面一致） |
| 8 | P3 | `RemediationStepService.paper` | 并发取题窗口内（两请求同时推进到同一新轮），`INSERT IGNORE` 竞态可能使返回试卷含未落库题目，提交时对应题 0 行跳过计分，属无害降级 | 不修（窗口极小、无数据损坏，修复 1 已消除其最坏后果） |
| 9 | P3 | `RemediationConfigMapper.upsert` 等 | `VALUES(col)` 写法 MySQL 8.0.20+ deprecated（仅 warning），项目既有 SQL 亦如此 | 不修（风格一致性问题） |

## 规格验收逐条核对（通过项摘要）

1. **任务生成**：`LIMITED`/空薄弱点前置跳过（有单测）；`uk_task_user_kp_record` + INSERT IGNORE，retry 幂等（有单测重放验证 0 新建）。✓
2. **跳视频门禁**：`gateOpen = 无映射 || 全部映射视频 COMPLETED || 快照 allowSkipVideo`，detail/取题/提交三处同口径复用 `videoGateOpen`；detail 展示 NO_VIDEO/TODO/DONE。✓
3. **视频与选题**：视频完成判定复用 M2 `learning_video_progress.status='COMPLETED'`（与 `LearningServiceImpl` 现行口径一致，设计文档写 sessions 为笔误）；变式排除本任务全部 PRACTICE 已用题，题池不足回退全池并标 `insufficient=true`（有单测）。✓
4. **复测与完成**：客观题本地比对 + 主观题一次批量 AI（maxScore=100、≥60 记正确、AI 失败按未判分落库不炸整批）；`total >= 快照 minMastery` 才 markCompleted；forceRetest=false 时练习完成即完成且 pendingRetest 保持"待复测"标记。✓
5. **掌握度折算**：SQL 内按 `pending_retest` 分支原子折算——待复测 `video*40 + accuracy*0.6`，复测后 `video*20 + accuracy*0.3 + retest*0.5`，与拍板口径一致；重做以最近一次覆盖；禁止读改写（ON DUPLICATE KEY UPDATE 单语句）。✓
6. **幂等并发**：任务/步骤唯一键兜底 + INSERT IGNORE；提交 `WHERE user_answer IS NULL`；掌握度 uk(user,kp) upsert；并发首次插入有隔离单测。✓（修复 1 补齐了全重复提交分支）
7. **数据范围**：`requireTask` 学员本人/教师 `isTeacherOfClass`/管理员全量；completeVideo 与三步骤接口二次收紧本人；overview 校验带班；config 校验"带班 ∩ 课程分配班级"非空；映射维护沿用题目标注 TEACHER/ADMIN 口径。均有越权单测。✓
8. **快照**：`config_snapshot` 生成时序列化 `RemediationPolicy`，门禁/取题/提交/完成判定全部读快照，不回查课程配置（有单测验证快照内容与默认回退：班级 0/多课程、无班、未配置 → 内置默认）。✓
9. **报告升级**：`buildSuggestions` 仅下发已发布映射视频（videoId/title/fileUrl/coverUrl，sort 升序去重），无映射回退纯文字；`Suggestion` delegating creator 兼容旧存量字符串 JSON（有往返单测）；前端 `ExamResult.vue` 双格式兼容渲染；`CourseService.java` 已删除（工作区无此文件）。✓
- **脱敏**：`SanitizedQuestion` 仅 title/type/multi/choices(content,sort)；SubmitResult 仅对错与反馈；报告 LIMITED 不下发答案与作答。✓
- **record 构造器映射**：`application.yaml` 开启 `arg-name-based-constructor-auto-mapping`，mybatis-plus 3.5.7（mybatis 3.5.16）支持，record 组件名反射可用，Summary/StudentRow/PointRow 列别名与参数名核对一致。✓
- **事务边界（报告侧）**：`DiagnosticReportService.generate` 无外层事务，报告先落库再生成任务，任务失败仅记日志降级（报告仍 READY，retry 可重建），符合设计风险对策。✓
- **前端契约**：request.js 统一解包 Result.data；三页字段名/类型与后端 record 逐一核对；`/videos/:id` 路由与图标引用均存在；`npm run build` 通过。✓

## 验证结果

- 后端：`mvn -o -Dmaven.resources.skip=true -Dtest=!ExamSystemServerApplicationTests test` → **Tests run: 221, Failures: 0, Errors: 0, Skipped: 0**（基线 220 + 本次新增 1 个并发重复提交用例），BUILD SUCCESS。
- 前端：`npm run build` → **✓ built in 29.34s** 通过（chunk 体积告警为既有历史问题）。
- 未执行任何 git commit/push；未触碰 application-local.yaml；增量 SQL 草稿未执行（待用户确认目标库后独立进行，见规格验收 10）。
