package com.zmail.service;

import com.zmail.email.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InitialSyncService {

    private static final int INITIAL_SYNC_DAYS = 3;
    private static final int INITIAL_SYNC_MAX  = 100;

    private final EmailService emailService;
    private final EmailProcessingService processingService;

    /**
     * Triggered asynchronously after a user connects an email account.
     * Fetches and processes the last INITIAL_SYNC_DAYS days of emails in parallel.
     */
    @Async
    public void triggerAsync(UUID userId) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(INITIAL_SYNC_DAYS);
        log.info("InitialSync started for user {} (last {} days)", userId, INITIAL_SYNC_DAYS);
        try {
            List<EmailMessage> emails = emailService.fetchRecent(userId, INITIAL_SYNC_MAX, since);
            log.info("InitialSync fetched {} emails for user {}", emails.size(), userId);
            processingService.processBatch(userId, emails);
            log.info("InitialSync complete for user {}", userId);
        } catch (Exception e) {
            log.error("InitialSync failed for user {}: {}", userId, e.getMessage(), e);
        }
    }
}