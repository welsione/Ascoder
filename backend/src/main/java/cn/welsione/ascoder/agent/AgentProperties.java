package cn.welsione.ascoder.agent;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 行为调参默认值，对应 application.yml 中 {@code ascoder.agent.*} 节点。
 *
 * <p>运行时实际值由 {@code RuntimeSettingsService} 读取；本类仅作为白名单写入门控与默认值兜底。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ascoder.agent")
public class AgentProperties {

    private int timeoutSeconds = 240;
    private int toolTimeoutSeconds = 300;
    private int maxIters = 12;
    private int codeResearcherMaxIters = 100;
    private int impactAnalyzerMaxIters = 8;
    private int roleSpecialistMaxIters = 8;
    private int modelMaxAttempts = 2;
    private int toolMaxAttempts = 1;
    private boolean planningEnabled = true;
    private int planMaxSubtasks = 10;

    private int sseTimeoutSeconds = 600;
    private int heartbeatIntervalSeconds = 30;
    private int streamCoreThreads = 2;
    private int streamMaxThreads = 16;
    private int streamQueueCapacity = 64;

    private boolean queryPlannerEnabled = false;
    private double queryPlannerConfidenceThreshold = 0.65;
    private double queryPlannerAmbiguousThreshold = 0.82;
}