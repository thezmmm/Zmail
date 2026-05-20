package com.zmail.controller;

import com.zmail.email.EmailDraft;
import com.zmail.email.EmailMessage;
import com.zmail.model.ApiResponse;
import com.zmail.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmailMessage>>> getUnread(
            Authentication auth,
            @RequestParam(defaultValue = "20") int maxResults) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(emailService.fetchUnread(userId, maxResults)));
    }

    @PostMapping("/{messageId}/archive")
    public ResponseEntity<ApiResponse<Void>> archive(
            @PathVariable String messageId,
            @RequestParam UUID accountId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        emailService.archive(userId, accountId, messageId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{messageId}/flag")
    public ResponseEntity<ApiResponse<Void>> flag(
            @PathVariable String messageId,
            @RequestParam UUID accountId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        emailService.flag(userId, accountId, messageId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable String messageId,
            @RequestParam UUID accountId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        emailService.markRead(userId, accountId, messageId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<ApiResponse<EmailMessage>> getById(
            @PathVariable String messageId,
            @RequestParam UUID accountId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok(
                emailService.fetchById(userId, accountId, messageId)));
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Void>> send(
            @Valid @RequestBody SendRequest req,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        emailService.send(userId, req.accountId(),
                new EmailDraft(req.to(), req.subject(), req.body()));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    record SendRequest(
            UUID accountId,
            @NotEmpty(message = "At least one recipient is required") List<String> to,
            @NotBlank(message = "Subject must not be blank") String subject,
            @NotBlank(message = "Body must not be blank") String body
    ) {}
}