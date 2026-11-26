-- =============================================================
-- M7 演示数据（2026-11-26）—— 一次性执行，严禁重复执行！
--
-- 用途：新机器部署演练 / 交付演示的标准数据集（交付形态：空库 + 全量 schema
--       + sql/ 目录 12 个增量脚本 + 本脚本）。
-- 前置依赖（必须按顺序全部执行完毕）：
--   1. 全量建表 schema（mysqldump --no-data 从现有环境导出）
--   2. 2026-09-29_unique_index_optimization.sql
--   3. 2026-11-15_exam_records_user_id.sql
--   4. 2026-11-15_videos_source_type.sql
--   5. 2026-11-15_video_views_session.sql
--   6. 2026-11-15_wrong_questions_favorites.sql
--   7. 2026-11-16_learning_external_videos.sql（可选，演示外链视频）
--   8. 2026-11-21_m1_classes_knowledge.sql
--   9. 2026-11-23_m2_courses_progress.sql
--  10. 2026-11-23_m3_exam_classes.sql
--  11. 2026-11-24_m2_learning_video_sessions.sql
--  12. 2026-11-24_m4_diagnostic_reports.sql
--  13. 2026-11-25_m5_remediation.sql
--
-- 演示场景：M7演示班（1 教师 + 3 学员）+ Java基础课程（1 章节 1 视频）
--           + M7演示考试（5 题 100 分，60 分及格，分配到演示班）
-- 账号密码统一 123456（BCrypt 哈希与现有种子账号一致）：
--   demo_admin / demo_teacher / demo_student1 / demo_student2 / demo_student3
-- 冒烟路径：demo_student1 参加考试交卷出分 → demo_teacher 教学统计页看班级效果
-- 说明：不预置考试记录/补课数据，成绩与补课由冒烟时真实操作产生，保证链路真实。
-- 目标库约定：仅在本地演练库（如 exam_system_demo）执行；ID 使用 9001+ 段避免与
--           现有环境冲突。不可执行到共享云端库。
-- =============================================================

-- 1. 账号（密码 123456，BCrypt 哈希取自现有种子账号）
INSERT INTO users (id, username, password, role, real_name, status, is_deleted) VALUES
(9001, 'demo_admin',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'ADMIN',   '演示管理员', 'ACTIVE', 0),
(9002, 'demo_teacher',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'TEACHER', '演示教师',   'ACTIVE', 0),
(9003, 'demo_student1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'STUDENT', '演示学员一', 'ACTIVE', 0),
(9004, 'demo_student2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'STUDENT', '演示学员二', 'ACTIVE', 0),
(9005, 'demo_student3', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'STUDENT', '演示学员三', 'ACTIVE', 0);

-- 2. 班级与成员（教师带班 + 3 学员）
INSERT INTO classes (id, name, direction, start_date, end_date, status, is_deleted) VALUES
(901, 'M7演示班', 'Java基础', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'ENABLED', 0);

INSERT INTO class_members (id, class_id, user_id, role_in_class) VALUES
(9001, 901, 9002, 'TEACHER'),
(9002, 901, 9003, 'STUDENT'),
(9003, 901, 9004, 'STUDENT'),
(9004, 901, 9005, 'STUDENT');

-- 3. 课程 / 章节 / 视频（站内占位视频，本地演示 MinIO 或外链均可替换）
INSERT INTO courses (id, name, description, status, created_by, is_deleted) VALUES
(901, 'M7演示课程', '面向对象基础：封装、继承与多态', 'PUBLISHED', 9001, 0);

INSERT INTO course_chapters (id, course_id, title, description, sort, is_deleted) VALUES
(901, 901, '第一章 面向对象入门', '封装/继承/多态基本概念', 1, 0);

INSERT INTO videos (id, title, description, file_url, source_type, duration, status, user_id) VALUES
(901, '面向对象基础概念', '什么是封装、继承与多态（演示占位视频）', '/files/demo/oop-intro.mp4', 0, 480, 1, 9002);

INSERT INTO chapter_videos (id, chapter_id, video_id, required, completion_ratio, sort) VALUES
(901, 901, 901, 1, 70, 1);

-- 4. 知识点与题目分类
INSERT INTO categories (id, name, parent_id, sort, is_deleted) VALUES
(901, 'Java基础-演示', 0, 1, 0);

INSERT INTO knowledge_points (id, name, description, sort) VALUES
(901, '封装与访问控制', '访问修饰符、getter/setter、数据隐藏', 1),
(902, '继承与多态', 'extends、方法重写、动态绑定', 2);

-- 5. 题库：2 单选 + 1 多选 + 1 判断 + 1 简答（每题卷面 20 分，总分 100）
INSERT INTO questions (id, title, type, multi, category_id, difficulty, score, analysis, is_deleted) VALUES
(9001, '下列哪一个不是面向对象的三大基本特征？', 'CHOICE', 0, 901, 'EASY', 20,
        '面向对象三大特征是封装、继承、多态；"过程"是面向过程编程的概念。', 0),
(9002, 'Java 中实现运行时多态的主要方式是？', 'CHOICE', 0, 901, 'MEDIUM', 20,
        '运行时多态通过方法重写（Override）实现，父类引用指向子类对象时调用子类实现。', 0),
(9003, '下列关于 private 访问修饰符的说法正确的是？（多选）', 'CHOICE', 1, 901, 'MEDIUM', 20,
        'private 修饰的成员仅本类可见；它不能直接修饰顶层类，但可以修饰内部类。', 0),
(9004, 'Java 的类之间支持多重继承。（判断）', 'JUDGE', 0, 901, 'EASY', 20,
        'Java 类是单继承的（一个类只能有一个直接父类），多继承通过接口实现。', 0),
(9005, '简答题：请用自己的话解释"封装"，并说明它的一个好处。', 'TEXT', 0, 901, 'MEDIUM', 20,
        '封装是把数据和方法绑定在类中并隐藏内部细节，仅通过公开接口访问。好处：降低耦合、保护数据。', 0);

INSERT INTO question_choices (id, question_id, content, is_correct, sort, is_deleted) VALUES
(9001, 9001, '封装', 0, 1, 0),
(9002, 9001, '继承', 0, 2, 0),
(9003, 9001, '多态', 0, 3, 0),
(9004, 9001, '过程', 1, 4, 0),
(9005, 9002, '方法重写（Override）', 1, 1, 0),
(9006, 9002, '方法重载（Overload）', 0, 2, 0),
(9007, 9002, '构造函数注入', 0, 3, 0),
(9008, 9002, '静态导入', 0, 4, 0),
(9009, 9003, 'private 成员仅能在本类中访问', 1, 1, 0),
(9010, 9003, 'private 字段通常配合公开 getter/setter 使用', 1, 2, 0),
(9011, 9003, 'private 可以直接修饰顶层类', 0, 3, 0),
(9012, 9003, 'private 修饰的成员子类不能直接访问', 1, 4, 0);

INSERT INTO question_answers (id, question_id, answer, keywords, is_deleted) VALUES
(9001, 9001, 'D', NULL, 0),
(9002, 9002, 'A', NULL, 0),
(9003, 9003, 'A,C,D', NULL, 0),
(9004, 9004, 'FALSE', NULL, 0),
(9005, 9005, '封装是把对象的属性和实现细节隐藏在类内部，只通过公开的方法访问和操作数据，外界不需要关心内部实现。',
        '隐藏,内部,访问,接口,数据', 0);

-- 题目-知识点标注（补课任务链依赖标注完成度）
INSERT INTO question_knowledge_points (id, question_id, knowledge_point_id) VALUES
(9001, 9001, 902),
(9002, 9002, 902),
(9003, 9003, 901),
(9004, 9004, 902),
(9005, 9005, 901);

-- 6. 试卷（5 题 × 20 分 = 100 分）
INSERT INTO paper (id, name, description, status, total_score, question_count, duration, is_deleted) VALUES
(901, 'M7演示试卷', '面向对象基础演示卷', 'PUBLISHED', 100.0, 5, 60, 0);

INSERT INTO paper_question (id, paper_id, question_id, score, is_deleted) VALUES
(9001, 901, 9001, 20.0, 0),
(9002, 901, 9002, 20.0, 0),
(9003, 901, 9003, 20.0, 0),
(9004, 901, 9004, 20.0, 0),
(9005, 901, 9005, 20.0, 0);

-- 7. 考试并分配到演示班（不限制起止时间窗口；学员可考 1 次）
INSERT INTO exams (id, name, description, duration, pass_score, total_score, question_count, status, is_deleted) VALUES
(901, 'M7演示考试', '面向对象基础阶段测试（演示）', 60, 60, 100, 5, 'PUBLISHED', 0);

INSERT INTO exam_classes (id, exam_id, class_id, max_attempts, status, is_deleted) VALUES
(9001, 901, 901, 1, 'ENABLED', 0);
