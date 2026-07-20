package cn.welsione.ascoder.runtime.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运行时配置变更事件。{@code RuntimeSettingsService.write / reset} 发布；
 * 各模块订阅后使本地快照失效或重新读取。
 */
@Getter
@AllArgsConstructor
public class RuntimeSettingsChangedEvent {

    public enum Action {
        /** 单条更新 */
        UPDATED,
        /** 整个分类被重置（DB 行被清空） */
        RESET
    }

    private final String key;
    private final String category;
    private final Action action;
}