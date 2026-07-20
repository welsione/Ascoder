package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * AgentConfig.listEnabled 的内存缓存，手写 TTL（30s）+ 事件失效。
 *
 * <p>项目未引入 Caffeine / Spring Cache，采用 ConcurrentHashMap + 时间戳实现。
 * 缓存单条目（listEnabled 列表），{@link #invalidate()} 由
 * {@link AgentConfigCacheListener} 在配置变更事件后调用。</p>
 */
@Component
public class AgentConfigCache {

    private static final String KEY = "listEnabled";
    private static final long DEFAULT_TTL_MILLIS = 30_000L;

    private final long ttlMillis;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public AgentConfigCache() {
        this(DEFAULT_TTL_MILLIS);
    }

    /** 供测试指定 TTL。 */
    AgentConfigCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * 获取 listEnabled 缓存，未命中或过期时调 loader 回填。
     */
    public List<AgentConfig> get(Supplier<List<AgentConfig>> loader) {
        CacheEntry entry = cache.get(KEY);
        if (entry != null && !entry.expired(ttlMillis)) {
            return entry.value;
        }
        List<AgentConfig> loaded = loader.get();
        cache.put(KEY, new CacheEntry(loaded));
        return loaded;
    }

    /**
     * 清空缓存。
     */
    public void invalidate() {
        cache.clear();
    }

    private static final class CacheEntry {
        final List<AgentConfig> value;
        final long timestamp;

        CacheEntry(List<AgentConfig> value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean expired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }
}
