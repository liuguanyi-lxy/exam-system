# 开发进度快照

> 用途：跨会话保留开发进度。恢复开发前先读本文件与 git log，再继续。
> 最后更新：2026-11-16

## 已完成并推送（main 分支，github.com/liuguanyi-lxy/exam-system）

| 提交 | 内容 |
| --- | --- |
| 0a81716 | monorepo 建立（前后端两条提交链保留）+ README/PRODUCT.md |
| 28c9614 | 考试主链路安全修复：交卷校验/刷新恢复/记录 userId 归属（含 SQL） |
| 05a740b | 视频外链功能 + 上传链路 NPE 修复 + 管理端 adminId 用登录上下文 |
| 7b38cb2 | 权限后端化：@RequireRole 注解 + 20 处接口 + 题目详情脱敏 |
| 1a692b1 | AI 批量判卷（每次交卷 N+1 → 2 次）+ 失败回退 + 提示词防注入 |
| 5c60094 | 视频观看统计会话化（心跳+sendBeacon）+ 记录列表 N+1 修复 + docs/DEPLOY.md |

## 已完成待提交：错题本 + 题目收藏（2026-11-16）

子代理流水线：api-designer 契约 → architect 表设计 → 2×general-purpose 实现 → code-reviewer 审查 → 2×general-purpose 修复 → test-writer 测试。

**产物**
- 设计文档：`docs/design-wrong-questions-favorites.md`（派生方案，判卷链零改动）
- API 契约：`docs/api-wrong-questions-favorites.md`
- 审查报告：`docs/review-wrong-questions-favorites.md`（含已实证的高危漏洞）
- 增量 SQL：`sql/2026-11-15_wrong_questions_favorites.sql`（已在本机+云端执行）

**功能**
- 后端：`WrongQuestionController`(`/api/wrong-questions`) + `FavoriteController`(`/api/question-favorites`)，共 7 接口；新增实体/VO/Mapper/Service 16 个文件
- 前端：错题本页 `views/wrong-question/WrongQuestionBook.vue`、收藏页 `views/favorite/MyFavorites.vue`、题目详情页 `views/question/QuestionDetail.vue`；导航/路由限 STUDENT
- 错题本口径：从 `answer_record JOIN exam_records` 按 `user_id` 实时派生，`is_correct=0`；已掌握标记表 LEFT JOIN + 时间比较实现"再错自动回归"

**审查发现并修复的高危漏洞（已实测复现+复验）**
- 伪造答题记录读取任意题库答案：`customSubmitAnswer` 不校验题目是否属于本试卷 → 判卷 catch 分支写 `is_correct=0` → 错题本下发答案。已修：提交时按试卷校验+去重；判卷时过滤非本卷答题记录
- `GET /api/questions/random` 学生可拿答案解析（既有漏洞）：已加 `@RequireRole({"TEACHER","ADMIN"})`

**同时修复的中/低危**
- 错题聚合 SQL 回连 1:N 导致分页虚高 → 改为聚合 `answer_record.id`
- 已掌握 Tab 前端过滤丢数据 → 后端加 `masteredOnly`，前端改服务端分页
- 分页 size/page 无上限 → 归一化（size≤100, page≤10000）
- 收藏状态对已删除题目误报 true → 补题目存在性校验
- 题目详情缺 `categoryName` → QuestionMapper JOIN 补齐
- `groupingBy` null 键 NPE → 过滤
- 前端教师/管理员可见学生入口并触发 403 → 导航/路由限 STUDENT

**验证结果**
- 后端 `mvn test`：47 用例全绿（基线 18 + 新增 29，含回退验证）
- 前端 `npm run build` 通过
- 联调实测（本地后端 + 云端 MySQL/Redis/MinIO）：漏洞攻击被拒、错题本 5 条字段完整、标记掌握闭环、收藏幂等、教师 403、size 上限生效

## 云端环境（2026-11-16 建立）

- 阿里云服务器 `47.119.114.135`，Tailscale `100.118.146.116`（主机名 `iZwz9e7qwjot5seuo3vvyvZ`）
- Docker 服务：`ts_mysql`(MySQL 8.0.30:3306) / `gmalldocker_redis`(6379，无密码) / `gmalldocker_minio`(9000，admin)
- `exam_system` 库已建，从本机导出 16 表 966 行导入；5 个增量脚本全部执行
- `application-local.yaml` 已切到云端（gitignored，不提交）；旧备份在 `D:\java_study\.db-migrate\`
- AI 服务已切换为 DeepSeek，`/models` 接口验证通过
- **安全提醒**：服务器 3306/6379/9000 映射到 0.0.0.0（公网可达），建议安全组只放行 Tailscale 网段

## 待办（按价值排序）

1. 提交并推送本轮错题本功能（等用户确认）
2. 契约第 8 节仍写"自动清标记重新进场"，与修订后 6.3 派生口径措辞冲突，建议统一
3. 低危未修：响应混入实体字段（需建专用 VO）、`hasWrongRecord` IN 列表规模隐患
4. 历史脏数据：4 条 active 答题记录的 question_id 不在对应试卷（2026-05 种子数据），不影响错题本展示
5. 可选方向：Redis 验证码/限流、题目收藏在考试结果页批量入口、错题本导出

## 协作约束

- 编程开发必须用本地子代理（全局 AGENTS.md「开发协作方式」）；并发上限 2；中断的用 SendMessage 续跑
- git 纪律见 AGENTS.md：不自动 commit/push；历史重写需当次明确授权
