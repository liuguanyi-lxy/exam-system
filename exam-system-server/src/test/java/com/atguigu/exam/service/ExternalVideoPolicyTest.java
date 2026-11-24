package com.atguigu.exam.service;

import com.atguigu.exam.config.properties.ExternalVideoProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalVideoPolicyTest {
    private final ExternalVideoProperties properties = new ExternalVideoProperties();
    private final ExternalVideoPolicy policy = new ExternalVideoPolicy(properties);

    @Test
    void allowsConfiguredDomainAndItsSubdomains() {
        properties.setAllowedDomains(List.of("example.test"));
        assertEquals("https://example.test/watch", policy.validate(" https://example.test/watch "));
        assertEquals("https://video.example.test/watch", policy.validate("https://video.example.test/watch"));
    }

    @Test
    void rejectsNonWhitelistedAndUnsupportedUrls() {
        properties.setAllowedDomains(List.of("example.test"));
        for (String url : List.of("https://other.test/watch", "https://notexample.test/watch",
                "file:///video", "https://user@example.test/watch", "https://example.test:8080/watch",
                "http://localhost/watch", "https://127.0.0.1/watch")) {
            assertThrows(IllegalArgumentException.class, () -> policy.validate(url));
        }
    }

    @Test
    void emptyConfigurationDeniesExternalLinks() {
        properties.setAllowedDomains(List.of());
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://example.test/watch"));
    }
}
