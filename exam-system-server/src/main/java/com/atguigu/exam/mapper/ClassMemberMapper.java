package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.ClassMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 班级成员Mapper接口
 * 继承MyBatis Plus的BaseMapper；数据范围层与成员管理共用
 */
public interface ClassMemberMapper extends BaseMapper<ClassMember> {
}
