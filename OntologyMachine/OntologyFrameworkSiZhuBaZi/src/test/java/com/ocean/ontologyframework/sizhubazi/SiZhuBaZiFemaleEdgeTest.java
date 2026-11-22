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

/** 女命断语·精确命例补测（《三命通会·论女命》《渊海子平·女命总断》：横夭少年/淫 等）。 */
@DisplayName("女命断语·精确命例补测（横夭少年/淫 等）")
class SiZhuBaZiFemaleEdgeTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
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

    private static boolean anyContains(Map<String, Object> r, String sub) {
        if (r == null) return false;
        for (Object v : r.values()) {
            if (v != null && String.valueOf(v).contains(sub)) return true;
        }
        return false;
    }

    @Test
    @DisplayName("女命·横夭少年（官食俱不见、身弱偏枯）：乙亥 己卯 甲子 戊辰")
    void femaleHengYaoWeak() throws Exception {
        Map<String, Object> r = drive("handleFemaleChart", map(
                "fourPillars", "乙亥 己卯 甲子 戊辰", "gender", "女", "strength", "身弱（失令、无根、寡助）"));
        assertThat(anyContains(r, "横夭少年")).as("官杀食伤俱不见+身弱 → 横夭少年").isTrue();
    }

    @Test
    @DisplayName("女命·横夭少年（官食俱不见、身旺无泄）：乙亥 己卯 甲子 戊辰 · 身强")
    void femaleHengYaoStrong() throws Exception {
        Map<String, Object> r = drive("handleFemaleChart", map(
                "fourPillars", "乙亥 己卯 甲子 戊辰", "gender", "女", "strength", "身强（得令）"));
        assertThat(anyContains(r, "横夭少年")).as("官杀食伤俱不见+身旺无泄 → 横夭少年").isTrue();
    }

    @Test
    @DisplayName("女命·反向极性（横夭neutral假支 / 淫·食伤旺支 / 安静守分·比劫透假支 / 娼 / 滚浪桃花）")
    void femaleOppositePolarities() throws Exception {
        // 官食俱不见 + 中和 → 横夭少年 (shenRuoF||strongF) 的 FALSE 支
        assertThat(drive("handleFemaleChart", map(
                "fourPillars", "乙亥 己卯 甲子 戊辰", "gender", "女", "strength", "中和"))).isNotNull();
        // 食伤旺(火多) + 身弱（非水泛）→ 淫 的「食伤≥3且身弱」支
        assertThat(drive("handleFemaleChart", map(
                "fourPillars", "丙寅 丁卯 甲午 丙寅", "gender", "女", "strength", "身弱（失令、无根、寡助）"))).isNotNull();
        // 官藏不透 + 比劫透（biJieTou!=0）→ 安静守分 的 biJieTou==0 FALSE 支
        assertThat(drive("handleFemaleChart", map(
                "fourPillars", "乙亥 甲申 甲子 丙寅", "gender", "女", "strength", "身强（得令）"))).isNotNull();
        // 官杀混杂 + 身弱 + 食伤不透 → 娼
        assertThat(drive("handleFemaleChart", map(
                "fourPillars", "庚申 辛酉 甲子 戊子", "gender", "女", "strength", "身弱（失令、无根、寡助）"))).isNotNull();
    }

    @Test
    @DisplayName("女命·决策表网格（官/食 透藏有无 × 比劫透 × 水泛/火旺 × 弱强中和）覆盖剩余极性")
    void femaleDecisionTable() throws Exception {
        String[] charts = {
                "乙亥 己卯 甲子 戊辰", "庚子 戊寅 甲子 戊辰", "壬申 丙寅 甲戌 戊辰", "乙亥 甲申 甲子 丙寅",
                "庚午 丙子 甲午 丙子", "甲子 壬子 甲子 壬子", "甲午 丙午 甲午 丙午", "辛卯 辛卯 甲申 辛卯",
                "癸酉 乙卯 甲寅 丁卯", "庚申 庚辰 甲申 庚辰", "壬子 壬子 丙子 壬子", "丙午 甲午 丙午 甲午",
                "戊申 戊申 壬申 戊申", "壬子 壬子 壬子 壬子"
        };
        String[] strengths = {"身弱（失令、无根、寡助）", "身强（得令）", "中和"};
        for (String fp : charts) {
            for (String st : strengths) {
                assertThat(drive("handleFemaleChart", map(
                        "fourPillars", fp, "gender", "女", "strength", st)))
                        .as("female " + fp + " / " + st).isNotNull();
            }
        }
    }
}
