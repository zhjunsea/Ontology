package com.ocean.ontologyframework.neinsight.analyze;

import com.ocean.utilities.LlmClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InputAnalysis} 纯函数单元测试（离线，无引擎 / 无网络）。
 *
 * <p>覆盖：必要信息齐全性判定、缺失字段与追问生成、客户显式字段覆盖、确定性降级，
 * 以及「与业务无关」守卫（提示词/降级不得内置任何具体行业/身份词）。
 */
@DisplayName("InputAnalysis · 输入分析通用能力（离线）")
public class InputAnalysisTest {

    private static final List<String> BUSINESS_WORDS = List.of(
            "新能源", "风电", "风能", "光伏", "储能", "氢能", "电力", "电网", "发电", "整机", "园区", "双碳");

    @Test
    @DisplayName("客户已显式提供全部必要信息：直接放行（source=PROVIDED），不调用大模型")
    void providedCompleteIsReady() {
        LlmClient llm = new LlmClient((sys, user) -> {
            throw new AssertionError("必要信息齐全时不应调用大模型");
        });

        Map<String, Object> in = Map.of(
                "insightScenario", "某场景",
                "scenario", "某行业客户数据平台建设",
                "identity", "方案提供商",
                "industry", "智能制造",
                "objective", "评估市场机会");

        InputAnalysis.Result r = InputAnalysis.analyze(llm, in);

        assertThat(r.ready()).isTrue();
        assertThat(r.missingFields()).isEmpty();
        assertThat(r.source()).isEqualTo("PROVIDED");
        assertThat(r.questions()).isEmpty();
        assertThat(r.analysis()).containsEntry("industry", "智能制造");
    }

    @Test
    @DisplayName("缺少必要信息：标记缺失并生成一一对应的追问")
    void missingRequiredProducesQuestions() {
        InputAnalysis.Result r = InputAnalysis.analyze(null, Map.of("insightScenario", "一段自由描述"));

        assertThat(r.ready()).isFalse();
        assertThat(r.source()).isEqualTo("HEURISTIC");
        assertThat(r.missingFields()).isEqualTo(List.of("identity", "industry", "objective"));
        assertThat(r.questions()).hasSize(r.missingFields().size());
        assertThat(r.questions()).allSatisfy(q -> assertThat(q).isNotBlank());
        assertThat(r.analysis()).containsEntry("scenario", "一段自由描述");
    }

    @Test
    @DisplayName("大模型返回 JSON：解析为结构化信息，缺失项仍被标记")
    void parsesLlmJson() {
        LlmClient llm = new LlmClient((sys, user) ->
                "```json\n{\"identity\":\"采购方\",\"scenario\":\"S\",\"industry\":\"I\","
                        + "\"objective\":\"\",\"keyEntities\":[\"k\"],\"confidence\":0.7}\n```");

        InputAnalysis.Result r = InputAnalysis.analyze(llm, Map.of("insightScenario", "raw"));

        assertThat(r.source()).isEqualTo("LLM");
        assertThat(r.missingFields()).isEqualTo(List.of("objective"));
        assertThat(r.analysis()).containsEntry("identity", "采购方");
        assertThat(r.analysis()).containsEntry("objective", "");
    }

    @Test
    @DisplayName("客户显式字段覆盖抽取结果（避免大模型丢失客户已确认信息）")
    void explicitFieldsOverrideExtraction() {
        LlmClient llm = new LlmClient((sys, user) ->
                "{\"identity\":\"模型给的\",\"scenario\":\"模型给的场景\",\"industry\":\"模型给的行业\",\"objective\":\"x\"}");

        InputAnalysis.Result r = InputAnalysis.analyze(llm, Map.of(
                "insightScenario", "raw",
                "identity", "客户确认的身份"));

        assertThat(r.analysis()).containsEntry("identity", "客户确认的身份");
        assertThat(r.ready()).isTrue();
    }

    @Test
    @DisplayName("与业务无关守卫：提示词与降级均不得内置任何具体行业/身份词")
    void promptAndFallbackAreBusinessAgnostic() {
        for (String w : BUSINESS_WORDS) {
            assertThat(InputAnalysis.SYSTEM_PROMPT)
                    .as("系统提示词不得包含业务词「%s」", w).doesNotContain(w);
        }
        Map<String, Object> fb = InputAnalysis.analyze(null, Map.of("insightScenario", "任意行业任意身份的输入")).analysis();
        assertThat(fb).containsEntry("identity", "").containsEntry("industry", "").containsEntry("objective", "");
    }
}
