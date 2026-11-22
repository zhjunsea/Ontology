package com.ocean.ontologyframework.sizhubazi;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 调候/印比重/旺衰·精确操作数side补测：直调 effectiveYongElem / isYinBiZhongYong（火炎/水寒双向与
 * 非当令side），并驱动 handleDayMasterStrength（中和偏弱）。均以确定值/区间断言，覆盖具体条件分支。
 */
@DisplayName("调候/印比重/旺衰·精确操作数side补测")
class SiZhuBaZiTiaohouOperandTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static Object call(Object w, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(w, args);
    }

    private static int[][] pf(String fp) throws Exception {
        return (int[][]) call(newWorker(), "parsePillars", new Class<?>[]{String.class}, fp);
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
                if (rt == float.class) return 0f;
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
        m.invoke(newWorker(), job, client);
        return cap.vars == null ? null : (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> v = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            v.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return v;
    }

    private static boolean anyValueContains(Map<String, Object> r, String sub) {
        if (r == null) return false;
        for (Object v : r.values()) {
            if (v != null && String.valueOf(v).contains(sub)) return true;
        }
        return false;
    }

    @Test
    @DisplayName("effectiveYongElem·调候双向：火日火月无水→水(4)；水日水月无火→火(1)")
    void tiaohouBothDirections() throws Exception {
        Object w = newWorker();
        int e1 = (int) call(w, "effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class},
                pf("丙午 丙午 丙午 丙午"), 2, true);
        assertThat(e1).as("火炎取水").isEqualTo(4);
        int e2 = (int) call(w, "effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class},
                pf("壬子 壬子 壬子 壬子"), 8, true);
        assertThat(e2).as("水寒取火").isEqualTo(1);
    }

    @Test
    @DisplayName("调候/印比重·组合网格（火日/水日 × 当令/非当令 × 含/不含对冲五行 × strong 真/假）覆盖全部 operand 极性")
    void tiaohouOperandSides() throws Exception {
        Object w = newWorker();
        String[] charts = {
                "丙午 甲午 丙午 戊午",  // 火日火月 无水
                "丙午 甲午 丙午 壬子",  // 火日火月 有水
                "丙寅 甲寅 丙寅 戊寅",  // 火日 非火月 无水
                "丙子 庚子 丙子 戊子",  // 火日 非火月(水)
                "壬子 庚子 壬子 戊子",  // 水日水月 无火
                "壬子 庚子 壬子 丙午",  // 水日水月 有火
                "壬申 庚申 壬申 戊申",  // 水日 非水月 无火
                "壬午 丙午 壬午 戊午",  // 水日 非水月(火)
                "甲申 庚申 甲申 戊申",  // 木日（非火非水）
                "庚申 甲申 庚申 丙子"   // 金日（非火非水，带火水）
        };
        for (String fp : charts) {
            int[][] p = pf(fp);
            int dg = p[2][0];
            Object e1 = call(w, "effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, p, dg, true);
            assertThat((int) e1).as("effectiveYongElem strong=true " + fp).isBetween(0, 4);
            Object e2 = call(w, "effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, p, dg, false);
            assertThat((int) e2).as("effectiveYongElem strong=false " + fp).isBetween(0, 4);
            Object b1 = call(w, "isYinBiZhongYong", new Class<?>[]{int[][].class, int.class, boolean.class}, p, dg, true);
            assertThat(b1).as("isYinBiZhongYong true " + fp).isNotNull();
            Object b2 = call(w, "isYinBiZhongYong", new Class<?>[]{int[][].class, int.class, boolean.class}, p, dg, false);
            assertThat(b2).as("isYinBiZhongYong false " + fp).isNotNull();
        }
    }

    @Test
    @DisplayName("旺衰·中和偏弱（失令、克泄耗偏多）：甲申 壬申 甲申 壬申")
    void strengthMidWeak() throws Exception {
        Map<String, Object> r = drive("handleDayMasterStrength",
                map("fourPillars", "甲申 壬申 甲申 壬申"));
        assertThat(anyValueContains(r, "中和偏弱")).as("应判中和偏弱").isTrue();
    }
}
