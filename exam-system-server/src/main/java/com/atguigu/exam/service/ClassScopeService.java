package com.atguigu.exam.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 班级数据范围层服务（M1 核心基建，M2/M3 全部接口复用）
 *
 * 语义约定（设计文档第 4 节）：
 * 1. 教师负责的班级集合以 class_members 为准（role_in_class='TEACHER'），与 users.role 无关；
 *    分配教师接口会校验目标用户 users.role='TEACHER'，保证常态下两者一致；
 * 2. 集合查询不过滤 classes.status：禁用班级的历史数据仍归属可见（验收标准 4）；
 * 3. 集合查询不 JOIN classes.is_deleted：删除班级由 ClassService.deleteClass 级联物理清理
 *    该班 class_members 行兜底（成员表本就物理删）；
 * 4. M1 直接查库不引入 Redis 缓存：权限数据缓存失效出错是安全事故，单人班级数个位数毫秒级。
 */
public interface ClassScopeService {

    /**
     * 教师负责的班级ID集合：class_members WHERE user_id=? AND role_in_class='TEACHER'，按 class_id 升序
     */
    List<Long> getTeacherClassIds(Long userId);

    /**
     * 学员所属的班级ID集合（一人多班可返回多个），按 class_id 升序
     */
    List<Long> getStudentClassIds(Long userId);

    /**
     * 点查：该教师是否负责班级 X（走 uk_class_user_role 最左前缀等值点查）
     */
    boolean isTeacherOfClass(Long userId, Long classId);

    /**
     * 一组班级的学员 userId 去重集合（一人多班去重），按 user_id 升序。
     * 调用方拿到的集合可能为空（教师无负责班级），必须按空集合哨兵防呆拼接 IN 条件：
     * <pre>
     * Set&lt;Long&gt; memberIds = classScopeService.getStudentUserIds(classScopeService.getTeacherClassIds(userId));
     * if (memberIds.isEmpty()) {
     *     wrapper.eq(ExamRecord::getUserId, -1L);   // 哨兵：user_id 不可能为 -1，天然空结果
     * } else {
     *     wrapper.in(ExamRecord::getUserId, memberIds);
     * }
     * </pre>
     * 禁止空集合直传 in：MyBatis-Plus 对空集合 in 的行为因版本而异，
     * 不拼条件会退化为全量，等于把全部数据暴露给无班级教师（权限漏洞）。
     */
    Set<Long> getStudentUserIds(Collection<Long> classIds);
}
