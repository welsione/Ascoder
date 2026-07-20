package cn.welsione.ascoder.agent.extension.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新 MCP 服务器启用状态的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMcpServerEnabledRequest {
    boolean enabled;
}
