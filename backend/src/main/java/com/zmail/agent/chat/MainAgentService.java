package com.zmail.agent.chat;

import com.zmail.config.AgentProperties;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MainAgentService {

    @Qualifier("mainModel")
    private final OpenAiStreamingChatModel streamingModel;
    private final MainAgentTools tools;
    private final AgentProperties agentProperties;

    private MainAgent mainAgent;

    /** In-memory chat memories keyed by sessionId. Survives for JVM lifetime. */
    private final Map<String, MessageWindowChatMemory> memories = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        // window + batchSize - 1 chat messages + ~5 system messages
        int maxMessages = agentProperties.getMemoryWindowSize()
                + agentProperties.getMemoryCompressBatchSize() + 5;
        mainAgent = AiServices.builder(MainAgent.class)
                .streamingChatLanguageModel(streamingModel)
                .chatMemoryProvider(memoryId -> memories.computeIfAbsent(
                        memoryId.toString(),
                        id -> MessageWindowChatMemory.builder()
                                .id(id)
                                .maxMessages(maxMessages)
                                .build()))
                .tools(tools)
                .systemMessageProvider(memoryId ->
                        "You are Zmail, a helpful email assistant. " +
                        "Be concise, actionable, and friendly. " +
                        "When the user has selected emails, call analyzeSelectedEmails to get a digest first.")
                .build();
        log.info("MainAgentService initialized");
    }

    public TokenStream chat(String sessionId, String message) {
        return mainAgent.chat(sessionId, message);
    }

    public void evict(String sessionId) {
        memories.remove(sessionId);
    }

    /**
     * Seeds LangChain4j memory for a session from persisted history.
     * If a summary of older messages exists it is prepended as a system message
     * so the LLM retains long-range context without receiving the full log.
     */
    public void seedMemory(String sessionId, String summary,
                           java.util.List<dev.langchain4j.data.message.ChatMessage> recentMessages) {
        int maxMessages = agentProperties.getMemoryWindowSize()
                + agentProperties.getMemoryCompressBatchSize() + 5;
        MessageWindowChatMemory mem = memories.computeIfAbsent(sessionId,
                id -> MessageWindowChatMemory.builder().id(id).maxMessages(maxMessages).build());

        if (summary != null && !summary.isBlank()) {
            mem.add(dev.langchain4j.data.message.SystemMessage.from(
                    "Summary of earlier conversation:\n" + summary));
        }
        recentMessages.forEach(mem::add);
    }
}