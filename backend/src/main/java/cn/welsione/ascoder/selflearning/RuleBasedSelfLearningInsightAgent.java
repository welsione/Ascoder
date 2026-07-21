package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Self Learning Agent 的规则兜底实现，仅在 {@code ascoder.llm-provider=database} 时装配，
 * 提供不依赖 LLM 的保底草稿（当前返回空，表示不生成洞察）。
 *
 * <p>不使用 {@code @ConditionalOnMissingBean}：该注解在 component scan 阶段评估时 bean registry
 * 尚未完成注册，与同为 {@code @Component} 的 AgentScope 实现存在竞态，会导致 database 模式下
 * 两个实现都不装配、{@code SelfLearningInsightAgent} bean 缺失。改用 {@code @ConditionalOnProperty}
 * 与 LlmProviderModelFactory 保持一致的互斥装配模式。
 */
@Component
@ConditionalOnProperty(name = "ascoder.llm-provider", havingValue = "database")
public class RuleBasedSelfLearningInsightAgent implements SelfLearningInsightAgent {

    @Override
    public Optional<SelfLearningInsightDraft> summarize(ProjectSpace projectSpace, List<LearningRawEvent> rawEvents) {
        return Optional.empty();
    }
}
