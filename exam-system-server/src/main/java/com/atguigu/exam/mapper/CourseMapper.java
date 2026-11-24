package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.Course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CourseMapper extends BaseMapper<Course> {
    @Select("SELECT id, created_by FROM courses WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    Course lockForAssignment(@Param("id") Long id);
}
