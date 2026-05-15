package com.zmail.agent;

import java.util.UUID;

/** Identifies a single email: provider-specific message ID + the account it belongs to. */
public record EmailRef(String providerId, UUID accountId) {}