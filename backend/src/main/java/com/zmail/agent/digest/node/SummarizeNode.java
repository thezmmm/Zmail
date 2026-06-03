package com.zmail.agent.digest.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.digest.DigestAgentState;
import com.zmail.agent.model.SummaryResult;
import com.zmail.email.EmailMeta;
import com.zmail.email.EmailMessage;
import com.zmail.service.LlmRetryHelper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@Slf4j
public class SummarizeNode implements NodeAction<DigestAgentState> {

    @Qualifier("summarizeModel")
    private final ChatLanguageModel summarizeModel;
    private final ObjectMapper      objectMapper;
    private final LlmRetryHelper    retry;

    @Qualifier("agentExecutor")
    private final Executor executor;

    private static final int BODY_LIMIT = 4000;

    @Override
    public Map<String, Object> apply(DigestAgentState state) throws Exception {
        Map<String, EmailMeta>    metaMap     = state.emailMetaMap();
        Map<String, String>       bodiesMap   = state.emailBodies();
        Map<String, SummaryResult> preSummaries = state.preSummaries();

        List<String> ids = new ArrayList<>(state.emailIds());
        List<CompletableFuture<SummaryResult>> futures = ids.stream()
                .map(id -> {
                    // Use existing DB summary — no LLM call needed
                    if (preSummaries.containsKey(id)) {
                        return CompletableFuture.completedFuture(preSummaries.get(id));
                    }
                    EmailMeta meta = metaMap.get(id);
                    if (meta == null) {
                        return CompletableFuture.completedFuture(SummaryResult.defaultResult());
                    }
                    // Body already in state — no second API fetch needed
                    String body = bodiesMap.getOrDefault(id, "");
                    return CompletableFuture
                            .supplyAsync(() -> summarizeOne(meta, body), executor)
                            .exceptionally(ex -> {
                                log.error("Summarize failed for {}: {}", id, ex.getMessage());
                                return SummaryResult.defaultResult();
                            });
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Map<String, SummaryResult> summaries = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            summaries.put(ids.get(i), futures.get(i).getNow(SummaryResult.defaultResult()));
        }

        long llmCalls = ids.stream().filter(id -> !preSummaries.containsKey(id)).count();
        log.info("Summarized {} emails ({} from DB cache, {} via LLM)", ids.size(),
                ids.size() - llmCalls, llmCalls);
        return Map.of(DigestAgentState.SUMMARIES, summaries);
    }

    private SummaryResult summarizeOne(EmailMeta meta, String body) {
        String truncated = body.length() > BODY_LIMIT ? body.substring(0, BODY_LIMIT) + "..." : body;
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
                """.formatted(meta.subject(), meta.sender(), meta.receivedAt(), truncated);

        return retry.call(
                "summarize:" + meta.providerId(),
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
