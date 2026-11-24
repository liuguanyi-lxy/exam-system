# 设计文档：M1 班级与成员管理 + 知识点管理（含数据范围层）

- 日期：2026-11-21
- 范围：五张表结构、数据范围层 `ClassScopeService` 方案、现有接口接入改造点、批量导入事务语义（配套增量脚本 `exam-system-server/sql/2026-11-21_m1_classes_knowledge.sql`）
- 约束：判卷/考试链不改；规格默认决策（`docs/specs/m1-classes-knowledge.md`）已定项不推翻；本阶段只出方案不执行 SQL（实现阶段统一执行）
- 现状核对依据：`User`/`ExamRecord`/`BaseEntity`/`VideoLike` 实体、`ExamRecordController`、`StatsController`+`StatsServiceImpl`+`StatsVo`、`AuthInterceptor`+`@RequireRole`、`UserServiceImpl`（BCrypt）、`QuestionBatchController`+`ExcelUtil`（导入先例）、`pom.xml`（Redis 依赖在）、`RedisUtils`（已在登录限流/热门题缓存使用）、云端 MySQL 8.0.30

## 1. 结论速览

| 决策点 | 结论 |
| --- | --- |
| 删除策略 | `classes` 走 BaseEntity 逻辑删除（任务指定）；`class_members`/`course_classes`/`knowledge_points`/`question_knowledge_points` **物理删除**（均带唯一索引且存在"删后重建/再入班"正常操作，逻辑删旧行占死唯一键，先例同 `question_favorites`） |
| 知识点 name 是否唯一 | **唯一**（`uk_kp_name`）。平铺全局字典，重名会让题目标注与 M4 知识点得分率无法聚合 |
| 标注是否记录来源人 | **记录** `created_by BIGINT NULL`。M4 诊断结论依赖标注质量，出问题能定位到人；系统批量补标注（无登录上下文）允许为空 |
| 数据范围层缓存 | **M1 直接查库，不引入 Redis 缓存**（理由见第 4.3 节），接口签名稳定预留演进 |
| `getPaperStats` 教师视角 | 在试卷条件之外按负责班级学员 `user_id` 限定；教师空范围不查成绩，管理员保留全卷口径 |
| 题目/试卷管理列表教师视角 | **M1 不接入班级过滤**（规格默认决策已定；题目/试卷是全局资源，班级归属列要等 M3，理由见 5.3） |
| `stats/overview` 教师视角 | `userCount`/`examCount`/`todayExamCount` 三项按负责班级学员过滤；`questionCount`/`categoryCount`/`paperCount` 维持全量（与题目列表不接入口径一致） |
| 批量导入事务 | **解析/预检阶段整单拒绝（可预期错误不入库）+ 入库阶段整体单事务 @Transactional**，事务成功后返回逐行结果（见第 6 节） |
| Mapper XML | **M1 不新增任何 XML**，全部 `LambdaQueryWrapper`（无跨表聚合需求；成员列表用"两步批量查"去 N+1，照 `ExamRecordServiceImpl.customPageGetExamRecord` 手法） |
| 实体命名 | 班级实体命名 `Clazz`（`Class` 是 Java 保留字），`@TableName("classes")` |

## 2. 方案对比与选型理由

### 2.1 关系表删除策略：物理删除（推荐）vs 逻辑删除

- **物理删除（选此）**：`class_members` 的唯一约束 `(class_id, user_id, role_in_class)` 与"移除后重新入班"直接冲突——逻辑删除旧行仍占用唯一键，重入必撞 `DuplicateKeyException`；换 `UNIQUE(..., is_deleted)` 只解决第一轮，第二次"入班→移除"出现两行 `is_deleted=1` 违反唯一约束。云端虽是 MySQL 8.0.30（可用函数索引/生成列绕），但脚本要求 5.7+ 兼容，且错题本设计文档已明确"复杂度不值"。
- **逻辑删除（不选）**：成员/标注/分配/知识点都不承载审计需求——历史归属在 M3 由 `exam_records` 补班级关联列快照解决，不依赖成员表留痕；"移除/取消/删除"后旧行无任何业务含义。
- **`classes` 例外**：班级是管理域主数据（学员班级页要展示"已停用/已删除"状态），任务指定走 BaseEntity 逻辑删除，与 `questions`/`exam_records` 同模式。

### 2.2 数据范围层实现：专用 Service 直查（选此）vs 缓存 vs 通用注解拦截器

- **专用 `ClassScopeService`（选此）**：M1 接入点只有 2 个接口，查询单表带索引、毫秒级；一个显式 service 调用点清晰可测，M2/M3 复用时按需调用。
- **通用拦截器/注解自动过滤（不选）**：接入点少且过滤语义各异（IN 列表 / 点查 / 计数），抽象拦截器是过度设计；等接入点超过 5 个且语义趋同再考虑（本设计不预做）。
- **Redis 缓存（不选，见 4.3）**：权限数据缓存失效出错是安全事故而非性能抖动，M1 规模下收益为零。

## 3. 数据模型

### 3.1 既有表依赖（不改动）

- `users`：`role`（ADMIN/TEACHER/STUDENT）、`status`（ACTIVE/INACTIVE）、`username` 唯一索引；密码 BCrypt（`UserServiceImpl.passwordEncoder`）。批量导入复用 `register` 模式。
- `exam_records`：`user_id BIGINT NULL` + `idx_exam_records_user`（`2026-11-15_exam_records_user_id.sql` 已建）；`status` 取值 `进行中/已结束/已批阅`（`ExamStatus`）；历史记录 `user_id IS NULL` 按 `student_name` 弱归属。
- `questions`：题目标注的挂载主体，逻辑删除 `is_deleted`。

### 3.2 五张新表（DDL 全文见配套 SQL 脚本，此处列字段与要点）

| 表 | 字段 | 约束/要点 |
| --- | --- | --- |
| `classes` | id、name、direction、start_date、end_date、status、create_time、update_time、is_deleted | 继承 BaseEntity（逻辑删除）；name 不唯一（跨期复用班名是真实需求，重名靠方向+起止时间区分）；`status` 取值 `ENABLED/DISABLED`（对齐 users 的 VARCHAR 枚举风格）；日期可空表示未定，先后关系应用层校验 |
| `class_members` | id、class_id、user_id、role_in_class、create_time | 物理删除；唯一 `(class_id, user_id, role_in_class)`（幂等兜底 + 点查 + 按班列成员三合一）；允许一人多班；role_in_class 取值 `STUDENT/TEACHER`（一班可多师） |
| `course_classes` | id、course_id、class_id、create_time | 物理删除；唯一 `(course_id, class_id)`；**M1 无实体无读写代码**，course_id 关联的 courses 表 M2 建，防误用仅靠唯一索引就位 |
| `knowledge_points` | id、name、description、sort、create_time | 物理删除；name 唯一；平铺列表按 sort 全量返回，不建额外索引；删除前后端校验引用（有题目标注则拒绝） |
| `question_knowledge_points` | id、question_id、knowledge_point_id、created_by、create_time | 物理删除；唯一 `(question_id, knowledge_point_id)`；created_by 记录标注人可空；删题后本表行残留，JOIN 过滤自然失效（口径同错题本对已删题），不预做清理 |

### 3.3 不建的表 / 不加的列

- 不建"班级-教师"单独关系表：教师就是 `class_members` 里 `role_in_class='TEACHER'` 的行，一班多师天然支持。
- 不在 `classes` 上加 `member_count` 冗余列：成员数实时 count 即可（单班几十行，走唯一索引前缀，成本可忽略），冗余列引入同步问题。
- 不给 `exam_records` 加班级列：那是 M3 的 `exam_classes` 阶段（路线图已排期），M1 教师视角靠 `user_id IN (成员集合)` 过渡。

## 4. 数据范围层：ClassScopeService（本阶段核心基建）

### 4.1 接口设计

```java
public interface ClassScopeService {
    /** 教师负责的班级ID集合：class_members WHERE user_id=? AND role_in_class='TEACHER' */
    List<Long> getTeacherClassIds(Long userId);

    /** 学员所属的班级ID集合（一人多班可返回多个） */
    List<Long> getStudentClassIds(Long userId);

    /** 点查：该教师是否负责班级 X（走 uk_class_user_role 最左前缀，等值点查） */
    boolean isTeacherOfClass(Long userId, Long classId);

    /** 一组班级的学员 userId 去重集合（一人多班去重）；
     *  调用方拿到的集合可能为空，必须按 4.4 的哨兵防呆拼接 IN 条件 */
    Set<Long> getStudentUserIds(Collection<Long> classIds);
}
```

- 三个查询全部落在 `class_members` 单表：`getTeacherClassIds`/`getStudentClassIds` 走 `idx_member_user(user_id)`；`getStudentUserIds` 走 `uk_class_user_role` 最左前缀 `class_id`；`isTeacherOfClass` 走唯一索引前缀等值点查。
- 实现层只用 `LambdaQueryWrapper`，注入 `ClassMemberMapper`，无自定义 SQL。
- admin 不走本服务（调用方按角色分流后直查全量），student 走 `getStudentClassIds` 供"我的班级"页使用。

### 4.2 查询语义约定

- 集合查询不过滤 `classes.status`：禁用班级的历史数据仍归属可见（见第 7 节边界表）。
- 集合查询不 JOIN `classes.is_deleted`：删除班级时由 `ClassService.deleteClass` 服务层级联物理清理该班 `class_members` 行（成员表本就物理删），保证残留行不存在；直连库手工逻辑删班级属运维越权操作，不在防线内。
- 返回值按 `user_id`/`class_id` 升序，保证测试可断言。

### 4.3 缓存策略：结论 = M1 直接查库，不引入 Redis

| 维度 | 判断 |
| --- | --- |
| 查询成本 | 单人班级数个位数（真实培训场景一人 1-2 个班），两个索引点查，毫秒级；错题本跨三表聚合都在毫秒级通过，本查询更轻 |
| 查询频率 | 中等：每次列表/统计接口调用一次，无循环内放大；学员端"我的班级"页频率低 |
| 缓存收益 | 接近零——省下的是单表索引点查的几毫秒 |
| 缓存风险 | 权限数据缓存失效错误是**安全事故**：admin 导入学员、分配教师、移除成员三处都要写失效逻辑，漏一处 = 教师看到不该看的数据或多班统计漏算 |
| 演进路径 | 接口签名稳定，M6/M7 若班级规模到千级或统计高频化，在 Impl 内部加本地缓存（TTL 60s + 成员变更主动失效）即可，调用方零改动 |

Redis 依赖虽已在项目中（登录限流、热门题 ZSet），但那些数据可容忍短暂过期；数据范围不属于此类。**结论：M1 直查。**

### 4.4 空集合防呆（必须落实的硬性约定）

`getStudentUserIds` 返回空集合时（教师无负责班级），调用方**禁止**直接把空集合交给 `wrapper.in(...)`：MyBatis-Plus 对空集合 `in` 的行为因版本而异（拼出 `IN ()` 报 SQL 错，或整段不拼条件退化为全量）——后者等于把全部数据暴露给无班级教师，是权限漏洞。统一防呆模式：

```java
Set<Long> memberIds = classScopeService.getStudentUserIds(classScopeService.getTeacherClassIds(userId));
if (memberIds.isEmpty()) {
    wrapper.eq(ExamRecord::getUserId, -1L);   // 哨兵：user_id 不可能为 -1，天然空结果
} else {
    wrapper.in(ExamRecord::getUserId, memberIds);
}
```

该模式在考试记录列表、单记录归属校验、单卷统计和概览中复用，代码注释中注明"禁止空集合直传 in"。

## 5. 现有接口接入改造点清单

### 5.1 `ExamRecordController.getExamRecords`（接入）

- **改造位置**：`LambdaQueryWrapper` 构建处的角色分支（现有 `if (!teacherOrAdmin) eq(userId)` 附近），把"教师/管理员合并分支"拆为显式三角色。
- **过滤逻辑**：
  - `ADMIN`：全量，不变；
  - `TEACHER`：`getStudentUserIds(getTeacherClassIds(currentUserId))` → 按哨兵模式拼 `IN`，即"负责班级的成员 user_id 集合"定界；
  - `STUDENT`：既有本人过滤不变。
- **角色取值**：`request.getAttribute("userRole")`（`AuthInterceptor` 已注入），与现有角色判断同源。
- **连带行为**：`studentName` LIKE 等既有筛选条件与 IN 过滤叠加，无需改动；列表后续的"未批阅脱敏"逻辑不变。

### 5.2 `ExamRecordController.getPaperStats`（接入）

对教师，查询条件为 `exam_id = paperId AND user_id IN (负责班级学员)`；空集合直接聚合空列表。管理员仍按 `exam_id` 统计全卷。试卷标题和总分是共享元数据，成绩聚合不能因为试卷共享而跨班。统计字段及分数段计算不变；不改变考试记录数据模型或 M3 草稿。

### 5.3 教师考试记录详情与删除（接入）

- 教师按当前记录非空 `user_id` 是否属于负责班级学员校验；历史空 `user_id` 记录教师不可见。非负责记录详情及删除返回 403，不能仅靠列表过滤保护单资源接口。
- 删除前先读取并校验归属，再删除；不存在记录维持幂等成功。管理员可按既有规则访问，学生已有本人归属与答卷脱敏规则不变。

### 5.4 题目/试卷管理列表教师视角（不接入，结论：共享资源）

**结论：M1 不接入班级过滤。** 理由：

1. **资源归属明确**：题目与试卷是机构内共享资源，没有班级归属列；M1 不按负责班级学员 ID 过滤题目/试卷管理列表。
2. **没有真实归属数据可过滤**：`questions`/`papers` 无班级归属列，班级资源化要等 M3 分配模型；M1 硬过滤只能得到空列表或伪造条件，没有业务意义。
3. **与 M2 需求冲突**：M2 教师要创建课程章节和考试，依赖题库/试卷可见；提前过滤会让教师"看不到自己要用的题"。
4. **无安全缺口**：教师角色已有 `@RequireRole` 保护与管理能力（建题改题是教师本职），答案脱敏对学生侧已生效。

### 5.5 `StatsController.getOverview`（接入）

- **现状**：`StatsServiceImpl.getSystemStats()` 六项 `selectCount` 全部无过滤。
- **改造位置**：`StatsController.getSystemStats()` 传入当前角色和用户 ID，由 `StatsServiceImpl` 按角色选择范围；保留无参数 `getSystemStats()` 兼容全量调用。
- **教师视角逐项过滤逻辑**：

| 统计项 | 教师视角 | 理由 |
| --- | --- | --- |
| `userCount` | `getStudentUserIds(负责班级).size()`（去重，一人多班计 1） | 教师的"用户"就是负责班级的学员 |
| `examCount` | `exam_records WHERE user_id IN (负责班级学员) AND is_deleted=0`（走既有 `idx_exam_records_user`） | 同上 |
| `todayExamCount` | 同上 + `create_time` 今日区间（沿用现有 between 写法） | 同上 |
| `questionCount` | **维持全量** | 题目是全局资源（与 5.3 口径一致，避免首页数字与题目列表页不一致） |
| `categoryCount` | **维持全量** | 同上 |
| `paperCount` | **维持全量** | 同上 |

- **实现细节**：先取 `studentIds`，为空时学员/考试记录指标直接置 0（不发起 IN 查询），非空时按成员 ID 过滤；`exam_records.user_id IS NULL` 的历史记录在教师视角天然不计（IN 不匹配 NULL），admin 全量含历史，边界见第 7 节。
- `StatsController` 的 `@RequireRole({"TEACHER","ADMIN"})` 不变，角色分流在方法体内完成。

### 5.6 未列入本轮的既有接口（明确不动）

- `getExamRanking`：公开排行榜口径未在规格与路线图 M1 清单中，不动。
- 错题本/收藏：学生本人 `userId` 定界已闭合，与班级无关。
- 题目与试卷资源及管理列表是**机构内共享资源**，M1 不按班级隐藏或过滤；教师考试成绩仍需按负责班级过滤。
- `/api/stats/test` 公开健康检查只允许返回简洁状态；不可返回用户数、考试数或异常详情。

## 6. 批量导入学员：事务语义（结论 = 预检整单拒绝 + 整体单事务）

### 6.1 流程设计（`ClassService.importStudents(classId, file, operatorId)`，参照 `QuestionBatchController` + `ExcelUtil` 先例）

1. **模板**：`GET /api/classes/import/template`，`ExcelUtil` 新增学员模板生成方法（列：用户名、姓名）。
2. **解析阶段（不入库）**：读 Excel → 结构错误（列缺失/空行/单元格类型错）**整单拒绝**，返回逐行错误原因；Excel 内重复用户名**合并为一行**处理（第二行视为同一人）。
3. **预检阶段（一次批量查）**：按 username 批量查 `users`，逐行判定：
   - 用户名不存在 → 标记"新建"（默认密码 `123456` BCrypt 加密、角色 STUDENT、status ACTIVE，真实姓名取 Excel）；
   - 存在且 role=STUDENT → 标记"已存在入班"；
   - 存在且 role 为 TEACHER/ADMIN → **行失败：角色冲突**，整单拒绝（可预期错误绝不产生半成功状态）。
4. **入库阶段（整体单事务 `@Transactional(rollbackFor = Exception.class)`）**：
   - 批量 insert 新建 users（BCrypt 编码，几十行 CPU 开销可接受）；
   - 批量 insert `class_members`；撞 `uk_class_user_role`（已在班）→ **幂等跳过**（`DuplicateKeyException` 转"已在班"成功语义，模式同 `ExamServiceImpl.startExam`）；
   - 事务内仅剩 DB 级意外错误（连接断、并发删用户），任何一行失败**整体回滚**。
5. **返回逐行结果**：行号 / 用户名 / 结果（`新建入班` | `已存在并入班` | `已在班跳过` | `失败原因`）；整单拒绝时事务未发生，逐行返回失败原因。

### 6.2 结论与理由

**选"整体 @Transactional"而非"逐行独立事务"**：

1. **状态最好解释**：半成功导入最难向管理员交代（哪些账号建了？哪些进了班？跨表部分效果无法用一句话说清）；全成或全败 + 重传即可。
2. **重传天然幂等**：回滚后库里无残留，重传时"已存在"分支与唯一索引兜底保证不重复建号、不重复入班。
3. **规模匹配**：单机构单班导入几十人，低频管理操作，逐行独立事务的容错收益体现不出来；预检已把可预期错误（角色冲突、格式错误）拦在事务外，事务内剩下的意外错误本就该全量回滚。
4. **演进空间**：若未来大班级导入频繁因个别行失败重传成本高，可改逐行独立事务（行级 try/catch + 部分成功语义），接口契约（模板与逐行结果结构）不变，不影响其他层。

### 6.3 同班重复入班的并发兜底

手动"添加学员"与批量导入均仅接受全局角色为 `STUDENT` 的账号；不得通过创建班级成员关系改变或绕过用户全局角色。手动添加先检查用户存在及角色，再判成员关系给友好提示，`save()` 外包 `try/catch (DuplicateKeyException)` 并发兜底为"已在班"成功——与 `question_favorites` 的幂等模式一致。

## 7. 边界语义表

| 场景 | 行为 |
| --- | --- |
| 教师无负责班级，调 list/overview | 空页/全 0（哨兵防呆），**绝非全量** |
| 历史 `exam_records.user_id IS NULL` | 教师视角不出现（IN 不匹配 NULL）；admin 全量含历史；学员视角既有 eq(userId) 本就看不到 |
| 一人多班 | scope 集合与 userCount 均按 user_id 去重；学员"我的班级"页列出全部所属班级 |
| 班级 `status=DISABLED`（禁用） | 数据范围仍以成员关系为准：教师/学员的历史数据可见；禁用挡的是管理侧操作与前端展示（学员班级页显示"已停用"，验收标准 4） |
| 成员账号 `status=INACTIVE` | 同上以成员关系为准，M1 不按账号状态二次过滤 |
| 删除班级 | `ClassService.deleteClass` 级联物理清理该班全部 `class_members` 行 → 再逻辑删 `classes`；scope 查询单表不 JOIN，依赖级联清理兜底（直连库手工删班级不在防线内，运维越权操作） |
| 移除成员后重新入班 | 物理删除不占唯一键，直接成功 |
| 并发重复入班/导入 | 撞 `uk_class_user_role` → `DuplicateKeyException` → 后端转"已在班"幂等成功 |
| 知识点重名 | 撞 `uk_kp_name`，后端转"名称已存在"友好提示 |
| 删除被引用的知识点 | 后端校验引用数（`question_knowledge_points` count），拒绝删除并提示引用数量；无引用才物理删行 |
| 删除题目 | 标注行残留，JOIN/前缀查询自然失效（同错题本对已删题口径），不预做清理 |
| 教师访问非负责班级（成员列表、改班等） | `isTeacherOfClass` 点查不通过 → 抛异常走全局处理器（403 语义），不是前端隐藏（验收标准 3） |
| course_classes | M1 无实体无读写代码；M2 建实体后按唯一索引幂等接入 |
| Excel 内重复用户名 | 合并为一行，第二行走"已存在"分支 |
| 导入同名但角色为教师/管理员 | 行失败"角色冲突"，整单拒绝，不入库 |

## 8. MyBatis-Plus 实体 / Mapper 清单（Wrapper vs XML 结论）

| 层 | 新增 | 说明 |
| --- | --- | --- |
| entity | `Clazz`（继承 `BaseEntity`，逻辑删除） | `@TableName("classes")`；`Class` 是 Java 保留字故命名 `Clazz` |
| entity | `ClassMember`、`CourseClass`、`KnowledgePoint`、`QuestionKnowledgePoint` | **不继承 BaseEntity**，物理删除，自带 `id/createTime`，照 `VideoLike`/`QuestionFavorite` 模式（否则 `@TableLogic` 强加 `is_deleted=0` 与表结构冲突） |
| mapper | `ClazzMapper`、`ClassMemberMapper`、`CourseClassMapper`、`KnowledgePointMapper`、`QuestionKnowledgePointMapper` | 仅 `BaseMapper`，**M1 全部不需要 XML** |
| service | `ClassScopeService`(+Impl) | 第 4 节；只用 Wrapper |
| service | `ClassService`(+Impl)：班级 CRUD/禁用、成员增删、分配教师、批量导入 | Wrapper + 批量 save；删除班级级联清成员 |
| service | `KnowledgePointService`(+Impl)：CRUD/排序、题目标注增删 | Wrapper |
| controller | `ClassController`(`/api/classes`)、`KnowledgePointController`(`/api/knowledge-points`) | 管理接口挂 `@RequireRole`；路径与字段细节以 API 契约文档为准（另行产出） |
| util | `ExcelUtil` 扩展学员导入模板/解析方法 | 现有类加方法，不新建 |
| vo | 导入逐行结果 VO、班级成员展示 VO（含 real_name） | 成员列表两步批量查：Wrapper 查 `class_members`（走唯一索引前缀）→ `selectBatchIds(users)` 组装，避免 N+1 |

**Wrapper vs XML 判断标准**（延续错题本文档口径）：等值/IN/唯一键查询用 Wrapper；跨表聚合与复杂 JOIN 才进 XML。M1 的查询全是单表等值/IN/全量排序，无聚合需求；唯一的多表场景（成员列表带姓名、按知识点筛题）都用"两步批量查"解决——按知识点筛题即：先 `question_knowledge_points` 按 `knowledge_point_id` 反查 question_id 集合（走 `idx_qkp_kp`），再对 `questions` 分页查询拼 `IN`（空集合哨兵防呆），不写 `inSql` 子查询，保持全程类型安全。

## 9. 索引设计（逐条理由）

| 索引 | 状态 | 理由 |
| --- | --- | --- |
| `class_members` `UNIQUE uk_class_user_role(class_id, user_id, role_in_class)` | 本脚本新增（建表内含） | 三合一：并发入班/导入幂等兜底（配合后端 DuplicateKeyException 处理）、"该教师是否负责班级X"与"是否已在班"点查（最左前缀两列等值）、按 `class_id` 前缀列班级成员与统计人数 |
| `class_members` `KEY idx_member_user(user_id)` | 本脚本新增 | 数据范围层反查方向：某人所属/负责哪些班级（`getTeacherClassIds`/`getStudentClassIds`）。唯一索引最左前缀是 `class_id` 帮不上反查；`role_in_class` 过滤在单人权责的少量行上回表完成，不值得为此扩复合索引 |
| `course_classes` `UNIQUE uk_course_class(course_id, class_id)` | 本脚本新增（建表内含） | 幂等兜底 + M2 主方向查询"某课程分配给哪些班级"（前缀 course_id） |
| `course_classes` `KEY idx_course_class_class(class_id)` | 本脚本新增 | M2 反查方向"某班级有哪些课程"，同 `idx_member_user` 道理 |
| `knowledge_points` `UNIQUE uk_kp_name(name)` | 本脚本新增（建表内含） | "name 唯一"结论的载体（重名破坏 M4 聚合口径）+ 创建时名称点查；管理页按 sort 全量返回，量级小不建 sort 索引 |
| `question_knowledge_points` `UNIQUE uk_qkp_question_kp(question_id, knowledge_point_id)` | 本脚本新增（建表内含） | 标注幂等兜底 + 题目编辑页按题取已标注知识点（前缀 question_id 点查） |
| `question_knowledge_points` `KEY idx_qkp_kp(knowledge_point_id)` | 本脚本新增 | "按知识点筛选题目"的反查方向（知识点 → question_id 集合），唯一索引前缀帮不上 |
| `classes` 无二级索引 | 结论：不建 | 全表量级预期百级以内，PK 点查/全表扫描均毫秒级；name 模糊搜索 `LIKE '%x%'` 索引帮不上；status 低基数不适合单独索引（口径同错题本文档对 `questions.type` 不加索引的判断） |
| `exam_records` `idx_exam_records_user(user_id)` | **已存在**，本脚本不重复创建 | 教师视角 `user_id IN (成员集合)` 与 stats 教师计数走它；脚本末尾附验证语句 |

## 10. 实施顺序（任务可独立验证，标注依赖）

1. **执行增量 SQL**（`sql/2026-11-21_m1_classes_knowledge.sql`）：建 5 表 + 4 个二级/唯一索引；先跑脚本头的表存在性预检与脚本尾的验证语句。本设计阶段不执行，实现阶段统一执行；无代码依赖，可先行。
2. **实体 + Mapper 五组**（依赖 1）：`Clazz`（BaseEntity）+ 四个物理删除实体；独立验证 CRUD 与唯一索引兜底（并发入班撞键）。
3. **`ClassScopeService` + Impl**（依赖 2）：四个查询方法；单测覆盖"有班/无班/一人多班/点查"，并断言空集合哨兵防呆模式在调用侧生效。
4. **班级管理域**（依赖 2、3）：`ClassService`/`ClassController`——班级 CRUD/禁用、成员增删/移除、分配教师、批量导入（预检 + 单事务）；独立验证：建班→导入 3 人（1 已存在 2 新建）→分配教师→成员列表完整（对齐验收标准 1、2）。
5. **教师/学员视角页数据**（依赖 3、4）：教师"负责班级列表+成员"、学员"我的班级"；越权点查 `isTeacherOfClass` 拦截非负责班级（验收标准 3、4）。
6. **知识点域**（依赖 2；与 4 并行）：`KnowledgePointService`/`Controller` CRUD/排序、题目标注增删、按知识点筛题（验收标准 7）。
7. **接入改造**（依赖 3）：`getExamRecords` 教师视角 IN 过滤、`getOverview` 教师视角三项过滤（验收标准 6、8）；`getPaperStats`/题目试卷列表按 5.2/5.3 结论不动。
8. **AI review + 全量回归**（依赖 4-7）：`mvn test` 全绿（47+ 用例 + 新增越权/导入幂等/空集合用例）；`npm run build` 通过；判卷/考试/错题本链路回归（验收标准 9、10）。
9. **用户 review**：推送功能分支开 PR，附验收清单对照；云端库执行 SQL 后联调。

## 11. 验证清单（交付前核对）

- [ ] teacher2 调班级成员接口访问 teacher1 负责的班级 → 被拒（后端 403 语义，非前端隐藏）
- [ ] 教师调 `GET /api/exam-records/list` 只返回负责班级学员记录；admin 全量；student 只看本人（既有行为不变）
- [ ] **无负责班级的教师调 list/overview → 空结果/全 0**（哨兵防呆生效，绝不退化为全量）
- [ ] 一人多班学员在 stats `userCount`、scope 集合、成员列表中只计一次
- [ ] `GET /api/stats/overview`：教师视角 userCount=负责班级学员去重数、examCount/todayExamCount 按成员过滤、其余三项全量；admin 六项全量不变
- [ ] `getPaperStats` 教师视角仍为全卷口径（回归验证未受影响）
- [ ] 批量导入：1 个已存在 student1 + 2 个新账号 → 三人入班；新账号 `123456` 可登录且角色 STUDENT；重传同一 Excel 全部"已在班跳过"，无重复数据
- [ ] 导入含角色冲突行（用户名是教师）→ 整单拒绝且库中无任何写入；含格式错误行 → 整单拒绝带逐行原因
- [ ] 并发重复入班：只落一行，另一请求不报 500（DuplicateKeyException 转"已在班"）
- [ ] 移除成员后重新添加：成功（物理删除不占唯一键）
- [ ] 班级禁用后学员班级页显示"已停用"，历史考试记录教师仍可见
- [ ] 知识点重名创建被拒；被题目引用的知识点删除被拒并提示引用数
- [ ] 题目编辑页标注 2 个知识点后，按知识点筛选命中该题；取消标注后筛选不再命中
- [ ] `EXPLAIN` 数据范围两类查询：反查走 `idx_member_user`，按班查学员走 `uk_class_user_role`
- [ ] course_classes 表存在且无任何业务代码读写（M2 接线前提就位）
- [ ] 判卷/考试/错题本/收藏链路回归全绿（47+ 用例）
