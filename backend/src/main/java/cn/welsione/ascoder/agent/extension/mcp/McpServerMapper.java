package cn.welsione.ascoder.agent.extension.mcp;

import cn.welsione.ascoder.common.TextUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * MCP Server 模块的 MapStruct 映射器，处理 Request → Entity 转换。
 */
@Mapper
public interface McpServerMapper {

    McpServerMapper INSTANCE = Mappers.getMapper(McpServerMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", expression = "java(request.getName().trim())")
    @Mapping(target = "description", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getDescription()))")
    @Mapping(target = "command", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getCommand()))")
    @Mapping(target = "argumentsJson", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getArgumentsJson()))")
    @Mapping(target = "endpointUrl", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getEndpointUrl()))")
    @Mapping(target = "headersJson", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getHeadersJson()))")
    @Mapping(target = "queryParamsJson", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getQueryParamsJson()))")
    @Mapping(target = "enabledToolsJson", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getEnabledToolsJson()))")
    @Mapping(target = "disabledToolsJson", expression = "java(cn.welsione.ascoder.common.TextUtil.trimToNull(request.getDisabledToolsJson()))")
    @Mapping(target = "timeoutSeconds", expression = "java(request.getTimeoutSeconds() <= 0 ? 30 : request.getTimeoutSeconds())")
    McpServerConfig toEntity(CreateMcpServerRequest request);
}
