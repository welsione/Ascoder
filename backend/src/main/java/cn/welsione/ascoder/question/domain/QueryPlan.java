package cn.welsione.ascoder.question.domain;

import cn.welsione.ascoder.question.planning.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 查询规划实体，记录问题分类、改写查询和推荐工具。
 */
@Entity
@Table(name = "queryPlans")
@Getter
@Setter
@NoArgsConstructor
public class QueryPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questionId", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(name = "questionType", nullable = false, length = 48)
    private QuestionType type;

    @Column(nullable = false, columnDefinition = "text")
    private String rewrittenQueriesJson;

    @Column(nullable = false, columnDefinition = "text")
    private String recommendedToolsJson;

    @Column(nullable = false, columnDefinition = "text")
    private String recommendedSkillsJson = "[]";

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, columnDefinition = "text")
    private String matchedSignalsJson = "[]";

    @Column(nullable = false, columnDefinition = "text")
    private String alternativeTypesJson = "[]";

    @Column(columnDefinition = "text")
    private String reasoning;

    @Column(nullable = false)
    private Date createdAt = new Date();
}
