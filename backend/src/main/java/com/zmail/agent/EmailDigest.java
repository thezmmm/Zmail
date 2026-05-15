package com.zmail.agent;

import java.util.List;

public record EmailDigest(
        String emailId,
        String subject,
        String sender,
        String summary,
        List<String> actionItems
) {}