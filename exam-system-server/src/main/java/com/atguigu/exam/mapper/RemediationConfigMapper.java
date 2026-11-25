package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.RemediationConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;

/**
 * 课程级补课策略 Mapper（M5）
 * 教师配置读取/保存与任务生成快照共用
 */
public interface RemediationConfigMapper extends BaseMapper<RemediationConfig> {

    /**
     * 课程级策略 upsert（唯一键 uk_course_config(course_id) 兜底）：
     * 已存在（含逻辑删除行，逻辑删除行仍占唯一键）则整字段覆盖并复活 is_deleted=0。
     * 时间戳在 SQL 内取 CURRENT_TIMESTAMP（云端 DB 为 UTC，禁止 JVM 侧填充时间）。
     */
    @Insert("""
            INSERT INTO remediation_configs
              (course_id, min_mastery, practice_count, retest_count, force_retest, allow_skip_video,
               due_days, create_time, update_time, is_deleted)
            VALUES (#{courseId}, #{minMastery}, #{practiceCount}, #{retestCount}, #{forceRetest},
                    #{allowSkipVideo}, #{dueDays}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            ON DUPLICATE KEY UPDATE
              min_mastery = VALUES(min_mastery), practice_count = VALUES(practice_count),
              retest_count = VALUES(retest_count), force_retest = VALUES(force_retest),
              allow_skip_video = VALUES(allow_skip_video), due_days = VALUES(due_days),
              is_deleted = 0, update_time = CURRENT_TIMESTAMP
            """)
    int upsert(RemediationConfig config);
}
