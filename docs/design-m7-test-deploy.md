# M7 技术方案：测试补齐与部署交付

> 规格：docs/specs/m7-test-deploy.md（用户已确认验收标准与两项决策：本机模拟新机器、空库+demo 脚本为交付标准）

## 现状核实结论（2026-11-26）

| 验收项 | 现状 | 结论 |
| --- | --- | --- |
| A1 考前答案泄露 | ExamRecordController L195-199 已实现：未批阅记录学生视角 stripAnswer；列表 L98-102 同口径 | 纯补回归用例 |
| A2 AI 降级 | ExamServiceImpl.gradeExam：批量 AI 失败 → parallelGradeFallback 单题并行 → 结果 null → 主循环 catch 分支（score=0、ai_correction 写错误类型），不阻断整卷 | 纯补用例 |
| A3 人工复核 | 判卷失败在 ai_correction 留可识别错误标记；报告级有 confidence（LIMITED/DEGRADED）；教师复核接口 POST /api/diagnostic/{recordId}/review 已存在（+retry 重生成） | 纯补用例 |
| A4 控制器归属 | DiagnosticReportController / RemediationController 无 ScopeTest | 纯补用例 |

**M7 无表结构改动、无实现改动**（除非测试过程中暴露真实缺陷，按 fix 小步修复）。

## 实现方案

### A 组：测试补齐（全部 Mockito 单测，沿用现有风格）

1. **A1** `ExamRecordDetailSanitizeTest`：参考 ExamRecordControllerScopeTest 的 mock 模式。场景：
   - 学生查「进行中」记录 → paper.questions 无 answer/analysis/isCorrect
   - 学生查「已结束」（未批阅）记录 → 同上脱敏
   - 学生查「已批阅」记录 → 答案可见（错题本豁免口径不变）
2. **A2** `ExamGradeFallbackTest`：
   - aiService 批量判卷抛异常 → fallback 单题成功 → 该题按 AI 结果落库、整卷出分
   - 批量失败且单题也失败 → 该题 ai_correction 含「AI判题服务异常」、score=0，其余客观题正常出分，gradeExam 不抛异常
3. **A3** 并入 A2 + `DiagnosticReportReviewTest`：retry 重生成、教师 review 后报告状态流转（复用 DiagnosticReportTest 的 mock 口径）
4. **A4** `DiagnosticReportControllerScopeTest`、`RemediationControllerScopeTest`：学生 A 访问学生 B 的报告/补课任务 → 拒绝
5. **A5** `docs/test-inventory.md`：M7 九项专项 → 测试类映射表

### B 组：部署交付

6. **B1** DEPLOY.md：SQL 清单补全 12 条、新增「备份与恢复」（mysqldump 全量恢复步骤、MinIO 数据目录）、M7 演练记录章节
7. **B2** `sql/2026-11-26_demo_data.sql`：
   - 依赖：全量 schema + 前 12 个增量脚本执行完毕
   - 内容：1 班（M7演示班）+ demo_teacher + demo_student1-3（BCrypt 哈希从现有库 users 表取，密码 123456）+ 班级成员 + 1 门课程/章节/视频 + 知识点 + 试卷（2 单选+1 多选+1 判断+1 简答）+ 考试分配到班
   - 不造成绩/补课数据：冒烟时由真实操作产生，保证数据链路真实
   - 脚本头部注明「一次性执行，依赖前置脚本清单」
8. **B3** 新机器演练（本机空库 exam_system_demo）：建库 → 导出 schema（从现有库 --no-data）→ 12 增量 → demo 脚本 → `mvn clean package` → war 启动（新端口/新库）→ 前端 build。问题回写 DEPLOY.md
9. **B4** 冒烟（浏览器）：demo_teacher 建班级分配考试看统计 / demo_student1 考试交卷自动判卷出分

## 风险与对策

- schema 导出可能含云端库环境痕迹（AUTO_INCREMENT 起点等）→ 接受，演练记录中注明
- demo 脚本误执行到云端库 → 只在本地 exam_system_demo 执行；脚本头部显著警告
- 测试暴露真实越权/脱敏缺陷 → 立即修复并补用例，不隐瞒降级
- war 打包时间较长 → 后台执行

## 任务拆解（小步，每步可独立验证）

| # | 任务 | 验证 |
| --- | --- | --- |
| T1 | A1 答案泄露回归用例 | 单测通过 |
| T2 | A2 AI 降级用例 | 单测通过 |
| T3 | A3 复核流转用例 | 单测通过 |
| T4 | A4 两个 ScopeTest | 单测通过 |
| T5 | A5 测试清单映射文档 | 文档落盘 |
| T6 | B1 DEPLOY.md 修订 | 文档核对 |
| T7 | B2 demo 数据脚本 | 空库试执行通过 |
| T8 | B3 全流程演练 + 问题回写 | 三角色冒烟通过 |
| T9 | AI review + 全量测试 + PR | mvn test 全绿 + build 通过 |
