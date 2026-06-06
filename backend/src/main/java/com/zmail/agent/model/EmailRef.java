package com.zmail.agent.model;

import java.io.Serializable;
import java.util.UUID;

/** Identifies a single email: provider-specific message ID + the account it belongs to. */
public record EmailRef(String providerId, UUID accountId) implements Serializable {}