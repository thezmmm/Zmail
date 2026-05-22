package com.zmail.service;

import com.zmail.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;

    public User getById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }

    @Transactional
    public User findOrCreate(String email, String name) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setName(name);
                    return userRepository.save(user);
                });
    }

    @Transactional
    public EmailAccount upsertEmailAccount(User user, EmailProvider provider,
                                           String accountEmail, String accessToken,
                                           String refreshToken, OffsetDateTime tokenExpiry) {
        return emailAccountRepository
                .findByUserAndProviderAndAccountEmail(user, provider, accountEmail)
                .map(account -> {
                    account.setAccessToken(accessToken);
                    account.setRefreshToken(refreshToken);
                    account.setTokenExpiry(tokenExpiry);
                    account.setNeedsReauth(false);
                    return emailAccountRepository.save(account);
                })
                .orElseGet(() -> {
                    EmailAccount account = new EmailAccount();
                    account.setUser(user);
                    account.setProvider(provider);
                    account.setAccountEmail(accountEmail);
                    account.setAccessToken(accessToken);
                    account.setRefreshToken(refreshToken);
                    account.setTokenExpiry(tokenExpiry);
                    return emailAccountRepository.save(account);
                });
    }
}