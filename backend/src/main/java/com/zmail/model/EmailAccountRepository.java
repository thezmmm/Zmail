package com.zmail.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, UUID> {
    Optional<EmailAccount> findByUserAndProviderAndAccountEmail(User user, EmailProvider provider, String accountEmail);
    List<EmailAccount> findAllByUser(User user);
    List<EmailAccount> findAllByUserId(UUID userId);
    List<EmailAccount> findAllByUserAndProvider(User user, EmailProvider provider);

    @Query("SELECT DISTINCT a.user.id FROM EmailAccount a")
    List<UUID> findDistinctUserIds();
}