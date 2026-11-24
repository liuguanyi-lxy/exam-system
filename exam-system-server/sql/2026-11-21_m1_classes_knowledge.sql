-- ============================================================
-- 增量脚本：M1 班级与成员管理 + 知识点管理
-- 日期：2026-11-21    目标库：exam_system    要求：MySQL 5.7+
-- 作用：新建 5 张表：classes / class_members / course_classes /
--       knowledge_points / question_knowledge_points
--       M1 不改既有表、不动判卷/考试链；course_classes 仅建表（M2 接线）
-- 删除策略：classes 走 BaseEntity 逻辑删除；其余 4 张物理删除
--       （均带唯一索引且存在"删除后重建/再入班"的正常操作，
--       逻辑删除旧行占唯一键必撞 DuplicateKey，先例同 question_favorites）
-- 执行前请备份数据库！
-- 执行前先查目标库当前状态（5 张表均应返回 Empty set）：
--   SHOW TABLES LIKE 'classes';
--   SHOW TABLES LIKE 'class_members';
--   SHOW TABLES LIKE 'course_classes';
--   SHOW TABLES LIKE 'knowledge_points';
--   SHOW TABLES LIKE 'question_knowledge_points';
-- ============================================================

-- ------------------------------------------------------------
-- 1) 班级表：管理员管理域，走 BaseEntity 逻辑删除模式
--    name 不做唯一：跨期复用班名是真实需求（如"Java 培训一班"2026/2027 届），
--    重名靠列表中的培训方向 + 起止时间区分，量级小不建二级索引
--    start_date/end_date 可空表示未定；先后关系由应用层校验（不依赖 CHECK，保持 5.7 兼容）
-- ------------------------------------------------------------
CREATE TABLE classes (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100) NOT NULL COMMENT '班级名称，如：Java 培训一班；不做唯一，重名靠方向与起止时间区分',
    direction   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '培训方向，如：Java后端 / Web前端',
    start_date  DATE         NULL COMMENT '开班日期，NULL 表示未定',
    end_date    DATE         NULL COMMENT '结班日期，NULL 表示未定；start_date <= end_date 由应用层校验',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用 DISABLED-禁用；数据范围仍以成员关系为准，禁用只挡管理侧操作与展示',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除（BaseEntity @TableLogic）',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表（管理员管理，逻辑删除）';

-- ------------------------------------------------------------
-- 2) 班级成员表：一人可入多班，唯一约束 (class_id, user_id, role_in_class)
--    物理删除：移除成员后重新入班是正常操作，逻辑删除旧行会占死唯一键；
--    历史归属不依赖本表（M3 会把班级关联落到 exam_records 列上），无审计需求。
--    唯一索引三合一：并发入班幂等兜底（配合后端 DuplicateKeyException 转"已在班"）、
--    "该教师是否负责班级X"点查（最左前缀 class_id,user_id）、按 class_id 前缀列成员
--    idx_member_user：数据范围层反查方向（某人所属/负责哪些班级），
--    唯一索引最左前缀是 class_id 帮不上，role_in_class 过滤在少量行上回表完成
-- ------------------------------------------------------------
CREATE TABLE class_members (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    class_id      BIGINT      NOT NULL COMMENT '班级ID，关联 classes.id',
    user_id       BIGINT      NOT NULL COMMENT '成员用户ID，关联 users.id',
    role_in_class VARCHAR(16) NOT NULL COMMENT '班内角色：STUDENT-学员 TEACHER-教师（一班可多师）',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入班时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_user_role (class_id, user_id, role_in_class),
    KEY idx_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级成员表（允许一人多班；物理删除，移除后可重新入班）';

-- ------------------------------------------------------------
-- 3) 课程-班级分配表：M1 仅建表（course_id 列），courses 表 M2 建好后再接线
--    M1 阶段无实体无读写代码，防误用仅靠唯一索引就位
--    物理删除：分配/取消分配可反复，同 class_members 理由
-- ------------------------------------------------------------
CREATE TABLE course_classes (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id   BIGINT   NOT NULL COMMENT '课程ID，关联 courses.id（courses 表 M2 建，M1 先留列不接线）',
    class_id    BIGINT   NOT NULL COMMENT '班级ID，关联 classes.id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_class (course_id, class_id),
    KEY idx_course_class_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-班级分配表（M1 仅建表，M2 接线；物理删除）';

-- ------------------------------------------------------------
-- 4) 知识点表：平铺列表 + 排序，不做树形
--    name 唯一（结论）：平铺全局字典，重名会让题目标注与 M4 诊断的
--    知识点得分率无法聚合（两个"多线程"各算各的）；撞唯一由后端转友好提示
--    物理删除：删除前由后端校验引用（存在题目标注则拒绝删除），无引用才删行；
--    唯一索引与逻辑删除的冲突（删后同名重建撞键）由此规避
--    列表按 sort 全量返回，量级小不建额外索引
-- ------------------------------------------------------------
CREATE TABLE knowledge_points (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100) NOT NULL COMMENT '知识点名称，全局唯一（重名会破坏 M4 按知识点聚合的统计口径）',
    description VARCHAR(500) NULL COMMENT '知识点说明，可空',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前，管理页可调',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_kp_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表（平铺列表；物理删除，有题目标注引用时后端拒绝删除）';

-- ------------------------------------------------------------
-- 5) 题目-知识点标注表：多对多，question_id + knowledge_point_id 唯一
--    created_by 记录标注人（结论）：M4 诊断结论依赖标注质量，出问题能定位到人；
--    可空——题目批量导入/无登录上下文的补标注场景不填
--    物理删除：标注可反复增删，同上唯一索引理由
--    删除题目时本表行残留（无外键，JOIN 过滤自然失效），口径同错题本对已删题的处理，不预做清理
-- ------------------------------------------------------------
CREATE TABLE question_knowledge_points (
    id                 BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    question_id        BIGINT   NOT NULL COMMENT '题目ID，关联 questions.id',
    knowledge_point_id BIGINT   NOT NULL COMMENT '知识点ID，关联 knowledge_points.id',
    created_by         BIGINT   NULL COMMENT '标注人 users.id（教师/管理员）；系统批量补标注无登录上下文时为空',
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标注时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qkp_question_kp (question_id, knowledge_point_id),
    KEY idx_qkp_kp (knowledge_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-知识点标注表（多对多；物理删除，标注可反复增删）';

-- 验证：
-- 5 张新表应各返回一行建表语句：
-- SHOW CREATE TABLE classes;
-- SHOW CREATE TABLE class_members;
-- SHOW CREATE TABLE course_classes;
-- SHOW CREATE TABLE knowledge_points;
-- SHOW CREATE TABLE question_knowledge_points;
-- 数据范围层两类查询的执行计划（把 1 换成真实用户/班级ID）：
-- 反查"某人负责的班级"应走 idx_member_user：
-- EXPLAIN SELECT class_id FROM class_members WHERE user_id = 1 AND role_in_class = 'TEACHER';
-- "班级X的学员集合"应走 uk_class_user_role（type=ref）：
-- EXPLAIN SELECT user_id FROM class_members WHERE class_id = 1 AND role_in_class = 'STUDENT';
-- 教师视角考试记录过滤复用既有索引（应返回 1，本脚本不重复创建）：
-- SHOW INDEX FROM exam_records WHERE Key_name = 'idx_exam_records_user';
