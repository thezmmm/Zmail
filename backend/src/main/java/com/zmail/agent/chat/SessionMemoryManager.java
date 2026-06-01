package com.zmail.agent.chat;

import com.zmail.config.AgentProperties;
import com.zmail.model.AgentMessage;
import com.zmail.model.AgentSession;
import com.zmail.model.AgentSessionRepository;
import com.zmail.service.AgentSessionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges PostgreSQL message history with LangChain4j's in-memory chat memory.
 *
 * Responsibilities:
 *  - ensureSeeded: on first access after JVM start, loads history from DB and seeds
 *    the LangChain4j memory with summary (if any) + last WINDOW_SIZE messages.
 *  - maybeCompress: after each assistant reply, checks whether total message count
 *    exceeds COMPRESS_THRESHOLD and, if so, compresses the oldest messages into a
 *    running summary stored back in agent_sessions.summary.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionMemoryManager {

    @Qualifier("compressModel")
    private final ChatLanguageModel compressModel;
    private final MainAgentService mainAgentService;
    private final AgentSessionService sessionService;
    private final AgentSessionRepository sessionRepo;
    private final AgentProperties props;

    private static final int MAX_TRACKED_SESSIONS = 2000;

    /** LRU-bounded set: evicts the oldest entry when capacity is exceeded. */
    private final Set<String> seededSessions = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<>(MAX_TRACKED_SESSIONS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED_SESSIONS;
                }
            }));

    /** sessionId → total message count at the time of last compression. */
    private final java.util.Map<String, Integer> lastCompressedTotal = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_TRACKED_SESSIONS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> eldest) {
                    return size() > MAX_TRACKED_SESSIONS;
                }
            });
    private ConversationSummaryAgent summaryAgent;

    @PostConstruct
    void init() {
        summaryAgent = AiServices.builder(ConversationSummaryAgent.class)
                .chatLanguageModel(compressModel)
                .build();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ensures this session's history is loaded into LangChain4j memory.
     * Safe to call on every request — idempotent within a JVM lifetime.
     */
    public void ensureSeeded(UUID sessionId, UUID userId) {
        if (!seededSessions.add(sessionId.toString())) return;

        AgentSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        int compressedUntil = session.getCompressedUntil();
        lastCompressedTotal.put(sessionId.toString(), compressedUntil);

        List<AgentMessage> allMessages = sessionService.listMessages(sessionId, userId);
        // Pass everything from compressedUntil onward: uncompressed batch + active window
        List<AgentMessage> toSeed = compressedUntil == 0
                ? allMessages
                : allMessages.subList(compressedUntil, allMessages.size());

        mainAgentService.seedMemory(sessionId.toString(), session.getSummary(),
                toChatMessages(toSeed));

        log.info("Seeded session {} — {} messages (compressedUntil={}, summary={})",
                sessionId, toSeed.size(), compressedUntil,
                session.getSummary() != null ? "yes" : "no");
    }

    /**
     * Checks whether the session needs compression and runs it if so.
     * Called after each assistant message is persisted.
     */
    public void maybeCompress(UUID sessionId, UUID userId) {
        int total = sessionService.countMessages(sessionId);
        int window = props.getMemoryWindowSize();
        int batchSize = props.getMemoryCompressBatchSize();
        int outsideWindow = total - window;
        if (outsideWindow <= 0) return;

        // Fall back to the DB value when the in-memory map has no entry (e.g. after a JVM restart
        // where ensureSeeded failed mid-way, leaving compressedUntil uninitialized in this map).
        int compressedUntil = lastCompressedTotal.computeIfAbsent(
                sessionId.toString(),
                id -> sessionRepo.findById(sessionId)
                        .map(AgentSession::getCompressedUntil)
                        .orElse(0));

        int newUncompressed = outsideWindow - compressedUntil;
        if (newUncompressed < batchSize) return;

        List<AgentMessage> allMessages = sessionService.listMessages(sessionId, userId);
        // Use the actual list size as the upper bound: countMessages() and listMessages() are two
        // separate queries with no shared transaction, so allMessages.size() may be smaller than
        // outsideWindow if messages were deleted between the two calls.
        int safeEnd = Math.min(outsideWindow, allMessages.size());
        if (safeEnd <= compressedUntil) return;

        List<AgentMessage> toCompress = allMessages.subList(compressedUntil, safeEnd);

        AgentSession session = sessionRepo.findById(sessionId).orElseThrow();
        String newSummary = compress(session.getSummary(), toCompress);

        sessionService.updateSummary(sessionId, newSummary);
        sessionService.updateCompressedUntil(sessionId, safeEnd);
        lastCompressedTotal.put(sessionId.toString(), safeEnd);

        log.info("Compressed messages[{}..{}] for session {} (total={})",
                compressedUntil, safeEnd, sessionId, total);
    }

    public void evict(UUID sessionId) {
        String key = sessionId.toString();
        seededSessions.remove(key);
        lastCompressedTotal.remove(key);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String compress(String existingSummary, List<AgentMessage> messages) {
        StringBuilder sb = new StringBuilder();

        if (existingSummary != null && !existingSummary.isBlank()) {
            sb.append("=== Previous Summary ===\n")
              .append(existingSummary)
              .append("\n\n");
        }

        sb.append("=== Messages to incorporate ===\n");
        for (AgentMessage m : messages) {
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }

        return summaryAgent.summarize(sb.toString());
    }

    private List<ChatMessage> toChatMessages(List<AgentMessage> messages) {
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (AgentMessage m : messages) {
            if ("USER".equals(m.getRole())) {
                result.add(UserMessage.from(m.getContent()));
            } else {
                result.add(AiMessage.from(m.getContent()));
            }
        }
        return result;
    }
}