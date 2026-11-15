package com.fingermemory.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Auth auth) {
    public record Auth(
            boolean registrationEnabled,
            int loginAttemptsPerMinute,
            int registerAttemptsPerMinute) {
    }
}
