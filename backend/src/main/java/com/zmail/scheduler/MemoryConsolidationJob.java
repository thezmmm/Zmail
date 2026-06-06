package com.zmail.scheduler;

import com.zmail.config.AgentProperties;
import com.zmail.model.EmailAccountRepository;
import com.zmail.model.ProcessingResult;
import com.zmail.model.ProcessingResultRepository;
import com.zmail.service.EmailEmbeddingService;
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
public class MemoryConsolidationJob {

    private final EmailAccountRepository accountRepository;
    private final ProcessingResultRepository resultRepository;
    private final EmailEmbeddingService embeddingService;
    private final RunGuard runGuard;
    private final AgentProperties props;

    @Scheduled(cron = "${zmail.scheduler.memory-consolidation-cron}")
    public void consolidate() {
        List<UUID> userIds = accountRepository.findDistinctUserIds();
        if (userIds.isEmpty()) return;

        log.info("MemoryConsolidationJob starting — {} users", userIds.size());
        for (UUID userId : userIds) {
            consolidateUser(userId);
        }
        log.info("MemoryConsolidationJob complete");
    }

    private void consolidateUser(UUID userId) {
        try {
            runGuard.acquire(userId);
        } catch (IllegalStateException e) {
            log.debug("Skipping user {} — {}", userId, e.getMessage());
            return;
        }

        try {
            List<ProcessingResult> unembedded =
                    resultRepository.findUnembedded(userId, props.getMaxEmailsPerRun());

            if (unembedded.isEmpty()) {
                log.debug("No unembedded emails for user {}", userId);
                return;
            }

            int count = 0;
            for (ProcessingResult result : unembedded) {
                embeddingService.embed(result);
                count++;
            }
            log.info("MemoryConsolidationJob user {} — embedded {} emails", userId, count);
        } catch (Exception e) {
            log.error("Memory consolidation failed for user {}: {}", userId, e.getMessage(), e);
        } finally {
            runGuard.release(userId);
        }
    }
}
