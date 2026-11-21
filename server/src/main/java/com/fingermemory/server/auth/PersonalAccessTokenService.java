package com.fingermemory.server.auth;

import com.fingermemory.server.auth.AccessTokenDtos.CreateRequest;
import com.fingermemory.server.auth.AccessTokenDtos.CreatedTokenView;
import com.fingermemory.server.auth.AccessTokenDtos.TokenView;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.user.UserAccount;
import com.fingermemory.server.user.UserMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalAccessTokenService {
    private static final String TOKEN_PREFIX = "fm_pat_";
    private static final String ACCOUNT_SCOPE = "account";
    private static final int SECRET_BYTE_COUNT = 32;
    private static final long DEFAULT_LIFETIME_MS = Duration.ofDays(90).toMillis();
    private static final long MINIMUM_LIFETIME_MS = Duration.ofMinutes(5).toMillis();
    private static final long MAXIMUM_LIFETIME_MS = Duration.ofDays(365).toMillis();
    private static final long LAST_USED_WRITE_INTERVAL_MS = Duration.ofMinutes(5).toMillis();

    private final PersonalAccessTokenMapper tokenMapper;
    private final UserMapper userMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PersonalAccessTokenService(
            PersonalAccessTokenMapper tokenMapper,
            UserMapper userMapper) {
        this.tokenMapper = tokenMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public CreatedTokenView create(String ownerId, CreateRequest request) {
        long now = Instant.now().toEpochMilli();
        long expiresAtMs = request.expiresAtMs() == null
                ? now + DEFAULT_LIFETIME_MS
                : request.expiresAtMs();
        validateExpiry(now, expiresAtMs);

        byte[] secret = new byte[SECRET_BYTE_COUNT];
        secureRandom.nextBytes(secret);
        String plaintext = TOKEN_PREFIX + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secret);

        PersonalAccessToken token = new PersonalAccessToken();
        token.setId(UUID.randomUUID().toString());
        token.setOwnerId(ownerId);
        token.setName(request.name().trim());
        token.setTokenHash(hash(plaintext));
        token.setTokenPrefix(plaintext.substring(0, 12));
        token.setScope(ACCOUNT_SCOPE);
        token.setCreatedAtMs(now);
        token.setExpiresAtMs(expiresAtMs);
        token.setLastUsedAtMs(null);
        token.setRevokedAtMs(null);
        token.setVersion(0);
        tokenMapper.insert(token);
        return new CreatedTokenView(TokenView.from(token), plaintext);
    }

    public List<TokenView> list(String ownerId) {
        return tokenMapper.listByOwner(ownerId).stream().map(TokenView::from).toList();
    }

    @Transactional
    public void revoke(String ownerId, String tokenId) {
        int updated = tokenMapper.revoke(tokenId, ownerId, Instant.now().toEpochMilli());
        if (updated == 0) {
            PersonalAccessToken existing = tokenMapper.findByOwnerAndId(ownerId, tokenId);
            if (existing == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "AUTH_TOKEN_NOT_FOUND",
                        "访问令牌不存在");
            }
            if (existing.getRevokedAtMs() == null) {
                throw new ApiException(HttpStatus.CONFLICT, "AUTH_TOKEN_CONFLICT",
                        "访问令牌状态已变化，请刷新后重试");
            }
        }
    }

    public FmUserPrincipal authenticate(String plaintext) {
        if (plaintext == null || !plaintext.startsWith(TOKEN_PREFIX)
                || plaintext.length() > 128) {
            throw invalidToken();
        }
        long now = Instant.now().toEpochMilli();
        PersonalAccessToken token = tokenMapper.findByHash(hash(plaintext));
        if (token == null
                || token.getRevokedAtMs() != null
                || token.getExpiresAtMs() <= now
                || !ACCOUNT_SCOPE.equals(token.getScope())) {
            throw invalidToken();
        }

        UserAccount user = userMapper.findById(token.getOwnerId());
        if (user == null || !"active".equals(user.getStatus())
                || user.isMustChangePassword()) {
            throw invalidToken();
        }

        if (token.getLastUsedAtMs() == null
                || now - token.getLastUsedAtMs() >= LAST_USED_WRITE_INTERVAL_MS) {
            tokenMapper.touchLastUsed(token.getId(), now);
        }
        return new FmUserPrincipal(user);
    }

    private void validateExpiry(long now, long expiresAtMs) {
        if (expiresAtMs < now + MINIMUM_LIFETIME_MS
                || expiresAtMs > now + MAXIMUM_LIFETIME_MS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_TOKEN_EXPIRY_INVALID",
                    "访问令牌有效期必须在 5 分钟到 365 天之间");
        }
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED",
                "访问令牌无效、已过期或已撤销");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
