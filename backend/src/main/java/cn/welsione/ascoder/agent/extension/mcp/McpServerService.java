package cn.welsione.ascoder.agent.extension.mcp;

import cn.welsione.ascoder.common.TextUtil;
import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MCP 服务器服务，处理 MCP 服务器配置的 CRUD 和校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private final McpServerJpaRepository repository;

    @Transactional(readOnly = true)
    public List<McpServerConfig> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public McpServerConfig create(CreateMcpServerRequest request) {
        log.info("创建 MCP 服务器，name={}，transport={}", request.getName(), request.getTransport());
        repository.findByName(request.getName().trim()).ifPresent(existing -> {
            throw new DuplicateException("MCP Server 名称已存在");
        });

        validateTransport(request);

        McpServerConfig server = McpServerMapper.INSTANCE.toEntity(request);
        return repository.save(server);
    }

    @Transactional
    public McpServerConfig updateEnabled(Long id, UpdateMcpServerEnabledRequest request) {
        McpServerConfig server = getEntity(id);
        server.setEnabled(request.isEnabled());
        log.info("更新 MCP 服务器启用状态，id={}，enabled={}", id, request.isEnabled());
        return repository.save(server);
    }

    @Transactional(readOnly = true)
    public McpServerConfig getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MCP Server", id));
    }

    private void validateTransport(CreateMcpServerRequest request) {
        if (request.getTransport() == McpTransport.STDIO && TextUtil.isBlank(request.getCommand())) {
            throw new ValidationException("STDIO MCP Server 必须配置 command");
        }
        if ((request.getTransport() == McpTransport.SSE || request.getTransport() == McpTransport.HTTP)
                && TextUtil.isBlank(request.getEndpointUrl())) {
            throw new ValidationException("HTTP/SSE MCP Server 必须配置 endpointUrl");
        }
    }
}
