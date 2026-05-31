package com.zmail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.action.ActionAgentService;
import com.zmail.agent.action.EmailProcessingAgent;
import com.zmail.agent.action.EmailSummarizeAgent;
import com.zmail.agent.model.ActionType;
import com.zmail.agent.model.DraftStatus;
import com.zmail.agent.model.EmailRef;
import com.zmail.email.EmailMessage;
import com.zmail.model.ProcessingResult;
import com.zmail.model.ProcessingResultRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingService {

    @Qualifier("classifyModel")
    private final ChatLanguageModel classifyModel;
    @Qualifier("summarizeModel")
    private final ChatLanguageModel summarizeModel;
    @Qualifier("agentExecutor")
    private final Executor agentExecutor;
    private final ObjectMapper objectMapper;
    private final ProcessingResultRepository resultRepository;
    private final EmailService emailService;
    private final ActionAgentService actionAgentService;
    private final LlmRetryHelper retry;
    private final EmailEmbeddingService emailEmbeddingService;

    private static final int BODY_LIMIT = 3000;

    private EmailProcessingAgent processingAgent;
    private EmailSummarizeAgent summarizeAgent;

    @PostConstruct
    void init() {
        processingAgent = AiServices.builder(EmailProcessingAgent.class)
                .chatLanguageModel(classifyModel)
                .build();
        summarizeAgent = AiServices.builder(EmailSummarizeAgent.class)
                .chatLanguageModel(summarizeModel)
                .build();
    }

    /** Processes a batch of emails concurrently using the agent thread pool. */
    public void processBatch(UUID userId, List<EmailMessage> emails) {
        List<CompletableFuture<Void>> futures = emails.stream()
                .filter(email -> !isAlreadyProcessed(userId, email.providerId()))
                .map(email -> CompletableFuture.runAsync(() -> {
                    try {
                        classify(userId, email.accountId(), email);
                    } catch (Exception e) {
                        log.error("Failed to classify email {} for user {}: {}",
                                email.providerId(), userId, e.getMessage());
                    }
                }, agentExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("processBatch complete for user {} — {} emails submitted", userId, futures.size());
    }

    public boolean isAlreadyProcessed(UUID userId, String emailProviderId) {
        return resultRepository.existsByUserIdAndEmailProviderId(userId, emailProviderId);
    }

    /** Background classify-only — fast, cheap, no summary/actions. */
    public void classify(UUID userId, UUID accountId, EmailMessage email) {
        ClassifyResult classification = runClassify(email);

        ProcessingResult result = new ProcessingResult();
        result.setUserId(userId);
        result.setEmailProviderId(email.providerId());
        result.setAccountId(accountId);
        result.setSubject(email.subject());
        result.setSender(email.sender());
        result.setCategory(classification.category().toUpperCase());
        result.setPriority(classification.priority().toUpperCase());
        result.setSentiment(classification.sentiment().toUpperCase());
        result.setRequiresResponse(classification.requiresResponse());
        result.setActionTaken(parseAction(classification.recommendedAction()));
        result.setReceivedAt(email.receivedAt());
        result.setProcessedAt(OffsetDateTime.now());
        result.setAnalyzed(false);

        resultRepository.save(result);
        log.info("Classified email {} for user {} → {}", email.providerId(), userId, result.getActionTaken());
    }

    /** On-demand analysis triggered on first view — summarize, extract action items, embed. */
    @Transactional
    public ProcessingResult analyzeOnDemand(UUID userId, UUID resultId) {
        ProcessingResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new NoSuchElementException("Result not found: " + resultId));
        if (!result.getUserId().equals(userId)) throw new SecurityException("Access denied");
        if (result.isAnalyzed()) return result;

        EmailMessage email;
        try {
            email = emailService.fetchById(userId, result.getAccountId(), result.getEmailProviderId());
        } catch (Exception e) {
            log.warn("Could not fetch email body for analysis {}: {}", resultId, e.getMessage());
            result.setAnalyzed(true);
            return resultRepository.save(result);
        }

        SummarizeResult summarized = runSummarize(email);
        result.setSummary(summarized.summary());
        result.setActionItems(toJsonArray(summarized.actionItems()));
        result.setAnalyzed(true);
        ProcessingResult saved = resultRepository.save(result);

        emailEmbeddingService.embed(saved);
        return saved;
    }

    /** User-initiated draft generation. */
    @Transactional
    public ProcessingResult generateDraft(UUID userId, UUID resultId) {
        ProcessingResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new NoSuchElementException("Result not found: " + resultId));
        if (!result.getUserId().equals(userId)) throw new SecurityException("Access denied");

        EmailRef ref = new EmailRef(result.getEmailProviderId(), result.getAccountId());
        String draft = actionAgentService.draftReply(userId, ref, "Draft a professional reply to this email.");
        result.setReplyDraft(draft);
        result.setDraftStatus(DraftStatus.PENDING_REVIEW);
        return resultRepository.save(result);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private ClassifyResult runClassify(EmailMessage email) {
        String content = buildContent(email);
        return retry.call(
                "classify:" + email.providerId(),
                () -> objectMapper.readValue(
                        stripMarkdown(processingAgent.analyze(content)),
                        ClassifyResult.class),
                new ClassifyResult("OTHER", "LOW", "NEUTRAL", false, "NONE")
        );
    }

    private SummarizeResult runSummarize(EmailMessage email) {
        String content = buildContent(email);
        return retry.call(
                "summarize:" + email.providerId(),
                () -> objectMapper.readValue(
                        stripMarkdown(summarizeAgent.summarize(content)),
                        SummarizeResult.class),
                new SummarizeResult("Could not summarize this email.", List.of())
        );
    }

    private String buildContent(EmailMessage email) {
        String body = email.body() != null && email.body().length() > BODY_LIMIT
                ? email.body().substring(0, BODY_LIMIT) + "..."
                : email.body();
        return """
                Subject: %s
                From: %s
                Received: %s
                Body:
                %s
                """.formatted(email.subject(), email.sender(), email.receivedAt(), body);
    }

    private ActionType parseAction(String raw) {
        try {
            return ActionType.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ActionType.NONE;
        }
    }

    private String toJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String stripMarkdown(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int s = t.indexOf('{'), e = t.lastIndexOf('}');
            if (s >= 0 && e >= s) return t.substring(s, e + 1);
        }
        return t;
    }

    private record ClassifyResult(
            String category,
            String priority,
            String sentiment,
            boolean requiresResponse,
            String recommendedAction
    ) {}

    private record SummarizeResult(
            String summary,
            List<String> actionItems
    ) {}
}
