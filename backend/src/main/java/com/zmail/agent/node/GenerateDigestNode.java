package com.zmail.agent.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.DigestAgentState;
import com.zmail.agent.DigestResult;
import com.zmail.agent.EmailDigest;
import com.zmail.agent.SummaryResult;
import com.zmail.email.EmailMeta;
import com.zmail.service.LlmRetryHelper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateDigestNode implements NodeAction<DigestAgentState> {

    @Qualifier("summarizeModel")
    private final ChatLanguageModel summarizeModel;
    private final ObjectMapper objectMapper;
    private final LlmRetryHelper retry;

    @Override
    public Map<String, Object> apply(DigestAgentState state) throws Exception {
        List<String> ids = state.emailIds();
        if (ids.isEmpty()) {
            return Map.of(DigestAgentState.DIGEST, DigestResult.empty());
        }

        Map<String, SummaryResult> summaries = state.summaries();
        Map<String, EmailMeta> metaMap = state.emailMetaMap();

        // Build per-email digest list
        List<EmailDigest> emailDigests = ids.stream()
                .map(id -> {
                    EmailMeta meta = metaMap.getOrDefault(id,
                            new EmailMeta(id, null, "Unknown", "Unknown", null));
                    SummaryResult sum = summaries.getOrDefault(id, SummaryResult.defaultResult());
                    return new EmailDigest(id, meta.subject(), meta.sender(),
                            sum.summary(), sum.actionItems());
                })
                .toList();

        // Build condensed input for the overview + priorities prompt
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emailDigests.size(); i++) {
            EmailDigest d = emailDigests.get(i);
            sb.append(i + 1).append(". From: ").append(d.sender())
              .append(" | Subject: ").append(d.subject())
              .append("\n   Summary: ").append(d.summary());
            if (!d.actionItems().isEmpty()) {
                sb.append("\n   Action items: ").append(String.join("; ", d.actionItems()));
            }
            sb.append("\n");
        }

        String prompt = """
                You are an email assistant. Based on the following email summaries, provide:
                1. A brief overall overview (2-3 sentences) of what the user's inbox looks like today.
                2. A prioritized list of the top 3-5 action items the user should focus on.

                Respond ONLY with a valid JSON object, no markdown:
                {
                  "overview": "<overall summary>",
                  "priorities": ["<action 1>", "<action 2>", ...]
                }

                Emails:
                %s
                """.formatted(sb);

        record OverviewResponse(String overview, List<String> priorities) {}

        OverviewResponse overview = retry.call(
                "generateDigest",
                () -> objectMapper.readValue(stripMarkdown(summarizeModel.chat(prompt)),
                        OverviewResponse.class),
                new OverviewResponse("Here is your email digest.", List.of())
        );

        DigestResult result = new DigestResult(overview.overview(), emailDigests, overview.priorities());
        log.info("Generated digest for {} emails", ids.size());
        return Map.of(DigestAgentState.DIGEST, result);
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