package com.zmail.agent.node;

import com.zmail.agent.ActionType;
import com.zmail.agent.ClassificationResult;
import com.zmail.agent.EmailAgentState;
import com.zmail.memory.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActionDecideNode implements NodeAction<EmailAgentState> {

    private final MemoryService memoryService;

    private static final Set<String> ARCHIVE_CATEGORIES = Set.of("SPAM", "NEWSLETTER");

    @Override
    public Map<String, Object> apply(EmailAgentState state) throws Exception {
        Map<String, ClassificationResult> classifications = state.classifications();
        Map<String, ActionType> decisions = new HashMap<>();

        List<String> replyIds   = new ArrayList<>();
        List<String> archiveIds = new ArrayList<>();
        List<String> flagIds    = new ArrayList<>();

        for (String id : state.emailIds()) {
            ClassificationResult clf = classifications.getOrDefault(id, ClassificationResult.defaultResult());
            ActionType action = decide(clf);
            decisions.put(id, action);

            switch (action) {
                case REPLY   -> replyIds.add(id);
                case ARCHIVE -> archiveIds.add(id);
                case FLAG    -> flagIds.add(id);
                default      -> { /* NONE */ }
            }
            log.debug("Email {} → {}", id, action);
        }

        List<String> pendingActions = new ArrayList<>();
        if (!replyIds.isEmpty())   pendingActions.add("reply");
        if (!archiveIds.isEmpty()) pendingActions.add("archive");
        if (!flagIds.isEmpty())    pendingActions.add("flag");

        log.info("Decided for {} emails: reply={}, archive={}, flag={}",
                decisions.size(), replyIds.size(), archiveIds.size(), flagIds.size());

        persist(state, decisions);

        return Map.of(
                EmailAgentState.DECISIONS,       decisions,
                EmailAgentState.REPLY_IDS,       replyIds,
                EmailAgentState.ARCHIVE_IDS,     archiveIds,
                EmailAgentState.FLAG_IDS,        flagIds,
                EmailAgentState.PENDING_ACTIONS, pendingActions
        );
    }

    private void persist(EmailAgentState state, Map<String, ActionType> decisions) {
        try {
            memoryService.saveDecisionBatch(
                    state.userId(),
                    state.emailIds(),
                    state.emailMetaMap(),
                    state.classifications(),
                    state.summaries(),
                    decisions);
        } catch (Exception e) {
            log.error("Failed to persist processing results: {}", e.getMessage(), e);
        }
    }

    private ActionType decide(ClassificationResult clf) {
        if (ARCHIVE_CATEGORIES.contains(clf.category())) return ActionType.ARCHIVE;
        if (clf.requiresResponse() && "HIGH".equals(clf.priority())) return ActionType.REPLY;
        if (clf.requiresResponse() || "HIGH".equals(clf.priority())) return ActionType.FLAG;
        return ActionType.ARCHIVE;
    }
}