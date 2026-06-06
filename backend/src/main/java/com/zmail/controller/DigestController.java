package com.zmail.controller;

import com.zmail.model.ApiResponse;
import com.zmail.model.DailyDigestDto;
import com.zmail.service.DigestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/digest")
@RequiredArgsConstructor
public class DigestController {

    private final DigestService digestService;

    /** Returns today's cached digest, or 404 if none has been generated yet. */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<DailyDigestDto>> getToday(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return digestService.findToday(userId)
                .<ResponseEntity<ApiResponse<DailyDigestDto>>>map(d -> ResponseEntity.ok(ApiResponse.ok(d)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No digest for today")));
    }

    /**
     * Generates today's digest.  Returns the cached version if one already exists,
     * unless {@code force=true} is passed to trigger a fresh LLM run.
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<DailyDigestDto>> generate(
            @RequestParam(defaultValue = "false") boolean force,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        DailyDigestDto digest = digestService.generate(userId, force);
        return ResponseEntity.ok(ApiResponse.ok(digest));
    }
}
