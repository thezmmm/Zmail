package com.zmail.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks the last successful sync timestamp per user.
 * Persisted in Redis so watermarks survive backend restarts.
 * Falls back to a 24-hour lookback when no watermark exists (new user, or Redis unavailable).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncWatermarkService {

    private static final Duration FALLBACK_LOOKBACK = Duration.ofHours(24);
    private static final String KEY_PREFIX = "zmail:sync:watermark:";
    private static final Duration KEY_TTL   = Duration.ofDays(90);

    private final StringRedisTemplate redis;

    public OffsetDateTime getWatermark(UUID userId) {
        try {
            String raw = redis.opsForValue().get(KEY_PREFIX + userId);
            if (raw != null) return OffsetDateTime.parse(raw);
        } catch (Exception e) {
            log.warn("Redis unavailable reading watermark for user {}, using fallback: {}", userId, e.getMessage());
        }
        return OffsetDateTime.now().minus(FALLBACK_LOOKBACK);
    }

    /** Only advances the watermark — never moves it backwards. */
    public void advance(UUID userId, OffsetDateTime syncStartedAt) {
        try {
            OffsetDateTime current = getWatermark(userId);
            if (syncStartedAt.isAfter(current)) {
                redis.opsForValue().set(KEY_PREFIX + userId, syncStartedAt.toString(), KEY_TTL);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable writing watermark for user {}: {}", userId, e.getMessage());
        }
    }
}
