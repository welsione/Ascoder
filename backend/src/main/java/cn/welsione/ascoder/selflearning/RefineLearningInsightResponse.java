package cn.welsione.ascoder.selflearning;

import lombok.Value;

/**
 * 候选洞察微调响应，返回可应用到编辑表单的结构化建议。
 */
@Value
public class RefineLearningInsightResponse {
    Long insightId;
    SaveLearningInsightRequest suggestion;
    String assistantMessage;
}
