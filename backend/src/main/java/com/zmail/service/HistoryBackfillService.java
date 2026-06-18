package com.zmail.service;

import com.zmail.email.EmailMessage;
import com.zmail.model.EmailAccount;
import com.zmail.model.EmailAccountRepository;
import com.zmail.model.ProcessingResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * On-demand historical backfill: pulls older emails for a user's accounts, one batch at a
 * time, until enough rows exist locally to satisfy a requested page. Triggered implicitly by
 * ResultController when the user pages past the end of what's already synced — there is no
 * standalone "sync" button; pagination itself drives how much history gets pulled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoryBackfillService {

    private static final int BATCH_SIZE = 50;
    /** Safety cap on batches per request, so a single page load can't run away fetching forever. */
    private static final int MAX_BATCHES_PER_CALL = 10;

    private final EmailService emailService;
    private final EmailProcessingService processingService;
    private final EmailAccountRepository emailAccountRepository;
    private final ProcessingResultRepository processingResultRepository;

    /** Pulls older emails across all of the user's accounts until at least minRows are synced. */
    public void ensureBackfilled(UUID userId, long minRows) {
        List<EmailAccount> accounts = emailAccountRepository.findAllByUserId(userId);
        long current = processingResultRepository.countByUserId(userId);

        for (int i = 0; current < minRows && i < MAX_BATCHES_PER_CALL; i++) {
            boolean progressed = false;
            for (EmailAccount account : accounts) {
                if (account.isHistoryBackfillComplete() || account.isNeedsReauth()) continue;
                BatchResult result = fetchOneBatch(userId, account);
                if (result.count() > 0) progressed = true;
            }
            if (!progressed) break;
            current = processingResultRepository.countByUserId(userId);
        }
    }

    private record BatchResult(int count, boolean complete) {}

    private BatchResult fetchOneBatch(UUID userId, EmailAccount account) {
        OffsetDateTime before = account.getHistoryBackfillBefore();
        if (before == null) {
            before = processingResultRepository.findEarliestReceivedAt(account.getId())
                    .orElse(OffsetDateTime.now());
        }

        List<EmailMessage> batch;
        try {
            batch = emailService.fetchBefore(userId, account.getId(), BATCH_SIZE, before);
        } catch (Exception e) {
            log.warn("History backfill batch failed for account {} ({}): {}",
                    account.getId(), account.getAccountEmail(), e.getMessage());
            return new BatchResult(0, false);
        }

        if (batch.isEmpty()) {
            account.setHistoryBackfillComplete(true);
            emailAccountRepository.save(account);
            return new BatchResult(0, true);
        }

        processingService.processBatch(userId, batch);

        OffsetDateTime newCursor = batch.stream()
                .map(EmailMessage::receivedAt)
                .min(Comparator.naturalOrder())
                .orElse(before);
        boolean complete = batch.size() < BATCH_SIZE;

        account.setHistoryBackfillBefore(newCursor);
        account.setHistoryBackfillComplete(complete);
        emailAccountRepository.save(account);

        return new BatchResult(batch.size(), complete);
    }
}
