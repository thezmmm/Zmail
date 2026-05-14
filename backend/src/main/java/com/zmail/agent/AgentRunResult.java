package com.zmail.agent;

import java.util.Map;

public record AgentRunResult(
        int emailsProcessed,
        int replied,
        int archived,
        int flagged,
        Map<String, String> summaries
) {
    public static AgentRunResult empty() {
        return new AgentRunResult(0, 0, 0, 0, Map.of());
    }
}