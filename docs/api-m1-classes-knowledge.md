# 接口契约：M1 班级与成员管理 + 知识点管理

> 状态：设计稿（待实现）
> 适用端：exam-system-server（Spring Boot 3 + MyBatis-Plus）、exam-system-web（Vue3 + Pinia + Element Plus）
> 规格：`docs/specs/m1-classes-knowledge.md`（本契约支撑其 10 条验收标准，对照表见第 14 节）
> 面向读者：前后端开发、architect（表与 `ClassScopeService` 联动见第 12 节）

---

## 1. 功能定位

| 功能块 | 说明 | 新增表 / 服务 |
| --- | --- | --- |
| 班级管理 | 管理员建班（名称、培训方向、起止时间）、启用/禁用、删除；教师查看自己负责的班级 | `classes` |
| 成员管理 | Excel 批量导入学员（已存在用户直接入班，不存在则自动建号）、手动添加/移除、成员分页查询 | `class_members` |
| 教师分配 | 一班可多师；分配/移除；教师数据范围由此表判定 | `class_members(role_in_class='TEACHER')` |
| 账号管理 | 重置密码为默认值 `123456`、禁用/启用（嵌在成员管理场景，不做独立账号页） | 复用 `users` |
| 我的班级 | 学员端：所属班级列表（信息+教师+状态）、同学列表（不暴露敏感信息） | 读 `class_members` |
| 知识点 | 平铺列表 CRUD + 排序；管理页 + 题目编辑页选择器 | `knowledge_points` |
| 题目标注 | 给题目设置知识点集合（整体替换）、按知识点筛选题目、详情回显标注 | `question_knowledge_points` |

本模块是 M1 的**数据范围基建**：`ClassScopeService`（给定 userId → 教师负责班级集合 / 学员所属班级集合）随本契约落地，供 M2/M3 复用（见第 12 节）。

---

## 2. 通用约定（与现有接口对齐）

### 2.1 认证、角色与数据范围图例

- 所有接口需携带请求头 `Authorization: Bearer <token>`；本契约所有路径前缀（`/api/classes`、`/api/my`、`/api/knowledge-points`）**不加入** `AuthInterceptor` 公开白名单。
- 角色控制沿用 `@RequireRole` + `AuthInterceptor`（403）；**班级维度数据范围**由 `ClassScopeService` 在 Controller/Service 内判定。
- **教师数据范围的语义（全文统一）**：
  1. 教师负责的班级集合 = `class_members` 中 `user_id = 当前用户` 且 `role_in_class = 'TEACHER'` 且班级未逻辑删除的行——**以成员表为准，与 `users.role` 无关**；
  2. 分配教师接口会校验目标用户 `users.role = 'TEACHER'`，保证常态下两者一致；
  3. "TEACHER-own-class" 的含义：教师请求**列表类**接口时自动按负责班级过滤（返回子集，不报错）；请求**单资源**（详情/成员/操作）时若班级不在负责集合内 → HTTP 403；
  4. 教师可见的学员 = 负责班级中 `role_in_class = 'STUDENT'` 的成员。
- 数据范围标注图例（第 3 节总览表使用）：

| 标注 | 含义 |
| --- | --- |
| ADMIN | 仅管理员（`@RequireRole("ADMIN")`） |
| ADMIN / TEACHER-own | 管理员全量；教师仅负责班级（见上） |
| STUDENT-own | 学员仅本人所属班级 |
| TEACHER / ADMIN | 教师与管理员均可，无班级范围维度（知识点是全局资源） |
| 登录即可 | 任意已登录角色 |

### 2.2 统一响应结构

与现有 `Result<T>` 完全一致：

```json
{ "code": 200, "message": "操作成功", "data": { } }
```

- 成功：`code = 200`；业务失败：HTTP 200 + `code = 500`（`GlobalExceptionHandler` 兜底），不为本模块新引入业务 4xx。
- **新增一种异常映射**：班级数据范围越权（教师访问非负责班级的单资源）由专用异常 `ClassScopeException` 抛出，`GlobalExceptionHandler` 映射为 **HTTP 403 + `{code:403, message:"无权访问该班级"}`**。理由：规格明确"后端 403/过滤，不是前端隐藏"，且越权与普通业务失败（参数错、资源不存在）语义不同；文案与 `AuthInterceptor` 的 403 风格一致。

### 2.3 分页

与 `/api/questions/list`、`/api/exam-records/list` 一致：

- 请求参数：`page`（默认 1）、`size`（默认 10）。
- 响应 `data` 为 MyBatis-Plus `Page` 序列化结果（`records/total/size/current/pages`，其余框架字段前端忽略）。

### 2.4 错误码语义（全模块统一）

| HTTP | body code | 触发条件 | 前端处理 |
| --- | --- | --- | --- |
| 401 | 401 | 未登录或 token 过期（`AuthInterceptor`） | `request.js` 拦截器已有处理，本模块无需额外处理 |
| 403 | 403 | ① 角色不足（`@RequireRole` 拦截）；② 教师访问非负责班级的单资源（`ClassScopeException`） | 提示"无权访问该班级 / 无权限执行该操作" |
| 200 | 500 | 业务失败（班级不存在、名称重复、Excel 行错误、知识点被引用等），`message` 为用户可读文案 | `request.js` 已统一 `ElMessage.error(message)` 弹出 |

### 2.5 其他约定

- **时间字段**：日期（起止时间）`yyyy-MM-dd` 字符串；时间戳（joinTime、lastExamTime、createTime）`yyyy-MM-dd HH:mm:ss` 字符串。
- **枚举值**：班级状态 `status`：`ENABLED` / `DISABLED`；账号状态沿用 `users.status`：`ACTIVE` / `INACTIVE`；班内角色 `roleInClass`：`STUDENT` / `TEACHER`。
- **幂等**：成员添加、教师分配/移除、标注替换、重置密码等重复调用均返回成功，不产生重复数据（依赖 `class_members(class_id, user_id, role_in_class)`、`question_knowledge_points(question_id, knowledge_point_id)` 唯一索引兜底）。
- **筛选参数非法值**：`status`、`roleInClass`、`knowledgePointId` 传枚举外/非数字值时忽略该筛选条件（与 `/api/exam-records/list` 对非法 `status` 的处理一致）。
- 后端实现按现有风格补 Swagger 注解，Knife4j 分组名："班级管理"、"知识点管理"。

---

## 3. 接口总览

| # | 方法 | 路径 | 说明 | 数据范围 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/api/classes/list` | 班级分页列表 | ADMIN / TEACHER-own |
| 2 | POST | `/api/classes` | 创建班级 | ADMIN |
| 3 | PUT | `/api/classes/{id}` | 更新班级 | ADMIN |
| 4 | PUT | `/api/classes/{id}/status` | 启用/禁用班级 | ADMIN |
| 5 | GET | `/api/classes/{id}` | 班级详情（含教师与计数） | ADMIN / TEACHER-own |
| 6 | DELETE | `/api/classes/{id}` | 删除班级（逻辑删除） | ADMIN |
| 7 | GET | `/api/classes/{id}/members` | 成员分页列表（可选考试摘要） | ADMIN / TEACHER-own |
| 8 | POST | `/api/classes/{id}/members` | 手动添加学员（已存在用户） | ADMIN |
| 9 | POST | `/api/classes/{id}/members/import` | Excel 批量导入学员 | ADMIN |
| 10 | GET | `/api/classes/members/import-template` | 下载学员导入模板 | ADMIN |
| 11 | DELETE | `/api/classes/{id}/members/{userId}` | 移除学员 | ADMIN |
| 12 | POST | `/api/classes/{id}/teachers` | 分配教师（可批量） | ADMIN |
| 13 | DELETE | `/api/classes/{id}/teachers/{userId}` | 移除教师 | ADMIN |
| 14 | PUT | `/api/user/{id}/password/reset` | 重置密码为默认值 | ADMIN |
| 15 | PUT | `/api/user/{id}/status` | 禁用/启用账号 | ADMIN |
| 16 | GET | `/api/my/classes` | 我的班级列表 | STUDENT |
| 17 | GET | `/api/my/classes/{classId}/classmates` | 我的同学列表 | STUDENT-own |
| 18 | GET | `/api/knowledge-points/list` | 知识点分页列表（管理页） | TEACHER / ADMIN |
| 19 | GET | `/api/knowledge-points/all` | 知识点全量列表（选择器） | TEACHER / ADMIN |
| 20 | POST | `/api/knowledge-points` | 创建知识点 | TEACHER / ADMIN |
| 21 | PUT | `/api/knowledge-points/{id}` | 更新知识点 | TEACHER / ADMIN |
| 22 | DELETE | `/api/knowledge-points/{id}` | 删除知识点（被引用时禁止） | TEACHER / ADMIN |
| 23 | POST | `/api/knowledge-points/sort` | 批量排序 | TEACHER / ADMIN |
| 24 | PUT | `/api/questions/{id}/knowledge-points` | 给题目整体替换知识点集合 | TEACHER / ADMIN |

命名说明：复数名词 + kebab-case（与 `/api/exam-records` 一致）；成员/教师建模为班级子资源；学员个人视图用 `/api/my/` 前缀与资源 ID 从 token 解析（路径不出现 userId）；账号操作挂既有 `/api/user` 前缀（`UserController` 现有 `@RequestMapping("/api/user")`，不新开 `/api/users` 风格）。

---

## 4. 班级接口

### 4.1 班级分页列表

**GET `/api/classes/list`**（ADMIN 全量 / TEACHER 仅负责的，接口自动过滤）

**请求参数（Query）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page / size | Integer | 否 | 分页，默认 1 / 10；页码限制 1–10000，每页 1–100 条，超界自动收敛 |
| name | String | 否 | 班级名称模糊筛选 |
| status | String | 否 | `ENABLED` / `DISABLED`，非法值忽略 |

**数据范围：** ADMIN 返回全部；TEACHER 由 `ClassScopeService` 过滤为负责班级集合（含已禁用的负责班级，由 `status` 字段区分）。STUDENT 调用 → 403（学生走 `/api/my/classes`）。

**响应 `data`：** Page 结构，`records[]` 元素：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 班级 ID |
| name | String | 班级名称 |
| direction | String | 培训方向 |
| startDate / endDate | String | 起/止日期 `yyyy-MM-dd` |
| status | String | `ENABLED` / `DISABLED` |
| studentCount | Integer | 学员数（role_in_class=STUDENT） |
| teacherCount | Integer | 教师数 |
| createTime | String | 创建时间 |

**响应示例：**

```json
{
  "code": 200, "message": "操作成功",
  "data": {
    "records": [
      { "id": 1, "name": "Java 培训一班", "direction": "Java 后端", "startDate": "2026-12-01",
        "endDate": "2027-03-01", "status": "ENABLED", "studentCount": 3, "teacherCount": 1,
        "createTime": "2026-11-21 10:00:00" }
    ],
    "total": 1, "size": 10, "current": 1, "pages": 1
  }
}
```

**前端调用示例：**

```js
/** GET /api/classes/list */
export function getClassList(params) {
  return request.get('/api/classes/list', { params })
}
```

**Mock 说明：** 全部字段可先 mock；教师视角 mock 时只造自己负责的班级。

### 4.2 创建班级

**POST `/api/classes`**（ADMIN）

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 班级名称，全局唯一，≤ 50 字符 |
| direction | String | 否 | 培训方向，如"Java 后端" |
| startDate / endDate | String | 否 | 起/止日期 `yyyy-MM-dd`；两者都传时要求 `endDate >= startDate` |

> 规格字段即"名称、方向、起止时间、状态"，状态创建时固定 `ENABLED`；不设 description 等额外字段（决策表 #14）。

**校验规则：** 名称非空且未与未删除班级重名（重复 → `{code:500, message:"班级名称已存在"}`）；日期顺序校验；其余非法 → 参数错误文案。

**响应 `data`：** 创建后的班级完整对象（同 4.1 records 元素，`studentCount/teacherCount` 为 0），前端拿 `data.id` 跳详情页。

**响应示例：**

```json
{ "code": 200, "message": "创建成功", "data": { "id": 2, "name": "Java 培训二班", "direction": "Java 后端", "startDate": null, "endDate": null, "status": "ENABLED", "studentCount": 0, "teacherCount": 0, "createTime": "2026-11-21 11:00:00" } }
```

### 4.3 更新班级

**PUT `/api/classes/{id}`**（ADMIN）

请求体同 4.2（`name` 必填；唯一性校验排除自身）。更新为完整编辑表单语义：`direction` 传空字符串可清空；`startDate`/`endDate` 传 `null` 可清空可选日期。班级不存在或已删除 → `{code:500, message:"班级不存在"}`。响应 `data` 为更新后的完整对象（同 4.2）。

### 4.4 启用/禁用班级

**PUT `/api/classes/{id}/status`**（ADMIN）

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| status | String | 是 | `ENABLED` / `DISABLED` |

语义：禁用**只影响班级可见性标记**——学员"我的班级"页仍显示该班并标记"已停用"（验收 4）；M1 不因禁用改变考试/课程可见性（M3 收敛）。幂等：重复设置同值返回成功。

**响应示例：** `{ "code": 200, "message": "操作成功", "data": null }`

### 4.5 班级详情

**GET `/api/classes/{id}`**（ADMIN / TEACHER-own，非负责班级 → 403）

**响应 `data`：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id / name / direction / startDate / endDate / status / createTime | 同 4.1 | 班级本体 |
| studentCount / teacherCount | Integer | 成员计数 |
| teachers | Array | 教师列表：`{userId, username, realName}` |

**响应示例：**

```json
{
  "code": 200, "message": "操作成功",
  "data": {
    "id": 1, "name": "Java 培训一班", "direction": "Java 后端", "startDate": "2026-12-01",
    "endDate": "2027-03-01", "status": "ENABLED", "createTime": "2026-11-21 10:00:00",
    "studentCount": 3, "teacherCount": 1,
    "teachers": [{ "userId": 10, "username": "teacher1", "realName": "王老师" }]
  }
}
```

> 成员**不在详情响应内**：成员走 4.7 分页子资源（班级人数可能上百，详情响应不膨胀；详情页成员区直接调分页接口）。决策表 #2。

**验收 3 对照：** teacher2 访问非负责班级详情 → HTTP 403；班级列表中不出现该班（过滤）。

### 4.6 删除班级

**DELETE `/api/classes/{id}`**（ADMIN）

逻辑删除（`is_deleted`）。**成员/教师关系不级联清理**（班级不可见即失效，`users` 账号不受影响）；前端删除前二次确认即可。班级不存在 → `{code:500, message:"班级不存在"}`。响应 `data: null`。

---

## 5. 成员接口

### 5.1 成员分页列表

**GET `/api/classes/{id}/members`**（ADMIN / TEACHER-own，非负责班级 → 403）

**请求参数（Query）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page / size | Integer | 否 | 分页，默认 1 / 10；页码限制 1–10000，每页 1–100 条，超界自动收敛 |
| roleInClass | String | 否 | `STUDENT` / `TEACHER`，不传返回全部（含教师行） |
| keyword | String | 否 | 用户名/姓名模糊匹配 |
| includeSummary | Boolean | 否 | 默认 `false`；`true` 时每条附考试摘要（见下） |

**响应 `data`：** Page 结构，`records[]` 元素：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userId | Long | 用户 ID（移除/重置密码/禁用操作的定位键） |
| username | String | 登录名 |
| realName | String | 姓名 |
| role | String | 全局角色（`users.role`，正常为 STUDENT） |
| accountStatus | String | 账号状态 `ACTIVE` / `INACTIVE`（前端渲染"禁用"标签与启用按钮） |
| roleInClass | String | 班内角色 |
| joinedAt | String | 入班时间 |
| examCount | Integer | 仅 `includeSummary=true`：该学员历史考试记录总数（M3 前考试未关联班级，是**本人全量**口径，不做班级内过滤） |
| lastExamTime | String | 仅 `includeSummary=true`：最近一次考试时间，无记录为 null |

**实现提示（给后端）：** `includeSummary=true` 时对当页 userId 批量聚合 `exam_records`（一次 group by 查询），禁止循环 N+1。

**响应示例：**

```json
{
  "code": 200, "message": "操作成功",
  "data": {
    "records": [
      { "userId": 21, "username": "student1", "realName": "张三", "role": "STUDENT",
        "accountStatus": "ACTIVE", "roleInClass": "STUDENT", "joinedAt": "2026-11-21 10:05:00",
        "examCount": 2, "lastExamTime": "2026-11-20 15:30:00" }
    ],
    "total": 3, "size": 10, "current": 1, "pages": 1
  }
}
```

**Mock 说明：** 全部字段可先 mock；`examCount/lastExamTime` 在真实聚合就绪前 mock 固定值即可。

### 5.2 手动添加学员

**POST `/api/classes/{id}/members`**（ADMIN）

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | Long | 是 | **已存在用户**的 ID（不含自动建号，自动建号只在 Excel 导入里做） |

**校验规则：**

- 班级不存在/已删除 → `{code:500, message:"班级不存在"}`；
- 用户不存在 → `{code:500, message:"用户不存在"}`；
- 用户全局角色不是 `STUDENT` → 拒绝添加，不改写用户角色；
- 已在班内（任一 roleInClass）→ **幂等成功**，`alreadyInClass: true`，不重复入班；
- 禁用账号（INACTIVE）允许入班（可登录后再启用，M1 不加限制）。

**响应 `data`：**

```json
{ "code": 200, "message": "添加成功", "data": { "userId": 21, "username": "student1", "realName": "张三", "alreadyInClass": false } }
```

### 5.3 Excel 批量导入学员

**POST `/api/classes/{id}/members/import`**（ADMIN），`multipart/form-data`，参数 `file`（.xlsx/.xls）。参照 `QuestionBatchController` 的导入模式（MultipartFile + ExcelUtil）。

**Excel 格式（与模板一致）：** 列 A=用户名（必填）、列 B=姓名（选填，缺省用用户名）；第 1 行表头，数据行从第 2 行起。

**处理规则（规格默认决策）：**

1. 用户名已存在（不论全局角色）→ 直接以 `role_in_class='STUDENT'` 入班，**不修改**其 `users.role`；
2. 用户名不存在 → 自动创建账号：默认密码 `123456`、角色 `STUDENT`、姓名取 Excel（验收 2），随后入班；
3. 已在班内的行 → 幂等成功，**不重复入班**（`joinedCount` 不计）；
4. 行级失败（用户名为空、含非法字符等）→ 不中断整批，计入 `failedRows`；
5. 单文件数据行上限 500，超出整单拒绝并提示分批导入。

**响应 `data`（导入结果）：**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| totalRows | Integer | Excel 数据行数（不含表头） |
| successRows | Integer | 成功行数（含幂等跳过行） |
| createdAccounts | Integer | 本次新建账号数 |
| joinedCount | Integer | 本次实际新增入班人次 |
| failedRows | Array | `{row, username, reason}`，row 为 Excel 行号 |

**响应示例（对应验收 1：1 个已存在 student1 + 2 个新建）：**

```json
{
  "code": 200, "message": "导入完成",
  "data": {
    "totalRows": 3, "successRows": 3, "createdAccounts": 2, "joinedCount": 3, "failedRows": []
  }
}
```

**错误码：** 文件为空/格式不符 → `{code:500, message:"请上传 .xlsx 或 .xls 文件"}`；超 500 行 → `{code:500, message:"单次最多导入 500 行，请分批导入"}`；班级不存在 → "班级不存在"。

**前端调用示例：**

```js
/** POST /api/classes/{id}/members/import */
export function importMembers(classId, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/api/classes/${classId}/members/import`, fd)
}
```

**Mock 说明：** 直接按上例造结果；`failedRows` 造 1 条用于验证失败行渲染。

### 5.4 下载学员导入模板

**GET `/api/classes/members/import-template`**（ADMIN）

返回 Excel 文件流（`Content-Disposition: attachment; filename=student_template.xlsx`），两列表头：`用户名`、`姓名`。实现对齐 `ExcelUtil.generateTemplate()` 现有模式；前端 `<a>` 直链下载即可，**前端无需 mock**（可先造静态 xlsx 顶替）。

### 5.5 移除学员

**DELETE `/api/classes/{id}/members/{userId}`**（ADMIN）

仅删除该用户在本班的 `role_in_class='STUDENT'` 记录；教师关系的移除走 6.2（角色分离，决策表 #9）。幂等：无该记录时同样返回成功。响应 `data: null`。

---

## 6. 教师接口

### 6.1 分配教师（可批量）

**POST `/api/classes/{id}/teachers`**（ADMIN）

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userIds | Long[] | 是 | 教师 ID 列表（一班可多师），非空且去重 |

**校验规则：**

- 班级不存在 → "班级不存在"；
- 任一 userId 对应用户不存在或 `users.role != 'TEACHER'` → **整批失败**：`{code:500, message:"用户 X 不是教师角色"}`（明确报出问题 ID，避免部分成功造成理解成本）；
- 已是本班教师的 ID → 幂等跳过，不计入 `addedCount`。

**响应 `data`：**

```json
{ "code": 200, "message": "分配成功", "data": { "addedCount": 1, "teacherIds": [10] } }
```

### 6.2 移除教师

**DELETE `/api/classes/{id}/teachers/{userId}`**（ADMIN）

仅删除 `role_in_class='TEACHER'` 记录；幂等（无该记录返回成功）。响应 `data: null`。

---

## 7. 账号接口（管理端，嵌在成员管理场景）

### 7.1 重置密码为默认值

**PUT `/api/user/{id}/password/reset`**（ADMIN）

无请求体。将目标账号密码重置为默认值 **`123456`**（验收 5）。

**校验规则：**

- 目标用户不存在 → `{code:500, message:"用户不存在"}`；
- 目标是 ADMIN 账号（含管理员重置自己）→ `{code:500, message:"管理员账号不支持重置"}`（防自锁；决策表 #6）；
- 幂等：重复重置返回成功。

**响应 `data`：**

```json
{ "code": 200, "message": "密码已重置", "data": { "userId": 22, "defaultPassword": "123456" } }
```

> 前端展示默认密码用 `data.defaultPassword`，不要解析 `message` 文案。

### 7.2 禁用/启用账号

**PUT `/api/user/{id}/status`**（ADMIN）

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| enabled | Boolean | 是 | `true` → `ACTIVE`；`false` → `INACTIVE` |

**校验规则：**

- 目标不存在 → "用户不存在"；目标是 ADMIN（含自己）→ `{code:500, message:"管理员账号不支持禁用"}`；
- 禁用后该账号**登录必须失败**：既有 `/api/user/login` 需补充 `status` 校验（见第 11 节）；
- 幂等：重复设置同值返回成功；已发 token 不强制失效（决策表 #7）。

**响应示例：** `{ "code": 200, "message": "操作成功", "data": { "userId": 22, "status": "INACTIVE" } }`

---

## 8. 学员接口

### 8.1 我的班级列表

**GET `/api/my/classes`**（`@RequireRole({"STUDENT"})`）

归属从 token 解析（`request.getAttribute("userId")`），路径无 userId 参数。返回学员所属的**全部班级**（含已停用，由 `status` 区分——验收 4），不分页（决策表 #10）。

**响应 `data`：** 数组，元素：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| classId | Long | 班级 ID（进同学列表用） |
| name / direction / startDate / endDate / status | 同 4.1 | 班级信息 |
| joinedAt | String | 本人入班时间 |
| teachers | Array | `{userId, realName}`（不下发 username，学员端最小暴露） |
| studentCount | Integer | 班级学员数 |

**响应示例：**

```json
{
  "code": 200, "message": "操作成功",
  "data": [
    { "classId": 1, "name": "Java 培训一班", "direction": "Java 后端",
      "startDate": "2026-12-01", "endDate": "2027-03-01", "status": "ENABLED",
      "joinedAt": "2026-11-21 10:05:00", "studentCount": 3,
      "teachers": [{ "userId": 10, "realName": "王老师" }] }
  ]
}
```

**Mock 说明：** 全部字段可先 mock；造一条 `status:"DISABLED"` 用于验证停用标记。

### 8.2 我的同学列表

**GET `/api/my/classes/{classId}/classmates`**（STUDENT-own：本人是该班成员（roleInClass=STUDENT）才放行，否则 403）

**响应 `data`：** 数组，元素 `{userId, realName, joinedAt}`——**不下发 username**（登录名半敏感，"不暴露敏感信息"口径，决策表 #11）。全量返回不分页（单班人数规模小）。

**响应示例：**

```json
{ "code": 200, "message": "操作成功", "data": [{ "userId": 21, "realName": "张三", "joinedAt": "2026-11-21 10:05:00" }] }
```

**错误码：** 非本人所属班级 → HTTP 403；班级不存在 → `{code:500, message:"班级不存在"}`。

---

## 9. 知识点接口

知识点是**全局资源**（不挂班级），读写均为 TEACHER / ADMIN；学生端 M1 不提供知识点接口（M4 诊断时再议）。

### 9.1 分页列表（管理页）

**GET `/api/knowledge-points/list`**（TEACHER / ADMIN）

**请求参数（Query）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page / size | Integer | 否 | 分页，默认 1 / 10；页码限制 1–10000，每页 1–100 条，超界自动收敛 |
| name | String | 否 | 名称模糊筛选 |

**排序：** `sort` 升序、`id` 升序（与选择器顺序一致）。

**响应 `data`：** Page 结构，`records[]` 元素：`{id, name, description, sort, questionCount, createTime}`；`questionCount` 为引用该知识点的题目数（删除确认与空态提示用）。

**响应示例：**

```json
{
  "code": 200, "message": "操作成功",
  "data": {
    "records": [
      { "id": 1, "name": "面向对象", "description": "封装/继承/多态", "sort": 1, "questionCount": 12, "createTime": "2026-11-21 09:00:00" }
    ],
    "total": 3, "size": 10, "current": 1, "pages": 1
  }
}
```

### 9.2 全量列表（题目编辑页选择器）

**GET `/api/knowledge-points/all`**（TEACHER / ADMIN）

不分页，按 `sort` 升序返回 `data: [{id, name, sort}]`。与 9.1 并存的理由：编辑页多选需要完整有序集合，管理页需要分页与引用计数（决策表 #12）。

**响应示例：**

```json
{ "code": 200, "message": "操作成功", "data": [{ "id": 1, "name": "面向对象", "sort": 1 }] }
```

### 9.3 创建 / 更新

**POST `/api/knowledge-points`**、**PUT `/api/knowledge-points/{id}`**（TEACHER / ADMIN）

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 名称，全局唯一（重复 → `{code:500, message:"知识点名称已存在"}`），≤ 50 字符 |
| description | String | 否 | 说明 |
| sort | Integer | 否 | 排序号，缺省追加到末尾（取当前 max+1） |

**校验规则：** 更新时目标不存在 → `{code:500, message:"知识点不存在"}`；唯一性排除自身。

**响应 `data`：** 创建/更新后的完整对象（同 9.1 records 元素；新建 `questionCount: 0`）。

### 9.4 删除

**DELETE `/api/knowledge-points/{id}`**（TEACHER / ADMIN）

**校验规则：** 不存在 → "知识点不存在"；**已被题目引用**（`question_knowledge_points` 有记录）→ `{code:500, message:"该知识点已被 N 道题引用，请先移除标注"}`（不做级联清理，决策表 #13）。

**响应示例：** `{ "code": 200, "message": "删除成功", "data": null }`

### 9.5 批量排序

**POST `/api/knowledge-points/sort`**（TEACHER / ADMIN）

管理页上移/下移仅交换当前页相邻项的不同排序号，并一次性提交。相邻项排序号相同或位于当前页边界时，禁用对应按钮；同排序号可通过编辑更改。

**请求体（JSON）：** `[ { "id": 1, "sort": 1 }, { "id": 2, "sort": 2 } ]`

**校验规则：** 列表非空，每项必须包含非空的 `id` 和 `sort`；格式非法时在查询前拒绝。任一 `id` 不存在 → `{code:500, message:"知识点不存在"}`（整批不写入）。

**响应示例：** `{ "code": 200, "message": "排序已保存", "data": null }`

---

## 10. 题目-知识点标注

### 10.1 设置题目知识点（整体替换）

**PUT `/api/questions/{id}/knowledge-points`**（TEACHER / ADMIN）

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| knowledgePointIds | Long[] | 是 | 目标知识点集合；**整体替换**：不在集合中的既有标注删除、新的插入；空数组 = 清空全部标注 |

**校验规则：**

- 题目不存在/已逻辑删除 → `{code:500, message:"题目不存在"}`；
- 任一知识点不存在 → `{code:500, message:"知识点不存在"}`（整批不生效）；
- 幂等：集合与现状一致时返回成功，无副作用。

**响应 `data`：**

```json
{ "code": 200, "message": "标注已保存", "data": { "questionId": 5, "knowledgePoints": [{ "id": 1, "name": "面向对象" }, { "id": 3, "name": "集合框架" }] } }
```

**前端调用示例：**

```js
/** PUT /api/questions/{id}/knowledge-points */
export function setQuestionKnowledgePoints(questionId, knowledgePointIds) {
  return request.put(`/api/questions/${questionId}/knowledge-points`, { knowledgePointIds })
}
```

**Mock 说明：** mock `{questionId, knowledgePoints}` 回显即可。

### 10.2 按知识点筛选题目——结论：扩展现有 `/api/questions/list`

**结论：扩展现有 `GET /api/questions/list`，新增可选参数 `knowledgePointId`，不新建独立筛选接口。**

理由：该接口已有 `categoryId/type/difficulty/keyword` 同类筛选参数，知识点筛选是同维扩展；为同一资源另立 `/api/questions/by-knowledge` 会引入第二种筛选风格，违反"一次不引入超过一种新风格"，且前端筛选表单要维护两套请求。

**参数：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| knowledgePointId | Long | 否 | 只返回标注了该知识点的题目；非数字/不存在时忽略该条件（与其他筛选一致） |

其余参数与响应结构完全不变；`records[]` 元素新增 `knowledgePoints` 字段（见 10.3）。该参数对所有登录角色生效（接口本身未限角色），但 M1 仅管理端提供筛选入口。

### 10.3 题目详情/列表返回已标注知识点

- **`GET /api/questions/{id}`**（详情）：响应增加 `knowledgePoints: [{id, name, sort}]` 数组（编辑页回显标注的数据源）；对所有角色下发（知识点标注不含答案信息，不影响脱敏口径）。
- **`GET /api/questions/list`**（列表）：`records[]` 元素同步增加 `knowledgePoints`（管理页列表显示知识点标签列）。实现提示：按当页 questionId 批量查询标注表再组装，禁止循环 N+1。

**响应示例（详情中的增量部分）：**

```json
"knowledgePoints": [{ "id": 1, "name": "面向对象", "sort": 1 }]
```

### 10.4 创建/更新题目时携带标注（建议性扩展）

建议在既有 **POST `/api/questions`** 与 **PUT `/api/questions/{id}`** 请求体中增加可选字段 `knowledgePointIds: Long[]`（语义同 10.1 整体替换），后端随题目写入透传到标注表。前端不强依赖：创建后追加调用 10.1 的 PUT 同样成立；后端不做也不影响（决策表 #8）。

---

## 11. 既有接口接入数据范围（M1 范围清单）

以下为**行为变更**，均不改响应结构，前端无感（按规格默认决策，用户 review 可推翻）：

| 接口 | M1 变更 | 验收对照 |
| --- | --- | --- |
| `GET /api/exam-records/list` | TEACHER 角色强制按负责班级学员过滤（`ClassScopeService` 取教师班级 → STUDENT 成员 userId 集合 → `user_id IN (...)`）；ADMIN 全量、STUDENT 本人逻辑不变。历史记录 `user_id` 为空的行教师不可见（ADMIN 可见）。页码限制 1–10000、每页 1–100 条，超界自动收敛 | 验收 6 |
| `GET/DELETE /api/exam-records/{id}` | TEACHER 仅可读取/删除 `user_id` 属于负责班级学员的记录；空范围或历史空 `user_id` 不授权；ADMIN 既有行为不变，STUDENT 本人校验不变 | 验收 6 |
| `GET /api/exam-records/stats` | TEACHER 在 `paperId` 外叠加负责班级学员 `user_id` 范围；空范围返回零统计；ADMIN 仍按 `paperId` 全量聚合 | 验收 6 |
| `GET /api/exam-records/ranking` | 需登录；TEACHER 限负责班级学员，STUDENT 限本人所属班级成员并包含本人，ADMIN 全量；教师空范围返回空列表，无班学员仅可见本人 | 验收 6 |
| `POST /api/exams/{examRecordId}/grade` | TEACHER 只能触发负责班级学员记录判卷；STUDENT 仍仅本人，ADMIN 全量 | 验收 6 |
| `GET /api/stats/overview` | TEACHER 只统计负责班级学员数与考试记录；共享题目/分类/试卷计数保持全机构；空范围学员/考试指标为零。ADMIN 全量 | 验收 8 |
| `GET /api/stats/test` | 公开检查仅返回健康状态或通用不可用消息；不返回机构统计或内部异常详情 | 安全检查 |
| `/api/user/login` | 补充账号状态校验：`INACTIVE` 登录失败，返回 `{code:500, message:"账号已被禁用"}`（业务失败风格，不新增 403） | 验收 5 的前置 |
| 题目管理列表教师视角脱敏 | **维持现状**（不在本契约范围内） | — |
| 题目/试卷管理列表（教师视角） | 维持机构内共享资源口径，不按班级过滤；不存在可用于 M1 的班级资源归属 | 共享资源规则 |

> `course_classes`（班级-课程关系）M1 只建表，无接口；课程-班级分配接口属 M2。

---

## 12. 与后端表设计 / ClassScopeService 的联动约定（给 architect）

1. **`classes`**：`id, name（唯一索引）, direction, start_date, end_date, status(ENABLED/DISABLED)` + `BaseEntity`（is_deleted 等）；逻辑删除。
2. **`class_members`**：`(class_id, user_id, role_in_class)` 唯一索引（一人一班一角色）；`role_in_class ∈ {STUDENT, TEACHER}`；`joined_at`；一人可入多班。
3. **`knowledge_points`**：`name（唯一索引）, description, sort`；逻辑删除。**`question_knowledge_points`**：`(question_id, knowledge_point_id)` 唯一索引；建议同时给 `knowledge_point_id` 单列索引支撑引用计数与筛选。
4. **`ClassScopeService`**（本阶段核心基建，M2/M3 复用）：
   - `List<Long> getTeacherClassIds(Long userId)`：`class_members` 中 `role_in_class='TEACHER'` 且班级未删除；
   - `List<Long> getStudentClassIds(Long userId)`：同上 `role_in_class='STUDENT'`；
   - 建议配套 `Set<Long> getTeacherVisibleStudentIds(Long userId)`（负责班级 STUDENT 成员 ∪ 集合），`/api/exam-records/list` 与 `/api/stats/overview` 直接消费。
5. **越权异常**：新增 `ClassScopeException`（携带文案"无权访问该班级"），`GlobalExceptionHandler` 映射 HTTP 403；不要用 `RuntimeException("无权...")`（会被兜底成 code 500，丢失 403 语义）。
6. **Excel**：学员模板/解析复用 `ExcelUtil`，新增 `StudentImportVo {username, realName}` 与行级错误收集（区别于题目导入的整批字符串结果，见决策表 #4）。

---

## 13. 前端接入说明

1. **API 层**：新增 `src/api/classManage.js`（4–7 节班级/成员/教师/账号函数）、`src/api/knowledgePoint.js`（9–10 节）、`src/api/myClass.js`（8 节），风格对齐 `src/api/examRecord.js`。
2. **管理端路由**（`/admin` 子路由，继承 `AdminLayout` 的 `meta: { roles: ['ADMIN','TEACHER'] }`）：
   - `/admin/classes`（班级列表）、`/admin/classes/:id`（班级详情 + 成员管理 + 教师分配，重置密码/禁用账号嵌在成员行操作里）、`/admin/knowledge-points`（知识点管理）。
   - 账号操作入口在成员行内，**不新增** `/admin/users` 独立账号页（决策表 #5）。
3. **AdminLayout 导航**：新增 `el-sub-menu index="class-group"`「班级管理」，子项"班级列表"与"知识点"；教师登录看到相同入口，后端数据范围兜底（列表只见负责班级）。
4. **学员端路由**（`UserLayout` 子路由）：`/my/classes`（我的班级）、`/my/classes/:id`（班级详情含同学列表），`meta: { title: '我的班级', requiresAuth: true, roles: ['STUDENT'] }`；`UserLayout` 顶部导航或用户下拉菜单加"我的班级"入口（与"我的考试"并列）。
5. **题库联动**：
   - `QuestionEdit.vue`：新增知识点多选（数据源 `GET /api/knowledge-points/all`）；编辑态回显用详情接口的 `knowledgePoints`；保存时把 `knowledgePointIds` 放进题目保存请求体（10.4），或保存成功后调 10.1 的 PUT。
   - `QuestionList.vue`：筛选区新增"知识点"下拉（同数据源），列表加知识点标签列。
6. **状态处理**：`request.js` 拦截器已统一处理 `code!==200` 弹错、401 登出、403 文案；页面组件只需 `catch` 静默兜底。导入结果用 `data.failedRows` 渲染行级失败表格，不要解析 `message`。

---

## 14. 验收标准对照与决策记录

### 14.1 验收标准 ↔ 契约对照

| 验收标准 | 支撑接口 |
| --- | --- |
| 1. 建班 → Excel 导入 3 学员（1 已存在 + 2 新建）→ 分配 teacher1 → 成员列表三人 | 4.2 / 5.3 / 6.1 / 5.1（`roleInClass=STUDENT` 过滤后 3 条） |
| 2. 新账号默认密码 `123456` 登录且角色 STUDENT | 5.3 处理规则 2（导入自动建号） |
| 3. teacher1 看到班级及成员；teacher2 无权/空 | 4.1（过滤）/ 4.5（403）/ 5.1 |
| 4. student1 我的班级显示信息与教师；禁用后显示已停用 | 8.1（含 `status`） |
| 5. admin 重置 student2 密码 → `123456` 重登成功 | 7.1 + 第 11 节 login 状态校验 |
| 6. 教师考试记录按负责班级过滤 | 第 11 节 `/api/exam-records/list` |
| 7. 知识点新建 3 个 → 标注 2 个 → 按知识点筛选命中 | 9.3 / 10.1 / 10.2 |
| 8. stats/overview 教师只统计负责班级 | 第 11 节 |
| 9. 增量 SQL / mvn test / npm build | 第 12 节表清单（非契约内容） |
| 10. 既有功能回归 | 契约未触碰错题本/收藏/判卷链路 |

### 14.2 决策记录（YAGNI 边界，防止后续扯皮）

| # | 决策 | 结论 | 不做的事 |
| --- | --- | --- | --- |
| 1 | 按知识点筛选题目 | 扩展既有 `GET /api/questions/list` 加 `knowledgePointId` | 不新建独立筛选接口 |
| 2 | 班级详情不含成员全量 | 详情带 teachers + 计数；成员走分页子资源 | 详情响应不随人数膨胀 |
| 3 | 成员考试摘要 | `includeSummary=true` 才下发，当页批量聚合 | 不做独立"成员学习报表"（M6 统计） |
| 4 | Excel 导入 | 一次导入 + 行级 `failedRows`（行结构简单，失败行重导即可） | 不做题目导入那样的 preview-excel 两段式 |
| 5 | 账号管理形态 | 重置/禁用嵌在成员行操作 | 不做独立账号分页列表（需要时另立契约） |
| 6 | ADMIN 账号保护 | 重置/禁用不得作用于 ADMIN（含自操作） | 不做管理员自助改密流程 |
| 7 | 禁用账号已发 token | 不强制失效，自然过期 | 不做踢下线/token 黑名单 |
| 8 | 题目保存体扩展 `knowledgePointIds` | 建议透传（可选实现） | 前端不强依赖，兜底走 10.1 PUT |
| 9 | 成员/教师移除 | 按角色分离（members 删 STUDENT、teachers 删 TEACHER） | 不做"一个接口删全部角色" |
| 10 | 我的班级 | 全量不分页 | 学员班数规模小，不预设分页 |
| 11 | 同学列表 | 只下发 `userId/realName/joinedAt`，不下发 username | 不做同学个人页 |
| 12 | 知识点双列表 | 管理页分页（带引用计数）+ 选择器 `/all` 全量 | 编辑页不做分页选择器 |
| 13 | 知识点删除 | 被题目引用时禁止删除 | 不做级联清理/引用迁移 |
| 14 | 班级字段 | 名称/方向/起止时间/状态，与规格一致 | 不加 description 等规格外字段 |
| 15 | 数据范围越权 | 单资源 403（`ClassScopeException`）；列表类用过滤返回子集 | 不把越权混进 code 500 |
| 16 | `course_classes` | M1 只建表无接口 | 课程-班级分配接口属 M2 |
| 17 | 无班级学员可见性 | M1 不动（规格默认决策） | 不在本契约加过滤参数 |
