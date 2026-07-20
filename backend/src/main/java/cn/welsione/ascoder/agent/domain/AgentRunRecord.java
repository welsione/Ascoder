package cn.welsione.ascoder.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent 单次运行记录实体，持久化在 agentRunRecords 表。
 *
 * <p>按 Agent 维度记录每次运行的输入摘要、输出摘要、工具调用次数、迭代次数、耗时与最终状态，
 * 服务于前端管理面板的"使用记录"和"响应记录"展示。与 {@code AgentEvent}（问答级细粒度事件流）
 * 解耦，通过 questionId + attemptNo 关联，不重复存储事件负载。</p>
 *
 * <p>{@code agentConfigId} 仅作为外键值存储，不建 JPA {@code @ManyToOne} 关联，避免跨聚合对象图
 * 与 lazy loading 耦合（符合 CLAUDE.md 跨聚合约束）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agentRunRecords")
public class AgentRunRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String agentId;

    @Column(nullable = false)
    private Long agentConfigId;

    private Long questionId;

    private Long conversationId;

    @Column(nullable = false)
    private int attemptNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status;

    @Column(columnDefinition = "text")
    private String inputSummary;

    @Column(columnDefinition = "text")
    private String outputSummary;

    @Column(nullable = false)
    private int toolCallCount = 0;

    @Column(nullable = false)
    private int iterCount = 0;

    @Column(columnDefinition = "mediumtext")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
