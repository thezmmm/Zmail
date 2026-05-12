package com.zmail.email;

import java.util.List;

public record EmailDraft(
        List<String> to,
        String subject,
        String body
) {}