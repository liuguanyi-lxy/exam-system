package com.atguigu.exam.service;

import com.atguigu.exam.vo.QuestionFavoriteVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 题目收藏 Service
 *
 * 收藏行为表物理删除，重复收藏/取消收藏均幂等；
 * 列表查询一律按当前登录人 userId 定界。
 */
public interface FavoriteService {

    /**
     * 收藏题目，幂等；题目不存在时抛业务异常。
     */
    void addFavorite(Long userId, Long questionId);

    /**
     * 取消收藏，幂等；未收藏时同样成功。
     */
    void removeFavorite(Long userId, Long questionId);

    /**
     * 分页查询当前登录人的收藏列表（按收藏时间倒序）。
     *
     * @param userId     当前登录人ID（归属定界的唯一依据）
     * @param page       页码，从1开始
     * @param size       每页条数
     * @param type       题型筛选，非法值忽略
     * @param difficulty 难度筛选，非法值忽略
     * @param categoryId 分类筛选，非数字/不存在时忽略
     */
    Page<QuestionFavoriteVo> pageFavorites(Long userId, Integer page, Integer size,
                                           String type, String difficulty, String categoryId);

    /**
     * 查询某题是否已被当前登录人收藏。
     */
    boolean isFavorited(Long userId, Long questionId);
}
