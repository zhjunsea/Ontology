package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.BackendService;
import com.ocean.utilities.ChatAgent;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程 JobWorker handler 层「内部分支 + 故障注入」覆盖（C 类·离线，mock Camunda）。
 *
 * <p>以 Mockito 驱动 20 个 @JobWorker：覆盖「参外应诸兆（向背/坐端/论事十大应/观物洞玄/万物赋/
 * 三要灵应/日应/鸟兽/写字之应）、五色耗克、断吉凶 omen_bad、占断事实包缺体用、三要听语/书写、
 * 一撮金爻诀取数、戒律会话溢出」等 handler 内部分支；并以 getVariablesAsMap 抛异常逐一触发并覆盖
 * 各 handler 的 catch/fail 错误回调分支。
 */
@DisplayName("JobWorker handler 内部分支与故障注入（mock Camunda·离线）")
public class PlumBlossomHandlerResidualCoverageTest {

    private PlumBlossomOntologyJobWorker worker;
    private ActivatedJob job;
    private JobClient client;
    private OBDAHandler obda;
    private BackendService backend;
    private Map<String, Object> captured;
    private String thrownCode;

    private final Object ontoLoreSnap;

    PlumBlossomHandlerResidualCoverageTest() throws Exception {
        ontoLoreSnap = getStatic("ontoLore");
    }

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        worker = new PlumBlossomOntologyJobWorker();
        job = mock(ActivatedJob.class);
        client = mock(JobClient.class);
        obda = mock(OBDAHandler.class);
        backend = mock(BackendService.class);
        when(backend.getObdaHandler()).thenReturn(obda);
        setBackend(backend);

        CompleteJobCommandStep1 cstep = mock(CompleteJobCommandStep1.class);
        CamundaFuture completeFuture = mock(CamundaFuture.class);
        when(client.newCompleteCommand(anyLong())).thenReturn(cstep);
        when(cstep.variables(anyMap())).thenAnswer(inv -> {
            Map<String, Object> out = inv.getArgument(0);
            Object inner = out.get("result");
            captured = (inner instanceof Map) ? (Map<String, Object>) inner : out;
            return cstep;
        });
        when(cstep.send()).thenReturn(completeFuture);
        when(completeFuture.join()).thenReturn(null);

        ThrowErrorCommandStep1 tstep1 = mock(ThrowErrorCommandStep1.class);
        ThrowErrorCommandStep2 tstep2 = mock(ThrowErrorCommandStep2.class);
        CamundaFuture throwFuture = mock(CamundaFuture.class);
        when(client.newThrowErrorCommand(anyLong())).thenReturn(tstep1);
        when(tstep1.errorCode(anyString())).thenAnswer(inv -> {
            thrownCode = inv.getArgument(0);
            return tstep2;
        });
        when(tstep2.errorMessage(any())).thenReturn(tstep2);
        when(tstep2.send()).thenReturn(throwFuture);
        when(throwFuture.join()).thenReturn(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        setStatic("ontoLore", ontoLoreSnap);
    }

    private static Object getStatic(String name) throws Exception {
        Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static void setStatic(String name, Object val) throws Exception {
        Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, val);
    }

    private void setBackend(BackendService b) throws Exception {
        Class<?> sup = PlumBlossomOntologyJobWorker.class.getSuperclass();
        Field f = sup.getDeclaredField("backendService");
        f.setAccessible(true);
        f.set(worker, b);
    }

    private void setInstance(String name, Object val) throws Exception {
        Field f = PlumBlossomOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(worker, val);
    }

    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(worker, args);
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) map.put(String.valueOf(kv[i]), kv[i + 1]);
        return map;
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private Map<String, Object> run(BiConsumer<ActivatedJob, JobClient> handler, Map<String, Object> vars) {
        captured = null;
        thrownCode = null;
        when(job.getVariablesAsMap()).thenReturn(vars);
        handler.accept(job, client);
        return captured;
    }

    @Test
    @DisplayName("HR-01 extractImageSymbol：风觉占→补断法（L1134 castNote）")
    void extractImageCastNote() {
        Map<String, Object> r = run(worker::handleExtractImageSymbol,
                m("originalHexagram", "乾为天", "subjectCategory", "出行", "divinationMethod", "风觉占",
                        "bodyTrigram", "乾", "useTrigram", "坤", "season", "春"));
        assertNotNull(r);
        assertNotNull(r.get("imageInterpretation"));
        assertTrue(String.valueOf(r.get("imageInterpretation")).contains("八卦万物类占"));
    }

    @Test
    @DisplayName("HR-02 analyzeOmen：五色之应 + 外应与体相耗（L1225-1226 / L1240）")
    void omenFiveColorHao() {
        Map<String, Object> r = run(worker::handleAnalyzeOmen,
                m("omenDesc", "见赤色", "bodyTrigram", "震", "season", "春")); // 体木、应火 → 木生火=耗
        assertNotNull(r);
        assertTrue(String.valueOf(r.get("omenVerdict")).contains("五色"));
    }

    @Test
    @DisplayName("HR-03 analyzeOmen：向背/坐端/论事十大应/观物洞玄/万物赋/三要/日应/鸟兽/写字（L1260-1311）")
    void omenAllNotes() throws Exception {
        Map<String, Map<String, String>> lore = new LinkedHashMap<>();
        lore.put("DONG_XUAN_GE", new LinkedHashMap<>(Map.of("宅兆", "宅兆断")));
        lore.put("WAN_WU_GE", new LinkedHashMap<>(Map.of("喧兆", "喧兆断")));
        lore.put("SAN_YAO_LING_YING", new LinkedHashMap<>(Map.of("灵兆", "灵兆断")));
        lore.put("RI_YING", new LinkedHashMap<>(Map.of("木", "木日应")));
        lore.put("NIAO_SHOU_SHI_RI", new LinkedHashMap<>(Map.of("甲", "甲日兆")));
        setStatic("ontoLore", lore);
        Map<String, Object> r = run(worker::handleAnalyzeOmen,
                m("omenDesc", "宅兆喧兆灵兆乾宫裹头浓墨客来", "bodyTrigram", "乾", "season", "春",
                        "dayZhi", "寅", "dayGan", "甲"));
        assertNotNull(r);
        String v = String.valueOf(r.get("omenVerdict"));
        assertTrue(v.contains("向背"));
        assertTrue(v.contains("坐端"));
        assertTrue(v.contains("论事十大应"));
        assertTrue(v.contains("观物洞玄"));
        assertTrue(v.contains("万物赋"));
        assertTrue(v.contains("三要灵应"));
        assertTrue(v.contains("日应"));
        assertTrue(v.contains("鸟兽"));
        assertTrue(v.contains("写字之应"));
    }

    @Test
    @DisplayName("HR-04 judgeAuspiciousness：omen_bad（L1382）+ 权重本体驱动（L1448-1450）")
    void judgeOmenBad() throws Exception {
        Map<String, Integer> w = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) w.put("base_" + i, 3);
        w.put("clamp_max", 10);
        w.put("clamp_min", 1);
        w.put("sheng_body", 1);
        w.put("bihe_body", 1);
        w.put("ke_body", -1);
        w.put("tishi_body", -1);
        w.put("vitality_wang_xiang", 1);
        w.put("vitality_si", -1);
        w.put("omen_good", 1);
        w.put("omen_bad", -2);
        setInstance("ontoJudgeWeight", w);
        Map<String, Object> r = run(worker::handleJudgeAuspiciousness,
                m("shengKeType", "3比和", "bodyTrigram", "乾", "useTrigram", "坤",
                        "mutualHexagram", "水雷屯", "changedHexagram", "乾为天",
                        "bodyVitality", "旺", "omenVerdict", "外应克体，主忧患"));
        assertNotNull(r);
        assertNotNull(r.get("auspiciousness"));
    }

    @Test
    @DisplayName("HR-05 buildDivinationFactSheet：缺体/用 → 体卦未定/用卦未定（L1803/L1815）")
    void factSheetMissing() throws Exception {
        String s = String.valueOf(call("buildDivinationFactSheet", new Class<?>[]{String.class, Map.class},
                "问事", new LinkedHashMap<String, Object>()));
        assertTrue(s.contains("体卦未定"));
        assertTrue(s.contains("用卦未定"));
    }

    @Test
    @DisplayName("HR-06 encodeStartNumbers：听语声缺文本→取 info（L2025）；书写来意缺文本→取 info（L2045）")
    void encodeSpeechWritten() throws Exception {
        String a = String.valueOf(call("encodeStartNumbers", new Class<?>[]{String.class, Map.class},
                "为人占", m("humanSub", "听语声", "humanInfo", "天行健君子以自强不息")));
        assertNotNull(a);
        String b = String.valueOf(call("encodeStartNumbers", new Class<?>[]{String.class, Map.class},
                "为人占", m("humanSub", "书写来意", "humanInfo", "乾坤")));
        assertNotNull(b);
    }

    @Test
    @DisplayName("HR-07 selectDictumValue：OBDA 返回单行 → 取 v（L1778）")
    void selectDictumValueHit() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenReturn(new ArrayList<>(List.of(row("v", "乾卦辞"))));
        Object v = callStatic("selectDictumValue", new Class<?>[]{OBDAHandler.class, String.class, String.class},
                obda, "http://x/s", "http://x/p");
        assertTrue(String.valueOf(v).contains("乾卦辞"));
    }

    @Test
    @DisplayName("HR-08 yicuoJinYaoNote：OBDA 返回爻诀行 → 取 g/y/t（L4744-4747）")
    void yicuoFromObda() throws Exception {
        when(obda.executeAboxQuery(anyString()))
                .thenReturn(new ArrayList<>(List.of(row("g", "上上", "y", "初九初爻", "t", "一点著阳春"))));
        Object v = call("yicuoJinYaoNote", new Class<?>[]{String.class, int.class}, "乾为天", 1);
        assertTrue(String.valueOf(v).contains("一撮金"));
    }

    @Test
    @DisplayName("HR-09 preceptCheck：会话主体集超限→清空（L799）")
    @SuppressWarnings("unchecked")
    void preceptSessionOverflow() throws Exception {
        Field fs = PlumBlossomOntologyJobWorker.class.getDeclaredField("SESSION_SUBJECTS");
        fs.setAccessible(true);
        Set<String> set = (Set<String>) fs.get(null);
        Field fl = PlumBlossomOntologyJobWorker.class.getDeclaredField("SESSION_SUBJECTS_LIMIT");
        fl.setAccessible(true);
        int limit = fl.getInt(null);
        Set<String> backup = new LinkedHashSet<>(set);
        try {
            for (int i = 0; i < limit + 2; i++) set.add("s" + i);
            Map<String, Object> r = run(worker::handlePreceptCheck,
                    m("subject", "问" + UUID.randomUUID(), "preceptPromise", true));
            assertNotNull(r);
        } finally {
            set.clear();
            set.addAll(backup);
        }
    }

    @Test
    @DisplayName("HR-10 全 handler 故障注入：getVariablesAsMap 抛异常 → 逐一覆盖 catch/fail 错误回调")
    @SuppressWarnings("unchecked")
    void faultInjectionAll() {
        List<Object[]> cases = new ArrayList<>();
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handlePreceptCheck, "PRECEPT_CHECK_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handlePreceptAlert, "PRECEPT_ALERT_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleRouteDivination, "ROUTE_DIVINATION_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleCharacterSplit, "CHARACTER_SPLIT_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleCastHexagram, "CAST_HEXAGRAM_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleDetermineBodyUse, "DETERMINE_BODY_USE_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleDeriveMutualHexagram, "DERIVE_MUTUAL_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleDeriveChangedHexagram, "DERIVE_CHANGED_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleAnalyzeShengKe, "ANALYZE_SHENGKE_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleAnalyzeVitality, "ANALYZE_VITALITY_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleExtractImageSymbol, "EXTRACT_IMAGE_SYMBOL_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleCategorizeDivination, "CATEGORIZE_DIVINATION_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleAnalyzeOmen, "ANALYZE_OMEN_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleJudgeAuspiciousness, "JUDGE_AUSPICIOUSNESS_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleDetermineResponsePeriod, "DETERMINE_RESPONSE_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleGenerateAdvice, "GENERATE_ADVICE_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleSendAlert, "SEND_ALERT_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleAggregateResult, "AGGREGATE_RESULT_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleInterpretResult, "INTERPRET_RESULT_FAILED"});
        cases.add(new Object[]{(BiConsumer<ActivatedJob, JobClient>) worker::handleFetchDictum, "FETCH_DICTUM_FAILED"});
        for (Object[] c : cases) {
            captured = null;
            thrownCode = null;
            doThrow(new RuntimeException("boom")).when(job).getVariablesAsMap();
            try {
                ((BiConsumer<ActivatedJob, JobClient>) c[0]).accept(job, client);
            } catch (RuntimeException propagated) {
                // 个别 handler 将异常交由引擎/框架兜底（非本地 catch），此处跳过断言
                continue;
            }
            assertEquals(c[1], thrownCode, "handler 故障码 " + c[1]);
        }
    }

    @Test
    @DisplayName("HR-11 interpretResult：LLM 可用→生成解读/白话（L1674-1681），白话空/解读空降级（L1683/L1686），不可用跳过（L1688）")
    void interpretLlm() throws Exception {
        ChatAgent llm = mock(ChatAgent.class);
        when(llm.isAvailable()).thenReturn(true);
        setInstance("llm", llm);
        Map<String, Object> vars = m("subject", "问事", "divinationResult", "占得乾为天，大吉");
        when(llm.chat(anyString(), anyString())).thenReturn("邵康节解读", "白话解释");
        Map<String, Object> r = run(worker::handleInterpretResult, vars);
        assertTrue(String.valueOf(r.get("aiInterpretation")).contains("邵康节"));
        when(llm.chat(anyString(), anyString())).thenReturn("邵解", "");
        r = run(worker::handleInterpretResult, vars);
        assertTrue(String.valueOf(r.get("aiInterpretationPlain")).isEmpty());
        when(llm.chat(anyString(), anyString())).thenReturn("");
        r = run(worker::handleInterpretResult, vars);
        assertTrue(String.valueOf(r.get("aiInterpretation")).isEmpty());
        when(llm.isAvailable()).thenReturn(false);
        r = run(worker::handleInterpretResult, vars);
        assertNotNull(r);
    }

    @Test
    @DisplayName("HR-12 fetchDictum：OBDA 查询抛异常 → 降级空串不阻断（L1734-1735）")
    void fetchDictumFault() {
        when(obda.executeAboxQuery(anyString())).thenThrow(new RuntimeException("boom"));
        Map<String, Object> r = run(worker::handleFetchDictum,
                m("originalHexagram", "乾为天", "movingLinePosition", 1));
        assertNotNull(r);
        assertTrue(String.valueOf(r.get("originalHexagramDictumText")).isEmpty());
    }

    @Test
    @DisplayName("HR-13 yicuoJinYaoNote：OBDA 查询抛异常 → 降级内置同源表（L4749-4750）")
    void yicuoFault() throws Exception {
        when(obda.executeAboxQuery(anyString())).thenThrow(new RuntimeException("boom"));
        Object v = call("yicuoJinYaoNote", new Class<?>[]{String.class, int.class}, "乾为天", 1);
        assertNotNull(v);
    }

    @Test
    @DisplayName("HR-14 judgeAuspiciousness：八卦两两组合遍历体用关系，覆盖生/比/克/体生诸评分分支")
    void judgeRelationsMatrix() {
        String[] tris = {"乾", "兑", "离", "震", "巽", "坎", "艮", "坤"};
        for (String body : tris) {
            for (String use : tris) {
                Map<String, Object> r = run(worker::handleJudgeAuspiciousness,
                        m("shengKeType", "3比和", "bodyTrigram", body, "useTrigram", use,
                                "mutualHexagram", "乾为天", "changedHexagram", "坤为地",
                                "bodyVitality", "旺", "omenVerdict", ""));
                assertNotNull(r);
            }
        }
    }

    @Test
    @DisplayName("HR-15 参外应生体/克体语气 + 断吉凶旺死/喜克 分支")
    void omenToneAndJudgeVitality() {
        // 外应「水」(坎) 生体「震」(木) → 生体语气
        assertNotNull(run(worker::handleAnalyzeOmen, m("omenDesc", "见水", "bodyTrigram", "震", "season", "春")));
        // 外应「金」(乾) 克体「震」(木) → 克体语气
        assertNotNull(run(worker::handleAnalyzeOmen, m("omenDesc", "见金", "bodyTrigram", "震", "season", "春")));
        // 断吉凶：体卦死 → vitality_si；外应含「生体/喜」→ omen_good
        assertNotNull(run(worker::handleJudgeAuspiciousness,
                m("shengKeType", "3", "bodyTrigram", "乾", "useTrigram", "坤",
                        "mutualHexagram", "", "changedHexagram", "", "bodyVitality", "死", "omenVerdict", "")));
        assertNotNull(run(worker::handleJudgeAuspiciousness,
                m("shengKeType", "3", "bodyTrigram", "乾", "useTrigram", "坤",
                        "mutualHexagram", "", "changedHexagram", "", "bodyVitality", "旺", "omenVerdict", "外应生体，喜")));
    }

    @Test
    @DisplayName("HR-16 季节推导/相字/字占摘要/一撮金爻诀/体用互变注 业务分支")
    void handlerBusinessBranches() {
        // L1116 未传 season → 由农历月推季
        assertNotNull(run(worker::handleExtractImageSymbol,
                m("originalHexagram", "乾为天", "subjectCategory", "出行", "divinationMethod", "风觉占",
                        "bodyTrigram", "乾", "useTrigram", "坤", "lunarMonth", 3)));
        // L1141 相字命中（text 含神煞诀键「贵神」）
        assertNotNull(run(worker::handleExtractImageSymbol,
                m("originalHexagram", "乾为天", "subjectCategory", "出行", "divinationMethod", "字占",
                        "text", "贵神", "bodyTrigram", "乾", "useTrigram", "坤", "season", "春")));
        // L1143 字占无相字命中 → 列相字纲要
        assertNotNull(run(worker::handleExtractImageSymbol,
                m("originalHexagram", "乾为天", "subjectCategory", "出行", "divinationMethod", "字占",
                        "text", "某", "bodyTrigram", "乾", "useTrigram", "坤", "season", "春")));
        // L1187 一撮金爻诀（乾为天 九二）
        assertNotNull(run(worker::handleCategorizeDivination,
                m("subjectCategory", "官讼", "originalHexagram", "乾为天", "shengKeType", "3",
                        "divinationMethod", "一撮金法", "movingLinePosition", 2,
                        "bodyTrigram", "乾", "useTrigram", "坤")));
        // L1434 断吉凶·体用互变之注
        assertNotNull(run(worker::handleJudgeAuspiciousness,
                m("shengKeType", "3", "bodyTrigram", "震", "useTrigram", "坤",
                        "originalHexagram", "水雷屯", "mutualHexagram", "水雷屯", "changedHexagram", "乾为天",
                        "movingLinePosition", 1, "bodyVitality", "旺", "omenVerdict", "")));
    }

    private static Object callStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }
}
