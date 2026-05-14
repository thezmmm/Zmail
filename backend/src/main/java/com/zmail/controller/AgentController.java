package com.zmail.controller;

import com.zmail.agent.AgentRunResult;
import com.zmail.agent.EmailAgentGraph;
import com.zmail.model.ApiResponse;
import com.zmail.service.RunGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final EmailAgentGraph emailAgentGraph;
    private final RunGuard runGuard;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<AgentRunResult>> runAgent(
            Authentication auth,
            @RequestParam(defaultValue = "20") int maxResults) {

        UUID userId = UUID.fromString(auth.getName());
        runGuard.acquire(userId);
        try {
            AgentRunResult result = emailAgentGraph.run(userId, maxResults);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } finally {
            runGuard.release(userId);
        }
    }
}