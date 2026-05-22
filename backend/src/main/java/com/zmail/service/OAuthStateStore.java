package com.zmail.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived OAuth2 state token store.
 * Each token is valid for 10 minutes; expired entries are purged every 5 minutes.
 *
 * Two flows are tracked separately:
 *   - login  (generate / consume)       — first-time OAuth, creates/finds user
 *   - link   (generateLink / consumeLink) — adds an email account to an existing user
 *
 * TODO: replace with Redis for multi-instance deployments.
 */
@Component
public class OAuthStateStore {

    private static final long TTL_MS = 600_000L;

    /** login flow: state → expiry */
    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    /** link flow: state → {expiry, userId} */
    private record LinkEntry(long expiry, UUID userId) {}
    private final Map<String, LinkEntry> linkPending = new ConcurrentHashMap<>();

    public String generate() {
        String state = UUID.randomUUID().toString().replace("-", "");
        pending.put(state, System.currentTimeMillis() + TTL_MS);
        return state;
    }

    public boolean consume(String state) {
        Long expiry = pending.remove(state);
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    /** Generate a link-flow state that carries the authenticated user's ID. */
    public String generateLink(UUID userId) {
        String state = UUID.randomUUID().toString().replace("-", "");
        linkPending.put(state, new LinkEntry(System.currentTimeMillis() + TTL_MS, userId));
        return state;
    }

    /**
     * Validates and consumes a link-flow state.
     * Returns the userId if the token is valid, empty otherwise.
     */
    public Optional<UUID> consumeLink(String state) {
        LinkEntry entry = linkPending.remove(state);
        if (entry == null || entry.expiry() < System.currentTimeMillis()) return Optional.empty();
        return Optional.of(entry.userId());
    }

    @Scheduled(fixedDelay = 300_000)
    void purgeExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue() < now);
        linkPending.entrySet().removeIf(e -> e.getValue().expiry() < now);
    }
}
