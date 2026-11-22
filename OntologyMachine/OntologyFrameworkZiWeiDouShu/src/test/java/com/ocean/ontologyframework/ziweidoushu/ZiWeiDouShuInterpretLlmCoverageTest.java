package com.ocean.ontologyframework.ziweidoushu;

import com.ocean.utilities.ChatAgent;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * 命书详解节点 {@code interpretResult} 的「大模型可用」分支覆盖（离线·Mockito + ChatAgent 桩）。
 *
 * <p>正常盘/无 key 场景下该节点走"跳过/降级"分支，{@code buildInterpretUserPrompt}、
 * {@code buildInterpretPlainUserPrompt}、{@code appendLine} 从不执行（JaCoCo 指令覆盖为 0）。
 * 本类以 {@link ChatAgent} 桩函数（{@link ChatAgent#ChatAgent(java.util.function.BiFunction)}）
 * 注入"可用大模型"，覆盖详解成功、白话为空降级、对话失败降级三条分支。
 */
@DisplayName("命书详解 interpretResult：大模型可用三分支（详解成功/白话空/对话失败）")
class ZiWeiDouShuInterpretLlmCoverageTest {

    private static final long JOB_KEY = 321L;

    private ActivatedJob jobWithReading() {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(JOB_KEY);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("reading", "本命综合断：命宫天同，格局中格……");
        vars.put("chartSkeleton", "命宫=亥;身宫=丑");
        vars.put("patternResult", "成格：机月同梁");
        when(job.getVariablesAsMap()).thenReturn(vars);
        return job;
    }

    private ZiWeiDouShuOntologyJobWorker workerWithLlm(ChatAgent llm) throws Exception {
        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField("llm");
        f.setAccessible(true);
        f.set(w, llm);
        return w;
    }

    @Test
    @DisplayName("详解成功：两次对话均返回非空 → 产出详解+白话，complete")
    void interpretSuccess() throws Exception {
        AtomicInteger n = new AtomicInteger();
        ChatAgent llm = new ChatAgent((sys, user) -> n.getAndIncrement() == 0 ? "命书详解内容" : "白话解读内容");
        ZiWeiDouShuOntologyJobWorker w = workerWithLlm(llm);
        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
        w.handleInterpretResult(jobWithReading(), client);
        verify(client).newCompleteCommand(JOB_KEY);
        verify(client, never()).newThrowErrorCommand(anyLong());
    }

    @Test
    @DisplayName("白话为空：第 2 次对话返回空白 → 详解保留、白话降级空串，complete")
    void interpretPlainBlank() throws Exception {
        AtomicInteger n = new AtomicInteger();
        ChatAgent llm = new ChatAgent((sys, user) -> n.getAndIncrement() == 0 ? "命书详解内容" : "  ");
        ZiWeiDouShuOntologyJobWorker w = workerWithLlm(llm);
        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
        w.handleInterpretResult(jobWithReading(), client);
        verify(client).newCompleteCommand(JOB_KEY);
    }

    @Test
    @DisplayName("对话失败：chat 返回 null → 详解/白话均降级空串（不阻断），complete")
    void interpretChatFail() throws Exception {
        ChatAgent llm = new ChatAgent((sys, user) -> null);
        ZiWeiDouShuOntologyJobWorker w = workerWithLlm(llm);
        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
        w.handleInterpretResult(jobWithReading(), client);
        verify(client).newCompleteCommand(JOB_KEY);
        verify(client, never()).newThrowErrorCommand(anyLong());
    }
}
