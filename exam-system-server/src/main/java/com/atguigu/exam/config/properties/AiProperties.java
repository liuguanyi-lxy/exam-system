package com.atguigu.exam.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 服务配置（OpenAI 兼容的 Chat Completions 接口）。
 *
 * 默认接 DeepSeek，但只依赖几个可配置项，换供应商不需要改代码：
 *  - DeepSeek：base-url = https://api.deepseek.com/chat/completions ，model = deepseek-chat
 *  - Kimi    ：base-url = https://api.moonshot.cn/v1/chat/completions ，model = kimi-k2.6
 *  - 其它提供 OpenAI 兼容端点的服务同样可以直接替换
 */
@ConfigurationProperties(prefix = "ai")
@Data
public class AiProperties {

    /** 供应商标识，仅用于日志排查，例如 deepseek / kimi */
    private String provider = "deepseek";

    /** 完整的 chat/completions 端点地址 */
    private String baseUrl;

    /** 模型名，例如 deepseek-chat / deepseek-reasoner / kimi-k2.6 */
    private String model;

    /** API Key，建议放 application-local.yaml 或用环境变量注入 */
    private String apiKey;

    /** 单次回复的最大 token 数 */
    private Integer maxTokens = 4096;

    /**
     * 温度。DeepSeek 的 deepseek-chat 支持 0~2；
     * Kimi 的 k2 系列只接受 1；为 null 时不下发，交给服务端默认值。
     */
    private Double temperature;

    /** 单次调用超时时间（秒） */
    private Integer timeoutSeconds = 60;
}
