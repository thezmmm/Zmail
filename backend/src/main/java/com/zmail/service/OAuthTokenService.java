package com.zmail.service;

import com.zmail.model.EmailAccount;
import com.zmail.model.EmailAccountRepository;
import com.zmail.model.EmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final RestTemplate restTemplate;

    /** Per-account mutex: prevents two concurrent threads from both issuing a refresh call
     *  with the same refresh token, which would invalidate it on Microsoft OAuth. */
    private final ConcurrentHashMap<UUID, Object> refreshLocks = new ConcurrentHashMap<>();

    public String getValidAccessToken(EmailAccount account) {
        if (account.isNeedsReauth()) {
            // Use the stored access token if it hasn't expired yet
            if (account.getTokenExpiry() != null && account.getTokenExpiry().isAfter(OffsetDateTime.now())) {
                return account.getAccessToken();
            }
            throw new IllegalStateException(
                    "Account " + account.getId() + " requires re-authorization");
        }
        if (isExpiringSoon(account)) {
            Object lock = refreshLocks.computeIfAbsent(account.getId(), id -> new Object());
            synchronized (lock) {
                // Re-read from DB after acquiring the lock: a concurrent thread may have already
                // completed a refresh while this thread was waiting. If so, skip the refresh.
                EmailAccount current = emailAccountRepository.findById(account.getId())
                        .orElseThrow(() -> new IllegalStateException("Account not found: " + account.getId()));
                if (isExpiringSoon(current)) {
                    refreshAccessToken(current);
                }
                return current.getAccessToken();
            }
        }
        return account.getAccessToken();
    }

    @Transactional
    public void refreshAccessToken(EmailAccount account) {
        String registrationId = account.getProvider() == EmailProvider.GMAIL ? "google" : "microsoft";
        ClientRegistration reg = clientRegistrationRepository.findByRegistrationId(registrationId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", account.getRefreshToken());
        params.add("client_id", reg.getClientId());
        params.add("client_secret", reg.getClientSecret());

        ResponseEntity<Map<String, Object>> resp;
        try {
            resp = restTemplate.exchange(
                    reg.getProviderDetails().getTokenUri(),
                    HttpMethod.POST,
                    new HttpEntity<>(params, headers),
                    MAP_TYPE
            );
        } catch (HttpClientErrorException e) {
            // 400 invalid_grant / 401 unauthorized = refresh token revoked or expired
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 401) {
                account.setNeedsReauth(true);
                emailAccountRepository.save(account);
                log.warn("Refresh token invalid for account {} ({}), marking needs-reauth",
                        account.getId(), e.getStatusCode());
                throw new IllegalStateException(
                        "Account " + account.getId() + " requires re-authorization", e);
            }
            throw e;
        }

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("OAuth2 token refresh failed for account " + account.getId());
        }

        Map<String, Object> data = resp.getBody();
        account.setAccessToken((String) data.get("access_token"));
        Number expiresInNum = (Number) data.get("expires_in");
        long expiresIn = expiresInNum != null ? expiresInNum.longValue() : 3600L;
        account.setTokenExpiry(OffsetDateTime.now().plusSeconds(expiresIn));
        // Microsoft returns a new refresh_token on every refresh; always save it when present
        String newRefreshToken = (String) data.get("refresh_token");
        if (newRefreshToken != null && !newRefreshToken.isBlank()) {
            account.setRefreshToken(newRefreshToken);
        }
        account.setNeedsReauth(false);
        emailAccountRepository.save(account);
        log.debug("Refreshed token for account {}", account.getId());
    }

    private boolean isExpiringSoon(EmailAccount account) {
        return account.getTokenExpiry() != null
                && account.getTokenExpiry().isBefore(OffsetDateTime.now().plusMinutes(5));
    }
}