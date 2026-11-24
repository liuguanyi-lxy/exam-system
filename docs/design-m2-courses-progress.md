# 设计文档：M2 课程、章节与视频学习（含学习进度派生）

- 日期：2026-11-23
- 范围：课程与学习进度、个人学习会话、完成状态派生、教师权限和课程分配（配套 M2 课程与个人会话两份增量 SQL）
- 约束：判卷/考试/错题本链不改；规格默认决策（`docs/specs/m2-courses-chapters-videos.md`）与契约（`docs/api-m2-courses-progress.md`，23 接口）已定项不推翻；本阶段只出方案不执行 SQL
- 现状核对依据：`ClassScopeService`（M1 已落地四方法签名）、`CourseClass` 实体（物理删、仅 createTime）、`GlobalExceptionHandler` 的 `ClassScopeException`→HTTP 403、`VideoServiceImpl.recordVideoView`（会话化上报：delta ≤0→0、>60 截 60、同会话累加 `view_duration`、新会话插行+观次+1）、`Video` 实体（source_type/duration/status 常量）、`AuthInterceptor` 白名单（`^/api/videos/\d+/view$` 与 GET `/api/videos` 前缀公开）、`video_views.session_id` 已有 `idx_video_views_session` 索引、`BaseEntity`（id/createTime/updateTime/isDeleted @TableLogic）、M3 已建 `exam_classes`（逻辑删+复活先例，与本阶段无表冲突）

## 1. 结论速览

| 决策点 | 结论 |
| --- | --- |
| 五张表 | `courses`/`course_chapters` 逻辑删除，`chapter_videos` 物理移除。个人进度与会话不因课程或章节删除而清除。 |
| `learning_video_progress` 存什么 | 有效学习秒数（完成后冻结）、状态、完成时间。会话使用独立表；章节完成和课程进度派生。 |
| 完成判定通道 | heartbeat 独立接口内部复用 `recordVideoView`，观看统计与视频中心同源；**完成判定与观看统计使用同一个清洗后的 delta**，两条链路口径永不分裂 |
| 防刷 | 五条防线：同源 delta 清洗（≤0→0、>60→60）、空 sessionId 拒绝、服务端间隔折算（建议项）、外链确认不信任前端输入、外链墙钟下限（建议项）——见第 4 节 |
| `chapter_videos.video_id` 必填性 | **NOT NULL 必填**：EXIST 与 EXTERNAL 两种来源最终都落到一行 `videos`，不存在"无视频的章节视频行"；完成通道由 `videos.source_type` 决定，与添加方式正交 |
| EXTERNAL 代建 videos 行 | `source_type=1`、`status=1` 直接发布、`duration=NULL`（外链完成走固定 60 秒门槛，不依赖时长）、`uploader_type=2`、`uploader_name=教师姓名`、`admin_id`/`user_id` 不填（避免污染管理端统计），溯源走 chapter_videos→章节→`courses.created_by` |
| 派生查询形态 | **服务层内存聚合 + 两步批量查，M2 不新增 XML**（契约 11.6 "单条聚合 SQL" 评估结论：不采用，量级小 + 类型安全，M1 口径延续）；学员课程列表固定 6 次查询、班级统计固定 6 次查询，零 N+1 |
| 空集合防呆 | 沿用 M1 硬性约定：scope 集合为空时 `eq(字段, -1L)` 哨兵，禁止空集合直传 `in`（无班级教师/学员绝不退化为全量） |
| 课程-班级分配 | PUT 整体替换用**差量同步**实现（终态=classIds 全等）：删多余、插缺失、交集行不动 → `create_time` 即"首次分配时间"（契约 11.5），重复提交同集合零写入，无需给 course_classes 加 assigned_at 列（偏离契约 4.7 "全删重建"字面，见第 13 节） |
| sort 复合索引 | **不建** (course_id, sort)/(chapter_id, sort)：量级论证见第 10 节（契约 11.2/11.3 的"建议"不采纳） |
| Mapper XML | M2 **零 XML**，全部 `LambdaQueryWrapper`（延续 M1 判断标准：跨表聚合用两步批量查在服务层完成） |

## 2. 方案对比与选型理由

### 2.1 chapter_videos 删除策略：物理删除（选此）vs 逻辑删除+复活

- **物理删除（选此）**：`uk_chapter_video(chapter_id, video_id)` 唯一索引与"移除后重新添加同一视频"（常规操作，契约 6.2 未禁止）直接冲突——逻辑删旧行仍占死唯一键；M1 已论证 `UNIQUE(..., is_deleted)` 只解决第一轮（第二轮出现两行 is_deleted=1 违反约束），且要求 5.7 兼容。chapter_videos 是纯内容配置行，无审计价值；课程/章节删除级联清理简单。
- **逻辑删除+复活（不选，M3 exam_classes 用了此方案）**：M3 选它是因为分配撤销有管理留痕价值且字段多（时间窗/次数上限）；chapter_videos 无此类诉求，复活 SQL 是无收益复杂度。
- **连带影响**：契约 5.4/6.4 的"级联逻辑删除其 chapter_videos"落地为**级联物理删**；学员 `learning_video_progress` 保留不变（progress 按 video_id 关联，与章节视频行解耦，重挂同视频不产生脏数据——契约口径不变）。

### 2.2 learning_video_progress 存什么（本阶段核心"给结论"）

完成状态派生不落表的前提下，这张表只存**派生计算不可省略的原始事实**：

| 字段 | 为什么必须在表上 |
| --- | --- |
| `watched_seconds` | 跨会话累计的有效学习秒数是完成判定的输入，也是 7.3 的展示值；不存则每次心跳无法累加 |
| `completed_at` | 完成时间是事实记录（验收 3/4），也是未来错题本/诊断关联"学过什么"的锚点（契约 11.4） |
| `session_id` | 旧版保留，不用于判定；新的学习会话由 `learning_video_sessions` 按进度行隔离 |
| `status` | 完成判定结果的落点（COMPLETED 后冻结），避免每查询都重算视频级完成 |

**不存的东西**：章节完成表、课程进度百分比列、冗余计数列（契约决策 #2，聚合成本低）；`chapter_video_id` 仅作首学溯源（见 3.2 表内说明），任何判定与统计查询不依赖它。

### 2.3 派生查询：服务层内存聚合（选此）vs 单条聚合 SQL

- **内存聚合（选此）**：单机构学员 ≤ 百人、章节 ≤ 几十、视频 ≤ 几十，两步批量查（先查关联行 → 拿 ID 集合 → 一次 IN 批查目标表）即可覆盖全部聚合并保持全程 `LambdaQueryWrapper` 类型安全；M1 错题本跨三表派生、成员列表去 N+1 都是同一手法（`ExamRecordServiceImpl.customPageGetExamRecord` 先例）。
- **单条聚合 SQL（不选，对照形态见 5.4）**：契约 11.6 要求"评估"，结论是不采用——需要新增 XML 或 `@Select` 注解 SQL，破坏 M1 "零 XML"口径；且 `COUNT(DISTINCT CASE WHEN...)` 的章节完成判定（必学集⊆完成集）在 SQL 里表达笨拙、无法复用于 7.1/7.2/7.3 的逐视频展示。量级到千级学员时再演进。

## 3. 数据模型

### 3.1 既有表依赖（不改动）

- `videos`：章节视频的内容实体。EXIST 选取校验 `status=1`（`STATUS_PUBLISHED`）且站内视频 `duration` 非空>0（决策 #19）；EXTERNAL 代建新行（见第 7 节）；观看统计复用 `view_count`/`video_views`。
- `video_views`：公共观看统计，无 user 列。学习心跳可以同步更新，但不作为外链确认依据。
- `classes`/`class_members`/`course_classes`：M1 已建。`course_classes` 本阶段首次接线（增删查，无单条分配接口），唯一索引 `uk_course_class(course_id,class_id)`、反查索引 `idx_course_class_class(class_id)` 已就位，M2 **不加任何索引**。
- `users`：`created_by`/课程创建者姓名、8.1 学员 username/realName 展示。

### 3.2 五张新表（DDL 全文见配套 SQL 脚本，此处列字段与要点）

| 表 | 字段 | 约束/要点 |
| --- | --- | --- |
| `courses` | id、name、description、cover_url、status、created_by、create_time、update_time、is_deleted | BaseEntity 逻辑删除；`status` VARCHAR 取值 `DRAFT/PUBLISHED` 默认 DRAFT（对齐 classes 的 VARCHAR 枚举风格）；`created_by NOT NULL`（创建接口必有登录态），是教师可编辑性的**唯一依据**（契约 1 节：负责班级分配的课程可看可统计不可改）；name 不唯一（决策 #8）；**无二级索引**（论证同 M1 classes） |
| `course_chapters` | id、course_id、title、description、sort、create_time、update_time、is_deleted | BaseEntity 逻辑删除；sort 新增自动置底（服务层 max+1）、上下移交换相邻值、删除后不重排（sort 空洞，决策 #10）；**不建 (course_id, sort) 索引**（第 10 节） |
| `chapter_videos` | id、chapter_id、video_id、required、completion_ratio、sort、create_time | **物理删除**（2.1 论证）；`video_id NOT NULL`（两种来源都落到 videos 行）；`required TINYINT` 默认 1（决策 #11：忘配按必学算，口径保守）；`completion_ratio INT` 默认 70，仅站内视频生效（外链固定 60 秒门槛忽略本值）；唯一 `uk_chapter_video(chapter_id, video_id)` 防重复添加（6.2 不幂等，报"该视频已在本章节中"） |
| `learning_video_progress` | id、user_id、video_id、chapter_video_id、status、watched_seconds、completed_at、create_time、update_time | 学员和视频唯一；完成时间及进度冻结；毫秒精度 `update_time` 限制并发标签页累计。原 `session_id` 保留但不使用。 |
| `learning_video_sessions` | id、progress_id、chapter_video_id、session_id、last_sequence、watched_seconds、last_signal_at | 每个进度和会话 ID 唯一；服务端间隔、序号去重及外链确认均以此表为准。 |

### 3.3 不建的表 / 不加的列

- 不建"章节完成表"/"课程进度表"：派生计算（契约决策 #2），单班聚合成本毫秒级。
- 不给 `videos` 加课程归属列：视频中心是全局资产，章节归属在 `chapter_videos`，删除视频由视频中心自身逻辑管（M2 不动 `VideoServiceImpl.deleteVideo`）。
- 不给 `course_classes` 加 `assigned_at` 列：差量同步下 `create_time` 即首次分配时间（第 8 节）。
- 不加外键：全库无外键先例，引用完整性由服务层级联与唯一索引承担。

## 4. heartbeat 完成判定与防刷（本阶段核心）

### 4.1 处理流程

1. 读取章节视频和课程，验证课程已发布且关联本人所属启用班级。校验视频仍已发布，外链仍在白名单。
2. 第 0 拍只初始化：创建 `(user_id, video_id)` 唯一进度行，再锁定该行。会话表按 `(progress_id, session_id)` 唯一，并绑定章节视频。后续心跳必须逐一递增序号；相同或旧序号不修改进度，跳号拒绝。
3. 每拍最多记 60 秒，也不超过会话两次已接受心跳的服务端时间差。站内进度还受同一进度行时间差约束，防止多个标签页重复累计；完成后冻结累计时间及完成时间。章节完成实时派生。
4. 公共观看量复用 `recordVideoView`，使用从进度 ID 和会话 UUID 派生的独立统计 ID；它只作统计，不能生成或修改个人进度。

### 4.2 外链确认与限制

- 打开外链后开始计时；页面隐藏不累计。确认须提交当前会话 ID；后端锁定本人视频进度行，并读取对应章节视频的个人会话。满 60 秒方可标记完成；重复确认不会修改完成时间。
- 客户端以 `sessionWatchedSeconds` 显示确认资格，刷新后新建会话，不继承此前未完成会话的停留。
- 停留/播放信号和服务端时间只提供学习证据的时间约束，不等于防作弊或验证外站实际播放。停用班级或撤回课程后，新的读取与心跳不再授权。

## 5. 完成状态派生查询（零 N+1）

### 5.1 章节完成判定（服务层内存判定为主）

```java
// 空必学集 → true（空集语义，契约 10.3：教师没配必学 = 不设卡）
boolean chapterCompleted = requiredVideoIds.stream().allMatch(completedVideoIds::contains);
```

DB 侧对照形态（仅对照，不在实现中用；chapter_videos 物理删无 is_deleted）：

```sql
SELECT NOT EXISTS (
    SELECT 1 FROM chapter_videos cv
    LEFT JOIN learning_video_progress p
           ON p.video_id = cv.video_id AND p.user_id = ? AND p.status = 'COMPLETED'
    WHERE cv.chapter_id = ? AND cv.required = 1 AND p.id IS NULL
) AS completed;
```

课程进度 = `floor(已完成章节数 / 章节总数 × 100)`，章节总数 0 → 0%（契约 10.4）。

### 5.2 7.1 我的课程列表（固定 6 次查询）

1. `studentClassIds = classScopeService.getStudentClassIds(userId)`，为空 → **直接返回空数组**（验收 6）；
2. `course_classes WHERE class_id IN (studentClassIds)` → courseId 集合 + 每课程最早 create_time（assignedAt，多班分配取最小）；
3. `courses WHERE id IN (courseIds) AND status='PUBLISHED'`（is_deleted 自动过滤）；
4. `course_chapters WHERE course_id IN (courseIds)`；
5. `chapter_videos WHERE chapter_id IN (章节 ids)`（物理删表无 is_deleted）；
6. `learning_video_progress WHERE user_id=me AND video_id IN (全部视频 ids)` → Set(completedVideoIds)。

内存：逐课程聚合 chapterTotal/chapterCompleted/progressPercent。查询次数与课程数无关。

### 5.3 7.2 课程学习详情 / 7.3 章节学习详情

- 7.2：课程校验（存在+PUBLISHED+分配给本人班级）→ 4 → 5 → 6（同 5.2 复用），按章节分组补 completedVideoCount/completed/requiredVideoCount；目录不含视频列表（契约 7.2 懒加载）。
- 7.3：课程校验同上 → 章节归属校验 → `chapter_videos WHERE chapter_id=?` → `videos selectBatchIds`（标题/来源/时长/封面）→ `progress WHERE user_id=me AND video_id IN (...)`。requiredSeconds：站内 `ceil(duration×ratio/100)`、外链固定 60。共 4-5 次查询。

### 5.4 8.1 班级课程学员完成情况（固定 6 次查询）

1. 数据范围：TEACHER `isTeacherOfClass(userId, classId)` 点查 + `uk_course_class` 点查 (courseId, classId) 分配存在（未分配 → `"该课程未分配给该班级"`）；ADMIN 跳过点查；
2. `class_members WHERE class_id=? AND role_in_class='STUDENT'`（走 uk 前缀）→ studentIds（空 → 空 students 数组）；
3. `users selectBatchIds(studentIds)` → username/realName；
4. `course_chapters WHERE course_id=?` → 章节；
5. `chapter_videos WHERE chapter_id IN (章节 ids)` → 课程全部视频与必学标记；
6. `learning_video_progress WHERE user_id IN (students) AND video_id IN (课程视频 ids)`（uk 前缀 user_id 命中）。

内存按 user 分组：completedVideoCount（含选学）、requiredVideoTotal、completedChapterCount、progressPercent、lastLearnTime=本课程范围 progress 行 `max(update_time)`（无记录 null）。

**单条聚合 SQL 对照形态（评估后不采用，理由见 2.3）**：

```sql
SELECT p.user_id,
       SUM(p.status = 'COMPLETED') AS completed_video_count,
       MAX(p.update_time)          AS last_learn_time
FROM learning_video_progress p
WHERE p.user_id IN (...) AND p.video_id IN (...)
GROUP BY p.user_id;
```

章节完成维度（必学集 ⊆ 完成集）仍需服务层组装，SQL 无法一并给出；且引入 XML/注解 SQL 破坏零 XML 口径。

## 6. 教师三层权限接入点（ClassScopeService）

契约 11.7 结论：**不新增 ClassScopeService 方法**；课程级"创建者校验"是 `created_by` 比对，不进 ClassScopeService。

| 语义 | 接入点 | 判定逻辑 |
| --- | --- | --- |
| 可见（4.1 列表、4.6 回显） | `CourseService` 列表入口 | TEACHER：可见 courseId 集合 = `course_classes WHERE class_id IN (getTeacherClassIds)` 的 course_id ∪ `courses WHERE created_by=me`；两集合并集为空 → 哨兵 `eq(Course::getId, -1L)`（沿用 M1 防呆约定，注释注明"禁止空集合直传 in"）；列表过滤返回子集不报错。ADMIN 全量 |
| 可编辑（2-5 号课程接口、8-17 号章节/章节视频接口） | `CourseService.assertEditable(course, userId, role)` 私有方法，所有写接口统一调用 | ADMIN 放行；TEACHER 要求 `course.created_by == userId`，否则抛 `ClassScopeException("无权访问该课程")` → 全局处理器 403；分配给负责班级但非本人创建的课程可看可统计不可改（契约 1 节语义 2） |
| 可统计（8.1） | `CourseService` 统计入口 | TEACHER：`isTeacherOfClass` 点查 + 分配关系点查（5.4 第 1 步）；ADMIN 任意组合 |
| 分配边界（4.7） | `CourseService.assignClasses` | TEACHER：classIds ⊆ `getTeacherClassIds(userId)`，越界抛 `ClassScopeException("无权访问该班级")`；ADMIN 不限 |
| 学员侧（7.1-7.5） | `LearningService` | `getStudentClassIds(userId)`；空班级集合 → 7.1 空数组、7.2-7.5 统一"课程不存在或未开放"（不泄露课程存在性） |

角色取值沿用 `request.getAttribute("userRole")`（AuthInterceptor 已注入）；STUDENT 调管理接口由 `@RequireRole` 在拦截器层 403。

## 7. 章节视频两种来源与外链代建（结论）

### 7.1 EXIST（视频中心已有）

- 校验：video 存在且 `status=1`；站内视频（`source_type=0`）`duration` 必须非空>0，否则拒绝（决策 #19，无时长无法自动判定）；`source_type=1` 的视频也允许选入。
- **完成通道由 `videos.source_type` 决定，与添加方式（EXIST/EXTERNAL）正交**：站内（0）走 heartbeat 自动判定；外链（1）走 heartbeat 计停留 + external-complete 显式确认。同一章节重复添加同一视频由 `uk_chapter_video` 兜底报错（不幂等，重复无业务意义）。

### 7.2 EXTERNAL（代建 videos 行）

代建字段结论（全部定死，实现不再讨论）：

| videos 字段 | 值 | 理由 |
| --- | --- | --- |
| title | 入参 title（≤100 字） | 契约 6.2 |
| file_url | externalUrl（http/https + 域名白名单校验） | 复用 `resolveVideoFileUrl` 的外链校验思路，白名单额外前置 |
| source_type | 1 | 契约 6.2 |
| status | 1（STATUS_PUBLISHED） | 外链无内容文件可审，直接发布；否则 EXIST 选取校验会挡住自己 |
| uploader_type | 2 | 契约 6.2（避开"用户投稿待审核"队列） |
| uploader_name | 当前用户姓名 | 契约 6.2 |
| duration | NULL | 外链无精确时长；完成走固定 60 秒门槛不依赖 duration；列表显示 durationText=null（契约 6.1 示例） |
| cover_url / description / category_id | NULL | 教师可在视频中心后续补；展示侧已兼容 null |
| admin_id / user_id | 不填 | `admin_id` 语义是管理员上传（`uploadVideoByAdmin` 落该列），教师代建落该列会污染管理端统计；溯源走 chapter_videos→course_chapters→`courses.created_by`（教师编辑校验本来就查课程创建者） |
| view_count / like_count | 0 | 新资产 |

代建行是视频中心资产：移除章节视频**保留不删**（契约 6.4），可被其他章节 EXIST 再选；删除走视频中心既有 `deleteVideo`（M2 不动）。

### 7.3 外链白名单

`ExternalVideoPolicy`（`@Component`）：配置键 `learning.external-video.allowed-domains`（List<String>），默认 `bilibili.com / iqiyi.com / youku.com / v.qq.com / douyin.com`；空配置拒绝外链。按域名标签边界匹配根域和子域，不匹配相似字符串；在新建或挂载已有外链时均校验。独立成类供 6.2 与未来 M5 推荐补课视频复用。

## 8. 课程-班级分配接线（PUT 整体替换）

### 8.1 差量同步实现"整体替换"语义（结论）

目标态 = 调用者可管理范围内的 classIds 全等。管理员管理全部关系；课程创建教师只管理负责班级的关系。课程行锁串行化同一课程的替换请求，以下步骤在同一事务中执行：

1. 查现有关系 `course_classes WHERE course_id=?`，教师额外限定负责班级；
2. 删除可管理范围内不在 classIds 中的行，空数组只清空该范围；
3. 插入缺失行，**交集行与范围外的行不动**。

选差量同步而非契约 4.7 字面的"全删重建"的理由：终态语义完全等价（整体替换 = 替换后集合一致）；保留交集行使 `create_time` 恒为**首次分配时间**（契约 11.5 的 assigned_at 语义），且重复提交同一集合零写入、4.6 回显的 assignedAt 稳定——"幂等：重复提交同一集合，重建结果一致"在差量同步下是字面成立。不需要给 course_classes 加 assigned_at 列。**偏离记录见第 13 节，review 可推翻**（推翻则全删重建，assignedAt 语义随之变为"最近一次分配时间"，需在契约上确认）。

### 8.2 校验顺序

1. 课程存在且可编辑（assertEditable，ADMIN 或创建者）；
2. TEACHER：classIds ⊆ `getTeacherClassIds(userId)`（越界 403）；
3. 班级存在性：`classes WHERE id IN (classIds) AND is_deleted=0` 全命中（禁用班级**允许**分配，可见性只算 ENABLED 由查询侧处理）；缺失 → `"班级不存在"`；
4. classIds 去重后执行 8.1。

### 8.3 4.6 回显

`course_classes WHERE course_id=?` → `classes selectBatchIds`（含禁用班，status 区分）→ studentCount 用 `class_members WHERE class_id IN (...) AND role_in_class='STUDENT'` 按 class 分组 count（一次查询，非逐班 N+1）。两步批量查风格同 M1 成员列表。

## 9. MyBatis-Plus 实体 / Mapper / Service 清单

| 层 | 新增 | 说明 |
| --- | --- | --- |
| entity | `Course`、`CourseChapter`（继承 BaseEntity，逻辑删除） | `@TableName("courses")` / `@TableName("course_chapters")` |
| entity | `ChapterVideo`（物理删，自带 id/createTime）、`LearningVideoProgress`（不删，id/createTime/updateTime） | 照 `CourseClass`/`ClassMember` 模式，**不继承 BaseEntity**（否则 @TableLogic 强加 is_deleted 与表结构冲突） |
| mapper | `CourseMapper`、`CourseChapterMapper`、`ChapterVideoMapper`、`LearningVideoProgressMapper` | 仅 BaseMapper；`CourseClassMapper` M1 已有直接复用；**M2 零 XML** |
| service | `CourseService(+Impl)`：课程 CRUD/状态切换、可见集合、分配差量同步、4.6/8.1 | Wrapper + 哨兵防呆 |
| service | `ChapterService(+Impl)`：章节增删改移、章节视频 EXIST/EXTERNAL 添加/配置/移除/移序 | Wrapper；EXTERNAL 代建走 `VideoMapper.insert`（不注入 VideoService，避免拉入上传逻辑） |
| service | `LearningService(+Impl)`：heartbeat、external-complete、7.1/7.2/7.3 学员聚合 | Wrapper；注入 `VideoService` 复用 `recordVideoView`（跨 bean 调用事务生效） |
| controller | `CourseController(/api/courses)`、`ChapterController(/api/chapters)`、`ChapterVideoController(/api/chapter-videos)`、`MyCourseController(/api/my/courses)`、`LearningController(/api/learning)` | 路径严格按契约第 3 节 23 条；Swagger 分组"课程管理/我的课程/学习进度" |
| 组件 | `ExternalVideoPolicy` | 第 7.3 节 |
| vo | 课程分页项、我的课程项、章节节点、章节视频（管理/学员两视角）、班级统计学员项等 | 展示字段按契约 4.1/6.1/7.2/7.3/8.1 |

**Wrapper vs XML 判断标准**（延续 M1）：等值/IN/唯一键查询用 Wrapper；M2 的全部聚合（7.1/7.2/7.3/8.1）均以"两步批量查 + 内存分组"实现，无跨表 JOIN 需求，不进 XML。

## 10. 索引设计（逐条理由）

| 索引 | 状态 | 理由 |
| --- | --- | --- |
| `chapter_videos` `UNIQUE uk_chapter_video(chapter_id, video_id)` | 本脚本新增（建表内含） | 三合一：重复添加唯一兜底（6.2 报"已在本章节中"）、按章节列视频/统计 videoCount（最左前缀 chapter_id）、8.1 的 chapter_id IN 批查 |
| `learning_video_progress` `UNIQUE uk_progress_user_video(user_id, video_id)` | 本脚本新增（建表内含） | 三合一：进度行唯一（规格明文 user+video）、heartbeat 点查（最左前缀 user_id,video_id 等值）、8.1 的 user_id IN + video_id IN（user_id 前缀命中后回表过滤，人均行数少） |
| `courses` 无二级索引 | 结论：不建 | 量级百级（单机构课程数），PK 点查/全表扫描毫秒级；keyword 模糊 `LIKE '%x%'` 索引帮不上；status 低基数；created_by 点查在百级表不值得建（口径同 M1 classes） |
| `course_chapters` 无二级索引（含不采纳契约 11.2 建议的 `(course_id, sort)`） | 结论：不建 | 章节 = 课程×章节数，百级；按课程取章节全扫+按 sort 排序几行 filesort 无感；先例同 M1 knowledge_points 不建 sort 索引。演进条件：课程×章节过千补 `KEY idx_chapter_course_sort(course_id, sort)` |
| `chapter_videos` 不建 `(chapter_id, sort)`（契约 11.3 建议不采纳） | 结论：不建 | uk 最左前缀 chapter_id 已覆盖过滤方向，sort 排序发生在单章节几行内；同上口径 |
| `learning_video_progress` 不建 video_id 反查索引 | 结论：不建 | 全部查询方向恒以 user_id 打头（7.x 本人、8.1 user_id IN），uk 前缀够用；无"按视频反查学习者"需求 |
| `course_classes` `uk_course_class(course_id, class_id)` + `idx_course_class_class(class_id)` | **已存在**（M1），本脚本不重复创建 | 正查"课程分配了哪些班"走 uk 前缀，反查"班级有哪些课程"（7.1 学员可见集合、教师可见集合）走 idx_course_class_class；脚本尾附验证语句确认接线前提 |

## 11. 实施顺序（任务可独立验证，标注依赖）

1. **仅在授权测试环境验证增量 SQL**：先核对目标库，按部署指南顺序执行课程与个人会话脚本；不要重复执行。
2. **实体 + Mapper 四组**（依赖 1）：`Course`/`CourseChapter`（BaseEntity）+ `ChapterVideo`/`LearningVideoProgress`（物理存在不删）；独立验证 CRUD 与唯一索引兜底（重复添加章节视频撞 uk、并发心跳撞 uk 后转幂等）。
3. **课程-班级分配接线**（依赖 2）：分配差量同步 + 4.6 回显；独立验证：分配 2 班 → 追加 1 删 1 → create_time 仅新增行变化 → 重复提交同集合零写入。
4. **课程管理域**（依赖 2、3）：CRUD/状态切换/可见集合（哨兵防呆）/assertEditable；独立验证：教师只见"负责班级已分配 ∪ 本人创建"，无班级教师空列表绝非全量，非创建者教师改他人课程 403。
5. **章节与章节视频域**（依赖 4）：增删改移、EXIST/EXTERNAL 添加（ExternalVideoPolicy + 代建 videos 行）；独立验证：外链白名单拦截、站内缺 duration 拒绝、重加同视频报已存在、sort 上下移交换。
6. **学习进度域**（依赖 5）：heartbeat（复用 recordVideoView + 防线 1-3 + 条件更新完成判定）、external-complete、7.1/7.2/7.3 聚合；独立验证：验收 3/4/5 全链路（达标自动完成、重看不重复计、外链 60 秒拒绝与放行）。
7. **教师统计**（依赖 6）：8.1 聚合；独立验证：全班每人一行、未入班学员不出现（验收 6 教师侧印证）。
8. **AI review + 全量回归**（依赖 4-7）：`mvn test` 全绿（47+ 用例 + 新增进度/权限/防刷用例）、`npm run build` 通过、错题本/考试/判卷回归（验收 9）。
9. **用户 review**：推送功能分支开 PR，附验收清单对照；云端库执行 SQL 后联调。

## 12. 验证清单（交付前核对）

- [ ] teacher1 建课程分配"Java 培训一班" → 班内学员 `/api/my/courses` 可见（验收 1）；分配新班级 → 新班学员立即可见（验收 7，查询时派生无缓存）
- [ ] 章节视频 EXIST（站内缺 duration 被拒）/ EXTERNAL（代建 videos 行 source_type=1 status=1 duration=null）→ 学习页两种来源展示与完成通道正确（验收 2）
- [ ] 站内视频 watched_seconds 达 `ceil(duration×ratio/100)` → heartbeat 响应 completed=true、chapterCompletedAfterThisVideo=true；课程进度 50%（1/2 章节）（验收 3）
- [ ] 已完成视频重看：状态不变、completedAt 不变、watched_seconds 冻结、观看次数 +1（新会话）（验收 4）
- [ ] 外链停留 <60 秒确认 → `"请继续观看满 60 秒后再确认完成（还差 X 秒）"`；≥60 秒 → 成功；重复确认幂等（验收 5）
- [ ] 未入班学员课程列表空数组；无班级教师课程列表空（哨兵防呆，绝非全量）（验收 6）
- [ ] 教师编辑权：非创建者教师 PUT/DELETE 他人课程 → 403"无权访问该课程"；教师分配越权班级 → 403"无权访问该班级"
- [ ] heartbeat 防刷：负数增量按 0、单拍 >60 截 60、空 sessionId 报参数错误；（若采纳建议项）脚本连发被服务端间隔折算压制
- [ ] external-complete：站内视频调用被拒、无进度行被拒、行缺失防御拒绝
- [ ] 章节视频移除后重新添加同一视频成功（物理删不占唯一键）；同章节重复添加被拒"该视频已在本章节中"
- [ ] 课程删除：章节逻辑删、chapter_videos 物理删、分配行清空、learning_video_progress 保留；重学同名课程不复用旧进度
- [ ] 修改 required/completionRatio 只影响后续判定：已完成的进度与完成时间不回退；改必学→选学后章节完成条件立即放宽（派生）
- [ ] 8.1：班级每人一行、completedChapterCount/lastLearnTime 正确、无学习记录 lastLearnTime=null；TEACHER 访问未分配班级课程 → "该课程未分配给该班级"
- [ ] `EXPLAIN` 心跳锁定走 `uk_progress_user_video`；个人会话查询使用 `uk_learning_progress_session`
- [ ] `/api/learning/**`、`/api/courses/**`、`/api/chapters/**`、`/api/chapter-videos/**` 未登录 → 401（未加入 AuthInterceptor 白名单，与 `/api/videos/**` 公开白名单区分）
- [ ] 判卷/考试/错题本/视频中心 `/api/videos/{id}/view` 回归全绿（验收 9）

## 13. 与契约的偏离记录（review 可推翻项）

| # | 偏离 | 契约原文 | 偏离理由 | 推翻成本 |
| --- | --- | --- | --- | --- |
| 1 | `chapter_videos` **物理删除** | 11.3"逻辑删除"、5.4/6.4"级联逻辑删除" | `uk_chapter_video` 与"移除后重加同视频"冲突，逻辑删旧行占死唯一键；M1 已论证 `UNIQUE(...,is_deleted)` 不可行且需 5.7 兼容；纯配置行无审计价值 | 若坚持逻辑删除需引入 M3 式复活 SQL（UPDATE is_deleted=0）+ 5.7 兼容性论证，接口契约不变 |
| 2 | 分配 PUT 用**差量同步**实现整体替换 | 4.7"删除全部现有关系后全量重建" | 终态语义等价；create_time 即首次分配时间（11.5 的 assigned_at 语义），重复提交真幂等零写入；不加 assigned_at 列 | 推翻则改回全删重建，assignedAt 变"最近分配时间"，需契约确认 |
| 3 | 不建 `(course_id, sort)`/`(chapter_id, sort)` 索引 | 11.2/11.3"建议" | 表量级百级，uk 前缀已覆盖过滤方向，sort filesort 无感；M1 knowledge_points 同口径 | 推翻则补两条普通索引，一行 DDL |
| 4 | 本人进度下独立的学习会话 | 不重绑 progress 旧会话列，不读取公共观看统计 | 按章节视频校验会话归属、隔离用户与刷新 | 新增独立增量脚本 |
| 5 | 进度行锁、序号去重与服务端时间上限 | 明确第 0 拍不计时间、重试不累加 | 保留并发进度和外链停留的可信边界 | MySQL 跨事务验收待进行 |
