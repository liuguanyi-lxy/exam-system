# 开发进度快照

> 用途：跨会话保留开发进度。恢复开发前先读本文件与 git log，再继续。
> 最后更新：2026-11-25

## M1（已发布到功能分支，环境验收待补）

- 已有工作区实现覆盖班级/成员与 Excel 导入、知识点标注/筛选，以及记录和概览初步数据范围；不代表 M1 验收完成。
- 本轮补强教师单卷统计、记录详情与删除的负责班级归属校验；公开 `/api/stats/test` 仅返回状态或通用错误，不下发全机构统计/异常信息。路线图、规格、设计与 API 契约已统一为“题目/试卷是机构内共享资源，但教师成绩按负责班级过滤”。
- 后续 M1 安全复核发现排行榜 `/api/exam-records/ranking` 曾匿名全量返回成绩；现已移出公开白名单，要求三类登录角色，并让教师按负责班级、学员按所属班级过滤（无班学员仅本人），管理员仍全量。又发现教师可触发跨班 `/grade`，现增加负责班级校验。新增控制器范围测试；两个定向测试类 11 项通过。HTTP 拦截器链及数据库 XML 查询尚未做真实集成验证。
- 后续回归发现并修复 Excel 导入接受 TEACHER/ADMIN 全局账号作为学员的问题：现有非 STUDENT 账号在事务前拒绝，且不修改全局角色；成员考试摘要仅聚合班内学员，教师成员不暴露个人考试记录。
- 本轮发现班级列表与成员列表未限制分页参数，现统一收敛页码为 1–10000、每页 1–100 条；控制器边界测试验证了超大、负数和零输入均被修正。
- 修复班级编辑表单清空可选日期/方向时后端或前端保留旧值的问题；PUT 明确用 `null` 清日期、空字符串清方向，并增加 Mock 更新 SQL 断言。
- 本轮隔离定向运行 M1 权限、统计、知识点、导入与摘要测试：32 项通过；新增控制器/学员越权、Mock 重复导入、班级更新、排行榜/判卷范围、匿名排行榜拦截及班级分页上限用例后，全部非 Spring 上下文后端回归 92 项通过。重导幂等仅由 Mock 模拟唯一键冲突，不代表真实数据库事务验证。前端 `npm run build` 在 `envDir: false` 且清空构建进程 `VITE_*` 变量后通过（仅 Sass 弃用和 chunk 体积警告）。Spring 上下文测试未运行，以免连接真实服务/数据库；不读取私有配置、不连云库、不执行增量 SQL。
- 待决文档/实现冲突：API 契约与当前 service 把班级名限制为全局唯一；技术设计与 M1 SQL 明确允许跨期重名。此行为涉及产品规则，本轮不擅自改变；需在 review 前统一。
- 2026-11-24：补齐考试记录分页约束和知识点管理回归。批量排序空条目或缺字段在 Mapper 查询前拒绝；知识点专项 14 项通过，非 Spring 上下文后端回归共 100 项通过。知识点管理页在同排序号或页边界禁用无效移动，Chromium 使用模拟接口验证了禁用、提示与正常交换请求，未连接真实后端。
  - 隔离前端生产构建再次通过；浏览器模拟接口和生产构建均不等于端到端验收。
- 2026-11-24：新增建班默认启用、停启用保留成员、删除限定班级清理、手动添加学员幂等、教师分配去重及整批角色校验、按角色移除的 Mock 用例。班级/成员/Excel 专项 24 项通过，最新非 Spring 上下文后端回归 108 项通过；未验证真实数据库事务。
- 2026-11-24：班级控制器专项 7 项通过，其中 3 项使用独立 MockMvc，覆盖教师负责班级详情成功、跨班详情拒绝、教师不能删班/移除成员、匿名拒绝及「我的班级」取认证学员 ID。健康接口 MockMvc 验证匿名仅可检查状态、不可读取机构概览。MockMvc 使用模拟 token 和服务，不代表部署环境的 HTTP 或数据库验收。
- 2026-11-24：成员筛选专项 10 项通过，验证角色、关键词和班级条件组合，以及无匹配返回空页且不查成员或成绩。最新隔离后端回归 114 项通过。Chromium 模拟接口验证管理员建班、详情跳转、编辑、停启用及删除。
- Excel 导入页拒绝非法后缀并清除上次选择，模板下载失败有明确反馈。Chromium 使用模拟接口验证模板下载、HTTP 403 提示、大写 `.XLSX` 上传和逐行失败原因。
- M1 已提交 `10535cf` 并推送功能分支。完整验收 0/7；真实数据库迁移、事务、部署 HTTP 和前后端联通待验。后续功能开发不等待环境验收。

### M1 验收证据

| 项目 | 已验证 | 仍未验收 |
| --- | --- | --- |
| 建班、编辑、停启用、删除、成员操作 | `ClassServiceLifecycleTest`、`ClassServiceUpdateTest`、`ClassServiceMemberSummaryTest` 验证 Mapper 调用、清空可选字段、角色隔离、筛选与幂等；管理员班级页面通过模拟接口浏览器验证 | 真实事务及页面与后端联通；重名规则待定 |
| Excel 导入 | `StudentExcelImportTest` 使用内存工作簿和 Mock 验证解析、行级失败、角色约束及重复导入；浏览器模拟接口验证模板与导入交互 | 真实唯一索引、逐行事务和上传/模板端到端 |
| 教师和学员班级范围 | `ClassControllerScopeTest`、`ClassScopeServiceImplTest`、`ClassServiceMemberSummaryTest` 覆盖教师列表/单班/成员、学员身份与同学列表范围；其中班级控制器部分用例走 MockMvc | 真实多账号联调 |
| 知识点管理、标注和筛选 | `KnowledgePointManagementTest`、`QuestionKnowledgePointServiceTest` 和模拟接口浏览器排序验证 | 真实数据库关联写入及搜索/编辑回显联调 |
| 教师记录、统计、概览及公共健康 | `ExamRecordControllerScopeTest`、`StatsServiceImplScopeTest`、`StatsControllerHealthTest` 等覆盖范围、空范围和健康响应；MockMvc 验证匿名健康与概览路由的权限区别 | 真实聚合查询与部署 HTTP 验收 |
| 共享题库/试卷 | 已统一路线图、规格、设计与 API 契约，不套学员 ID 过滤 | 不新增班级归属规则 |

## M2（功能已接通，环境验收待补）

- 已接通课程班级分配的查询和替换 API。教师只操作自己负责班级的分配，管理员可操作全部；差量更新保留未变化及范围外关系。学员课程的分配时间查询已改为参数绑定，空集合不匹配任何记录。
- 课程管理已接通分页、创建、信息清空、发布/撤回、删除与分配入口。教师仅可修改创建的课程；课程列表仍可见其负责班级的分配，班级数按教师范围计算。课程专项 13 项通过（包含 Mapper SQL 生成和服务 Mock），前端生产构建通过；未验证真实数据库级联和端到端。
- 课程章节和视频管理已接通：增删改、相邻排序、站内视频时长检查、外链白名单、必学与完成比例、移除后重挂。课程管理详情批量读取关联视频；修改课程配置不改共享视频。非 Spring 上下文回归 137 项通过，前端生产构建通过；未验证数据库行锁、唯一约束或实际视频播放。
- 学员可查看启用班级中的已发布课程、章节和个人进度；教师学习情况限负责且已分配的班级。站内按有效观看比例完成；外链只计本人会话的有效停留，满 60 秒需确认。序号去重，已完成视频重看不覆盖进度。
- 149 项非 Spring 上下文后端测试通过，前端生产构建通过；Chromium 模拟接口验证了课程入口、打开外链才开始计时、顺序心跳、确认、刷新完成状态及未开放课程响应。真实数据库并发事务、迁移及实际媒体播放仍待验，两份 M2 增量 SQL 均未执行。

## M3（班级考试已接通，环境验收待补）

- 管理端可按班级或试卷管理分配、设置考试窗口和次数、查看班级成绩。分配成绩按开考分配归属，保留转班学员的历史成绩。
- 有班学员新开考须满足班级分配、开放窗口和剩余次数。已有进行中的本人记录优先续考。尝试按账号和试卷计数，删除记录不返还次数。无班学员保留已发布试卷可见性，兼容规则待最终确认。
- 考试记录、成绩统计、概览和排行按开考班级快照授权。旧记录需要已关联的账号才能按当前班级归属处理。
- 隔离后端回归 173 项通过，前端生产构建通过。真实数据库迁移、行锁并发和多账号端到端验收待补。完整验收仍为 0/7。

## M4（已合并 main（PR #2，`edef6c2`），环境验收待补）

- 整卷诊断：交卷批阅后异步生成结构化报告（`ai_exam_reports` + 轮询 worker）。lease token + claim 保证多实例幂等；失败按 attempts 重试，3 次后 FAILED，可显式 retry 重置。报告叙述复用判分 AI 评语（`exam_records.summary`），评语缺失或判分证据不完整时置信度降级为 LIMITED（不下发参考答案/学员作答），生成失败降级为 DEGRADED 保留分数与知识点指标。报告支持教师/管理员复核（意见不可覆盖他人，重放幂等）。
- 知识点口径：报告指标在生成时点读取题目-知识点关联并持久化进 `report_json`，即"生成时点快照"；之后题目标签变更不影响已生成报告。retry 重新生成属显式用户行为，将采用当时的最新标注。未依赖额外快照表。
- 逐题追问（本轮完成）：学员批阅后可对试卷内题目追问（`ai_question_chats`），仅 STUDENT 可提问，教师/管理员可按归属查看。每题上限 20 轮（与前端计数一致），同一时刻每题仅一条 PENDING，提问间隔限频 10 秒；requestId 唯一键幂等（重复提交重放已存在轮次）。生成含题目/参考答案/学员作答/得分/判分反馈与最近 3 轮 READY 上下文（FAILED 轮次不入上下文），prompt 版本 `question-chat-v1`，记录模型与提示词版本，`review_status` 字段为 M6 复核差异预留。AI 空输出视为无效并按失败重试降级；追问前校验题目属于本卷、记录已批阅。
- 增量 SQL：`sql/2026-11-24_m4_diagnostic_reports.sql`（两张表）已于 2026-11-24 在云端 `exam_system` 库执行，`ai_exam_reports`/`ai_question_chats` 建表与索引验证通过。
- 产品决策（2026-11-24 用户确认）：报告对薄弱知识点的补课建议保留文字描述，真实视频/练习资源关联与可点击推荐留到 M5 补课任务链一并实现（避免提前建知识点-资源映射表后被 M5 表结构推翻）。
- 验证：隔离后端回归 186 项通过（含 `DiagnosticReportTest` 7 项 + `QuestionChatServiceTest` 6 项，全部为 Mock/隔离测试），前端生产构建通过（仅既有 Sass 弃用与 chunk 体积警告）。迁移 SQL 已在云端执行；真实数据库/AI 服务与端到端验收未做，完整验收仍为 0/7。
- AI review（2026-11-24）修复 5 项中危 + 2 项低危：①LIMITED 置信度报告统一清空全部题目的参考答案/学员作答（此前仅单题异常时清空）；②追问提示词增加防泄露指令（不逐字输出参考答案、抗提示注入）；③追问 RUNNING 卡死自愈（due/claim 纳入 lease 过期的 RUNNING，对齐报告表）；④并发防护——`ai_question_chats` 增加 (record,question,round_no) 唯一键，ask 冲突后重读计数重试一次，封堵并发突破 20 轮；⑤报告 retry 增加 1 分钟冷却。前端修正知识点得分率百分比换算（后端百分制直接取整）与置信度文案映射（NORMAL→高、LIMITED→低）。未采纳/遗留：`vite.config.js` 的 `envDir:false` 为 M1 隔离构建的有意决策，保留；复核人显示裸用户 ID 留 M6 一并处理。
- 真实链路验证（2026-11-24，本地后端连云端 MySQL/Redis/DeepSeek）：student1 对 paper 1 走通 开始考试→交卷（5 题 CHOICE，3 对 2 错）→AI 批阅（DeepSeek 真实调用，score=25，评语 513 字）→报告 worker 生成（先 LIMITED 降级：题目无知识点标注，正确触发脱敏；补 2 个知识点关联后重生成 → READY/NORMAL，知识点得分率与薄弱排名、参考答案下发、逐题判定全部正确）→逐题追问（PENDING→worker→READY，AI 正确答出本题未得分与正确项）。越权抽查：student2 访问他人报告 403 ✅；限频抽查：单题生成中再提问被拦（HTTP 200 + code=500「上一条追问仍在生成中」，业务文案正常）✅。countRecent 10 秒冷却在真实 AI 时序（生成约 20 秒）下总是被"单题生成中"检查先拦截，其窗口由单测覆盖。发现并修复两个问题：
  1. **云端 schema 漂移**：`exam_records` 缺 M3 脚本定义的 `class_id` 列与 `idx_exam_records_class_snapshot` 索引（M3 脚本当时部分执行），交卷即报 Unknown column 'class_id'。已按脚本原文在云端补齐。
  2. **追问冷却时区 bug**：DB 会话时区为 UTC、JVM 为本地时间，`latestCreatedAt` 读回 UTC 时间与 JVM `now()` 比较，8 小时偏差导致 10 秒冷却永不触发。修复为 SQL 内比较（`countRecent`：`created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 SECOND)`），比较完全留在 DB 侧。隔离回归 186 项全绿。
  3. 观察项（未改）：业务校验异常（IllegalArgumentException）经全局兜底 handler 返回 HTTP 200 + `code=500` + message，前端按 `code` 字段处理可正常展示文案；语义不理想但属全局历史行为，留后续统一治理。
  - 验证数据：云端 `ai_exam_reports`/`ai_question_chats` 各 1 条报告与 9 条追问轮次（真实 AI 生成）；测试用知识点 `Java基础语法`/`面向对象` 及关联为验证造数。前端浏览器验证仍未做。
- 遗留待清理（2026-11-24 用户确认留到 M5）：未跟踪死文件 `exam-system-server/src/main/java/com/atguigu/exam/service/CourseService.java`（空接口无引用，M5 补课任务链实现时一并删除或落实）；`.agents/` 目录暂不处理。

## M5（已合并 main（PR #4，`7831948`），环境验收待补）

- 补课任务链与掌握度：报告 READY（confidence NORMAL）且薄弱点非空时自动按点生成任务链（`INSERT IGNORE` + 唯一键幂等，报告 retry 不重建）；LIMITED（DEGRADED）不生成；生成失败只记日志降级不拖垮报告。
- 四步任务链：推荐视频 → 基础练习 → 变式题 → 阶段复测。视频门禁口径：无映射视频自动放行（视同 videoDone=1）/ 全部映射视频 M2 口径 COMPLETED / 快照 allowSkipVideo，三者其一放行练习与复测。轮次模型：本轮余题直接返回，无余题 round+1 重开新选题；变式排除基础练习已用题，不足回退全池标 `insufficient`。判分：客观题复用考试判卷比对（包级方法复用），TEXT/CODE 走一次 AI 调用（score≥60 记正确，AI 失败按未判分不炸整批）；提交幂等 `WHERE user_answer IS NULL`。
- 掌握度：单语句原子 upsert（SQL 内折算，禁读改写）——待复测 `total = videoDone*40 + accuracy*0.6`，复测后 `= videoDone*20 + accuracy*0.3 + retest*0.5`；复测达快照阈值（默认 60）任务 COMPLETED；forceRetest=false 时视频+练习完成即完成（掌握度保持 pending_retest=1 "未复测"）。
- 教师侧：PUT 课程级补课策略（课程归属=负责班级∩分配该课程的班级；upsert）、PUT 知识点视频映射（**物理删除**——逻辑删行占 uk_kp_video 唯一键，替换式维护必须物理删）、GET 班级概览（汇总/学员明细/薄弱点排行，`isTeacherOfClass` 数据范围）。
- 报告推荐升级：`recommendations` 升级为 `{text, videos[]}`（`@JsonCreator` 委托兼容旧字符串 JSON），有映射下发已发布视频（videoId/title/fileUrl/coverUrl），无映射回退文字建议；删除死文件 CourseService.java。
- 产品决策（2026-11-25 用户拍板）：策略配置课程级，但试卷/考试记录无课程关联——**开考班级经 course_classes 只分配一门课程时用该课程配置，多门/无课程用内置默认值**（60/5/5/强制复测/不跳视频/14 天），生成时快照进 `config_snapshot`，后续改配置不影响已生成任务。
- 前端：补课中心列表（状态筛选/掌握度进度条/逾期红标）+ 任务详情四步 Steps 交互 + 练习共用组件（脱敏作答、AI 反馈、再练一轮）；路由与菜单限 STUDENT；ExamResult 报告页视频卡片兼容渲染（跳站内 `/videos/:id`）。
- AI review（2026-11-25，docs/review-m5-remediation.md）：P0×0；修复 P1（并发整轮重复提交时 upsert 以 accuracy=0 覆盖既有掌握度/复测分——totalCount=0 跳过联动回显 DB 值）与 P2（AI 主观题判分移出事务，TransactionTemplate 单事务落库）；P3×7 记录不修（部分提交口径、复测用正确率而非分值加权、列表阈值硬编码 60 等）。
- 增量 SQL：`sql/2026-11-25_m5_remediation.sql`（5 张表）已于 2026-11-25 在云端 `exam_system` 库执行，5 张表与全部唯一键（uk_kp_video/uk_course_config/uk_task_user_kp_record/uk_item_task_step_q_round/uk_mastery_user_kp）验证就绪。
- 验证：隔离后端回归 221 项全绿（基线 186 → +35），前端 `npm run build` 通过。真实数据库迁移已执行；AI 判分链路与端到端验收待补。
- 教师侧前端（2026-11-25，PR #6 `e9a2fa6`）：课程管理页"补课策略"对话框、知识点管理页"推荐视频"多选维护、管理后台"补课概览"页（汇总/学员明细/薄弱排行，`/admin/remediation-overview`）；视频选择器复用既有 `GET /api/admin/videos`，零后端改动。已知取舍：后端无配置/映射查询接口，对话框打开时不回显（保存后会话内缓存），需要时补 GET 端点。
- 真实链路验证（2026-11-25，本地后端连云端 MySQL/Redis/DeepSeek + 浏览器走查）：教师侧走真实 API 配置课程策略（PUT config：60/3/3/强制复测/不跳视频/14天）与知识点视频映射；student1 走通 开考（班级分配快照 class_id=1）→交卷（故意 3 错锁定薄弱点口径）→AI 批阅→报告 READY→自动生成任务链（数量=薄弱点数，config_snapshot 与课程配置逐字段一致，due_time=+14 天）。薄弱点口径验证：KP1 得分率 80% 不算薄弱、KP2 0% 算薄弱 → 仅生成 KP2 任务，符合"低得分率才建链"。四步链全程验证：视频门禁未完成时取练习被拦（"请先完成视频学习"）→外链视频 60 秒心跳+确认完成（M2 口径，videoDone=1, total=40）→练习 3 题（2/3 对）→total=40+33×0.6=59.8 精确吻合→变式（练习已用 5 题仅剩 1<3，insufficient=true 回退全池）→复测全对→total=20+67×0.3+100×0.5=90.1，≥60 任务 COMPLETED；重复提交复测被拦（"当前没有待作答的题目"）幂等 ✓。默认策略路径：无班学员 student3 走通全链，2 任务快照为内置默认 5/5/60/强制/不跳/14 天 ✓。报告 retry 只对 FAILED 生效（READY 报告不能 retry，M4 时代旧报告不回溯生成任务，行为正确）。越权抽查：student2 访问他人任务 403 ✓。教师概览 API 数字与库一致，浏览器验证补课中心（任务卡/掌握度 90%）、任务详情四步 Steps、报告页视频推荐卡片（生成时点映射快照）全部正常。发现并修复 1 个 P1 bug：**取题响应 choices 恒为空**——`selectPoolQuestions` 池 SQL 只查 `q.*`，新选题路径未按 id 补全选项即脱敏下发（余题返回路径有 loadQuestions 故单测未拦住）；修复为新选题同样走 `loadQuestions` 补全（隔离回归 221 项全绿，新选题路径测试补选项断言）。观察项（未改）：教师概览页首次进入偶现统计 0（班级下拉异步加载与首次查询的时序竞态），刷新后正常。
  - 验证造数（云端 exam_system）：班级"M5验证班"（teacher1+student1+student2）、课程"M5验证课程"（PUBLISHED，分配该班）、exam_classes 分配试卷 1（窗口 7 天/10 次）、KP1 补 7 题/KP2 补 4 题标注、课程章节挂视频 19/24、B 站外链视频"M5验证外链视频"（白名单不含 YouTube，种子外链不可学）。

## M6（已合并 main（PR #9，`5b6fb2b`），真实链路已验证）

- 教学统计：`GET /api/teaching-stats?classId=&days=`（单班聚合一次返回：班级信息/近 7 或 30 天活跃/课程完成率/考试统计含分布与进步/缺考/逾期补课/高频错题 TOP10/复核差异）+ `GET /api/teaching-stats/compare`（教师限负责班级、管理员全量的横向对比）。全部为班级维度实时聚合 SQL，学员集合统一 `class_members(role_in_class='STUDENT')` 子查询限定，考试记录范围 = 班内学员 +（class_id 或 exam_class_id 归属）开考班级快照口径；时间判定统一在 SQL 内按 DB 时间算（云端 DB 为 UTC）。前端统一"教学统计"页 `/admin/teaching-stats`，教师/管理员一级菜单，薄弱知识点排行同屏内嵌复用 M5 `/api/remediation/overview`。
- AI review（docs/review-m6-teaching-stats.md）：修复 1 P0 + 1 P1 + 2 P2；隔离回归 236 项全绿，前端 build 通过。
- 真实链路验证（2026-11-25，本地后端 8081 连云端库，teacher1）：单班聚合接口与 SQL 直查逐指标一致（studentCount=2、active=1、graded=1、avgScore=20.0、passRate=0、高频错题 1004/3006/3007 各错 1 次、reportTotal=1、remediationCompletionRate=100）✅。**发现并修复 1 个 P0**：`compareClasses` 的 passRate 标量子查询少一个右括号 → `/compare` 必现 500；单测 Mock 了 Mapper 不跑真实 SQL 所以没拦住——**@Select 注解 SQL 的语法正确性只能靠真实链路验证兜底**（修复于 PR #11）。验证顺带发现 IDE 里驻留的旧版 8080 后端无 M6 接口（404），本地验证用 8081 新构建。
- 同日修复考试判卷触发回归（PR #10，`528c338`）：交卷后记录停在「已结束」且**判卷从未被触发**——结果页用 `score !== null` 判断已出分，而 `exam_records.score` 默认 0（非 NULL）恒为真，未判卷答题记录 `is_correct` 默认 0 被前端渲染成「全部错误」、且未批阅不下发答案。修复：结果页按状态「已结束」自动调 `/grade`（幂等），兜底重判改按 recordId 调 API。真实链路复验：卡住的记录 54 补触发判卷出分 74（11/15 对），选项内容型答案匹配判分全部正确，主观题 AI 反馈正常 ✅。

## 已完成并推送（main 分支，github.com/liuguanyi-lxy/exam-system）

- 2026-11-24：M1–M4 全量经 PR #2 合并 main（merge commit `edef6c2`，功能分支已删除）。M1 班级/知识点、M2 课程章节视频与学习进度、M3 班级考试分配/续考/历史成绩、M4 考后 AI 诊断与逐题追问；共 208 文件 +17281 行。同日云端 `exam_system` 库补齐迁移：经查库确认 M1/M2 主脚本/M3 此前已执行，本轮补执行 `2026-11-24_m2_learning_video_sessions.sql` 与 `2026-11-24_m4_diagnostic_reports.sql`，31 张表就绪。部署 HTTP、真实 AI 链路与端到端验收待补，完整验收仍 0/7。

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
