# Agent 协作规范（exam-system 仓库）

所有在本仓库工作的 AI 编码 agent 必须遵循本规范。

## 先读这些

| 文档 | 内容 |
| --- | --- |
| `PRODUCT.md` | 产品定位与三角色权限边界（单机构培训） |
| `docs/DEV-ROADMAP.md` | 开发路线图 M1–M7（当前阶段的细化与验收标准） |
| `docs/DEV-PROGRESS.md` | 进度快照（已完成/进行中/待办） |
| `docs/DEPLOY.md` | 部署与环境（增量 SQL 清单在它的第 2 节） |
| `docs/specs/` | 各功能规格（验收标准） |
| `docs/design-*.md` / `docs/api-*.md` / `docs/review-*.md` | 历史契约、设计与审查报告 |

## 开发流程（每个功能固定走一遍）

- 分支策略：功能分支 + GitHub PR；merge 到 main 即提交并推送（流程内授权，merge 前展示变更摘要）
1. **规格**：写功能规格（要解决什么 + 验收标准）到 `docs/specs/`；用户确认验收标准后才进技术设计
2. **计划**：契约/表设计/实现方案与风险；重大产品决策由用户拍板
3. **任务拆解**：拆成可独立验证的小步
4. **小步实施**：逐步实现，每步可独立验证（编译/测试）
5. **AI review**：全面代码审查并修复，全量测试通过
6. **用户 review**：推送功能分支开 PR，提供 diff 摘要 + 验收对照 + 运行方式，等用户确认
7. **优化**：按用户 review 结论修复，直到验收全绿
8. **合并**：merge PR（= 提交并推送），删除功能分支
9. **复盘**：沉淀踩坑与流程调整，写进 `docs/DEV-PROGRESS.md` 与 agent 记忆

## 子代理分工（支持子代理体系的 agent 适用）

- 设计：api-designer（接口契约）/ architect（表结构与实现方案）
- 实现：general-purpose（边界隔离：后端代理不碰前端，反之亦然）
- 测试：test-writer（不写连库的集成测试）
- 审查：code-reviewer / security-auditor（审查报告落盘 `docs/review-*.md`）
- 主会话：任务拆解、上下文准备、基线校验、git 操作；同一仓库的代码实施默认串行，避免多个 agent 同时编辑共享工作区。只有任务文件边界完全独立且模型/网络稳定时才并行只读审查或独立实现
- 一旦出现额度/连接失败，立即停止扩散并发，先盘点已落盘状态，再按单个小闭环续跑；优先用 SendMessage 恢复已有 agent 上下文，不反复重开
- 不支持子代理的 agent：按上述 role 顺序自行扮演，但"AI review"与"用户 review"两个关口不可跳过

## Git 纪律

- 功能分支命名：`feat/<功能>`、`fix/<问题>`、`docs/<主题>`
- 禁止直接 push 到 main（hotfix 除外，也建议走 PR）
- 提交信息：简洁中文，说明"做了什么 + 修了什么"
- 增量 SQL（`exam-system-server/sql/`）按日期顺序执行，**不可重复执行**，每次执行前先查目标库当前状态
- 历史重写（force push / reset --hard / rebase / filter-repo）必须用户当次明确授权

## 项目技术要点

- monorepo：`exam-system-server`（Java 17 / Spring Boot 3 / MyBatis-Plus）+ `exam-system-web`（Vue 3 / Vite / Pinia / Element Plus）
- 质量基线：后端 `mvn test` 全绿（当前 47+ 用例）；前端 `npm run build` 通过
- 密钥只放 `exam-system-server/src/main/resources/application-local.yaml`（gitignored）或环境变量，绝不入仓库
- 角色与权限：`@RequireRole` 注解 + `AuthInterceptor`；数据范围校验参考 `ExamRecordController` 的归属模式
- 脱敏口径：学生不下发答案解析；错题本是唯一豁免场景（复习需要）
- 云端环境：MySQL/Redis/MinIO 在阿里云（Tailscale `100.118.146.116`），凭据在 application-local.yaml
