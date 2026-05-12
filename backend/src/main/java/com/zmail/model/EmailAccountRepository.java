package com.zmail.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, UUID> {
    Optional<EmailAccount> findByUserAndProviderAndAccountEmail(User user, EmailProvider provider, String accountEmail);
    List<EmailAccount> findAllByUserAndProvider(User user, EmailProvider provider);
}