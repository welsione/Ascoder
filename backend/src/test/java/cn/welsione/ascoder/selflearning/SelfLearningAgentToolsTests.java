package cn.welsione.ascoder.selflearning;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 Self Learning Agent 工具只暴露可安全召回的项目经验。
 */
class SelfLearningAgentToolsTests {

    private final LearningKnowledgeItemJpaRepository knowledgeRepository = mock(LearningKnowledgeItemJpaRepository.class);
    private final LearningInsightJpaRepository insightRepository = mock(LearningInsightJpaRepository.class);
    private final LearningRawEventJpaRepository rawEventRepository = mock(LearningRawEventJpaRepository.class);
    private final SelfLearningAgentTools tools = new SelfLearningAgentTools(
            7L, knowledgeRepository, insightRepository, rawEventRepository
    );

    @Test
    void searchKnowledge_returnsOnlyUsableKnowledge() {
        LearningKnowledgeItem verified = knowledge("支付术语映射", "业务支付 = PaymentService.pay", LearningKnowledgeStatus.VERIFIED);
        LearningKnowledgeItem stale = knowledge("旧支付逻辑", "废弃内容", LearningKnowledgeStatus.STALE);
        when(knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L))
                .thenReturn(List.of(verified, stale));

        ToolResultBlock result = tools.searchKnowledge("支付", 10).block();

        assertThat(extractText(result))
                .contains("支付术语映射")
                .contains("PaymentService.pay")
                .doesNotContain("旧支付逻辑")
                .doesNotContain("废弃内容");
    }

    @Test
    void pendingInsights_returnsOnlyPendingReviewItems() {
        LearningInsight pending = insight("接口命名经验", LearningInsightStatus.PENDING_REVIEW);
        LearningInsight approved = insight("已审核经验", LearningInsightStatus.APPROVED);
        when(insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L))
                .thenReturn(List.of(pending, approved));

        ToolResultBlock result = tools.pendingInsights(10).block();

        assertThat(extractText(result))
                .contains("接口命名经验")
                .doesNotContain("已审核经验");
    }

    @Test
    void recentRawEvents_canFilterByEventType() {
        LearningRawEvent question = rawEvent(LearningRawEventType.USER_QUESTION, "用户问了支付入口");
        LearningRawEvent answer = rawEvent(LearningRawEventType.ASSISTANT_ANSWER, "回答了旧内容");
        when(rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(question, answer));

        ToolResultBlock result = tools.recentRawEvents("USER_QUESTION", 10).block();

        assertThat(extractText(result))
                .contains("用户问了支付入口")
                .doesNotContain("回答了旧内容");
    }

    private LearningKnowledgeItem knowledge(String title, String content, LearningKnowledgeStatus status) {
        LearningKnowledgeItem item = new LearningKnowledgeItem();
        item.setTitle(title);
        item.setContent(content);
        item.setStatus(status);
        item.setType(LearningKnowledgeType.GLOSSARY);
        item.setConfidence(0.91);
        return item;
    }

    private LearningInsight insight(String title, LearningInsightStatus status) {
        LearningInsight item = new LearningInsight();
        item.setTitle(title);
        item.setConclusion(title + "结论");
        item.setStatus(status);
        item.setType(LearningKnowledgeType.BUSINESS_CONTEXT);
        item.setConfidence(0.7);
        return item;
    }

    private LearningRawEvent rawEvent(LearningRawEventType type, String summary) {
        LearningRawEvent item = new LearningRawEvent();
        item.setEventType(type);
        item.setSummary(summary);
        item.setAgentId("self-learning-agent");
        return item;
    }

    private String extractText(ToolResultBlock block) {
        if (block == null || block.getOutput() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ContentBlock output : block.getOutput()) {
            if (output instanceof TextBlock textBlock && textBlock.getText() != null) {
                builder.append(textBlock.getText());
            }
        }
        return builder.toString();
    }
}
