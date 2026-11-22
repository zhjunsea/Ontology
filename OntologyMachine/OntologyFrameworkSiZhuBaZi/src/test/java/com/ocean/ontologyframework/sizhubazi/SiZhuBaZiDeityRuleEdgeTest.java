package com.ocean.ontologyframework.sizhubazi;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 神煞起例·受控行补测：以定制 deity:DeityRule 行覆盖 matchDeityRule 的
 * 日柱/日干(多键·单键)/年支/日支/月支三合(多键·单键)/月支 各分支、各类跳过项，
 * 以及「本体在用但四柱未见命中」降级文案(L699)。
 */
@DisplayName("神煞起例·受控行补测（matchDeityRule 各基准分支 + 无命中降级文案）")
class SiZhuBaZiDeityRuleEdgeTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Map<String, String>> deityRows() {
        List<Map<String, String>> l = new ArrayList<>();
        l.add(row("id", "1", "bp", "日干", "bg", "甲/乙", "bh", "子/丑", "dn", "福星贵人", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "2", "bp", "日干", "bg", "甲", "bh", "子", "dn", "天乙贵人", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "3", "bp", "日干", "bg", "丙", "bh", "寅", "dn", "文昌贵人", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "4", "bp", "月支三合", "bg", "寅/卯", "bh", "午/子", "dn", "驿马", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "5", "bp", "月支三合", "bg", "巳", "bh", "亥", "dn", "驿马", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "6", "bp", "月支三合", "bg", "寅", "bh", "午", "dn", "将星", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "7", "bp", "月支", "bg", "寅", "bh", "丑", "dn", "华盖", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "8", "bp", "年支", "bg", "子", "bh", "寅", "dn", "月德贵人", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "9", "bp", "日支", "bg", "午", "bh", "卯", "dn", "将星", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "10", "bp", "日柱", "bg", "甲午", "bh", "", "dn", "金神", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "11", "bp", "日柱/年月", "bg", "甲午", "bh", "子", "dn", "应跳过A", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "12", "bp", "日柱", "bg", "甲子旬", "bh", "", "dn", "旬空类", "da", "", "cat", "", "src", "s"));
        l.add(row("id", "13", "bp", "日干", "bg", "甲", "bh", "子", "dn", "", "da", "", "cat", "", "src", "s"));
        return l;
    }

    private static SiZhuBaZiOntologyJobWorker workerWithDeityObda() throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String q = inv.getArgument(0);
            return q.contains("DeityRule") ? deityRows() : new ArrayList<Map<String, String>>();
        });
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    private static final class Cap implements Answer<Object> {
        Object vars;

        @Override
        public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) {
                vars = inv.getArgument(0);
            }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) return null;
            if (rt.isPrimitive()) {
                if (rt == long.class) return 0L;
                if (rt == int.class) return 0;
                if (rt == boolean.class) return false;
                if (rt == double.class) return 0d;
                return null;
            }
            try {
                return mock(rt, this);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private static Map<String, Object> drive(String handler, Map<String, Object> vars) throws Exception {
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(workerWithDeityObda(), job, client);
        return cap.vars == null ? null : (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> v = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            v.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return v;
    }

    @Test
    @DisplayName("神煞匹配·各基准分支（日干多/单键、月支三合多/单键、月支、年支、日支、日柱、跳过项）")
    void deityMatchBranches() throws Exception {
        Map<String, Object> r = drive("handleArrangeDeities", map("fourPillars", "甲子 丙寅 甲午 丁卯"));
        assertThat(r).isNotNull();
        String d = String.valueOf(r.get("deities"));
        assertThat(d).as("有命中，非空且非未见文案").isNotBlank();
        assertThat(d).doesNotContain("未见");
        assertThat(d).contains("命中神煞");
    }

    @Test
    @DisplayName("神煞·本体在用但四柱未见命中 → 降级文案（deity_rule 起例库）")
    void deityOntologyEmptyNoMatch() throws Exception {
        Map<String, Object> r = drive("handleArrangeDeities", map("fourPillars", "庚申 庚申 庚申 庚申"));
        assertThat(r).isNotNull();
        assertThat(String.valueOf(r.get("deities"))).as("deity_rule 起例库降级文案").contains("deity_rule 起例库");
    }
}
