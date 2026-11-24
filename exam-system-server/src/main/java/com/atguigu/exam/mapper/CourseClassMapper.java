package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.CourseClass;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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
    List<Map<String, Object>> selectAssignedCourseTimes(@Param("classIds") List<Long> classIds);
}
