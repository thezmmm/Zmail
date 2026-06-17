package com.zmail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived OAuth2 state token store backed by Redis.
 * TTL is enforced natively; no in-process cleanup needed.
 *
 * Two flows:
 *   - login  (generate / consume)         — first-time OAuth, creates/finds user
 *   - link   (generateLink / consumeLink) — adds an email account to an existing user
 */
@Component
@RequiredArgsConstructor
public class OAuthStateStore {

    private static final Duration TTL           = Duration.ofMinutes(10);
    private static final String   LOGIN_PREFIX  = "zmail:oauth:state:";
    private static final String   LINK_PREFIX   = "zmail:oauth:link:";

    private final StringRedisTemplate redis;

    public String generate() {
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(LOGIN_PREFIX + state, "1", TTL);
        return state;
    }

    public boolean consume(String state) {
        return redis.opsForValue().getAndDelete(LOGIN_PREFIX + state) != null;
    }

    /** Generate a link-flow state that carries the authenticated user's ID. */
    public String generateLink(UUID userId) {
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(LINK_PREFIX + state, userId.toString(), TTL);
        return state;
    }

    /**
     * Validates and consumes a link-flow state.
     * Returns the userId if the token is valid, empty otherwise.
     */
    public Optional<UUID> consumeLink(String state) {
        String val = redis.opsForValue().getAndDelete(LINK_PREFIX + state);
        if (val == null) return Optional.empty();
        return Optional.of(UUID.fromString(val));
    }
}
