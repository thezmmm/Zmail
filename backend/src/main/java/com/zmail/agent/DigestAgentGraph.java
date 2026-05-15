package com.zmail.agent;

import com.zmail.agent.node.FetchSelectedNode;
import com.zmail.agent.node.GenerateDigestNode;
import com.zmail.agent.node.SummarizeNode;
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
}