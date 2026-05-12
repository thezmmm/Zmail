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
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final RestTemplate restTemplate;

    public String getValidAccessToken(EmailAccount account) {
        if (isExpiringSoon(account)) {
            refreshAccessToken(account);
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

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                reg.getProviderDetails().getTokenUri(),
                HttpMethod.POST,
                new HttpEntity<>(params, headers),
                MAP_TYPE
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("OAuth2 token refresh failed for account " + account.getId());
        }

        Map<String, Object> data = resp.getBody();
        account.setAccessToken((String) data.get("access_token"));
        account.setTokenExpiry(OffsetDateTime.now().plusSeconds(
                ((Number) data.get("expires_in")).longValue()));
        emailAccountRepository.save(account);
        log.debug("Refreshed token for account {}", account.getId());
    }

    private boolean isExpiringSoon(EmailAccount account) {
        return account.getTokenExpiry() != null
                && account.getTokenExpiry().isBefore(OffsetDateTime.now().plusMinutes(5));
    }
}