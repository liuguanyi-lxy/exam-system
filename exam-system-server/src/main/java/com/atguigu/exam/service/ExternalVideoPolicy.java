package com.atguigu.exam.service;

import com.atguigu.exam.config.properties.ExternalVideoProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class ExternalVideoPolicy {
    private final ExternalVideoProperties properties;

    public ExternalVideoPolicy(ExternalVideoProperties properties) {
        this.properties = properties;
    }

    public String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("外链地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("外链地址格式错误");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || uri.getRawUserInfo() != null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("外链地址必须为有效的 http(s) URL");
        }
        int defaultPort = scheme.equalsIgnoreCase("https") ? 443 : 80;
        if (uri.getPort() != -1 && uri.getPort() != defaultPort) {
            throw new IllegalArgumentException("不支持的视频链接端口");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!validDomain(normalizedHost)) throw new IllegalArgumentException("不支持的视频链接域名");
        var allowed = properties.getAllowedDomains();
        boolean matched = allowed != null && allowed.stream().filter(domain -> domain != null && !domain.isBlank())
                .map(domain -> domain.strip().toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""))
                .filter(this::validDomain)
                .anyMatch(domain -> normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain));
        if (!matched) throw new IllegalArgumentException("不支持的视频链接域名");
        String url = uri.toASCIIString();
        if (url.length() > 500) {
            throw new IllegalArgumentException("外链地址不能超过500字");
        }
        return url;
    }

    private boolean validDomain(String domain) {
        return domain.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{1,62}");
    }
}
