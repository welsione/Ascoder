package cn.welsione.ascoder.agent.extension.mcp;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建 MCP 服务器的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMcpServerRequest {
    @NotBlank @Size(max = 120)
    String name;
    String description;
    @NotNull
    McpTransport transport;
    String command;
    String argumentsJson;
    String endpointUrl;
    String headersJson;
    String queryParamsJson;
    String enabledToolsJson;
    String disabledToolsJson;
    @Min(1)
    int timeoutSeconds;
    boolean enabled;
}
