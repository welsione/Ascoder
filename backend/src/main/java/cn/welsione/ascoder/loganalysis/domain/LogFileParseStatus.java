package cn.welsione.ascoder.loganalysis.domain;

/**
 * 日志文件解析状态。
 */
public enum LogFileParseStatus {
    PENDING,
    PARSING,
    PARSED,
    FAILED
}
