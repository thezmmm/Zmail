package com.zmail.email;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Lightweight email header stored in agent state — body excluded. */
public record EmailMeta(
        String providerId,
        UUID accountId,
        String subject,
        String sender,
        OffsetDateTime receivedAt
) {}