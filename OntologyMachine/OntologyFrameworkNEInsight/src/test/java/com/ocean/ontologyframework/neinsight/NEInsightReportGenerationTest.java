package com.ocean.ontologyframework.neinsight;

import com.ocean.utilities.LlmClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * generate-report · 洞察报告生成（离线，无需引擎）。
 *
 * <p>以 mock 的 {@link ActivatedJob}/{@link JobClient} 直接驱动生产
 * {@link NEInsightOntologyJobWorker#handleGenerateReport}：读取汇总节点产出的洞察 SKILL，
 * 导入大模型生成《洞察报告》并落盘 {@code report/neinsight-<key>-洞察报告.md}，回写
 * {@code insightReportPath}；大模型不可用时降级为直接落盘 SKILL 内容。
 */
@DisplayName("generate-report · 洞察报告生成落盘（离线）")
class NEInsightReportGenerationTest {

    private static final long KEY = 123L;
    private static final String SCENARIO = "面向双碳目标的新能源企业 IT 与云协同数据平台建设";

    @TempDir
    Path tmp;

    @Test
    @DisplayName("P-1 大模型可用：据 SKILL 生成报告并落盘 neinsight-<key>-洞察报告.md，回写 insightReportPath")
    void generatesReportViaLlm() throws Exception {
        String skill = "# 洞察报告生成 SKILL\n\n> 测试用 SKILL 内容\n";
        Files.write(tmp.resolve("neinsight-" + KEY + ".md"), skill.getBytes(StandardCharsets.UTF_8));

        LlmClient llm = new LlmClient((sys, user) -> "# 洞察报告\n\n> 由大模型据 SKILL 生成\n");
        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        ReflectionTestUtils.setField(worker, "llm", llm);
        ReflectionTestUtils.setField(worker, "skillDir", tmp.toString());
        ReflectionTestUtils.setField(worker, "reportDir", tmp.toString());

        Map<String, Object> out = run(worker);

        Path report = tmp.resolve("neinsight-" + KEY + "-洞察报告.md");
        assertThat(Files.exists(report)).as("洞察报告应落盘: %s", report).isTrue();
        assertThat(Files.readString(report, StandardCharsets.UTF_8))
                .as("报告内容应为大模型输出")
                .contains("# 洞察报告")
                .contains("由大模型据 SKILL 生成");
        assertThat(out.get("insightReportPath"))
                .as("应回写报告路径")
                .isEqualTo(report.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("B-1 大模型不可用：降级为直接落盘 SKILL 内容，流程不阻断")
    void fallsBackToSkillContentWhenLlmUnavailable() throws Exception {
        String skill = "# 洞察报告生成 SKILL\n\n> 降级时应原样落盘\n";
        Files.write(tmp.resolve("neinsight-" + KEY + ".md"), skill.getBytes(StandardCharsets.UTF_8));

        LlmClient llm = new LlmClient();
        llm.configure(false, "", "", "stub", 5);
        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        ReflectionTestUtils.setField(worker, "llm", llm);
        ReflectionTestUtils.setField(worker, "skillDir", tmp.toString());
        ReflectionTestUtils.setField(worker, "reportDir", tmp.toString());

        Map<String, Object> out = run(worker);

        Path report = tmp.resolve("neinsight-" + KEY + "-洞察报告.md");
        assertThat(Files.exists(report)).as("降级也应落盘报告: %s", report).isTrue();
        assertThat(Files.readString(report, StandardCharsets.UTF_8))
                .as("降级应直接落盘 SKILL 内容")
                .isEqualTo(skill);
        assertThat(out.get("insightReportPath")).as("仍应回写报告路径").isNotNull();
    }

    @Test
    @DisplayName("P-2 有联网检索结果：user 提示词含「联网检索结果」区块与检索片段")
    void promptIncludesRetrieval() {
        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        String retrieval = "### 节点 `scan-industry-trend` 联网检索结果\n\n- 标题 —— https://x.example ：摘要\n";
        String user = ReflectionTestUtils.invokeMethod(worker, "buildReportUserPrompt", "场景X", "SKILL正文", retrieval);
        assertThat(user).isNotNull()
                .contains("洞察场景：场景X")
                .contains("SKILL正文")
                .contains("## 联网检索结果")
                .contains("scan-industry-trend")
                .contains("https://x.example")
                .contains("标注来源 URL");
    }

    @Test
    @DisplayName("P-3 无联网检索结果：提示词标注「未能获取联网检索结果」且不含检索区块")
    void promptWithoutRetrieval() {
        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        String user = ReflectionTestUtils.invokeMethod(worker, "buildReportUserPrompt", "场景X", "SKILL正文", "");
        assertThat(user).isNotNull()
                .contains("未能获取联网检索结果")
                .doesNotContain("## 联网检索结果");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(NEInsightOntologyJobWorker worker) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(KEY);
        when(job.getProcessInstanceKey()).thenReturn(KEY);
        when(job.getBpmnProcessId()).thenReturn("NewEnergyInsightProcess");
        when(job.getVariablesAsMap()).thenReturn(Map.of("insightScenario", SCENARIO));

        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
        var completeStep = client.newCompleteCommand(KEY);
        worker.handleGenerateReport(job, client);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(completeStep).variables(cap.capture());
        return cap.getValue();
    }
}
