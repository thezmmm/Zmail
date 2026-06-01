package com.zmail.controller;

import com.zmail.model.ApiResponse;
import com.zmail.model.EmailProvider;
import com.zmail.model.User;
import com.zmail.service.InitialSyncService;
import com.zmail.service.JwtService;
import com.zmail.service.OAuthStateStore;
import com.zmail.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;
    private final InitialSyncService initialSyncService;
    private final OAuthStateStore stateStore;

    @Value("${zmail.gmail.redirect-uri}")
    private String gmailRedirectUri;

    @Value("${zmail.msgraph.redirect-uri}")
    private String msgraphRedirectUri;

    @Value("${zmail.frontend-url}")
    private String frontendUrl;

    // ── Gmail ──────────────────────────────────────────────────────────────────

    @GetMapping("/gmail/login")
    public void initiateGmailLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect(buildGmailAuthUri(stateStore.generate()));
    }

    /** Returns the Google OAuth URL so the authenticated frontend can initiate a link. */
    @PostMapping("/gmail/link-init")
    public ResponseEntity<ApiResponse<String>> gmailLinkInit(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(buildGmailAuthUri(stateStore.generateLink(userId))));
    }

    private String buildGmailAuthUri(String state) {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
        return UriComponentsBuilder
                .fromHttpUrl(google.getProviderDetails().getAuthorizationUri())
                .queryParam("client_id", google.getClientId())
                .queryParam("redirect_uri", gmailRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", google.getScopes()))
                .queryParam("state", state)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .toUriString();
    }

    @GetMapping("/gmail/callback")
    public void handleGmailCallback(@RequestParam String code,
                                    @RequestParam String state,
                                    HttpServletResponse response) throws IOException {
        // Determine flow: link (existing user) or login (find/create user)
        Optional<UUID> linkUserId = stateStore.consumeLink(state);
        if (linkUserId.isEmpty() && !stateStore.consume(state)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or expired OAuth2 state");
            return;
        }

        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");

        // Exchange code for tokens
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", google.getClientId());
        params.add("client_secret", google.getClientSecret());
        params.add("redirect_uri", gmailRedirectUri);
        params.add("grant_type", "authorization_code");

        ResponseEntity<Map<String, Object>> tokenResp = restTemplate.exchange(
                google.getProviderDetails().getTokenUri(),
                HttpMethod.POST,
                new HttpEntity<>(params, formHeaders),
                MAP_TYPE
        );

        if (!tokenResp.getStatusCode().is2xxSuccessful() || tokenResp.getBody() == null) {
            log.error("Token exchange failed: {}", tokenResp.getStatusCode());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Token exchange failed");
            return;
        }

        Map<String, Object> tokenData = tokenResp.getBody();
        String accessToken  = (String) tokenData.get("access_token");
        String refreshToken = (String) tokenData.get("refresh_token");
        Number expiresInNum = (Number) tokenData.get("expires_in");
        long expiresIn      = expiresInNum != null ? expiresInNum.longValue() : 3600L;
        OffsetDateTime tokenExpiry = OffsetDateTime.now().plusSeconds(expiresIn);

        // Parse user info from id_token JWT payload — avoids a second HTTP call
        String rawIdToken = (String) tokenData.get("id_token");
        if (rawIdToken == null) {
            log.error("Google token response missing id_token");
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "id_token missing from Google response");
            return;
        }
        Map<String, Object> userInfo = decodeIdToken(rawIdToken);
        String email = (String) userInfo.get("email");
        if (email == null) {
            log.error("Google id_token missing email claim");
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "email claim missing from id_token");
            return;
        }
        String name  = (String) userInfo.get("name");

        if (linkUserId.isPresent()) {
            // Link flow: attach this Gmail account to the already-authenticated user
            User user = userService.getById(linkUserId.get());
            userService.upsertEmailAccount(user, EmailProvider.GMAIL, email,
                    accessToken, refreshToken, tokenExpiry);
            initialSyncService.triggerAsync(user.getId());
            log.info("Gmail account {} linked to user {}", email, user.getId());
            response.sendRedirect(frontendUrl + "/auth/link-callback?provider=gmail");
        } else {
            // Login flow: find or create the user, then issue a new JWT
            User user = userService.findOrCreate(email, name);
            userService.upsertEmailAccount(user, EmailProvider.GMAIL, email,
                    accessToken, refreshToken, tokenExpiry);
            initialSyncService.triggerAsync(user.getId());
            String jwt = jwtService.generateToken(user.getId().toString());
            log.info("Gmail OAuth2 login completed for {}", email);
            response.sendRedirect(frontendUrl + "/auth/callback?token=" + jwt + "&provider=gmail");
        }
    }

    // ── Microsoft Graph ────────────────────────────────────────────────────────

    @GetMapping("/msgraph/login")
    public void initiateMsgraphLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect(buildMsgraphAuthUri(stateStore.generate()));
    }

    /** Returns the Microsoft OAuth URL so the authenticated frontend can initiate a link. */
    @PostMapping("/msgraph/link-init")
    public ResponseEntity<ApiResponse<String>> msgraphLinkInit(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(buildMsgraphAuthUri(stateStore.generateLink(userId))));
    }

    private String buildMsgraphAuthUri(String state) {
        ClientRegistration microsoft = clientRegistrationRepository.findByRegistrationId("microsoft");
        return UriComponentsBuilder
                .fromHttpUrl(microsoft.getProviderDetails().getAuthorizationUri())
                .queryParam("client_id", microsoft.getClientId())
                .queryParam("redirect_uri", msgraphRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", microsoft.getScopes()))
                .queryParam("state", state)
                .queryParam("prompt", "consent")
                .toUriString();
    }

    @GetMapping("/msgraph/callback")
    public void handleMsgraphCallback(@RequestParam String code,
                                      @RequestParam String state,
                                      HttpServletResponse response) throws IOException {
        // Determine flow: link (existing user) or login (find/create user)
        Optional<UUID> linkUserId = stateStore.consumeLink(state);
        if (linkUserId.isEmpty() && !stateStore.consume(state)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or expired OAuth2 state");
            return;
        }

        ClientRegistration microsoft = clientRegistrationRepository.findByRegistrationId("microsoft");

        // Exchange code for tokens
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", microsoft.getClientId());
        params.add("client_secret", microsoft.getClientSecret());
        params.add("redirect_uri", msgraphRedirectUri);
        params.add("grant_type", "authorization_code");

        ResponseEntity<Map<String, Object>> tokenResp = restTemplate.exchange(
                microsoft.getProviderDetails().getTokenUri(),
                HttpMethod.POST,
                new HttpEntity<>(params, formHeaders),
                MAP_TYPE
        );

        if (!tokenResp.getStatusCode().is2xxSuccessful() || tokenResp.getBody() == null) {
            log.error("MS Graph token exchange failed: {}", tokenResp.getStatusCode());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Token exchange failed");
            return;
        }

        Map<String, Object> tokenData = tokenResp.getBody();
        String accessToken  = (String) tokenData.get("access_token");
        String refreshToken = (String) tokenData.get("refresh_token");
        Number expiresInNum = (Number) tokenData.get("expires_in");
        long expiresIn      = expiresInNum != null ? expiresInNum.longValue() : 3600L;
        OffsetDateTime tokenExpiry = OffsetDateTime.now().plusSeconds(expiresIn);

        // Fetch user profile from MS Graph
        HttpHeaders bearerHeaders = new HttpHeaders();
        bearerHeaders.setBearerAuth(accessToken);
        ResponseEntity<Map<String, Object>> profileResp = restTemplate.exchange(
                "https://graph.microsoft.com/v1.0/me",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders),
                MAP_TYPE
        );

        if (!profileResp.getStatusCode().is2xxSuccessful() || profileResp.getBody() == null) {
            log.error("MS Graph profile fetch failed: {}", profileResp.getStatusCode());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Failed to fetch user profile");
            return;
        }

        Map<String, Object> profile = profileResp.getBody();
        // Prefer mail (actual email address), fallback to userPrincipalName
        String email = profile.get("mail") != null
                ? (String) profile.get("mail")
                : (String) profile.get("userPrincipalName");
        String name  = (String) profile.get("displayName");

        if (linkUserId.isPresent()) {
            // Link flow: attach this Outlook account to the already-authenticated user
            User user = userService.getById(linkUserId.get());
            userService.upsertEmailAccount(user, EmailProvider.MSGRAPH, email,
                    accessToken, refreshToken, tokenExpiry);
            initialSyncService.triggerAsync(user.getId());
            log.info("Outlook account {} linked to user {}", email, user.getId());
            response.sendRedirect(frontendUrl + "/auth/link-callback?provider=msgraph");
        } else {
            // Login flow: find or create the user, then issue a new JWT
            User user = userService.findOrCreate(email, name);
            userService.upsertEmailAccount(user, EmailProvider.MSGRAPH, email,
                    accessToken, refreshToken, tokenExpiry);
            initialSyncService.triggerAsync(user.getId());
            String jwt = jwtService.generateToken(user.getId().toString());
            log.info("MS Graph OAuth2 login completed for {}", email);
            response.sendRedirect(frontendUrl + "/auth/callback?token=" + jwt + "&provider=msgraph");
        }
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    /**
     * Issues a new JWT for the currently authenticated user without re-running OAuth.
     * The caller must present a still-valid JWT; this endpoint is not reachable once
     * the token has already expired. Frontends should call this proactively (e.g. when
     * the stored token has less than 5 minutes remaining).
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<String>> refreshToken(Authentication auth) {
        String newToken = jwtService.generateToken(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(newToken));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> decodeIdToken(String idToken) throws IOException {
        String[] parts = idToken.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return new ObjectMapper().readValue(payload, new TypeReference<>() {});
    }
}