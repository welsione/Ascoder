package cn.welsione.ascoder.agent.extension.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Agent 技能配置实体，持久化存储可注入 Agent 的技能内容。
 */
@Entity
@Table(name = "agent_skills")
@Getter
@Setter
@NoArgsConstructor
public class AgentSkillConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, columnDefinition = "longtext")
    private String skillContent;

    @Column(nullable = false, length = 120)
    private String source = "manual";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    public void touch() {
        this.updatedAt = new Date();
    }
}
