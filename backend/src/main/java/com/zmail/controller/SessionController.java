package com.zmail.controller;

import com.zmail.agent.chat.MainAgentService;
import com.zmail.agent.chat.SessionMemoryManager;
import com.zmail.model.AgentMessage;
import com.zmail.model.AgentSession;
import com.zmail.model.ApiResponse;
import com.zmail.service.AgentSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agent/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final AgentSessionService sessionService;
    private final MainAgentService mainAgentService;
    private final SessionMemoryManager sessionMemoryManager;

    public record CreateSessionRequest(String title) {}

    public record SessionDto(UUID id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record MessageDto(UUID id, String role, String content, OffsetDateTime createdAt) {}
    public record SessionDetailDto(SessionDto session, List<MessageDto> messages) {}

    @PostMapping
    public ResponseEntity<ApiResponse<SessionDto>> create(
            @RequestBody(required = false) CreateSessionRequest req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        String title = (req != null && req.title() != null && !req.title().isBlank())
                ? req.title() : "New conversation";
        AgentSession s = sessionService.create(userId, title);
        return ResponseEntity.ok(ApiResponse.ok(toDto(s)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionDto>>> list(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        List<SessionDto> dtos = sessionService.listForUser(userId).stream()
                .map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<SessionDetailDto>> get(
            @PathVariable UUID sessionId, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        AgentSession session = sessionService.getOrThrow(sessionId, userId);
        List<MessageDto> messages = sessionService.listMessages(sessionId, userId)
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(
                new SessionDetailDto(toDto(session), messages)));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<MessageDto>>> messages(
            @PathVariable UUID sessionId, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        List<MessageDto> messages = sessionService.listMessages(sessionId, userId)
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID sessionId, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        sessionService.delete(sessionId, userId);
        mainAgentService.evict(sessionId.toString());
        sessionMemoryManager.evict(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private SessionDto toDto(AgentSession s) {
        return new SessionDto(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private MessageDto toDto(AgentMessage m) {
        return new MessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
    }
}