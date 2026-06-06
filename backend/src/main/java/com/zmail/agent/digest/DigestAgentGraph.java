package com.zmail.agent.digest;

import com.zmail.agent.digest.node.FetchSelectedNode;
import com.zmail.agent.digest.node.GenerateDigestNode;
import com.zmail.agent.digest.node.SummarizeNode;
import com.zmail.agent.model.DigestResult;
import com.zmail.agent.model.EmailRef;
import com.zmail.agent.model.SummaryResult;
import com.zmail.email.EmailMeta;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DigestAgentGraph {

    private final FetchSelectedNode  fetchSelectedNode;
    private final SummarizeNode      summarizeNode;
    private final GenerateDigestNode generateDigestNode;

    private CompiledGraph<DigestAgentState> compiledGraph;
    private CompiledGraph<DigestAgentState> digestOnlyGraph;

    @PostConstruct
    void initGraph() {
        try {
            compiledGraph = new StateGraph<>(DigestAgentState::new)
                    .addNode("fetch",    AsyncNodeAction.node_async(fetchSelectedNode))
                    .addNode("summarize", AsyncNodeAction.node_async(summarizeNode))
                    .addNode("digest",   AsyncNodeAction.node_async(generateDigestNode))
                    .addEdge(GraphDefinition.START, "fetch")
                    .addEdge("fetch",    "summarize")
                    .addEdge("summarize", "digest")
                    .addEdge("digest",   GraphDefinition.END)
                    .compile();

            digestOnlyGraph = new StateGraph<>(DigestAgentState::new)
                    .addNode("digest", AsyncNodeAction.node_async(generateDigestNode))
                    .addEdge(GraphDefinition.START, "digest")
                    .addEdge("digest", GraphDefinition.END)
                    .compile();

            log.info("DigestAgentGraph initialized");
        } catch (GraphStateException e) {
            throw new IllegalStateException("Failed to build digest agent graph", e);
        }
    }

    public DigestResult run(UUID userId, String sessionId, List<EmailRef> emailRefs) {
        Map<String, Object> initState = new HashMap<>();
        initState.put(DigestAgentState.USER_ID,    userId);
        initState.put(DigestAgentState.SESSION_ID, sessionId);
        initState.put(DigestAgentState.EMAIL_REFS, emailRefs);

        try {
            Optional<DigestAgentState> result = compiledGraph.invoke(initState);
            return result.map(DigestAgentState::digest).orElse(DigestResult.empty());
        } catch (Exception e) {
            log.error("DigestAgent failed for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Digest execution failed", e);
        }
    }

    /**
     * Skips fetch + summarize entirely — uses pre-built summaries from the DB.
     * Runs only GenerateDigestNode (1 LLM call).
     */
    public DigestResult runFromSummaries(UUID userId, String sessionId,
                                         List<String> emailIds,
                                         Map<String, EmailMeta> metaMap,
                                         Map<String, SummaryResult> summaries) {
        Map<String, Object> initState = new HashMap<>();
        initState.put(DigestAgentState.USER_ID,       userId);
        initState.put(DigestAgentState.SESSION_ID,    sessionId);
        initState.put(DigestAgentState.EMAIL_IDS,     emailIds);
        initState.put(DigestAgentState.EMAIL_META_MAP, metaMap);
        initState.put(DigestAgentState.SUMMARIES,     summaries);

        try {
            Optional<DigestAgentState> result = digestOnlyGraph.invoke(initState);
            return result.map(DigestAgentState::digest).orElse(DigestResult.empty());
        } catch (Exception e) {
            log.error("DigestAgent (fast) failed for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Digest execution failed", e);
        }
    }
}