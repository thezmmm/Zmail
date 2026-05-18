package com.zmail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.action.ActionAgentService;
import com.zmail.agent.action.EmailProcessingAgent;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingService {

    @Qualifier("classifyModel")
    private final ChatLanguageModel classifyModel;
    @Qualifier("agentExecutor")
    private final Executor agentExecutor;
    private final ObjectMapper objectMapper;
    private final ProcessingResultRepository resultRepository;
    private final EmailService emailService;
    private final ActionAgentService actionAgentService;
    private final LlmRetryHelper retry;

    private static final int BODY_LIMIT = 3000;

    private EmailProcessingAgent processingAgent;

    @PostConstruct
    void init() {
        processingAgent = AiServices.builder(EmailProcessingAgent.class)
                .chatLanguageModel(classifyModel)
                .build();
    }

    /** Processes a batch of emails concurrently using the agent thread pool. */
    public void processBatch(UUID userId, List<EmailMessage> emails) {
        List<CompletableFuture<Void>> futures = emails.stream()
                .filter(email -> !isAlreadyProcessed(userId, email.providerId()))
                .map(email -> CompletableFuture.runAsync(() -> {
                    try {
                        process(userId, email.accountId(), email);
                    } catch (Exception e) {
                        log.error("Failed to process email {} for user {}: {}",
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

    public void process(UUID userId, UUID accountId, EmailMessage email) {
        AnalysisResult analysis = analyze(email);

        ProcessingResult result = new ProcessingResult();
        result.setUserId(userId);
        result.setEmailProviderId(email.providerId());
        result.setAccountId(accountId);
        result.setSubject(email.subject());
        result.setSender(email.sender());
        result.setCategory(analysis.category());
        result.setPriority(analysis.priority());
        result.setSentiment(analysis.sentiment());
        result.setRequiresResponse(analysis.requiresResponse());
        result.setSummary(analysis.summary());
        result.setActionItems(toJsonArray(analysis.actionItems()));
        result.setProcessedAt(OffsetDateTime.now());

        ActionType action = parseAction(analysis.recommendedAction());
        result.setActionTaken(action);

        EmailRef ref = new EmailRef(email.providerId(), accountId);
        switch (action) {
            case REPLY -> {
                String draft = actionAgentService.draftReply(userId, ref,
                        "Draft a professional reply to this email.");
                result.setReplyDraft(draft);
                result.setDraftStatus(DraftStatus.PENDING_REVIEW);
            }
            case ARCHIVE -> emailService.archive(userId, accountId, email.providerId());
            case FLAG    -> emailService.flag(userId, accountId, email.providerId());
            case NONE    -> {}
        }

        resultRepository.save(result);
        log.info("Processed email {} for user {} → {}", email.providerId(), userId, action);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private AnalysisResult analyze(EmailMessage email) {
        String body = email.body() != null && email.body().length() > BODY_LIMIT
                ? email.body().substring(0, BODY_LIMIT) + "..."
                : email.body();

        String content = """
                Subject: %s
                From: %s
                Received: %s
                Body:
                %s
                """.formatted(email.subject(), email.sender(), email.receivedAt(), body);

        return retry.call(
                "processEmail:" + email.providerId(),
                () -> objectMapper.readValue(
                        stripMarkdown(processingAgent.analyze(content)),
                        AnalysisResult.class),
                new AnalysisResult("other", "low", "neutral", false,
                        "Could not analyze this email.", List.of(), "NONE")
        );
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

    private record AnalysisResult(
            String category,
            String priority,
            String sentiment,
            boolean requiresResponse,
            String summary,
            List<String> actionItems,
            String recommendedAction
    ) {}
}