package com.zmail.email;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EmailMessage(
        String providerId,
        UUID accountId,
        String subject,
        String sender,
        List<String> recipients,
        OffsetDateTime receivedAt,
        String body
) {}