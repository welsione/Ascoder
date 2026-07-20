package cn.welsione.ascoder.question.api;

import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.planning.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link QuestionResponse} 嵌套 DTO 在 JSON 解析失败、为空、正常等场景的健壮性。
 */
class QuestionResponseTests {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void evidenceFromJson_nullReturnsEmpty() {
        assertThat(QuestionResponse.AnswerEvidenceResponse.fromJson(null, mapper)).isEmpty();
    }

    @Test
    void evidenceFromJson_blankReturnsEmpty() {
        assertThat(QuestionResponse.AnswerEvidenceResponse.fromJson("   ", mapper)).isEmpty();
    }

    @Test
    void evidenceFromJson_malformedReturnsEmpty() {
        assertThat(QuestionResponse.AnswerEvidenceResponse.fromJson("not-json", mapper)).isEmpty();
    }

    @Test
    void evidenceFromJson_validParses() {
        String json = "[{\"title\":\"t\",\"reference\":\"r\",\"detail\":\"d\"}]";
        List<QuestionResponse.AnswerEvidenceResponse> list =
                QuestionResponse.AnswerEvidenceResponse.fromJson(json, mapper);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("t");
        assertThat(list.get(0).getReference()).isEqualTo("r");
        assertThat(list.get(0).getDetail()).isEqualTo("d");
    }

    @Test
    void queryPlanFrom_nullPlanReturnsNull() {
        assertThat(QuestionResponse.QueryPlanResponse.from(null, mapper)).isNull();
    }

    @Test
    void queryPlanFrom_parsesScoringFields() {
        QueryPlan queryPlan = queryPlan(
                "[\"入口\"]",
                "[\"codegraph_search\"]",
                "[\"spring_boot_analysis\"]",
                "[\"ENTRY_POINT:入口\"]",
                "[\"BUSINESS_FLOW\"]"
        );

        QuestionResponse.QueryPlanResponse response = QuestionResponse.QueryPlanResponse.from(queryPlan, mapper);

        assertThat(response.getConfidence()).isEqualTo(0.67);
        assertThat(response.getMatchedSignals()).containsExactly("ENTRY_POINT:入口");
        assertThat(response.getAlternativeTypes()).containsExactly(QuestionType.BUSINESS_FLOW);
    }

    @Test
    void queryPlanFrom_nullOrMalformedJsonFallsBackToEmptyLists() {
        QueryPlan queryPlan = queryPlan(null, "not-json", "", null, "[\"NO_SUCH_TYPE\"]");

        QuestionResponse.QueryPlanResponse response = QuestionResponse.QueryPlanResponse.from(queryPlan, mapper);

        assertThat(response.getRewrittenQueries()).isEmpty();
        assertThat(response.getRecommendedTools()).isEmpty();
        assertThat(response.getRecommendedSkills()).isEmpty();
        assertThat(response.getMatchedSignals()).isEmpty();
        assertThat(response.getAlternativeTypes()).isEmpty();
    }

    private QueryPlan queryPlan(String rewrittenQueriesJson, String recommendedToolsJson,
                                String recommendedSkillsJson, String matchedSignalsJson,
                                String alternativeTypesJson) {
        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", 42L);

        QueryPlan queryPlan = new QueryPlan();
        ReflectionTestUtils.setField(queryPlan, "id", 7L);
        queryPlan.setQuestion(question);
        queryPlan.setType(QuestionType.ENTRY_POINT);
        queryPlan.setRewrittenQueriesJson(rewrittenQueriesJson);
        queryPlan.setRecommendedToolsJson(recommendedToolsJson);
        queryPlan.setRecommendedSkillsJson(recommendedSkillsJson);
        queryPlan.setConfidence(0.67);
        queryPlan.setMatchedSignalsJson(matchedSignalsJson);
        queryPlan.setAlternativeTypesJson(alternativeTypesJson);
        queryPlan.setReasoning("reason");
        return queryPlan;
    }
}
