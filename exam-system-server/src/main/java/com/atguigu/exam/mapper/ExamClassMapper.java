package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.ExamClass;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExamClassMapper extends BaseMapper<ExamClass> {
    @Select("""
            <script>
            SELECT a.* FROM exam_classes a
            JOIN classes c ON c.id = a.class_id AND c.is_deleted = 0 AND c.status = 'ENABLED'
            JOIN class_members m ON m.class_id = a.class_id AND m.user_id = #{userId} AND m.role_in_class = 'STUDENT'
            WHERE a.is_deleted = 0 AND a.status = 'ENABLED'
            <if test="examId != null">AND a.exam_id = #{examId}</if>
            ORDER BY a.id
            <if test="lock">FOR UPDATE</if>
            </script>
            """)
    List<ExamClass> findStudentAssignments(@Param("userId") Long userId, @Param("examId") Integer examId,
                                           @Param("lock") boolean lock);

    @Select("SELECT * FROM exam_classes WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    ExamClass lockById(@Param("id") Long id);

    /** Revive the unique-key row left behind by a previous logical revoke. */
    @Update("UPDATE exam_classes SET is_deleted = 0, start_time = #{startTime}, end_time = #{endTime}, " +
            "max_attempts = #{maxAttempts}, status = 'ENABLED', update_time = CURRENT_TIMESTAMP " +
            "WHERE exam_id = #{examId} AND class_id = #{classId} AND is_deleted = 1")
    int reviveDeleted(@Param("examId") Long examId,
                      @Param("classId") Long classId,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime,
                      @Param("maxAttempts") Integer maxAttempts);

    @Select("SELECT * FROM exam_classes WHERE exam_id = #{examId} AND class_id = #{classId} FOR UPDATE")
    ExamClass lockByPair(@Param("examId") Long examId, @Param("classId") Long classId);

    /** Explicitly writes nullable times; MyBatis-Plus NOT_NULL update strategy cannot clear them. */
    @Update("UPDATE exam_classes SET start_time = #{startTime}, end_time = #{endTime}, " +
            "max_attempts = #{maxAttempts}, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND is_deleted = 0")
    int updateWindowAndAttempts(@Param("id") Long id,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                @Param("maxAttempts") Integer maxAttempts);
}
