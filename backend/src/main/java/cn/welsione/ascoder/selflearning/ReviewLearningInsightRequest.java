package cn.welsione.ascoder.selflearning;

import lombok.Data;

/**
 * 审核候选洞察请求，记录管理员审核意见。
 */
@Data
public class ReviewLearningInsightRequest {
    String reviewComment;
}
