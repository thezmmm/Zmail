package com.zmail.controller;

import com.zmail.agent.chat.SessionMemoryManager;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final MainAgentService    mainAgentService;
    private final MainAgentTools      mainAgentTools;
    private final AgentSessionService sessionService;
    private final SessionMemoryManager sessionMemoryManager;

    public record ChatRequest(
            @NotBlank String sessionId,
            @NotBlank String message,
            List<EmailRefDto> emails  // optional: selected emails
    ) {}

    public record EmailRefDto(String providerId, String accountId) {}

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        SseEmitter emitter = new SseEmitter(300_000L); // 5-min timeout

        // Validate session ownership
        AgentSession session = sessionService.getOrThrow(UUID.fromString(req.sessionId()), userId);

        // Restore LangChain4j memory from DB if this session hasn't been seeded yet
        sessionMemoryManager.ensureSeeded(session.getId(), userId);

        // Persist user message
        sessionService.appendMessage(session.getId(), "USER", req.message());

        // Register selected email context for tool use
        List<EmailRef> refs = req.emails() == null ? List.of() :
                req.emails().stream()
                        .map(e -> new EmailRef(e.providerId(), UUID.fromString(e.accountId())))
                        .toList();
        mainAgentTools.registerContext(req.sessionId(), userId, refs, status -> {
            try {
                emitter.send(SseEmitter.event().name("tool_start").data(status));
            } catch (java.io.IOException ignored) {}
        });

        StringBuilder assistantResponse = new StringBuilder();
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        mainAgentService.chat(req.sessionId(), req.message())
                .onPartialResponse(token -> {
                    assistantResponse.append(token);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        if (emitterDone.compareAndSet(false, true)) emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(response -> {
                    sessionService.appendMessage(session.getId(), "ASSISTANT",
                            assistantResponse.toString());
                    mainAgentTools.clearContext(req.sessionId());
                    sessionMemoryManager.maybeCompress(session.getId(), userId);

                    // Send "done" immediately so the frontend's streaming indicator stops
                    try { emitter.send(SseEmitter.event().name("done").data("[DONE]")); }
                    catch (IOException ignored) {}

                    String currentTitle = session.getTitle();
                    boolean needsTitle = "新对话".equals(currentTitle) || "New conversation".equals(currentTitle);

                    if (needsTitle) {
                        // Generate title async — sends a "session_title" event when ready, then closes emitter
                        CompletableFuture.runAsync(() -> {
                            String title = sessionService.generateAndSaveTitle(session.getId(), req.message());
                            if (emitterDone.compareAndSet(false, true)) {
                                try { emitter.send(SseEmitter.event().name("session_title").data(title)); }
                                catch (IOException ignored) {}
                                emitter.complete();
                            }
                        });
                    } else {
                        if (emitterDone.compareAndSet(false, true)) emitter.complete();
                    }
                    log.debug("Chat complete for session {}", req.sessionId());
                })
                .onError(err -> {
                    mainAgentTools.clearContext(req.sessionId());
                    if (emitterDone.compareAndSet(false, true)) {
                        log.error("Chat error for session {}: {}", req.sessionId(), err.getMessage());
                        // Send error as SSE event so Spring doesn't dispatch through @ControllerAdvice
                        // (which would produce a misleading 500 response)
                        try {
                            emitter.send(SseEmitter.event().name("error").data(toLlmErrorMessage(err)));
                        } catch (IOException ignored) {}
                        emitter.complete();
                    } else {
                        log.debug("Chat stream ended after client disconnect for session {}", req.sessionId());
                    }
                })
                .start();

        return emitter;
    }

    private static String toLlmErrorMessage(Throwable err) {
        String msg = err.getMessage();
        if (msg == null) return "请求失败，请稍后重试。";
        if (msg.contains("429")) return "AI 服务请求频率限制，请稍后重试。";
        if (msg.contains("401") || msg.contains("403")) return "AI 服务认证失败，请检查配置。";
        if (msg.contains("timeout") || msg.contains("timed out")) return "AI 服务响应超时，请重试。";
        return "AI 服务暂时不可用，请稍后重试。";
    }
}