package cn.welsione.ascoder.selflearning;

import lombok.Value;

/**
 * 自学习总览响应，聚合项目空间自学习设置和知识数量。
 */
@Value
public class SelfLearningSummaryResponse {
    SelfLearningSettingsResponse settings;
    long rawEventCount;
    long insightCount;
    long pendingInsightCount;
    long knowledgeItemCount;
    long experienceCount;
    long candidateCount;
    long correctionCount;
    long termCount;
}
