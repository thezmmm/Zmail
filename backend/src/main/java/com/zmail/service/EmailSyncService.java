package com.zmail.service;

import com.zmail.config.AgentProperties;
import com.zmail.email.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Core incremental sync logic shared by the scheduled job and the manual sync API.
 *
 * Fetches new emails since the user's watermark, classifies each one via LLM,
 * and advances the watermark on success.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSyncService {

    private final EmailService emailService;
    private final EmailProcessingService processingService;
    private final InitialSyncService initialSyncService;
    private final SyncWatermarkService watermark;
    private final RunGuard runGuard;
    private final AgentProperties props;

    /**
     * Runs an incremental sync for the given user. Classification runs in parallel via
     * {@link EmailProcessingService#processBatch} (same path used by initial sync and history
     * backfill) instead of one LLM call at a time, since callers may block on this synchronously.
     *
     * @throws IllegalStateException if an initial sync is still running, or if the
     *                               RunGuard cooldown has not expired yet — callers may
     *                               let this propagate to a 409 response
     */
    public void syncUser(UUID userId) {
        if (initialSyncService.getStatus(userId) == InitialSyncService.SyncStatus.RUNNING) {
            throw new IllegalStateException("Initial sync is still in progress, please wait");
        }
        runGuard.acquire(userId);

        OffsetDateTime syncStartedAt = OffsetDateTime.now();
        OffsetDateTime since = watermark.getWatermark(userId);

        try {
            List<EmailMessage> emails = emailService.fetchRecent(userId, props.getMaxEmailsPerRun(), since);
            processingService.processBatch(userId, emails);
            watermark.advance(userId, syncStartedAt);
            log.info("Sync complete for user {} since {} — fetched {} emails", userId, since, emails.size());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Sync failed for user {}: {}", userId, e.getMessage(), e);
        } finally {
            runGuard.release(userId);
        }
    }
}