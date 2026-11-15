package com.atguigu.exam.config;

import com.atguigu.exam.config.properties.AiProperties;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * AI 调用使用的 WebClient 配置。
 *
 * 只依赖「OpenAI 兼容端点 + Bearer Key」，因此 DeepSeek / Kimi / 其它兼容服务
 * 都可以通过 application.yaml 里的 ai.* 配置切换，不需要改代码。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
@lombok.extern.slf4j.Slf4j
public class WebClientConfiguration {

    @Autowired
    private AiProperties aiProperties;

    @Bean
    public WebClient webClient() {
        // 启动时把当前生效的供应商打出来，避免"到底连的是哪家、哪个模型"只能靠猜
        log.info("AI 服务配置：provider={}, model={}, endpoint={}, temperature={}",
                aiProperties.getProvider(), aiProperties.getModel(), aiProperties.getBaseUrl(),
                aiProperties.getTemperature());
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()
                || aiProperties.getApiKey().startsWith("请填入")) {
            log.warn("AI API Key 未配置，AI 组卷/判卷/总评会失败，请在 application-local.yaml 的 ai.api-key 填入");
        }

        HttpClient httpClient = HttpClient.create()
                // 建立连接超时，避免供应商不可达时线程被长时间占住
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()));

        return WebClient.builder()
                // 端点即完整地址（含 /chat/completions），调用处直接 post() 即可
                .baseUrl(aiProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                .build();
    }
}
