package com.zmail.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last successful sync timestamp per user so the sync job
 * only fetches emails newer than the previous run rather than all unread mail.
 * In-memory: resets to a 24-hour fallback on backend restart.
 */
@Service
public class SyncWatermarkService {

    private static final Duration FALLBACK_LOOKBACK = Duration.ofHours(24);

    private final ConcurrentHashMap<UUID, OffsetDateTime> watermarks = new ConcurrentHashMap<>();

    public OffsetDateTime getWatermark(UUID userId) {
        return watermarks.getOrDefault(userId, OffsetDateTime.now().minus(FALLBACK_LOOKBACK));
    }

    public void advance(UUID userId, OffsetDateTime syncStartedAt) {
        watermarks.merge(userId, syncStartedAt, (existing, candidate) ->
                candidate.isAfter(existing) ? candidate : existing);
    }
}
