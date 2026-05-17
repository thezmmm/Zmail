package com.zmail.agent.digest.node;

import com.zmail.agent.digest.DigestAgentState;
import com.zmail.agent.model.EmailRef;
import com.zmail.email.EmailMeta;
import com.zmail.email.EmailMessage;
import com.zmail.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class FetchSelectedNode implements NodeAction<DigestAgentState> {

    private final EmailService emailService;

    @Override
    public Map<String, Object> apply(DigestAgentState state) throws Exception {
        UUID userId = state.userId();
        List<EmailRef> refs = state.emailRefs();

        List<String> emailIds = new ArrayList<>(refs.size());
        Map<String, EmailMeta> metaMap = new HashMap<>(refs.size());

        for (EmailRef ref : refs) {
            try {
                EmailMessage msg = emailService.fetchById(userId, ref.accountId(), ref.providerId());
                emailIds.add(msg.providerId());
                metaMap.put(msg.providerId(), new EmailMeta(
                        msg.providerId(), msg.accountId(),
                        msg.subject(), msg.sender(), msg.receivedAt()));
            } catch (Exception e) {
                log.warn("Could not fetch email {}: {}", ref.providerId(), e.getMessage());
            }
        }

        log.info("Fetched {}/{} selected emails for user {}", emailIds.size(), refs.size(), userId);
        return Map.of(
                DigestAgentState.EMAIL_IDS,      emailIds,
                DigestAgentState.EMAIL_META_MAP, metaMap
        );
    }
}