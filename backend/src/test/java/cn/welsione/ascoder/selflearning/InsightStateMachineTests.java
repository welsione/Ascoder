package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.InvalidStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 InsightStateMachine 对状态转换的守卫行为。
 */
class InsightStateMachineTests {

    private InsightStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new InsightStateMachineImpl();
    }

    @Test
    void transitionFromPendingReviewToApproved() {
        LearningInsight insight = pendingInsight();

        stateMachine.transition(insight, LearningInsightStatus.APPROVED, "看起来不错");

        assertThat(insight.getStatus()).isEqualTo(LearningInsightStatus.APPROVED);
        assertThat(insight.getReviewComment()).isEqualTo("看起来不错");
        assertThat(insight.getReviewedAt()).isNotNull();
    }

    @Test
    void transitionFromPendingReviewToRejected() {
        LearningInsight insight = pendingInsight();

        stateMachine.transition(insight, LearningInsightStatus.REJECTED, "代码已过时");

        assertThat(insight.getStatus()).isEqualTo(LearningInsightStatus.REJECTED);
        assertThat(insight.getReviewComment()).isEqualTo("代码已过时");
    }

    @Test
    void transitionFromApprovedToMerged() {
        LearningInsight insight = insightWithStatus(LearningInsightStatus.APPROVED);

        stateMachine.transition(insight, LearningInsightStatus.MERGED, "已归纳为知识");

        assertThat(insight.getStatus()).isEqualTo(LearningInsightStatus.MERGED);
    }

    @Test
    void rejectFromApprovedIsNotAllowed() {
        LearningInsight insight = insightWithStatus(LearningInsightStatus.APPROVED);

        assertThatThrownBy(() ->
                stateMachine.transition(insight, LearningInsightStatus.REJECTED, "反悔了")
        ).isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("APPROVED")
                .hasMessageContaining("REJECTED");
    }

    @Test
    void approveFromRejectedIsNotAllowed() {
        LearningInsight insight = insightWithStatus(LearningInsightStatus.REJECTED);

        assertThatThrownBy(() ->
                stateMachine.transition(insight, LearningInsightStatus.APPROVED, "改主意了")
        ).isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("REJECTED")
                .hasMessageContaining("APPROVED");
    }

    @Test
    void approveFromMergedIsNotAllowed() {
        LearningInsight insight = insightWithStatus(LearningInsightStatus.MERGED);

        assertThatThrownBy(() ->
                stateMachine.transition(insight, LearningInsightStatus.APPROVED, "重复审核")
        ).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void transitionToSameStateIsNotAllowed() {
        LearningInsight insight = pendingInsight();

        assertThatThrownBy(() ->
                stateMachine.transition(insight, LearningInsightStatus.PENDING_REVIEW, null)
        ).isInstanceOf(InvalidStateException.class);
    }

    private LearningInsight pendingInsight() {
        LearningInsight insight = new LearningInsight();
        insight.setId(1L);
        insight.setStatus(LearningInsightStatus.PENDING_REVIEW);
        insight.setTitle("测试洞察");
        insight.setConclusion("结论");
        insight.setCreatedAt(new Date());
        insight.setUpdatedAt(new Date());
        return insight;
    }

    private LearningInsight insightWithStatus(LearningInsightStatus status) {
        LearningInsight insight = pendingInsight();
        insight.setStatus(status);
        return insight;
    }
}
