-- ============================================================
-- 增量脚本：M2 课程、章节与视频学习（含学习进度派生）
-- 日期：2026-11-23    目标库：exam_system    要求：MySQL 5.7+
-- 作用：新建 4 张表：courses / course_chapters / chapter_videos /
--       learning_video_progress
--       M2 不改既有表、不动判卷/考试/错题本链；课程-班级分配接线
--       M1 已建的 course_classes（本脚本不触碰该表，索引已在）
-- 依赖：2026-11-21_m1_classes_knowledge.sql 已执行（classes /
--       class_members / course_classes 在）；2026-11-15 系列
--       videos.source_type 与 video_views.session_id 已执行
-- 删除策略：
--       courses / course_chapters 走 BaseEntity 逻辑删除；
--       chapter_videos 物理删除（唯一索引 uk_chapter_video 与
--       "移除后重新添加同一视频"冲突，逻辑删旧行占死唯一键，
--       先例同 M1 class_members / course_classes；纯内容配置行
--       无审计价值，不引入 M3 exam_classes 式"逻辑删+复活"流程）；
--       learning_video_progress 不逻辑删也不物理删——学习历史是
--       真实记录，课程/章节删除保留，重挂同视频不产生脏数据
-- 完成判定口径（契约 docs/api-m2-courses-progress.md 第 10 节）：
--       站内视频 watched_seconds >= ceil(duration*completion_ratio/100)
--       由 heartbeat 服务端判定；外链固定 60 秒门槛 + 显式确认；
--       章节完成/课程进度一律查询时派生计算，不落冗余表
-- 执行前请备份数据库！
-- 执行前先查目标库当前状态（前 4 条应返回 Empty set，后 3 条应各返回 1 行）：
--   SHOW TABLES LIKE 'courses';
--   SHOW TABLES LIKE 'course_chapters';
--   SHOW TABLES LIKE 'chapter_videos';
--   SHOW TABLES LIKE 'learning_video_progress';
--   SHOW TABLES LIKE 'course_classes';
--   SHOW COLUMNS FROM videos LIKE 'source_type';
--   SHOW COLUMNS FROM video_views LIKE 'session_id';
-- ============================================================

-- ------------------------------------------------------------
-- 1) 课程表：教师/管理员建课，走 BaseEntity 逻辑删除模式
--    name 不做唯一：同名课程可并存（契约决策 #8），靠 ID 区分；
--    status VARCHAR 枚举对齐 classes 表风格；发布走显式动作（默认 DRAFT）
--    created_by 是教师可编辑性的唯一依据（契约 1 节语义 2）：
--    分配给负责班级但非本人创建的课程，教师可看可统计、不可改
--    无二级索引：量级百级，PK 点查/全表扫描毫秒级；
--    keyword 模糊 LIKE '%x%' 索引帮不上、status 低基数（口径同 M1 classes）
-- ------------------------------------------------------------
CREATE TABLE courses (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100) NOT NULL COMMENT '课程名称，≤100字；不做唯一（同名课程可并存，决策表#8）',
    description VARCHAR(500) NULL COMMENT '课程描述，可空，≤500字',
    cover_url   VARCHAR(500) NULL COMMENT '封面图片URL（复用 FileController /files 的 MinIO 上传通道），可空',
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT-草稿 PUBLISHED-已发布；发布后对已分配班级学员可见，切回草稿学员入口立即消失（进度数据保留）',
    created_by  BIGINT       NOT NULL COMMENT '创建人 users.id（教师/管理员，创建接口必有登录态）；教师可编辑性的唯一依据，负责班级分配的课程可看可统计不可改',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除（BaseEntity @TableLogic）；删除级联逻辑删章节、物理删 chapter_videos 与 course_classes 分配行，学员学习进度保留',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表（逻辑删除；教师可编辑性以 created_by 为唯一依据）';

-- ------------------------------------------------------------
-- 2) 课程章节表：课程下按 sort 升序学习，走 BaseEntity 逻辑删除模式
--    sort 新增自动置底（服务层取当前最大值+1），上下移交换相邻值，
--    删除后不重排（sort 空洞，契约决策 #10，按剩余章节排序展示）；
--    先后关系如 sort 唯一性不依赖数据库约束（应用层交换是两次 UPDATE，
--    唯一约束反而会让交换中途撞键）
--    无二级索引：章节总量百级，按 course_id 过滤全扫毫秒级；
--    契约 11.2"建议 (course_id, sort)"不采纳，演进条件：课程×章节过千
--    再补 KEY idx_chapter_course_sort(course_id, sort)（口径同 M1
--    knowledge_points 不建 sort 索引）
-- ------------------------------------------------------------
CREATE TABLE course_chapters (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id   BIGINT       NOT NULL COMMENT '课程ID，关联 courses.id',
    title       VARCHAR(100) NOT NULL COMMENT '章节标题，≤100字',
    description VARCHAR(500) NULL COMMENT '章节说明，可空',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前；新增自动置底（当前最大sort+1），上下移交换相邻章节的sort，删除后不重排（sort空洞）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除（BaseEntity @TableLogic）；删除级联物理删其 chapter_videos，学员学习进度保留（progress 按 video_id 关联，与章节行解耦）',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程章节表（逻辑删除；删除后 sort 不重排，按剩余章节排序展示）';

-- ------------------------------------------------------------
-- 3) 章节视频表：章节与视频的关联行（多对多），物理删除
--    video_id 必填 NOT NULL：EXIST（选视频中心已有）与 EXTERNAL（代建
--    videos 行 source_type=1）两种来源最终都落到一行 videos，
--    不存在"无视频的章节视频行"；完成通道由 videos.source_type 决定
--    （站内=heartbeat 自动判定，外链=60 秒门槛+显式确认），与添加方式正交
--    物理删除（结论）：移除后重新添加同视频是常规操作，唯一索引
--    uk_chapter_video 会被逻辑删旧行占死（先例同 M1 class_members）；
--    EXTERNAL 代建的 videos 行在移除时保留不删（视频中心资产，
--    可能被其他章节引用）
--    uk_chapter_video 三合一：重复添加唯一兜底（6.2 报"该视频已在本章节中"，
--    不幂等）、按章节列视频/统计 videoCount（最左前缀 chapter_id）、
--    8.1 统计的 chapter_id IN 批查
--    契约 11.3"建议 (chapter_id, sort)"不采纳：uk 前缀已覆盖过滤方向，
--    单章节几行 filesort 无感；required 默认必学（教师忘配按必学算，
--    进度口径保守，决策表 #11）；completion_ratio 仅站内视频生效，
--    外链完成走固定 60 秒门槛忽略本值
-- ------------------------------------------------------------
CREATE TABLE chapter_videos (
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    chapter_id       BIGINT      NOT NULL COMMENT '章节ID，关联 course_chapters.id',
    video_id         BIGINT      NOT NULL COMMENT '视频ID，关联 videos.id；EXIST 选已有 / EXTERNAL 代建（source_type=1, status=1, duration=NULL, uploader_type=2, uploader_name=教师姓名），见脚本头说明',
    required         TINYINT     NOT NULL DEFAULT 1 COMMENT '必学标记：1-必学 0-选学，默认必学（忘配按必学算，进度保守）；章节完成=必学视频全部完成，无必学视频的章节视为已满足完成条件（空集语义，决策表#15）',
    completion_ratio INT         NOT NULL DEFAULT 70 COMMENT '完成比例（百分比整数1-100）：站内视频 watched_seconds >= ceil(duration*ratio/100) 判定完成；仅站内生效，外链固定60秒门槛忽略本值；修改只影响后续判定，已完成进度不回退（决策表#12）',
    sort             INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前；新增自动置底，上下移交换同一章节内相邻视频的sort',
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chapter_video (chapter_id, video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节视频表（章节×视频关联，物理删除——移除后可重新添加同视频不占唯一键）';

-- ------------------------------------------------------------
-- 4) 学习进度表：user+video 唯一，学习历史永久保留（不逻辑删不物理删）
--    这张表存什么（结论）：累计有效学习秒数 watched_seconds（完成判定
--    输入 + 7.3 展示值）、完成时间 completed_at（事实记录 + 未来错题本/
--    诊断关联"学过什么"的锚点）、当前活跃会话 session_id（外链 60 秒
--    门槛按"当前绑定会话"校验，契约 7.5/决策 #13）、状态落点 status
--    （COMPLETED 后冻结，避免每次查询重算视频级完成）；
--    章节完成/课程进度不落本表，一律查询时派生计算（契约决策 #2）
--    watched_seconds 跨会话累加（未完成时每拍 +min(增量,60)），置
--    COMPLETED 后冻结不再累加（重看不重复计进度，验收4）
--    session_id 与 video_views.session_id 同源命名，每次心跳重绑为
--    最新上报会话（决策 #13"刷新重计"成立的必要条件）
--    chapter_video_id 仅作首学溯源：同一视频可挂多个章节而进度按
--    user+video 唯一，本列记录首次学习的章节视频行；一切完成判定与
--    统计查询不依赖它（悬挂引用不影响正确性）
--    uk_progress_user_video 三合一：进度行唯一（规格明文 user+video）、
--    heartbeat 点查（最左前缀等值）、8.1 统计的 user_id IN 批查；
--    全部查询方向恒以 user_id 打头，无需 video_id 反查索引
--    update_time 双职责：8.1 教师统计 lastLearnTime 的数据源 +
--    防刷"服务端间隔折算"的上次心跳时间基准（设计文档 4.2 防线4）
-- ------------------------------------------------------------
CREATE TABLE learning_video_progress (
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT      NOT NULL COMMENT '学员 users.id（登录态从 token 解析，路径不带 userId）',
    video_id         BIGINT      NOT NULL COMMENT '视频ID，关联 videos.id',
    chapter_video_id BIGINT      NULL COMMENT '首次触发学习的章节视频行 chapter_videos.id，仅作学习来源溯源参考；同一视频可挂多个章节而进度按 user+video 唯一，记录首学位置；一切完成判定与统计查询不依赖本列',
    status           VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '学习状态：IN_PROGRESS-学习中 COMPLETED-已完成；完成判定由 heartbeat 服务端实时计算，本列只是计算结果落点，不存在独立的章节完成表（契约决策#2）',
    watched_seconds  INT         NOT NULL DEFAULT 0 COMMENT '累计有效学习秒数：未完成时每拍累加 min(增量,60)（跨会话累加）；置 COMPLETED 后冻结不再累加（重看不重复计进度）；站内完成门槛=ceil(duration*completion_ratio/100)，外链固定60秒仅作展示',
    session_id       VARCHAR(36) NULL COMMENT '当前活跃观看会话（与 video_views.session_id 同源命名）；每次心跳重绑为最新上报会话，外链完成确认按本会话在 video_views 的累计 view_duration >= 60 校验（刷新学习页生成新会话、停留重计，决策表#13）',
    completed_at     DATETIME    NULL COMMENT '完成时间；置 COMPLETED 时写入且不再变更，是未来错题本/诊断类功能关联"学过什么"的锚点',
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次学习时间',
    update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近学习时间（心跳即更新）；8.1 教师统计 lastLearnTime 取本列最大值，同时作为防刷服务端间隔折算的基准',
    PRIMARY KEY (id),
    UNIQUE KEY uk_progress_user_video (user_id, video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习进度表（user+video 唯一；不逻辑删不物理删——课程/章节删除保留学习历史，重挂同视频不产生脏数据）';

-- 验证：
-- 4 张新表应各返回一行建表语句：
-- SHOW CREATE TABLE courses;
-- SHOW CREATE TABLE course_chapters;
-- SHOW CREATE TABLE chapter_videos;
-- SHOW CREATE TABLE learning_video_progress;
-- 课程-班级分配接线前提（M1 已建，应各返回 1 行，本脚本不重复创建）：
-- SHOW INDEX FROM course_classes WHERE Key_name = 'uk_course_class';
-- SHOW INDEX FROM course_classes WHERE Key_name = 'idx_course_class_class';
-- 视频会话索引（外链完成确认的点查依据，M2 复用不新建）：
-- SHOW INDEX FROM video_views WHERE Key_name = 'idx_video_views_session';
-- 两类核心查询的执行计划（把 1 换成真实用户/章节ID）：
-- heartbeat 进度点查应走 uk_progress_user_video（type=const/eq_ref）：
-- EXPLAIN SELECT * FROM learning_video_progress WHERE user_id = 1 AND video_id = 1;
-- 外链确认按会话查累计时长应走 idx_video_views_session（type=ref）：
-- EXPLAIN SELECT view_duration FROM video_views WHERE session_id = '550e8400-e29b-41d4-a716-446655440000';
-- 章节视频列表应走 uk_chapter_video 最左前缀（type=ref）：
-- EXPLAIN SELECT * FROM chapter_videos WHERE chapter_id = 1;
