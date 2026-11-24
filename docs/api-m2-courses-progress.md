# 接口契约：M2 课程、章节与视频学习

> 状态：设计稿（待实现）
> 适用端：exam-system-server（Spring Boot 3 + MyBatis-Plus）、exam-system-web（Vue3 + Pinia + Element Plus）
> 规格：`docs/specs/m2-courses-chapters-videos.md`（本契约支撑其 9 条验收标准，对照表见第 13 节）
> 依赖：M1 契约 `docs/api-m1-classes-knowledge.md` 落地的 `ClassScopeService`、`classes`/`class_members`/`course_classes` 表（`course_classes` M1 只建表，分配接口在本契约定义）
> 面向读者：前后端开发、architect（表与 `ClassScopeService` 联动见第 11 节）

---

## 1. 功能定位

| 功能块 | 说明 | 新增表 / 复用 |
| --- | --- | --- |
| 课程管理 | 教师/管理员建课程（名称、描述、封面、草稿/发布），分配班级（多选整体替换） | `courses`；分配接线 M1 的 `course_classes` |
| 章节管理 | 课程下章节增删改、上下移排序 | `course_chapters` |
| 章节视频 | 章节内挂视频：从视频中心选已有 video_id，或新建外链；设必学标记、完成比例、排序 | `chapter_videos`；视频实体复用 `videos`（source_type=0 站内 / 1 外链，复用 `VideoController` 既有机制） |
| 学习进度 | 站内视频观看达标自动完成；外链学员点"完成学习"确认 + 最低停留 60 秒；重看不重复计进度 | `learning_video_progress`（user+video 唯一）；外链停留校验复用 `video_views.session_id` 心跳数据 |
| 我的课程 | 学员端课程列表（按班级过滤 + 个人进度百分比）、课程学习页（章节目录 + 播放 + 完成标记） | 读 `course_classes` + `class_members`（依赖 M1 `ClassScopeService`） |
| 教师统计 | 班级课程视角的学员完成情况列表（简单列表，仪表盘是 M6） | 聚合 `learning_video_progress` + `class_members` |

**核心机制说明（前后端一致理解，详见第 10 节完成判定口径）：**

1. **完成判定走独立进度接口，不改 `/api/videos/{id}/view`**（本契约最重要的决策，理由见 7.4）。
2. 章节完成 = 章节内 `required=true` 的必学视频全部完成；课程进度 = 已完成章节数 / 章节总数。完成状态与进度百分比均为**查询时派生计算**，不落表。
3. 外链视频的"最低停留 60 秒"由后端基于**当前绑定观看会话**（`video_views.session_id` 心跳累计时长）校验，不虚构精确进度。

---

## 2. 通用约定（与现有接口对齐）

### 2.1 认证、角色与数据范围图例

- 所有接口需携带请求头 `Authorization: Bearer <token>`；本契约所有路径前缀（`/api/courses`、`/api/my/courses`、`/api/learning`、`/api/chapters`、`/api/chapter-videos`）**不加入** `AuthInterceptor` 公开白名单（与 `/api/videos/**` 的公开白名单不同——学习进度必须有登录态）。
- 角色控制沿用 `@RequireRole` + `AuthInterceptor`（403）；**班级维度数据范围**由 `ClassScopeService` 在 Controller/Service 内判定（签名见 M1 契约第 12 节：`getTeacherClassIds(userId)`、`getStudentClassIds(userId)`）。
- **教师课程数据范围的语义（全文统一）**：
  1. **可见**：教师负责班级已分配的课程（`course_classes` ∩ `getTeacherClassIds`）∪ 本人创建的课程（`courses.created_by = 当前用户`）；
  2. **可编辑**（课程/章节/章节视频的增删改）：仅 ADMIN 与**课程创建者本人**。分配给了负责班级但不 created_by 的课程，教师可看可统计、不可改——避免多教师互相改动对方课程内容；
  3. **可统计**：课程分配给了自己负责的班级即可查看该班级的完成统计；
  4. 教师分配班级时，`classIds` 必须 ⊆ 本人负责班级集合（ADMIN 不限）。
- **学员可见性语义**：学员课程 = 本人所属班级（`getStudentClassIds`，仅 `ENABLED` 班级）已分配且课程状态为 `PUBLISHED` 的课程。**无班级学员课程列表为空**（规格默认决策：课程是班级资源；考试可见性不在本阶段动）。
- 数据范围标注图例（第 3 节总览表使用，与 M1 契约一致）：

| 标注 | 含义 |
| --- | --- |
| ADMIN | 仅管理员（`@RequireRole("ADMIN")`） |
| ADMIN / TEACHER-own | 管理员全量；教师限数据范围内（列表过滤返回子集，单资源越权 403） |
| STUDENT-own | 学员限本人所属班级资源（班级外的课程/章节 → 403） |
| STUDENT | 仅学员本人（个人学习数据，从 token 解析 userId） |

- 越权异常沿用 M1 约定：`ClassScopeException` → HTTP 403 + `{code:403, message:"无权访问该班级"}`（课程类单资源越权文案为"无权访问该课程"）。

### 2.2 统一响应结构

与现有 `Result<T>` 完全一致：

```json
{ "code": 200, "message": "操作成功", "data": { } }
```

- 成功：`code = 200`；业务失败：HTTP 200 + `code = 500`（`GlobalExceptionHandler` 兜底），不为本模块新引入 4xx 业务码；401/403 语义同 M1 契约 2.4。

### 2.3 分页

- 管理端列表（4.1）与现有 `/api/questions/list` 一致：`page`（默认 1）、`size`（默认 10），响应为 MyBatis-Plus `Page` 序列化结果（`records/total/size/current/pages`，其余框架字段前端忽略）。
- **学员"我的课程"列表不分页**（7.1）：单机构单班课程量小（几十以内），进度百分比需要全量计算，分页反而增加聚合成本；教师统计列表（8.1）为班级成员全量，同样不分页。

### 2.4 错误码语义（全模块统一）

| HTTP | body code | 触发条件 | 前端处理 |
| --- | --- | --- | --- |
| 401 | 401 | 未登录或 token 过期（`AuthInterceptor`） | `request.js` 已有处理 |
| 403 | 403 | ① 角色不足（`@RequireRole`）；② 教师/学员访问数据范围外的单资源（`ClassScopeException`） | 提示"无权访问该课程 / 该班级 / 无权限执行该操作" |
| 200 | 500 | 业务失败（课程不存在、章节重复、外链停留不足 60 秒等），`message` 为用户可读文案 | `request.js` 已统一 `ElMessage.error(message)` 弹出 |

### 2.5 学习计时约定（heartbeat 协议，前后端对齐）

- **心跳间隔 10 秒**：课程学习页对站内/外链视频统一每 10 秒调一次 `POST /api/learning/chapter-videos/{id}/heartbeat`，`viewDuration` 传**自上次心跳以来的真实停留增量**（站内=播放器播放增量，外链=页面停留增量）。
- **页面隐藏暂停计时**：监听 `document.visibilitychange`，页面不可见时不累计、不发心跳；恢复可见后继续。
- **不做 sendBeacon 兜底**：现有视频中心 `/view` 是公开接口所以 beacon 可行；heartbeat 需要登录态（`Authorization` 头），`sendBeacon` 无法携带自定义请求头。丢最后一拍增量可接受（最多丢 10 秒，详见决策表 #14）。
- **增量上限**：后端每次心跳增量与 `VideoServiceImpl.VIEW_DELTA_CAP_SECONDS`（=60）取 min，防刷逻辑与观看统计一致。
- 时间字段统一 `yyyy-MM-dd HH:mm:ss` 字符串（与现有 `@JsonFormat` 一致）；枚举值全大写字符串（`DRAFT`/`PUBLISHED`、`IN_PROGRESS`/`COMPLETED`）。
- 幂等：外链完成确认重复调用返回成功（已完成即幂等）；分配班级重复提交同一集合无副作用。
- 后端实现按现有风格补 Swagger 注解，Knife4j 分组名："课程管理"、"我的课程"、"学习进度"。

---

## 3. 接口总览

| # | 方法 | 路径 | 说明 | 数据范围 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/api/courses/list` | 课程分页列表（管理视角） | ADMIN / TEACHER-own（可见范围） |
| 2 | POST | `/api/courses` | 创建课程 | ADMIN / TEACHER |
| 3 | PUT | `/api/courses/{id}` | 更新课程基本信息 | ADMIN / TEACHER（创建者） |
| 4 | DELETE | `/api/courses/{id}` | 删除课程 | ADMIN / TEACHER（创建者） |
| 5 | PUT | `/api/courses/{id}/status` | 草稿/发布切换 | ADMIN / TEACHER（创建者） |
| 6 | GET | `/api/courses/{id}/classes` | 查询已分配班级 | ADMIN / TEACHER-own |
| 7 | PUT | `/api/courses/{id}/classes` | 课程-班级分配（多选，整体替换） | ADMIN / TEACHER（创建者，班⊆负责班级） |
| 8 | GET | `/api/courses/{courseId}/chapters` | 章节列表（管理视角，含视频数） | ADMIN / TEACHER-own |
| 9 | POST | `/api/courses/{courseId}/chapters` | 新增章节 | ADMIN / TEACHER（创建者） |
| 10 | PUT | `/api/chapters/{id}` | 更新章节 | ADMIN / TEACHER（创建者） |
| 11 | DELETE | `/api/chapters/{id}` | 删除章节 | ADMIN / TEACHER（创建者） |
| 12 | PUT | `/api/chapters/{id}/move` | 章节排序上移/下移 | ADMIN / TEACHER（创建者） |
| 13 | GET | `/api/chapters/{chapterId}/videos` | 章节视频列表（管理视角） | ADMIN / TEACHER-own |
| 14 | POST | `/api/chapters/{chapterId}/videos` | 添加章节视频（选已有 / 新建外链） | ADMIN / TEACHER（创建者） |
| 15 | PUT | `/api/chapter-videos/{id}` | 更新章节视频配置（必学/比例） | ADMIN / TEACHER（创建者） |
| 16 | DELETE | `/api/chapter-videos/{id}` | 移除章节视频 | ADMIN / TEACHER（创建者） |
| 17 | PUT | `/api/chapter-videos/{id}/move` | 章节视频排序上移/下移 | ADMIN / TEACHER（创建者） |
| 18 | GET | `/api/my/courses` | 我的课程列表（班级过滤 + 进度） | STUDENT |
| 19 | GET | `/api/my/courses/{courseId}` | 课程学习详情（章节目录含完成状态） | STUDENT-own |
| 20 | GET | `/api/my/courses/{courseId}/chapters/{chapterId}` | 章节学习详情（视频含完成状态） | STUDENT-own |
| 21 | POST | `/api/learning/chapter-videos/{chapterVideoId}/heartbeat` | 学习心跳上报（完成判定通道） | STUDENT |
| 22 | POST | `/api/learning/chapter-videos/{chapterVideoId}/external-complete` | 外链"完成学习"确认（≥60 秒校验） | STUDENT |
| 23 | GET | `/api/courses/{courseId}/class-progress` | 班级课程学员完成情况（教师统计） | ADMIN / TEACHER-own |

命名说明：课程资源复数 `courses`、章节 `chapters`、章节视频 `chapter-videos`（kebab-case 与 `/api/exam-records` 一致）；学员个人数据挂在 `/api/my/...`（与 M1 `/api/my/classes` 一致，userId 从 token 解析，路径不出 userId 参数）；学习动作用动词子资源（`heartbeat`、`external-complete`、`move`）。

---

## 4. 课程管理接口

> 4.1、4.6 按可见范围查询；创建课程后，修改、发布、删除和替换分配仅允许管理员或创建教师。越权返回 HTTP 403。

### 4.1 分页查询课程列表

**GET `/api/courses/list`**

**数据范围：** ADMIN 全部；TEACHER 按 1 节"可见"语义过滤（负责班级已分配 ∪ 本人创建），不报错返回子集。STUDENT 调用 → 403（学生走 `/api/my/courses`）。

**请求参数（Query）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page / size | Integer | 否 | 默认 1 / 10 |
| keyword | String | 否 | 课程名称模糊匹配 |
| status | String | 否 | `DRAFT` / `PUBLISHED`，非法值忽略该筛选（沿用现有约定） |

**响应 `data`：** Page 结构，`records[]` 元素字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 课程 ID |
| name / description / coverUrl | String | 基本信息 |
| status | String | `DRAFT` / `PUBLISHED` |
| creatorName | String | 创建者姓名（join `users`） |
| createdBy | Long | 创建者 userId |
| chapterCount | Integer | 章节数 |
| classCount | Integer | 已分配班级数；教师仅统计本人负责的班级 |
| createdAt / updatedAt | String | `yyyy-MM-dd HH:mm:ss` |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1, "name": "Java 入门", "description": "零基础 Java 语法与面向对象",
        "coverUrl": "http://localhost:9000/files/course/java-intro.jpg",
        "status": "PUBLISHED", "creatorName": "teacher1", "createdBy": 3,
        "chapterCount": 2, "classCount": 1,
        "createdAt": "2026-11-23 10:00:00", "updatedAt": "2026-11-23 12:00:00"
      }
    ],
    "total": 1, "size": 10, "current": 1, "pages": 1
  }
}
```

### 4.2 创建课程

**POST `/api/courses`**

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| name | String | 是 | 非空，≤100 字；**不强制全局唯一**（决策表 #8） |
| description | String | 否 | ≤500 字 |
| coverUrl | String | 否 | 封面图片 URL；上传复用 `FileController`（`/files`）既有 MinIO 上传通道，本契约不新增封面接口 |

**行为：** `created_by` = 当前用户；`status` 默认 `DRAFT`（创建不直接发布，发布走 4.4 显式动作）。响应返回新建课程完整对象（字段同 4.1 records 元素，classCount=0）。

### 4.3 更新课程

**PUT `/api/courses/{id}`**

请求体同 4.2（全量覆盖 name/description/coverUrl）。课程不存在或已删除 → `{code:500, message:"课程不存在"}`。

### 4.4 草稿/发布切换

**PUT `/api/courses/{id}/status`**

**请求体：** `{ "status": "PUBLISHED" }`（`DRAFT` / `PUBLISHED`，其他值 → `{code:500, message:"参数错误"}`）。

发布后课程对已分配班级学员可见；切回 `DRAFT` 学员入口立即消失（列表与详情均不可见，正在学习页的进度数据保留不清）。课程无章节时允许发布（学员看到空目录），不阻断。

### 4.5 删除课程

**DELETE `/api/courses/{id}`**

逻辑删除课程及其章节，物理删除 `chapter_videos` 和 `course_classes` 关系。**学员的 `learning_video_progress` 保留不清**；它按学员和视频记录，复用同一视频时已有的视频学习历史仍有效。删除前不校验是否有学习进度，确认动作由前端弹窗承担（决策表 #9）。

### 4.6 查询课程已分配班级

**GET `/api/courses/{id}/classes`**

分配抽屉回显用。响应 `data`：

```json
{
  "classIds": [1, 2],
  "classes": [
    { "classId": 1, "className": "Java 培训一班", "status": "ENABLED", "studentCount": 25, "assignedAt": "2026-11-23 10:05:00" }
  ]
}
```

`classes[]` 含已禁用班级（教师能看到它已分配但不可见性不生效，由 `status` 区分）；`assignedAt` 为该分配关系创建时间。
管理员回显全部未删除班级；教师只回显自己负责的班级及其人数。

### 4.7 课程-班级分配（整体替换）

**PUT `/api/courses/{id}/classes`**

**请求体（JSON）：**

```json
{ "classIds": [1, 2, 3] }
```

**语义与校验：**

1. **按权限整体替换**：管理员替换全部班级分配；课程创建教师只替换本人负责班级内的分配，不影响管理员分配到其他班级的关系。落库采用差量同步，保留未变化的关系及分配时间；
2. `classIds` 为空数组 = 清空当前调用者可管理的分配；
3. 每个班级必须存在且未逻辑删除（禁用班级**允许**分配，但学员可见性按 1 节语义只算 `ENABLED` 班级）；不存在 → `{code:500, message:"班级不存在"}`；
4. TEACHER（课程创建者）的 `classIds` ⊆ `ClassScopeService.getTeacherClassIds(当前用户)`，越界 → HTTP 403（`ClassScopeException`，"无权访问该班级"）；
5. 幂等：同一课程的替换请求通过课程行锁串行执行，重复提交同一集合不写入。

**响应：** `{ "classIds": [1, 2, 3] }`（回显调用者可管理范围内的替换结果）。验收 1/7 由本接口支撑。

---

## 5. 章节管理接口

> 所有接口要求：课程可见（TEACHER-own）且**课程可编辑**（ADMIN 或创建者本人），否则 403。章节删除为逻辑删除，sort 不重排（前端展示时按剩余章节 sort 排序即可，决策表 #10）。

### 5.1 章节列表（管理视角）

**GET `/api/courses/{courseId}/chapters`**

**响应 `data`（数组，按 sort 升序）：**

```json
{
  "chapters": [
    {
      "id": 1, "courseId": 1, "title": "第一章 环境搭建", "description": "JDK 安装与 IDE",
      "sort": 1, "videoCount": 2, "requiredVideoCount": 2, "createdAt": "2026-11-23 10:10:00"
    }
  ]
}
```

管理视角不含完成状态（学员完成情况走 8.1）。

### 5.2 新增章节

**POST `/api/courses/{courseId}/chapters`**

请求体：`{ "title": "...", "description": "..." }`（title 必填 ≤100 字）。`sort` 自动置底（当前最大 sort + 1）。响应返回新建章节对象。

### 5.3 更新章节

**PUT `/api/chapters/{id}`**　请求体同 5.2，全量覆盖。

### 5.4 删除章节

**DELETE `/api/chapters/{id}`**

逻辑删除章节并级联逻辑删除其 chapter_videos。学员相关 `learning_video_progress` 保留（按 user+video 唯一，与章节解耦，重挂同视频不产生脏数据）。

### 5.5 章节排序上移/下移

**PUT `/api/chapters/{id}/move`**

**请求体：** `{ "direction": "UP" }`（`UP` / `DOWN`）。

与相邻章节**交换 sort 值**；已在顶/底时幂等成功无变化。响应返回更新后的章节对象（新 sort）。不做拖拽、不做任意目标位置交换（规格默认决策）。

---

## 6. 章节视频管理接口

> 权限同第 5 节（课程可编辑）。字段 `required`、`completionRatio` 均落在 `chapter_videos`（章节-视频关联行），不污染 `videos` 实体。

### 6.1 章节视频列表（管理视角）

**GET `/api/chapters/{chapterId}/videos`**

**响应 `data`（数组，按 sort 升序）：**

```json
{
  "chapterVideos": [
    {
      "id": 12, "chapterId": 1, "videoId": 5, "sort": 1,
      "title": "Java 环境搭建实操", "sourceType": 0, "fileUrl": "http://localhost:9000/videos/original/setup.mp4",
      "coverUrl": "http://localhost:9000/videos/covers/setup.jpg",
      "duration": 1800, "durationText": "30:00",
      "required": true, "completionRatio": 70,
      "createdAt": "2026-11-23 10:20:00"
    },
    {
      "id": 13, "chapterId": 1, "videoId": 9, "sort": 2,
      "title": "JDK 官方文档导读（B 站）", "sourceType": 1, "fileUrl": "https://www.bilibili.com/video/BV1xx411c7mD",
      "coverUrl": null, "duration": null, "durationText": null,
      "required": true, "completionRatio": 70,
      "createdAt": "2026-11-23 10:21:00"
    }
  ]
}
```

`title`/`sourceType`/`fileUrl`/`duration` 等来自 join `videos`；`required`/`completionRatio` 来自 `chapter_videos`。站内视频 `duration` 为空时的约束见 6.2。

### 6.2 添加章节视频

**POST `/api/chapters/{chapterId}/videos`**

两种来源二选一，由 `source` 区分：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| source | String | 是 | `EXIST`（视频中心已有视频）/ `EXTERNAL`（新建外链） |
| videoId | Long | source=EXIST | 视频 ID，须存在且 `status=1`（已发布）；source_type 站内/外链均可选入；站内视频 `duration` 必须非空且 >0（否则无法自动判定完成 → `{code:500, message:"该视频缺少时长信息，无法自动判定完成，请先在视频中心补充时长"}`） |
| title | String | source=EXTERNAL | 外链视频标题 ≤100 字 |
| externalUrl | String | source=EXTERNAL | 外链 URL，须 http/https 且域名在白名单内（见下方） |
| required | Boolean | 否 | 必学标记，**默认 true**（教师忘配则按必学算，进度口径保守，决策表 #11） |
| completionRatio | Integer | 否 | 完成比例 1–100（百分比整数），默认 70，仅对站内视频生效 |

**行为：**

- `EXIST`：插入 `chapter_videos` 关联行；同一章节重复添加同一视频 → `{code:500, message:"该视频已在本章节中"}`（唯一索引兜底，不幂等——重复无业务意义）；
- `EXTERNAL`：后端**代建** `videos` 记录（`source_type=1`、`file_url=externalUrl`、`status=1` 已发布、`uploader_type=2`、`uploader_name=当前用户姓名`、`duration=null`），再插关联行；
- **外链白名单**：配置项 `learning.external-video.allowed-domains`（application.yaml 列表）。**默认允许主流视频站**（bilibili.com、iqiyi.com、youku.com、v.qq.com、douyin.com）；配置为空 = 不限制（单机构场景宽松起步）。校验只匹配域名后缀（`www.bilibili.com` 命中 `bilibili.com`）；不在白名单 → `{code:500, message:"不支持的视频链接域名"}`；
- **新上传站内视频不走本接口**：指引教师先到视频中心上传（`POST /api/admin/videos`，既有 TEACHER/ADMIN 接口），再回来 `EXIST` 选取（决策表 #3）。

**响应：** 返回新建的章节视频对象（字段同 6.1 元素）。

### 6.3 更新章节视频配置

**PUT `/api/chapter-videos/{id}`**

请求体（全量覆盖关联配置，不改动 videos 实体）：

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| title | String | 是 | 外链视频可改标题（同步更新 videos.title）；站内视频改标题时提示将影响视频中心展示 |
| required | Boolean | 是 | 必学标记 |
| completionRatio | Integer | 是 | 1–100 |

修改 `required`/`completionRatio` 只影响**之后的完成判定**：已完成的学习进度不回退（重看不重复计进度），未完成者按新口径判定（决策表 #12）。

### 6.4 移除章节视频

**DELETE `/api/chapter-videos/{id}`**

删除关联行。`EXTERNAL` 代建的 videos 记录**保留不删**（视频中心资产，可能被其他章节引用）。学员进度行保留（同 5.4 理由）。

### 6.5 章节视频排序上移/下移

**PUT `/api/chapter-videos/{id}/move`**　语义同 5.5，交换同一章节内相邻视频的 sort。

---

## 7. 学员学习接口

> 全部 `@RequireRole("STUDENT")`；userId 从 token 解析（`request.getAttribute("userId")`），路径不带 userId。

### 7.1 我的课程列表

**GET `/api/my/courses`**（不分页，见 2.3）

**口径：** 课程 ∈ 本人所属 `ENABLED` 班级的分配集合（`course_classes` ∩ `ClassScopeService.getStudentClassIds`）∩ `status=PUBLISHED`。无班级学员返回空数组（验收 6）。

**响应 `data`（数组，按课程创建时间倒序）：**

```json
{
  "courses": [
    {
      "courseId": 1, "name": "Java 入门", "description": "零基础 Java 语法与面向对象",
      "coverUrl": "http://localhost:9000/files/course/java-intro.jpg",
      "teacherName": "teacher1",
      "chapterTotal": 2, "chapterCompleted": 1,
      "progressPercent": 50,
      "assignedAt": "2026-11-23 10:05:00"
    }
  ]
}
```

`progressPercent` = `floor(已完成章节数 / 章节总数 × 100)`；章节总数为 0 → 0%（口径见第 10 节）。

### 7.2 课程学习详情（章节目录含完成状态）

**GET `/api/my/courses/{courseId}`**

**校验：** 课程存在且 `PUBLISHED`，且已分配给本人所属班级；不满足 → `{code:500, message:"课程不存在或未开放"}`（学员视角不区分"不存在"与"无权"，避免探测未分配课程；越权与不存在同文案）。

**响应 `data`：**

```json
{
  "courseId": 1, "name": "Java 入门", "description": "…", "coverUrl": "…",
  "teacherName": "teacher1",
  "chapterTotal": 2, "chapterCompleted": 1, "progressPercent": 50,
  "chapters": [
    {
      "chapterId": 1, "title": "第一章 环境搭建", "sort": 1,
      "videoCount": 2, "requiredVideoCount": 2, "completedVideoCount": 2,
      "completed": true
    },
    {
      "chapterId": 2, "title": "第二章 面向对象", "sort": 2,
      "videoCount": 1, "requiredVideoCount": 1, "completedVideoCount": 0,
      "completed": false
    }
  ]
}
```

章节目录只到章节级完成状态，**不含视频列表**（视频列表按章节懒加载走 7.3，目录轻量、学习页首次加载快）。

### 7.3 章节学习详情（视频列表含每个视频的完成状态）

**GET `/api/my/courses/{courseId}/chapters/{chapterId}`**

**校验：** 课程同 7.2；章节必须属于该课程（否则 `{code:500, message:"章节不存在"}`）。

**响应 `data`：**

```json
{
  "chapterId": 1, "courseId": 1, "title": "第一章 环境搭建", "sort": 1,
  "completed": true,
  "videos": [
    {
      "chapterVideoId": 12, "videoId": 5, "sort": 1,
      "title": "Java 环境搭建实操", "sourceType": 0,
      "fileUrl": "http://localhost:9000/videos/original/setup.mp4",
      "coverUrl": "…", "duration": 1800, "durationText": "30:00",
      "required": true, "completionRatio": 70,
      "requiredSeconds": 1260, "watchedSeconds": 1300,
      "completed": true, "completedAt": "2026-11-23 14:30:00"
    },
    {
      "chapterVideoId": 13, "videoId": 9, "sort": 2,
      "title": "JDK 官方文档导读（B 站）", "sourceType": 1,
      "fileUrl": "https://www.bilibili.com/video/BV1xx411c7mD",
      "coverUrl": null, "duration": null, "durationText": null,
      "required": true, "completionRatio": 70,
      "requiredSeconds": 60, "watchedSeconds": 45,
      "completed": false, "completedAt": null
    }
  ]
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| requiredSeconds | 完成门槛秒数。站内视频 = `ceil(duration × completionRatio / 100)`；外链 = 固定 60（最低停留秒数） |
| watchedSeconds | 本人累计观看秒数（`learning_video_progress.watched_seconds`，未完成时随心跳累加，完成后冻结） |
| completed（顶层） | 章节完成状态（该章必学视频是否全部完成，前端渲染目录图标用） |
| completed / completedAt（videos 元素内） | 视频完成状态与完成时间（`learning_video_progress` 按 user+video 唯一） |

外链视频 `fileUrl` 下发原始链接，前端新窗口打开（决策表 #6）；站内视频播放复用现有播放器（MinIO 直链）。

### 7.4 学习心跳上报（完成判定通道，本契约核心接口）

**POST `/api/learning/chapter-videos/{chapterVideoId}/heartbeat`**

**结论先行——为什么是独立接口而不是改 `/api/videos/{id}/view`：**

1. `/api/videos/**` 在 `AuthInterceptor` 白名单（`AuthInterceptor.java` 第 107/129 行），**公开无登录态、无 userId**；`video_views` 只有 `user_ip/user_agent/session_id`，**根本没有 user 列**，承载不了"按 user+video"的学习进度；
2. 公共视频中心的观看统计（游客可看、按 IP 计数）与课程学习进度是两种语义，改公共接口会把课程逻辑耦合进视频中心；
3. 所以：**课程学习页唯一上报通道是本接口**，内部**复用 `VideoService.recordVideoView`**——观看次数、会话时长统计与视频中心完全一致（验收 4 的"观看次数+1"由此天然满足），公共视频页继续用 `/view` 不动。

**请求体（JSON）：**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| sessionId | String | 是 | 观看会话 ID（UUID，进入学习页生成并全程复用；与视频中心 `/view` 同一命名，后端据此关联 `video_views`） |
| viewDuration | Integer | 是 | 自上次心跳以来的停留增量秒数（0 允许，负数/超 60 截断为上限逻辑同 `VIEW_DELTA_CAP_SECONDS`） |

**处理流程：**

1. 校验 chapterVideoId 存在（课程 `PUBLISHED` 且分配给本人班级，同 7.2 口径）；
2. 调用 `videoService.recordVideoView(videoId, sessionId, viewDuration, request)`：首报建会话计观看数，同会话心跳累加 `video_views.view_duration`（既有机制，零改动）；
3. 读写本人 `learning_video_progress`（user+video 唯一）：不存在则插入 `IN_PROGRESS` 并绑定 `session_id=sessionId`（当前活跃会话）；已存在且 `COMPLETED` → **只走第 2 步，不再累加 watchedSeconds、不改状态**（重看不重复计进度）；
4. 未完成时：`watched_seconds += min(delta, 60)`；站内视频判定 `watched_seconds ≥ ceil(duration × completionRatio / 100)` → 置 `COMPLETED` 并记 `completed_at`。外链视频**不在此接口判定完成**（须显式确认走 7.5）；
5. 章节完成后重算（派生，不落表）。

**响应 `data`：**

```json
{
  "chapterVideoId": 12, "videoId": 5, "sourceType": 0,
  "required": true, "completionRatio": 70,
  "watchedSeconds": 1300, "requiredSeconds": 1260,
  "completed": true,
  "chapterCompleted": true,
  "chapterCompletedAfterThisVideo": true
}
```

| 字段 | 说明 |
| --- | --- |
| completed | 该视频当前完成状态（本次心跳后） |
| chapterCompleted | 该章节当前完成状态（本次心跳后，派生重算） |
| chapterCompletedAfterThisVideo | 本次心跳**是否导致章节从未完成变为完成**（true 时前端即时刷新目录图标/进度条，无需回查 7.2；非必学视频完成时恒为 false） |

外链视频的心跳响应 `completed` 恒为其当前状态（false 直到 7.5 确认），`requiredSeconds=60` 供前端做"满 60 秒才能点确认"的倒计时提示。

### 7.5 外链"完成学习"确认（最低停留 60 秒校验）

**POST `/api/learning/chapter-videos/{chapterVideoId}/external-complete`**

请求体为空（停留数据全部来自服务端已累计的心跳，前端不传时长——不可信输入不参与校验）。

**校验规则：**

1. chapterVideoId 存在且 `videos.source_type=1`（站内视频调此接口 → `{code:500, message:"站内视频无需手动确认完成"}`）；
2. 本人存在 `learning_video_progress` 行且绑定了 session_id；无进度行（一次心跳都没发过）→ `{code:500, message:"请先打开视频后再确认完成"}`；
3. **最低停留**：当前绑定会话在 `video_views` 的累计 `view_duration ≥ 60` 秒。不满足 → `{code:500, message:"请继续观看满 60 秒后再确认完成（还差 X 秒）"}`（验收 5）；
4. 通过 → 置 `COMPLETED` + `completed_at`，幂等（已 COMPLETED 直接返回成功响应）。

**响应 `data`：** 结构同 7.4（completed=true、chapterCompleted 为确认后的章节状态）。

**边界（决策表 #13）：** 最低停留按**当前绑定会话**计（`video_views.session_id` 粒度），用户刷新学习页会生成新会话、停留重新计时——60 秒要求本来就是"确认真看了"的轻量门槛，跨会话累计属于过度设计。

---

## 8. 教师统计接口

### 8.1 班级课程学员完成情况

**GET `/api/courses/{courseId}/class-progress?classId=1`**（不分页）

**数据范围：** ADMIN 任意课程+班级组合；TEACHER 要求 `classId ∈ getTeacherClassIds` 且课程已分配给该班级（未分配 → `{code:500, message:"该课程未分配给该班级"}`），越权 403。

**请求参数（Query）：**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| classId | Long | 是 | 班级 ID |

**响应 `data`：**

```json
{
  "courseId": 1, "courseName": "Java 入门",
  "classId": 1, "className": "Java 培训一班",
  "chapterTotal": 2,
  "students": [
    {
      "userId": 11, "username": "student1", "realName": "张三",
      "completedVideoCount": 3, "requiredVideoTotal": 3,
      "completedChapterCount": 1, "chapterTotal": 2,
      "progressPercent": 50,
      "lastLearnTime": "2026-11-23 14:30:00"
    },
    {
      "userId": 12, "username": "student2", "realName": "李四",
      "completedVideoCount": 0, "requiredVideoTotal": 3,
      "completedChapterCount": 0, "chapterTotal": 2,
      "progressPercent": 0,
      "lastLearnTime": null
    }
  ]
}
```

口径说明：

- 学员集合 = 该班级 `role_in_class='STUDENT'` 的成员（未入班者不出现，验收 6 的教师侧印证）；
- `completedVideoCount` 统计该课程全部章节视频（含选学）；`requiredVideoTotal` 为必学视频总数（教师判断"落后"的主指标是章节维度，两者都给，前端自行取舍）；
- `lastLearnTime` = 该学员本课程范围内 `learning_video_progress.updated_at` 最大值，无任何学习记录为 null；
- 一人可入多班：同班内每人一行，不做跨班去重（本接口以班级为视角）。

详细仪表盘（知识点薄弱、趋势）是 M6，本接口不做任何扩展参数。

---

## 9. 既有接口的联动（不新增、不改动清单）

| 既有机制 | M2 的关系 |
| --- | --- |
| `POST /api/videos/{videoId}/view`（VideoController） | **不改**。公共视频中心继续使用；课程学习页的观看统计由 heartbeat 内部复用 `recordVideoView` 达成同效果 |
| `videos` 实体（source_type / file_url / duration / status） | 复用。章节添加 `EXIST` 校验 `status=PUBLISHED`；`EXTERNAL` 代建记录的语义见 6.2 |
| `POST /api/admin/videos`（视频中心上传，TEACHER/ADMIN） | 教师新上传站内视频的唯一通道，章节添加前先传 |
| `FileController`（`/files` MinIO 上传） | 课程封面图上传复用，不新增封面接口 |
| `ClassScopeService`（M1） | 教师班级集合 / 学员班级集合的全部数据范围判定；本契约只定义业务口径，调用细节实现期对齐 |
| `course_classes` 表（M1 只建表） | 本契约 4.6/4.7 首次接线（增删查，无单条分配接口） |
| 错题本 / 考试 / 判卷 | 无改动（验收 9 回归项） |

---

## 10. 完成判定口径（集中定义，前后端一致理解）

1. **站内视频完成**：`learning_video_progress.watched_seconds ≥ ceil(videos.duration × chapter_videos.completion_ratio / 100)`，由 heartbeat 服务端判定，比例默认 70%。`watched_seconds` 为**本人累计**（跨会话累加，未完成前每次心跳累加 min(delta, 60)），非单会话时长。
2. **外链视频完成**：学员在停留 ≥60 秒后点"完成学习"，服务端校验当前绑定会话 `video_views.view_duration ≥ 60` → 置 COMPLETED。不虚构精确进度（无百分比概念，`watchedSeconds` 仅展示停留累计）。
3. **章节完成**：该章节 `required=true` 的 chapter_videos **全部** COMPLETED。**无必学视频的章节视为已满足完成条件**（空集语义：教师没配必学 = 不设卡，直接计入课程进度分子）——统一口径、实现简单，且"教师配了必学才产生约束"（决策表 #15）。
4. **课程进度**：`floor(已完成章节数 / 章节总数 × 100)`；章节总数 0 → 0%。DRAFT 课程对学员整体不可见，不产生进度问题。
5. **重看**：已完成视频可无限重看；heartbeat 对 COMPLETED 的 progress 行只走观看统计（观看次数、会话时长），**不累加 watchedSeconds、不改状态、不改 completedAt**。观看次数沿用 `videos.view_count` 会话机制（新 sessionId 计 1 次）。
6. **配置变更不追溯**：修改 required / completionRatio 只影响之后未完成者的判定；已完成的进度与完成时间不回退。把视频从必学改为选学后，章节完成条件立即放宽（派生计算的天然结果）；反之立即收紧。
7. **无 userId 的观看数据不可用于进度**：`video_views` 的 IP 游客数据只服务公共视频中心统计；一切学习判定以登录用户的 progress 行为准。

---

## 11. 与后端表设计 / ClassScopeService 的联动约定（给 architect）

本契约不规定具体表结构，只依赖以下**语义**，建表由 architect 输出增量 SQL `2026-11-23_m2_courses_progress.sql`：

1. **`courses`**：`name, description, cover_url, status(DRAFT/PUBLISHED), created_by(→users.id)` + BaseEntity；逻辑删除。`created_by` 是教师可编辑性的唯一依据（1 节语义），**必须落库**。
2. **`course_chapters`**：`(course_id, title, description, sort)`；逻辑删除；查询按 `sort` 升序。建议 `(course_id, sort)` 索引。
3. **`chapter_videos`**：`(chapter_id, video_id)` **唯一索引**（防重复添加）、`required, completion_ratio(default 70), sort`；逻辑删除。`(chapter_id, sort)` 索引支撑排序与列表。
4. **`learning_video_progress`**：`(user_id, video_id)` **唯一索引**（规格明文：按 user+video 唯一，重挂同章节不产生脏数据）、`chapter_video_id, status(IN_PROGRESS/COMPLETED), watched_seconds, session_id(当前活跃会话), completed_at`；**不逻辑删除**（学习历史保留，课程/章节删除不清）。`completed_at` 是错题本/诊断类功能未来关联"学过什么"的锚点。
5. **`course_classes`**（M1 已建）：本契约按 `(course_id, class_id)` 整体替换语义读写；唯一索引 `(course_id, class_id)`；`assigned_at` 建议记录首次分配时间（4.6 回显）。
6. **派生计算**：章节完成、课程进度、7.1/7.2/8.1 的聚合一律**查询时实时计算**，不建章节完成表、不写冗余计数列（单班 ≤百人、章节 ≤几十，聚合成本可接受；冗余列要维护一致性，收益为零）。8.1 全班聚合请评估单条聚合 SQL（`class_members` join `learning_video_progress` group by user），避免 N+1。
7. **`ClassScopeService` 复用点**：4.7（教师班集合）、7.1/7.2/7.3/7.4（学员班集合）、8.1（教师班集合）；新增方法**不需要**——M1 签名已够。课程级"创建者校验"是简单 `created_by` 比对，不进 ClassScopeService。
8. **外链白名单**：`learning.external-video.allowed-domains` 配置键（`List<String>`），默认值见 6.2；后端提供 `ExternalVideoPolicy` 类封装校验，供 6.2 与未来 M5"推荐补课视频"复用。

---

## 12. 前端接入说明

1. **API 层**：新增 `src/api/course.js`（4–6 节管理端函数 + 8.1 统计）、`src/api/myCourse.js`（7.1–7.5），风格对齐 `src/api/examRecord.js`；heartbeat 封装为带计时器的组合式函数 `useLearningHeartbeat(chapterVideoId)`（10 秒间隔 + `visibilitychange` 暂停恢复），供学习页复用。
2. **管理端路由**（`/admin` 子路由，继承 `AdminLayout` 的 `meta: { roles: ['ADMIN','TEACHER'] }`）：
   - `/admin/courses`：课程列表（筛选 keyword/status）+ 新建对话框 + 分配班级抽屉（数据源 `GET /api/classes/list`，教师端后端已过滤负责班级）；
   - `/admin/courses/:id`：课程编辑 + 章节管理（手风琴列表，章节头部带上移/下移/删除；展开后为视频表格：来源、时长、必学开关 el-switch、完成比例 el-input-number、上移/下移/移除）+ "添加视频"对话框（Tab：选视频中心已有 / 填外链）；
   - 统计入口：课程列表行"学习情况"按钮 → 页内抽屉或 `/admin/courses/:id/progress?classId=`（班级下拉 + 学员表格）。
3. **AdminLayout 导航**：新增菜单项"课程管理"（与"试卷管理"并列）。
4. **学员端路由**（`UserLayout` 子路由）：
   - `/my/courses`：我的课程卡片墙（封面 + 进度条 + "继续学习"按钮）；
   - `/my/courses/:id`：课程学习页（单页不拆子路由，决策表 #16）：左侧章节目录（完成图标：已完成 √ / 进行中 ◐ / 未开始 ○，点击切换右侧内容），右侧播放区——站内视频内嵌播放器，外链显示卡片（"打开视频"新窗口按钮 + 满足 60 秒前禁用的"完成学习"按钮，按钮文案实时显示倒计时）。
5. **导航**：`UserLayout` 顶部导航或用户下拉加"我的课程"入口（与"我的班级"/"我的考试"并列）。
6. **状态处理**：`request.js` 已统一处理 `code!==200` 弹错、401、403；学习页对外链确认失败的文案（"还差 X 秒"）直接展示 `message`，不自行翻译错误码。heartbeat 失败静默（网络抖动丢一拍不弹错），连续 3 次失败才提示。

---

## 13. 验收标准对照与决策记录

### 13.1 验收标准 ↔ 契约对照

| # | 验收标准 | 支撑接口/口径 |
| --- | --- | --- |
| 1 | teacher1 建课程分配班级 → 班内学员可见 | 4.2 + 4.7（教师班⊆负责班级）；7.1（学员可见性口径） |
| 2 | 2 章节；章节 1 含必学站内 + 外链；章节 2 含必学视频 | 5.2 + 6.2（EXIST/EXTERNAL）；6.3（required） |
| 3 | 站内达标自动标记 + 外链确认 → 章节 1 完成；课程进度 50% | 7.4（≥70% 判定）+ 7.5（确认）；第 10 节口径 3/4 |
| 4 | 重复观看完成状态不变、观看次数+1 | 7.4 处理流程第 3 步 + `recordVideoView` 复用；第 10 节口径 5 |
| 5 | 外链停留 <60 秒确认 → 拒绝 | 7.5 校验规则 3 |
| 6 | 未入班学员看不到课程 | 7.1 口径（空班级集合 → 空列表）+ 8.1 教师侧印证 |
| 7 | 分配新班级 → 新班学员立即可见 | 4.7 整体替换 + 7.1 查询时派生（无缓存延迟） |
| 8 | mvn test 全绿、npm run build、增量 SQL | 第 11 节建表约定 |
| 9 | 错题本/考试/判卷回归 | 第 9 节：无改动清单 |

### 13.2 决策记录（YAGNI 边界，防止后续扯皮）

| # | 决策 | 结论 | 不做的事 |
| --- | --- | --- | --- |
| 1 | **完成判定走哪个接口** | **独立进度接口** `POST /api/learning/chapter-videos/{id}/heartbeat`，内部复用 `recordVideoView`；**不改** `/api/videos/{id}/view` | 理由见 7.4（view 无登录态、video_views 无 user 列、语义耦合） |
| 2 | 完成状态存储 | 派生计算（查询时实时聚合） | 不建章节完成表、不写冗余计数列 |
| 3 | 章节添加视频的新上传 | 指引先到视频中心（`/api/admin/videos`）上传再 EXIST 选取 | 不做章节内嵌上传组件（复用视频中心审核/封面流，避免两套上传） |
| 4 | 排序 | 上下移交换 sort | 不做拖拽库、不做任意位置插入 |
| 5 | 课程-班级分配 | PUT 整体替换（含空数组清空） | 不做单条 add/remove 分配接口 |
| 6 | 外链播放方式 | 新窗口打开 | 不做 iframe 嵌入（受制于第三方 X-Frame-Options，跨域坑多） |
| 7 | 课程删除 | 级联逻辑删章节/分配；**progress 保留** | 不做"已发布课程删除需二次审批" |
| 8 | 课程名唯一性 | 不强制全局唯一（同名课程可并存，ID 区分） | 不做重名校验报错 |
| 9 | 删除确认 | 前端弹窗承担，后端不校验学习进度 | 不做回收站 |
| 10 | 章节 sort 空洞 | 删除后不重排（按剩余 sort 排序展示） | 不做删除后全量重编号 |
| 11 | required 默认值 | 默认 true（教师忘配按必学算，进度保守） | 默认 false 会漏算 |
| 12 | 配置变更追溯 | 只影响后续判定，已完成不回退 | 不做"改比例后重算历史" |
| 13 | 外链 60 秒口径 | 当前绑定会话累计（刷新重计） | 不做跨会话累计、不做防多开 |
| 14 | heartbeat 可靠性 | 不做 sendBeacon（带不了 Authorization），丢一拍可接受 | 不做 WebSocket 长连接上报 |
| 15 | 无必学视频的章节 | 视为完成条件已满足（空集语义） | 不做"空章节不计分母"（口径不稳定） |
| 16 | 课程学习页 | 单页（目录 + 播放区切换） | 不拆章节子路由、不做续播记忆（watchedSeconds 仅展示） |
| 17 | 教师编辑权 | 限创建者本人；负责班级分配的课程可看可统计不可改 | 不做课程协作编辑 |
| 18 | 学员课程列表 | 不分页（单班量小 + 全量聚合更简单） | 不做课程搜索/标签/收藏 |
| 19 | 站内视频 duration 缺失 | 添加时校验拒绝（无时长无法自动判定） | 不做"按心跳固定时长兜底"的隐式规则 |
| 20 | 外链视频转码/字幕/弹题 | 明确非目标（规格暂缓清单） | — |
