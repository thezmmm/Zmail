package com.zmail.scheduler;

import com.zmail.config.AgentProperties;
import com.zmail.email.EmailMessage;
import com.zmail.model.EmailAccountRepository;
import com.zmail.service.EmailProcessingService;
import com.zmail.service.EmailService;
import com.zmail.service.RunGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        // RunGuard enforces a 60-second cooldown (minRunIntervalMs) after each release().
        // InitialSyncService.triggerAsync() calls processBatch() directly and does NOT
        // go through RunGuard, so it never sets lastRunAt. The first scheduled run after
        // initial sync will therefore always succeed — the cooldown only kicks in once
        // EmailSyncJob itself has completed at least one run for this user.
        try {
            runGuard.acquire(userId);
        } catch (IllegalStateException e) {
            log.debug("Skipping user {} — {}", userId, e.getMessage());
            return;
        }

        int processed = 0;
        int skipped = 0;

        try {
            List<EmailMessage> emails = emailService.fetchUnread(userId, props.getMaxEmailsPerRun());

            for (EmailMessage email : emails) {
                if (processingService.isAlreadyProcessed(userId, email.providerId())) {
                    skipped++;
                    continue;
                }
                try {
                    processingService.process(userId, email.accountId(), email);
                    processed++;
                } catch (Exception e) {
                    log.error("Failed to process email {} for user {}: {}",
                            email.providerId(), userId, e.getMessage());
                }
            }

            log.info("Synced user {} — processed={} skipped={} total={}",
                    userId, processed, skipped, emails.size());
        } catch (Exception e) {
            log.error("Sync failed for user {}: {}", userId, e.getMessage(), e);
        } finally {
            runGuard.release(userId);
        }
    }
}