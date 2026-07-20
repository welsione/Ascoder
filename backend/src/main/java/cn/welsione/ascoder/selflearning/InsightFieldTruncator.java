package cn.welsione.ascoder.selflearning;

import org.springframework.stereotype.Component;

/**
 * LLM 输出字段截断器，按 DB 列长度安全截断。
 *
 * <p>LLM 返回的 title / summary / conclusion 等字段可能超过数据库列定义长度，
 * 在持久化前必须截断，否则 DataIntegrityViolationException 会导致整个对话整理失败。</p>
 */
@Component
public class InsightFieldTruncator {

    private static final int TITLE_MAX = 200;
    private static final int SUMMARY_MAX = 6000;
    private static final int CONCLUSION_MAX = 6000;
    private static final int TAGS_MAX = 500;

    public String truncateTitle(String title) {
        return truncate(title, TITLE_MAX);
    }

    public String truncateSummary(String summary) {
        return truncate(summary, SUMMARY_MAX);
    }

    public String truncateConclusion(String conclusion) {
        return truncate(conclusion, CONCLUSION_MAX);
    }

    public String truncateTags(String tags) {
        return truncate(tags, TAGS_MAX);
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 10) + "...[truncated]";
    }
}
