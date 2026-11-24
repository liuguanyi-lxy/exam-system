package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.CourseClass;
import com.atguigu.exam.vo.CourseAssignmentTimeVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 课程-班级分配Mapper接口
 * M1 仅建 Mapper 无业务读写代码（契约 course_classes 无接口），M2 接线
 */
public interface CourseClassMapper extends BaseMapper<CourseClass> {
    @Select("""
            <script>
            SELECT cc.course_id AS courseId, MIN(cc.create_time) AS assignedAt
            FROM course_classes cc
            JOIN classes c ON c.id = cc.class_id AND c.is_deleted = 0 AND c.status = 'ENABLED'
            WHERE
            <choose>
              <when test="classIds != null and !classIds.isEmpty()">
                cc.class_id IN
                <foreach collection="classIds" item="classId" open="(" separator="," close=")">
                  #{classId}
                </foreach>
              </when>
              <otherwise>1 = 0</otherwise>
            </choose>
            GROUP BY cc.course_id
            </script>
            """)
    List<CourseAssignmentTimeVo> selectAssignedCourseTimes(@Param("classIds") List<Long> classIds);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM course_classes cc
                JOIN classes c ON c.id = cc.class_id AND c.is_deleted = 0 AND c.status = 'ENABLED'
                JOIN class_members cm ON cm.class_id = cc.class_id AND cm.role_in_class = 'STUDENT'
                WHERE cc.course_id = #{courseId} AND cm.user_id = #{userId}
            )
            """)
    boolean hasActiveStudentAssignment(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
