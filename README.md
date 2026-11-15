# 智能考试系统（exam-system）

线上自主学习与在线考试平台：视频学习、在线考试、AI 智能判卷、成绩回看与排行榜。

## 功能

**学习者端**

- 注册登录，进入首页学习入口
- 视频中心：分类浏览、播放、投稿、点赞
- 在线考试：考试列表、多题型作答（单选/多选/判断/简答/编程）、答题卡与倒计时、交卷
- 成绩回看：AI 逐题反馈、历史记录、按试卷统计、排行榜

**管理端**

- 题库与分类管理、Excel 批量导入、AI 生成题目
- 手动组卷 / AI 组卷、试卷发布、考试记录管理
- 视频与视频分类管理、投稿审核
- 公告、轮播内容与数据统计

**判卷**：客观题本地判分；简答/编程题调用 OpenAI 兼容 AI 接口并行判卷，支持换供应商（DeepSeek、Kimi 等）仅改配置。

## 技术栈

| 端 | 技术 |
| --- | --- |
| `exam-system-server` | Java 17、Spring Boot 3、MyBatis-Plus、MySQL、Redis、MinIO、OpenAI 兼容 AI 接口 |
| `exam-system-web` | Vue 3、Vite、Pinia、Vue Router、Element Plus |

## 目录结构

```
exam-system/
├── exam-system-server/   # Spring Boot 后端
├── exam-system-web/      # Vue 3 前端
├── PRODUCT.md            # 产品背景与功能范围
└── README.md
```

## 快速开始

### 后端

依赖：JDK 17、MySQL 8、Redis、MinIO、任一 OpenAI 兼容 AI 服务。

1. 初始化数据库（在 MySQL 中创建 `exam_system` 库，执行 `exam-system-server/sql/` 下的脚本）。
2. 创建 `exam-system-server/src/main/resources/application-local.yaml`，覆盖私密配置（数据库/Redis/MinIO 账号、`ai.api-key`）。所有可覆盖项见 `application.yaml` 中的 `${VAR:}` 占位符，也可直接用环境变量注入。该文件已被 `.gitignore` 排除，不会被提交。
3. 启动：

```bash
cd exam-system-server
mvn spring-boot:run          # 默认端口 8080
# 接口文档（开发环境）：http://localhost:8080/doc.html
```

### 前端

```bash
cd exam-system-web
npm install
npm run dev                  # 开发地址 http://localhost:5173
npm run build                # 生产构建
```

## 测试

```bash
cd exam-system-server
mvn test                     # JWT 与判卷比对单元测试
```

## 说明

- 仓库不含任何密钥；私密配置通过 `application-local.yaml` 或环境变量注入。
- 生产部署（依赖服务、数据库脚本、打包发布、Nginx 配置）见 [docs/DEPLOY.md](docs/DEPLOY.md)。
- AI 判卷依赖外部 AI 服务，评分结果仅供学习参考，不能替代人工复核。
- 前后端联调记录见 `exam-system-web/UI-OPTIMIZATION-NOTES.md`。
