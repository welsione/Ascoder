package cn.welsione.ascoder.loganalysis.domain;

/**
 * 日志上传状态。
 */
public enum LogUploadStatus {
    /** 接收中。 */
    UPLOADING,
    /** 已落盘待解析。 */
    STORED,
    /** 预处理中。 */
    PARSING,
    /** 预处理完成可被 Agent 使用。 */
    READY,
    /** 解析失败。 */
    FAILED,
    /** 已过期或被清理。 */
    EXPIRED
}
