package cn.welsione.ascoder.agent.extension.mcp;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link McpServerService} 的 CRUD 操作与校验逻辑。
 */
@ExtendWith(MockitoExtension.class)
class McpServerServiceTests {

    @Mock
    private McpServerJpaRepository repository;

    private McpServerService service;

    @BeforeEach
    void setUp() {
        service = new McpServerService(repository);
    }

    @Test
    void create_stdioWithoutCommand_throwsValidation() {
        CreateMcpServerRequest request = new CreateMcpServerRequest(
                "test-server", null, McpTransport.STDIO, null, null, null, null, null, null, null, 30, true
        );
        when(repository.findByName("test-server")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_sseWithoutEndpointUrl_throwsValidation() {
        CreateMcpServerRequest request = new CreateMcpServerRequest(
                "test-server", null, McpTransport.SSE, null, null, null, null, null, null, null, 30, true
        );
        when(repository.findByName("test-server")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_httpWithoutEndpointUrl_throwsValidation() {
        CreateMcpServerRequest request = new CreateMcpServerRequest(
                "test-server", null, McpTransport.HTTP, null, null, null, null, null, null, null, 30, true
        );
        when(repository.findByName("test-server")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_duplicateName_throwsDuplicate() {
        when(repository.findByName("existing")).thenReturn(Optional.of(new McpServerConfig()));

        CreateMcpServerRequest request = new CreateMcpServerRequest(
                "existing", null, McpTransport.STDIO, "cmd", null, null, null, null, null, null, 30, true
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateException.class);
    }

    @Test
    void create_stdioWithCommand_succeeds() {
        when(repository.findByName("my-server")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateMcpServerRequest request = new CreateMcpServerRequest(
                "  my-server  ", "desc", McpTransport.STDIO, " npx cmd ", null, null, null, null, null, null, 0, true
        );

        McpServerConfig result = service.create(request);

        assertThat(result.getName()).isEqualTo("my-server");
        assertThat(result.getDescription()).isEqualTo("desc");
        assertThat(result.getTransport()).isEqualTo(McpTransport.STDIO);
        assertThat(result.getCommand()).isEqualTo("npx cmd");
        assertThat(result.getTimeoutSeconds()).isEqualTo(30);
        verify(repository).save(any());
    }

    @Test
    void create_sseWithEndpoint_succeeds() {
        when(repository.findByName("sse-server")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateMcpServerRequest request = new CreateMcpServerRequest(
                "sse-server", null, McpTransport.SSE, null, null, "  http://localhost:3000/sse  ", null, null, null, null, 60, false
        );

        McpServerConfig result = service.create(request);

        assertThat(result.getEndpointUrl()).isEqualTo("http://localhost:3000/sse");
        assertThat(result.getTimeoutSeconds()).isEqualTo(60);
        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void create_blankFields_becomeNull() {
        when(repository.findByName("server")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateMcpServerRequest request = new CreateMcpServerRequest(
                "server", "   ", McpTransport.STDIO, "cmd", "  ", null, "  ", null, "  ", null, 30, true
        );

        McpServerConfig result = service.create(request);

        assertThat(result.getDescription()).isNull();
        assertThat(result.getArgumentsJson()).isNull();
        assertThat(result.getHeadersJson()).isNull();
        assertThat(result.getDisabledToolsJson()).isNull();
    }

    @Test
    void getEntity_notFound_throwsResourceNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEntity(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateEnabled_togglesValue() {
        McpServerConfig config = new McpServerConfig();
        config.setId(1L);
        config.setEnabled(false);
        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        McpServerConfig result = service.updateEnabled(1L, new UpdateMcpServerEnabledRequest(true));

        assertThat(result.isEnabled()).isTrue();
    }
}
