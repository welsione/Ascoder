package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.extension.config.CreateLlmProviderRequest;
import cn.welsione.ascoder.agent.extension.config.UpdateLlmProviderRequest;
import cn.welsione.ascoder.agent.persistence.LlmProviderJpaRepository;
import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmProviderService 集成测试：验证 LLM 供应商 CRUD、API Key 加密/脱敏/解密、默认切换与引用校验。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。
 * testConnection 方法依赖外部 LLM API，不在本测试覆盖范围。</p>
 */
@Transactional
class LlmProviderServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private LlmProviderService service;

    @Autowired
    private LlmProviderJpaRepository repository;

    @Autowired
    private IntegrationTestDataFactory factory;

    private CreateLlmProviderRequest buildCreateRequest(String name) {
        CreateLlmProviderRequest request = new CreateLlmProviderRequest();
        request.setName(name);
        request.setProviderType("ANTHROPIC_COMPATIBLE");
        request.setApiKey("sk-test-abcdef123456");
        request.setBaseUrl("https://api.example.com");
        request.setModelId("claude-3-sonnet");
        request.setMaxTokens(4096);
        request.setTimeoutSeconds(240L);
        request.setDefault(false);
        request.setEnabled(true);
        return request;
    }

    @Test
    void createEncryptsApiKeyAndListReturnsMasked() {
        LlmProvider created = service.create(buildCreateRequest("test-create-encrypt-" + System.nanoTime()));

        assertNotNull(created.getId());
        // DB 中存储的是加密后的 apiKey，不是明文
        LlmProvider persisted = repository.findById(created.getId()).orElseThrow();
        String encrypted = persisted.getApiKey();
        assertNotEquals("sk-test-abcdef123456", encrypted);
        assertTrue(encrypted.length() > 6);

        // list 返回脱敏后的 apiKey（基于加密值脱敏）
        List<LlmProvider> providers = service.list();
        LlmProvider found = providers.stream()
                .filter(p -> p.getId().equals(created.getId()))
                .findFirst().orElseThrow();
        String expectedMasked = encrypted.substring(0, 2) + "****" + encrypted.substring(encrypted.length() - 4);
        assertEquals(expectedMasked, found.getApiKey());
        assertNotEquals("sk-test-abcdef123456", found.getApiKey());
    }

    @Test
    void getReturnsMaskedApiKey() {
        LlmProvider created = service.create(buildCreateRequest("test-get-masked-" + System.nanoTime()));

        LlmProvider found = service.get(created.getId());
        LlmProvider persisted = repository.findById(created.getId()).orElseThrow();
        String encrypted = persisted.getApiKey();
        String expectedMasked = encrypted.substring(0, 2) + "****" + encrypted.substring(encrypted.length() - 4);
        assertEquals(expectedMasked, found.getApiKey());
    }

    @Test
    void getDecryptedReturnsPlaintextApiKey() {
        LlmProvider created = service.create(buildCreateRequest("test-decrypt-" + System.nanoTime()));

        LlmProvider decrypted = service.getDecrypted(created.getId());
        assertEquals("sk-test-abcdef123456", decrypted.getApiKey());
    }

    @Test
    void getDefaultReturnsDefaultProviderWithEncryptedKey() {
        CreateLlmProviderRequest request = buildCreateRequest("test-default-" + System.nanoTime());
        request.setDefault(true);
        LlmProvider created = service.create(request);

        LlmProvider defaultProvider = service.getDefault();
        assertEquals(created.getName(), defaultProvider.getName());
        assertTrue(defaultProvider.isDefault());
        // getDefault 返回加密后的 apiKey（未脱敏、未解密）
        LlmProvider persisted = repository.findById(created.getId()).orElseThrow();
        assertEquals(persisted.getApiKey(), defaultProvider.getApiKey());
    }

    @Test
    void getDefaultDecryptedReturnsPlaintextApiKey() {
        CreateLlmProviderRequest request = buildCreateRequest("test-default-decrypt-" + System.nanoTime());
        request.setDefault(true);
        service.create(request);

        LlmProvider decrypted = service.getDefaultDecrypted();
        assertEquals("sk-test-abcdef123456", decrypted.getApiKey());
    }

    @Test
    void updatePreservesApiKeyWhenBlank() {
        LlmProvider created = service.create(buildCreateRequest("test-update-keep-key-" + System.nanoTime()));
        String encryptedBefore = repository.findById(created.getId()).orElseThrow().getApiKey();

        UpdateLlmProviderRequest update = new UpdateLlmProviderRequest();
        update.setName(created.getName());
        update.setApiKey(""); // 空值，应保留原加密值
        update.setEnabled(true);
        update.setDefault(false);

        service.update(created.getId(), update);

        String encryptedAfter = repository.findById(created.getId()).orElseThrow().getApiKey();
        assertEquals(encryptedBefore, encryptedAfter);
    }

    @Test
    void updateReplacesApiKeyWhenProvided() {
        LlmProvider created = service.create(buildCreateRequest("test-update-replace-key-" + System.nanoTime()));
        String encryptedBefore = repository.findById(created.getId()).orElseThrow().getApiKey();

        UpdateLlmProviderRequest update = new UpdateLlmProviderRequest();
        update.setName(created.getName());
        update.setApiKey("sk-new-key-987654");
        update.setEnabled(true);
        update.setDefault(false);

        service.update(created.getId(), update);

        String encryptedAfter = repository.findById(created.getId()).orElseThrow().getApiKey();
        assertNotEquals(encryptedBefore, encryptedAfter);

        LlmProvider decrypted = service.getDecrypted(created.getId());
        assertEquals("sk-new-key-987654", decrypted.getApiKey());
    }

    @Test
    void setDefaultIsMutuallyExclusive() {
        CreateLlmProviderRequest reqA = buildCreateRequest("test-set-default-a-" + System.nanoTime());
        reqA.setDefault(true);
        LlmProvider providerA = service.create(reqA);

        CreateLlmProviderRequest reqB = buildCreateRequest("test-set-default-b-" + System.nanoTime());
        reqB.setDefault(false);
        LlmProvider providerB = service.create(reqB);

        service.setDefault(providerB.getId());

        LlmProvider persistedA = repository.findById(providerA.getId()).orElseThrow();
        LlmProvider persistedB = repository.findById(providerB.getId()).orElseThrow();
        assertFalse(persistedA.isDefault());
        assertTrue(persistedB.isDefault());
    }

    @Test
    void setDefaultThrowsForDisabledProvider() {
        CreateLlmProviderRequest request = buildCreateRequest("test-set-default-disabled-" + System.nanoTime());
        request.setEnabled(false);
        LlmProvider created = service.create(request);

        assertThrows(InvalidStateException.class, () -> service.setDefault(created.getId()));
    }

    @Test
    void deleteThrowsWhenReferencedByAgentConfig() {
        LlmProvider created = service.create(buildCreateRequest("test-delete-referenced-" + System.nanoTime()));
        factory.createAgentConfigWithLlmProvider("test-agent-ref-" + System.nanoTime(), created.getId());

        assertThrows(InvalidStateException.class, () -> service.delete(created.getId()));
    }

    @Test
    void deleteSucceedsWhenNotReferenced() {
        LlmProvider created = service.create(buildCreateRequest("test-delete-ok-" + System.nanoTime()));

        service.delete(created.getId());

        assertTrue(repository.findById(created.getId()).isEmpty());
    }

    @Test
    void deleteBuiltinThrowsInvalidStateException() {
        LlmProvider created = service.create(buildCreateRequest("test-delete-builtin-" + System.nanoTime()));
        created.setBuiltin(true);
        repository.save(created);

        assertThrows(InvalidStateException.class, () -> service.delete(created.getId()));
    }

    @Test
    void updateEnabledThrowsForDefaultProvider() {
        CreateLlmProviderRequest request = buildCreateRequest("test-disable-default-" + System.nanoTime());
        request.setDefault(true);
        LlmProvider created = service.create(request);

        assertThrows(InvalidStateException.class, () -> service.updateEnabled(created.getId(), false));
    }

    @Test
    void updateEnabledSucceedsForNonDefault() {
        LlmProvider created = service.create(buildCreateRequest("test-disable-non-default-" + System.nanoTime()));

        LlmProvider updated = service.updateEnabled(created.getId(), false);
        assertFalse(updated.isEnabled());
    }

    @Test
    void createDuplicateNameThrowsDuplicateException() {
        String name = "test-dup-name-" + System.nanoTime();
        service.create(buildCreateRequest(name));

        assertThrows(DuplicateException.class, () -> service.create(buildCreateRequest(name)));
    }

    @Test
    void createWithInvalidProviderTypeThrowsValidationException() {
        CreateLlmProviderRequest request = buildCreateRequest("test-bad-type-" + System.nanoTime());
        request.setProviderType("INVALID_TYPE");

        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createWithBlankApiKeyThrowsValidationException() {
        CreateLlmProviderRequest request = buildCreateRequest("test-blank-key-" + System.nanoTime());
        request.setApiKey("");

        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void getThrowsWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(999999L));
    }
}
