package com.atguigu.exam.config;

import com.atguigu.exam.config.properties.MinioProperties;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * projectName: day23_exam-system-server
 *
 * @author: 赵伟风
 * description: Minio的配置类，完成MinioClient组件的创建
 * 1. 注册配置属性类为 Bean
 * @EnableConfigurationProperties将标注了 @ConfigurationProperties 的类自动注册到 Spring 容器中,使其可以被注入使用。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {

    /**
     * Minio配置属性
     */
    @Autowired
    private MinioProperties minioProperties;

    /**
     * 创建MinioClient客户端Bean实例
     * 使用配置的endpoint、用户名和密码构建Minio客户端,用于后续的文件上传下载等操作
     *
     * @return MinioClient Minio客户端实例,已配置好连接信息
     */
    @Bean
    public MinioClient minioClient() {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getUsername(), minioProperties.getPassword())
                .build();
        log.info("完成了minio的连接和配置！minio客户端对象为：{}",minioClient);
        return minioClient;
    }
}
