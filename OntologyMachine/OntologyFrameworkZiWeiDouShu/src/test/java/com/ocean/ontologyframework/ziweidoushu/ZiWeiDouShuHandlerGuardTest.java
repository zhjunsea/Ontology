package com.ocean.ontologyframework.ziweidoushu;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * 紫微斗数 JobWorker 节点级「守护/边界分支」单元测试（离线·Mockito，无需 Camunda/DB）。
 *
 * <p>端到端测试无法稳定制造验盘"轮次上限>5""缺 subjectTraits 失败"等分支，且历史上
 * {@code handleVerifyChart}/{@code fail} 指令覆盖为 0。本类以 Mockito 深桩模拟 {@link ActivatedJob}
 * /{@link JobClient}，直接驱动 {@code handleVerifyChart}，精准覆盖这些此前未测分支。
 */
@DisplayName("紫微斗数节点守护分支：验盘轮次上限 / 缺参失败 / 正常完成")
class ZiWeiDouShuHandlerGuardTest {

    private static final long JOB_KEY = 123L;
    private static final String CHART_BASE = "SOLAR=1990-05-15T05:00;LM=4;LD=21";

    private final ZiWeiDouShuOntologyJobWorker worker = new ZiWeiDouShuOntologyJobWorker();

    private ActivatedJob jobWith(Map<String, Object> vars) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(JOB_KEY);
        when(job.getVariablesAsMap()).thenReturn(vars);
        return job;
    }

    @Test
    @DisplayName("verifyChart 轮次上限：verifyRounds=5 → rounds=6>5，走「需人工复核」并 complete（不重排）")
    void verifyChartRoundsCapCompletes() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subjectTraits", "相貌清秀、性情温和");
        vars.put("verifyRounds", "5");
        vars.put("chartBase", CHART_BASE);
        ActivatedJob job = jobWith(vars);
        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);

        worker.handleVerifyChart(job, client);

        verify(client).newCompleteCommand(JOB_KEY);
        verify(client, never()).newThrowErrorCommand(anyLong());
    }

    @Test
    @DisplayName("verifyChart 缺 subjectTraits（空白）→ 抛错被捕获 → throwError（VERIFY_CHART_FAILED）")
    void verifyChartMissingTraitsThrowsError() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subjectTraits", "   ");
        vars.put("chartBase", CHART_BASE);
        ActivatedJob job = jobWith(vars);
        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);

        worker.handleVerifyChart(job, client);

        verify(client).newThrowErrorCommand(JOB_KEY);
        verify(client, never()).newCompleteCommand(anyLong());
    }

    @Test
    @DisplayName("verifyChart 正常（rounds=1）：完成不抛错，且成功 complete（不 throwError）")
    void verifyChartNormalCompletes() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subjectTraits", "相貌清秀、性情温和、已知 30 岁迁移");
        vars.put("chartBase", CHART_BASE);
        ActivatedJob job = jobWith(vars);
        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);

        assertThatCode(() -> worker.handleVerifyChart(job, client)).doesNotThrowAnyException();
        verify(client).newCompleteCommand(JOB_KEY);
        verify(client, never()).newThrowErrorCommand(anyLong());
    }
}
