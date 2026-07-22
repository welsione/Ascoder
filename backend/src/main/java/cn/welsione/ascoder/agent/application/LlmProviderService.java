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
import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LLM 供应商配置管理服务，提供 CRUD、API Key 加密存储、默认切换与引用校验。
 *
 * <p>apiKey 在持久化前经 {@link ApiKeyEncryptor} 加密，list/get 返回脱敏值，
 * {@link #getDecrypted(Long)} 供运行时获取明文。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderService {

    private final LlmProviderJpaRepository repository;
    private final ApiKeyEncryptor encryptor;
    private final AgentConfigPort agentConfigPort;
    private final List<ConnectionTestStrategy> connectionTestStrategies;
    private final RuntimeSettingsService runtimeSettings;

    @Transactional(readOnly = true)
    public List<LlmProvider> list() {
        List<LlmProvider> providers = repository.findAllByOrderBySortOrderAsc();
        providers.forEach(this::maskApiKey);
        return providers;
    }

    @Transactional(readOnly = true)
    public LlmProvider get(Long id) {
        LlmProvider provider = findOrThrow(id);
        maskApiKey(provider);
        return provider;
    }

    /**
     * 获取默认启用的 LLM 供应商，返回未脱敏的完整配置供运行时使用。
     */
    @Transactional(readOnly = true)
    public LlmProvider getDefault() {
        return repository.findByIsDefaultTrueAndEnabledTrue()
                .orElseThrow(() -> new InvalidStateException("未配置默认 LLM 供应商"));
    }

    /**
     * 获取指定 ID 的 LLM 供应商并解密 apiKey，供 LlmProviderModelFactory 使用。
     */
    @Transactional(readOnly = true)
    public LlmProvider getDecrypted(Long id) {
        LlmProvider provider = findOrThrow(id);
        provider.setApiKey(encryptor.decrypt(provider.getApiKey()));
        return provider;
    }

    @Transactional
    public LlmProvider create(CreateLlmProviderRequest request) {
        validateCreate(request);
        LlmProvider provider = new LlmProvider();
        applyCreate(provider, request);
        if (provider.isDefault()) {
            clearExistingDefault();
        }
        provider.setApiKey(encryptor.encrypt(request.getApiKey()));
        log.info("创建 LLM 供应商，name={}", request.getName());
        return repository.save(provider);
    }

    @Transactional
    public LlmProvider update(Long id, UpdateLlmProviderRequest request) {
        LlmProvider provider = findOrThrow(id);
        validateUpdate(provider, request);
        applyUpdate(provider, request);
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            // 保留原加密值，不修改
        } else {
            provider.setApiKey(encryptor.encrypt(request.getApiKey()));
        }
        if (provider.isDefault()) {
            clearExistingDefault();
        }
        provider.setUpdatedAt(LocalDateTime.now());
        log.info("更新 LLM 供应商，id={}，name={}", id, provider.getName());
        return repository.save(provider);
    }

    @Transactional
    public void delete(Long id) {
        LlmProvider provider = findOrThrow(id);
        if (provider.isBuiltin()) {
            throw new InvalidStateException("内置 LLM 供应商禁止删除");
        }
        long refCount = agentConfigPort.countByLlmProviderId(id);
        if (refCount > 0) {
            throw new InvalidStateException("该供应商被 " + refCount + " 个 Agent 引用，请先修改 Agent 配置");
        }
        repository.delete(provider);
        log.info("删除 LLM 供应商，id={}，name={}", id, provider.getName());
    }

    @Transactional
    public LlmProvider setDefault(Long id) {
        LlmProvider provider = findOrThrow(id);
        if (!provider.isEnabled()) {
            throw new InvalidStateException("禁用的供应商不能设为默认");
        }
        clearExistingDefault();
        provider.setDefault(true);
        provider.setUpdatedAt(LocalDateTime.now());
        log.info("设置默认 LLM 供应商，id={}，name={}", id, provider.getName());
        return repository.save(provider);
    }

    @Transactional
    public LlmProvider updateEnabled(Long id, boolean enabled) {
        LlmProvider provider = findOrThrow(id);
        if (provider.isDefault() && !enabled) {
            throw new InvalidStateException("默认供应商不能禁用，请先设置其他供应商为默认");
        }
        provider.setEnabled(enabled);
        provider.setUpdatedAt(LocalDateTime.now());
        log.info("更新 LLM 供应商启停，id={}，name={}，enabled={}", id, provider.getName(), enabled);
        return repository.save(provider);
    }

    /**
     * 测试指定供应商的连接可用性，按协议类型选择对应策略执行。
     */
    public ConnectionTestResult testConnection(Long id) {
        LlmProvider provider = getDecrypted(id);
        Long timeoutSeconds = provider.getTimeoutSeconds() != null
                ? provider.getTimeoutSeconds()
                : runtimeSettings.readLong("agent.tool-timeout-seconds");
        ResolvedModelConfig config = new ResolvedModelConfig(
                provider.getApiKey(), provider.getBaseUrl(), provider.getModelId(),
                provider.getMaxTokens(), timeoutSeconds, provider.getProviderType());
        for (ConnectionTestStrategy strategy : connectionTestStrategies) {
            if (strategy.supports(provider.getProviderType().name())) {
                return strategy.test(config);
            }
        }
        throw new InvalidStateException("不支持的供应商协议类型：" + provider.getProviderType());
    }

    private LlmProvider findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LlmProvider", id));
    }

    private void clearExistingDefault() {
        repository.findByIsDefaultTrueAndEnabledTrue().ifPresent(existing -> {
            existing.setDefault(false);
            existing.setUpdatedAt(LocalDateTime.now());
            repository.save(existing);
        });
    }

    private void validateCreate(CreateLlmProviderRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("name", "供应商名称不能为空");
        }
        if (repository.existsByName(request.getName())) {
            throw new DuplicateException("LLM 供应商名称已存在: " + request.getName());
        }
        if (request.getProviderType() == null || request.getProviderType().isBlank()) {
            throw new ValidationException("providerType", "供应商类型不能为空");
        }
        try {
            LlmProviderType.valueOf(request.getProviderType());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("providerType", "不支持的供应商类型: " + request.getProviderType());
        }
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            throw new ValidationException("apiKey", "API Key 不能为空");
        }
        if (request.getBaseUrl() == null || request.getBaseUrl().isBlank()) {
            throw new ValidationException("baseUrl", "Base URL 不能为空");
        }
        if (request.getModelId() == null || request.getModelId().isBlank()) {
            throw new ValidationException("modelId", "模型 ID 不能为空");
        }
    }

    private void validateUpdate(LlmProvider provider, UpdateLlmProviderRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("name", "供应商名称不能为空");
        }
        if (!request.getName().equals(provider.getName()) && repository.existsByName(request.getName())) {
            throw new DuplicateException("LLM 供应商名称已存在: " + request.getName());
        }
        if (request.getProviderType() != null && !request.getProviderType().isBlank()) {
            try {
                LlmProviderType.valueOf(request.getProviderType());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("providerType", "不支持的供应商类型: " + request.getProviderType());
            }
        }
        if (request.getBaseUrl() != null && request.getBaseUrl().isBlank()) {
            throw new ValidationException("baseUrl", "Base URL 不能为空");
        }
        if (request.getModelId() != null && request.getModelId().isBlank()) {
            throw new ValidationException("modelId", "模型 ID 不能为空");
        }
    }

    private void applyCreate(LlmProvider provider, CreateLlmProviderRequest request) {
        provider.setName(request.getName());
        provider.setProviderType(LlmProviderType.valueOf(request.getProviderType()));
        provider.setBaseUrl(request.getBaseUrl());
        provider.setModelId(request.getModelId());
        provider.setMaxTokens(request.getMaxTokens());
        provider.setTimeoutSeconds(request.getTimeoutSeconds());
        provider.setDefault(request.isDefault());
        provider.setEnabled(request.isEnabled());
        provider.setBuiltin(false);
        provider.setSortOrder(0);
    }

    private void applyUpdate(LlmProvider provider, UpdateLlmProviderRequest request) {
        provider.setName(request.getName());
        if (request.getProviderType() != null && !request.getProviderType().isBlank()) {
            provider.setProviderType(LlmProviderType.valueOf(request.getProviderType()));
        }
        if (request.getBaseUrl() != null && !request.getBaseUrl().isBlank()) {
            provider.setBaseUrl(request.getBaseUrl());
        }
        if (request.getModelId() != null && !request.getModelId().isBlank()) {
            provider.setModelId(request.getModelId());
        }
        if (request.getMaxTokens() != null) {
            provider.setMaxTokens(request.getMaxTokens());
        }
        if (request.getTimeoutSeconds() != null) {
            provider.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        provider.setDefault(request.isDefault());
        provider.setEnabled(request.isEnabled());
    }

    /**
     * 对 apiKey 进行脱敏：保留前 2 位和后 4 位，中间用 **** 替换。
     * 长度不足 6 位的直接返回 ****。
     */
    private void maskApiKey(LlmProvider provider) {
        String apiKey = provider.getApiKey();
        if (apiKey == null || apiKey.length() < 6) {
            provider.setApiKey("****");
            return;
        }
        String masked = apiKey.substring(0, 2) + "****" + apiKey.substring(apiKey.length() - 4);
        provider.setApiKey(masked);
    }
}
