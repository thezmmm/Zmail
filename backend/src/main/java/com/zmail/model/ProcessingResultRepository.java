package com.zmail.model;

import com.zmail.agent.model.ActionType;
import com.zmail.agent.model.DraftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.query.Param;

public interface ProcessingResultRepository extends JpaRepository<ProcessingResult, UUID> {

    boolean existsByUserIdAndEmailProviderId(UUID userId, String emailProviderId);

    /** Pessimistic write lock — used by analyzeOnDemand to prevent duplicate LLM calls. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ProcessingResult r WHERE r.id = :id")
    Optional<ProcessingResult> findByIdForUpdate(@Param("id") UUID id);

    Page<ProcessingResult> findByUserId(UUID userId, Pageable pageable);

    Page<ProcessingResult> findByUserIdAndCategory(UUID userId, String category, Pageable pageable);

    /** Latest row for a given (user, email) pair — used by ReplyNode to update its draft. */
    Optional<ProcessingResult> findTopByUserIdAndEmailProviderIdOrderByProcessedAtDesc(
            UUID userId, String emailProviderId);

    Page<ProcessingResult> findByUserIdAndDraftStatusOrderByProcessedAtDesc(
            UUID userId, DraftStatus draftStatus, Pageable pageable);

    long countByUserIdAndActionTaken(UUID userId, ActionType actionTaken);
}