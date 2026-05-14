package com.zmail.agent.node;

import com.zmail.agent.EmailAgentState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 pendingActions 队列中取出下一个待执行的 action 节点名，
 * 写入 currentAction；条件边根据 currentAction 分流到对应节点。
 * 各 action 节点执行完后回到 dispatch，形成循环直到队列清空。
 */
@Component
@Slf4j
public class DispatchNode implements NodeAction<EmailAgentState> {

    @Override
    public Map<String, Object> apply(EmailAgentState state) throws Exception {
        List<String> pending = new ArrayList<>(state.pendingActions());

        if (pending.isEmpty()) {
            log.debug("Dispatch: queue empty → END");
            return Map.of(EmailAgentState.CURRENT_ACTION, GraphDefinition.END);
        }

        String next = pending.remove(0);
        log.debug("Dispatch: → {} (remaining: {})", next, pending);
        return Map.of(
                EmailAgentState.PENDING_ACTIONS, pending,
                EmailAgentState.CURRENT_ACTION,  next
        );
    }
}