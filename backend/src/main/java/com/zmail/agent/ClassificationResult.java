package com.zmail.agent;

public record ClassificationResult(
        String category,
        String priority,
        String sentiment,
        boolean requiresResponse
) {
    public static ClassificationResult defaultResult() {
        return new ClassificationResult("OTHER", "LOW", "NEUTRAL", false);
    }
}