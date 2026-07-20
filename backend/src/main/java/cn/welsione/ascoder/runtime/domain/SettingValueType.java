package cn.welsione.ascoder.runtime.domain;

/**
 * 运行时配置项的值类型，决定如何把字符串解析为 Java 类型。
 */
public enum SettingValueType {
    INT,
    LONG,
    BOOLEAN,
    STRING,
    DOUBLE
}