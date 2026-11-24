package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.mapper.*;
import com.atguigu.exam.service.ClassScopeService;
import com.atguigu.exam.service.StatsService;
import com.atguigu.exam.vo.StatsVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 统计数据服务实现类
 */
@Slf4j
@Service
public class StatsServiceImpl implements StatsService {

    @Autowired
    private QuestionMapper questionMapper;  // 题目Mapper
    
    @Autowired
    private UserMapper userMapper;  // 用户Mapper
    
    @Autowired
    private ExamRecordMapper examRecordMapper;  // 考试记录Mapper
    
    @Autowired
    private CategoryMapper categoryMapper;  // 分类Mapper
    
    @Autowired
    private PaperMapper paperMapper;  // 试卷Mapper

    @Autowired
    private ClassScopeService classScopeService;

    @Override
    public StatsVo getSystemStats() {
        return getSystemStats(null, null);
    }

    @Override
    public StatsVo getSystemStats(String role, Long userId) {
        StatsVo stats = new StatsVo();
        java.util.Set<Long> teacherVisibleStudentIds = null;
        if ("TEACHER".equals(role)) {
            teacherVisibleStudentIds = userId == null
                    ? java.util.Collections.emptySet()
                    : classScopeService.getStudentUserIds(classScopeService.getTeacherClassIds(userId));
        }
        
        try {
            // 统计题目总数  // 查询题目数量
            Long questionCount = questionMapper.selectCount(new QueryWrapper<>());
            stats.setQuestionCount(questionCount);
            log.info("题目总数: {}", questionCount);
        } catch (Exception e) {
            log.error("查询题目总数失败: {}", e.getMessage());
            stats.setQuestionCount(0L);
        }
        
        try {
            // 教师只统计负责班级中的学员；管理员保持全量统计
            Long userCount;
            if (teacherVisibleStudentIds == null) {
                userCount = userMapper.selectCount(new QueryWrapper<>());
            } else {
                QueryWrapper<com.atguigu.exam.entity.User> userQuery = new QueryWrapper<>();
                if (teacherVisibleStudentIds.isEmpty()) {
                    userQuery.eq("id", -1L);
                } else {
                    userQuery.in("id", teacherVisibleStudentIds);
                }
                userCount = userMapper.selectCount(userQuery);
            }
            stats.setUserCount(userCount);
            log.info("用户总数: {}", userCount);
        } catch (Exception e) {
            log.error("查询用户总数失败: {}", e.getMessage());
            stats.setUserCount(0L);
        }
        
        try {
            // 教师只统计负责班级学员的记录，空范围使用不可能的 user_id 哨兵
            QueryWrapper<ExamRecord> examQuery = new QueryWrapper<>();
            if (teacherVisibleStudentIds != null) {
                if (teacherVisibleStudentIds.isEmpty()) {
                    examQuery.eq("user_id", -1L);
                } else {
                    examQuery.in("user_id", teacherVisibleStudentIds);
                }
            }
            Long examCount = examRecordMapper.selectCount(examQuery);
            stats.setExamCount(examCount);
            log.info("考试总场次: {}", examCount);
        } catch (Exception e) {
            log.error("查询考试总场次失败: {}", e.getMessage());
            stats.setExamCount(0L);
        }
        
        try {
            // 统计今日考试次数  // 查询今天的考试记录（使用create_time字段）
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();  // 今天00:00:00
            LocalDateTime endOfDay = today.atTime(LocalTime.MAX);  // 今天23:59:59
            
            QueryWrapper<ExamRecord> todayQueryWrapper = new QueryWrapper<>();
            todayQueryWrapper.between("create_time", startOfDay, endOfDay);  // 使用正确的字段名
            if (teacherVisibleStudentIds != null) {
                if (teacherVisibleStudentIds.isEmpty()) {
                    todayQueryWrapper.eq("user_id", -1L);
                } else {
                    todayQueryWrapper.in("user_id", teacherVisibleStudentIds);
                }
            }
            Long todayExamCount = examRecordMapper.selectCount(todayQueryWrapper);
            stats.setTodayExamCount(todayExamCount);
            log.info("今日考试次数: {}", todayExamCount);
        } catch (Exception e) {
            log.error("查询今日考试次数失败: {}", e.getMessage());
            stats.setTodayExamCount(0L);
        }
        
        try {
            // 统计分类总数  // 查询分类数量
            Long categoryCount = categoryMapper.selectCount(new QueryWrapper<>());
            stats.setCategoryCount(categoryCount);
            log.info("分类总数: {}", categoryCount);
        } catch (Exception e) {
            log.error("查询分类总数失败: {}", e.getMessage());
            stats.setCategoryCount(0L);
        }
        
        try {
            // 统计试卷总数  // 查询试卷数量
            Long paperCount = paperMapper.selectCount(new QueryWrapper<>());
            stats.setPaperCount(paperCount);
            log.info("试卷总数: {}", paperCount);
        } catch (Exception e) {
            log.error("查询试卷总数失败: {}", e.getMessage());
            stats.setPaperCount(0L);
        }
        
        log.info("系统统计数据获取完成: {}", stats);
        return stats;
    }

    @Override
    public void checkDatabaseConnection() {
        questionMapper.selectCount(new QueryWrapper<>());
    }
}
