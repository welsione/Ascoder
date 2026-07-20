package cn.welsione.ascoder.runtime.application;

import cn.welsione.ascoder.agent.AgentProperties;
import cn.welsione.ascoder.codegraph.CodeGraphProperties;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.git.GitProperties;
import cn.welsione.ascoder.runtime.domain.RuntimeSettingsChangedEvent;
import cn.welsione.ascoder.runtime.domain.SystemSetting;
import cn.welsione.ascoder.runtime.persistence.SystemSettingJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RuntimeSettingsService 白名单校验、类型转换、事件发布测试。
 */
class RuntimeSettingsServiceTests {

    private SystemSettingJpaRepository repository;
    private RuntimeSettingsCache cache;
    private ApplicationEventPublisher eventPublisher;
    private RuntimeSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(SystemSettingJpaRepository.class);
        cache = mock(RuntimeSettingsCache.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        AgentProperties agent = new AgentProperties();
        CodeGraphProperties codegraph = new CodeGraphProperties();
        GitProperties git = new GitProperties();
        service = new RuntimeSettingsService(repository, cache, eventPublisher, agent, codegraph, git);
        // 触发 @PostConstruct（手动调用 initCatalog）
        invokeInitCatalog();
    }

    private void invokeInitCatalog() {
        try {
            var m = RuntimeSettingsService.class.getDeclaredMethod("initCatalog");
            m.setAccessible(true);
            m.invoke(service);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void readIntFallsBackToDefaultWhenDbEmpty() {
        when(cache.getOrLoad("agent.max-iters")).thenReturn(null);

        assertEquals(12, service.readInt("agent.max-iters"));
    }

    @Test
    void readBooleanParsesTrueFalse() {
        SystemSetting row = new SystemSetting();
        row.setKey("agent.planning-enabled");
        row.setValue("true");
        row.setValueType(cn.welsione.ascoder.runtime.domain.SettingValueType.BOOLEAN);
        when(cache.getOrLoad("agent.planning-enabled")).thenReturn(row);

        assertTrue(service.readBoolean("agent.planning-enabled"));

        row.setValue("false");
        assertFalse(service.readBoolean("agent.planning-enabled"));
    }

    @Test
    void writeRejectsUnknownKey() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.write("foo.bar", "1"));
        assertTrue(ex.getMessage().contains("未知配置项"));
    }

    @Test
    void writeCoercesAndPersists() {
        when(repository.findById("agent.max-iters")).thenReturn(Optional.empty());
        when(repository.save(any(SystemSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        service.write("agent.max-iters", "42");

        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        assertEquals("42", captor.getValue().getValue());
        verify(cache).put(any(SystemSetting.class));
        ArgumentCaptor<RuntimeSettingsChangedEvent> eventCaptor = ArgumentCaptor.forClass(RuntimeSettingsChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("agent.max-iters", eventCaptor.getValue().getKey());
        assertEquals(RuntimeSettingsChangedEvent.Action.UPDATED, eventCaptor.getValue().getAction());
    }

    @Test
    void writeRejectsInvalidInt() {
        assertThrows(ValidationException.class,
                () -> service.write("agent.max-iters", "not-a-number"));
    }

    @Test
    void resetRejectsUnknownCategory() {
        assertThrows(ValidationException.class, () -> service.reset("not-a-category"));
    }

    @Test
    void resetDeletesAndPublishes() {
        List<SystemSetting> rows = new ArrayList<>();
        when(repository.findAllByCategoryOrderByKeyAsc("agent")).thenReturn(rows);

        service.reset("agent");

        verify(repository).deleteByCategory("agent");
        verify(cache).invalidateAll();
        ArgumentCaptor<RuntimeSettingsChangedEvent> eventCaptor = ArgumentCaptor.forClass(RuntimeSettingsChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(RuntimeSettingsChangedEvent.Action.RESET, eventCaptor.getValue().getAction());
        assertEquals("agent", eventCaptor.getValue().getCategory());
    }

    @Test
    void listAllReturnsMergedViews() {
        when(repository.findAll()).thenReturn(new ArrayList<>());

        List<RuntimeSettingsService.SettingView> views = service.listAll();
        // 白名单共 21 项（17 agent + 3 codegraph + 1 git）
        assertEquals(21, views.size());
        RuntimeSettingsService.SettingView any = views.stream()
                .filter(v -> v.getKey().equals("agent.max-iters"))
                .findFirst()
                .orElseThrow();
        assertEquals("12", any.getDefaultValue());
        assertEquals("12", any.getValue());
        assertFalse(any.isOverridden());
    }

    @Test
    void listByCategoryFilters() {
        when(repository.findAll()).thenReturn(new ArrayList<>());

        List<RuntimeSettingsService.SettingView> views = service.listByCategory("codegraph");
        assertTrue(views.stream().allMatch(v -> "codegraph".equals(v.getCategory())));
        assertEquals(3, views.size());
    }
}