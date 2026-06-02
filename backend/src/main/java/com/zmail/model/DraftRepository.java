package com.zmail.model;

import com.zmail.agent.model.DraftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DraftRepository extends JpaRepository<Draft, UUID> {

    Page<Draft> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, DraftStatus status, Pageable pageable);

    /** Used to look up a draft by its linked ProcessingResult (for backward-compat approve/reject). */
    Optional<Draft> findTopByUserIdAndResultIdOrderByCreatedAtDesc(UUID userId, UUID resultId);
}
