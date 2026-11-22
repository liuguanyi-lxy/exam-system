# 接口契约：错题本 + 题目收藏

> 状态：设计稿（待实现）
> 适用端：exam-system-server（Spring Boot 3 + MyBatis-Plus）、exam-system-web（Vue3 + Pinia + Element Plus）
> 面向读者：前后端开发、architect（表设计联动见第 6 节）

---

## 1. 功能定位

| 功能 | 说明 | 数据来源 |
| --- | --- | --- |
| 错题本 | 学生本人历史考试中**答错的题**的聚合视图，用于复习。按题目去重，展示最近一次错误答案、最近一次考试时间与试卷名、累计错误次数；支持"标记已掌握"移出错题本 | `answer_records`（is_correct=0）→ `exam_records` → `questions` 系列表 |
| 题目收藏 | 学生主动收藏题库中的题目，形成个人收藏夹 | 新增收藏表 + `questions` 系列表 |

两个功能均为**学生个人学习数据**，接口路径中一律不出现 `userId` 参数，归属人一律从 token 解析（复用 `AuthInterceptor` 写入的 `request.getAttribute("userId")`），与 `ExamRecordController` 的归属校验思路一致。

---

## 2. 通用约定（与现有接口对齐）

### 2.1 认证与角色

- 所有接口需携带请求头 `Authorization: Bearer <token>`；两个路径前缀（`/api/wrong-questions`、`/api/question-favorites`）**不加入** `AuthInterceptor` 公开白名单。
- 所有接口标注 `@RequireRole({"STUDENT"})`：**仅学生本人可访问**。
- **TEACHER / ADMIN 访问这两个功能一律 HTTP 403**。理由：
  1. 错题本与收藏是"个人复习/收纳"数据，教师/管理员没有查看或操作他人错题、收藏的业务场景（批阅看的是整卷成绩，不是错题本）；
  2. 若开放教师查看，就必须引入 `userId` 查询参数，等于在个人数据接口上开越权口子；
  3. 未来若确有"教师查看某学生错题分析"的需求，应另立管理端接口（如 `/api/teacher/students/{userId}/wrong-questions`），而不是在本契约上扩展。
- 未登录 / token 失效：由 `AuthInterceptor` 统一返回。

### 2.2 统一响应结构

与现有 `Result<T>` 完全一致：

```json
{ "code": 200, "message": "操作成功", "data": { } }
```

- 成功：`code = 200`。
- **业务失败：HTTP 200 + `code = 500`**（沿用 `GlobalExceptionHandler` + `Result.error(message)` 的现有风格，如"题目不存在"），不为本模块新引入 4xx 业务码。
- 401 / 403：仅由 `AuthInterceptor` 产生，HTTP 状态码与 body `code` 相同（见 2.4）。

### 2.3 分页

与 `/api/questions/list`、`/api/exam-records/list` 一致：

- 请求参数：`page`（默认 1，从 1 开始）、`size`（默认 10）。
- 响应 `data` 为 MyBatis-Plus `Page` 序列化结果：

```json
{
  "records": [ ],
  "total": 42,
  "size": 10,
  "current": 1,
  "pages": 5
}
```

其余框架字段（如 `orders`）前端忽略。

### 2.4 错误码语义（全模块统一）

| HTTP | body code | 触发条件 | 前端处理 |
| --- | --- | --- | --- |
| 401 | 401 | 未登录或 token 过期（`AuthInterceptor`） | `request.js` 响应拦截器已有处理：登出并跳转 `/login`，本模块无需额外处理 |
| 403 | 403 | 已登录但角色非 STUDENT（TEACHER/ADMIN 访问本模块，`@RequireRole` 拦截） | 提示"没有权限执行该操作"；本模块页面只挂学生端导航，正常不会触发 |
| 200 | 500 | 业务失败（题目不存在、该题不在你的错题本中、参数非法等），`message` 为用户可读文案 | `request.js` 已统一 `ElMessage.error(message)` 弹出 |

### 2.5 其他约定

- **筛选参数非法值处理**：`type` / `difficulty` 传了枚举外的值时**忽略该筛选条件**（与 `/api/exam-records/list` 对非法 `status` 的处理一致）；`categoryId` 非数字或不存在时同样忽略。
- **时间字段格式**：本契约新增的时间字段统一 `yyyy-MM-dd HH:mm:ss` 字符串（与 `ExamRecord.startTime` 的 `@JsonFormat` 下发格式一致）。
- **幂等**：收藏 / 取消收藏 / 标记已掌握 / 取消已掌握，重复调用均返回成功，不报错、不产生重复数据（依赖唯一索引兜底）。
- 后端实现时按现有风格补充 Swagger 注解（`@Tag` / `@Operation` / `@Parameter`）， Knife4j 分组名："错题本"与"题目收藏"。

---

## 3. 接口总览

| # | 方法 | 路径 | 说明 | 角色 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/api/wrong-questions/list` | 错题本分页列表（按题聚合） | STUDENT |
| 2 | POST | `/api/wrong-questions/{questionId}/mastered` | 标记已掌握（移出错题本），幂等 | STUDENT |
| 3 | DELETE | `/api/wrong-questions/{questionId}/mastered` | 取消已掌握（恢复进错题本），幂等 | STUDENT |
| 4 | POST | `/api/question-favorites` | 收藏题目，幂等 | STUDENT |
| 5 | DELETE | `/api/question-favorites/{questionId}` | 取消收藏，幂等 | STUDENT |
| 6 | GET | `/api/question-favorites/list` | 收藏列表分页 | STUDENT |
| 7 | GET | `/api/question-favorites/{questionId}` | 查询某题是否已收藏 | STUDENT |

命名说明：复数名词 + kebab-case（与 `/api/exam-records` 一致）；"已掌握"建模为错题资源下的子资源 `mastered`，用存在性表达状态。

---

## 4. 错题本接口

### 4.1 分页查询错题本

**GET `/api/wrong-questions/list`**

**聚合口径（重要，前后端一致理解）：**

1. 只统计**本人**在历史考试中 `answer_records.is_correct = 0`（完全错误）的答题记录；
2. `is_correct = 2`（基本正确）**不计入错题本**、不累计错误次数。理由：基本正确通常已获大部分分值，纳入会给复习列表制造噪音；学生想看"基本正确"的 AI 评语可去考试结果页；
3. 同一题多次答错只出一条记录（按 `question_id` 聚合）；
4. `lastWrong*` 三元组取**最近一次 `is_correct = 0` 的答题记录**（注意：不是最近一次作答——若学生后来某次答对了，仍展示最近一次**答错**时的答案）；
5. 排序：按最近一次答错时间倒序（最近错的排最前）；
6. 判卷前的答题记录（`exam_records.status` 为"进行中/已完成"）不参与聚合（此时 `is_correct` 尚为空，天然不满足条件，无需额外过滤）。

**请求参数（Query）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| type | String | 否 | 题型筛选：`CHOICE`（单选）/`JUDGE`（判断）/`TEXT`（简答）/`CODE`（编程）。注意：题库实际存在 CODE 类型，别按旧文档只写三种 |
| categoryId | Long | 否 | 题目分类 ID |
| difficulty | String | 否 | 难度筛选：`EASY` / `MEDIUM` / `HARD` |
| includeMastered | Boolean | 否 | 默认 `false`：只返回未标记已掌握的题（错题本默认视图）；`true` 时额外返回已掌握的题，由每条记录的 `mastered` 字段区分（用于前端"已掌握"分组/筛选） |

**响应 `data`：** Page 结构，`records[]` 元素字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| questionId | Long | 题目 ID |
| mastered | Boolean | 是否已被学生标记"已掌握"（默认列表恒为 false；`includeMastered=true` 时可能为 true） |
| wrongCount | Integer | 该题累计答错次数（仅统计 is_correct=0 的记录） |
| lastWrongAnswer | String | 最近一次答错时学生提交的答案（`answer_records.user_answer`） |
| lastWrongTime | String | 最近一次答错所属考试的考试时间（`exam_records.end_time`，缺失时回退答题记录创建时间），格式 `yyyy-MM-dd HH:mm:ss` |
| lastExamRecordId | Long | 最近一次答错所属的考试记录 ID（前端跳 `/exam/result/{id}` 看整卷解析用） |
| lastPaperId | Long | 最近一次答错所属试卷 ID |
| lastPaperName | String | 最近一次答错所属试卷名（`papers.name`） |
| aiCorrection | String | 最近一次答错对应的 AI 批改评语（`answer_records.ai_correction`；客观题无 AI 评语时为 null） |
| question | Object | 题目对象，字段见 4.4 |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "questionId": 5,
        "mastered": false,
        "wrongCount": 3,
        "lastWrongAnswer": "A",
        "lastWrongTime": "2026-11-10 15:30:00",
        "lastExamRecordId": 102,
        "lastPaperId": 3,
        "lastPaperName": "Java 基础期中卷",
        "aiCorrection": null,
        "question": {
          "id": 5,
          "title": "以下关于 Java 面向对象编程的说法正确的是？",
          "type": "CHOICE",
          "multi": false,
          "difficulty": "MEDIUM",
          "categoryId": 1,
          "categoryName": "Java 基础",
          "analysis": "Java 支持封装、继承、多态三大特性……",
          "choices": [
            { "id": 11, "content": "Java 是面向对象编程语言", "sort": 1, "isCorrect": true },
            { "id": 12, "content": "Java 不支持多继承", "sort": 2, "isCorrect": false }
          ],
          "answer": { "answer": "A", "keywords": null }
        }
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

**错误码：**

| 场景 | 返回 |
| --- | --- |
| 未登录 | HTTP 401 + `{code:401, message:"未登录或登录已过期，请重新登录"}` |
| TEACHER/ADMIN 访问 | HTTP 403 + `{code:403, message:"无权限执行该操作"}` |
| 其他 | HTTP 200 + `{code:500, message}`（GlobalExceptionHandler 兜底） |

**前端调用示例**（axios 风格，与 `src/api/examRecord.js` 一致）：

```js
/** GET /api/wrong-questions/list */
export function getWrongQuestionList(params) {
  return request.get('/api/wrong-questions/list', { params })
}
// 页面里：getWrongQuestionList({ page: 1, size: 10, type: 'CHOICE', categoryId: 1, difficulty: 'MEDIUM', includeMastered: false })
```

**Mock 说明**：全部字段可先 mock。`question.*`、`wrongCount`、`lastWrong*`、`aiCorrection` 均来自现有表，mock 数据可直接按 4.1 示例造；`mastered` 在真实标记表就绪前 mock 为 `false` 即可。

### 4.2 标记已掌握（移出错题本）

**POST `/api/wrong-questions/{questionId}/mastered`**

语义：写入"已掌握标记"。标记后该题从错题本默认列表消失（`includeMastered=true` 时仍可见，`mastered=true`）。

**请求参数（Path）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| questionId | Long | 是 | 题目 ID，示例 `5` |

请求体为空。

**校验规则：**

- 题目不存在（或已逻辑删除）→ `{code:500, message:"题目不存在"}`；
- 该题在本人历史考试中**没有** `is_correct=0` 的记录（即不在错题本中）→ `{code:500, message:"该题不在你的错题本中"}`。理由：语义是"从错题本移出"，只能移出存在的题，同时避免学生给任意题目刷标记制造垃圾数据；
- 已标记过再标记 → 幂等成功（返回 `{code:200, message:"操作成功"}`）。

**响应示例：**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

**错误码：** 见 4.1 错误码表，另加上述两条业务校验（均 HTTP 200 + code 500）。

**前端调用示例：**

```js
/** POST /api/wrong-questions/{questionId}/mastered */
export function markMastered(questionId) {
  return request.post(`/api/wrong-questions/${questionId}/mastered`)
}
```

**Mock 说明**：直接 mock 成功响应即可。

### 4.3 取消已掌握（恢复进错题本）

**DELETE `/api/wrong-questions/{questionId}/mastered`**

语义：删除"已掌握标记"，用于误操作恢复——该题重新出现在错题本默认列表中（题目本身仍有答错历史，聚合口径不变）。

**请求参数（Path）：** 同 4.2。

**校验规则：** 幂等——无标记时调用同样返回成功；不校验该题是否在错题本中（恢复动作无需前提）。

**响应示例：** 同 4.2。

**前端调用示例：**

```js
/** DELETE /api/wrong-questions/{questionId}/mastered */
export function unmarkMastered(questionId) {
  return request.delete(`/api/wrong-questions/${questionId}/mastered`)
}
```

**Mock 说明**：直接 mock 成功响应。

> 补充：4.2 + 4.3 是同一标记的 set / clear，作为对称操作成对提供，成本极低；契约不额外提供"标记已掌握的题列表"独立接口，`includeMastered=true` 的列表查询已覆盖该场景（YAGNI）。

### 4.4 错题本 question 对象字段下发说明（学生角色）

| 字段 | 类型 | 是否下发 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | 题目 ID |
| title | String | 是 | 题干 |
| type | String | 是 | 题型：`CHOICE`/`JUDGE`/`TEXT`/`CODE` |
| multi | Boolean | 是 | 是否多选（仅 CHOICE 有效） |
| difficulty | String | 是 | 难度 `EASY`/`MEDIUM`/`HARD` |
| categoryId | Long | 是 | 分类 ID |
| categoryName | String | 是 | 分类名（后端 join `categories` 得到，省得前端再查分类接口） |
| analysis | String | 是 | **题目解析**（复习核心字段） |
| choices | Array | 是 | 选项列表：`{id, content, sort, isCorrect}`；**`isCorrect` 正常下发**（复习时需要知道正确选项） |
| answer | Object | 是 | `{answer, keywords}`：**正确答案**与**评分关键词**（TEXT/CODE 题的复习依据） |
| paperScore | BigDecimal | 否 | 不下发（错题本不关心分值） |
| category 完整对象 | Object | 否 | 不下发，只给 categoryName |

**与 `QuestionSanitizer.stripAnswer` 的差异及理由（必须向后端实现者交底）：**

- `stripAnswer` 会置空 `answer`、`analysis`、`choices[].isCorrect`。错题本接口**不走**这个脱敏，完整下发上述字段。
- 理由：脱敏规则的目的是**防止考前泄题**（学生提前看到未考题目的答案等于开卷）。而错题本里的题满足两个前提：① 都是**本人**已作答的题；② 都已经过判卷（`is_correct` 已落库），学生本人在考试结果页（`GET /api/exam-records/{id}`，已批阅状态完整返回）本来看得到这些答案与解析。错题本只是把"已判分内容"按题重新聚合，不扩大信息暴露面，不构成泄题通道。
- **边界**：该豁免仅适用于错题本。题目收藏（第 5 节）针对任意题库题目（含从未考过的题），必须维持 `stripAnswer` 脱敏，否则学生会把整个题库收藏一遍来"开卷"，破坏组卷公平性。

---

## 5. 题目收藏接口

### 5.1 收藏题目

**POST `/api/question-favorites`**

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| questionId | Long | 是 | 要收藏的题目 ID，示例 `5` |

**校验规则：**

- `questionId` 为空 → `{code:500, message:"参数错误：questionId 不能为空"}`；
- 题目不存在（或已逻辑删除）→ `{code:500, message:"题目不存在"}`；
- 重复收藏 → **幂等成功**，返回与首次一致的成功响应，不报错（幂等由 `user_id + question_id` 唯一索引兜底）。

**响应示例：**

```json
{ "code": 200, "message": "收藏成功", "data": null }
```

> 前端只判断 `code === 200`，不要依赖 `message` 文案（重复收藏时后端可能返回"收藏成功"或"已收藏"）。

**前端调用示例：**

```js
/** POST /api/question-favorites */
export function addFavorite(questionId) {
  return request.post('/api/question-favorites', { questionId })
}
```

**Mock 说明**：mock 成功响应即可。

### 5.2 取消收藏

**DELETE `/api/question-favorites/{questionId}`**

按题目 ID 取消本人收藏（路径变量是 questionId 而非收藏记录 ID，保证幂等语义直观）。

**校验规则：** 幂等——未收藏时调用同样返回成功。

**响应示例：**

```json
{ "code": 200, "message": "取消收藏成功", "data": null }
```

**前端调用示例：**

```js
/** DELETE /api/question-favorites/{questionId} */
export function removeFavorite(questionId) {
  return request.delete(`/api/question-favorites/${questionId}`)
}
```

**Mock 说明**：mock 成功响应即可。

### 5.3 收藏列表分页

**GET `/api/question-favorites/list`**

**请求参数（Query）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| type | String | 否 | 题型筛选，取值同 4.1 |
| categoryId | Long | 否 | 分类 ID |
| difficulty | String | 否 | 难度筛选，取值同 4.1 |

排序：按收藏时间倒序（最近收藏在前）。

**响应 `data`：** Page 结构，`records[]` 元素字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| questionId | Long | 题目 ID |
| favoriteTime | String | 收藏时间，`yyyy-MM-dd HH:mm:ss` |
| question | Object | 题目对象，字段如下（**脱敏版**） |

**question 对象（脱敏版）字段：**

| 字段 | 类型 | 下发 | 说明 |
| --- | --- | --- | --- |
| id / title / type / multi / difficulty / categoryId / categoryName | 同 4.4 | 是 | 同 4.4 |
| choices | Array | 是 | 仅 `{id, content, sort}`，**`isCorrect` 置 null 不下发** |
| answer / analysis | - | **否** | **不下发**（见下方说明） |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "questionId": 8,
        "favoriteTime": "2026-11-12 10:00:00",
        "question": {
          "id": 8,
          "title": "简述 JVM 垃圾回收的分代假设",
          "type": "TEXT",
          "multi": null,
          "difficulty": "HARD",
          "categoryId": 2,
          "categoryName": "JVM",
          "choices": []
        }
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

**脱敏决策说明（与错题本的差异）：** 收藏列表维持 `QuestionSanitizer.stripAnswer` 等价脱敏，不下发正确答案、解析、关键词、选项对错。理由：收藏动作可作用于**任意题库题目**，包括从未出现在任何考试中的题；若收藏列表下发答案，学生把题库全量收藏一遍即可绕过题库接口的角色脱敏，构成泄题通道。学生要看某道收藏题的解析：若该题曾答错，去错题本看；未考过的题保持谜面（如未来做"练习册/刷题模式"，再在那一组接口上开放答案，不属于本契约范围）。

**错误码：** 同 4.1 错误码表。

**前端调用示例：**

```js
/** GET /api/question-favorites/list */
export function getFavoriteList(params) {
  return request.get('/api/question-favorites/list', { params })
}
```

**Mock 说明**：全部字段可 mock；注意 mock 的 question 对象**不要带** answer/analysis/isCorrect 字段，避免后端就绪后前端误以为有数据。

### 5.4 查询某题是否已收藏

**GET `/api/question-favorites/{questionId}`**

用于题目详情/题目卡片上的收藏按钮初始状态。

**请求参数（Path）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| questionId | Long | 是 | 题目 ID，示例 `5` |

**响应 `data`：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| questionId | Long | 题目 ID（回显入参） |
| favorited | Boolean | 本人是否已收藏 |

**响应示例：**

```json
{ "code": 200, "message": "操作成功", "data": { "questionId": 5, "favorited": true } }
```

**校验规则：** `questionId` 对应题目不存在时**不报错**，正常返回 `favorited: false`（该接口语义是状态查询，不是资源获取，报"题目不存在"会逼前端先查题目再查状态，多一次往返）。

**前端调用示例：**

```js
/** GET /api/question-favorites/{questionId} */
export function getFavoriteStatus(questionId) {
  return request.get(`/api/question-favorites/${questionId}`)
}
```

**Mock 说明**：mock `{ questionId, favorited }` 即可。

---

## 6. 与后端表设计 / 判卷模块的联动约定（给 architect）

本契约不规定具体表结构，只依赖以下**语义**，具体建表由 architect 输出：

1. **"已掌握标记"必须持久化**（派生方案下"移出"没有落点）。建议形如 `wrong_question_marks`：`(user_id, question_id)` 唯一索引，标记存在即已掌握；契约不依赖表名与多余字段。
2. **收藏表**：`(user_id, question_id)` 唯一索引，支撑幂等；逻辑删除字段与现有 `BaseEntity` 约定一致（`is_deleted`）。取消收藏可用逻辑删除或物理删除，契约无感。
3. **判卷联动（以派生判定为准，判卷链不做额外写入）**：
   - "已掌握的题再次答错自动回到错题本"由列表查询按 `mastered_time < 最近一次答错时间` 派生判定回归，`ExamServiceImpl.gradeExam` 落库链路不写 `wrong_question_mastered`、不引入跨表事务；
   - 答对（`is_correct` 为 1/2）**不会**自动把题移出错题本——掌握与否由学生手动确认（答对一次不代表掌握，保持学生主动权）；
   - **边缘行为**：若题目因"标记后又答错"退回待复习（`mastered_time <` 新的答错时间），之后删除这次答错对应的考试记录（`exam_records` 与 `answer_record` 逻辑删除），"最近一次答错时间"会回退到更早的答错记录；当标记时间不早于回退后的答错时间时，题目可能重新出现在"已掌握"列表。这是派生口径的自然结果，不是数据错误。
4. **聚合查询实现提示**：错题列表从 `answer_records`（本人、is_correct=0）按 `question_id` 分组聚合，需关联 `exam_records`（取 endTime/试卷）与 `questions` 系列；请在 `(user_id, question_id)` 或 `answer_records(exam_record_id)` 现有索引基础上评估聚合 SQL，必要时给 `answer_records` 增加 `(user_id, question_id, is_correct)` 组合索引（以 architect 表设计为准）。`answer_records` 本身无 user_id 字段时需经 `exam_records` join 过滤本人。

---

## 7. 前端接入说明

1. **API 层**：新增 `src/api/wrongQuestion.js`（4.1–4.3 的三个函数）与 `src/api/favorite.js`（5.1–5.4 的四个函数），签名见上文各节示例，风格对齐 `src/api/examRecord.js`。
2. **路由**：`src/router/index.js` 用户端布局（`UserLayout`）子路由下新增 `/wrong-questions`（错题本）与 `/favorites`（题目收藏），`meta: { title: '错题本', requiresAuth: true }`；无需 `roles` 配置——接口层已限 STUDENT，学生端页面不存在角色分流。
3. **导航**：`src/layouts/UserLayout.vue` 顶部导航增加"错题本"入口（或先挂在右上角用户下拉菜单，与"我的考试"并列，改动最小）。
4. **状态处理**：`src/utils/request.js` 的响应拦截器已统一处理 `code!==200` 弹错、401 登出跳转、HTTP 403 文案提示，页面组件只需 `catch` 后做静默兜底（如刷新列表失败保持原列表），无需重复处理错误码。

---

## 8. 决策记录（YAGNI 边界，防止后续扯皮）

| 决策 | 结论 | 不做的事 |
| --- | --- | --- |
| 错题本单题详情接口 | 不做。列表已携带全部复习字段；历次错误明细列表暂无需求，出现需求再加 `GET /api/wrong-questions/{questionId}` | — |
| 错题本收录口径 | 仅 `is_correct=0` | 不收录 `is_correct=2`（基本正确） |
| 再次答错与已掌握标记 | 自动清标记重新进场 | 答对不自动移出 |
| 教师端错题/收藏查看 | 不做，403 | 不加 userId 查询参数 |
| 收藏列表下发答案 | 不下发（脱敏） | 收藏不作为绕过脱敏的口子 |
| 收藏分组/标签 | 不做 | 收藏夹就一个，需要分组时再议 |
| 取消已掌握接口 | 提供（对称操作，防误触无解） | — |
