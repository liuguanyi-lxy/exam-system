# 代码审查报告：错题本 + 题目收藏

- 日期：2026-11-16
- 范围：错题本/收藏功能实现（后端 16 新增 2 修改，前端 5 新增 3 修改）
- 依据：`docs/design-wrong-questions-favorites.md`、`docs/api-wrong-questions-favorites.md`
- 结论：**存在可实际利用的答案泄露漏洞，不具备合入条件**；修复高/中危项后可合入

## 已实证的高危漏洞

**伪造答题记录读取任意题库题目的答案解析**（已本地实测确认）

- 攻击链：开考任意试卷 → `POST /api/exams/{id}/submit` 提交任意 `questionId`（后端不校验是否属于本试卷）→ `POST /api/exams/{id}/grade` 判卷 → 该题因"不在试卷中"抛异常，catch 分支写入 `is_correct=0` → 错题本 SQL 以 `is_correct=0` 为准并完整下发答案
- 实测结果：提交 paper1 之外的 `questionId=1002` 后，`GET /api/wrong-questions/list` 返回该题题干、正确答案 `C`、完整解析、`choices[].isCorrect`
- 影响：学生可枚举整个题库（含未发布试卷题目），绕过全部脱敏约束
- 复现脚本：`D:\java_study\.db-migrate\verify_leak.py`（测试记录已清理）

## 修复清单（按优先级）

### 高

| # | 位置 | 问题 | 修复要求 |
|---|---|---|---|
| H1 | `ExamServiceImpl.customSubmitAnswer` / `gradeExam` catch 分支 | 提交的 questionId 不校验试卷归属；判卷 catch 分支把未知题写成 `is_correct=0` | 1) 提交时按 `examRecord.examId` 查 `paper_question`，拒绝不属于本卷的 questionId，并按 questionId 去重；2) 判卷时对不在试卷内的答题记录不写 `is_correct=0`（跳过或清理），不能伪造错题 |
| H2 | `QuestionController` `GET /api/questions/random` | 无 `@RequireRole`，也无脱敏，学生可随机抽题拿全部答案解析（既有漏洞） | 按注释定位（智能组卷）加 `@RequireRole({"TEACHER","ADMIN"})`；若要坚持给学生用，则必须 `QuestionSanitizer.stripAnswer` |

### 中

| # | 位置 | 问题 | 修复要求 |
|---|---|---|---|
| M1 | `AnswerRecordMapper.xml` 错题 SQL 回连 `answer_record d` | 只按 `(exam_record_id, question_id)` 回连且未加 `d.is_correct=0`，重复答题记录会导致 1:N、分页 total 虚高、最近错误答案取错行 | 内层聚合改为取"最近一次错误答题记录 id"（`group_concat(ar.id order by ar.create_time desc, ar.id desc)` + substring_index），外层 `join answer_record d on d.id = w.last_answer_id` 并加 `d.is_correct = 0` |
| M2 | 已掌握 Tab（前后端） | 前端用 `includeMastered=true` 拉全量再本地过滤（size=100 × 50 页上限，超限静默丢数据，且每页带完整答案解析） | 后端 `GET /api/wrong-questions/list` 增加 `masteredOnly` 参数（`true` 时走 `selectMasteredQuestionPage` 语义，仅返回已掌握）；前端改为服务端分页、删除本地过滤与 50 页上限 |
| M3 | 契约 6.3 vs 设计文档 | 契约要求"判卷落库时清除已掌握标记"，设计文档要求"判卷链零改动"，代码采用时间比较 | **采用时间比较方案（设计文档）**，修订 API 契约 6.3 为"由 `mastered_time < 最近一次答错时间` 派生回归，判卷链不改"，并在文档注明边缘行为 |
| M4 | `WrongQuestionServiceImpl` / `FavoriteServiceImpl` 分页 | `page`/`size` 无上限，`size=100000` 可拉大量含答案解析数据 | 服务端统一 `size` 上限 100、`page` 上限 10000，并在 Swagger 注明 |
| M5 | 前端 `UserLayout.vue` / `router/index.js` / `QuestionDetail.vue` | 教师/管理员能看到学生专属入口，进入后 403 报错；收藏按钮对所有角色显示 | 导航与路由限 STUDENT（路由 `meta.roles:['STUDENT']` + 导航 v-if）；题目详情页收藏状态请求与按钮仅对学生执行 |

### 低（本次一并修）

| # | 位置 | 问题 | 修复要求 |
|---|---|---|---|
| L1 | 错题本前端 | 客观题 `aiCorrection="回答错误"` 被渲染成"AI 评语" | 前端仅对 `TEXT`/`CODE` 展示 AI 评语 |
| L2 | `FavoriteServiceImpl` 状态查询 | 题目已删除时仍返回 `favorited=true`（契约要求 false） | 状态查询 JOIN `questions` 且 `is_deleted=0`，不存在返回 false |
| L3 | `QuestionDetail.vue` 分类名 | 读取 `category?.name || categoryName`，但后端详情不返回分类名字段，永远为空 | 后端详情查询补 `categoryName`（JOIN categories），或前端移除该标签 |
| L4 | `WrongQuestionServiceImpl` / `FavoriteServiceImpl` 分组 | `Collectors.groupingBy(QuestionChoice::getQuestionId)` 遇 null 键抛 NPE | 分组前过滤 `questionId != null` |

### 暂不修（记录在案）

- L5 响应混入实体字段（`QuestionItemVo` choices/answer 复用实体，带出多余字段）：需要为选项/答案建专用 VO，改动面偏大，留待后续
- L6 `hasWrongRecord` 先拉全部考试记录再 IN：单人数据量级可接受，规模上来再改
- 契约偏离项（`/list` 路径、`/api/question-favorites`、`lastWrongTime` 口径、新增 VO）**不是问题**——设计文档已声明接口以契约为准

## 审查确认无问题的点

- 前后端字段名与结构全部对得上（`questionId/mastered/wrongCount/lastWrongAnswer/lastWrongTime/lastPaperName/aiCorrection/question.*/favoriteTime/favorited`）
- `is_deleted` 过滤齐全；收藏/掌握两表物理删除正确
- 收藏列表 SQL 的 DB 侧筛选 + 分页语义正确
- 幂等与并发：`DuplicateKeyException` 兜底到位，物理删除 0 行仍成功
- 安全边界：所有 `userId` 均来自 `@RequestAttribute`，无外部 userId 参数；参数全部 `#{}` 无注入
