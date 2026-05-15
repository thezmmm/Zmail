package com.zmail.service;

import com.zmail.model.AgentMessage;
import com.zmail.model.AgentMessageRepository;
import com.zmail.model.AgentSession;
import com.zmail.model.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentSessionRepository sessionRepo;
    private final AgentMessageRepository messageRepo;

    @Transactional
    public AgentSession create(UUID userId, String title) {
        AgentSession s = new AgentSession();
        s.setUserId(userId);
        s.setTitle(title);
        return sessionRepo.save(s);
    }

    public AgentSession getOrThrow(UUID sessionId, UUID userId) {
        AgentSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (!s.getUserId().equals(userId)) throw new SecurityException("Session not owned by user");
        return s;
    }

    public List<AgentSession> listForUser(UUID userId) {
        return sessionRepo.findAllByUserIdOrderByUpdatedAtDesc(userId);
    }

    public List<AgentMessage> listMessages(UUID sessionId, UUID userId) {
        getOrThrow(sessionId, userId); // auth check
        return messageRepo.findAllBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public AgentMessage appendMessage(UUID sessionId, String role, String content) {
        AgentSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        AgentMessage msg = new AgentMessage();
        msg.setSession(session);
        msg.setRole(role);
        msg.setContent(content);
        return messageRepo.save(msg);
    }

    @Transactional
    public void delete(UUID sessionId, UUID userId) {
        AgentSession s = getOrThrow(sessionId, userId);
        sessionRepo.delete(s);
    }

    /** Auto-generate a title from the first user message (truncate at 60 chars). */
    public String generateTitle(String firstMessage) {
        String t = firstMessage.trim().replaceAll("\\s+", " ");
        return t.length() > 60 ? t.substring(0, 57) + "..." : t;
    }
}