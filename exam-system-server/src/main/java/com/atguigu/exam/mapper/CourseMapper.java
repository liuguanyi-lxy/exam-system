package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.Course;
import com.atguigu.exam.vo.CourseSummaryVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CourseMapper extends BaseMapper<Course> {
    @Select("SELECT * FROM courses WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    Course lockForAssignment(@Param("id") Long id);

    @Select("""
            <script>
            SELECT c.id, c.name, c.description, c.cover_url, c.status, c.created_by,
                   COALESCE(NULLIF(u.real_name, ''), u.username) AS creator_name,
                   c.create_time AS created_at, c.update_time AS updated_at,
                   (SELECT COUNT(*) FROM course_chapters ch
                    WHERE ch.course_id = c.id AND ch.is_deleted = 0) AS chapter_count,
                   (SELECT COUNT(*) FROM course_classes cc
                    JOIN classes cls ON cls.id = cc.class_id AND cls.is_deleted = 0
                    WHERE cc.course_id = c.id
                    <if test="!admin">
                      <choose>
                        <when test="!classIds.isEmpty()">
                          AND cc.class_id IN
                          <foreach collection="classIds" item="classId" open="(" separator="," close=")">#{classId}</foreach>
                        </when>
                        <otherwise>AND 1 = 0</otherwise>
                      </choose>
                    </if>) AS class_count
            FROM courses c
            LEFT JOIN users u ON u.id = c.created_by AND u.is_deleted = 0
            WHERE c.is_deleted = 0
            <if test="!admin">
              AND (c.created_by = #{userId}
              <if test="!classIds.isEmpty()">
                OR EXISTS (SELECT 1 FROM course_classes cc
                           JOIN classes cls ON cls.id = cc.class_id AND cls.is_deleted = 0
                           WHERE cc.course_id = c.id AND cc.class_id IN
                           <foreach collection="classIds" item="classId" open="(" separator="," close=")">#{classId}</foreach>)
              </if>)
            </if>
            <if test="keyword != null">AND c.name LIKE CONCAT('%', #{keyword}, '%')</if>
            <if test="status != null">AND c.status = #{status}</if>
            ORDER BY c.update_time DESC, c.id DESC
            </script>
            """)
    Page<CourseSummaryVo> pageSummaries(Page<CourseSummaryVo> page, @Param("admin") boolean admin,
                                        @Param("userId") Long userId, @Param("classIds") List<Long> classIds,
                                        @Param("keyword") String keyword, @Param("status") String status);
}
