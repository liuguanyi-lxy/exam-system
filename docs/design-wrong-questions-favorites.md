# 设计文档：错题本 + 题目收藏

- 日期：2026-11-15
- 范围：仅数据库表设计与后端实现方案（配套增量脚本 `exam-system-server/sql/2026-11-15_wrong_questions_favorites.sql`）
- 约束：判卷链（`ExamServiceImpl.gradeExam`）不改；API 契约由并行设计文档定义，本文接口路径仅供参考，以契约为准

## 1. 结论速览

| 决策点 | 结论 |
| --- | --- |
| 错题本实现方式 | **方案A：实时派生**。从 `answer_record JOIN exam_records` 按 `user_id` 定界聚合，不建错题物化表 |
| 移出/已掌握状态 | 新表 `wrong_question_mastered`，存 `(user_id, question_id, mastered_time)`，派生查询 LEFT JOIN 过滤 |
| 收藏表 | 新表 `question_favorites`，`UNIQUE(user_id, question_id)`，**物理删除** |
| 新 Controller | `WrongQuestionController` 与 `FavoriteController` 各自独立新建，不塞进 `QuestionController` |
| 判卷链改动 | **零改动**（已验证：判卷后每条答题记录 `is_correct` 必非空，未判卷行为 NULL 被派生查询天然过滤） |

## 2. 方案对比与选型理由

### 方案A：实时派生（推荐）

查询链：`users.id → exam_records.user_id（已有索引 idx_exam_records_user）→ answer_record.exam_record_id → is_correct=0 聚合`。

- **一致性**：无同步问题。`answer_record.is_correct` 是唯一事实来源，判卷落库即可见；重判、AI 判卷失败兜底（异常分支写 `is_correct=0`）等所有分支的结果自动生效，不存在"错题表忘了更新"这类脏数据。
- **查询成本**：查询始终按 `user_id` 定界。单个学生的考试记录为几十条量级、答题记录几百到几千条量级，聚合在该子集上完成，成本与单人数据量线性相关，**不随全表增长**。现有两个索引（见第 5 节）支撑下为毫秒级。
- **开发量**：一个 MyBatis XML 聚合查询 + 一张标记表 CRUD；判卷链零改动，无历史数据回填（回填历史错题反而依赖历史记录 `user_id` 已回填，`2026-11-15_exam_records_user_id.sql` 已做）。

### 方案B：判卷时写错题表（不推荐）

- **一致性风险**：`gradeExam` 存在多条写入路径（客观题即时判卷、AI 批量判卷、批量失败回退单题并行、异常兜底分支），错题表必须在每个分支同步维护；且 `gradeExam` 无 `@Transactional`，答题记录与错题表跨两次写库，任一失败即产生"答错但错题本没有/已答对但错题本残留"的脏数据，还需要对账脚本。
- **查询成本收益**：错题表直查确实更快，但当前规模（考试记录万级、答题记录十万级以内）下派生查询已经足够快，收益无法抵消一致性成本。
- **开发量**：判卷链 2 处改动 + 历史数据回填 + 对账脚本，明显更多。

**结论：选方案A，并用 `wrong_question_mastered` 存"已掌握/移出"状态。** 若未来学生量级到十万、答题记录到百万级且错题本成为高频页面，再考虑引入物化表（可用"判卷成功后异步写错题表 + 定期对账"演进，本设计不预做）。

## 3. 数据模型

### 3.1 既有表依赖（不改动）

- `exam_records`：`user_id BIGINT NULL` + `idx_exam_records_user`（`2026-11-15_exam_records_user_id.sql` 已建）。`status` 取值 `进行中/已结束/已批阅`（`ExamStatus` 常量类）。
- `answer_record`：注意**实际表名是单数 `answer_record`**，以 `AnswerRecord` 实体 `@TableName("answer_record")` 为准（本文与 SQL 脚本均用单数）。判卷前 `is_correct` 为 NULL（提交构造器只写 `examRecordId/questionId/userAnswer`），判卷后写入 0/1/2。逻辑删除字段 `is_deleted` 存在（`ExamRecordServiceImpl.customRemoveById` 对其做逻辑删）。
- `questions`：`type/multi/difficulty/category_id`，逻辑删除 `is_deleted`。

### 3.2 新表一：题目收藏 `question_favorites`

```sql
CREATE TABLE question_favorites (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '收藏人，关联 users.id',
    question_id BIGINT       NOT NULL COMMENT '题目ID，关联 questions.id',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorites_user_question (user_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目收藏表（学生维度，物理删除）';
```

**为什么物理删除而不是逻辑删除**（结论）：

1. **唯一索引与 `is_deleted` 复用冲突**：若加 `is_deleted` 并建 `UNIQUE(user_id, question_id)`，取消收藏后旧行仍占用唯一键，再次收藏必然撞 `DuplicateKeyException`；改用 `UNIQUE(user_id, question_id, is_deleted)` 也只解决第一轮——第二次"收藏→取消"会出现两行 `is_deleted=1` 直接违反唯一约束（MySQL 5.7 无函数索引，无法像 `uk_exam_active_student_paper` 那样用生成列规避，除非引入删除时间戳生成列，复杂度不值）。
2. **无审计需求**：收藏是纯用户偏好状态，取消后没有业务含义；错题数据在 `answer_record` 里，不依赖收藏行。
3. **项目先例**：行为表 `video_likes` 就是无 `is_deleted` 的物理删除风格，本表与其对齐。

实体类不继承 `BaseEntity`（否则 `@TableLogic` 会在 MP 生成的 SQL 里强加 `is_deleted = 0` 与表结构冲突），自带 `id/userId/questionId/createTime` 字段，模式照抄 `VideoLike`。`create_time` 表级默认值兜底，`MyMetaObjectHandler.strictInsertFill` 仍会填充同名字段。

**重复收藏幂等**：Service 先 `selectCount` 判存（给前端友好响应），并发兜底靠唯一索引——直接 `save()` 并 `catch (DuplicateKeyException)` 按"已收藏"成功返回。该模式与 `ExamServiceImpl.startExam` 处理 `uk_exam_active_student_paper` 的写法一致。取消收藏物理 `remove`，与逻辑删不同不存在"复活"问题。

### 3.3 新表二：已掌握标记 `wrong_question_mastered`

```sql
CREATE TABLE wrong_question_mastered (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT   NOT NULL COMMENT '学生，关联 users.id',
    question_id  BIGINT   NOT NULL COMMENT '题目ID，关联 questions.id',
    mastered_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标记掌握时间；重复标记时更新为最新时间',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次标记时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mastered_user_question (user_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题已掌握标记表（物理删除，取消掌握即删行）';
```

- 与派生查询的关系：**LEFT JOIN 探测**。`ON mm.user_id = #{userId} AND mm.question_id = w.question_id`，两个等值条件正好命中唯一索引，点查成本。
- **移出语义（推荐"再错自动回归"）**：错题本过滤条件为 `mm.id IS NULL OR mm.mastered_time < 最近一次错误时间`。即标记掌握后题目移出错题本；之后再做错同题（新错误时间晚于 `mastered_time`）自动回到错题本，无需判卷链写任何数据。该语义完全由 SQL 表达，并支持"已掌握"Tab 的反向查询（`mm.id IS NOT NULL AND mm.mastered_time >= 最近一次错误时间`）。
- 取消掌握 / 恢复 = 物理删除标记行；重复标记掌握 = 先查再更新 `mastered_time`，并发兜底 `catch (DuplicateKeyException)` 后改为 `updateById`（与收藏同模式）。物理删除理由同 3.2 第 1、2 点。

### 3.4 不建的表

不建错题物化表（见第 2 节）；不建收藏/掌握的宽表冗余题目字段——题目详情由查询时 JOIN `questions` 获取，避免题目编辑后快照过期。

## 4. 数据流与边界语义

| 场景 | 派生方案下的行为 |
| --- | --- |
| 判卷完成（含 AI 异常兜底分支） | 答错的行 `is_correct=0` 落库，错题本立即可见，无任何额外写入 |
| 未判卷（进行中/已交卷未批阅） | `is_correct IS NULL`，被聚合条件 `ar.is_correct = 0` 过滤，不进错题本 |
| 学生删除考试记录 | `customRemoveById` 逻辑删 `exam_records` 与对应 `answer_record`，该卷错题自动从错题本消失（预期行为，双表都过滤 `is_deleted=0`） |
| 教师删除题目 | `questions.is_deleted=1`，JOIN 条件过滤后该题从错题本与收藏列表消失；收藏/掌握标记行残留（无业务影响，可忽略；如需洁癖可加定期清理） |
| 历史记录 `user_id IS NULL` | 不进错题本。与既有归属校验（user_id 空时按姓名弱校验）的弱覆盖口径一致；姓名匹配的历史数据无法可靠归属到账号，不强造归属 |
| 同一试卷重复考试 | 多次错误都计入 `wrong_count`，"最近一次错误答案/时间"取最新一条 |
| 学生查看自己错题本 | 接口一律使用 `request.getAttribute("userId")` 强制定界，前端传参不参与归属 |

## 5. 索引设计（逐条理由）

| 索引 | 状态 | 理由 |
| --- | --- | --- |
| `exam_records` `idx_exam_records_user(user_id)` | **已存在**（`2026-11-15_exam_records_user_id.sql`），本脚本不重复创建，仅验证 | 派生查询的驱动入口：按学生定位其全部考试记录，range 扫描 |
| `answer_record` `idx_answer_record_exam_correct(exam_record_id, is_correct)` | **本脚本新增** | 派生查询第二跳：对每条命中的 `exam_records.id` 做等值探测。复合顺序先 `exam_record_id`（等值）后 `is_correct`（等值过滤），索引内直接筛掉答对/未判卷行，减少回表。仓库内无建表 DDL、无法从代码查实 `exam_record_id` 是否已有单列索引，脚本先给验证语句；若已存在单列索引，复合索引仍值得加（覆盖过滤列），若完全重复则报 `Duplicate key name`，无害可跳过 |
| `question_favorites` `UNIQUE uk_favorites_user_question(user_id, question_id)` | 本脚本新增（建表内含） | 三合一：幂等兜底（重复收藏撞唯一键）、"是否已收藏"点查、按 `user_id` 前缀列出收藏列表 |
| `wrong_question_mastered` `UNIQUE uk_mastered_user_question(user_id, question_id)` | 本脚本新增（建表内含) | 幂等兜底 + LEFT JOIN 探测点查 |
| `questions.type / difficulty / category_id` 不加索引 | — | 错题本分页是先按人聚合（结果几十行内）再 JOIN `questions` 主键点查并过滤，`questions` 侧走主键，单列过滤索引帮不上；题库列表页现有查询模式也未依赖它们 |

## 6. 派生查询设计（错题本分页 SQL 草稿）

放置于 `AnswerRecordMapper.xml`（主表是 `answer_record`，与现有 XML 风格一致；不需要为纯查询建假实体 Mapper）。MyBatis-Plus 分页拦截器已配置（`MybatisPlusConfiguration`），Mapper 方法首参传 `Page` 即可自动物理分页与 count。

```xml
<select id="selectWrongQuestionPage" resultType="com.atguigu.exam.vo.WrongQuestionVo">
    select
        w.question_id,
        w.wrong_count,
        w.last_wrong_time,
        d.exam_record_id  last_exam_record_id,
        d.user_answer     last_wrong_answer,
        d.ai_correction,
        qs.type,
        qs.multi,
        qs.difficulty,
        qs.category_id,
        qs.title,
        qs.analysis
    from (
        select
            ar.question_id,
            count(*)            wrong_count,
            max(ar.create_time) last_wrong_time,
            /* 取"最近一次错误"所在的考试记录ID：按错误时间倒序取第一个ID，
               用 id 做并列时间下的稳定并列破除；只拼 ID 不拼答案内容，
               天然避开 GROUP_CONCAT 分隔符与答案内容（多选"A,B"）冲突 */
            cast(substring_index(group_concat(ar.exam_record_id order by ar.create_time desc, ar.id desc), ',', 1) as signed) last_exam_record_id
        from answer_record ar
        join exam_records er on er.id = ar.exam_record_id
        where er.is_deleted = 0
          and ar.is_deleted = 0
          and er.user_id = #{userId}
          and ar.is_correct = 0
        group by ar.question_id
    ) w
    join answer_record d on d.exam_record_id = w.last_exam_record_id and d.question_id = w.question_id
    join questions qs on qs.id = w.question_id and qs.is_deleted = 0
    left join wrong_question_mastered mm on mm.user_id = #{userId} and mm.question_id = w.question_id
    <where>
        (mm.id is null or mm.mastered_time &lt; w.last_wrong_time)
        <if test="type != null and type != ''"> and qs.type = #{type}</if>
        <if test="difficulty != null and difficulty != ''"> and qs.difficulty = #{difficulty}</if>
        <if test="categoryId != null"> and qs.category_id = #{categoryId}</if>
    </where>
    order by w.last_wrong_time desc
</select>
```

要点说明：

- **聚合只产出 ID 与统计值**，"最近一次错误答案/批语"通过 `last_exam_record_id + question_id` 回连 `answer_record` 取整行——一次考试同一题只有一条答题记录（交卷状态校验防重复提交），回连 1:1，避免 `GROUP_CONCAT` 拼接用户答案带来的分隔符、转义与长度问题。
- **MySQL 5.7 兼容**：无窗口函数；`GROUP_CONCAT` 仅拼数字 ID，默认 `group_concat_max_len=1024` 上限对应单题约百余次错误，单人单题不可能触达（若极端担心可在会话层调大，不写入 mapper）。
- **已掌握过滤放在外层 WHERE**：`mm.mastered_time` 需与聚合别名 `w.last_wrong_time` 比较，聚合别名不能进 WHERE，故聚合包成派生表 `w` 后再过滤；同时保证 MP 自动生成的 count 语句语义一致，分页总数正确。
- **已掌握 Tab** 为同结构反向条件：`(mm.id is not null and mm.mastered_time >= w.last_wrong_time)`，建议做成第二个 `<select>` 而非参数开关，SQL 可读性更好。
- 排序 `last_wrong_time desc`：最近错的排前面；如需"错得最多的排前面"可加参数切换 `wrong_count desc`。
- 题型/难度/分类筛选过滤的是聚合后的题目属性，数据量小，无需下沉进内层。

## 7. 后端实现要点

### 7.1 代码清单（全部为新增，不改动现有文件）

| 层 | 新增 | 说明 |
| --- | --- | --- |
| entity | `QuestionFavorite`、`WrongQuestionMastered` | 不继承 `BaseEntity`（物理删除，见 3.2），自带 `id/userId/questionId/createTime`（+ `masteredTime`），照 `VideoLike` 模式 |
| vo | `WrongQuestionVo` | 第 6 节 SQL 列对应的出参对象；`QuestionFavorite` 列表复用 `Question`（service 组装 choices/answer） |
| mapper | `QuestionFavoriteMapper`、`WrongQuestionMasteredMapper` | 仅 `BaseMapper`，无自定义 SQL（判存/删除用 Wrapper） |
| mapper | `AnswerRecordMapper.selectWrongQuestionPage` / `selectMasteredQuestionPage` | 自定义 XML（第 6 节），主表 `answer_record`，首参 `Page` 供分页拦截器 |
| service | `WrongQuestionService`(+Impl)、`FavoriteService`(+Impl) | 见 7.2 |
| controller | `WrongQuestionController`、`FavoriteController` | 见 7.3 |

### 7.2 Service 关键方法

- `FavoriteService`：
  - `addFavorite(userId, questionId)`：先 `selectCount(user_id, question_id)` 判存给友好提示；`save()` 外包 `try/catch (DuplicateKeyException)` 并发兜底（参考 `ExamServiceImpl.startExam` 的"撞唯一索引即视为已存在"写法）。
  - `removeFavorite(userId, questionId)`：Wrapper 定位后物理 `remove`。
  - `pageFavorites(userId, page...)`：先按唯一索引前缀查收藏行（可按 `create_time desc` 排），再按本页 `question_id` 批量查 `questions/question_choices/question_answers` 组装（参照 `ExamRecordServiceImpl.customPageGetExamRecord` 的批量去 N+1 手法），照 `QuestionSanitizer` 口径**不下发**之外——收藏页学生需要看题目与选项即可，答案解析是否下发跟随错题本口径（见 7.4）。
- `WrongQuestionService`：
  - `pageWrongQuestions(userId, page, type, difficulty, categoryId)`：直调 `AnswerRecordMapper.selectWrongQuestionPage`。
  - `markMastered(userId, questionId)`：查标记行，存在则更新 `mastered_time`；不存在则插入，`catch (DuplicateKeyException)` 回退更新。
  - `unmarkMastered(userId, questionId)`：物理删除标记行。
- Mapper 层其余操作全部用 MyBatis-Plus `LambdaQueryWrapper`（两表结构简单，等值/唯一键查询不值得写自定义 SQL）。

### 7.3 Controller 放置结论：两个都独立新建

- `WrongQuestionController`（`/api/wrong-questions`）：`GET /page`（错题本分页）、`GET /mastered/page`（已掌握列表）、`POST /{questionId}/master`（标记掌握）、`DELETE /{questionId}/master`（取消掌握）。
- `FavoriteController`（`/api/favorites`）：`GET /page`（收藏列表）、`POST /{questionId}`（收藏）、`DELETE /{questionId}`（取消收藏）、`GET /{questionId}/exists`（详情页红心状态）。

**不放 `QuestionController`**：该类现有职责是题目管理域（教师向 CRUD + 学生脱敏下发），收藏/错题本是学生行为域，混入会让脱敏逻辑和职责边界变糊；且视频模块已有先例——行为数据独立表（`video_likes`）+ 独立路径（`POST /{videoId}/like`）。错误约定沿用项目现状：业务失败抛 `RuntimeException` 交全局异常处理器，成功走 `Result.success`；登录校验依赖 `AuthInterceptor` 注入的 `request.getAttribute("userId"/"userRole")`，接口一律用当前登录人 `userId` 定界（教师/管理员同样查自己的错题本与收藏，无需按角色分支）。

### 7.4 与判卷链的衔接验证（派生方案零改动的依据）

已逐分支核对 `ExamServiceImpl.gradeExam`：

1. 客观题分支：判对写 `isCorrect=1`，判错写 `isCorrect=0`；
2. AI 判卷分支：0 分写 0、满分写 1、部分分写 2；
3. **判卷异常 catch 分支也写 `isCorrect=0`**（含题目缺失/缺答案/缺分值/AI 结果缺失）——即判卷完成后**不存在 `is_correct` 为 NULL 的答题行**；
4. `answerRecordService.updateBatchById` 一次性写回。

因此派生查询用 `ar.is_correct = 0` 单条件即可可靠区分"已判卷且答错"，无需 JOIN `exam_records.status`（status 与答题行落库是两次独立更新，依赖 status 反而引入半更新窗口）。`customSubmitAnswer`/`startExam`/`gradeExam` 的签名与逻辑均不动，与并行 API 契约代理的"mastered 标记 + LEFT JOIN 派生"假设吻合。

### 7.5 已知下发口径注意点

- 现有 `GET /api/questions/{id}` 对学生做答案脱敏（`QuestionSanitizer.stripAnswer`），**错题本不能复用该详情接口**——错题本的核心价值就是带出正确答案与解析。口径：错题本列表 SQL 直接返回 `analysis` 与最近一次错误批语；正确答案（`question_answers.answer`）与选项建议由 service 在错题本上下文按本页题目批量补齐下发。具体字段以并行 API 契约文档为准，本设计保证数据可得。

## 8. 实施顺序（任务可独立验证，标注依赖）

1. **执行增量 SQL**（`sql/2026-11-15_wrong_questions_favorites.sql`）：建两表 + `answer_record` 复合索引；先跑验证语句确认前置索引状态。无代码依赖，可先行。
2. **实体 + Mapper**（依赖 1）：`QuestionFavorite`、`WrongQuestionMastered` 实体与两个 BaseMapper；可独立用单测/接口工具验证 CRUD 与唯一索引兜底。
3. **`FavoriteService` + `FavoriteController`**（依赖 2）：收藏增删查与红心状态；独立可验证（不依赖错题本）。
4. **`WrongQuestionVo` + `AnswerRecordMapper` XML 聚合查询**（依赖 1；与 3 并行）：先在库上用第 6 节 SQL 手工 `EXPLAIN` 验证走 `idx_exam_records_user` → `idx_answer_record_exam_correct`。
5. **`WrongQuestionService` + `WrongQuestionController`**（依赖 4）：错题本分页、标记/取消掌握、已掌握列表；验证与真实判卷数据的联动（判一卷→错题出现→标记掌握→消失→再错→回归）。
6. **联调**（依赖 3、5）：前端对接并行契约文档定义的接口；确认学生脱敏例外口径。

## 9. 验证清单（交付前核对）

- [ ] 重复并发收藏：只落一行，另一请求不报 500（DuplicateKeyException 被吞为幂等成功）
- [ ] 取消收藏后再次收藏：成功（物理删除不占唯一键）
- [ ] 判卷新错题即时出现在错题本；未判卷记录不出现在错题本
- [ ] 标记掌握后从错题本消失、出现在已掌握列表；再错同题后自动回到错题本
- [ ] 删除考试记录后其错题从错题本消失；删除题目后错题本/收藏不再展示该题
- [ ] `EXPLAIN` 派生查询：`exam_records` 走 `idx_exam_records_user`，`answer_record` 走 `idx_answer_record_exam_correct`
- [ ] 学生只能看到 `userId` = 本人 的错题与收藏（伪造 `questionId`/分页参数无法越界）
