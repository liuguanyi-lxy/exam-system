# M7 规格：测试补齐与部署交付

## 要解决什么

M1–M6 功能已全部合并并通过浏览器端到端联调，但离"可交真实机构试用"还差两件事：

1. **自动化测试按 M7 清单盘点补齐**：路线图列出的专项（越权、答案泄露、AI 降级等）覆盖不均，部分缺口无用例兜底。
2. **部署交付链路从未完整走通**：DEPLOY.md 增量 SQL 清单缺 M4/M5 两条；备份恢复无文档；无一套可复用的演示数据；"新机器完整部署"从未实际演练。

## 现状盘点（2026-11-26）

### 测试覆盖现状（现有 36 个测试类，236 用例全绿）

| M7 清单项 | 覆盖情况 |
| --- | --- |
| 班级权限与越权 | ✅ 已覆盖：Class/Exam/ExamRecord/TeachingStats 四个 Controller ScopeTest + ClassScopeServiceImplTest + StatsServiceImplScopeTest + AuthInterceptorRankingTest |
| 诊断与追问归属 | ✅ 已覆盖：DiagnosticReportTest、QuestionChatServiceTest |
| 视频完成与重看 | ✅ 已覆盖：ExternalVideoPolicyTest、LearningServiceImplTest、CourseLearningQueryServiceTest |
| 重复交卷 | ✅ 已覆盖：ExamSubmitAnswerTest（已结束/已批阅拒绝重复提交） |
| 补课与掌握度更新 | ✅ 已覆盖：RemediationTaskServiceTest、RemediationGradingServiceTest、RemediationStepTest |
| M6 教学统计 | ✅ 已覆盖：TeachingStatsServiceTest、TeachingStatsControllerScopeTest |
| **考前答案泄露** | ❌ 缺口：未交卷记录不暴露答案/解析/诊断，无显式用例（H1 修复时顺带收紧过，但没有回归用例钉住） |
| **AI 超时/限流降级** | ❌ 缺口：AI 判卷批量失败回退单题、AI 服务不可用时的用户可见行为，无用例 |
| **人工复核** | ⚠️ 待确认：DiagnosticReportTest 覆盖了报告生成，但 AI 失败标记人工复核状态流转未验证 |
| **M4/M5 控制器归属** | ⚠️ 待确认：DiagnosticReportController / RemediationController / MyClassController 无 ScopeTest（越权风险点） |

### 部署现状缺口

- DEPLOY.md 第 2 节 SQL 清单缺 `2026-11-24_m4_diagnostic_reports.sql`、`2026-11-25_m5_remediation.sql`（实际 sql/ 目录共 12 个脚本）
- 备份恢复：无文档（MySQL dump 恢复、MinIO 数据目录）
- 演示数据：仅 `2026-11-16_learning_external_videos.sql` 有种子，缺一套完整演示数据（班级/学员/课程/考试/成绩/补课任务）
- 新机器部署：从未从零演练过

## 验收标准

### A. 测试补齐

- [ ] A1 考前答案泄露回归用例：状态「进行中」的记录，学生取详情时不下发 question.answer/analysis、不下发判卷结果；交卷后（已结束/已批阅）才可见
- [ ] A2 AI 降级用例：批量 AI 判卷失败 → 回退单题并行判卷；单题仍失败 → 该题标记判卷失败/待人工复核，其余题正常出分，接口不 500
- [ ] A3 人工复核流转用例：AI 判卷失败或低置信度 → 记录/报告进入待人工复核状态 → 教师复核后状态更新
- [ ] A4 M4/M5 控制器 ScopeTest：DiagnosticReportController、RemediationController（含 MyClassController 视情况）的越权访问被拒
- [ ] A5 测试清单映射文档：ROADMAP M7 的 9 项专项 → 具体测试类/用例的映射表，落到 docs/（盘点即文档，缺口用例补齐后更新）
- [ ] A6 `mvn test` 全绿（现有 236 + 新增 ≥ 6 用例）

### B. 部署交付

- [ ] B1 DEPLOY.md 修订：SQL 清单补全至 12 个、新增"备份与恢复"章节（MySQL 全量+增量恢复、MinIO 数据目录备份）、上线检查清单核对每项
- [ ] B2 演示数据脚本：`exam-system-server/sql/2026-11-26_demo_data.sql`，一套完整演示场景（1 个班 + 3 学员 + 1 课程 + 1 场考试 + 成绩与补课任务），脚本头部注明一次性执行、依赖哪些前置脚本
- [ ] B3 新机器部署演练：在本机用**全新空库**（exam_system_demo）按 DEPLOY.md 从建库 → schema 导出 → 12 个增量 → 演示数据 → 后端打包启动 → 前端构建，全流程走通一次，将遇到的问题回写 DEPLOY.md
- [ ] B4 演练后冒烟：admin/teacher1/student1 三角色各完成一个核心操作（建班分配/查看教学统计/参加考试出分）

## 边界（不进 M7）

- 不引入 CI/CD 流水线（本地演练为主，GitHub Actions 另立事项）
- 不做自动化部署脚本（Ansible/Docker Compose 留给真实试用需求出现后再做）
- 不写连库的集成测试（沿用现有项目惯例：Mockito 单测 + 真实链路人工/浏览器验证）

## 待用户拍板的决策

1. **部署演练目标形态**：本机模拟新机器（推荐，零成本）即可，还是需要真正的另一台机器/云主机？
2. **演示数据口径**：复用现有云端库已有数据即可，还是 exam_system_demo 空库 + 演示数据脚本作为交付标准形态？
