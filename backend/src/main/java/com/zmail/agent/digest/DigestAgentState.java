package com.zmail.agent.digest;

import com.zmail.agent.model.DigestResult;
import com.zmail.agent.model.EmailRef;
import com.zmail.agent.model.SummaryResult;
import com.zmail.email.EmailMeta;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DigestAgentState extends AgentState {

    public static final String SESSION_ID       = "sessionId";
    public static final String USER_ID         = "userId";
    public static final String EMAIL_REFS      = "emailRefs";        // List<EmailRef>
    public static final String EMAIL_IDS       = "emailIds";         // List<String>
    public static final String EMAIL_META_MAP  = "emailMetaMap";     // Map<String, EmailMeta>
    public static final String EMAIL_BODIES    = "emailBodies";      // Map<String, String> providerId→body
    public static final String PRE_SUMMARIES   = "preSummaries";     // Map<String, SummaryResult> from DB
    public static final String SUMMARIES       = "summaries";        // Map<String, SummaryResult>
    public static final String DIGEST          = "digest";           // DigestResult

    public DigestAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public String sessionId() {
        return this.<String>value(SESSION_ID).orElse(null);
    }

    public UUID userId() {
        return this.<UUID>value(USER_ID).orElseThrow(() -> new IllegalStateException("userId not set"));
    }

    @SuppressWarnings("unchecked")
    public List<EmailRef> emailRefs() {
        return this.<List<EmailRef>>value(EMAIL_REFS).orElse(List.of());
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
    public Map<String, String> emailBodies() {
        return this.<Map<String, String>>value(EMAIL_BODIES).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, SummaryResult> preSummaries() {
        return this.<Map<String, SummaryResult>>value(PRE_SUMMARIES).orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, SummaryResult> summaries() {
        return this.<Map<String, SummaryResult>>value(SUMMARIES).orElse(Map.of());
    }

    public DigestResult digest() {
        return this.<DigestResult>value(DIGEST).orElse(DigestResult.empty());
    }
}