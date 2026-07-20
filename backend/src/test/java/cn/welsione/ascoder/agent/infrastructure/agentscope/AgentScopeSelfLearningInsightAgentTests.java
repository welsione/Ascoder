package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.selflearning.LearningKnowledgeType;
import cn.welsione.ascoder.selflearning.SelfLearningInsightDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Self Learning Agent 适配层测试，确保 LLM 文本 JSON 也能进入审核洞察流程。
 */
class AgentScopeSelfLearningInsightAgentTests {

    private final AgentScopeSelfLearningInsightAgent agent =
            new AgentScopeSelfLearningInsightAgent(null, null, new ObjectMapper(), null);

    @Test
    void parseDraftFromTextAcceptsMarkdownJsonFence() {
        String text = """
                ```json
                {
                  "type": "GLOSSARY",
                  "title": "个人执业章签署失败审核",
                  "summary": "用户确认 stampers 为空会触发 NO_PROVIDER_PARAM。",
                  "conclusion": "个人执业章签署失败时，需要核对 stampers 入参是否为空。",
                  "businessContext": "合同静默签署排查。",
                  "glossaryMappingsJson": "[{\\"term\\":\\"stampers\\",\\"meaning\\":\\"签署人印章参数\\"}]",
                  "codeSymbolsJson": "[\\"SignContractService\\"]",
                  "warnings": "需要核对当前代码和 Git 证据。",
                  "applicableScope": "合同签署模块。",
                  "tags": "llm-agent,contract-sign",
                  "confidence": 0.61
                }
                ```
                """;

        Optional<SelfLearningInsightDraft> draft = agent.parseDraftFromText(text);

        assertThat(draft).isPresent();
        assertThat(draft.get().getType()).isEqualTo(LearningKnowledgeType.GLOSSARY);
        assertThat(draft.get().getTitle()).contains("个人执业章");
        assertThat(draft.get().getCodeSymbolsJson()).contains("SignContractService");
        assertThat(draft.get().getConfidence()).isEqualTo(0.61);
    }

    @Test
    void extractJsonObjectIgnoresTextAroundJson() {
        String json = agent.extractJsonObject("分析结果如下：{\"title\":\"审核标题\"}\n请审核。");

        assertThat(json).isEqualTo("{\"title\":\"审核标题\"}");
    }

    @Test
    void parseDraftFromTextReturnsEmptyForInvalidJson() {
        Optional<SelfLearningInsightDraft> draft = agent.parseDraftFromText("{\"title\":\"缺少结束\"");

        assertThat(draft).isEmpty();
    }
}
