package cn.welsione.ascoder.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 供应商连接测试结果。
 */
@Getter
@AllArgsConstructor
public class ConnectionTestResult {

    private final boolean success;
    private final String message;
    private final Long latencyMs;
}
