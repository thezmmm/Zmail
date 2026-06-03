package com.zmail.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@RequiredArgsConstructor
public class LangChainConfig {

    private final AgentProperties agentProperties;

    @Value("${langchain4j.open-ai.api-key}")
    private String openAiApiKey;

    @Value("${zmail.embedding.model-name:text-embedding-3-small}")
    private String embeddingModelName;

    @Bean("classifyModel")
    public ChatLanguageModel classifyModel() {
        return OpenAiChatModel.builder()
                .baseUrl(agentProperties.getBaseUrl())
                .apiKey(openAiApiKey)
                .modelName(agentProperties.getClassifyModel())
                .maxTokens(512)
                .temperature(0.0)
                .build();
    }

    @Bean("summarizeModel")
    public ChatLanguageModel summarizeModel() {
        return OpenAiChatModel.builder()
                .baseUrl(agentProperties.getBaseUrl())
                .apiKey(openAiApiKey)
                .modelName(agentProperties.getSummarizeModel())
                .maxTokens(1024)
                .temperature(0.3)
                .build();
    }

    @Bean("compressModel")
    public ChatLanguageModel compressModel() {
        return OpenAiChatModel.builder()
                .baseUrl(agentProperties.getBaseUrl())
                .apiKey(openAiApiKey)
                .modelName(agentProperties.getCompressModel())
                .maxTokens(1024)
                .temperature(0.3)
                .build();
    }

    @Bean("mainModel")
    public OpenAiStreamingChatModel mainModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(agentProperties.getBaseUrl())
                .apiKey(openAiApiKey)
                .modelName(agentProperties.getMainModel())
                .temperature(0.7)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(agentProperties.getBaseUrl())
                .apiKey(openAiApiKey)
                .modelName(embeddingModelName)
                .build();
    }

    /**
     * Shared thread pool for parallel LLM calls inside SummarizeNode.
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
        // CallerRunsPolicy: when queue is full the submitting thread runs the task itself,
        // preventing RejectedExecutionException when processBatch submits more tasks than
        // the queue can hold (e.g. initialSync with 100 emails, queue capacity 50).
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setThreadNamePrefix("agent-llm-");
        exec.initialize();
        return exec;
    }
}
