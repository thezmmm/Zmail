package com.zmail.service;

import com.zmail.email.*;
import com.zmail.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final GmailAdapter gmailAdapter;
    private final MsGraphAdapter msGraphAdapter;

    public List<EmailMessage> fetchUnread(UUID userId, int maxResults) {
        User user = findUser(userId);
        return emailAccountRepository.findAllByUser(user).stream()
                .filter(this::isAccessible)
                .flatMap(account -> {
                    try {
                        return getAdapter(account.getProvider()).fetchUnread(account, maxResults).stream();
                    } catch (Exception e) {
                        log.warn("fetchUnread failed for account {} ({}): {}",
                                account.getId(), account.getAccountEmail(), e.getMessage());
                        return Stream.of();
                    }
                })
                .collect(Collectors.toList());
    }

    public List<EmailMessage> fetchRecent(UUID userId, int maxResults, OffsetDateTime since) {
        User user = findUser(userId);
        return emailAccountRepository.findAllByUser(user).stream()
                .filter(this::isAccessible)
                .flatMap(account -> {
                    try {
                        return getAdapter(account.getProvider()).fetchRecent(account, maxResults, since).stream();
                    } catch (Exception e) {
                        log.warn("fetchRecent failed for account {} ({}): {}",
                                account.getId(), account.getAccountEmail(), e.getMessage());
                        return Stream.of();
                    }
                })
                .collect(Collectors.toList());
    }

    /** Accounts flagged as needing re-authorization are excluded from background fetch operations. */
    private boolean isAccessible(EmailAccount account) {
        if (account.isNeedsReauth()) {
            log.warn("Skipping account {} ({}) — needs re-authorization",
                    account.getId(), account.getAccountEmail());
            return false;
        }
        return true;
    }

    public List<EmailAccount> getAccounts(UUID userId) {
        return emailAccountRepository.findAllByUser(findUser(userId));
    }

    public EmailAccount getAccount(UUID userId, UUID accountId) {
        return findAccount(userId, accountId);
    }

    @Transactional
    public void deleteAccount(UUID userId, UUID accountId) {
        EmailAccount account = findAccount(userId, accountId);
        emailAccountRepository.delete(account);
    }

    public void send(UUID userId, UUID accountId, EmailDraft draft) {
        EmailAccount account = findAccount(userId, accountId);
        getAdapter(account.getProvider()).send(account, draft);
    }

    public void archive(UUID userId, UUID accountId, String messageId) {
        EmailAccount account = findAccount(userId, accountId);
        getAdapter(account.getProvider()).archive(account, messageId);
    }

    public void markRead(UUID userId, UUID accountId, String messageId) {
        EmailAccount account = findAccount(userId, accountId);
        getAdapter(account.getProvider()).markRead(account, messageId);
    }

    public void flag(UUID userId, UUID accountId, String messageId) {
        EmailAccount account = findAccount(userId, accountId);
        getAdapter(account.getProvider()).flag(account, messageId);
    }

    public EmailMessage fetchById(UUID userId, UUID accountId, String messageId) {
        EmailAccount account = findAccount(userId, accountId);
        return getAdapter(account.getProvider()).fetchById(account, messageId);
    }

    private EmailPort getAdapter(EmailProvider provider) {
        return switch (provider) {
            case GMAIL -> gmailAdapter;
            case MSGRAPH -> msGraphAdapter;
        };
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private EmailAccount findAccount(UUID userId, UUID accountId) {
        EmailAccount account = emailAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (!account.getUser().getId().equals(userId)) {
            throw new SecurityException("Account does not belong to user");
        }
        return account;
    }
}