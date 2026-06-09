package com.zmail.controller;

import com.zmail.model.ApiResponse;
import com.zmail.model.EmailAccount;
import com.zmail.model.EmailProvider;
import com.zmail.service.EmailService;
import com.zmail.service.EmailSyncService;
import com.zmail.service.InitialSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages a user's linked email accounts.
 *
 * One user may link multiple accounts across providers (e.g. two Gmail addresses +
 * one Outlook). All accounts are managed here; email operations (fetch, send, etc.)
 * remain on /emails.
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final EmailService emailService;
    private final InitialSyncService initialSyncService;
    private final EmailSyncService syncService;

    /** List all linked email accounts for the authenticated user. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDto>>> list(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        List<AccountDto> dtos = emailService.getAccounts(userId).stream()
                .map(AccountDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    /** Remove a linked account. Associated processing history is retained. */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID accountId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        emailService.deleteAccount(userId, accountId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * Manually triggers an incremental sync for the authenticated user.
     * Returns the number of newly classified emails.
     * Responds 409 if a sync (initial or scheduled) is already in progress or the cooldown hasn't elapsed.
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Integer>> sync(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        int count = syncService.syncUser(userId);
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    /**
     * Returns the current initial-sync status for the authenticated user.
     * Defaults to DONE when no sync has been triggered in this JVM session.
     */
    @GetMapping("/sync-status")
    public ResponseEntity<ApiResponse<InitialSyncService.SyncStatus>> syncStatus(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(initialSyncService.getStatus(userId)));
    }

    /**
     * Returns the OAuth login URL for the given account's provider.
     * The client should open this URL in a browser to complete re-authorization.
     * After OAuth completes, the callback updates the stored tokens and issues a new JWT.
     */
    @GetMapping("/{accountId}/reauth")
    public ResponseEntity<ApiResponse<ReauthDto>> reauth(
            @PathVariable UUID accountId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        EmailAccount account = emailService.getAccount(userId, accountId);
        String loginPath = account.getProvider() == EmailProvider.GMAIL
                ? "/auth/gmail/login"
                : "/auth/msgraph/login";
        return ResponseEntity.ok(ApiResponse.ok(new ReauthDto(account.getProvider(), loginPath)));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    record AccountDto(
            UUID id,
            EmailProvider provider,
            String accountEmail,
            boolean needsReauth,
            OffsetDateTime createdAt
    ) {
        static AccountDto from(EmailAccount a) {
            return new AccountDto(a.getId(), a.getProvider(), a.getAccountEmail(),
                    a.isNeedsReauth(), a.getCreatedAt());
        }
    }

    record ReauthDto(EmailProvider provider, String loginPath) {}
}