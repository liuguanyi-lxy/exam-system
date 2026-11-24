package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id FROM users WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    Long lockExamCandidate(@Param("id") Long id);
}
