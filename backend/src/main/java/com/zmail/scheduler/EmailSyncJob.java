package com.zmail.scheduler;

import com.zmail.config.AgentProperties;
import com.zmail.email.EmailMessage;
import com.zmail.model.EmailAccountRepository;
import com.zmail.service.EmailProcessingService;
import com.zmail.service.EmailService;
import com.zmail.service.InitialSyncService;
import com.zmail.service.RunGuard;
import com.zmail.service.SyncWatermarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailSyncJob {

    private final EmailService emailService;
    private final EmailProcessingService processingService;
    private final EmailAccountRepository accountRepository;
    private final RunGuard runGuard;
    private final AgentProperties props;
    private final SyncWatermarkService watermark;
    private final InitialSyncService initialSyncService;

    @Scheduled(cron = "${zmail.scheduler.email-sync-cron}")
    public void sync() {
        List<UUID> userIds = accountRepository.findDistinctUserIds();
        if (userIds.isEmpty()) return;

        log.info("EmailSyncJob starting — {} users", userIds.size());
        for (UUID userId : userIds) {
            syncUser(userId);
        }
        log.info("EmailSyncJob complete");
    }

    private void syncUser(UUID userId) {
        // Skip if initial sync is still running to avoid duplicate classify LLM calls
        if (initialSyncService.getStatus(userId) == InitialSyncService.SyncStatus.RUNNING) {
            log.debug("Skipping scheduled sync for user {} — initial sync in progress", userId);
            return;
        }
        try {
            runGuard.acquire(userId);
        } catch (IllegalStateException e) {
            log.debug("Skipping user {} — {}", userId, e.getMessage());
            return;
        }

        // Snapshot the sync start time before fetching so we don't miss emails
        // that arrive during the sync window.
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
            log.info("Synced user {} since {} — processed={} skipped={} total={}",
                    userId, since, processed, skipped, emails.size());
        } catch (Exception e) {
            log.error("Sync failed for user {}: {}", userId, e.getMessage(), e);
        } finally {
            runGuard.release(userId);
        }
    }
}
