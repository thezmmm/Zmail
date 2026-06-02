package com.zmail.service;

import com.zmail.agent.model.DraftStatus;
import com.zmail.email.EmailDraft;
import com.zmail.model.ProcessingResult;
import com.zmail.model.ProcessingResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftService {

    private final ProcessingResultRepository repository;
    private final EmailService emailService;

    public Page<ProcessingResult> listPending(UUID userId, Pageable pageable) {
        return repository.findByUserIdAndDraftStatusOrderByProcessedAtDesc(
                userId, DraftStatus.PENDING_REVIEW, pageable);
    }

    @Transactional
    public ProcessingResult approve(UUID userId, UUID draftId) {
        ProcessingResult result = load(userId, draftId);
        require(result, DraftStatus.PENDING_REVIEW);

        if (result.getSender() == null) {
            throw new IllegalStateException("Draft " + draftId + " has no sender address");
        }

        result.setDraftStatus(DraftStatus.SENT);
        // saveAndFlush forces an immediate SQL UPDATE (with the @Version WHERE clause) before
        // the email is sent. If a concurrent request already committed its update, the version
        // will have changed and Spring throws ObjectOptimisticLockingFailureException here,
        // rolling back the transaction without sending the email.
        ProcessingResult saved = repository.saveAndFlush(result);

        EmailDraft draft = new EmailDraft(
                List.of(result.getSender()),
                "Re: " + result.getSubject(),
                result.getReplyDraft());

        emailService.send(userId, result.getAccountId(), draft);
        log.info("Draft {} approved and sent for user {}", draftId, userId);
        return saved;
    }

    @Transactional
    public ProcessingResult reject(UUID userId, UUID draftId) {
        ProcessingResult result = load(userId, draftId);
        require(result, DraftStatus.PENDING_REVIEW);
        result.setDraftStatus(DraftStatus.REJECTED);
        log.info("Draft {} rejected for user {}", draftId, userId);
        return repository.save(result);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ProcessingResult load(UUID userId, UUID id) {
        ProcessingResult result = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Draft not found: " + id));
        if (!result.getUserId().equals(userId)) {
            throw new SecurityException("Access denied");
        }
        return result;
    }

    private void require(ProcessingResult result, DraftStatus expected) {
        if (result.getDraftStatus() != expected) {
            throw new IllegalStateException(
                    "Draft is " + result.getDraftStatus() + ", expected " + expected);
        }
    }
}