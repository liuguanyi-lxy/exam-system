-- 学习视频外链种子数据
-- 将演示用 mp4 地址替换为课程相关的 YouTube 外链，并补充更多学习资源。
-- 外链视频不占用 MinIO 视频存储；file_url 保存第三方视频页地址。

UPDATE videos
SET title = 'Java 编程入门完整课程',
    description = '面向初学者的 Java 编程基础课程，涵盖语法、面向对象和常用开发概念。',
    file_url = 'https://www.youtube.com/watch?v=xk4_1vDrzzo',
    source_type = 1,
    cover_url = 'https://i.ytimg.com/vi/xk4_1vDrzzo/hqdefault.jpg',
    duration = 7200,
    file_size = NULL,
    tags = 'Java,编程基础,面向对象'
WHERE id = 1;

UPDATE videos
SET title = 'Python 编程入门完整课程',
    description = '从变量、流程控制到函数、文件和面向对象，适合 Python 零基础学习。',
    category_id = 3,
    file_url = 'https://www.youtube.com/watch?v=rfscVS0vtbw',
    source_type = 1,
    cover_url = 'https://i.ytimg.com/vi/rfscVS0vtbw/hqdefault.jpg',
    duration = 8880,
    file_size = NULL,
    tags = 'Python,编程基础,入门'
WHERE id = 2;

UPDATE videos
SET title = 'MySQL 数据库完整课程',
    description = '学习 MySQL 数据库、SQL 查询、表设计、关联查询和数据库基础。',
    category_id = 4,
    file_url = 'https://www.youtube.com/watch?v=7S_tz1z_5bA',
    source_type = 1,
    cover_url = 'https://i.ytimg.com/vi/7S_tz1z_5bA/hqdefault.jpg',
    duration = 10800,
    file_size = NULL,
    tags = 'MySQL,SQL,数据库'
WHERE id = 3;

UPDATE videos
SET title = 'HTML CSS 网页开发教程',
    description = '系统学习 HTML 与 CSS，掌握页面结构、布局、响应式设计和现代网页样式。',
    category_id = 5,
    file_url = 'https://www.youtube.com/watch?v=G3e-cpL7ofc',
    source_type = 1,
    cover_url = 'https://i.ytimg.com/vi/G3e-cpL7ofc/hqdefault.jpg',
    duration = 7200,
    file_size = NULL,
    tags = 'HTML,CSS,网页开发,前端'
WHERE id = 4;

UPDATE videos
SET title = 'Vue.js 前端开发入门',
    description = '学习 Vue 组件、响应式数据、事件处理和前端应用开发基础。',
    category_id = 6,
    file_url = 'https://www.youtube.com/watch?v=VeNfHj6MhgA',
    source_type = 1,
    cover_url = 'https://i.ytimg.com/vi/VeNfHj6MhgA/hqdefault.jpg',
    duration = 5400,
    file_size = NULL,
    tags = 'Vue,Vue.js,前端开发'
WHERE id = 5;

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'JavaScript 完整入门课程',
    '学习 JavaScript 核心语法、DOM、异步编程和浏览器开发基础。',
    5,
    'https://www.youtube.com/watch?v=W6NZfCO5SIk',
    1,
    'https://i.ytimg.com/vi/W6NZfCO5SIk/hqdefault.jpg',
    7200,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'JavaScript,前端,网页开发', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'JavaScript 完整入门课程');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'Spring Boot 后端开发教程',
    '使用 Spring Boot 构建 Java Web 后端，了解项目结构、接口和服务开发。',
    2,
    'https://www.youtube.com/watch?v=9SGDpanrc8U',
    1,
    'https://i.ytimg.com/vi/9SGDpanrc8U/hqdefault.jpg',
    5400,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'Java,Spring Boot,后端开发', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'Spring Boot 后端开发教程');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'Git 与 GitHub 版本控制入门',
    '掌握 Git 常用命令、分支、提交、合并和 GitHub 协作流程。',
    1,
    'https://www.youtube.com/watch?v=8JJ101D3knE',
    1,
    'https://i.ytimg.com/vi/8JJ101D3knE/hqdefault.jpg',
    4200,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'Git,GitHub,版本控制', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'Git 与 GitHub 版本控制入门');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'Docker 容器化入门教程',
    '了解 Docker 镜像、容器、网络和常用部署命令。',
    1,
    'https://www.youtube.com/watch?v=3c-iBn73dDE',
    1,
    'https://i.ytimg.com/vi/3c-iBn73dDE/hqdefault.jpg',
    5400,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'Docker,容器化,部署', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'Docker 容器化入门教程');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'React 前端开发入门',
    '学习 React 组件、状态、属性和现代前端应用开发基础。',
    5,
    'https://www.youtube.com/watch?v=bMknfKXIFA8',
    1,
    'https://i.ytimg.com/vi/bMknfKXIFA8/hqdefault.jpg',
    7200,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'React,前端开发,JavaScript', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'React 前端开发入门');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    '计算机网络基础课程',
    '了解 HTTP、TCP/IP、DNS、浏览器请求和网络通信基础。',
    1,
    'https://www.youtube.com/watch?v=qiQR5rTSshw',
    1,
    'https://i.ytimg.com/vi/qiQR5rTSshw/hqdefault.jpg',
    7200,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    '计算机网络,HTTP,TCP/IP', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = '计算机网络基础课程');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    '数据结构与算法入门',
    '学习数组、链表、栈、队列、树、图和常见算法思想。',
    1,
    'https://www.youtube.com/watch?v=8hly31xKIiA',
    1,
    'https://i.ytimg.com/vi/8hly31xKIiA/hqdefault.jpg',
    7200,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    '数据结构,算法,编程基础', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = '数据结构与算法入门');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'PostgreSQL 数据库教程',
    '学习 PostgreSQL 基础操作、SQL 查询和关系型数据库使用方法。',
    4,
    'https://www.youtube.com/watch?v=qw--VYLpxG4',
    1,
    'https://i.ytimg.com/vi/qw--VYLpxG4/hqdefault.jpg',
    5400,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'PostgreSQL,SQL,数据库', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'PostgreSQL 数据库教程');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'TypeScript 编程入门',
    '学习 TypeScript 类型、接口、泛型和现代前端项目开发。',
    5,
    'https://www.youtube.com/watch?v=30LWbarcOec',
    1,
    'https://i.ytimg.com/vi/30LWbarcOec/hqdefault.jpg',
    5400,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'TypeScript,前端,JavaScript', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'TypeScript 编程入门');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'REST API 设计与开发',
    '了解 RESTful API、HTTP 方法、状态码和接口设计实践。',
    2,
    'https://www.youtube.com/watch?v=7YcW25PHnAA',
    1,
    'https://i.ytimg.com/vi/7YcW25PHnAA/hqdefault.jpg',
    4200,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'REST API,HTTP,后端开发', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'REST API 设计与开发');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'Redis 缓存基础教程',
    '学习 Redis 数据结构、缓存使用场景和常用命令。',
    4,
    'https://www.youtube.com/watch?v=jgpVdJB2sKQ',
    1,
    'https://i.ytimg.com/vi/jgpVdJB2sKQ/hqdefault.jpg',
    3600,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'Redis,缓存,数据库', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'Redis 缓存基础教程');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'Linux 命令行基础',
    '掌握 Linux 文件、进程、权限、网络和常用命令行操作。',
    1,
    'https://www.youtube.com/watch?v=sWbUDq4S6Y8',
    1,
    'https://i.ytimg.com/vi/sWbUDq4S6Y8/hqdefault.jpg',
    5400,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'Linux,命令行,系统基础', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'Linux 命令行基础');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'Kubernetes 容器编排入门',
    '了解 Kubernetes 集群、Pod、Deployment 和服务部署基础。',
    1,
    'https://www.youtube.com/watch?v=X48VuDVv0do',
    1,
    'https://i.ytimg.com/vi/X48VuDVv0do/hqdefault.jpg',
    7200,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'Kubernetes,Docker,云原生', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'Kubernetes 容器编排入门');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'Java 数据结构与算法',
    '使用 Java 学习数组、链表、树、排序和搜索等常见算法。',
    2,
    'https://www.youtube.com/watch?v=BBpAmxU_NQo',
    1,
    'https://i.ytimg.com/vi/BBpAmxU_NQo/hqdefault.jpg',
    5400,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'Java,数据结构,算法', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'Java 数据结构与算法');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'React 实战项目教程',
    '通过项目学习 React 组件拆分、状态管理和前端页面组织。',
    5,
    'https://www.youtube.com/watch?v=Rh3tobg7hEo',
    1,
    'https://i.ytimg.com/vi/Rh3tobg7hEo/hqdefault.jpg',
    5400,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'React,前端,项目实战', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'React 实战项目教程');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'CSS Flexbox 与 Grid 布局',
    '掌握现代 CSS 布局技术，构建响应式网页界面。',
    5,
    'https://www.youtube.com/watch?v=3elGSZSWTbM',
    1,
    'https://i.ytimg.com/vi/3elGSZSWTbM/hqdefault.jpg',
    3600,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'CSS,Flexbox,Grid,前端', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'CSS Flexbox 与 Grid 布局');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    '软件工程与系统设计基础',
    '学习软件开发流程、系统设计思路、可维护性和工程实践。',
    1,
    'https://www.youtube.com/watch?v=F2FmTdLtb_4',
    1,
    'https://i.ytimg.com/vi/F2FmTdLtb_4/hqdefault.jpg',
    5400,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    '软件工程,系统设计,工程实践', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = '软件工程与系统设计基础');

INSERT INTO videos
    (title, description, category_id, file_url, source_type, cover_url, duration,
     file_size, uploader_name, uploader_type, admin_id, status, audit_admin_id,
     audit_time, view_count, like_count, tags, created_at, updated_at)
SELECT
    'Web 安全基础',
    '了解常见 Web 安全风险、认证授权、输入校验和安全开发原则。',
    1,
    'https://www.youtube.com/watch?v=AVgXqY5b3XQ',
    1,
    'https://i.ytimg.com/vi/AVgXqY5b3XQ/hqdefault.jpg',
    4200,
    NULL, '系统学习资源', 2, 1, 1, 1, NOW(), 0, 0,
    'Web安全,网络安全,开发安全', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM videos WHERE title = 'Web 安全基础');
