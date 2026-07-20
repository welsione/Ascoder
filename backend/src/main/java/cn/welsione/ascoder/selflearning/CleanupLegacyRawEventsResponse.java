package cn.welsione.ascoder.selflearning;

import lombok.Value;

/**
 * 清理旧粒度自学习原始记录后的结果。
 */
@Value
public class CleanupLegacyRawEventsResponse {
    int deletedRawEventCount;
    int deletedInsightCount;
    int staleKnowledgeItemCount;
    String message;
}
