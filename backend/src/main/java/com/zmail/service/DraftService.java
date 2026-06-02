package com.zmail.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmail.agent.model.DraftSource;
import com.zmail.agent.model.DraftStatus;
import com.zmail.email.EmailDraft;
import com.zmail.model.Draft;
import com.zmail.model.DraftRepository;
import com.zmail.model.ProcessingResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftService {

    private final DraftRepository draftRepository;
    private final ProcessingResultRepository processingResultRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    // ── Public API ─────────────────────────────────────────────────────────────

    public Page<Draft> listPending(UUID userId, Pageable pageable) {
        return draftRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId, DraftStatus.PENDING_REVIEW, pageable);
    }

    /** Called by EmailProcessingService when AI generates a reply draft. */
    @Transactional
    public Draft createFromResult(UUID userId, UUID accountId, String toAddress,
                                   String subject, String body,
                                   String replyToProviderId, UUID resultId) {
        Draft draft = new Draft();
        draft.setUserId(userId);
        draft.setAccountId(accountId);
        draft.setSource(DraftSource.AI);
        draft.setStatus(DraftStatus.PENDING_REVIEW);
        draft.setToAddresses(toJson(List.of(toAddress)));
        draft.setSubject(subject);
        draft.setBody(body);
        draft.setReplyToProviderId(replyToProviderId);
        draft.setResultId(resultId);
        return draftRepository.save(draft);
    }

    /** Called by DraftController when user manually composes a draft. */
    @Transactional
    public Draft createUserDraft(UUID userId, UUID accountId,
                                  List<String> to, String subject, String body) {
        Draft draft = new Draft();
        draft.setUserId(userId);
        draft.setAccountId(accountId);
        draft.setSource(DraftSource.USER);
        draft.setStatus(DraftStatus.PENDING_REVIEW);
        draft.setToAddresses(toJson(to));
        draft.setSubject(subject);
        draft.setBody(body);
        return draftRepository.save(draft);
    }

    /** Update draft body and/or subject (only PENDING_REVIEW drafts). */
    @Transactional
    public Draft update(UUID userId, UUID draftId, String body, String subject) {
        Draft draft = load(userId, draftId);
        requireStatus(draft, DraftStatus.PENDING_REVIEW);
        if (body != null)    draft.setBody(body);
        if (subject != null) draft.setSubject(subject);
        return draftRepository.save(draft);
    }

    /**
     * Approve: optionally update body, then send the email.
     * Accepts either a Draft.id or a ProcessingResult.id (backward compat with detail page).
     */
    @Transactional
    public Draft approve(UUID userId, UUID idOrResultId, String overrideBody) {
        Draft draft = loadByIdOrResultId(userId, idOrResultId);
        requireStatus(draft, DraftStatus.PENDING_REVIEW);

        if (overrideBody != null && !overrideBody.isBlank()) {
            draft.setBody(overrideBody);
        }
        draft.setStatus(DraftStatus.SENT);
        // saveAndFlush uses @Version to guard against concurrent approval
        Draft saved = draftRepository.saveAndFlush(draft);

        List<String> recipients = fromJson(draft.getToAddresses());
        EmailDraft emailDraft = new EmailDraft(
                recipients, draft.getSubject(), draft.getBody(), draft.getReplyToProviderId());
        emailService.send(userId, draft.getAccountId(), emailDraft);

        // Keep ProcessingResult.draftStatus in sync for the detail page
        syncResultStatus(draft.getResultId(), DraftStatus.SENT);

        log.info("Draft {} approved and sent for user {}", saved.getId(), userId);
        return saved;
    }

    /** Reject: mark rejected without sending. */
    @Transactional
    public Draft reject(UUID userId, UUID idOrResultId) {
        Draft draft = loadByIdOrResultId(userId, idOrResultId);
        requireStatus(draft, DraftStatus.PENDING_REVIEW);
        draft.setStatus(DraftStatus.REJECTED);
        Draft saved = draftRepository.save(draft);
        syncResultStatus(draft.getResultId(), DraftStatus.REJECTED);
        log.info("Draft {} rejected for user {}", saved.getId(), userId);
        return saved;
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    public record DraftDto(
            UUID id,
            UUID accountId,
            String source,
            String status,
            List<String> to,
            String subject,
            String body,
            String replyToProviderId,
            UUID resultId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public DraftDto toDto(Draft d) {
        return new DraftDto(
                d.getId(), d.getAccountId(),
                d.getSource().name(), d.getStatus().name(),
                fromJson(d.getToAddresses()),
                d.getSubject(), d.getBody(),
                d.getReplyToProviderId(), d.getResultId(),
                d.getCreatedAt(), d.getUpdatedAt());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Draft load(UUID userId, UUID draftId) {
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new NoSuchElementException("Draft not found: " + draftId));
        if (!draft.getUserId().equals(userId)) throw new SecurityException("Access denied");
        return draft;
    }

    private Draft loadByIdOrResultId(UUID userId, UUID id) {
        return draftRepository.findById(id)
                .filter(d -> d.getUserId().equals(userId))
                .or(() -> draftRepository.findTopByUserIdAndResultIdOrderByCreatedAtDesc(userId, id))
                .orElseThrow(() -> new NoSuchElementException("Draft not found: " + id));
    }

    private void requireStatus(Draft draft, DraftStatus expected) {
        if (draft.getStatus() != expected) {
            throw new IllegalStateException(
                    "Draft is " + draft.getStatus() + ", expected " + expected);
        }
    }

    private void syncResultStatus(UUID resultId, DraftStatus status) {
        if (resultId == null) return;
        processingResultRepository.findById(resultId).ifPresent(r -> {
            r.setDraftStatus(status);
            processingResultRepository.save(r);
        });
    }

    private String toJson(List<String> list) {
        try { return objectMapper.writeValueAsString(list); }
        catch (Exception e) { return "[]"; }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }
}
