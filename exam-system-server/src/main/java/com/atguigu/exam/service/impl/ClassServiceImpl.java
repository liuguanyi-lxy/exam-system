package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Clazz;
import com.atguigu.exam.entity.ClassMember;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.exception.ClassScopeException;
import com.atguigu.exam.mapper.ClassMemberMapper;
import com.atguigu.exam.mapper.ClazzMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.ClassService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.vo.ClassMemberVo;
import com.atguigu.exam.vo.ClassTeacherVo;
import com.atguigu.exam.vo.ClassmateVo;
import com.atguigu.exam.vo.MyClassTeacherVo;
import com.atguigu.exam.vo.MyClassVo;
import com.atguigu.exam.vo.StudentImportResultVo;
import com.atguigu.exam.vo.StudentImportVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 班级管理 Service 实现
 *
 * 事务与幂等语义（设计文档第 6 节）：
 * - Excel 导入：解析/预检阶段整单拒绝（可预期错误不入库，无事务），
 *   入库阶段经 TransactionTemplate 整体单事务（新增账号 + 成员行原子写入）；
 * - 手动入班/分配教师：先判存给友好提示，insert 外包 try/catch(DuplicateKeyException)
 *   并发兜底为"已在班"幂等成功（撞 uk_class_user_role），模式同 question_favorites；
 * - 删除班级：级联物理清理该班 class_members 行（成员表本就物理删），再逻辑删 classes，
 *   保证 ClassScopeService 单表查成员无残留行（设计文档 4.2）。
 * 所有列表/成员查询防 N+1：一律"先查本表 → 按 ID 集合批量查关联表"两步组装。
 */
@Slf4j
@Service
public class ClassServiceImpl extends ServiceImpl<ClazzMapper, Clazz> implements ClassService {

    /** 班内角色：教师 */
    private static final String ROLE_TEACHER = "TEACHER";
    /** 班内角色：学员 */
    private static final String ROLE_STUDENT = "STUDENT";
    /** 班级状态：启用 */
    private static final String STATUS_ENABLED = "ENABLED";
    /** 班级状态：禁用 */
    private static final String STATUS_DISABLED = "DISABLED";
    /** 全局角色：教师 */
    private static final String USER_ROLE_TEACHER = "TEACHER";
    /** 导入账号默认密码（验收标准 2/5） */
    public static final String DEFAULT_PASSWORD = "123456";
    /** 单文件数据行上限（契约 5.3 处理规则 5） */
    private static final int MAX_IMPORT_ROWS = 500;
    private static final int MAX_NAME_LENGTH = 50;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ClassMemberMapper classMemberMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ExamRecordMapper examRecordMapper;
    /** 入库阶段整体单事务（预检阶段不入库不占事务） */
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ==================== 班级 CRUD ====================

    @Override
    public Page<Clazz> pageClasses(Page<Clazz> page, String name, String status, List<Long> scopeClassIds) {
        LambdaQueryWrapper<Clazz> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(name != null && !name.isBlank(), Clazz::getName, name == null ? null : name.trim());
        // status 非法值忽略该筛选条件（契约 2.5）
        wrapper.eq(STATUS_ENABLED.equals(status) || STATUS_DISABLED.equals(status), Clazz::getStatus, status);
        if (scopeClassIds != null) {
            // 教师无负责班级 → 空页返回；禁止空集合直传 in（设计文档 4.4 哨兵约定）
            if (scopeClassIds.isEmpty()) {
                page.setTotal(0);
                page.setRecords(List.of());
                return page;
            }
            wrapper.in(Clazz::getId, scopeClassIds);
        }
        wrapper.orderByDesc(Clazz::getCreateTime);
        page(page, wrapper);
        fillMemberCounts(page.getRecords());
        return page;
    }

    @Override
    public Clazz createClass(Clazz clazz) {
        String name = requireValidName(clazz.getName());
        checkClassNameUnique(name, null);
        checkDateOrder(clazz.getStartDate(), clazz.getEndDate());
        clazz.setName(name);
        clazz.setDirection(clazz.getDirection() == null ? "" : clazz.getDirection().trim());
        clazz.setStatus(STATUS_ENABLED);
        save(clazz);
        Clazz created = getById(clazz.getId());
        created.setStudentCount(0);
        created.setTeacherCount(0);
        return created;
    }

    @Override
    public Clazz updateClass(Clazz clazz) {
        Clazz exist = getById(clazz.getId());
        if (exist == null) {
            throw new RuntimeException("班级不存在");
        }
        String name = requireValidName(clazz.getName());
        checkClassNameUnique(name, clazz.getId());
        checkDateOrder(clazz.getStartDate(), clazz.getEndDate());
        clazz.setName(name);
        if (clazz.getDirection() != null) {
            clazz.setDirection(clazz.getDirection().trim());
        }
        java.time.LocalDate startDate = clazz.getStartDate();
        java.time.LocalDate endDate = clazz.getEndDate();
        clazz.setStartDate(null);
        clazz.setEndDate(null);
        // status 不随本接口变更（启用/禁用走专用接口）；非空字段覆盖更新
        clazz.setStatus(null);
        clazz.setIsDeleted(null);
        update(clazz, new LambdaUpdateWrapper<Clazz>()
                .eq(Clazz::getId, clazz.getId())
                .set(Clazz::getStartDate, startDate)
                .set(Clazz::getEndDate, endDate));
        return getById(clazz.getId());
    }

    @Override
    public void updateClassStatus(Long classId, String status) {
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new RuntimeException("status 取值非法，仅支持 ENABLED/DISABLED");
        }
        Clazz clazz = getById(classId);
        if (clazz == null) {
            throw new RuntimeException("班级不存在");
        }
        // 幂等：重复设置同值返回成功
        clazz.setStatus(status);
        updateById(clazz);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClass(Long classId) {
        Clazz clazz = getById(classId);
        if (clazz == null) {
            throw new RuntimeException("班级不存在");
        }
        // 级联物理清理成员行（教师+学员）：成员表本就物理删，
        // ClassScopeService 单表查成员不 JOIN classes，依赖此处兜底无残留（设计文档 4.2）
        classMemberMapper.delete(new LambdaQueryWrapper<ClassMember>().eq(ClassMember::getClassId, classId));
        removeById(classId);
        log.info("删除班级 {}，级联清理成员行完成", classId);
    }

    @Override
    public Clazz getClassDetail(Long classId) {
        Clazz clazz = getById(classId);
        if (clazz == null) {
            throw new RuntimeException("班级不存在");
        }
        fillMemberCounts(List.of(clazz));
        clazz.setTeachers(listTeachersOfClasses(List.of(classId)).getOrDefault(classId, List.of()));
        return clazz;
    }

    // ==================== 成员管理 ====================

    @Override
    public boolean addStudent(Long classId, Long userId) {
        if (getById(classId) == null) {
            throw new RuntimeException("班级不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!ROLE_STUDENT.equals(user.getRole())) {
            throw new IllegalArgumentException("用户 " + userId + " 不是学员角色，不能添加到班级");
        }
        // 已在班内（任一 roleInClass）→ 幂等成功，不重复入班（契约 5.2）
        if (existsMember(classId, userId, null)) {
            return true;
        }
        try {
            ClassMember member = new ClassMember();
            member.setClassId(classId);
            member.setUserId(userId);
            member.setRoleInClass(ROLE_STUDENT);
            classMemberMapper.insert(member);
            return false;
        } catch (DuplicateKeyException e) {
            // 并发重复入班撞 uk_class_user_role → 转"已在班"幂等成功（设计文档 6.3）
            log.warn("并发添加学员撞唯一索引：classId={} userId={}", classId, userId);
            return true;
        }
    }

    @Override
    public Page<ClassMemberVo> pageMembers(Page<ClassMemberVo> voPage, Long classId, String roleInClass,
                                           String keyword, boolean includeSummary) {
        if (getById(classId) == null) {
            throw new RuntimeException("班级不存在");
        }
        LambdaQueryWrapper<ClassMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassMember::getClassId, classId);
        // roleInClass 非法值忽略该筛选条件（契约 2.5）
        wrapper.eq(ROLE_STUDENT.equals(roleInClass) || ROLE_TEACHER.equals(roleInClass),
                ClassMember::getRoleInClass, roleInClass);
        // keyword（用户名/姓名模糊）：先批量查匹配用户ID集合（两步批量查），
        // 匹配为空 → 空页；禁止对成员表做跨表模糊
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getUsername, kw).or().like(User::getRealName, kw))
                    .stream().map(User::getId).toList();
            if (matchedUserIds.isEmpty()) {
                voPage.setTotal(0);
                voPage.setRecords(List.of());
                return voPage;
            }
            wrapper.in(ClassMember::getUserId, matchedUserIds);
        }
        wrapper.orderByAsc(ClassMember::getCreateTime).orderByAsc(ClassMember::getId);

        Page<ClassMember> memberPage = new Page<>(voPage.getCurrent(), voPage.getSize());
        classMemberMapper.selectPage(memberPage, wrapper);
        List<ClassMember> members = memberPage.getRecords() == null ? List.of() : memberPage.getRecords();

        List<ClassMemberVo> vos = new ArrayList<>();
        if (!members.isEmpty()) {
            // 两步批量查用户信息，防 N+1
            List<Long> userIds = members.stream().map(ClassMember::getUserId).distinct().toList();
            Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
            // 考试摘要仅属于学员成员；教师成员不能通过班级列表暴露个人考试记录
            List<Long> studentIds = members.stream()
                    .filter(member -> ROLE_STUDENT.equals(member.getRoleInClass()))
                    .map(ClassMember::getUserId).distinct().toList();
            // includeSummary=true：对当页学员一次 group by 聚合，空集合不触发无条件查询
            Map<Long, long[]> summaryMap = includeSummary && !studentIds.isEmpty()
                    ? aggregateExamSummary(studentIds) : Map.of();
            for (ClassMember member : members) {
                User user = userMap.get(member.getUserId());
                if (user == null) {
                    continue; // 用户行缺失的脏数据防御性跳过
                }
                ClassMemberVo vo = new ClassMemberVo();
                vo.setUserId(user.getId());
                vo.setUsername(user.getUsername());
                vo.setRealName(user.getRealName());
                vo.setRole(user.getRole());
                vo.setAccountStatus(user.getStatus());
                vo.setRoleInClass(member.getRoleInClass());
                vo.setJoinedAt(member.getCreateTime());
                long[] summary = summaryMap.get(member.getUserId());
                if (summary != null) {
                    vo.setExamCount((int) summary[0]);
                    vo.setLastExamTime(summary[1] > 0 ? formatEpochMillis(summary[1]) : null);
                }
                vos.add(vo);
            }
        }
        voPage.setTotal(memberPage.getTotal());
        voPage.setSize(memberPage.getSize());
        voPage.setCurrent(memberPage.getCurrent());
        voPage.setPages(memberPage.getPages());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public void removeMember(Long classId, Long userId) {
        // 仅删 STUDENT 记录；教师关系走 removeTeacher（契约 6.2，角色分离）。幂等
        classMemberMapper.delete(new LambdaQueryWrapper<ClassMember>()
                .eq(ClassMember::getClassId, classId)
                .eq(ClassMember::getUserId, userId)
                .eq(ClassMember::getRoleInClass, ROLE_STUDENT));
    }

    @Override
    public Map<String, Object> assignTeachers(Long classId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new RuntimeException("分配教师列表不能为空");
        }
        if (getById(classId) == null) {
            throw new RuntimeException("班级不存在");
        }
        // 去重保序（契约 6.1：非空且去重）
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(userIds));
        // 任一 userId 不存在或非 TEACHER 角色 → 整批失败，明确报出问题 ID（契约 6.1）
        List<User> users = userMapper.selectBatchIds(distinctIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        for (Long id : distinctIds) {
            User user = userMap.get(id);
            if (user == null) {
                throw new RuntimeException("用户 " + id + " 不存在");
            }
            if (!USER_ROLE_TEACHER.equals(user.getRole())) {
                throw new RuntimeException("用户 " + id + " 不是教师角色");
            }
        }
        // 已是本班教师的 ID → 幂等跳过
        Set<Long> existing = classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                        .eq(ClassMember::getClassId, classId)
                        .in(ClassMember::getUserId, distinctIds)
                        .eq(ClassMember::getRoleInClass, ROLE_TEACHER))
                .stream().map(ClassMember::getUserId).collect(Collectors.toSet());

        List<Long> added = new ArrayList<>();
        for (Long id : distinctIds) {
            if (existing.contains(id)) {
                continue;
            }
            try {
                ClassMember member = new ClassMember();
                member.setClassId(classId);
                member.setUserId(id);
                member.setRoleInClass(ROLE_TEACHER);
                classMemberMapper.insert(member);
                added.add(id);
            } catch (DuplicateKeyException e) {
                log.warn("并发分配教师撞唯一索引：classId={} userId={}", classId, id);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("addedCount", added.size());
        data.put("teacherIds", added);
        return data;
    }

    @Override
    public void removeTeacher(Long classId, Long userId) {
        // 仅删 TEACHER 记录；幂等（契约 6.2）
        classMemberMapper.delete(new LambdaQueryWrapper<ClassMember>()
                .eq(ClassMember::getClassId, classId)
                .eq(ClassMember::getUserId, userId)
                .eq(ClassMember::getRoleInClass, ROLE_TEACHER));
    }

    // ==================== Excel 批量导入 ====================

    @Override
    public StudentImportResultVo importStudents(Long classId, MultipartFile file) {
        if (getById(classId) == null) {
            throw new RuntimeException("班级不存在");
        }
        // ---------- 文件校验与解析阶段（不入库） ----------
        List<StudentImportVo> rows;
        try {
            rows = ExcelUtil.parseStudentExcel(file);
        } catch (IOException e) {
            log.error("学员导入 Excel 解析失败：{}", e.getMessage(), e);
            throw new RuntimeException("Excel 文件解析失败，请检查文件内容");
        }
        int totalRows = rows.size();
        // 单文件数据行上限：超出整单拒绝（契约 5.3 处理规则 5）
        if (totalRows > MAX_IMPORT_ROWS) {
            throw new RuntimeException("单次最多导入 500 行，请分批导入");
        }

        StudentImportResultVo result = new StudentImportResultVo();
        result.setTotalRows(totalRows);
        result.setFailedRows(new ArrayList<>());

        // 行级预检：用户名为空/含非法字符等 → 不中断整批，计入 failedRows（契约 5.3 处理规则 4）
        List<StudentImportVo> validRows = new ArrayList<>();
        Set<String> seenUsernames = new HashSet<>();
        for (StudentImportVo row : rows) {
            String username = row.getUsername() == null ? "" : row.getUsername().trim();
            String realName = row.getRealName() == null ? "" : row.getRealName().trim();
            String error = validateImportRow(username, realName);
            if (error != null) {
                result.getFailedRows().add(new StudentImportResultVo.FailedRow(row.getRow(), username, error));
                continue;
            }
            // Excel 内重复用户名合并为一行处理，第二行视为同一人（设计文档 6.1）
            if (!seenUsernames.add(username)) {
                continue;
            }
            row.setUsername(username);
            row.setRealName(realName.isBlank() ? username : realName);
            validRows.add(row);
        }

        // ---------- 预检阶段（一次批量查，标记新建/入班） ----------
        List<User> toCreate = new ArrayList<>();
        List<StudentImportVo> toJoin = new ArrayList<>();
        if (!validRows.isEmpty()) {
            List<String> usernames = validRows.stream().map(StudentImportVo::getUsername).toList();
            Map<String, User> existing = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .in(User::getUsername, usernames))
                    .stream().collect(Collectors.toMap(User::getUsername, Function.identity(), (a, b) -> a));
            for (StudentImportVo row : validRows) {
                User user = existing.get(row.getUsername());
                if (user == null) {
                    // 不存在 → 自动创建账号：默认密码 123456、角色 STUDENT（契约 5.3 处理规则 2）
                    User created = new User();
                    created.setUsername(row.getUsername());
                    created.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
                    created.setRealName(row.getRealName());
                    created.setRole(ROLE_STUDENT);
                    created.setStatus("ACTIVE");
                    toCreate.add(created);
                } else if (!ROLE_STUDENT.equals(user.getRole())) {
                    throw new IllegalArgumentException("账号 " + row.getUsername()
                            + " 的全局角色为 " + user.getRole() + "，不能作为学员导入");
                } else {
                    // 已存在学员账号 → 入班，不修改其 users.role（契约 5.3 处理规则 1）
                }
                toJoin.add(row);
            }
        }

        // ---------- 入库阶段（整体单事务，任何一行意外失败整体回滚） ----------
        return transactionTemplate.execute(status -> applyStudentImport(classId, toCreate, toJoin, result));
    }

    /**
     * 导入入库：批量 insert 新账号 + 逐行 insert 成员（撞 uk_class_user_role 转"已在班"幂等成功）。
     * 只由 importStudents 经 TransactionTemplate 调用，勿单独调用。
     */
    private StudentImportResultVo applyStudentImport(Long classId, List<User> toCreate,
                                                     List<StudentImportVo> toJoin,
                                                     StudentImportResultVo result) {
        int createdAccounts = 0;
        List<Long> joinedUserIds = new ArrayList<>();
        Map<String, Long> existingIdByUsername = new HashMap<>();

        for (User user : toCreate) {
            try {
                userMapper.insert(user);
                createdAccounts++;
                existingIdByUsername.put(user.getUsername(), user.getId());
            } catch (DuplicateKeyException e) {
                // 并发注册同名账号：转为已存在用户入班（重传天然幂等，设计文档 6.2）
                User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, user.getUsername()));
                if (existing != null) {
                    existingIdByUsername.put(user.getUsername(), existing.getId());
                } else {
                    throw new RuntimeException("账号 " + user.getUsername() + " 创建失败，请重试");
                }
            }
        }

        // 已存在用户名 → user ID
        List<String> existingUsernames = toJoin.stream()
                .map(StudentImportVo::getUsername)
                .filter(u -> !existingIdByUsername.containsKey(u))
                .distinct().toList();
        if (!existingUsernames.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getUsername, existingUsernames))
                    .forEach(u -> existingIdByUsername.put(u.getUsername(), u.getId()));
        }

        for (StudentImportVo row : toJoin) {
            Long userId = existingIdByUsername.get(row.getUsername());
            if (userId == null) {
                continue; // 防御：理论上不可达
            }
            try {
                ClassMember member = new ClassMember();
                member.setClassId(classId);
                member.setUserId(userId);
                member.setRoleInClass(ROLE_STUDENT);
                classMemberMapper.insert(member);
                joinedUserIds.add(userId);
            } catch (DuplicateKeyException e) {
                // 已在班内的行 → 幂等成功，不重复入班（契约 5.3 处理规则 3，joinedCount 不计）
                log.warn("导入学员已在班内跳过：classId={} username={}", classId, row.getUsername());
            }
        }

        result.setSuccessRows(toJoin.size());
        result.setCreatedAccounts(createdAccounts);
        result.setJoinedCount(joinedUserIds.size());
        log.info("班级 {} 学员导入完成：总行 {}，成功 {}，新建账号 {}，实际入班 {}", classId,
                result.getTotalRows(), result.getSuccessRows(), createdAccounts, result.getJoinedCount());
        return result;
    }

    // ==================== 学员视角 ====================

    @Override
    public List<MyClassVo> listMyClasses(Long userId) {
        List<ClassMember> myMembers = classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                .eq(ClassMember::getUserId, userId)
                .eq(ClassMember::getRoleInClass, ROLE_STUDENT)
                .orderByAsc(ClassMember::getCreateTime));
        if (myMembers.isEmpty()) {
            return List.of();
        }
        List<Long> classIds = myMembers.stream().map(ClassMember::getClassId).distinct().toList();
        // 逻辑删除班级经 @TableLogic 自动过滤；级联清理兜底，缺失班级防御性跳过
        Map<Long, Clazz> classMap = listByIds(classIds).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));
        // 两步批量查：教师 + 学员计数，防 N+1
        Map<Long, List<MyClassTeacherVo>> teacherMap = listStudentViewTeachers(classIds);
        Map<Long, Integer> studentCountMap = countStudentsByClass(classIds);

        List<MyClassVo> result = new ArrayList<>();
        for (ClassMember member : myMembers) {
            Clazz clazz = classMap.get(member.getClassId());
            if (clazz == null) {
                continue;
            }
            MyClassVo vo = new MyClassVo();
            vo.setClassId(clazz.getId());
            vo.setName(clazz.getName());
            vo.setDirection(clazz.getDirection());
            vo.setStartDate(clazz.getStartDate() == null ? null : clazz.getStartDate().toString());
            vo.setEndDate(clazz.getEndDate() == null ? null : clazz.getEndDate().toString());
            vo.setStatus(clazz.getStatus());
            vo.setJoinedAt(member.getCreateTime());
            vo.setStudentCount(studentCountMap.getOrDefault(clazz.getId(), 0));
            vo.setTeachers(teacherMap.getOrDefault(clazz.getId(), List.of()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<ClassmateVo> listClassmates(Long classId, Long userId) {
        if (getById(classId) == null) {
            throw new RuntimeException("班级不存在");
        }
        // STUDENT-own：本人是该班成员（roleInClass=STUDENT）才放行，否则 403（契约 8.2）
        if (!existsMember(classId, userId, ROLE_STUDENT)) {
            throw new ClassScopeException();
        }
        List<ClassMember> members = classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                .eq(ClassMember::getClassId, classId)
                .eq(ClassMember::getRoleInClass, ROLE_STUDENT)
                .orderByAsc(ClassMember::getCreateTime));
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(
                        members.stream().map(ClassMember::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        List<ClassmateVo> result = new ArrayList<>();
        for (ClassMember member : members) {
            User user = userMap.get(member.getUserId());
            if (user == null) {
                continue;
            }
            result.add(new ClassmateVo(user.getId(), user.getRealName(), member.getCreateTime()));
        }
        return result;
    }

    // ==================== 私有工具 ====================

    /** 名称必填且 ≤50 字符（契约 4.2 校验规则） */
    private String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("班级名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new RuntimeException("班级名称不能超过 50 个字符");
        }
        return trimmed;
    }

    /** 名称与未删除班级不重名（excludeId 排除自身）；重复 → code 500"班级名称已存在" */
    private void checkClassNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Clazz> wrapper = new LambdaQueryWrapper<Clazz>().eq(Clazz::getName, name);
        wrapper.ne(excludeId != null, Clazz::getId, excludeId);
        if (count(wrapper) > 0) {
            throw new RuntimeException("班级名称已存在");
        }
    }

    /** 两者都传时要求 endDate >= startDate（契约 4.2） */
    private void checkDateOrder(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new RuntimeException("结班日期不能早于开班日期");
        }
    }

    /** 点查该用户是否在班内（roleInClass 为 null 时任一角色均算） */
    private boolean existsMember(Long classId, Long userId, String roleInClass) {
        LambdaQueryWrapper<ClassMember> wrapper = new LambdaQueryWrapper<ClassMember>()
                .eq(ClassMember::getClassId, classId)
                .eq(ClassMember::getUserId, userId);
        wrapper.eq(roleInClass != null, ClassMember::getRoleInClass, roleInClass);
        return classMemberMapper.exists(wrapper);
    }

    /** 行级校验：null 表示通过 */
    private String validateImportRow(String username, String realName) {
        if (username.isBlank()) {
            return "用户名为空";
        }
        if (username.length() > MAX_NAME_LENGTH) {
            return "用户名过长（最多 50 字符）";
        }
        if (username.chars().anyMatch(Character::isWhitespace)) {
            return "用户名不能包含空白字符";
        }
        if (realName.length() > MAX_NAME_LENGTH) {
            return "姓名过长（最多 50 字符）";
        }
        return null;
    }

    /** 班级列表/详情的成员计数：一次 IN 查询按 (classId, role) 分组，防 N+1 */
    private void fillMemberCounts(List<Clazz> classes) {
        if (classes == null || classes.isEmpty()) {
            return;
        }
        List<Long> classIds = classes.stream().map(Clazz::getId).toList();
        Map<Long, int[]> counts = new HashMap<>();
        for (ClassMember member : classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                .in(ClassMember::getClassId, classIds))) {
            int[] pair = counts.computeIfAbsent(member.getClassId(), k -> new int[2]);
            if (ROLE_TEACHER.equals(member.getRoleInClass())) {
                pair[1]++;
            } else if (ROLE_STUDENT.equals(member.getRoleInClass())) {
                pair[0]++;
            }
        }
        for (Clazz clazz : classes) {
            int[] pair = counts.getOrDefault(clazz.getId(), new int[2]);
            clazz.setStudentCount(pair[0]);
            clazz.setTeacherCount(pair[1]);
        }
    }

    /** 一组班级的教师列表（管理端口径，含 username），按教师 userId 升序 */
    private Map<Long, List<ClassTeacherVo>> listTeachersOfClasses(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return Map.of();
        }
        List<ClassMember> teachers = classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                .in(ClassMember::getClassId, classIds)
                .eq(ClassMember::getRoleInClass, ROLE_TEACHER)
                .orderByAsc(ClassMember::getUserId));
        if (teachers.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(
                        teachers.stream().map(ClassMember::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<ClassTeacherVo>> result = new HashMap<>();
        for (ClassMember member : teachers) {
            User user = userMap.get(member.getUserId());
            if (user == null) {
                continue;
            }
            result.computeIfAbsent(member.getClassId(), k -> new ArrayList<>())
                    .add(new ClassTeacherVo(user.getId(), user.getUsername(), user.getRealName()));
        }
        return result;
    }

    /** 一组班级的教师列表（学员端口径，不下发 username），按教师 userId 升序 */
    private Map<Long, List<MyClassTeacherVo>> listStudentViewTeachers(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return Map.of();
        }
        List<ClassMember> teachers = classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                .in(ClassMember::getClassId, classIds)
                .eq(ClassMember::getRoleInClass, ROLE_TEACHER)
                .orderByAsc(ClassMember::getUserId));
        if (teachers.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(
                        teachers.stream().map(ClassMember::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<MyClassTeacherVo>> result = new HashMap<>();
        for (ClassMember member : teachers) {
            User user = userMap.get(member.getUserId());
            if (user == null) {
                continue;
            }
            result.computeIfAbsent(member.getClassId(), k -> new ArrayList<>())
                    .add(new MyClassTeacherVo(user.getId(), user.getRealName()));
        }
        return result;
    }

    /** 一组班级的学员计数（按 class_id 一次 IN 查询分组计数） */
    private Map<Long, Integer> countStudentsByClass(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (ClassMember member : classMemberMapper.selectList(new LambdaQueryWrapper<ClassMember>()
                .in(ClassMember::getClassId, classIds)
                .eq(ClassMember::getRoleInClass, ROLE_STUDENT))) {
            result.merge(member.getClassId(), 1, Integer::sum);
        }
        return result;
    }

    /**
     * 当页成员的考试摘要聚合：一次 group by 查询（契约 5.1 实现提示，禁止循环 N+1）。
     * @return userId -> [examCount, lastCreateTimeEpochMillis(0 表示无记录)]
     */
    private Map<Long, long[]> aggregateExamSummary(List<Long> userIds) {
        QueryWrapper<ExamRecord> agg = new QueryWrapper<>();
        agg.select("user_id", "COUNT(*) AS exam_count", "MAX(create_time) AS last_time");
        agg.in("user_id", userIds);
        agg.groupBy("user_id");
        Map<Long, long[]> result = new HashMap<>();
        for (Map<String, Object> row : examRecordMapper.selectMaps(agg)) {
            Object userId = row.get("user_id");
            Object examCount = row.get("exam_count");
            Object lastTime = row.get("last_time");
            if (userId instanceof Number number) {
                long[] pair = new long[2];
                pair[0] = examCount instanceof Number n ? n.longValue() : 0;
                pair[1] = toEpochMillis(lastTime);
                result.put(number.longValue(), pair);
            }
        }
        return result;
    }

    /** create_time 聚合值的兼容转换（驱动可能返回 Timestamp/LocalDateTime/字符串） */
    private long toEpochMillis(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof java.util.Date date) {
            return date.getTime();
        }
        if (value instanceof LocalDateTime dateTime) {
            return java.sql.Timestamp.valueOf(dateTime).getTime();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return java.sql.Timestamp.valueOf(text).getTime();
            } catch (IllegalArgumentException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String formatEpochMillis(long epochMillis) {
        return TIME_FORMATTER.format(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault()));
    }
}
