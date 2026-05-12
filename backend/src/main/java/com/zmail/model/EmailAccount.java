package com.zmail.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}