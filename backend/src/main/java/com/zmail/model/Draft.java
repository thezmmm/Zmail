package com.zmail.model;

import com.zmail.agent.model.DraftSource;
import com.zmail.agent.model.DraftStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "drafts", indexes = {
        @Index(name = "idx_drafts_user_status", columnList = "user_id, status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DraftSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DraftStatus status = DraftStatus.PENDING_REVIEW;

    /** JSON array of recipient addresses, e.g. ["foo@example.com"] */
    @Column(name = "to_addresses", columnDefinition = "TEXT", nullable = false)
    private String toAddresses;

    @Column(columnDefinition = "TEXT")
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body = "";

    /** Original email provider ID — non-null means this is a reply. */
    @Column(name = "reply_to_provider_id", length = 255)
    private String replyToProviderId;

    /** FK back to the ProcessingResult that triggered AI generation (null for user drafts). */
    @Column(name = "result_id")
    private UUID resultId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
