package com.zmail.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived OAuth2 state token store.
 * Each token is valid for 10 minutes; expired entries are purged every 5 minutes.
 *
 * TODO: replace with Redis for multi-instance deployments.
 */
@Component
public class OAuthStateStore {

    private static final long TTL_MS = 600_000L;

    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    public String generate() {
        String state = UUID.randomUUID().toString().replace("-", "");
        pending.put(state, System.currentTimeMillis() + TTL_MS);
        return state;
    }

    /**
     * Validates and consumes a state token in one atomic step.
     * Returns true only if the token exists and has not expired.
     * The token is removed regardless, preventing replay attacks.
     */
    public boolean consume(String state) {
        Long expiry = pending.remove(state);
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    @Scheduled(fixedDelay = 300_000)
    void purgeExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue() < now);
    }
}
