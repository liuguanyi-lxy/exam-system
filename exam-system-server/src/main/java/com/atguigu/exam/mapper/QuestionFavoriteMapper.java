package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.QuestionFavorite;
import com.atguigu.exam.vo.QuestionFavoriteVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

/**
 * 题目收藏 Mapper
 *
 * 列表查询需要在 questions 表上做题型/难度/分类筛选后再分页，
 * 因此用自定义分页 SQL（收藏行与题目 JOIN），避免"先分页再内存过滤"导致分页语义错误。
 */
public interface QuestionFavoriteMapper extends BaseMapper<QuestionFavorite> {

    /**
     * 收藏列表分页查询（按收藏时间倒序）。
     *
     * @param page       分页对象（MyBatis-Plus 分页拦截器首参）
     * @param userId     当前登录人ID，归属定界唯一依据
     * @param type       题型筛选，已归一化，null 表示不过滤
     * @param difficulty 难度筛选，已归一化，null 表示不过滤
     * @param categoryId 分类筛选，已校验存在，null 表示不过滤
     */
    Page<QuestionFavoriteVo> selectFavoritePage(Page<QuestionFavoriteVo> page,
                                                @Param("userId") Long userId,
                                                @Param("type") String type,
                                                @Param("difficulty") String difficulty,
                                                @Param("categoryId") Long categoryId);
}
