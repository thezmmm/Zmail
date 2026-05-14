package com.zmail.agent;

import com.zmail.agent.node.*;
import com.zmail.config.AgentProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailAgentGraph {

    private final EmailFetchNode   emailFetchNode;
    private final ClassifyNode     classifyNode;
    private final SummarizeNode    summarizeNode;
    private final ActionDecideNode actionDecideNode;
    private final DispatchNode     dispatchNode;
    private final ReplyNode        replyNode;
    private final ArchiveNode      archiveNode;
    private final FlagNode         flagNode;
    private final AgentProperties  agentProperties;

    private CompiledGraph<EmailAgentState> compiledGraph;

    @PostConstruct
    void initGraph() {
        try {
            compiledGraph = new StateGraph<>(EmailAgentState::new)
                    .addNode("fetch",        AsyncNodeAction.node_async(emailFetchNode))
                    .addNode("classify",     AsyncNodeAction.node_async(classifyNode))
                    .addNode("summarize",    AsyncNodeAction.node_async(summarizeNode))
                    .addNode("actionDecide", AsyncNodeAction.node_async(actionDecideNode))
                    .addNode("dispatch",     AsyncNodeAction.node_async(dispatchNode))
                    .addNode("reply",        AsyncNodeAction.node_async(replyNode))
                    .addNode("archive",      AsyncNodeAction.node_async(archiveNode))
                    .addNode("flag",         AsyncNodeAction.node_async(flagNode))

                    // ── linear pipeline ──────────────────────────────────────
                    .addEdge(GraphDefinition.START, "fetch")
                    .addEdge("fetch",        "classify")
                    .addEdge("classify",     "summarize")
                    .addEdge("summarize",    "actionDecide")

                    // ── actionDecide → dispatch (skip if no emails) ──────────
                    .addConditionalEdges("actionDecide",
                            AsyncEdgeAction.edge_async(
                                    state -> state.pendingActions().isEmpty()
                                            ? GraphDefinition.END
                                            : "dispatch"),
                            Map.of("dispatch", "dispatch",
                                   GraphDefinition.END, GraphDefinition.END))

                    // ── dispatch → action node (loop until queue empty) ──────
                    .addConditionalEdges("dispatch",
                            AsyncEdgeAction.edge_async(state -> {
                                String action = state.currentAction();
                                return ACTION_NODES.contains(action) ? action : GraphDefinition.END;
                            }),
                            Map.of("reply",   "reply",
                                   "archive", "archive",
                                   "flag",    "flag",
                                   GraphDefinition.END, GraphDefinition.END))

                    // ── each action node loops back to dispatch ──────────────
                    .addEdge("reply",   "dispatch")
                    .addEdge("archive", "dispatch")
                    .addEdge("flag",    "dispatch")

                    .compile();

            log.info("EmailAgentGraph initialized");
        } catch (GraphStateException e) {
            throw new IllegalStateException("Failed to build email agent graph", e);
        }
    }

    private static final Set<String> ACTION_NODES = Set.of("reply", "archive", "flag");

    // ── Public API ────────────────────────────────────────────────────────────

    public AgentRunResult run(UUID userId, int maxResults) {
        int effectiveMax = Math.min(maxResults, agentProperties.getMaxEmailsPerRun());

        Map<String, Object> initState = new HashMap<>();
        initState.put(EmailAgentState.USER_ID,     userId);
        initState.put(EmailAgentState.MAX_RESULTS, effectiveMax);

        try {
            Optional<EmailAgentState> result = compiledGraph.invoke(initState);
            return result.map(this::toRunResult).orElse(AgentRunResult.empty());
        } catch (Exception e) {
            log.error("Agent graph execution failed for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Agent execution failed", e);
        }
    }

    private AgentRunResult toRunResult(EmailAgentState state) {
        Map<String, SummaryResult> summaries = state.summaries();
        Map<String, String> summaryTexts = new HashMap<>();
        summaries.forEach((id, s) -> summaryTexts.put(id, s.summary()));

        return new AgentRunResult(
                state.emailIds().size(),
                state.replyIds().size(),
                state.archiveIds().size(),
                state.flagIds().size(),
                summaryTexts
        );
    }
}