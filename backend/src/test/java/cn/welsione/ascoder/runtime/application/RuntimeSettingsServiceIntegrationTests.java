package cn.welsione.ascoder.runtime.application;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.runtime.domain.SystemSetting;
import cn.welsione.ascoder.runtime.persistence.SystemSettingJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuntimeSettingsService 集成测试：验证白名单读写、类型校验、分类重置与全量列表。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。
 * 由于 {@link RuntimeSettingsCache} 是内存单例、不参与事务回滚，
 * 每个测试方法前通过 {@code @BeforeEach} 清空缓存，避免跨测试污染。</p>
 */
@Transactional
class RuntimeSettingsServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private RuntimeSettingsService service;

    @Autowired
    private SystemSettingJpaRepository repository;

    @Autowired
    private RuntimeSettingsCache cache;

    @BeforeEach
    void invalidateCache() {
        cache.invalidateAll();
    }

    @Test
    void writeAndReadIntRoundTrip() {
        service.write("agent.max-iters", "20");

        int value = service.readInt("agent.max-iters");
        assertEquals(20, value);

        SystemSetting saved = repository.findById("agent.max-iters").orElseThrow();
        assertEquals("20", saved.getValue());
    }

    @Test
    void writeUnknownKeyThrowsValidationException() {
        assertThrows(ValidationException.class, () -> service.write("unknown.key", "value"));
    }

    @Test
    void writeTypeMismatchThrowsValidationException() {
        assertThrows(ValidationException.class, () -> service.write("agent.max-iters", "abc"));
    }

    @Test
    void resetAgentCategoryRestoresDefaultsButNotCodegraph() {
        service.write("agent.max-iters", "20");
        service.write("codegraph.timeout-seconds", "600");

        int deleted = service.reset("agent");
        assertTrue(deleted >= 1, "reset 应至少删除 1 条 agent 配置");

        assertEquals(12, service.readInt("agent.max-iters"), "agent.max-iters 应恢复默认值 12");
        assertEquals(600L, service.readLong("codegraph.timeout-seconds"), "codegraph.timeout-seconds 不应受影响");
    }

    @Test
    void listAllReturnsFullCatalog() {
        service.write("agent.max-iters", "99");

        List<RuntimeSettingsService.SettingView> views = service.listAll();

        assertNotNull(views);
        assertFalse(views.isEmpty(), "listAll 不应返回空列表");

        assertTrue(views.stream().anyMatch(v -> v.getCategory().equals(RuntimeSettingCatalog.CATEGORY_AGENT)));
        assertTrue(views.stream().anyMatch(v -> v.getCategory().equals(RuntimeSettingCatalog.CATEGORY_CODEGRAPH)));
        assertTrue(views.stream().anyMatch(v -> v.getCategory().equals(RuntimeSettingCatalog.CATEGORY_GIT)));

        RuntimeSettingsService.SettingView maxItersView = views.stream()
                .filter(v -> v.getKey().equals("agent.max-iters"))
                .findFirst()
                .orElseThrow();
        assertEquals("99", maxItersView.getValue(), "DB 覆盖值应为 99");
        assertEquals("12", maxItersView.getDefaultValue(), "默认值应为 12");
        assertTrue(maxItersView.isOverridden(), "已写入 DB 的配置应标记为 overridden");
    }

    @Test
    void readBooleanReadsCorrectly() {
        service.write("agent.planning-enabled", "false");
        assertFalse(service.readBoolean("agent.planning-enabled"));

        service.write("agent.planning-enabled", "true");
        assertTrue(service.readBoolean("agent.planning-enabled"));
    }

    @Test
    void readLongReadsCorrectly() {
        service.write("codegraph.timeout-seconds", "600");
        assertEquals(600L, service.readLong("codegraph.timeout-seconds"));
    }

    @Test
    void readDoubleReadsCorrectly() {
        service.write("agent.query-planner-confidence-threshold", "0.85");
        assertEquals(0.85, service.readDouble("agent.query-planner-confidence-threshold"), 0.001);
    }

    @Test
    void readStringReadsCorrectly() {
        service.write("codegraph.executable", "/usr/local/bin/codegraph");
        assertEquals("/usr/local/bin/codegraph", service.readString("codegraph.executable"));
    }

    @Test
    void readIntReturnsDefaultWhenNotInDb() {
        repository.findById("agent.max-iters").ifPresent(repository::delete);
        cache.invalidateAll();

        int value = service.readInt("agent.max-iters");
        assertEquals(12, value, "未写入 DB 时应返回默认值 12");
    }
}
