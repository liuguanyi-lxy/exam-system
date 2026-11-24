package com.atguigu.exam.service;

import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.vo.ClassMemberVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.atguigu.exam.vo.ClassmateVo;
import com.atguigu.exam.vo.MyClassVo;
import com.atguigu.exam.vo.StudentImportResultVo;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 班级管理 Service
 * 班级 CRUD/禁用、成员增删、分配教师、Excel 批量导入、学员"我的班级"视图
 * （设计文档第 8 节：Wrapper + 批量 save，删除班级级联清成员；M1 零 XML）
 */
public interface ClassService extends IService<Clazz> {

    /**
     * 班级分页列表。ADMIN 全量传 scopeClassIds=null；TEACHER 传负责班级集合，
     * 空集合返回空页（禁止空集合直传 in）。
     *
     * @param page          分页对象
     * @param name          名称模糊筛选（可空）
     * @param status        ENABLED/DISABLED，非法值忽略（可空）
     * @param scopeClassIds 数据范围班级集合；null 表示不限（admin），空集合返回空页
     */
    Page<Clazz> pageClasses(Page<Clazz> page,
                            String name, String status, List<Long> scopeClassIds);

    /**
     * 创建班级：名称必填 ≤50 字符、与未删除班级重名校验、日期先后校验；状态固定 ENABLED。
     * @return 创建后的完整班级对象（计数为 0）
     */
    Clazz createClass(Clazz clazz);

    /**
     * 更新班级：存在性校验、名称唯一性排除自身、日期先后校验。
     * @return 更新后的完整班级对象
     */
    Clazz updateClass(Clazz clazz);

    /**
     * 启用/禁用班级：幂等；禁用只影响可见性标记，不改数据范围（验收标准 4）
     */
    void updateClassStatus(Long classId, String status);

    /**
     * 删除班级（逻辑删除）：先级联物理清理该班全部 class_members 行，再逻辑删 classes，
     * 保证数据范围层"单表查成员不 JOIN classes"的残留行不存在（设计文档 4.2/第 7 节）
     */
    void deleteClass(Long classId);

    /**
     * 班级详情：本体 + 成员计数 + 教师列表（成员走分页子资源，详情响应不膨胀）
     */
    Clazz getClassDetail(Long classId);

    /**
     * 手动添加学员（已存在用户）。已在班内（任一 roleInClass）幂等成功不重复入班。
     * @return true 表示该用户此前已在班内（幂等跳过）
     */
    boolean addStudent(Long classId, Long userId);

    /**
     * 成员分页列表（契约 5.1）。keyword 先查匹配用户ID集合再过滤成员（两步批量查防 N+1）；
     * includeSummary=true 时对当页 userId 批量聚合 exam_records（一次 group by），禁止循环 N+1。
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<ClassMemberVo> pageMembers(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ClassMemberVo> page,
            Long classId, String roleInClass, String keyword, boolean includeSummary);

    /**
     * 移除学员：仅删除该用户在本班 role_in_class='STUDENT' 记录；幂等（物理删除不占唯一键）
     */
    void removeMember(Long classId, Long userId);

    /**
     * 分配教师（可批量）：任一 userId 不存在或 users.role != 'TEACHER' 整批失败；
     * 已是本班教师的 ID 幂等跳过不计入 addedCount。
     * @return {addedCount, teacherIds}（teacherIds 为本次新增的教师ID列表）
     */
    Map<String, Object> assignTeachers(Long classId, List<Long> userIds);

    /**
     * 移除教师：仅删除 role_in_class='TEACHER' 记录；幂等
     */
    void removeTeacher(Long classId, Long userId);

    /**
     * Excel 批量导入学员（契约 5.3）：
     * 文件级错误（空文件/格式不符/超 500 行）整单拒绝不入库；行级失败不中断整批计入 failedRows；
     * 已存在用户名（不论全局角色）直接以 STUDENT 入班不改 role；不存在则自动创建账号
     * （默认密码 123456、角色 STUDENT）；入库阶段整体单事务，撞 uk_class_user_role 转"已在班"幂等成功。
     */
    StudentImportResultVo importStudents(Long classId, MultipartFile file);

    /**
     * 学员"我的班级"列表：所属全部班级（含已停用，由 status 区分），全量不分页；
     * 附班级教师（不下发 username）与学员数
     */
    List<MyClassVo> listMyClasses(Long userId);

    /**
     * 我的同学列表：STUDENT-own 校验（本人是该班 STUDENT 成员才放行，否则 403 越权）；
     * 只下发 userId/realName/joinedAt，不下发 username
     */
    List<ClassmateVo> listClassmates(Long classId, Long userId);
}
