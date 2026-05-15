package com.zmail.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zmail.agent")
@Getter
@Setter
public class AgentProperties {
    private String baseUrl = "https://api.openai.com/v1";
    private String mainModel = "gpt-4o";
    private String summarizeModel = "gpt-4o";
    private String classifyModel = "gpt-4o-mini";
    private int maxEmailsPerRun = 50;
    /** Minimum milliseconds between two agent runs for the same user (default 60 s). */
    private long minRunIntervalMs = 60_000;
    /** Max concurrent LLM calls per agent run (bounds API parallelism). */
    private int maxParallelLlmCalls = 5;
}