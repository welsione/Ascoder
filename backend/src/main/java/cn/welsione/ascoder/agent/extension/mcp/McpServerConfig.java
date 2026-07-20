package cn.welsione.ascoder.agent.extension.mcp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * MCP 服务器配置实体，持久化存储 MCP 服务器连接信息。
 */
@Entity
@Table(name = "mcp_servers")
@Getter
@Setter
@NoArgsConstructor
public class McpServerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private McpTransport transport;

    @Column(columnDefinition = "text")
    private String command;

    @Column(columnDefinition = "text")
    private String argumentsJson;

    @Column(columnDefinition = "text")
    private String endpointUrl;

    @Column(columnDefinition = "text")
    private String headersJson;

    @Column(columnDefinition = "text")
    private String queryParamsJson;

    @Column(columnDefinition = "text")
    private String enabledToolsJson;

    @Column(columnDefinition = "text")
    private String disabledToolsJson;

    @Column(nullable = false)
    private int timeoutSeconds = 30;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(columnDefinition = "text")
    private String lastError;

    @Column(nullable = false)
    private Date createdAt = new Date();

    @Column(nullable = false)
    private Date updatedAt = new Date();

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
        touch();
    }

    public void touch() {
        this.updatedAt = new Date();
    }
}
