package com.zmail.controller;

import com.zmail.model.ApiResponse;
import com.zmail.model.ProcessingResult;
import com.zmail.service.DraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    /** List reply drafts awaiting user review. */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<ProcessingResult>>> listPending(
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(draftService.listPending(userId, pageable)));
    }

    /** Approve a draft — sends the email. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            Authentication auth,
            @PathVariable UUID id) {

        draftService.approve(UUID.fromString(auth.getName()), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Reject a draft — marks it rejected, email not sent. */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            Authentication auth,
            @PathVariable UUID id) {

        draftService.reject(UUID.fromString(auth.getName()), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}