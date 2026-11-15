package com.fingermemory.server.auth;

import com.fingermemory.server.common.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private final int loginLimit;
    private final int registerLimit;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();

    public AuthRateLimiter(com.fingermemory.server.config.AppProperties properties) {
        this.loginLimit = properties.auth().loginAttemptsPerMinute();
        this.registerLimit = properties.auth().registerAttemptsPerMinute();
    }

    public void checkLogin(String remoteAddress, String username) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!consume("login:ip:" + remoteAddress, loginLimit)
                || !consume("login:user:" + normalizedUsername, loginLimit)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", "尝试次数过多，请稍后再试");
        }
    }

    public void checkRegistration(String remoteAddress) {
        if (!consume("register:ip:" + remoteAddress, registerLimit)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", "尝试次数过多，请稍后再试");
        }
    }

    private boolean consume(String key, int limit) {
        Bucket bucket = buckets.get(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.classic(limit, io.github.bucket4j.Refill.greedy(limit, Duration.ofMinutes(1))))
                .build());
        return bucket.tryConsume(1);
    }
}
