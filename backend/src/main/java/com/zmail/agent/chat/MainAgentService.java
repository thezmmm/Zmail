package com.zmail.agent.chat;

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

    private MainAgent mainAgent;

    /** In-memory chat memories keyed by sessionId. Survives for JVM lifetime. */
    private final Map<String, MessageWindowChatMemory> memories = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        mainAgent = AiServices.builder(MainAgent.class)
                .streamingChatLanguageModel(streamingModel)
                .chatMemoryProvider(memoryId -> memories.computeIfAbsent(
                        memoryId.toString(),
                        id -> MessageWindowChatMemory.builder()
                                .id(id)
                                .maxMessages(40)
                                .build()))
                .tools(tools)
                .systemMessageProvider(memoryId ->
                        "You are Zmail, a helpful email assistant. " +
                        "The current session ID is: " + memoryId + ". " +
                        "When calling tools that require a sessionId, always pass this value. " +
                        "Be concise, actionable, and friendly. " +
                        "When the user has selected emails, call analyzeSelectedEmails to get a digest first.")
                .build();
        log.info("MainAgentService initialized");
    }

    public TokenStream chat(String sessionId, String message) {
        return mainAgent.chat(sessionId, message);
    }

    /** Preload past messages into memory when resuming a session. */
    public void seedMemory(String sessionId,
                           java.util.List<dev.langchain4j.data.message.ChatMessage> history) {
        MessageWindowChatMemory mem = memories.computeIfAbsent(sessionId,
                id -> MessageWindowChatMemory.builder().id(id).maxMessages(40).build());
        history.forEach(mem::add);
    }
}