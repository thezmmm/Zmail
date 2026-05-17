package com.zmail.agent.model;

import java.util.List;

public record SummaryResult(
        String summary,
        List<String> actionItems
) {
    public static SummaryResult defaultResult() {
        return new SummaryResult("No summary available.", List.of());
    }
}