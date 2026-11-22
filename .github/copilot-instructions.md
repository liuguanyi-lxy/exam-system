# Copilot 项目指令（exam-system）

完整协作规范见仓库根 `AGENTS.md`。

## 开发流程（每个功能固定走一遍）

功能分支 + GitHub PR（PR 是用户 review 载体，merge 即提交推送）：

1. 规格（验收标准先行，放 `docs/specs/`，用户确认后才设计）→ 2. 计划（重大产品决策用户拍板）→ 3. 任务拆解 → 4. 小步实施（每步可独立验证）→ 5. AI review（审查+修复+全量测试通过）→ 6. 用户 review（开 PR，给 diff 摘要+验收对照+运行方式，等确认）→ 7. 优化到验收全绿 → 8. merge PR（=提交推送，删分支）→ 9. 复盘（沉淀踩坑）

## Git

- 不自动 commit/push（流程第 8 步除外）；提交前展示变更摘要；commit message 简洁中文
- 动手前基线检查；历史重写类操作必须用户当次授权
- 红线：删除文件/git 历史、改密钥配置、公开发布——先问用户

## 项目要点

- monorepo：`exam-system-server`（Spring Boot 3）/ `exam-system-web`（Vue 3）
- 质量基线：`mvn test` 全绿 + `npm run build` 通过
- 密钥只在 application-local.yaml（gitignored）；动手前先读 `docs/DEV-ROADMAP.md` 与 `docs/DEV-PROGRESS.md`
