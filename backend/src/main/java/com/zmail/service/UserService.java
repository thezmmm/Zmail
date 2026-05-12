package com.zmail.service;

import com.zmail.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;

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