package com.zmail.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyDigestRepository extends JpaRepository<DailyDigest, UUID> {
    Optional<DailyDigest> findByUserIdAndDigestDate(UUID userId, LocalDate digestDate);
}
