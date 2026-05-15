package com.zmail.agent;

import java.util.List;

public record DigestResult(
        String overview,
        List<EmailDigest> emails,
        List<String> priorities
) {
    public static DigestResult empty() {
        return new DigestResult("No emails to analyze.", List.of(), List.of());
    }
}