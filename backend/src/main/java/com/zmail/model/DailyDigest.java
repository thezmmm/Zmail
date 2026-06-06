package com.zmail.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_digests", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "digest_date"})
})
@Getter
@Setter
@NoArgsConstructor
public class DailyDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "digest_date", nullable = false)
    private LocalDate digestDate;

    @Column(columnDefinition = "TEXT")
    private String overview;

    /** JSON array of EmailDigest records. */
    @Column(columnDefinition = "TEXT")
    private String emails;

    /** JSON array of priority strings. */
    @Column(columnDefinition = "TEXT")
    private String priorities;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
