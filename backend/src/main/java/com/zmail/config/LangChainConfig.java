package com.zmail.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@RequiredArgsConstructor
public class LangChainConfig {

    private final AgentProperties agentProperties;

    @Value("${langchain4j.anthropic.api-key}")
    private String anthropicApiKey;

    @Bean("classifyModel")
    public ChatLanguageModel classifyModel() {
        return AnthropicChatModel.builder()
                .apiKey(anthropicApiKey)
                .modelName(agentProperties.getClassifyModel())
                .maxTokens(512)
                .temperature(0.0)
                .build();
    }

    @Bean("summarizeModel")
    public ChatLanguageModel summarizeModel() {
        return AnthropicChatModel.builder()
                .apiKey(anthropicApiKey)
                .modelName(agentProperties.getSummarizeModel())
                .maxTokens(1024)
                .temperature(0.3)
                .build();
    }

    /**
     * Shared thread pool for parallel LLM calls inside ClassifyNode / SummarizeNode.
     * Pool size = maxParallelLlmCalls (default 5) → natural API concurrency cap.
     * Queue capacity absorbs bursts up to maxEmailsPerRun without rejection.
     */
    @Bean("agentExecutor")
    public Executor agentExecutor() {
        int parallelism = agentProperties.getMaxParallelLlmCalls();
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(parallelism);
        exec.setMaxPoolSize(parallelism);
        exec.setQueueCapacity(agentProperties.getMaxEmailsPerRun());
        exec.setThreadNamePrefix("agent-llm-");
        exec.initialize();
        return exec;
    }
}