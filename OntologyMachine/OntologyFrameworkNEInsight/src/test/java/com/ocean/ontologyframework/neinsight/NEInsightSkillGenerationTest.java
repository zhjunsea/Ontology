package com.ocean.ontologyframework.neinsight;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * summarize-insight · 洞察报告生成 SKILL（md）落盘验证（离线，纯 Openllet，无需引擎）。
 *
 * <p>直接以 mock 的 {@link ActivatedJob}/{@link JobClient} 驱动生产
 * {@link NEInsightOntologyJobWorker#handleSummarizeInsight}，断言其产出为一份「给大模型生成
 * 洞察报告的 SKILL」——以「角色设定 + 任务指令」为骨架，按「五看三定」逐节点给出
 * ①联网检索指令（检索关键词/信息源/需采集字段）、②数据加工与分析要求（+📚方法论来源）。
 * 应用口径：NEInsight 不再内嵌本体业务实例数据（内部知识基线不通用），各节点知识基线为
 * 本体逐节点方法论（core:InsightMethod），使大模型据此真正联网检索、加工、分析后生成洞察报告。
 */
@DisplayName("summarize-insight · 洞察 SKILL 落盘（离线）")
class NEInsightSkillGenerationTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("生成 skill/neinsight-<key>.md，围绕输入场景含角色设定/任务指令与内嵌五看三定洞察内容")
    void generatesInsightSkill() throws Exception {
        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        ReflectionTestUtils.setField(worker, "mainOntologyPath", "ontology/neinsight-all.owl");
        ReflectionTestUtils.setField(worker, "skillDir", tmp.toString());
        worker.init();

        String scenario = "面向双碳目标的新能源企业 IT 与云协同数据平台建设";

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(123L);
        when(job.getProcessInstanceKey()).thenReturn(123L);
        when(job.getBpmnProcessId()).thenReturn("NewEnergyInsightProcess");
        when(job.getVariablesAsMap()).thenReturn(Map.of("insightScenario", scenario));

        JobClient client = mock(JobClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

        worker.handleSummarizeInsight(job, client);

        Path skill = tmp.resolve("neinsight-123.md");
        assertThat(Files.exists(skill)).as("洞察 SKILL 应落盘: %s", skill).isTrue();

        String md = new String(Files.readAllBytes(skill), StandardCharsets.UTF_8);
        assertThat(md)
                .as("SKILL 应含「角色设定 + 任务指令」骨架")
                .contains("# 洞察报告生成 SKILL")
                .contains("## 一、角色设定")
                .contains("## 二、任务指令")
                .contains("战略洞察分析师");
        assertThat(md)
                .as("SKILL 应内嵌按 BPMN 流程逐步构建的五看三定洞察内容")
                .contains("## 三、洞察内容")
                .contains("按 BPMN 流程逐步构建")
                .contains("【五看】")
                .contains("节点 1 · 看行业/趋势")
                .contains("看市场/客户")
                .contains("看竞争")
                .contains("看自己")
                .contains("看机会")
                .contains("【三定】")
                .contains("节点 6 · 定战略控制点")
                .contains("定目标")
                .contains("定策略");
        assertThat(md)
                .as("SKILL 应围绕输入洞察场景组织，并标明流程起点与检索根域")
                .contains(scenario)
                .contains("3.2 输入洞察场景")
                .contains("洞察场景")
                .contains("检索根域")
                .contains("联合空间");
        assertThat(md)
                .as("角色设定应声明联网检索能力，任务指令应要求先联网采集再加工分析")
                .contains("联网检索能力")
                .contains("联网检索指令")
                .contains("数据加工与分析要求");
        assertThat(md)
                .as("每个节点应给出检索关键词/信息源/需采集字段")
                .contains("检索关键词")
                .contains("建议信息源")
                .contains("需采集的数据字段")
                .contains("🔎 联网检索指令")
                .contains("🧮 数据加工与分析要求");
        int nodeCount = md.split("🔎 联网检索指令", -1).length - 1;
        assertThat(nodeCount)
                .as("五看三定 8 个节点（5 看 + 3 定）均应含联网检索指令，实际 %d 处", nodeCount)
                .isGreaterThanOrEqualTo(8);
        int analysisCount = md.split("🧮 数据加工与分析要求", -1).length - 1;
        assertThat(analysisCount)
                .as("五看三定 8 个节点均应含数据加工与分析要求，实际 %d 处", analysisCount)
                .isGreaterThanOrEqualTo(8);
    }
}
