package com.atguigu.exam.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j API文档配置类
 * 用于配置智能考试系统的API文档分组和基本信息
 * 通过SpringDoc OpenAPI实现接口文档的自动生成和管理
 *
 * @author 赵伟风
 * @version 1.0
 */
@Configuration
public class Knife4jConfiguration {


    /**
     * 配置OpenAPI文档信息
     * 设置API文档的标题、描述、版本等基本信息
     * Knife4j会自动读取这些信息并生成美观的文档页面
     *
     * @return OpenAPI配置对象
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("🎓 智能考试系统API文档") // API文档标题，添加emoji图标
                        .description("📚 智能考试系统后端接口文档，提供完整的RESTful API服务\n\n" +
                                "✨ 主要功能模块：\n" +
                                "• 🧠 题目管理：支持选择题、判断题、简答题的增删改查\n" +
                                "• 📝 试卷管理：手动组卷和AI智能组卷\n" +
                                "• 🎨 轮播图管理：首页轮播图的图片上传和管理\n" +
                                "• 📊 考试记录：考试结果统计和分析\n" +
                                "• 🔔 公告管理：系统公告的发布和管理") // API文档描述，使用markdown格式
                        .version("v1.0.0"));
    }


    /**
     * 配置用户信息管理API分组
     * 包含用户相关的接口文档，如用户注册、登录、信息查询等功能
     *
     * @return 用户信息管理的GroupedOpenApi对象
     */
    @Bean
    public GroupedOpenApi userAPI() {

        return GroupedOpenApi.builder().group("用户信息管理").
                pathsToMatch(
                        "/api/user/**"
                ).
                build();
    }

    /**
     * 配置试题信息管理API分组
     * 包含题目分类管理和题目管理的接口文档，支持选择题、判断题、简答题等多种题型
     *
     * @return 试题信息管理的GroupedOpenApi对象
     */
    @Bean
    public GroupedOpenApi questionsAPI() {

        return GroupedOpenApi.builder().group("试题信息管理").
                pathsToMatch(
                        "/api/categories/**",
                        "/api/questions/**"
                ).
                build();
    }



    /**
     * 配置考试信息管理API分组
     * 包含试卷管理、考试管理和考试记录管理的接口文档，支持手动组卷和AI智能组卷功能
     *
     * @return 考试信息管理的GroupedOpenApi对象
     */
    @Bean
    public GroupedOpenApi papersAPI() {

        return GroupedOpenApi.builder().group("考试信息管理").
                pathsToMatch(
                        "/api/papers/**",
                        "/api/exams/**",
                        "/api/exam-records/**"
                ).
                build();
    }

    /**
     * 配置视频信息管理API分组
     * 包含管理端视频、客户端视频和视频分类管理的接口文档（预留配置，暂未实现）
     *
     * @return 视频信息管理的GroupedOpenApi对象
     */
    @Bean
    public GroupedOpenApi videosAPI() {

        return GroupedOpenApi.builder().group("视频信息管理").
                pathsToMatch(
                        "/api/admin/videos/**",
                        "/api/videos/**",
                        "/api/video-categories/**"
                ).
                build();
    }


    /**
     * 配置系统信息管理API分组
     * 包含轮播图管理和公告管理的接口文档，支持文件上传功能
     *
     * @return 系统信息管理的GroupedOpenApi对象
     */
    @Bean
    public GroupedOpenApi systemAPI() {

        return GroupedOpenApi.builder().group("系统信息管理").
                pathsToMatch(
                        "/api/banners/**",
                        "/api/notices/**"
                ).
                build();
    }


    /**
     * 配置其他内容管理API分组
     * 包含数据统计、文件查询和调试接口的文档（部分功能预留，暂未实现）
     *
     * @return 其他内容管理的GroupedOpenApi对象
     */
    @Bean
    public GroupedOpenApi otherAPI() {

        return GroupedOpenApi.builder().group("其他内容管理").
                pathsToMatch(
                        "/api/stats/**",
                        "/files/**",
                        "/api/debug/**"
                ).
                build();
    }


}