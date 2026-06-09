package com.zmail.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {
    List<AgentSession> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    @Modifying
    @Query("UPDATE AgentSession s SET s.updatedAt = CURRENT_TIMESTAMP WHERE s.id = :id")
    void touchUpdatedAt(@Param("id") UUID id);
}