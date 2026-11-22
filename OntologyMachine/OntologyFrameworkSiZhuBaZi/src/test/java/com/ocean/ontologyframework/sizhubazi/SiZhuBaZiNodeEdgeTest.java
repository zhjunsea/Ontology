package com.ocean.ontologyframework.sizhubazi;

import com.ocean.utilities.ChatAgent;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 节点级分支：LLM 命书详解（interpretResult）与起运依据（buildPilotBasis）。 */
@DisplayName("节点分支·命书详解LLM/起运依据")
class SiZhuBaZiNodeEdgeTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static void setField(Object t, String n, Object v) throws Exception {
        Class<?> c = t.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(n); f.setAccessible(true); f.set(t, v); return; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(n);
    }

    private static final class Cap implements Answer<Object> {
        Object vars;
        @Override public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) { vars = inv.getArgument(0); }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) return null;
            if (rt.isPrimitive()) { return rt == long.class ? (Object) 0L : rt == int.class ? (Object) 0
                    : rt == boolean.class ? (Object) false : rt == double.class ? (Object) 0d
                    : rt == float.class ? (Object) 0f : (Object) null; }
            try { return mock(rt, this); } catch (Throwable t) { return null; }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> drive(SiZhuBaZiOntologyJobWorker w, String handler, Map<String, Object> vars) throws Exception {
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        return cap.vars == null ? null : (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    private static SiZhuBaZiOntologyJobWorker workerWithLlm(ChatAgent llm) throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "llm", llm);
        return w;
    }

    private static Map<String, Object> readingVars(String reading) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("reading", reading);
        v.put("fourPillars", "庚午 辛巳 庚辰 壬午");
        return v;
    }

    @Test
    @DisplayName("interpretResult：LLM 可用→详解+白话均产出")
    void interpretSuccess() throws Exception {
        AtomicInteger n = new AtomicInteger();
        ChatAgent llm = new ChatAgent((sys, user) -> n.getAndIncrement() == 0 ? "命书详解内容" : "白话解读内容");
        SiZhuBaZiOntologyJobWorker w = workerWithLlm(llm);
        Map<String, Object> r = drive(w, "handleInterpretResult", readingVars("命书正文：格成财旺。"));
        assertThat(r).isNotNull();
        assertThat((String) r.get("aiInterpretation")).isEqualTo("命书详解内容");
        assertThat((String) r.get("aiInterpretationPlain")).isEqualTo("白话解读内容");
    }

    @Test
    @DisplayName("interpretResult：白话为空 → 白话降级空串（不阻断）")
    void interpretPlainBlank() throws Exception {
        AtomicInteger n = new AtomicInteger();
        ChatAgent llm = new ChatAgent((sys, user) -> n.getAndIncrement() == 0 ? "详解" : "   ");
        SiZhuBaZiOntologyJobWorker w = workerWithLlm(llm);
        Map<String, Object> r = drive(w, "handleInterpretResult", readingVars("命书正文。"));
        assertThat((String) r.get("aiInterpretation")).isEqualTo("详解");
        assertThat((String) r.get("aiInterpretationPlain")).isEqualTo("");
    }

    @Test
    @DisplayName("interpretResult：chat 返回 null → 详解/白话均降级空串")
    void interpretChatFail() throws Exception {
        ChatAgent llm = new ChatAgent((sys, user) -> null);
        SiZhuBaZiOntologyJobWorker w = workerWithLlm(llm);
        Map<String, Object> r = drive(w, "handleInterpretResult", readingVars("命书正文。"));
        assertThat((String) r.get("aiInterpretation")).isEqualTo("");
        assertThat((String) r.get("aiInterpretationPlain")).isEqualTo("");
    }

    @Test
    @DisplayName("interpretResult：LLM 不可用 / reading 为空 → 跳过（降级空串）")
    void interpretUnavailableOrBlank() throws Exception {
        SiZhuBaZiOntologyJobWorker w1 = workerWithLlm(new ChatAgent());
        Map<String, Object> r1 = drive(w1, "handleInterpretResult", readingVars("命书正文。"));
        assertThat((String) r1.get("aiInterpretation")).isEqualTo("");
        ChatAgent avail = new ChatAgent((sys, user) -> "x");
        SiZhuBaZiOntologyJobWorker w2 = workerWithLlm(avail);
        Map<String, Object> r2 = drive(w2, "handleInterpretResult", readingVars("   "));
        assertThat((String) r2.get("aiInterpretation")).isEqualTo("");
    }

    @Test
    @DisplayName("buildPilotBasis：null → 空；顺/逆行得「起运依据」")
    void pilotBasis() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Method m = WORKER.getDeclaredMethod("buildPilotBasis", LocalDateTime.class, String.class, double.class);
        m.setAccessible(true);
        assertThat((String) m.invoke(w, null, "顺", 5.5)).isEqualTo("");
        String shun = (String) m.invoke(w, LocalDateTime.of(1990, 5, 15, 10, 30), "顺", 5.5);
        assertThat(shun).contains("【起运依据】");
        String ni = (String) m.invoke(w, LocalDateTime.of(1990, 5, 15, 10, 30), "逆", 5.5);
        assertThat(ni).contains("【起运依据】");
    }

    @Test
    @DisplayName("interpretResult：详解返回空白 → L2665 假侧（详解降级空串）")
    void interpretFirstBlank() throws Exception {
        ChatAgent llm = new ChatAgent((sys, user) -> "   ");
        SiZhuBaZiOntologyJobWorker w = workerWithLlm(llm);
        Map<String, Object> r = drive(w, "handleInterpretResult", readingVars("命书正文。"));
        assertThat((String) r.get("aiInterpretation")).isEqualTo("");
        assertThat((String) r.get("aiInterpretationPlain")).isEqualTo("");
    }

    @Test
    @DisplayName("interpretResult：详解正常、白话返回 null → L2669 假侧（白话降级空串）")
    void interpretPlainNull() throws Exception {
        AtomicInteger n = new AtomicInteger();
        ChatAgent llm = new ChatAgent((sys, user) -> n.getAndIncrement() == 0 ? "详解" : null);
        SiZhuBaZiOntologyJobWorker w = workerWithLlm(llm);
        Map<String, Object> r = drive(w, "handleInterpretResult", readingVars("命书正文。"));
        assertThat((String) r.get("aiInterpretation")).isEqualTo("详解");
        assertThat((String) r.get("aiInterpretationPlain")).isEqualTo("");
    }
}
