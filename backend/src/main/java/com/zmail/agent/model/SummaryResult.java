package com.zmail.agent.model;

import java.io.Serializable;
import java.util.List;

public record SummaryResult(
        String summary,
        List<String> actionItems
) implements Serializable {
    public static SummaryResult defaultResult() {
        return new SummaryResult("No summary available.", List.of());
    }
}