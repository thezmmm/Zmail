package com.zmail.controller;

import com.zmail.agent.model.EmailRef;
import com.zmail.agent.chat.MainAgentService;
import com.zmail.agent.chat.MainAgentTools;
import com.zmail.model.AgentSession;
import com.zmail.service.AgentSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final MainAgentService    mainAgentService;
    private final MainAgentTools      mainAgentTools;
    private final AgentSessionService sessionService;

    public record ChatRequest(
            String sessionId,
            String message,
            List<EmailRefDto> emails  // optional: selected emails
    ) {}

    public record EmailRefDto(String providerId, String accountId) {}

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        SseEmitter emitter = new SseEmitter(300_000L); // 5-min timeout

        // Validate session ownership
        AgentSession session = sessionService.getOrThrow(UUID.fromString(req.sessionId()), userId);

        // Persist user message
        sessionService.appendMessage(session.getId(), "USER", req.message());

        // Register selected email context for tool use
        List<EmailRef> refs = req.emails() == null ? List.of() :
                req.emails().stream()
                        .map(e -> new EmailRef(e.providerId(), UUID.fromString(e.accountId())))
                        .toList();
        mainAgentTools.registerContext(req.sessionId(), userId, refs);

        StringBuilder assistantResponse = new StringBuilder();

        mainAgentService.chat(req.sessionId(), req.message())
                .onPartialResponse(token -> {
                    assistantResponse.append(token);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(response -> {
                    sessionService.appendMessage(session.getId(), "ASSISTANT",
                            assistantResponse.toString());
                    mainAgentTools.clearContext(req.sessionId());
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    } catch (IOException ignored) {}
                    emitter.complete();
                    log.debug("Chat complete for session {}", req.sessionId());
                })
                .onError(err -> {
                    mainAgentTools.clearContext(req.sessionId());
                    log.error("Chat error for session {}: {}", req.sessionId(), err.getMessage());
                    emitter.completeWithError(err);
                })
                .start();

        return emitter;
    }
}