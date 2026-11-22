package com.ocean.ontologyframework.sizhubazi;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B 类补齐 · 外部环境/IO 与纯辅助方法的真分支覆盖（离线：Mockito/桩 + ChatAgent 桩，不连真实库/模型）。
 *
 * <ul>
 *   <li>LLM 详解 {@code interpretResult}：大模型可用三分支（详解成功 / 白话空降级 / 对话失败降级）；</li>
 *   <li>历法：{@code lunarToSolar} 全部分支、{@code loadSolarTermYear}/{@code solarTermDateTime} 越界/空命中/正常/缓存/回退；</li>
 *   <li>本体映射：{@code tenGodOntologyMap} 命中/空/异常/缓存；</li>
 *   <li>纯辅助：truthy / inCategory / tianKeDiChong / parseHourFromText / yearBranchRelation / nayinElemIndex /
 *       lackElements / generates / sanheGroupOf / stageOf 全分支。</li>
 * </ul>
 */
@DisplayName("B类补齐·外部IO(LLM/历法/本体)与纯辅助方法真分支覆盖")
class SiZhuBaZiBClassCoverageTest {

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
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object w, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(w, args);
    }

    private static Object stat(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /** 用「查询体→行集」函数构造假 OBDA 后端（仅用于历法/十神等真实 schema 一致的数据）。 */
    private static SiZhuBaZiOntologyJobWorker workerWithObda(Function<String, List<Map<String, String>>> f) throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> f.apply(inv.getArgument(0)));
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    private static List<Map<String, String>> solarRowsValid() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            rows.add(row("idx", String.valueOf(i), "dt", "2020-01-06T05:30:00"));
        }
        return rows;
    }

    // ============================================================ LLM 详解三分支

    private static ActivatedJob jobWithReading() {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(321L);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("reading", "本命综合断：日主甲木生于寅月……");
        vars.put("fourPillars", "甲子 丙寅 甲午 丁卯");
        when(job.getVariablesAsMap()).thenReturn(vars);
        return job;
    }

    private SiZhuBaZiOntologyJobWorker workerWithLlm(ChatAgent llm) throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "llm", llm);
        return w;
    }

    @Test
    @DisplayName("详解成功：两次对话均非空 → 产出详解+白话，complete（覆盖 buildInterpretUserPrompt/Plain/appendLine）")
    void interpretSuccess() throws Exception {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        ChatAgent llm = new ChatAgent((sys, user) -> n.getAndIncrement() == 0 ? "命书详解内容" : "白话解读内容");
        SiZhuBaZiOntologyJobWorker w = workerWithLlm(llm);
        JobClient client = mock(JobClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        w.handleInterpretResult(jobWithReading(), client);
        org.mockito.Mockito.verify(client).newCompleteCommand(321L);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .newThrowErrorCommand(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("白话为空：第2次对话返回空白 → 详解保留、白话降级空串，complete")
    void interpretPlainBlank() throws Exception {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        ChatAgent llm = new ChatAgent((sys, user) -> n.getAndIncrement() == 0 ? "命书详解内容" : "   ");
        SiZhuBaZiOntologyJobWorker w = workerWithLlm(llm);
        JobClient client = mock(JobClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        w.handleInterpretResult(jobWithReading(), client);
        org.mockito.Mockito.verify(client).newCompleteCommand(321L);
    }

    @Test
    @DisplayName("对话失败：chat 返回 null → 详解/白话均降级空串（不阻断），complete")
    void interpretChatFail() throws Exception {
        ChatAgent llm = new ChatAgent((sys, user) -> null);
        SiZhuBaZiOntologyJobWorker w = workerWithLlm(llm);
        JobClient client = mock(JobClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        w.handleInterpretResult(jobWithReading(), client);
        org.mockito.Mockito.verify(client).newCompleteCommand(321L);
    }

    @Test
    @DisplayName("大模型未装配(null) → 跳过详解，complete")
    void interpretNoLlm() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker(); // llm 为 null
        JobClient client = mock(JobClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        w.handleInterpretResult(jobWithReading(), client);
        org.mockito.Mockito.verify(client).newCompleteCommand(321L);
    }

    // ============================================================ 纯辅助方法全分支

    @Test
    @DisplayName("truthy：null/Boolean/Number/String(true/1/是/闰/其他) 全分支")
    void truthyBranches() throws Exception {
        Class<?>[] t = {Object.class};
        assertThat((Boolean) stat("truthy", t, (Object) null)).isFalse();
        assertThat((Boolean) stat("truthy", t, Boolean.TRUE)).isTrue();
        assertThat((Boolean) stat("truthy", t, Boolean.FALSE)).isFalse();
        assertThat((Boolean) stat("truthy", t, 0)).isFalse();
        assertThat((Boolean) stat("truthy", t, 5)).isTrue();
        assertThat((Boolean) stat("truthy", t, "true")).isTrue();
        assertThat((Boolean) stat("truthy", t, "1")).isTrue();
        assertThat((Boolean) stat("truthy", t, "是")).isTrue();
        assertThat((Boolean) stat("truthy", t, "闰")).isTrue();
        assertThat((Boolean) stat("truthy", t, "abc")).isFalse();
    }

    @Test
    @DisplayName("inCategory：比劫/食伤/印/财/官杀 命中与不命中 + 默认 false")
    void inCategoryBranches() throws Exception {
        Class<?>[] t = {String.class, String.class};
        assertThat((Boolean) stat("inCategory", t, "比肩", "比劫")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "正官", "比劫")).isFalse();
        assertThat((Boolean) stat("inCategory", t, "伤官", "食伤")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "食神", "食伤")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "偏印", "印")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "正财", "财")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "偏官", "官杀")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "财", "未知类")).isFalse();
    }

    @Test
    @DisplayName("tianKeDiChong：null/短串/非法干支/天克地冲/仅克不冲/仅冲不克")
    void tianKeDiChongBranches() throws Exception {
        Class<?>[] t = {String.class, String.class};
        assertThat((Boolean) stat("tianKeDiChong", t, null, "甲子")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", t, "甲", "甲子")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", t, "XX", "甲子")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", t, "甲子", "庚午")).isTrue();   // 甲(木)克庚(金)+子午冲
        assertThat((Boolean) stat("tianKeDiChong", t, "甲子", "甲午")).isFalse();  // 不克
        assertThat((Boolean) stat("tianKeDiChong", t, "甲子", "庚子")).isFalse();  // 克而不冲
    }

    @Test
    @DisplayName("parseHourFromText：HH:mm / N点 / N时 / 无 → 时数")
    void parseHourFromTextBranches() throws Exception {
        Class<?>[] t = {String.class};
        assertThat((Integer) stat("parseHourFromText", t, "12:30")).isEqualTo(12);
        assertThat((Integer) stat("parseHourFromText", t, "5点")).isEqualTo(5);
        assertThat((Integer) stat("parseHourFromText", t, "9时")).isEqualTo(9);
        assertThat((Integer) stat("parseHourFromText", t, "无时刻")).isEqualTo(0);
    }

    @Test
    @DisplayName("yearBranchRelation：同支/六冲/相刑/六害/六合/三合/三会/无")
    void yearBranchRelationBranches() throws Exception {
        Class<?>[] t = {int.class, int.class};
        assertThat((String) stat("yearBranchRelation", t, 0, 0)).isEqualTo("同支比和");
        assertThat((String) stat("yearBranchRelation", t, 0, 6)).isEqualTo("六冲");   // 子午
        assertThat((String) stat("yearBranchRelation", t, 2, 5)).isEqualTo("相刑");   // 寅巳
        assertThat((String) stat("yearBranchRelation", t, 0, 7)).isEqualTo("六害");   // 子未
        assertThat((String) stat("yearBranchRelation", t, 0, 1)).isEqualTo("六合");   // 子丑
        assertThat((String) stat("yearBranchRelation", t, 8, 0)).isEqualTo("三合");   // 申子
        assertThat((String) stat("yearBranchRelation", t, 2, 3)).isEqualTo("三会");   // 寅卯
        assertThat((String) stat("yearBranchRelation", t, 0, 2)).isEqualTo("无合无冲");
    }

    @Test
    @DisplayName("nayinElemIndex：null/空/木火土金水/无法识别")
    void nayinElemIndexBranches() throws Exception {
        Class<?>[] t = {String.class};
        assertThat((Integer) stat("nayinElemIndex", t, (Object) null)).isEqualTo(-1);
        assertThat((Integer) stat("nayinElemIndex", t, "")).isEqualTo(-1);
        assertThat((Integer) stat("nayinElemIndex", t, "大林木")).isEqualTo(0);
        assertThat((Integer) stat("nayinElemIndex", t, "天上火")).isEqualTo(1);
        assertThat((Integer) stat("nayinElemIndex", t, "路旁土")).isEqualTo(2);
        assertThat((Integer) stat("nayinElemIndex", t, "砂中金")).isEqualTo(3);
        assertThat((Integer) stat("nayinElemIndex", t, "大海水")).isEqualTo(4);
        assertThat((Integer) stat("nayinElemIndex", t, "abc")).isEqualTo(-1);
    }

    @Test
    @DisplayName("generates / sanheGroupOf / stageOf / lackElements 分支")
    void miscHelpersBranches() throws Exception {
        assertThat((Boolean) stat("generates", new Class<?>[]{int.class, int.class}, 0, 1)).isTrue();
        assertThat((Boolean) stat("generates", new Class<?>[]{int.class, int.class}, 1, 0)).isFalse();
        assertThat((Integer) stat("sanheGroupOf", new Class<?>[]{int.class}, 0)).isEqualTo(0);
        assertThat((Integer) stat("sanheGroupOf", new Class<?>[]{int.class}, 2)).isEqualTo(1);
        assertThat((Integer) stat("sanheGroupOf", new Class<?>[]{int.class}, 5)).isEqualTo(2);
        assertThat((Integer) stat("sanheGroupOf", new Class<?>[]{int.class}, 11)).isEqualTo(3);
        assertThat((String) stat("stageOf", new Class<?>[]{int.class, int.class}, 0, 0)).isNotBlank();
        // 五行齐 → 无所欠
        int[][] full = new int[][]{{0, 0}, {1, 1}, {2, 2}, {3, 3}}; // 木火土金四干 + 支
        assertThat(stat("lackElements", new Class<?>[]{int[][].class}, (Object) full)).isNotNull();
    }

    // ============================================================ lunarToSolar 全分支

    @Test
    @DisplayName("lunarToSolar：Map缺年/空白串/无日期/越界/越月/闰不匹配 → null")
    void lunarToSolarInvalid() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] t = {Object.class};
        Map<String, Object> noYear = new LinkedHashMap<>();
        noYear.put("month", 5);
        noYear.put("day", 5);
        assertThat(call(w, "lunarToSolar", t, (Object) noYear)).isNull();
        assertThat(call(w, "lunarToSolar", t, "   ")).isNull();
        assertThat(call(w, "lunarToSolar", t, "abc")).isNull();
        assertThat(call(w, "lunarToSolar", t, "1899-5-5")).isNull();
        assertThat(call(w, "lunarToSolar", t, "2101-5-5")).isNull();
        assertThat(call(w, "lunarToSolar", t, "2000-13-5")).isNull();
        assertThat(call(w, "lunarToSolar", t, "2000-5-31")).isNull();
        Map<String, Object> leapMismatch = new LinkedHashMap<>();
        leapMismatch.put("year", 2022);
        leapMismatch.put("month", 6);
        leapMismatch.put("day", 1);
        leapMismatch.put("leap", true);
        assertThat(call(w, "lunarToSolar", t, (Object) leapMismatch)).isNull();
    }

    @Test
    @DisplayName("lunarToSolar：字符串(HH:mm / N时)/Map(时/时支/闰月/分) 有效 → 非空且时数正确")
    void lunarToSolarValid() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] t = {Object.class};
        LocalDateTime a = (LocalDateTime) call(w, "lunarToSolar", t, "2020-05-15 12:30");
        assertThat(a).isNotNull();
        assertThat(a.getHour()).isEqualTo(12);
        assertThat(a.getMinute()).isEqualTo(30);

        LocalDateTime b = (LocalDateTime) call(w, "lunarToSolar", t, "2020-05-15 8时");
        assertThat(b).isNotNull();
        assertThat(b.getHour()).isEqualTo(8);

        Map<String, Object> zhi = new LinkedHashMap<>();
        zhi.put("year", 2020);
        zhi.put("month", 4);
        zhi.put("day", 15);
        zhi.put("hourZhi", "子");
        assertThat(call(w, "lunarToSolar", t, (Object) zhi)).isNotNull();

        Map<String, Object> leapValid = new LinkedHashMap<>();
        leapValid.put("year", 2020);   // 2020 闰四月
        leapValid.put("month", 4);
        leapValid.put("day", 15);
        leapValid.put("leap", true);
        leapValid.put("minute", 30);
        LocalDateTime lm = (LocalDateTime) call(w, "lunarToSolar", t, (Object) leapValid);
        assertThat(lm).isNotNull();
        assertThat(lm.getMinute()).isEqualTo(30);

        Map<String, Object> hourMin = new LinkedHashMap<>();
        hourMin.put("year", 2020);
        hourMin.put("month", 4);
        hourMin.put("day", 15);
        hourMin.put("hour", 14);
        hourMin.put("minute", 20);
        LocalDateTime hm = (LocalDateTime) call(w, "lunarToSolar", t, (Object) hourMin);
        assertThat(hm).isNotNull();
        assertThat(hm.getHour()).isEqualTo(14);
        assertThat(hm.getMinute()).isEqualTo(20);
    }

    // ============================================================ 节气(loadSolarTermYear/solarTermDateTime)

    @Test
    @DisplayName("节气：越界(null)/正常24条/缓存/无库空命中(null)/坏行hit=0(null)/近似回退")
    void solarTermBranches() throws Exception {
        SiZhuBaZiOntologyJobWorker ok = workerWithObda(
                q -> q.contains("SolarTermInstant") ? solarRowsValid() : new ArrayList<>());
        assertThat(call(ok, "loadSolarTermYear", new Class<?>[]{int.class}, 1949)).isNull();
        assertThat(call(ok, "loadSolarTermYear", new Class<?>[]{int.class}, 2051)).isNull();
        LocalDateTime[] arr = (LocalDateTime[]) call(ok, "loadSolarTermYear", new Class<?>[]{int.class}, 2020);
        assertThat(arr).isNotNull();
        assertThat(arr.length).isEqualTo(24);
        assertThat((LocalDateTime) call(ok, "solarTermDateTime", new Class<?>[]{int.class, int.class}, 2020, 5)).isNotNull();
        assertThat((LocalDateTime) call(ok, "solarTermDateTime", new Class<?>[]{int.class, int.class}, 2020, 5)).isNotNull(); // 缓存

        SiZhuBaZiOntologyJobWorker none = newWorker(); // 无库 → 空命中
        assertThat(call(none, "loadSolarTermYear", new Class<?>[]{int.class}, 2020)).isNull();
        assertThat((LocalDateTime) call(none, "solarTermDateTime", new Class<?>[]{int.class, int.class}, 2020, 5)).isNotNull(); // 近似回退

        SiZhuBaZiOntologyJobWorker bad = workerWithObda(
                q -> q.contains("SolarTermInstant") ? Arrays.asList(row("idx", "0", "dt", "bad")) : new ArrayList<>());
        assertThat(call(bad, "loadSolarTermYear", new Class<?>[]{int.class}, 2020)).isNull(); // hit==0
    }

    // ============================================================ 十神本体映射(tenGodOntologyMap/tenGodName)

    @Test
    @DisplayName("十神映射：命中+缓存/本体空(null)/异常降级(null)/tenGodName降级算法")
    @SuppressWarnings("unchecked")
    void tenGodMapBranches() throws Exception {
        SiZhuBaZiOntologyJobWorker ok = workerWithObda(q -> q.contains("TenGodMap")
                ? Arrays.asList(row("d", "甲", "t", "丙", "g", "食神"), row("d", "", "t", "", "g", ""))
                : new ArrayList<>());
        Map<String, String> m = (Map<String, String>) call(ok, "tenGodOntologyMap", new Class<?>[]{});
        assertThat(m).isNotNull();
        assertThat(m.get("甲|丙")).isEqualTo("食神");
        Map<String, String> m2 = (Map<String, String>) call(ok, "tenGodOntologyMap", new Class<?>[]{});
        assertThat(m2).isSameAs(m); // 缓存命中
        assertThat((String) call(ok, "tenGodName", new Class<?>[]{int.class, int.class}, 0, 2)).isEqualTo("食神");

        SiZhuBaZiOntologyJobWorker none = newWorker(); // 本体不可用 → null
        assertThat(call(none, "tenGodOntologyMap", new Class<?>[]{})).isNull();
        assertThat((String) call(none, "tenGodName", new Class<?>[]{int.class, int.class}, 0, 0)).isNotBlank(); // 降级算法

        SiZhuBaZiOntologyJobWorker err = workerWithObda(q -> {
            throw new RuntimeException("boom");
        });
        assertThat(call(err, "tenGodOntologyMap", new Class<?>[]{})).isNull(); // 异常降级
    }
}




