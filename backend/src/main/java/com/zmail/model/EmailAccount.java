package com.zmail.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider", "account_email"}))
@Getter
@Setter
@NoArgsConstructor
public class EmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailProvider provider;

    @Column(name = "account_email", nullable = false)
    private String accountEmail;

    @JsonIgnore
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @JsonIgnore
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expiry")
    private OffsetDateTime tokenExpiry;

    /** True when the stored refresh token is invalid or revoked; requires user to re-authorize. */
    @Column(name = "needs_reauth", nullable = false)
    private boolean needsReauth = false;

    /** Exclusive upper bound for the next on-demand history-backfill request; null until the first backfill click. */
    @Column(name = "history_backfill_before")
    private OffsetDateTime historyBackfillBefore;

    /** True once a backfill request has reached the bottom of this account's mailbox. */
    @ColumnDefault("false")
    @Column(name = "history_backfill_complete", nullable = false)
    private boolean historyBackfillComplete = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}