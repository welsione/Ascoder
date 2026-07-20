package cn.welsione.ascoder.selflearning;

import lombok.Value;

/**
 * 历史聊天导入为自学习原始记录后的结果。
 */
@Value
public class ImportHistoryRawEventsResponse {
    int importedConversationCount;
    int importedRawEventCount;
    int skippedRawEventCount;
    String message;
}
