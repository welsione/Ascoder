package cn.welsione.ascoder.runtime.application;

import cn.welsione.ascoder.runtime.domain.SystemSetting;
import cn.welsione.ascoder.runtime.persistence.SystemSettingJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时配置本地缓存，避免每次读取都打 DB。
 *
 * <p>失效策略：写操作（{@link RuntimeSettingsService#write} / {@link RuntimeSettingsService#reset}）
 * 后由 {@link RuntimeSettingsService} 调用 {@link #invalidate(String)} / {@link #invalidateAll()}；
 * 读路径通过 {@link #computeIfAbsent} 防止并发回填。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeSettingsCache {

    private final SystemSettingJpaRepository repository;
    private final Map<String, SystemSetting> cache = new ConcurrentHashMap<>();
    private volatile long lastInvalidationMillis = 0L;

    /**
     * 读缓存：未命中时回查 DB，命中即返回。
     * 失效时间窗（200ms）内的并发读返回旧值，避免雪崩。
     */
    public SystemSetting getOrLoad(String key) {
        SystemSetting cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (System.currentTimeMillis() - lastInvalidationMillis < 200L) {
            // 失效窗内，直接读 DB 但不写回缓存
            return repository.findById(key).orElse(null);
        }
        // computeIfAbsent 保证并发安全：只一个线程回填
        return cache.computeIfAbsent(key, k -> repository.findById(k).orElse(null));
    }

    public void put(SystemSetting setting) {
        cache.put(setting.getKey(), setting);
    }

    public void invalidate(String key) {
        cache.remove(key);
        lastInvalidationMillis = System.currentTimeMillis();
    }

    public void invalidateAll() {
        cache.clear();
        lastInvalidationMillis = System.currentTimeMillis();
    }
}