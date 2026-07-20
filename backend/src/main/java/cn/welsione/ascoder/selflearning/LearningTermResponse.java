package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * 术语响应，描述项目空间内的专业名词和业务语境。
 */
@Value
public class LearningTermResponse {
    Long id;
    Long projectSpaceId;
    String term;
    String aliasesJson;
    String definition;
    String scope;
    String examples;
    String source;
    double confidence;
    Date createdAt;
    Date updatedAt;

    public static LearningTermResponse from(LearningTerm term) {
        return new LearningTermResponse(
                term.getId(),
                term.getProjectSpace().getId(),
                term.getTerm(),
                term.getAliasesJson(),
                term.getDefinition(),
                term.getScope(),
                term.getExamples(),
                term.getSource(),
                term.getConfidence(),
                term.getCreatedAt(),
                term.getUpdatedAt()
        );
    }
}
