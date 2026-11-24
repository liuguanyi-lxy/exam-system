package com.atguigu.exam.service;


import com.atguigu.exam.vo.StatsVo;

/**
 * 统计数据服务接口
 */
public interface StatsService {
    
    /**
     * 获取系统统计数据
     * @return 统计数据DTO
     */
    StatsVo getSystemStats();

    /** 获取按当前角色数据范围过滤的系统统计数据。 */
    StatsVo getSystemStats(String role, Long userId);

    void checkDatabaseConnection();
}
