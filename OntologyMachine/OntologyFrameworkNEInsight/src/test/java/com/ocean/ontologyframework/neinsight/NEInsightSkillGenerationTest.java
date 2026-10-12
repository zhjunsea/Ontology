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
 * 洞察报告的 SKILL」——以「角色设定 + 任务指令」为骨架，并内嵌本次流程经本体 TBox「五看三定」
 * 维度检索 ABox 实例数据所得的洞察内容。
 */
@DisplayName("summarize-insight · 洞察 SKILL 落盘（离线）")
class NEInsightSkillGenerationTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("生成 skill/neinsight-<key>.md，含角色设定/任务指令与内嵌五看三定洞察内容")
    void generatesInsightSkill() throws Exception {
        NEInsightOntologyJobWorker worker = new NEInsightOntologyJobWorker();
        ReflectionTestUtils.setField(worker, "mainOntologyPath", "ontology/neinsight-all.owl");
        ReflectionTestUtils.setField(worker, "skillDir", tmp.toString());
        worker.init();

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(123L);
        when(job.getProcessInstanceKey()).thenReturn(123L);
        when(job.getBpmnProcessId()).thenReturn("NewEnergyInsightProcess");
        when(job.getVariablesAsMap()).thenReturn(Map.of("insightDomain", "COMBINED"));

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
                .as("SKILL 应含流程起点域扫描与 ABox 实例数据（联合域）")
                .contains("联合空间")
                .contains("洞察域扫描")
                .contains("domainBaseline");
    }
}
