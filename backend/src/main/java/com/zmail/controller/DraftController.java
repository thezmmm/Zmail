package com.zmail.controller;

import com.zmail.model.ApiResponse;
import com.zmail.service.DraftService;
import com.zmail.service.DraftService.DraftDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    /** List drafts awaiting user review. */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<DraftDto>>> listPending(
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(
                draftService.listPending(userId, pageable).map(draftService::toDto)));
    }

    /** Look up the most recent draft generated for a given email (used by the email detail page). */
    @GetMapping("/by-result/{resultId}")
    public ResponseEntity<ApiResponse<DraftDto>> getByResult(
            @PathVariable UUID resultId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return draftService.findByResultId(userId, resultId)
                .map(draftService::toDto)
                .map(dto -> ResponseEntity.ok(ApiResponse.ok(dto)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(null)));
    }

    /** Create a new user-composed draft. */
    @PostMapping
    public ResponseEntity<ApiResponse<DraftDto>> create(
            @Valid @RequestBody CreateDraftRequest req,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        DraftDto dto = draftService.toDto(
                draftService.createUserDraft(userId, req.accountId(), req.to(), req.subject(), req.body()));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** Update draft body and/or subject before sending. */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<DraftDto>> update(
            @PathVariable UUID id,
            @RequestBody UpdateDraftRequest req,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        DraftDto dto = draftService.toDto(
                draftService.update(userId, id, req.body(), req.subject()));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /**
     * Approve a draft — sends the email.
     * Accepts an optional body override to update the content in the same request.
     * Also accepts a ProcessingResult.id for backward compat with the email detail page.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<DraftDto>> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveRequest req,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        DraftDto dto = draftService.toDto(
                draftService.approve(userId, id, req != null ? req.body() : null));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** Reject a draft — marks it rejected, email not sent. */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<DraftDto>> reject(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        DraftDto dto = draftService.toDto(draftService.reject(userId, id));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    // ── Request records ───────────────────────────────────────────────────────

    record CreateDraftRequest(
            @jakarta.validation.constraints.NotNull UUID accountId,
            @NotEmpty(message = "At least one recipient required") List<String> to,
            String subject,
            @NotBlank(message = "Body must not be blank") String body
    ) {}

    record UpdateDraftRequest(String body, String subject) {}

    record ApproveRequest(String body) {}
}
