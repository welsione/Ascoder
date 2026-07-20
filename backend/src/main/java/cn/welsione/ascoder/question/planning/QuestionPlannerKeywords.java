package cn.welsione.ascoder.question.planning;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 加载 {@code text-planner-keywords.yml} 中的分类关键词，供各策略复用。
 * 将硬编码字符串从 Java 源迁移到资源文件，便于运维快速调优而无需重新编译。
 */
@Slf4j
@Component
class QuestionPlannerKeywords {

    private final Resource keywordResource;
    private Map<String, List<String>> keywordMap = Collections.emptyMap();
    private Map<String, List<String>> dynamicKeywords = Collections.emptyMap();

    QuestionPlannerKeywords(@Value("classpath:question-planner-keywords.yml") Resource keywordResource) {
        this.keywordResource = keywordResource;
    }

    @PostConstruct
    @SuppressWarnings("unchecked")
    void load() {
        try (InputStream in = keywordResource.getInputStream()) {
            Map<String, Object> raw = new Yaml().load(in);
            Map<String, List<String>> map = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if ("dynamic".equals(entry.getKey())) {
                    continue;
                }
                map.put(entry.getKey(), (List<String>) entry.getValue());
            }
            this.keywordMap = Map.copyOf(map);
            Object dyn = raw.get("dynamic");
            if (dyn instanceof Map<?, ?> dynMap) {
                Map<String, List<String>> dynamic = new HashMap<>();
                dynMap.forEach((k, v) -> dynamic.put((String) k, (List<String>) v));
                this.dynamicKeywords = Map.copyOf(dynamic);
            }
            log.info("加载问题分类关键词完成，分类数={}，动态技能关键词组数={}", keywordMap.size(), dynamicKeywords.size());
        } catch (IOException ex) {
            throw new IllegalStateException("加载问题分类关键词失败", ex);
        }
    }

    List<String> forCategory(String category) {
        return keywordMap.getOrDefault(category, List.of());
    }

    List<String> dynamic(String key) {
        return dynamicKeywords.getOrDefault(key, List.of());
    }
}
