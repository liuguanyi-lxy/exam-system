package com.atguigu.exam.config;

import com.atguigu.exam.config.properties.KimiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * projectName: day23_exam-system-server
 *
 * @author: 赵伟风
 * description:
 */
@Configuration
// 启用配置属性功能，将KimiProperties类注册为配置属性类
// 这样Spring Boot会自动扫描并注册该类，使其@ConfigurationProperties注解生效
@EnableConfigurationProperties(KimiProperties.class)
public class WebClientConfiguration {


    @Autowired
    private KimiProperties kimiProperties;

    /**
     * 创建并配置WebClient Bean
     * WebClient是Spring WebFlux提供的非阻塞响应式HTTP客户端，用于发送HTTP请求
     *
     * @return 配置好的WebClient实例，已设置基础URL、默认请求头（Content-Type和Authorization）
     */
    @Bean
    public WebClient webClient() {
        // 使用构建器模式创建WebClient实例，配置Kimi API的基础URL、认证信息和默认请求头
        WebClient webClient = WebClient.builder()
                // 设置Kimi API的基础URL地址，所有通过此WebClient发送的请求都将以此URL为前缀
                .baseUrl(kimiProperties.getUri())
                // 设置默认的Content-Type请求头为application/json，表明请求体格式为JSON数据
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 设置Authorization请求头，使用Bearer Token方式进行API密钥认证，确保请求的合法性和安全性
                .defaultHeader("Authorization", "Bearer " + kimiProperties.getApiKey())
                .build();
        return webClient;
    }
}
