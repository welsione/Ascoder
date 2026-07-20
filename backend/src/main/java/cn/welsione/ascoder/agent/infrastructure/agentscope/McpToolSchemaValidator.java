package cn.welsione.ascoder.agent.infrastructure.agentscope;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具 inputSchema 安全校验器，在工具注册前过滤掉声明不安全的工具。
 * <p>
 * 防护范围：
 * <ul>
 *   <li>inputSchema.type 必须为 {@code object}（MCP 工具输入标准）</li>
 *   <li>additionalProperties 必须为 {@code false}，禁止 Server 接受任意额外参数</li>
 *   <li>properties 中每个属性必须声明 type，防止无类型参数被滥用</li>
 * </ul>
 * 不符合上述任一条件的工具视为不安全，返回其名称供调用方加入 disableTools。
 */
@Slf4j
public final class McpToolSchemaValidator {

    private McpToolSchemaValidator() {
    }

    /**
     * 返回工具列表中 schema 不安全的工具名称。
     *
     * @param tools MCP Server 声明的工具列表，可为 null
     * @return 不安全工具名称列表，空列表表示全部通过
     */
    public static List<String> filterUnsafeTools(List<McpSchema.Tool> tools) {
        List<String> unsafe = new ArrayList<>();
        if (tools == null || tools.isEmpty()) {
            return unsafe;
        }
        for (McpSchema.Tool tool : tools) {
            if (tool == null) {
                continue;
            }
            String reason = checkTool(tool);
            if (reason != null) {
                log.warn("MCP 工具 schema 校验失败，已禁用：name={}, reason={}", tool.name(), reason);
                unsafe.add(tool.name());
            }
        }
        return unsafe;
    }

    /**
     * 校验单个工具，返回不安全原因；返回 null 表示通过。
     */
    private static String checkTool(McpSchema.Tool tool) {
        McpSchema.JsonSchema schema = tool.inputSchema();
        if (schema == null) {
            return "missing inputSchema";
        }
        if (!"object".equals(schema.type())) {
            return "inputSchema.type must be object, got: " + schema.type();
        }
        if (!Boolean.FALSE.equals(schema.additionalProperties())) {
            return "additionalProperties must be false";
        }
        String propertyReason = checkProperties(schema.properties());
        if (propertyReason != null) {
            return propertyReason;
        }
        return null;
    }

    /**
     * 校验每个属性必须声明 type。
     */
    private static String checkProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> prop)) {
                return "property '" + entry.getKey() + "' is not an object";
            }
            if (prop.get("type") == null) {
                return "property '" + entry.getKey() + "' missing type";
            }
        }
        return null;
    }
}
