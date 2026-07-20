package cn.welsione.ascoder.common;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * 外部命令执行结果。
 */
@Value
@AllArgsConstructor
public class CommandResult {
    boolean success;
    String output;
}
