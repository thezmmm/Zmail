package com.zmail.agent.node;

import com.zmail.agent.EmailAgentState;
import com.zmail.email.EmailMeta;
import com.zmail.email.EmailMessage;
import com.zmail.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailFetchNode implements NodeAction<EmailAgentState> {

    private final EmailService emailService;

    @Override
    public Map<String, Object> apply(EmailAgentState state) throws Exception {
        UUID userId = state.userId();
        int maxResults = state.maxResults();

        // Fetch full messages once; extract IDs + lightweight meta, discard bodies from state.
        List<EmailMessage> messages = emailService.fetchUnread(userId, maxResults);

        List<String> emailIds = new ArrayList<>(messages.size());
        Map<String, EmailMeta> metaMap = new HashMap<>(messages.size());

        for (EmailMessage msg : messages) {
            emailIds.add(msg.providerId());
            metaMap.put(msg.providerId(), new EmailMeta(
                    msg.providerId(),
                    msg.accountId(),
                    msg.subject(),
                    msg.sender(),
                    msg.receivedAt()
            ));
        }

        log.info("Fetched {} emails for user {} (bodies not stored in state)", emailIds.size(), userId);
        return Map.of(
                EmailAgentState.EMAIL_IDS,      emailIds,
                EmailAgentState.EMAIL_META_MAP, metaMap
        );
    }
}