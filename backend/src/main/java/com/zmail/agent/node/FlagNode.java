package com.zmail.agent.node;

import com.zmail.agent.EmailAgentState;
import com.zmail.email.EmailMeta;
import com.zmail.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlagNode implements NodeAction<EmailAgentState> {

    private final EmailService emailService;

    @Override
    public Map<String, Object> apply(EmailAgentState state) throws Exception {
        List<String> ids = state.flagIds();
        if (ids.isEmpty()) return Map.of();

        UUID userId = state.userId();
        Map<String, EmailMeta> metaMap = state.emailMetaMap();
        int flagged = 0;

        for (String id : ids) {
            EmailMeta meta = metaMap.get(id);
            if (meta == null) { log.warn("No metadata for email {}", id); continue; }
            try {
                emailService.flag(userId, meta.accountId(), id);
                flagged++;
            } catch (Exception e) {
                log.error("Failed to flag {}: {}", id, e.getMessage());
            }
        }

        log.info("Flagged {} emails", flagged);
        return Map.of();
    }
}