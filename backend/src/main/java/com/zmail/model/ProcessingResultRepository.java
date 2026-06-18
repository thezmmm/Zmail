package com.zmail.model;

import com.zmail.agent.model.ActionType;
import com.zmail.agent.model.DraftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
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

    /** Total processed emails for a user, across all accounts — used to decide whether a page needs backfill. */
    long countByUserId(UUID userId);

    /** Earliest received email already processed for an account — starting point for history backfill. */
    @Query("SELECT MIN(r.receivedAt) FROM ProcessingResult r WHERE r.accountId = :accountId")
    Optional<OffsetDateTime> findEarliestReceivedAt(@Param("accountId") UUID accountId);

    List<ProcessingResult> findByUserIdAndProcessedAtAfter(UUID userId, OffsetDateTime since);

    Page<ProcessingResult> findByUserIdAndProcessedAtAfter(UUID userId, OffsetDateTime since, Pageable pageable);

    @Query(value = """
            SELECT * FROM processing_results pr
            WHERE pr.user_id = :userId
              AND pr.summary IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM email_embeddings ee
                  WHERE ee.user_id = pr.user_id
                    AND ee.source_id = pr.account_id::text || ':' || pr.email_provider_id
              )
            LIMIT :limit
            """, nativeQuery = true)
    List<ProcessingResult> findUnembedded(@Param("userId") UUID userId, @Param("limit") int limit);
}