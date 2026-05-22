package com.zmail.model;

import com.zmail.agent.model.ActionType;
import com.zmail.agent.model.DraftStatus;
import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processing_results", indexes = {
        @Index(name = "idx_pr_user_id",      columnList = "user_id"),
        @Index(name = "idx_pr_processed_at", columnList = "processed_at"),
        @Index(name = "idx_pr_draft_status", columnList = "draft_status")
})
@Getter
@Setter
@NoArgsConstructor
public class ProcessingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email_provider_id", nullable = false, length = 255)
    private String emailProviderId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(columnDefinition = "TEXT")
    private String subject;

    @Column(length = 500)
    private String sender;

    @Column(length = 50)
    private String category;

    @Column(length = 20)
    private String priority;

    @Column(length = 20)
    private String sentiment;

    @Column(name = "requires_response", nullable = false)
    private boolean requiresResponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken", length = 20)
    private ActionType actionTaken;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /** JSON 数组，e.g. ["Reply by Friday","Attach report"] */
    @JsonRawValue
    @Column(name = "action_items", columnDefinition = "TEXT")
    private String actionItems;

    /** LLM-generated reply draft; only set when actionTaken = REPLY. */
    @Column(name = "reply_draft", columnDefinition = "TEXT")
    private String replyDraft;

    /** Lifecycle of a reply draft; null for non-REPLY actions. */
    @Enumerated(EnumType.STRING)
    @Column(name = "draft_status", length = 20)
    private DraftStatus draftStatus;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;
}