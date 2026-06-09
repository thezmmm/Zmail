package com.zmail.service;

import com.zmail.model.AgentMessage;
import com.zmail.model.AgentMessageRepository;
import com.zmail.model.AgentSession;
import com.zmail.model.AgentSessionRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSessionService {

    private final AgentSessionRepository sessionRepo;
    private final AgentMessageRepository messageRepo;
    @Qualifier("classifyModel")
    private final ChatLanguageModel classifyModel;

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
        AgentMessage saved = messageRepo.save(msg);
        sessionRepo.touchUpdatedAt(sessionId);
        return saved;
    }

    @Transactional
    public void updateSummaryAndCompressedUntil(UUID sessionId, String summary, int compressedUntil) {
        AgentSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        s.setSummary(summary);
        s.setCompressedUntil(compressedUntil);
        sessionRepo.save(s);
    }

    public int countMessages(UUID sessionId) {
        return messageRepo.countBySessionId(sessionId);
    }

    @Transactional
    public void delete(UUID sessionId, UUID userId) {
        AgentSession s = getOrThrow(sessionId, userId);
        sessionRepo.delete(s);
    }

    /**
     * Calls gpt-4o-mini to generate a short title from the first user message,
     * then persists it. Falls back to truncated text if the LLM call fails.
     * Only called once per session (when title is still the default "新对话").
     */
    @Transactional
    public String generateAndSaveTitle(UUID sessionId, String firstUserMessage) {
        String prompt = "Generate a short conversation title based on the user's first message. " +
                "Rules: same language as the message, max 12 characters for Chinese/Japanese/Korean " +
                "or max 6 words for other languages, no quotes, no trailing punctuation.\n\n" +
                "Message: " + firstUserMessage + "\nTitle:";
        String title;
        try {
            String raw = classifyModel.chat(prompt).trim();
            title = raw.length() > 30 ? raw.substring(0, 27) + "…" : raw;
        } catch (Exception e) {
            log.warn("Title generation failed for session {}, falling back to truncation", sessionId, e);
            String t = firstUserMessage.trim().replaceAll("\\s+", " ");
            title = t.length() > 20 ? t.substring(0, 17) + "…" : t;
        }

        AgentSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        s.setTitle(title);
        sessionRepo.save(s);
        log.debug("Generated title for session {}: {}", sessionId, title);
        return title;
    }
}