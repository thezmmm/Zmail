package com.zmail.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.ActionType;
import com.zmail.agent.ClassificationResult;
import com.zmail.agent.DraftStatus;
import com.zmail.agent.SummaryResult;
import com.zmail.email.EmailMeta;
import com.zmail.model.ProcessingResult;
import com.zmail.model.ProcessingResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {

    private final ProcessingResultRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveDecisionBatch(
            UUID userId,
            List<String> emailIds,
            Map<String, EmailMeta> emailMetaMap,
            Map<String, ClassificationResult> classifications,
            Map<String, SummaryResult> summaries,
            Map<String, ActionType> decisions) {

        OffsetDateTime now = OffsetDateTime.now();
        List<ProcessingResult> rows = emailIds.stream()
                .map(id -> buildRow(userId, id, emailMetaMap, classifications, summaries, decisions, now))
                .toList();

        repository.saveAll(rows);
        log.info("Persisted {} processing results for user {}", rows.size(), userId);
    }

    private ProcessingResult buildRow(
            UUID userId,
            String emailId,
            Map<String, EmailMeta> emailMetaMap,
            Map<String, ClassificationResult> classifications,
            Map<String, SummaryResult> summaries,
            Map<String, ActionType> decisions,
            OffsetDateTime processedAt) {

        EmailMeta meta = emailMetaMap.getOrDefault(emailId,
                new EmailMeta(emailId, null, "", "", null));
        ClassificationResult clf = classifications.getOrDefault(emailId, ClassificationResult.defaultResult());
        SummaryResult sum = summaries.getOrDefault(emailId, SummaryResult.defaultResult());
        ActionType action = decisions.getOrDefault(emailId, ActionType.NONE);

        ProcessingResult row = new ProcessingResult();
        row.setUserId(userId);
        row.setEmailProviderId(emailId);
        row.setAccountId(meta.accountId());
        row.setSubject(meta.subject());
        row.setSender(meta.sender());
        row.setCategory(clf.category());
        row.setPriority(clf.priority());
        row.setSentiment(clf.sentiment());
        row.setRequiresResponse(clf.requiresResponse());
        row.setActionTaken(action);
        row.setSummary(sum.summary());
        row.setActionItems(toJson(sum.actionItems()));
        row.setProcessedAt(processedAt);
        return row;
    }

    /**
     * Updates the latest processing-result row for this email with the generated
     * reply draft. Called by ReplyNode — the row was already created by saveDecisionBatch.
     */
    @Transactional
    public void saveReplyDraft(UUID userId, String emailId, String draftText) {
        repository.findTopByUserIdAndEmailProviderIdOrderByProcessedAtDesc(userId, emailId)
                .ifPresentOrElse(
                        row -> {
                            row.setReplyDraft(draftText);
                            row.setDraftStatus(DraftStatus.PENDING_REVIEW);
                        },
                        () -> log.warn("No processing_result row found for email {} — draft not persisted", emailId)
                );
    }

    private String toJson(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }
}