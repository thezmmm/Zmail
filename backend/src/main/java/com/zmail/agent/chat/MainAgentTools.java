package com.zmail.agent.chat;

import com.zmail.agent.action.ActionAgentService;
import com.zmail.agent.digest.DigestAgentGraph;
import com.zmail.agent.model.DigestResult;
import com.zmail.agent.model.EmailDigest;
import com.zmail.agent.model.EmailRef;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tools available to the MainAgent LLM. Email refs for the current request are
 * registered by ChatController before invoking the agent, and cleared after.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MainAgentTools {

    private final DigestAgentGraph   digestAgentGraph;
    private final ActionAgentService actionAgentService;

    /** sessionId → (userId, emailRefs) for the pending request. */
    private final Map<String, PendingContext> pendingContexts = new ConcurrentHashMap<>();

    public void registerContext(String sessionId, UUID userId, List<EmailRef> emailRefs) {
        pendingContexts.put(sessionId, new PendingContext(userId, emailRefs));
    }

    public void clearContext(String sessionId) {
        pendingContexts.remove(sessionId);
    }

    // ── Tool definitions ──────────────────────────────────────────────────────

    @Tool("Analyze and summarize the emails the user has selected. " +
          "Call this when the user wants a digest, overview, or plan for their selected emails.")
    public String analyzeSelectedEmails(
            @P("The current session ID") String sessionId) {

        PendingContext ctx = pendingContexts.get(sessionId);
        if (ctx == null || ctx.emailRefs().isEmpty()) {
            return "No emails are currently selected for analysis. " +
                   "Please ask the user to select emails in the UI first.";
        }

        log.info("analyzeSelectedEmails called for session {} ({} emails)",
                sessionId, ctx.emailRefs().size());

        DigestResult result = digestAgentGraph.run(ctx.userId(), sessionId, ctx.emailRefs());
        return formatDigest(result);
    }

    @Tool("Draft a reply for a specific email based on the user's instructions.")
    public String draftEmailReply(
            @P("The current session ID") String sessionId,
            @P("The provider ID of the email to reply to") String emailProviderId,
            @P("The user's instruction for the reply") String instruction) {

        PendingContext ctx = pendingContexts.get(sessionId);
        if (ctx == null) return "Session context not found.";

        EmailRef ref = ctx.emailRefs().stream()
                .filter(r -> r.providerId().equals(emailProviderId))
                .findFirst()
                .orElse(null);

        if (ref == null) {
            return "Email " + emailProviderId + " is not in the selected set.";
        }

        String draft = actionAgentService.draftReply(ctx.userId(), ref, instruction);
        log.info("draftEmailReply generated for email {} in session {}", emailProviderId, sessionId);
        return "Here is a draft reply:\n\n" + draft;
    }

    @Tool("Answer a question about a specific email (e.g. what action is required, who sent it, key details).")
    public String askAboutEmail(
            @P("The current session ID") String sessionId,
            @P("The provider ID of the email") String emailProviderId,
            @P("The user's question about the email") String question) {

        PendingContext ctx = pendingContexts.get(sessionId);
        if (ctx == null) return "Session context not found.";

        EmailRef ref = ctx.emailRefs().stream()
                .filter(r -> r.providerId().equals(emailProviderId))
                .findFirst()
                .orElse(null);

        if (ref == null) {
            return "Email " + emailProviderId + " is not in the selected set.";
        }

        return actionAgentService.analyzeEmail(ctx.userId(), ref, question);
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private String formatDigest(DigestResult digest) {
        StringBuilder sb = new StringBuilder();
        sb.append(digest.overview()).append("\n\n");

        if (!digest.priorities().isEmpty()) {
            sb.append("**Priority Actions:**\n");
            for (int i = 0; i < digest.priorities().size(); i++) {
                sb.append(i + 1).append(". ").append(digest.priorities().get(i)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("**Email Summaries:**\n");
        for (EmailDigest e : digest.emails()) {
            sb.append("- **").append(e.subject()).append("** (from ").append(e.sender()).append(")\n");
            sb.append("  ").append(e.summary()).append("\n");
            if (!e.actionItems().isEmpty()) {
                sb.append("  Actions: ").append(String.join("; ", e.actionItems())).append("\n");
            }
        }
        return sb.toString();
    }

    private record PendingContext(UUID userId, List<EmailRef> emailRefs) {}
}