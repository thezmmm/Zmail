package com.zmail.controller;

import com.zmail.model.ApiResponse;
import com.zmail.model.ProcessingResult;
import com.zmail.model.ProcessingResultRepository;
import com.zmail.service.EmailProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/results")
@RequiredArgsConstructor
public class ResultController {

    private final ProcessingResultRepository repository;
    private final EmailProcessingService processingService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProcessingResult>>> list(
            Authentication auth,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20)
            @SortDefault.SortDefaults({
                @SortDefault(sort = "receivedAt", direction = Sort.Direction.DESC),
                @SortDefault(sort = "id",         direction = Sort.Direction.ASC)
            }) Pageable pageable) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid pagination: page >= 0 and size >= 1 required"));
        }
        UUID userId = UUID.fromString(auth.getName());
        Page<ProcessingResult> page = (category != null && !category.isBlank())
                ? repository.findByUserIdAndCategory(userId, category, pageable)
                : repository.findByUserId(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcessingResult>> get(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        ProcessingResult result = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Result not found: " + id));
        if (!result.getUserId().equals(userId)) {
            throw new SecurityException("Access denied");
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<ApiResponse<ProcessingResult>> analyze(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        ProcessingResult result = processingService.analyzeOnDemand(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/{id}/draft")
    public ResponseEntity<ApiResponse<ProcessingResult>> draft(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        ProcessingResult result = processingService.generateDraft(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
