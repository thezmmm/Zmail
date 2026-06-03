package com.zmail.agent.digest.node;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.digest.DigestAgentState;
import com.zmail.agent.model.EmailRef;
import com.zmail.agent.model.SummaryResult;
import com.zmail.email.EmailMeta;
import com.zmail.email.EmailMessage;
import com.zmail.model.ProcessingResultRepository;
import com.zmail.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class FetchSelectedNode implements NodeAction<DigestAgentState> {

    private final EmailService emailService;
    private final ProcessingResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(DigestAgentState state) throws Exception {
        UUID userId = state.userId();
        List<EmailRef> refs = state.emailRefs();

        List<String> emailIds = new ArrayList<>(refs.size());
        Map<String, EmailMeta> metaMap = new HashMap<>(refs.size());
        Map<String, String> bodiesMap = new HashMap<>(refs.size());

        // Load existing DB summaries to avoid redundant LLM calls in SummarizeNode
        Map<String, SummaryResult> preSummaries = new HashMap<>();
        for (EmailRef ref : refs) {
            resultRepository
                    .findTopByUserIdAndEmailProviderIdOrderByProcessedAtDesc(userId, ref.providerId())
                    .filter(r -> r.isAnalyzed() && r.getSummary() != null && !r.getSummary().isBlank())
                    .ifPresent(r -> preSummaries.put(ref.providerId(),
                            new SummaryResult(r.getSummary(), parseActionItems(r.getActionItems()))));
        }

        // Fetch full email (body + metadata) once — body stored in state for SummarizeNode
        for (EmailRef ref : refs) {
            try {
                EmailMessage msg = emailService.fetchById(userId, ref.accountId(), ref.providerId());
                emailIds.add(msg.providerId());
                metaMap.put(msg.providerId(), new EmailMeta(
                        msg.providerId(), msg.accountId(),
                        msg.subject(), msg.sender(), msg.receivedAt()));
                bodiesMap.put(msg.providerId(), msg.body() != null ? msg.body() : "");
            } catch (Exception e) {
                log.warn("Could not fetch email {}: {}", ref.providerId(), e.getMessage());
            }
        }

        log.info("Fetched {}/{} emails for user {} ({} pre-summarized from DB)",
                emailIds.size(), refs.size(), userId, preSummaries.size());
        return Map.of(
                DigestAgentState.EMAIL_IDS,      emailIds,
                DigestAgentState.EMAIL_META_MAP, metaMap,
                DigestAgentState.EMAIL_BODIES,   bodiesMap,
                DigestAgentState.PRE_SUMMARIES,  preSummaries
        );
    }

    private List<String> parseActionItems(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
