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
     * Runs an incremental sync for the given user.
     *
     * @return number of newly classified emails
     * @throws IllegalStateException if an initial sync is still running, or if the
     *                               RunGuard cooldown has not expired yet — callers may
     *                               let this propagate to a 409 response
     */
    public int syncUser(UUID userId) {
        if (initialSyncService.getStatus(userId) == InitialSyncService.SyncStatus.RUNNING) {
            throw new IllegalStateException("Initial sync is still in progress, please wait");
        }
        runGuard.acquire(userId);

        OffsetDateTime syncStartedAt = OffsetDateTime.now();
        OffsetDateTime since = watermark.getWatermark(userId);
        int processed = 0;
        int skipped = 0;

        try {
            List<EmailMessage> emails = emailService.fetchRecent(userId, props.getMaxEmailsPerRun(), since);

            for (EmailMessage email : emails) {
                if (processingService.isAlreadyProcessed(userId, email.providerId())) {
                    skipped++;
                    continue;
                }
                try {
                    processingService.classify(userId, email.accountId(), email);
                    processed++;
                } catch (Exception e) {
                    log.error("Failed to classify email {} for user {}: {}",
                            email.providerId(), userId, e.getMessage());
                }
            }

            watermark.advance(userId, syncStartedAt);
            log.info("Sync complete for user {} since {} — processed={} skipped={} total={}",
                    userId, since, processed, skipped, emails.size());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Sync failed for user {}: {}", userId, e.getMessage(), e);
        } finally {
            runGuard.release(userId);
        }

        return processed;
    }
}