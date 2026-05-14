package com.zmail.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Wraps an LLM call with up to MAX_ATTEMPTS tries and exponential back-off.
 * Uses a ThrowingSupplier so callers can propagate checked exceptions naturally.
 * On final failure the provided fallback value is returned — the agent graph
 * continues rather than crashing.
 */
@Component
@Slf4j
public class LlmRetryHelper {

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final int  MAX_ATTEMPTS   = 3;
    private static final long BASE_DELAY_MS  = 1_000;

    public <T> T call(String opName, ThrowingSupplier<T> action, T fallback) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("[{}] failed after {} attempts: {}", opName, MAX_ATTEMPTS, e.getMessage());
                    return fallback;
                }
                long delay = BASE_DELAY_MS * (1L << (attempt - 1)); // 1 s, 2 s, 4 s
                log.warn("[{}] attempt {}/{} failed ({}), retrying in {} ms",
                        opName, attempt, MAX_ATTEMPTS, e.getMessage(), delay);
                sleep(delay);
            }
        }
        return fallback;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}