package cn.welsione.ascoder.agent.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听 AgentConfigChangedEvent，在事务提交后失效 AgentConfigCache。
 *
 * <p>不标注 {@code @Transactional}（Spring 不允许 @TransactionalEventListener 与 @Transactional 同用，
 * 见 CLAUDE.md）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConfigCacheListener {

    private final AgentConfigCache cache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfigChanged(AgentConfigChangedEvent event) {
        log.info("Agent 配置变更事件，失效缓存，agentId={}，action={}", event.getAgentId(), event.getAction());
        cache.invalidate();
    }
}
