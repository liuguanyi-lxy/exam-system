package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
public interface QuestionMapper extends BaseMapper<Question> {
    @Select("select category_id,ifnull(count(*),0) count from questions where is_deleted=0 group by category_id")
    List<Map<Long,Long>> selectCategoryCount();
    //TODO: 根据ID查询题目详情，包括选项和答案
    Question customGetById(Long id);
} 