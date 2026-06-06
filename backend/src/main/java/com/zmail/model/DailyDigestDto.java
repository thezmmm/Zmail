package com.zmail.model;

import com.zmail.agent.model.EmailDigest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyDigestDto(
        UUID id,
        UUID userId,
        LocalDate digestDate,
        String overview,
        List<EmailDigest> emails,
        List<String> priorities,
        OffsetDateTime createdAt
) {}
