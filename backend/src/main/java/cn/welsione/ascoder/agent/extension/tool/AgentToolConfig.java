package cn.welsione.ascoder.agent.extension.tool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent 工具配置实体，记录内置工具的启停状态和管理元数据。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agentToolConfigs")
public class AgentToolConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String toolKey;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 80)
    private String groupName;

    @Column(nullable = false, length = 40)
    private String riskLevel;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean builtin = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version = 0L;

    public void updateFromDefinition(AgentToolDefinition definition) {
        boolean changed = false;
        if (!definition.getDisplayName().equals(this.displayName)) {
            this.displayName = definition.getDisplayName();
            changed = true;
        }
        if (!definition.getGroupName().equals(this.groupName)) {
            this.groupName = definition.getGroupName();
            changed = true;
        }
        if (!definition.getRiskLevel().equals(this.riskLevel)) {
            this.riskLevel = definition.getRiskLevel();
            changed = true;
        }
        if (!definition.getDescription().equals(this.description)) {
            this.description = definition.getDescription();
            changed = true;
        }
        if (!this.builtin) {
            this.builtin = true;
            changed = true;
        }
        if (changed) {
            touch();
        }
    }

    public void replaceFromDefinition(AgentToolDefinition definition) {
        this.displayName = definition.getDisplayName();
        this.groupName = definition.getGroupName();
        this.riskLevel = definition.getRiskLevel();
        this.description = definition.getDescription();
        this.builtin = true;
        touch();
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
