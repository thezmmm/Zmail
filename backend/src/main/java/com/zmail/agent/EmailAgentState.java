package com.zmail.agent;

import com.zmail.email.EmailMeta;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EmailAgentState extends AgentState {

    // ── pipeline inputs ───────────────────────────────────────────────────────
    public static final String USER_ID         = "userId";
    public static final String MAX_RESULTS     = "maxResults";

    // ── Step 3: slim state (no email bodies) ─────────────────────────────────
    public static final String EMAIL_IDS       = "emailIds";         // List<String>
    public static final String EMAIL_META_MAP  = "emailMetaMap";     // Map<String, EmailMeta>

    // ── LLM results ──────────────────────────────────────────────────────────
    public static final String CLASSIFICATIONS = "classifications";  // Map<String, ClassificationResult>
    public static final String SUMMARIES       = "summaries";        // Map<String, SummaryResult>
    public static final String DECISIONS       = "decisions";        // Map<String, ActionType>
    public static final String REPLY_DRAFTS    = "replyDrafts";      // Map<String, String>

    // ── Step 2: dispatch routing ──────────────────────────────────────────────
    public static final String REPLY_IDS       = "replyIds";         // List<String>
    public static final String ARCHIVE_IDS     = "archiveIds";       // List<String>
    public static final String FLAG_IDS        = "flagIds";          // List<String>
    public static final String PENDING_ACTIONS = "pendingActions";   // List<String>
    public static final String CURRENT_ACTION  = "currentAction";    // String

    public EmailAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public UUID userId() {
        return this.<UUID>value(USER_ID).orElseThrow(() -> new IllegalStateException("userId not set"));
    }

    public int maxResults() {
        return this.<Integer>value(MAX_RESULTS).orElse(20);
    }

    @SuppressWarnings("unchecked")
    public List<String> emailIds() {
        return this.<List<String>>value(EMAIL_IDS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, EmailMeta> emailMetaMap() {
        return this.<Map<String, EmailMeta>>value(EMAIL_META_MAP).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, ClassificationResult> classifications() {
        return this.<Map<String, ClassificationResult>>value(CLASSIFICATIONS).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, SummaryResult> summaries() {
        return this.<Map<String, SummaryResult>>value(SUMMARIES).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, ActionType> decisions() {
        return this.<Map<String, ActionType>>value(DECISIONS).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> replyDrafts() {
        return this.<Map<String, String>>value(REPLY_DRAFTS).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> replyIds() {
        return this.<List<String>>value(REPLY_IDS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> archiveIds() {
        return this.<List<String>>value(ARCHIVE_IDS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> flagIds() {
        return this.<List<String>>value(FLAG_IDS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> pendingActions() {
        return this.<List<String>>value(PENDING_ACTIONS).orElse(List.of());
    }

    public String currentAction() {
        return this.<String>value(CURRENT_ACTION).orElse(null);
    }
}