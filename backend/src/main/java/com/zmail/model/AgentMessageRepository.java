package com.zmail.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {
    List<AgentMessage> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    int countBySessionId(UUID sessionId);
}