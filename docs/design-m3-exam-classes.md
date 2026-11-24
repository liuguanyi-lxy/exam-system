# 设计文档：M3 班级考试分配与可见性收敛

- 日期：2026-11-23
- 范围：试卷与班级分配、学员开考/续考、成绩快照与权限；接口见 `docs/api-m3-exam-classes.md`。
- 核对依据：`ExamParticipationService`、`ExamClassServiceImpl`、`ExamRecordScope`、`PaperController`、`ExamRecordMapper`、迁移 `exam-system-server/sql/2026-11-23_m3_exam_classes.sql`。
- 迁移未执行；仅在检查目标库状态并获得授权后执行。

## 1. 结论速览

| 决策点 | 结论 |
| --- | --- |
| 分配表 | 新表 `exam_classes`，试卷 × 班级粒度，唯一 `uk_exam_class (exam_id, class_id)`，BaseEntity 逻辑删除；`start_time/end_time` 可空表示该侧不限 |
| 记录快照 | `exam_records` 加 `exam_class_id`、`class_id`、`attempt_no`；不回填旧记录 |
| 新开考与续考 | 按账号锁定后先查本人进行中记录；只有新建记录才校验发布、分配、窗口和次数 |
| 考试次数 | 同账号同卷历史次数 + 1，计入逻辑删除；账号行锁覆盖计数和插入，进行中唯一键作为兜底 |
| 试卷可见性 | 教师/管理员保留机构共享列表；学员按分配展示，保留本人进行中的续考入口；无班级兼容策略待确认 |
| 分配生命周期 | 同卷同班只有一个分配 ID；撤销为逻辑删，恢复保留 ID，已开考不得改次数 |
| 成绩范围 | 有班级快照按开考班级，无班级快照回退 M1 账号成员范围；分配统计只看本分配 |

## 2. 数据模型

- 分配表 `exam_classes` 关联 `paper` 与 `classes`，唯一键 `uk_exam_class (exam_id, class_id)`，另有按班级的反查索引。启用状态、逻辑删除及教师负责范围独立判定。
- `exam_records` 增加可空的 `exam_class_id`、`class_id`、`attempt_no`。索引覆盖分配、班级快照和 `(user_id, exam_id)`；具体字段与索引名以迁移脚本为准。
- 新开考写入快照，交卷和判卷不重算。旧记录不回填，因为无法可靠还原当时的班级；新无班兼容记录也不伪造班级，但保存尝试序号。
- 进行中唯一索引只约束未删进行中记录。生成列以 `u:` 标记账号身份，历史缺失 `user_id` 的行以独立 `n:` 命名空间避免与账号冲突；姓名不是续考或成绩访问认证依据。

## 3. 开考事务

1. `ExamParticipationService.start` 以 `READ_COMMITTED` 开事务，并锁定认证学员在 `users` 中的行。账号不存在时拒绝。
2. 查同账号同卷未删进行中记录。显式 `resumeRecordId` 不匹配则拒绝；匹配或存在可续考记录时直接返回，不重新校验窗口、分配、次数，不生成记录。
3. 新开考必须是已发布试卷。读取同账号同卷尝试次数，包括逻辑删除。学员有班级时查询并锁定启用班级的有效分配；无班级则走旧兼容路径。
4. 窗口两端均含边界，`NULL` 表示该侧不限制。窗口内多个分配优先选总次数最多、分配 ID 最小者；没有开放分配或次数耗尽则拒绝。
5. 插入 `attempt_no = 历史次数 + 1` 并保存选定分配和班级快照。唯一键异常只回查同账号同卷进行中记录；不得按姓名回查，也不得当成成功的新尝试。

同账号的新开考试请求在账号行上串行，计数、判定、写入处于同一事务。分配行锁用于和分配编辑、撤销串行；唯一键是额外约束，不能单独保护次数校验。真实锁竞争和并发边界须在目标版本的隔离数据库验证。

## 5. papers/list 可见性收敛（契约 2.2）

### 5.1 分流结构

`PaperController.listPapers` 读取 `request.getAttribute("userRole")` 分流：

| 角色 | 行为 |
| --- | --- |
| TEACHER / ADMIN / role 缺失（既有兼容） | 现状 `getAllPapers(name, status)` 不动（管理视角全量，含各状态） |
| STUDENT，班级集合为空 | `getAllPapers` + 服务端强制 `status='PUBLISHED'`（忽略前端传的 status 参数；现状前端学员页本就传 PUBLISHED，此处把口径从"前端约定"收到"后端强制"，防直接调 API 看到 DRAFT/STOPPED） |
| STUDENT，班级集合非空 | 两步批量查（5.2），**只看分配的，不看全量**（规格默认决策：有班级学员只见分配内容） |

响应仍为 `List<Paper>`，Paper 实体追加 `@TableField(exist = false)` 瞬态字段：`assigned Boolean`、`examStartTime/examEndTime LocalDateTime`（`@JsonFormat` GMT+8）、`windowStatus String`（未开始/进行中/已结束）、`attemptLeft Integer`。教师/管理员视角这些字段为 null——追加字段不破坏现有前端解析（契约"响应结构在既有字段上追加"）。

### 5.2 学员有班级分支的两步查询（SQL 形态）

```sql
-- 第一步：学员所属班级的全部有效分配（走 idx_exam_class_class）
SELECT id, exam_id, class_id, start_time, end_time, max_attempts
FROM exam_classes
WHERE class_id IN (?, ...) AND is_deleted = 0 AND status = 'ENABLED';

-- 第二步：PUBLISHED 过滤（走 paper 主键 IN）
SELECT * FROM paper
WHERE id IN (第一步的 exam_id 去重集) AND status = 'PUBLISHED' AND is_deleted = 0;

-- 第三步：一次分组计数取各卷已用尝试次数（走既有 idx_exam_records_user 定界）
SELECT exam_id, COUNT(*) AS used
FROM exam_records
WHERE user_id = ? AND exam_id IN (第二步的试卷集) AND is_deleted = 0
GROUP BY exam_id;
```

实现用三个 `LambdaQueryWrapper`/`selectMaps`（第三步分组计数用 `QueryWrapper.select("exam_id, COUNT(*) AS used").groupBy(...)`），零 XML。窗口字段在 Java 层计算：

- `windowStatus`：now < start → 未开始；start ≤ now ≤ end（NULL 侧放开）→ 进行中；now > end → 已结束；
- `attemptLeft`：`max_attempts - used`，下限截为 0；
- `assigned`：学员有班级分支恒为 true（无班级分支 false）。

**窗口判断在列表层的决策（需用户 review 确认的口径）**：本设计让分配行**全部出现在列表**（未开始/进行中/已结束三态都可见），"窗口外看不到入口"落在两处——前端把未开始/已结束/次数用尽渲染为禁用态（契约 §4 ExamList 追加窗口状态标签与剩余次数，标签数据即来自这些字段）、后端 startExam 强校验兜底。理由：①规格功能范围明确"考试列表带'未开始/进行中/已结束'状态与剩余时间"——已结束状态只有行可见才展示得出来；②契约定义的 `windowStatus` 三态与 `attemptLeft=0` 才有意义；③全部隐藏会让"我为什么看不到考试"无从排查。**若用户 review 倾向严格"窗口外整行不可见"**（任务提示的另一种读法），改动点仅是第一步 WHERE 追加窗口谓词 `AND (start_time IS NULL OR start_time <= now) AND (end_time IS NULL OR end_time >= now)`，其余设计不变——两种口径的差异集中在这一条谓词上，实现前由用户拍板。

### 5.3 无班级学员分支

现状放行（全量 PUBLISHED）+ 5.1 所述的服务端强制 PUBLISHED。`assigned=false`，窗口/次数字段 null（无上限语义由前端按 assigned=false 区分展示）。

### 5.4 同卷多分配的展示口径

学员属于多个班、同卷被分配多次（不同窗口/次数）时：列表按试卷去重一行，行上展示字段取"对学员最宽松的分配"——`max_attempts` 最大、并列时 `end_time` 最晚（NULL 视为最晚）、再按 id 最小；与 startExam 的快照选择规则（4.1 第 6 步）保持同一口径，避免"列表显示 A 窗口、开考却按 B 校验"的错位。尝试次数按 user+exam 全局计（跨分配共享，见 7.1 第 5 行）。

## 6. 分配管理边界语义（契约 2.1）

### 6.1 撤销分配 `DELETE /api/exam-classes/{id}`

- **拒绝条件**：`EXISTS (SELECT 1 FROM exam_records WHERE exam_class_id = #{id} AND status = '进行中' AND is_deleted = 0)`（走 `idx_exam_records_class`）→ 抛"有学员正在考试，无法撤销分配"。
- **探测口径用 `exam_class_id` 快照而非班级成员**：学员可能属两个班、同卷分配两次，他在分配 B 下进行考试时，撤销分配 A 不受影响（进行中记录快照指向 B，A 上没有进行中记录）——按成员探测会误伤；按快照探测的语义恰好是"**本分配**产生的进行中考试"，与文案一致。
- **放行条件**：仅历史记录（已结束/已批阅）或无记录 → 逻辑删分配行（`removeById`，`@TableLogic` 置 `is_deleted=1`）。历史记录的 `exam_class_id` 快照保留（不级联改写），教师按班级筛选历史记录仍能看到。
- 与规格功能范围"可撤销分配（窗口未开始时）"的关系：契约把边界收紧为"仅进行中拒绝、仅历史允许"，窗口已开始但有历史记录时**允许撤销**（契约为准）。窗口未开始且无记录当然可撤（无任何记录）。

### 6.2 修改分配 `PUT /api/exam-classes/{id}`

- **maxAttempts 边界**：请求中 `maxAttempts` 与库中现值不同，且 `EXISTS (SELECT 1 FROM exam_records WHERE exam_class_id = #{id} AND is_deleted = 0)`（任何状态，进行中/已结束/已批阅都算"已有人开考"）→ 抛"已有学员开考，无法修改考试次数"。值相同则不触发该校验（幂等编辑通过）。
- **时间窗口随时可改**：进行中考试继续按"开考时间 + 试卷时长 + 宽限"交卷（`checkSubmitDeadline` 口径），不受窗口收缩影响；窗口只拦新开考。缩小窗口导致部分学员窗口外——他们已开考的照常交卷，未开考的后续被 ② 拦截，语义自洽。
- **服务层校验**：`start_time` 与 `end_time` 同时非空时 start ≤ end；`max_attempts` ≥ 1。
- 修改后若把分配置为 DISABLED（契约未定义 PUT 可改 status；禁用属管理域独立操作或后续扩展，本阶段 PUT 不接 status 字段，避免与"撤销"语义重叠）。

### 6.3 创建分配 `POST /api/exam-classes`

- 仅 PUBLISHED 试卷可分配（查 `paper` 状态，非 PUBLISHED → "仅已发布试卷可分配"）；
- `classIds[]` 逐班校验在教师负责范围内（`ClassScopeService` 点查，签名待 M1 对齐；ADMIN 跳过）→ 越权班抛 403 语义"无权限执行该操作"；
- 同卷同班重复：正常行存在 → 逐班结果报"该班级已分配此试卷"；已删行占键 → 3.4 复活流程；
- 批量创建逐班独立处理，返回逐班结果（成功/已存在/复活成功/失败原因）。

### 6.4 列表与详情

- `GET /api/exam-classes?examId=&classId=&page=&size=`：双视角天然由参数驱动——examId 走 `uk_exam_class` 最左前缀，classId 走 `idx_exam_class_class`；教师视角叠加 `class_id IN (负责班级)`（空集合哨兵防呆，模式同 M1 设计 4.4，禁止空 IN 直传）；列表联查班级名/试卷名两步批量补齐（防 N+1）。
- `GET /api/exam-classes/{id}`：详情含班级名、试卷名、当前各状态人数。人数口径与 7.2 的 students 聚合同源：按 `exam_id + 班级学员集合` 查记录后聚合（未考 = 班内学员中零记录者、进行中/已交/已批阅 = 最近一条记录状态归类），而非按快照归类——学员在另一分配下考过的，在本分配详情里显示"已考"，避免两个班的详情页对同一学员口径打架。

### 6.5 教师数据范围接入点汇总（ClassScopeService 签名待 M1 对齐）

| 接口 | 校验方式 |
| --- | --- |
| POST /api/exam-classes | 目标 `classIds[]` 逐个点查是否负责班级；ADMIN 放行 |
| PUT / DELETE / GET /{id} / GET /{id}/students | 分配行的 `class_id` 点查是否负责班级；ADMIN 放行 |
| GET /api/exam-classes | 教师 `class_id IN 负责班级` 过滤（空集合哨兵） |
| papers/list 学员分支、startExam | 学员班级集合查询（一人多班） |
| GET /api/exam-records/list 的 classId 筛选 | 教师 `classId` ∈ 负责班级点查，越权 403 |

以上均按 M1 设计文档第 4 节既定能力描述，具体方法签名与空集合哨兵模式在实现阶段以 M1 实际代码为准。

## 7. 班级成绩视角

### 7.1 `GET /api/exam-classes/{id}/students` 聚合方案（任务重点）

按分配维度列学员：姓名、尝试次数、最高分、最近状态。三步批量查 + Java 内存聚合（不写 XML，不用 MySQL 8 窗口函数，保持 5.7 兼容口径）：

```sql
-- 第一步：分配行（含 class_id、exam_id、max_attempts），主键点查
SELECT * FROM exam_classes WHERE id = #{id} AND is_deleted = 0;

-- 第二步：该班学员集合（走 uk_class_user_role 最左前缀）
SELECT user_id FROM class_members
WHERE class_id = #{classId} AND role_in_class = 'STUDENT';

-- 第三步：这批学员在该卷上的全部未删记录（走 idx_exam_records_user 逐人命中，
-- 单班学员数十人 × 人均记录几条，结果数百行内）
SELECT user_id, status, score, start_time, id
FROM exam_records
WHERE exam_id = #{examId} AND user_id IN (第二步集合) AND is_deleted = 0;
```

Java 聚合规则：

| 展示列 | 口径 |
| --- | --- |
| 姓名 | `users.real_name`（批量 selectBatchIds） |
| 尝试次数 | 该学员在该卷上的未删记录数（**按 user+exam 全局计，不按快照**） |
| 最高分 | 记录中 `MAX(score)`（未批阅记录 score 为 NULL，天然被 MAX 忽略；全 NULL → 显示"-"） |
| 最近状态 | 按 `create_time DESC, id DESC` 取最近一条记录映射：无记录 → 未考；进行中 → 进行中；已结束 → 已交；已批阅 → 已批阅 |

第三步为 IN 查询（走 `idx_exam_records_user`），学员数十量级无 N+1；未出班的学员（曾在班、已移出）不出现在列表——成员表是当前状态源，历史归属已由记录快照承载（教师按班级筛选记录列表可查到已移出学员的历史记录，两处口径分工：students 页 = 当前成员状态，记录列表 = 历史事实）。

### 7.2 `GET /api/exam-records/list` 追加 classId 筛选与班级名（契约 2.3）

- 新增 `classId` 参数：`examRecordLambdaQueryWrapper.eq(classId != null, ExamRecord::getExamClassId, classId)`——**按快照列过滤**。历史记录（`exam_class_id IS NULL`）在按班级筛选时不出现（口径：筛选"按分配考出来的记录"），不带筛选时正常展示；
- TEACHER 传 classId 时点查负责班级，非负责 → 403 语义；ADMIN 不限；STUDENT 忽略该参数（既有本人强制过滤不变）；
- 教师/管理员视角无 classId 时维持 M1 已接入的负责班级范围过滤（该层属 M1 产出，本阶段不动）；
- 记录返回追加班级名：本页记录的 `exam_class_id` 去重后批量查 `exam_classes` → `classes` 两跳取 `classes.name`，挂到记录的瞬态字段 `className`（NULL 快照的历史记录班级名为空）；沿用 `customPageGetExamRecord` 的批量补齐手法，无 N+1。

## 8. 索引设计（逐条理由）

| 索引 | 状态 | 理由 |
| --- | --- | --- |
| `exam_classes` `UNIQUE uk_exam_class (exam_id, class_id)` | 本脚本新增（建表内含） | 三合一：重复分配幂等兜底（撞键 → 后端转"已分配"/复活流程）、startExam 班级成员校验按 `exam_id` 最左前缀点查（`WHERE exam_id=? AND class_id IN (...)`）、"按试卷查分配了哪些班"视角（examId 参数） |
| `exam_classes` `KEY idx_exam_class_class (class_id)` | 本脚本新增（建表内含） | 反查方向：学员可见性第一步（按班级集合查分配）、教师按班级视角列表、"按班级查分配了哪些试卷"视角、`exam-records/list` classId 联查前置。唯一索引最左前缀是 `exam_id` 帮不上；与 M1 `course_classes` 的 `idx_course_class_class` 完全同构（先例照抄） |
| `exam_records` `KEY idx_exam_records_class (exam_class_id)` | 本脚本新增 | 三个查询都按 `exam_class_id` 等值过滤：撤销分配的"进行中"探测、修改 maxAttempts 的"已开考"探测、记录列表按班级筛选（分页驱动列，无索引则每次分页全表扫）。万级记录表上全扫虽不致命，但这是分页热路径，一列等值索引成本极低收益明确 |
| `exam_records` `(user_id, exam_id)` 复合索引 | **不加** | attempt 计数与学员聚合都是先按 `user_id` 定界（既有 `idx_exam_records_user` 已做）再在单人记录子集（几十条内）上过滤 `exam_id`——回表过滤成本可忽略；口径同 M1 对 `idx_member_user` 不扩复合索引的判断。若未来单人记录量上千再议 |
| `exam_classes.start_time/end_time` 不加索引 | 结论：不建 | 窗口比较发生在"按班级/试卷查出的分配子集"（个位数行）之后，Java 层完成；无"按时间范围扫分配表"的查询形态 |
| `exam_classes.max_attempts/status` 不加索引 | 结论：不建 | 低基数列 + 总在等值子集内过滤，单独索引无收益（口径同错题本文档对 `questions.type` 的判断） |
| `exam_records` `idx_exam_records_user(user_id)` | **已存在**（`2026-11-15_exam_records_user_id.sql`） | attempt 计数、papers/list 第三步分组计数、students 聚合第三步全部以它为入口；脚本末尾附验证语句，不重复创建 |

## 9. 实施顺序（任务可独立验证，标注依赖）

1. **执行增量 SQL**（`sql/2026-11-23_m3_exam_classes.sql`）：建 `exam_classes` + `exam_records` 两列一索引；先跑脚本头预检与脚本尾验证语句。无代码依赖可先行；本设计阶段不执行，实现阶段统一执行。
2. **实体 + Mapper**（依赖 1）：`ExamClass` 实体（继承 BaseEntity）；`ExamRecord` 实体补 `examClassId`/`attemptNo` 两字段（瞬态 `className` 等展示字段一并加）；`ExamClassMapper`（BaseMapper + 复活 SQL 一条）。独立验证：CRUD、撞唯一键、复活流程。
3. **`ExamClassService` 分配管理域**（依赖 2 + M1 `ClassScopeService` 就位）：创建（含复活）/列表/详情/修改/撤销五接口 + 教师数据范围接入（6.3–6.5）。独立验证：建分配 → 列表双视角 → 撤销 → 复活重分配；教师越权 403。
4. **startExam 改造**（依赖 2、3）：4.1 流程 + 错误文案（契约第 3 节逐条对齐）。独立验证：无班级学员现状放行、班级成员三重校验、窗口文案、次数用尽文案、并发双开只落一条、续考不受移出班级影响（验收标准 5、6、7）。
5. **papers/list 学员分支**（依赖 2、3；与 4 并行）：角色分流 + 两步批量查 + 窗口/次数字段。独立验证：无班级全量、有班级只见分配、PUBLISHED 过滤、多分配展示口径（验收标准 3）。
6. **students 聚合 + records/list classId 筛选**（依赖 2、3）：7.1/7.2 两处。独立验证：未考/进行中/已交/已批阅四态人数、最高分、班级名补齐、教师班级越权。
7. **前端接入**（依赖 3–6）：契约 §4 四处（ExamAssign 管理页、PaperList 分配入口、ExamList 状态标签、ExamHistory 班级筛选）。
8. **AI review + 全量回归**（依赖 3–7）：`mvn test` 全绿（既有 47+ 用例 + 新增并发开考/窗口/次数/越权用例）；`npm run build` 通过；判卷/交卷/错题本/收藏/课程进度回归（验收标准 8、9）。
9. **用户 review**：推送功能分支开 PR，附验收对照；云端库执行 SQL 后联调。窗口可见性口径（5.2 决策点）在 PR 描述中单独列出请用户拍板。

依赖关系：1 → 2 → 3 → {4, 5, 6 可并行} → 7 → 8 → 9。M1 的 `ClassScopeService` 是 3 的硬前置（其未就位时 2 可先行）。

## 10. 边界语义表

| 场景 | 行为 |
| --- | --- |
| 无班级学员开考任何已发布卷 | 现状放行（三重校验跳过），记录 `exam_class_id=NULL`、`attempt_no` 照常计数 |
| 有班级学员开考未分配的卷 | ① 拦截："该考试未分配给你的班级" |
| 分配被禁用（DISABLED） | 等价于不存在：列表不出现、startExam 报"未分配"（该分配不参与任何判定） |
| 窗口外（未开始/已结束）新开考 | ② 拦截："考试时间：{start} ~ {end}，当前不可参加" |
| 次数用尽（attempt_no+1 > max_attempts） | ③ 拦截："考试次数已用完（共 {n} 次）"；错误优先级 ①→②→③ |
| 学员中途被移出班级 | 列表不再出现该卷；**进行中的考试可续考、可交卷**（startExam 续考路径不走校验 + 交卷五重校验不看班级） |
| 班级禁用/删除后进行中的考试 | 同上完成优先；删除班级属 M1 级联范围，分配行残留由列表联查过滤（联查不到班级名显示"-"） |
| 撤销分配时有进行中考试 | 拒绝："有学员正在考试，无法撤销分配"（按 exam_class_id 快照探测） |
| 撤销分配时仅历史记录 | 逻辑删分配；历史记录快照保留，班级筛选仍可查 |
| 撤销后重新分配同卷同班 | 撞 uk_exam_class → 复活已删行（3.4），保留原行 ID |
| 改 maxAttempts（已有人开考） | 拒绝："已有学员开考，无法修改考试次数"；值相同不触发 |
| 改时间窗口 | 随时可改；进行中考试交卷不受窗口收缩影响（交卷按时长+宽限） |
| 同卷分配多班、学员属多班 | 开考快照取"max_attempts 最大、id 最小"的窗口内分配；列表展示同口径；尝试次数按 user+exam 全局计，跨分配共享 |
| 交卷后再考（重考） | 上条已结束不占进行中唯一索引，正常开新记录；attempt_no = 历史未删记录数 + 1 |
| 并发重考双开 | 撞 `uk_exam_active_student_paper` → 后到者返回先到者的进行中记录（4.3 分析），attempt_no 无重复、次数无击穿 |
| 学员删除自己的考试记录（逻辑删） | 未删记录数减少 → 剩余次数相应恢复；attempt_no 快照不重算，"尝试次数"展示口径始终按未删行数计（与教师 students 页一致） |
| 历史记录（无快照） | `exam_class_id/attempt_no` 为 NULL：班级筛选不出现、班级名显示空；列表/详情/错题本等既有路径不受影响；不回填 |
| 无班级学员在 papers/list | 全量 PUBLISHED（服务端强制），`assigned=false`、窗口字段 null |
| 教师操作非负责班级的分配 | 403 语义："无权限执行该操作"（点查 `isTeacherOfClass`，具体签名待 M1 对齐） |
| STOPPED 试卷的存量进行中考试 | 不在 M3 范围（现状 startExam 本就不校验试卷状态）；分配仅允许 PUBLISHED 试卷创建（6.3），新开考被分配体系间接约束 |

## 11. 验证清单（交付前核对）

- [ ] teacher1 分配"Java基础知识测试"给"Java 培训一班"（窗口今天起 7 天、max_attempts=2）→ 列表可见、详情四态人数正确（未考=全班）
- [ ] student1（班内）考试列表出现该卷（带窗口标签与剩余次数）→ 开考 → 交卷 → 判卷；`exam_records` 落 `exam_class_id` 快照与 `attempt_no=1`
- [ ] student3（无班级）考试列表与开考行为和现状一致；其记录 `exam_class_id=NULL`、`attempt_no` 正常
- [ ] teacher2（非负责班级）无法看到该班学员记录、无法操作该分配（403 语义）；admin 全量
- [ ] 窗口外开考被拒且文案含精确窗口时间；次数用尽被拒且文案含总次数；未分配开考被拒
- [ ] 重考：首考后再次开考成功，第二条记录 `attempt_no=2`；`max_attempts=1` 时重考被拒
- [ ] **并发双开**（同学员同卷同时两次 startExam）：只落一条进行中记录，另一请求返回已有记录且不报 500；并发重考同理（上一条已结束不拦）
- [ ] 学员中途被移出班级：列表该卷消失；已开考的可续考、可交卷、判卷正常
- [ ] 撤销分配：有进行中 → 拒绝；仅历史 → 逻辑删成功；撤销后重新分配同卷同班 → 复活成功且原 ID 保留
- [ ] 已有学员开考的分配改 max_attempts → 拒绝；只改时间 → 成功；进行中的考试交卷不受窗口收缩影响
- [ ] `exam-classes/{id}/students`：尝试次数/最高分/最近状态与记录事实一致；未批阅记录不拉低最高分
- [ ] `exam-records/list` 按 classId 筛选只出该班快照记录；历史无快照记录在无筛选时正常出现；教师越权 classId 被拒
- [ ] 无负责班级的教师调分配列表 → 空页（哨兵防呆，绝不退化全量）
- [ ] `EXPLAIN` 三条热路径：startExam 分配点查走 `uk_exam_class`；学员列表第一步走 `idx_exam_class_class`；撤销/筛选记录走 `idx_exam_records_class`；attempt 计数走 `idx_exam_records_user`
- [ ] 交卷五重校验与判卷链回归全绿；错题本/收藏/课程进度（M2）回归不受影响
- [ ] `mvn test` 全绿（47+ 既有用例 + 新增用例）；`npm run build` 通过
