package com.ocean.ontologyframework.neinsight;

import com.ocean.utilities.LlmClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * analyze-input · 入口「输入分析」节点离线验证（不连引擎、不发起真实 HTTP）。
 *
 * <p>通过给生产 {@link NEInsightOntologyJobWorker} 注入 {@link LlmClient} 桩函数，
 * 覆盖：①大模型可用（含 ```json 代码块包裹）时的结构化抽取；②大模型不可用/非 JSON 返回时的
 * 确定性降级（<b>不猜测业务</b>：仅取场景原文、其余字段留空 → 触发追问）。
 * 断言产出 {@code refinedScenario} / {@code inputIdentity} / {@code inputAnalysisSource} /
 * {@code inputMissingFields} 等流程变量。
 */
@DisplayName("analyze-input · 入口输入分析（离线）")
class NEInsightInputAnalysisTest {

    private static final String RAW = "面向双碳目标的新能源电网企业 IT 与云协同数据平台建设";

    @Test
    @DisplayName("大模型可用：抽取身份/场景并产出 refinedScenario（作为后续 SKILL 输入），必要信息齐全")
    void llmUsedExtractsStructuredInfo() {
        String json = "{\"identity\":\"电网运营企业\",\"scenario\":\"电网企业双碳目标下 IT 与云协同数据平台建设\","
                + "\"industry\":\"新能源\",\"customer\":\"电网运营企业\",\"objective\":\"数据平台建设\","
                + "\"region\":\"\",\"timeframe\":\"\",\"keyEntities\":[\"双碳\",\"云协同\"],\"confidence\":0.9}";
        LlmClient llm = new LlmClient((sys, user) -> "```json\n" + json + "\n```");

        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        ReflectionTestUtils.setField(worker, "llm", llm);

        Map<String, Object> out = run(worker);

        assertThat(out.get("inputAnalysisSource")).isEqualTo("LLM");
        assertThat(out.get("refinedScenario"))
                .isEqualTo("电网企业双碳目标下 IT 与云协同数据平台建设");
        assertThat(out.get("inputIdentity")).isEqualTo("电网运营企业");
        assertThat(out.get("inputMissingFields")).as("必要信息齐全").isEqualTo(List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = (Map<String, Object>) out.get("inputAnalysisResult");
        assertThat(analysis)
                .containsEntry("scenario", "电网企业双碳目标下 IT 与云协同数据平台建设")
                .containsEntry("industry", "新能源");
    }

    @Test
    @DisplayName("大模型不可用：降级为确定性提取（仅取原文、不猜测业务），缺失必要信息被标记")
    void llmUnavailableFallsBackToHeuristic() {
        LlmClient llm = new LlmClient();
        llm.configure(false, "", "", "stub", 5);

        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        ReflectionTestUtils.setField(worker, "llm", llm);

        Map<String, Object> out = run(worker);

        assertThat(out.get("inputAnalysisSource")).isEqualTo("HEURISTIC");
        assertThat(out.get("refinedScenario")).isEqualTo(RAW);
        assertThat(out.get("inputIdentity")).as("降级不得猜测业务身份").isEqualTo("");
        assertThat(out.get("inputMissingFields")).as("缺失必要信息应被标记以触发追问")
                .isEqualTo(List.of("identity", "industry", "objective"));
    }

    @Test
    @DisplayName("大模型返回非 JSON：降级为确定性提取，不抛异常")
    void llmReturnsGarbageFallsBack() {
        LlmClient llm = new LlmClient((sys, user) -> "抱歉，我无法解析这段输入。");

        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        ReflectionTestUtils.setField(worker, "llm", llm);

        Map<String, Object> out = run(worker);

        assertThat(out.get("inputAnalysisSource")).isEqualTo("HEURISTIC");
        assertThat(out.get("refinedScenario")).isEqualTo(RAW);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) out.get("inputMissingFields");
        assertThat(missing).contains("identity", "industry", "objective");
    }

    @Test
    @DisplayName("大模型返回 JSON 但必要信息不全：标记缺失字段以触发追问")
    void llmPartialJsonMarksMissing() {
        String json = "{\"identity\":\"方案提供商\",\"scenario\":\"某行业客户数据平台建设\","
                + "\"industry\":\"\",\"objective\":\"\",\"customer\":\"\",\"region\":\"\",\"timeframe\":\"\","
                + "\"keyEntities\":[],\"confidence\":0.4}";
        LlmClient llm = new LlmClient((sys, user) -> json);

        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        ReflectionTestUtils.setField(worker, "llm", llm);

        Map<String, Object> out = run(worker);

        assertThat(out.get("inputAnalysisSource")).isEqualTo("LLM");
        assertThat(out.get("inputMissingFields")).isEqualTo(List.of("industry", "objective"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(NEInsightOntologyJobWorker worker) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(7L);
        when(job.getVariablesAsMap()).thenReturn(Map.of("insightScenario", RAW));

        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
        // 预取 deep-stub 完成命令构建器（同一 key 会被缓存复用），供随后 verify 捕获 variables 参数
        var completeStep = client.newCompleteCommand(7L);
        worker.handleAnalyzeInput(job, client);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(completeStep).variables(cap.capture());
        return cap.getValue();
    }
}
