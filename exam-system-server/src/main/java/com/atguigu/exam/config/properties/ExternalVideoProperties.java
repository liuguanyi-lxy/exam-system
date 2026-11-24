package com.atguigu.exam.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "learning.external-video")
public class ExternalVideoProperties {
    private List<String> allowedDomains = List.of("bilibili.com", "iqiyi.com", "youku.com", "v.qq.com", "douyin.com");
}
