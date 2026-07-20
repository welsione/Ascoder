package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.ConnectionTestResult;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import cn.welsione.ascoder.agent.extension.config.CreateLlmProviderRequest;
import cn.welsione.ascoder.agent.extension.config.UpdateLlmProviderRequest;
import cn.welsione.ascoder.agent.infrastructure.agentscope.ConnectionTestStrategy;
import cn.welsione.ascoder.agent.persistence.LlmProviderJpaRepository;
import cn.welsione.ascoder.agent.port.AgentConfigPort;
import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.common.security.ApiKeyEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LlmProviderService 核心场景测试：创建、更新、删除、默认切换、引用校验。
 */
@ExtendWith(MockitoExtension.class)
class LlmProviderServiceTests {

    @Mock
    private LlmProviderJpaRepository repository;

    @Spy
    private ApiKeyEncryptor encryptor = new ApiKeyEncryptor("HLfVkzObvOehBaIGLqc+duuuKahnnoyOWS4FH96spE8=");

    @Mock
    private AgentConfigPort agentConfigPort;

    @Mock
    private List<ConnectionTestStrategy> connectionTestStrategies;

    @InjectMocks
    private LlmProviderService service;

    private CreateLlmProviderRequest createRequest() {
        CreateLlmProviderRequest req = new CreateLlmProviderRequest();
        req.setName("test-provider");
        req.setProviderType("ANTHROPIC_COMPATIBLE");
        req.setApiKey("test-key-1234567890");
        req.setBaseUrl("https://api.anthropic.com");
        req.setModelId("claude-3-5-sonnet");
        req.setMaxTokens(4096);
        req.setTimeoutSeconds(60L);
        req.setDefault(false);
        req.setEnabled(true);
        return req;
    }

    private LlmProvider provider(Long id, String name, boolean isDefault, boolean enabled, boolean builtin) {
        LlmProvider p = new LlmProvider();
        p.setId(id);
        p.setName(name);
        p.setProviderType(LlmProviderType.ANTHROPIC_COMPATIBLE);
        p.setApiKey(encryptor.encrypt("test-original-key"));
        p.setBaseUrl("https://api.anthropic.com");
        p.setModelId("claude-3-5-sonnet");
        p.setDefault(isDefault);
        p.setEnabled(enabled);
        p.setBuiltin(builtin);
        p.setSortOrder(0);
        return p;
    }

    @Test
    void createSucceeds() {
        when(repository.existsByName("test-provider")).thenReturn(false);
        when(repository.save(any(LlmProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        LlmProvider saved = service.create(createRequest());

        ArgumentCaptor<LlmProvider> captor = ArgumentCaptor.forClass(LlmProvider.class);
        verify(repository).save(captor.capture());
        assertEquals("test-provider", captor.getValue().getName());
        assertEquals(LlmProviderType.ANTHROPIC_COMPATIBLE, captor.getValue().getProviderType());
        // apiKey should be encrypted, not the raw value
        assertNotEquals("test-key-1234567890", captor.getValue().getApiKey());
        assertFalse(captor.getValue().isDefault());
        assertTrue(captor.getValue().isEnabled());
        assertFalse(captor.getValue().isBuiltin());
    }

    @Test
    void createWithDefaultClearsExistingDefault() {
        when(repository.existsByName("new-default")).thenReturn(false);
        LlmProvider existingDefault = provider(1L, "old-default", true, true, false);
        when(repository.findByIsDefaultTrueAndEnabledTrue()).thenReturn(Optional.of(existingDefault));
        when(repository.save(any(LlmProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateLlmProviderRequest req = createRequest();
        req.setName("new-default");
        req.setDefault(true);
        service.create(req);

        // existing default should be cleared
        verify(repository).save(existingDefault);
        assertFalse(existingDefault.isDefault());
    }

    @Test
    void createDuplicateNameThrows() {
        when(repository.existsByName("test-provider")).thenReturn(true);
        assertThrows(DuplicateException.class, () -> service.create(createRequest()));
    }

    @Test
    void createBlankNameThrows() {
        CreateLlmProviderRequest req = createRequest();
        req.setName("");
        assertThrows(ValidationException.class, () -> service.create(req));
    }

    @Test
    void createInvalidProviderTypeThrows() {
        CreateLlmProviderRequest req = createRequest();
        req.setProviderType("INVALID_TYPE");
        assertThrows(ValidationException.class, () -> service.create(req));
    }

    @Test
    void createBlankApiKeyThrows() {
        CreateLlmProviderRequest req = createRequest();
        req.setApiKey("");
        assertThrows(ValidationException.class, () -> service.create(req));
    }

    @Test
    void createBlankBaseUrlThrows() {
        CreateLlmProviderRequest req = createRequest();
        req.setBaseUrl("");
        assertThrows(ValidationException.class, () -> service.create(req));
    }

    @Test
    void createBlankModelIdThrows() {
        CreateLlmProviderRequest req = createRequest();
        req.setModelId("");
        assertThrows(ValidationException.class, () -> service.create(req));
    }

    @Test
    void updateSucceeds() {
        LlmProvider existing = provider(1L, "test-provider", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByName("renamed")).thenReturn(false);
        when(repository.save(any(LlmProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLlmProviderRequest req = new UpdateLlmProviderRequest();
        req.setName("renamed");
        req.setProviderType("OPENAI_COMPATIBLE");
        req.setBaseUrl("https://api.openai.com");
        req.setModelId("gpt-4");
        req.setMaxTokens(8192);
        req.setTimeoutSeconds(120L);
        req.setDefault(false);
        req.setEnabled(true);

        LlmProvider updated = service.update(1L, req);
        assertEquals("renamed", updated.getName());
        assertEquals(LlmProviderType.OPENAI_COMPATIBLE, updated.getProviderType());
        assertEquals("https://api.openai.com", updated.getBaseUrl());
        assertEquals("gpt-4", updated.getModelId());
        assertEquals(8192, updated.getMaxTokens());
        assertEquals(120L, updated.getTimeoutSeconds());
    }

    @Test
    void updateWithBlankApiKeyPreservesOriginal() {
        LlmProvider existing = provider(1L, "test-provider", false, true, false);
        String originalEncrypted = existing.getApiKey();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(LlmProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLlmProviderRequest req = new UpdateLlmProviderRequest();
        req.setName("test-provider");
        req.setApiKey("");
        req.setDefault(false);
        req.setEnabled(true);

        service.update(1L, req);
        // apiKey should remain unchanged
        assertEquals(originalEncrypted, existing.getApiKey());
    }

    @Test
    void updateWithNewApiKeyEncrypts() {
        LlmProvider existing = provider(1L, "test-provider", false, true, false);
        String originalEncrypted = existing.getApiKey();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(LlmProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLlmProviderRequest req = new UpdateLlmProviderRequest();
        req.setName("test-provider");
        req.setApiKey("test-new-api-key");
        req.setDefault(false);
        req.setEnabled(true);

        service.update(1L, req);
        // apiKey should be changed (encrypted new value)
        assertNotEquals(originalEncrypted, existing.getApiKey());
    }

    @Test
    void updateWithDefaultClearsExistingDefault() {
        LlmProvider existing = provider(1L, "test-provider", false, true, false);
        LlmProvider currentDefault = provider(2L, "current-default", true, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findByIsDefaultTrueAndEnabledTrue()).thenReturn(Optional.of(currentDefault));
        when(repository.save(any(LlmProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLlmProviderRequest req = new UpdateLlmProviderRequest();
        req.setName("test-provider");
        req.setDefault(true);
        req.setEnabled(true);

        service.update(1L, req);
        assertFalse(currentDefault.isDefault());
    }

    @Test
    void updateDuplicateNameThrows() {
        LlmProvider existing = provider(1L, "test-provider", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByName("other-provider")).thenReturn(true);

        UpdateLlmProviderRequest req = new UpdateLlmProviderRequest();
        req.setName("other-provider");
        req.setDefault(false);
        req.setEnabled(true);

        assertThrows(DuplicateException.class, () -> service.update(1L, req));
    }

    @Test
    void deleteBuiltinThrows() {
        LlmProvider builtin = provider(1L, "builtin-provider", false, true, true);
        when(repository.findById(1L)).thenReturn(Optional.of(builtin));

        assertThrows(InvalidStateException.class, () -> service.delete(1L));
    }

    @Test
    void deleteReferencedByAgentThrows() {
        LlmProvider provider = provider(1L, "referenced-provider", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(provider));
        when(agentConfigPort.countByLlmProviderId(1L)).thenReturn(3L);

        InvalidStateException ex = assertThrows(InvalidStateException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("3"));
    }

    @Test
    void deleteNonReferencedSucceeds() {
        LlmProvider provider = provider(1L, "free-provider", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(provider));
        when(agentConfigPort.countByLlmProviderId(1L)).thenReturn(0L);

        service.delete(1L);
        verify(repository).delete(provider);
    }

    @Test
    void setDefaultSucceeds() {
        LlmProvider provider = provider(1L, "new-default", false, true, false);
        LlmProvider currentDefault = provider(2L, "current-default", true, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(provider));
        when(repository.findByIsDefaultTrueAndEnabledTrue()).thenReturn(Optional.of(currentDefault));
        when(repository.save(any(LlmProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        LlmProvider result = service.setDefault(1L);
        assertTrue(result.isDefault());
        assertFalse(currentDefault.isDefault());
    }

    @Test
    void setDefaultDisabledProviderThrows() {
        LlmProvider provider = provider(1L, "disabled-provider", false, false, false);
        when(repository.findById(1L)).thenReturn(Optional.of(provider));

        assertThrows(InvalidStateException.class, () -> service.setDefault(1L));
    }

    @Test
    void updateEnabledDisableDefaultThrows() {
        LlmProvider provider = provider(1L, "default-provider", true, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(provider));

        assertThrows(InvalidStateException.class, () -> service.updateEnabled(1L, false));
    }

    @Test
    void updateEnabledNonDefaultSucceeds() {
        LlmProvider provider = provider(1L, "non-default", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(provider));
        when(repository.save(any(LlmProvider.class))).thenAnswer(inv -> inv.getArgument(0));

        LlmProvider result = service.updateEnabled(1L, false);
        assertFalse(result.isEnabled());
    }

    @Test
    void listMasksApiKeys() {
        LlmProvider p1 = provider(1L, "p1", false, true, false);
        LlmProvider p2 = provider(2L, "p2", false, true, false);
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(p1, p2));

        List<LlmProvider> result = service.list();
        assertEquals(2, result.size());
        // apiKey should be masked (contains ****)
        for (LlmProvider p : result) {
            assertTrue(p.getApiKey().contains("****"));
        }
    }

    @Test
    void getMasksApiKey() {
        LlmProvider provider = provider(1L, "test", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(provider));

        LlmProvider result = service.get(1L);
        assertTrue(result.getApiKey().contains("****"));
    }

    @Test
    void getDefaultReturnsUnmasked() {
        LlmProvider provider = provider(1L, "default", true, true, false);
        when(repository.findByIsDefaultTrueAndEnabledTrue()).thenReturn(Optional.of(provider));

        LlmProvider result = service.getDefault();
        // apiKey should NOT be masked (contains the encrypted value, not ****)
        assertFalse(result.getApiKey().contains("****"));
    }

    @Test
    void getDefaultMissingThrows() {
        when(repository.findByIsDefaultTrueAndEnabledTrue()).thenReturn(Optional.empty());
        assertThrows(InvalidStateException.class, () -> service.getDefault());
    }

    @Test
    void getDecryptedReturnsPlainApiKey() {
        LlmProvider provider = provider(1L, "test", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(provider));

        LlmProvider result = service.getDecrypted(1L);
        assertEquals("test-original-key", result.getApiKey());
    }

    @Test
    void getMissingThrows() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(99L));
    }

    @Test
    void maskApiKeyShortValue() {
        LlmProvider provider = provider(1L, "short", false, true, false);
        provider.setApiKey("abc");
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(provider));

        List<LlmProvider> result = service.list();
        assertEquals("****", result.get(0).getApiKey());
    }

    @Test
    void testConnectionSucceeds() {
        LlmProvider p = provider(1L, "test", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        ConnectionTestStrategy strategy = mock(ConnectionTestStrategy.class);
        when(strategy.supports("ANTHROPIC_COMPATIBLE")).thenReturn(true);
        ConnectionTestResult expectedResult = new ConnectionTestResult(true, "连接成功", 150L);
        when(strategy.test(any(ResolvedModelConfig.class))).thenReturn(expectedResult);
        when(connectionTestStrategies.iterator()).thenReturn(List.of(strategy).iterator());

        ConnectionTestResult result = service.testConnection(1L);

        assertTrue(result.isSuccess());
        assertEquals("连接成功", result.getMessage());
        assertEquals(150L, result.getLatencyMs());
    }

    @Test
    void testConnectionUnsupportedTypeThrows() {
        LlmProvider p = provider(1L, "test", false, true, false);
        when(repository.findById(1L)).thenReturn(Optional.of(p));
        when(connectionTestStrategies.iterator()).thenReturn(List.<ConnectionTestStrategy>of().iterator());

        assertThrows(InvalidStateException.class, () -> service.testConnection(1L));
    }

    @Test
    void testConnectionMissingProviderThrows() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.testConnection(99L));
    }
}
