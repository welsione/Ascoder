package cn.welsione.ascoder.analysis;

import cn.welsione.ascoder.agent.domain.AnswerEvidence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 CodeGraph 文本上下文中提取可展示的代码证据。
 */
public class CodeEvidenceExtractor {

    private static final int MAX_EVIDENCE_ITEMS = 8;

    private static final Pattern FILE_WITH_LINE = Pattern.compile("([\\w./-]+\\.(?:java|yml|yaml|xml|sql|ts|tsx|js|vue|md)):(\\d+)");
    private static final Pattern FILE_ONLY = Pattern.compile("([\\w./-]+\\.(?:java|yml|yaml|xml|sql|ts|tsx|js|vue|md))");

    public List<AnswerEvidence> extract(String codeContext) {
        if (codeContext == null || codeContext.isBlank()) {
            return List.of();
        }

        Set<String> references = new LinkedHashSet<>();
        Matcher withLine = FILE_WITH_LINE.matcher(codeContext);
        while (withLine.find()) {
            references.add(withLine.group(1) + ":" + withLine.group(2));
        }

        Matcher fileOnly = FILE_ONLY.matcher(codeContext);
        while (fileOnly.find()) {
            String reference = fileOnly.group(1);
            boolean alreadyCapturedWithLine = references.stream()
                    .anyMatch(item -> item.startsWith(reference + ":"));
            if (!alreadyCapturedWithLine) {
                references.add(reference);
            }
        }

        List<AnswerEvidence> evidence = new ArrayList<>();
        for (String reference : references) {
            if (isInternalSkillDefinition(reference)) {
                continue;
            }
            evidence.add(new AnswerEvidence(
                    "CodeGraph 代码引用",
                    reference,
                    ""
            ));
            if (evidence.size() >= MAX_EVIDENCE_ITEMS) {
                break;
            }
        }
        return evidence;
    }

    private boolean isInternalSkillDefinition(String reference) {
        return reference.contains("/skill/BuiltinSkillCatalog.java")
                || reference.contains("/skill/BuiltinSkillDefinition.java")
                || reference.contains("/skill/BuiltinSkillInitializer.java");
    }
}
