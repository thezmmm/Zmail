package com.zmail.agent.node;

import com.zmail.agent.EmailAgentState;
import com.zmail.agent.SummaryResult;
import com.zmail.email.EmailMeta;
import com.zmail.email.EmailMessage;
import com.zmail.memory.MemoryService;
import com.zmail.service.EmailService;
import com.zmail.service.LlmRetryHelper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReplyNode implements NodeAction<EmailAgentState> {

    @Qualifier("summarizeModel")
    private final ChatLanguageModel summarizeModel;
    private final EmailService emailService;
    private final MemoryService memoryService;
    private final LlmRetryHelper retry;

    @Override
    public Map<String, Object> apply(EmailAgentState state) throws Exception {
        List<String> ids = state.replyIds();
        if (ids.isEmpty()) return Map.of();

        UUID userId = state.userId();
        Map<String, EmailMeta> metaMap = state.emailMetaMap();
        Map<String, SummaryResult> summaries = state.summaries();
        Map<String, String> replyDrafts = new HashMap<>();

        for (String id : ids) {
            EmailMeta meta = metaMap.get(id);
            if (meta == null) continue;

            String draft = generateReply(meta, summaries.get(id), userId);
            replyDrafts.put(id, draft);

            // ── Human-in-Loop: persist draft for review, do NOT auto-send ──
            try {
                memoryService.saveReplyDraft(userId, id, draft);
                log.info("Reply draft saved for review — email {}", id);
            } catch (Exception e) {
                log.error("Could not persist reply draft for {}: {}", id, e.getMessage());
            }
        }

        return Map.of(EmailAgentState.REPLY_DRAFTS, replyDrafts);
    }

    private String generateReply(EmailMeta meta, SummaryResult summary, UUID userId) {
        String context = (summary != null && !summary.summary().isBlank())
                ? summary.summary()
                : fetchBodySnippet(userId, meta);

        String prompt = """
                Draft a brief, professional email reply. Return ONLY the reply body, no subject line.

                Original from: %s
                Subject: %s
                Context: %s

                Write a concise and helpful reply in 2-4 sentences.
                """.formatted(meta.sender(), meta.subject(), context);

        return retry.call(
                "reply:" + meta.providerId(),
                () -> summarizeModel.chat(prompt).trim(),
                "Thank you for your email. I will get back to you shortly."
        );
    }

    private String fetchBodySnippet(UUID userId, EmailMeta meta) {
        try {
            EmailMessage full = emailService.fetchById(userId, meta.accountId(), meta.providerId());
            String body = full.body();
            return body != null && body.length() > 1000 ? body.substring(0, 1000) + "..." : body;
        } catch (Exception e) {
            log.warn("Could not fetch body for reply context {}: {}", meta.providerId(), e.getMessage());
            return "(email content unavailable)";
        }
    }
}