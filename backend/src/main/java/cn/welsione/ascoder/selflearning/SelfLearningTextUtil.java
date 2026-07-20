package cn.welsione.ascoder.selflearning;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 自学习模块共享的文本与 JSON 处理工具，供各拆分后的 Service 复用。
 */
@Slf4j
public final class SelfLearningTextUtil {

    private SelfLearningTextUtil() {
    }

    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String toJsonArray(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    public static String toJsonArrayText(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        return items.stream().collect(Collectors.joining(",", "[", "]"));
    }

    public static List<Long> parseJsonIds(ObjectMapper objectMapper, String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            long[] ids = objectMapper.readValue(value, long[].class);
            List<Long> result = new java.util.ArrayList<>();
            for (long id : ids) {
                result.add(id);
            }
            return result;
        } catch (Exception ex) {
            log.warn("解析 JSON ID 数组失败，value={}，error={}", value, ex.getMessage());
            return List.of();
        }
    }

    public static String jsonString(ObjectMapper objectMapper, String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("JSON 序列化失败，value={}，error={}", value, ex.getMessage());
            return "null";
        }
    }

    public static String mergeWarnings(String agentWarnings) {
        String mandatory = "管理员审核时必须核对当前代码、工具证据和 Git 追溯；若与当前代码冲突，必须以当前代码为准。";
        String warnings = trimToNull(agentWarnings);
        if (warnings == null) {
            return mandatory;
        }
        if (warnings.contains("当前代码") && warnings.contains("Git")) {
            return warnings;
        }
        return warnings + "\n" + mandatory;
    }

    public static double normalizeAgentConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN()) {
            return 0.42;
        }
        return Math.max(0.1, Math.min(0.8, confidence));
    }

    public static String candidateTitle(String questionText) {
        return "问答经验：" + truncate(questionText == null ? "未命名问题" : questionText.trim(), 80);
    }
}
