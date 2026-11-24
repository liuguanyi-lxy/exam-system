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

1. 创建数据库 `exam_system`（utf8mb4）。
2. **全量建表脚本不在仓库中**——请从现有环境导出：`mysqldump -u<user> -p --no-data exam_system > schema.sql`，在新库执行。
3. 按日期顺序执行 `exam-system-server/sql/` 下的增量脚本：
   - `2026-09-29_unique_index_optimization.sql` —— 防并发重复开考/重复点赞的唯一索引
   - `2026-11-15_exam_records_user_id.sql` —— 考试记录归属 user_id（含历史数据回填）
   - `2026-11-15_videos_source_type.sql` —— 视频外部链接来源
   - `2026-11-15_video_views_session.sql` —— 视频观看会话化统计
   - `2026-11-15_wrong_questions_favorites.sql` —— 错题本与题目收藏所需表和索引
   - `2026-11-16_learning_external_videos.sql` —— 学习类外链视频种子数据（可选，开发演示使用）
   - `2026-11-21_m1_classes_knowledge.sql` —— M1 班级、成员、课程-班级占位关系与知识点表
   - `2026-11-23_m2_courses_progress.sql` —— M2 课程、章节视频及个人学习进度表（依赖 M1）
   - `2026-11-24_m2_learning_video_sessions.sql` —— M2 个人学习会话与心跳去重（依赖上一条）
4. 增量 SQL **不可重复执行**。执行前先在目标库检查对应表/索引当前状态并备份；不确定现状时先停止核对，不要直接重跑脚本。

## 3. 后端部署

### 3.1 配置

私密配置二选一：

- **application-local.yaml**（推荐）：在 `exam-system-server/src/main/resources/` 下创建，覆盖数据源、Redis、MinIO、AI key；该文件已被 `.gitignore` 排除。
- **环境变量**：所有配置项都有 `${VAR:默认值}` 占位，直接注入即可，关键项：

| 变量 | 说明 |
| --- | --- |
| `DB_HOST` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接 |
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

启动后验证：`http://<host>:8080/api/stats/test` 返回 JSON 即正常（开发环境另可访问 `/doc.html` 看接口文档）。

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
