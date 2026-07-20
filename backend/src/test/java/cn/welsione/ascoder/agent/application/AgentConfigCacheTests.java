package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentConfigCache 缓存命中、过期、失效行为测试。
 */
class AgentConfigCacheTests {

    @Test
    void firstCallInvokesLoader() {
        AgentConfigCache cache = new AgentConfigCache(30_000L);
        List<AgentConfig> loaded = List.of(config("a"));
        List<Integer> callCount = new ArrayList<>(List.of(0));

        List<AgentConfig> result = cache.get(() -> {
            callCount.set(0, callCount.get(0) + 1);
            return loaded;
        });

        assertEquals(loaded, result);
        assertEquals(1, callCount.get(0));
    }

    @Test
    void secondCallHitsCacheWithoutLoader() {
        AgentConfigCache cache = new AgentConfigCache(30_000L);
        List<AgentConfig> loaded = List.of(config("a"));
        List<Integer> callCount = new ArrayList<>(List.of(0));

        cache.get(() -> {
            callCount.set(0, callCount.get(0) + 1);
            return loaded;
        });
        List<AgentConfig> result = cache.get(() -> {
            callCount.set(0, callCount.get(0) + 1);
            return List.of(config("b"));
        });

        assertEquals(loaded, result);
        assertEquals(1, callCount.get(0));
    }

    @Test
    void invalidateForcesReload() {
        AgentConfigCache cache = new AgentConfigCache(30_000L);
        List<AgentConfig> first = List.of(config("a"));
        List<AgentConfig> second = List.of(config("b"));
        List<Integer> callCount = new ArrayList<>(List.of(0));

        cache.get(() -> {
            callCount.set(0, callCount.get(0) + 1);
            return first;
        });
        cache.invalidate();
        List<AgentConfig> result = cache.get(() -> {
            callCount.set(0, callCount.get(0) + 1);
            return second;
        });

        assertEquals(second, result);
        assertEquals(2, callCount.get(0));
    }

    @Test
    void expiredEntryReloads() throws InterruptedException {
        AgentConfigCache cache = new AgentConfigCache(20L);
        List<AgentConfig> first = List.of(config("a"));
        List<AgentConfig> second = List.of(config("b"));
        List<Integer> callCount = new ArrayList<>(List.of(0));

        cache.get(() -> {
            callCount.set(0, callCount.get(0) + 1);
            return first;
        });
        Thread.sleep(40L);
        List<AgentConfig> result = cache.get(() -> {
            callCount.set(0, callCount.get(0) + 1);
            return second;
        });

        assertEquals(second, result);
        assertEquals(2, callCount.get(0));
    }

    private AgentConfig config(String agentId) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(agentId);
        return config;
    }
}
