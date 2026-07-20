package cn.welsione.ascoder.agent.infrastructure.agentscope;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link McpToolSchemaValidator} 单元测试，覆盖安全 schema 通过、危险 schema 被过滤的场景。
 */
class McpToolSchemaValidatorTest {

    @Test
    void filtersEmptyOrNull() {
        assertThat(McpToolSchemaValidator.filterUnsafeTools(null)).isEmpty();
        assertThat(McpToolSchemaValidator.filterUnsafeTools(List.of())).isEmpty();
    }

    @Test
    void passesSafeSchema() {
        // type=object, additionalProperties=false, 每个属性有 type
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("type", "string"));
        properties.put("lines", Map.of("type", "integer"));
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", properties, List.of("path"), false, null, null);
        McpSchema.Tool tool = new McpSchema.Tool(
                "safe_tool", null, "desc", schema, null, null, null);

        List<String> unsafe = McpToolSchemaValidator.filterUnsafeTools(List.of(tool));
        assertThat(unsafe).isEmpty();
    }

    @Test
    void rejectsMissingInputSchema() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "no_schema", null, "desc", null, null, null, null);
        List<String> unsafe = McpToolSchemaValidator.filterUnsafeTools(List.of(tool));
        assertThat(unsafe).containsExactly("no_schema");
    }

    @Test
    void rejectsNonObjectType() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "string", null, null, false, null, null);
        McpSchema.Tool tool = new McpSchema.Tool(
                "bad_type", null, "desc", schema, null, null, null);
        List<String> unsafe = McpToolSchemaValidator.filterUnsafeTools(List.of(tool));
        assertThat(unsafe).containsExactly("bad_type");
    }

    @Test
    void rejectsAdditionalPropertiesTrue() {
        // additionalProperties=true 允许任意额外参数，必须拒绝
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of("path", Map.of("type", "string")), null, true, null, null);
        McpSchema.Tool tool = new McpSchema.Tool(
                "open_props", null, "desc", schema, null, null, null);
        List<String> unsafe = McpToolSchemaValidator.filterUnsafeTools(List.of(tool));
        assertThat(unsafe).containsExactly("open_props");
    }

    @Test
    void rejectsAdditionalPropertiesNull() {
        // additionalProperties=null（未显式声明 false）也拒绝，强制要求显式 false
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of("path", Map.of("type", "string")), null, null, null, null);
        McpSchema.Tool tool = new McpSchema.Tool(
                "null_props", null, "desc", schema, null, null, null);
        List<String> unsafe = McpToolSchemaValidator.filterUnsafeTools(List.of(tool));
        assertThat(unsafe).containsExactly("null_props");
    }

    @Test
    void rejectsPropertyWithoutType() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", Map.of("description", "no type"));
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", properties, null, false, null, null);
        McpSchema.Tool tool = new McpSchema.Tool(
                "prop_no_type", null, "desc", schema, null, null, null);
        List<String> unsafe = McpToolSchemaValidator.filterUnsafeTools(List.of(tool));
        assertThat(unsafe).containsExactly("prop_no_type");
    }

    @Test
    void filtersMixedTools() {
        // 一个安全、一个危险
        Map<String, Object> safeProps = Map.of("path", Map.of("type", "string"));
        McpSchema.JsonSchema safeSchema = new McpSchema.JsonSchema(
                "object", safeProps, null, false, null, null);
        McpSchema.Tool safe = new McpSchema.Tool(
                "safe", null, "desc", safeSchema, null, null, null);

        McpSchema.JsonSchema unsafeSchema = new McpSchema.JsonSchema(
                "object", null, null, true, null, null);
        McpSchema.Tool unsafe = new McpSchema.Tool(
                "unsafe", null, "desc", unsafeSchema, null, null, null);

        List<String> result = McpToolSchemaValidator.filterUnsafeTools(List.of(safe, unsafe));
        assertThat(result).containsExactly("unsafe");
    }
}
