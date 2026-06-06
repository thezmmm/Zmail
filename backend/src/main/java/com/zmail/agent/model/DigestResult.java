package com.zmail.agent.model;

import java.io.Serializable;
import java.util.List;

public record DigestResult(
        String overview,
        List<EmailDigest> emails,
        List<String> priorities
) implements Serializable {
    public static DigestResult empty() {
        return new DigestResult("No emails to analyze.", List.of(), List.of());
    }
}