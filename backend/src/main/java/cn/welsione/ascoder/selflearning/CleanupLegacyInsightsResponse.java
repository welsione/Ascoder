package cn.welsione.ascoder.selflearning;

import lombok.Value;

/**
 * 清理旧版 Self Learning Agent 候选洞察后的结果。
 */
@Value
public class CleanupLegacyInsightsResponse {
    int deletedInsightCount;
    int staleKnowledgeItemCount;
    String message;
}
