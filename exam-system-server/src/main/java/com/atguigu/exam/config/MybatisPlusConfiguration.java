package com.atguigu.exam.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * projectName: day23_exam-system-server
 *
 * @author: 赵伟风
 * description: mybatis-plus专属的配置类
 */
@Configuration
@MapperScan("com.atguigu.exam.mapper")
public class MybatisPlusConfiguration {
    //该代码创建MybatisPlusInterceptor拦截器实例，并添加针对MySQL数据库的分页内部拦截器。其作用是在SQL执行前自动拦截并注入分页参数，实现数据库层面的物理分页功能。
    @Bean
    public MybatisPlusInterceptor paginationInterceptor() {
        //1.创建MybatisPlus拦截器对象
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        //2.添加分页拦截器
        mybatisPlusInterceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return mybatisPlusInterceptor;
    }
}
