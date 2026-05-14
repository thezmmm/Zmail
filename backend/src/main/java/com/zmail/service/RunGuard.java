package com.zmail.service;

import com.zmail.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user concurrency lock + cooldown enforcer for agent runs.
 *
 * Guards against:
 *  - Two concurrent runs for the same user (could double-send / double-archive)
 *  - Runs triggered faster than minRunIntervalMs (API cost abuse)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RunGuard {

    private final AgentProperties agentProperties;

    private final ConcurrentHashMap<UUID, Boolean>  running   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long>     lastRunAt = new ConcurrentHashMap<>();

    /**
     * Acquires a run slot for the user.
     *
     * @throws IllegalStateException if already running or cooldown not expired
     */
    public void acquire(UUID userId) {
        if (running.putIfAbsent(userId, Boolean.TRUE) != null) {
            throw new IllegalStateException("Agent is already running for user " + userId);
        }

        long minInterval = agentProperties.getMinRunIntervalMs();
        Long last = lastRunAt.get(userId);
        if (last != null) {
            long elapsed = System.currentTimeMillis() - last;
            if (elapsed < minInterval) {
                running.remove(userId);
                long wait = (minInterval - elapsed) / 1000;
                throw new IllegalStateException(
                        "Too frequent. Please wait " + wait + " s before the next run.");
            }
        }
    }

    /** Must be called in a finally block after acquire(). */
    public void release(UUID userId) {
        lastRunAt.put(userId, System.currentTimeMillis());
        running.remove(userId);
    }
}