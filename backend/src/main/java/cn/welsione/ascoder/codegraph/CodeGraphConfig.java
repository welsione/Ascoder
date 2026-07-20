package cn.welsione.ascoder.codegraph;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * CodeGraph CLI 配置参数，从外部传入。
 */
@Getter
@AllArgsConstructor
public class CodeGraphConfig {
    private final String executable;
    private final Duration timeout;
    private final Duration indexTimeout;
}
