package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自学习上下文构建器，根据项目空间正式知识库为回答注入历史线索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfLearningContextBuilder {

    private static final int MAX_CONTEXT_KNOWLEDGE = 6;
    private static final int MAX_CONTEXT_CHARS = 12000;

    private final ProjectSpaceService projectSpaceService;
    private final SelfLearningEntityLoader entityLoader;
    private final LearningKnowledgeItemJpaRepository knowledgeRepository;

    @Transactional
    public String buildContext(Long projectSpaceId, String questionText) {
        SelfLearningSettings settings = entityLoader.settings(projectSpaceId);
        if (!settings.isEnabled() || !settings.isAnswerInjectionEnabled()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendContextHeader(builder);
        appendKnowledgeContext(builder, projectSpaceId, questionText);
        return SelfLearningTextUtil.truncate(builder.toString(), MAX_CONTEXT_CHARS);
    }

    private void appendContextHeader(StringBuilder builder) {
        builder.append("""
                以下内容来自项目空间正式知识库，仅作为历史线索：
                - 只有 active / verified 正式知识可用于回答线索
                - negative 知识只用于提醒不要重复历史错误
                - 如与当前代码证据冲突，必须以当前代码为准
                """.trim()).append("\n\n");
    }

    private void appendKnowledgeContext(StringBuilder builder, Long projectSpaceId, String questionText) {
        List<LearningKnowledgeItem> selected = selectRelevant(
                knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).stream()
                        .filter(item -> item.getStatus() == LearningKnowledgeStatus.VERIFIED
                                || item.getStatus() == LearningKnowledgeStatus.ACTIVE
                                || item.getStatus() == LearningKnowledgeStatus.NEGATIVE)
                        .toList(),
                questionText,
                MAX_CONTEXT_KNOWLEDGE
        );
        if (selected.isEmpty()) {
            return;
        }
        builder.append("## 正式知识与防错提醒\n\n");
        for (LearningKnowledgeItem item : selected) {
            builder.append("- 标题：").append(item.getTitle()).append('\n');
            builder.append("  类型：").append(item.getType()).append("，状态：").append(item.getStatus())
                    .append("，置信度：").append(item.getConfidence()).append('\n');
            if (item.getApplicableScope() != null) {
                builder.append("  适用范围：").append(item.getApplicableScope()).append('\n');
            }
            builder.append("  内容：").append(SelfLearningTextUtil.truncate(item.getContent(), 900)).append('\n');
            if (item.getEvidenceJson() != null) {
                builder.append("  证据：").append(SelfLearningTextUtil.truncate(item.getEvidenceJson(), 500)).append('\n');
            }
            if (item.getGitProvenanceJson() != null) {
                builder.append("  Git 追溯：").append(SelfLearningTextUtil.truncate(item.getGitProvenanceJson(), 500)).append('\n');
            }
        }
        builder.append('\n');
    }

    private <T> List<T> selectRelevant(List<T> items, String questionText, int limit) {
        List<T> matched = items.stream()
                .filter(item -> matches(item, questionText))
                .limit(limit)
                .toList();
        if (!matched.isEmpty()) {
            return matched;
        }
        return items.stream().limit(limit).toList();
    }

    private boolean matches(Object item, String questionText) {
        if (questionText == null || questionText.isBlank()) {
            return false;
        }
        String question = questionText.toLowerCase();
        String haystack = searchableText(item).toLowerCase();
        for (String token : question.split("[\\s,，。；;：:、]+")) {
            if (token.length() >= 2 && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String searchableText(Object item) {
        if (item instanceof LearningExperience experience) {
            return String.join(" ",
                    SelfLearningTextUtil.nullToEmpty(experience.getTitle()),
                    SelfLearningTextUtil.nullToEmpty(experience.getProblem()),
                    SelfLearningTextUtil.nullToEmpty(experience.getConclusion()),
                    SelfLearningTextUtil.nullToEmpty(experience.getApplicableScope()),
                    SelfLearningTextUtil.nullToEmpty(experience.getTags())
            );
        }
        if (item instanceof LearningTerm term) {
            return String.join(" ",
                    SelfLearningTextUtil.nullToEmpty(term.getTerm()),
                    SelfLearningTextUtil.nullToEmpty(term.getAliasesJson()),
                    SelfLearningTextUtil.nullToEmpty(term.getDefinition()),
                    SelfLearningTextUtil.nullToEmpty(term.getScope())
            );
        }
        if (item instanceof LearningCorrection correction) {
            return String.join(" ",
                    SelfLearningTextUtil.nullToEmpty(correction.getWrongConclusion()),
                    SelfLearningTextUtil.nullToEmpty(correction.getCorrectedConclusion()),
                    SelfLearningTextUtil.nullToEmpty(correction.getVerificationProcess())
            );
        }
        if (item instanceof LearningKnowledgeItem knowledgeItem) {
            return String.join(" ",
                    SelfLearningTextUtil.nullToEmpty(knowledgeItem.getTitle()),
                    SelfLearningTextUtil.nullToEmpty(knowledgeItem.getContent()),
                    SelfLearningTextUtil.nullToEmpty(knowledgeItem.getSummary()),
                    SelfLearningTextUtil.nullToEmpty(knowledgeItem.getApplicableScope()),
                    SelfLearningTextUtil.nullToEmpty(knowledgeItem.getTags())
            );
        }
        return "";
    }
}
