package com.zmail.agent.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.ClassificationResult;
import com.zmail.agent.EmailAgentState;
import com.zmail.email.EmailMeta;
import com.zmail.email.EmailMessage;
import com.zmail.service.EmailService;
import com.zmail.service.LlmRetryHelper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClassifyNode implements NodeAction<EmailAgentState> {

    @Qualifier("classifyModel")
    private final ChatLanguageModel classifyModel;
    private final ObjectMapper      objectMapper;
    private final EmailService      emailService;
    private final LlmRetryHelper    retry;

    @Qualifier("agentExecutor")
    private final Executor executor;

    private static final int BODY_LIMIT = 2000;

    @Override
    public Map<String, Object> apply(EmailAgentState state) throws Exception {
        UUID userId = state.userId();
        Map<String, EmailMeta> metaMap = state.emailMetaMap();

        // ── Submit all classify tasks concurrently ────────────────────────────
        List<String> ids = new ArrayList<>(state.emailIds());
        List<CompletableFuture<ClassificationResult>> futures = ids.stream()
                .map(id -> {
                    EmailMeta meta = metaMap.get(id);
                    if (meta == null) {
                        return CompletableFuture.completedFuture(ClassificationResult.defaultResult());
                    }
                    return CompletableFuture
                            .supplyAsync(() -> classifyOne(userId, meta), executor)
                            .exceptionally(ex -> {
                                log.error("Classify failed for {}: {}", id, ex.getMessage());
                                return ClassificationResult.defaultResult();
                            });
                })
                .toList();

        // ── Wait for all and collect ──────────────────────────────────────────
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<String, ClassificationResult> classifications = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            classifications.put(ids.get(i),
                    futures.get(i).getNow(ClassificationResult.defaultResult()));
        }

        log.info("Classified {} emails (parallel, pool≤{})", classifications.size(),
                executor.toString());
        return Map.of(EmailAgentState.CLASSIFICATIONS, classifications);
    }

    private ClassificationResult classifyOne(UUID userId, EmailMeta meta) {
        EmailMessage email = fetchBody(userId, meta);
        return classify(email != null ? email : bodylessMessage(meta));
    }

    private EmailMessage fetchBody(UUID userId, EmailMeta meta) {
        try {
            return emailService.fetchById(userId, meta.accountId(), meta.providerId());
        } catch (Exception e) {
            log.warn("Could not fetch body for {}: {}", meta.providerId(), e.getMessage());
            return null;
        }
    }

    private EmailMessage bodylessMessage(EmailMeta meta) {
        return new EmailMessage(meta.providerId(), meta.accountId(),
                meta.subject(), meta.sender(), List.of(), meta.receivedAt(), "");
    }

    private ClassificationResult classify(EmailMessage email) {
        String body = email.body() != null && email.body().length() > BODY_LIMIT
                ? email.body().substring(0, BODY_LIMIT) + "..."
                : email.body();

        String prompt = """
                Classify the following email. Respond with ONLY a valid JSON object, no markdown:
                {
                  "category": "<WORK|PERSONAL|NEWSLETTER|SPAM|FINANCE|TRAVEL|SOCIAL|OTHER>",
                  "priority": "<HIGH|MEDIUM|LOW>",
                  "sentiment": "<POSITIVE|NEGATIVE|NEUTRAL>",
                  "requiresResponse": <true|false>
                }

                Subject: %s
                From: %s
                Body:
                %s
                """.formatted(email.subject(), email.sender(), body);

        return retry.call(
                "classify:" + email.providerId(),
                () -> objectMapper.readValue(stripMarkdown(classifyModel.chat(prompt)),
                        ClassificationResult.class),
                ClassificationResult.defaultResult()
        );
    }

    private String stripMarkdown(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int s = t.indexOf('{'), e = t.lastIndexOf('}');
            if (s >= 0 && e >= s) return t.substring(s, e + 1);
        }
        return t;
    }
}
