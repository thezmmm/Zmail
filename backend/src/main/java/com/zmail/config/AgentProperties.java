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
    private String classifyModel = "claude-haiku-4-5-20251001";
    private String summarizeModel = "claude-sonnet-4-6";
    private int maxEmailsPerRun = 50;
    /** Minimum milliseconds between two agent runs for the same user (default 60 s). */
    private long minRunIntervalMs = 60_000;
    /** Max concurrent LLM calls per agent run (bounds API parallelism). */
    private int maxParallelLlmCalls = 5;
}