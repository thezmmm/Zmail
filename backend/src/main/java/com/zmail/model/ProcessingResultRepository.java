package com.zmail.model;

import com.zmail.agent.ActionType;
import com.zmail.agent.DraftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessingResultRepository extends JpaRepository<ProcessingResult, UUID> {

    Page<ProcessingResult> findByUserIdOrderByProcessedAtDesc(UUID userId, Pageable pageable);

    /** Latest row for a given (user, email) pair — used by ReplyNode to update its draft. */
    Optional<ProcessingResult> findTopByUserIdAndEmailProviderIdOrderByProcessedAtDesc(
            UUID userId, String emailProviderId);

    Page<ProcessingResult> findByUserIdAndDraftStatusOrderByProcessedAtDesc(
            UUID userId, DraftStatus draftStatus, Pageable pageable);

    long countByUserIdAndActionTaken(UUID userId, ActionType actionTaken);
}