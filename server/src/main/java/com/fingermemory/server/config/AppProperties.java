package com.fingermemory.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Auth auth, Content content) {
    public record Auth(
            boolean registrationEnabled,
            int loginAttemptsPerMinute,
            int registerAttemptsPerMinute) {
    }

    public record Content(
            String packageRoot,
            String engineVersion) {
    }
}
