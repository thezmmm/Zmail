package com.zmail.service;

import com.zmail.email.*;
import com.zmail.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final GmailAdapter gmailAdapter;
    private final MsGraphAdapter msGraphAdapter;

    public List<EmailMessage> fetchUnread(UUID userId, int maxResults) {
        User user = findUser(userId);
        return emailAccountRepository.findAllByUser(user).stream()
                .flatMap(account -> getAdapter(account.getProvider())
                        .fetchUnread(account, maxResults).stream())
                .collect(Collectors.toList());
    }

    public List<EmailAccount> getAccounts(UUID userId) {
        return emailAccountRepository.findAllByUser(findUser(userId));
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