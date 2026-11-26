# 部署指南

本文档覆盖 exam-system 的依赖服务、数据库初始化、后端打包部署与前端构建发布。

## 1. 环境依赖

| 组件 | 版本要求 | 用途 |
| --- | --- | --- |
| JDK | 17+ | 后端运行 |
| MySQL | 8.0+（5.7 可用） | 业务数据 |
| Redis | 6.x+ | 验证码/缓存（不在线时热门题目自动降级查库） |
| MinIO | 任意近期版本 | 视频与图片文件存储 |
| AI 服务 | 任一 OpenAI 兼容接口（DeepSeek / Kimi 等） | AI 组卷、简答与代码题判卷、考试总评 |
| Node.js | 18+ | 前端构建 |

## 2. 数据库初始化

**两条路径，二选一，不可混用**（M7 部署演练实测结论）：

### 路径 A：全新库初始化（推荐，M7 演练验证过）

1. 创建空库 `exam_system`（utf8mb4）。
2. 执行**全量建表 schema**：从现有环境导出，`mysqldump -u<user> -p --no-data exam_system > schema.sql`，在空库执行。该 schema 已包含下列全部增量脚本的最终结构。
3. 按需执行**数据类脚本**（含 INSERT 的种子数据）：
   - `2026-11-16_learning_external_videos.sql` —— 学习类外链视频种子（可选，演示用）
   - `2026-11-26_m7_demo_data.sql` —— M7 演示数据（演示班/账号/课程/试卷/考试，交付演示标准数据集）

> ⚠️ 全量 schema 之后**不要再执行任何增量 DDL 脚本**——会报 `Duplicate column/table` 错误（结构已存在）。增量脚本只用于路径 B。

### 路径 B：存量旧版本库升级

增量 SQL（`exam-system-server/sql/`）按日期顺序执行，**不可重复执行**，执行前先查目标库当前状态：

- `2026-09-29_unique_index_optimization.sql` —— 防并发重复开考/重复点赞的唯一索引
- `2026-11-15_exam_records_user_id.sql` —— 考试记录归属 user_id（含历史数据回填）
- `2026-11-15_videos_source_type.sql` —— 视频外部链接来源
- `2026-11-15_video_views_session.sql` —— 视频观看会话化统计
- `2026-11-15_wrong_questions_favorites.sql` —— 错题本与题目收藏所需表和索引
- `2026-11-16_learning_external_videos.sql` —— 学习类外链视频种子数据（可选，开发演示使用）
- `2026-11-21_m1_classes_knowledge.sql` —— M1 班级、成员、课程-班级占位关系与知识点表
- `2026-11-23_m2_courses_progress.sql` —— M2 课程、章节视频及个人学习进度表（依赖 M1）
- `2026-11-23_m3_exam_classes.sql` —— M3 考试分配、历史快照及活动记录唯一键（执行前核对同账号同卷活动记录预检）
- `2026-11-24_m2_learning_video_sessions.sql` —— M2 个人学习会话与心跳去重（依赖上一条）
- `2026-11-24_m4_diagnostic_reports.sql` —— M4 整卷诊断报告与逐题追问记录表
- `2026-11-25_m5_remediation.sql` —— M5 补课任务链、任务项与知识点-视频映射表

不确定现状时先停止核对，不要直接重跑脚本。

## 3. 后端部署

### 3.1 配置

私密配置二选一：

- **application-local.yaml**（推荐）：在 `exam-system-server/src/main/resources/` 下创建，覆盖数据源、Redis、MinIO、AI key；该文件已被 `.gitignore` 排除。
- **环境变量**：所有配置项都有 `${VAR:默认值}` 占位，直接注入即可，关键项：

| 变量 | 说明 |
| --- | --- |
| `DB_HOST` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接（库名默认 exam_system） |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis 连接 |
| `MINIO_ENDPOINT` / `MINIO_USERNAME` / `MINIO_PASSWORD` | MinIO 连接 |
| `AI_API_KEY` / `AI_BASE_URL` / `AI_MODEL` | AI 服务（见 application.yaml 内注释） |
| `JWT_SECRET` | **生产必改**，默认值仅供本地演示 |
| `CORS_ALLOWED_ORIGINS` | 前后端分离部署时的前端来源，逗号分隔；同源部署无需配置 |
| `KNIFE4J_PRODUCTION` | 生产置 `true` 关闭接口文档页面 |

### 3.2 打包与运行

```bash
cd exam-system-server
mvn clean package -DskipTests
# 产物 target/exam-system-server-0.0.1-SNAPSHOT.war（Spring Boot 可执行 war）
java -jar target/exam-system-server-0.0.1-SNAPSHOT.war --server.port=8080
```

> ⚠️ **M7 演练发现的打包陷阱**：`src/main/resources/application-local.yaml`（gitignored）会被 `mvn package` 一并打进 war。如果它存在，且未显式指定 profile，启动会用 `local` profile 的硬编码数据源，**绕过全部环境变量**。生产部署二选一：
> - 打包前把 `application-local.yaml` 移出 `src/main/resources/`；
> - 启动时显式 `--spring.profiles.active=default` 并注入下表环境变量（库名可用 `DB_NAME` 覆盖）。
>
> 另注意 `DB_HOST` 默认值是 `localhost`——连远程 MySQL 必须显式传入，否则报 `Access denied for 'root'@'localhost'`。

启动后验证：`http://<host>:8080/api/stats/test` 返回 JSON 即正常（开发环境另可访问 `/doc.html` 看接口文档）。`JWT_SECRET` 未配置或不足 32 字符将**拒绝启动**（设计如此）。

## 4. 前端构建与发布

```bash
cd exam-system-web
npm install
npm run build        # 产物 dist/
```

- **同源部署（推荐）**：将 `dist/` 内容放到 Nginx 站点根，`/api`、`/files` 反代到后端 8080，无需 CORS 配置：

```nginx
server {
    listen 80;
    root /var/www/exam-system;
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;   # Vue Router history 模式回退
    }
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    location /files/ {
        proxy_pass http://127.0.0.1:8080;
    }
}
```

- **分离部署**：前端接口地址通过 `VITE_API_BASE_URL` 指向后端（`src/utils/request.js` 读取），并在后端配置 `CORS_ALLOWED_ORIGINS` 放行前端来源。

## 5. 上线检查清单

- [ ] `JWT_SECRET` 已配置为至少 32 个字符的强随机值（未配置将拒绝启动）
- [ ] `KNIFE4J_PRODUCTION=true`
- [ ] AI key 通过环境变量或 local 配置注入，未出现在任何提交里
- [ ] 五个必需 SQL 增量脚本已按顺序执行；学习类外链视频脚本按需执行
- [ ] MinIO 桶可写（首次上传会自动建桶并设置公共读）
- [ ] 视频外链功能上线前确认目标平台（B 站 / YouTube）的内嵌播放未被网络策略拦截
- [ ] AI 判卷依赖外部服务，评分结果仅供学习参考，成绩复核需人工确认

## 6. 备份与恢复

### 6.1 MySQL

```bash
# 全量备份（含数据与结构）
mysqldump -u<user> -p --single-transaction --default-character-set=utf8mb4 exam_system > backup_$(date +%F).sql

# 恢复到新库
mysql -u<user> -p -e "create database if not exists exam_system default charset utf8mb4"
mysql -u<user> -p exam_system < backup_2026-11-26.sql
```

- 恢复后核对：表数量（36 张）、`users` 角色分布、最新 `exam_records` 日期。
- 有 binlog 的环境可加做增量恢复；演示环境全量日备足够。

### 6.2 MinIO（视频与图片文件）

- 数据目录备份：MinIO 的数据盘目录整体快照/复制（桶名见上传配置，首桶自动创建）。
- 只备份数据库不备份 MinIO 会导致课件视频链接失效——两者必须同窗口备份。

## 7. M7 新机器部署演练记录（2026-11-26，本机模拟）

按路径 A 在空库 `exam_system_demo` 完整走通：建库 → schema 导出 → 数据脚本 → `mvn clean package` → war 启动（`--spring.profiles.active=default` + 环境变量）→ 三角色 API 冒烟全过（学员考试交卷自动判卷 100 分、教师教学统计与班级对比、管理员班级列表）。

演练踩坑（均已回写上文）：
1. 全量 schema 之后执行增量 DDL 全部 Duplicate → 确立路径 A/B 二分流程。
2. war 打包把 gitignored 的 application-local.yaml 带进去且默认激活 local profile → 绕过环境变量连到主库；生产必须显式 profile 或移除 local.yaml。
3. `DB_HOST` 缺省 localhost 导致 `Access denied for 'root'@'localhost'` → 文档强调必传。
4. `JWT_SECRET` 缺失拒绝启动（符合安全基线）；登录失败限流按账号+IP 记 Redis，冒烟脚本注意不要触发。
