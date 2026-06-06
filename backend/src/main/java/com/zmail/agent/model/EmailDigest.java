package com.zmail.agent.model;

import java.io.Serializable;
import java.util.List;

public record EmailDigest(
        String emailId,
        String subject,
        String sender,
        String summary,
        List<String> actionItems
) implements Serializable {}