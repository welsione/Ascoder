package cn.welsione.ascoder.runtime.application;

import cn.welsione.ascoder.agent.AgentProperties;
import cn.welsione.ascoder.codegraph.CodeGraphProperties;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.git.GitProperties;
import cn.welsione.ascoder.runtime.domain.RuntimeSettingsChangedEvent;
import cn.welsione.ascoder.runtime.domain.SettingValueType;
import cn.welsione.ascoder.runtime.domain.SystemSetting;
import cn.welsione.ascoder.runtime.persistence.SystemSettingJpaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时配置服务：白名单读 + 类型校验 + DB 写入 + 事件发布。
 *
 * <p>读路径：DB 命中即返回；未命中回退到 {@link RuntimeSettingCatalog.Meta#getDefaultValue()}（即 application.yml 默认值）。</p>
 *
 * <p>写路径：仅允许白名单 key，写入前按 {@link SettingValueType} 校验与转换。</p>
 *
 * <p>事件：每次写或 reset 都发布 {@link RuntimeSettingsChangedEvent}，
 * 监听方在事务提交后失效本地快照或重新读取。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeSettingsService {

    private final SystemSettingJpaRepository repository;
    private final RuntimeSettingsCache cache;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentProperties agentProperties;
    private final CodeGraphProperties codegraphProperties;
    private final GitProperties gitProperties;

    private Map<String, RuntimeSettingCatalog.Meta> catalog;

    @PostConstruct
    void initCatalog() {
        this.catalog = RuntimeSettingCatalog.buildCatalog(agentProperties, codegraphProperties, gitProperties);
        log.info("运行时配置白名单初始化完成，共 {} 项", catalog.size());
    }

    // ==================== 类型化读取 ====================

    public int readInt(String key) {
        return toInt(key, readRaw(key));
    }

    public long readLong(String key) {
        return toLong(key, readRaw(key));
    }

    public boolean readBoolean(String key) {
        return toBoolean(key, readRaw(key));
    }

    public double readDouble(String key) {
        return toDouble(key, readRaw(key));
    }

    public String readString(String key) {
        Object raw = readRaw(key);
        return raw == null ? null : raw.toString();
    }

    /**
     * 读原始值：DB 优先 → 白名单默认。
     * DB 未命中且 key 不在白名单时返回 null（不抛异常，让调用方走自己的默认值）。
     */
    public Object readRaw(String key) {
        SystemSetting setting = cache.getOrLoad(key);
        if (setting != null) {
            return coerceValue(setting.getValue(), setting.getValueType());
        }
        RuntimeSettingCatalog.Meta meta = catalog.get(key);
        return meta == null ? null : meta.getDefaultValue();
    }

    // ==================== 写入 ====================

    @Transactional
    public SystemSetting write(String key, String rawValue) {
        RuntimeSettingCatalog.Meta meta = catalog.get(key);
        if (meta == null) {
            throw new ValidationException("key", "未知配置项: " + key);
        }
        Object coerced = coerceValue(rawValue, meta.getValueType());
        String normalized = String.valueOf(coerced);

        SystemSetting setting = repository.findById(key).orElseGet(() -> {
            SystemSetting created = new SystemSetting();
            created.setKey(key);
            created.setValueType(meta.getValueType());
            created.setCategory(meta.getCategory());
            created.setDescription(meta.getDescription());
            created.setUpdatedAt(LocalDateTime.now());
            return created;
        });
        setting.setValue(normalized);
        setting.setValueType(meta.getValueType());
        setting.setCategory(meta.getCategory());
        setting.setDescription(meta.getDescription());
        setting.setUpdatedAt(LocalDateTime.now());
        SystemSetting saved = repository.save(setting);
        cache.put(saved);
        log.info("运行时配置已更新，key={}，value={}", key, normalized);
        eventPublisher.publishEvent(new RuntimeSettingsChangedEvent(key, meta.getCategory(), RuntimeSettingsChangedEvent.Action.UPDATED));
        return saved;
    }

    /**
     * 重置某分类：删除该分类的所有 DB 行（恢复 application.yml 默认值）。
     */
    @Transactional
    public int reset(String category) {
        validateCategory(category);
        int deleted = repository.findAllByCategoryOrderByKeyAsc(category).size();
        repository.deleteByCategory(category);
        cache.invalidateAll();
        log.info("运行时配置分类已重置，category={}，清理 {} 项", category, deleted);
        eventPublisher.publishEvent(new RuntimeSettingsChangedEvent(null, category, RuntimeSettingsChangedEvent.Action.RESET));
        return deleted;
    }

    // ==================== 列表 ====================

    /**
     * 返回白名单全量配置（合并 DB 覆盖值与默认值）。
     */
    @Transactional(readOnly = true)
    public List<SettingView> listAll() {
        Map<String, SystemSetting> dbRows = new LinkedHashMap<>();
        repository.findAll().forEach(s -> dbRows.put(s.getKey(), s));
        List<SettingView> views = new ArrayList<>();
        catalog.values().stream()
                .sorted(Comparator.comparing(RuntimeSettingCatalog.Meta::getKey))
                .forEach(meta -> {
                    SystemSetting row = dbRows.get(meta.getKey());
                    Object effective = row != null ? coerceValue(row.getValue(), row.getValueType()) : meta.getDefaultValue();
                    views.add(new SettingView(
                            meta.getKey(),
                            effective == null ? null : String.valueOf(effective),
                            String.valueOf(meta.getDefaultValue()),
                            meta.getValueType().name(),
                            meta.getCategory(),
                            meta.getDescription(),
                            row != null
                    ));
                });
        return views;
    }

    @Transactional(readOnly = true)
    public List<SettingView> listByCategory(String category) {
        validateCategory(category);
        return listAll().stream().filter(v -> v.getCategory().equals(category)).toList();
    }

    private void validateCategory(String category) {
        List<String> allowed = List.of(
                RuntimeSettingCatalog.CATEGORY_AGENT,
                RuntimeSettingCatalog.CATEGORY_CODEGRAPH,
                RuntimeSettingCatalog.CATEGORY_GIT);
        if (category == null || !allowed.contains(category)) {
            throw new ValidationException("category", "未知分类: " + category);
        }
    }

    // ==================== 类型转换 ====================

    private Object coerceValue(String raw, SettingValueType type) {
        if (raw == null) {
            return null;
        }
        try {
            return switch (type) {
                case INT -> Integer.parseInt(raw.trim());
                case LONG -> Long.parseLong(raw.trim());
                case BOOLEAN -> Boolean.parseBoolean(raw.trim());
                case DOUBLE -> Double.parseDouble(raw.trim());
                case STRING -> raw;
            };
        } catch (NumberFormatException e) {
            throw new ValidationException("value", "无法解析为 " + type + ": " + raw);
        }
    }

    private int toInt(String key, Object value) {
        if (value == null) {
            RuntimeSettingCatalog.Meta meta = catalog.get(key);
            throw new ValidationException("key", meta == null ? "未配置: " + key : meta.getKey() + " 缺少默认值");
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new ValidationException("key", key + " 不是 INT: " + value);
        }
    }

    private long toLong(String key, Object value) {
        if (value == null) {
            throw new ValidationException("key", "未配置: " + key);
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new ValidationException("key", key + " 不是 LONG: " + value);
        }
    }

    private boolean toBoolean(String key, Object value) {
        if (value == null) {
            throw new ValidationException("key", "未配置: " + key);
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private double toDouble(String key, Object value) {
        if (value == null) {
            throw new ValidationException("key", "未配置: " + key);
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new ValidationException("key", key + " 不是 DOUBLE: " + value);
        }
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class SettingView {
        private final String key;
        private final String value;
        private final String defaultValue;
        private final String valueType;
        private final String category;
        private final String description;
        private final boolean overridden;
    }
}