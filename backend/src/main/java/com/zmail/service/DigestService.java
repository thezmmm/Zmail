package com.zmail.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.digest.DigestAgentGraph;
import com.zmail.agent.model.DigestResult;
import com.zmail.agent.model.EmailDigest;
import com.zmail.agent.model.SummaryResult;
import com.zmail.config.AgentProperties;
import com.zmail.email.EmailMeta;
import com.zmail.model.DailyDigest;
import com.zmail.model.DailyDigestDto;
import com.zmail.model.DailyDigestRepository;
import com.zmail.model.ProcessingResult;
import com.zmail.model.ProcessingResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DigestService {

    private final DailyDigestRepository digestRepository;
    private final ProcessingResultRepository resultRepository;
    private final EmailProcessingService processingService;
    private final DigestAgentGraph digestAgentGraph;
    private final ObjectMapper objectMapper;
    private final AgentProperties props;

    @Qualifier("agentExecutor")
    private final Executor agentExecutor;

    /** Prevents concurrent digest generation per user. */
    private final ConcurrentHashMap<UUID, AtomicBoolean> inProgress = new ConcurrentHashMap<>();

    public Optional<DailyDigestDto> findToday(UUID userId) {
        return digestRepository.findByUserIdAndDigestDate(userId, LocalDate.now())
                .map(this::toDto);
    }

    public DailyDigestDto generate(UUID userId, boolean force) {
        AtomicBoolean flag = inProgress.computeIfAbsent(userId, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            throw new IllegalStateException("Digest generation already in progress");
        }
        try {
            return doGenerate(userId, force);
        } finally {
            flag.set(false);
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private DailyDigestDto doGenerate(UUID userId, boolean force) {
        LocalDate today = LocalDate.now();

        if (!force) {
            Optional<DailyDigest> existing = digestRepository.findByUserIdAndDigestDate(userId, today);
            if (existing.isPresent()) return toDto(existing.get());
        }

        OffsetDateTime since = OffsetDateTime.now().minusDays(1);
        List<ProcessingResult> recent = resultRepository.findByUserIdAndProcessedAtAfter(
                userId, since,
                PageRequest.of(0, props.getMaxEmailsPerRun(), Sort.by("processedAt").descending())
        ).getContent();

        recent = ensureAnalyzed(userId, since, recent);

        DigestResult result;
        if (recent.isEmpty()) {
            log.info("DigestService: no emails in past 24h for user {}", userId);
            result = DigestResult.empty();
        } else {
            // Use ProcessingResult UUID as the map key so EmailDigest.emailId
            // becomes the UUID needed for /emails/{id} navigation.
            List<String> emailIds = recent.stream()
                    .map(r -> r.getId().toString())
                    .toList();

            Map<String, EmailMeta> metaMap = recent.stream().collect(Collectors.toMap(
                    r -> r.getId().toString(),
                    r -> new EmailMeta(r.getEmailProviderId(), r.getAccountId(),
                            r.getSubject(), r.getSender(), r.getReceivedAt())
            ));

            Map<String, SummaryResult> summaries = recent.stream().collect(Collectors.toMap(
                    r -> r.getId().toString(),
                    r -> new SummaryResult(
                            r.getSummary() != null ? r.getSummary() : "",
                            parseActionItems(r.getActionItems())
                    )
            ));

            String sessionId = "daily-" + today + "-" + userId;
            result = digestAgentGraph.runFromSummaries(userId, sessionId, emailIds, metaMap, summaries);
            log.info("DigestService: generated digest for user {} ({} emails)", userId, emailIds.size());
        }

        DailyDigest entity = digestRepository.findByUserIdAndDigestDate(userId, today)
                .orElseGet(DailyDigest::new);
        entity.setUserId(userId);
        entity.setDigestDate(today);
        entity.setOverview(result.overview());
        entity.setEmails(toJson(result.emails()));
        entity.setPriorities(toJson(result.priorities()));

        return toDto(digestRepository.save(entity));
    }

    /**
     * Triggers on-demand analysis (in parallel, bounded by agentExecutor) for any
     * emails that haven't been summarized yet, then re-fetches so the digest has
     * full summaries and action items rather than just subject+sender.
     */
    private List<ProcessingResult> ensureAnalyzed(UUID userId, OffsetDateTime since,
                                                   List<ProcessingResult> results) {
        // Cap pre-analysis to avoid blocking the request for too long.
        // Emails beyond the cap will contribute only subject+sender to the digest.
        int analysisCap = Math.min(props.getMaxParallelLlmCalls() * 4, 20);
        List<ProcessingResult> unanalyzed = results.stream()
                .filter(r -> !r.isAnalyzed())
                .limit(analysisCap)
                .toList();
        if (unanalyzed.isEmpty()) return results;

        log.info("DigestService: analyzing {} unanalyzed emails for user {} before digest",
                unanalyzed.size(), userId);

        List<CompletableFuture<Void>> futures = unanalyzed.stream()
                .map(r -> CompletableFuture.runAsync(() -> {
                    try {
                        processingService.analyzeOnDemand(userId, r.getId());
                    } catch (Exception e) {
                        log.warn("Pre-digest analysis failed for email {}: {}", r.getId(), e.getMessage());
                    }
                }, agentExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return resultRepository.findByUserIdAndProcessedAtAfter(
                userId, since,
                PageRequest.of(0, props.getMaxEmailsPerRun(), Sort.by("processedAt").descending())
        ).getContent();
    }

    private DailyDigestDto toDto(DailyDigest entity) {
        return new DailyDigestDto(
                entity.getId(),
                entity.getUserId(),
                entity.getDigestDate(),
                entity.getOverview(),
                fromJson(entity.getEmails(), new TypeReference<List<EmailDigest>>() {}),
                fromJson(entity.getPriorities(), new TypeReference<List<String>>() {}),
                entity.getCreatedAt()
        );
    }

    private List<String> parseActionItems(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            if (json == null || json.isBlank()) return objectMapper.readValue("[]", type);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            try {
                return objectMapper.readValue("[]", type);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to deserialize JSON", ex);
            }
        }
    }
}
