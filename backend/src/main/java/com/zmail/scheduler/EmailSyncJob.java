package com.zmail.scheduler;

import com.zmail.model.EmailAccountRepository;
import com.zmail.service.EmailSyncService;
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

    private final EmailAccountRepository accountRepository;
    private final EmailSyncService syncService;

    @Scheduled(cron = "${zmail.scheduler.email-sync-cron}")
    public void sync() {
        List<UUID> userIds = accountRepository.findDistinctUserIds();
        if (userIds.isEmpty()) return;

        log.info("EmailSyncJob starting — {} users", userIds.size());
        for (UUID userId : userIds) {
            try {
                syncService.syncUser(userId);
            } catch (IllegalStateException e) {
                log.debug("Skipping user {} — {}", userId, e.getMessage());
            } catch (Exception e) {
                log.error("Sync failed for user {}: {}", userId, e.getMessage(), e);
            }
        }
        log.info("EmailSyncJob complete");
    }
}
