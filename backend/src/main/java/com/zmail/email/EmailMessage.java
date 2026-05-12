package com.zmail.email;

import java.time.OffsetDateTime;
import java.util.List;

public record EmailMessage(
        String providerId,
        String subject,
        String sender,
        List<String> recipients,
        OffsetDateTime receivedAt,
        String body
) {}