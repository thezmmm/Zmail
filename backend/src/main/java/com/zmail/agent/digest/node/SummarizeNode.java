package com.zmail.agent.digest.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.digest.DigestAgentState;
import com.zmail.agent.model.SummaryResult;
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
public class SummarizeNode implements NodeAction<DigestAgentState> {

    @Qualifier("summarizeModel")
    private final ChatLanguageModel summarizeModel;
    private final ObjectMapper      objectMapper;
    private final EmailService      emailService;
    private final LlmRetryHelper    retry;

    @Qualifier("agentExecutor")
    private final Executor executor;

    private static final int BODY_LIMIT = 4000;

    @Override
    public Map<String, Object> apply(DigestAgentState state) throws Exception {
        UUID userId = state.userId();
        Map<String, EmailMeta> metaMap = state.emailMetaMap();

        // ── Submit all summarize tasks concurrently ───────────────────────────
        List<String> ids = new ArrayList<>(state.emailIds());
        List<CompletableFuture<SummaryResult>> futures = ids.stream()
                .map(id -> {
                    EmailMeta meta = metaMap.get(id);
                    if (meta == null) {
                        return CompletableFuture.completedFuture(SummaryResult.defaultResult());
                    }
                    return CompletableFuture
                            .supplyAsync(() -> summarizeOne(userId, meta), executor)
                            .exceptionally(ex -> {
                                log.error("Summarize failed for {}: {}", id, ex.getMessage());
                                return SummaryResult.defaultResult();
                            });
                })
                .toList();

        // ── Wait for all and collect ──────────────────────────────────────────
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<String, SummaryResult> summaries = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            summaries.put(ids.get(i), futures.get(i).getNow(SummaryResult.defaultResult()));
        }

        log.info("Summarized {} emails (parallel, pool≤{})", summaries.size(),
                executor.toString());
        return Map.of(DigestAgentState.SUMMARIES, summaries);
    }

    private SummaryResult summarizeOne(UUID userId, EmailMeta meta) {
        EmailMessage email = fetchBody(userId, meta);
        return summarize(email != null ? email : bodylessMessage(meta));
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

    private SummaryResult summarize(EmailMessage email) {
        String body = email.body() != null && email.body().length() > BODY_LIMIT
                ? email.body().substring(0, BODY_LIMIT) + "..."
                : email.body();

        String prompt = """
                Summarize the following email concisely. Respond with ONLY a valid JSON object, no markdown:
                {
                  "summary": "<2-3 sentence summary>",
                  "actionItems": ["<item 1>", "<item 2>"]
                }
                If there are no action items, use [].

                Subject: %s
                From: %s
                Received: %s
                Body:
                %s
                """.formatted(email.subject(), email.sender(), email.receivedAt(), body);

        return retry.call(
                "summarize:" + email.providerId(),
                () -> objectMapper.readValue(stripMarkdown(summarizeModel.chat(prompt)),
                        SummaryResult.class),
                SummaryResult.defaultResult()
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