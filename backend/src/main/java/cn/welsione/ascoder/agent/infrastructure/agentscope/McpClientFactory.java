package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.extension.mcp.McpServerConfig;
import cn.welsione.ascoder.agent.extension.mcp.McpTransport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端构建工厂，封装 MCP 服务器连接创建与 JSON 参数解析，
 * 供 Orchestrator 工具装配时复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class McpClientFactory {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * 按 MCP 服务器配置创建并初始化客户端，阻塞至连接就绪。
     */
    McpClientWrapper createClient(McpServerConfig server) {
        McpClientBuilder builder = McpClientBuilder.create(server.getName())
                .timeout(Duration.ofSeconds(server.getTimeoutSeconds()))
                .initializationTimeout(Duration.ofSeconds(server.getTimeoutSeconds()));

        if (server.getTransport() == McpTransport.STDIO) {
            builder.stdioTransport(
                    server.getCommand(),
                    parseStringList(server.getArgumentsJson()),
                    Map.of()
            );
        } else if (server.getTransport() == McpTransport.SSE) {
            builder.sseTransport(server.getEndpointUrl());
        } else {
            builder.streamableHttpTransport(server.getEndpointUrl());
        }

        Map<String, String> headers = parseStringMap(server.getHeadersJson());
        Map<String, String> queryParams = parseStringMap(server.getQueryParamsJson());
        if (!headers.isEmpty()) {
            builder.headers(headers);
        }
        if (!queryParams.isEmpty()) {
            builder.queryParams(queryParams);
        }

        return builder.buildAsync().block(Duration.ofSeconds(server.getTimeoutSeconds() + 5L));
    }

    List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 数组解析失败：" + ex.getMessage(), ex);
        }
    }

    Map<String, String> parseStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 对象解析失败：" + ex.getMessage(), ex);
        }
    }
}
