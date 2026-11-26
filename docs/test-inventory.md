# M7 自动化测试清单（路线图 → 用例映射）

> 对应 DEV-ROADMAP.md「M7 细化」自动化测试清单。盘点基准 2026-11-26，M7 新增用例见 ★。
> 全部为 Mockito 单测（不连库），连库行为由真实链路验证兜底（惯例见 specs/m7-test-deploy.md 边界）。

| 专项 | 覆盖用例 | 说明 |
| --- | --- | --- |
| 班级权限与越权 | ClassControllerScopeTest、ClassScopeServiceImplTest、ExamControllerScopeTest、ExamRecordControllerScopeTest、StatsServiceImplScopeTest、TeachingStatsControllerScopeTest、RemediationTaskServiceTest（他人任务/无带班教师拒绝）、RemediationTeacherServiceTest（无权配置/概览拒绝）、★AuthInterceptorRoleTest（@RequireRole 角色不足 403） | Controller 层查 SQL 范围，service 层查归属拒绝，拦截器层查角色注解 |
| 考前答案泄露 | ★ExamRecordDetailSanitizeTest（进行中/已结束脱敏、已批阅可见、教师不脱敏、非本人拒绝） | 口径：未批阅学生视角不下发 answer/analysis/isCorrect |
| 诊断与追问归属 | DiagnosticReportTest（wrongOwner/CrossClass 拒绝、WAITING_FOR_GRADE 不可读）、QuestionChatServiceTest | 追问记录关联 userId/examRecordId |
| 视频完成与重看 | ExternalVideoPolicyTest、LearningServiceImplTest、CourseLearningQueryServiceTest | 外链白名单、会话心跳去重、重看不重复计观看 |
| 重复交卷 | ExamSubmitAnswerTest（已结束/已批阅拒绝、同题覆盖保留一条） | 交卷事务内去重 |
| AI 超时限流降级 | ★ExamGradeFallbackTest（批量失败回退单题、单题失败标记不阻断整卷、总评失败兜底文案） | 判卷失败 ai_correction 留「判题过程出现问题（错误类型：X）」可识别标记 |
| 人工复核 | DiagnosticReportTest（review 权限/不可覆盖/replay、fail 后 token-bound retry）、★retryRequeuesReportAndUnsubmittedRecordIsRejected、limitedConfidence 脱敏 | 教师复核接口 POST /api/diagnostic/{id}/review |
| 补课与掌握度更新 | RemediationTaskServiceTest（快照口径/视频门禁/幂等）、RemediationStepTest（重复提交不重复计分/越权题拒绝）、RemediationGradingServiceTest | score>=60 折算掌握度对照快照阈值 |
| 部署验证 | B3 新机器演练（本机空库 exam_system_demo 全流程）+ B4 三角色冒烟，记录见 docs/DEPLOY.md | 不写连库集成测试（惯例） |

## M7 新增文件

- `controller/ExamRecordDetailSanitizeTest.java`（5 用例）
- `service/impl/ExamGradeFallbackTest.java`（3 用例）
- `config/AuthInterceptorRoleTest.java`（4 用例）
- `service/DiagnosticReportTest.java`（新增 retry 用例 1 个）

## 质量基线

`mvn test` 全量绿（236 → 249+）；前端 `npm run build` 通过。
