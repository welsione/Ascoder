package cn.welsione.ascoder.agent.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * LLM 供应商配置实体，存储供应商连接参数和运行参数。
 *
 * <p>供应商级配置（apiKey/baseUrl/modelId/maxTokens/timeoutSeconds）从本表读取，
 * AgentConfig 可通过 llmProviderId 关联并在 modelId/maxTokens/timeoutSeconds 上覆盖。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "llmProvider")
public class LlmProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LlmProviderType providerType;

    @Column(nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String modelId;

    private Integer maxTokens;

    private Long timeoutSeconds;

    @Column(nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean builtin;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
