package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 第六批·流程 JobWorker 分支矩阵（handler 层，mock Camunda {@link ActivatedJob}/{@link JobClient}）。
 *
 * <p>此前的五批均覆盖「算理/文本」纯函数分支；本批补齐 20 个 {@code @JobWorker} 方法的
 * 节点级分支：以 Mockito 驱动 {@code completeResult(...)}（{@code newCompleteCommand(k).variables(map).send().join()}）
 * 与失败路径 {@code fail(...)}（{@code newThrowErrorCommand(k).errorCode(..).errorMessage(..).send().join()}），
 * 逐 handler 铺「逻辑分支 × 数据分支」。纯离线：直接 {@code new PlumBlossomOntologyJobWorker()}，
 * 所有本体/LLM/OBDA 依赖走内置兜底或空转，无需引擎、无需外部服务。
 */
public class PlumBlossomBranchMatrixBatch6Test {

    private PlumBlossomOntologyJobWorker worker;
    private ActivatedJob job;
    private JobClient client;

    /** 捕获最近一次 completeResult 传入的内层 result 映射。 */
    private Map<String, Object> captured;
    /** 捕获最近一次 fail 的 errorCode。 */
    private String thrownCode;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        worker = new PlumBlossomOntologyJobWorker();
        job = mock(ActivatedJob.class);
        client = mock(JobClient.class);

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

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private Map<String, Object> run(BiConsumer<ActivatedJob, JobClient> handler, Map<String, Object> vars) {
        captured = null;
        thrownCode = null;
        when(job.getVariablesAsMap()).thenReturn(vars);
        handler.accept(job, client);
        return captured;
    }

    // ---------------- 节点 1：四不占校验 preceptCheck ----------------

    @Test
    @DisplayName("B6-01 preceptCheck：无事（subject 空）→ pass=false/无事不占")
    void preceptCheck_noSubject() {
        Map<String, Object> r = run(worker::handlePreceptCheck, m("subject", "   "));
        assertEquals(Boolean.FALSE, r.get("pass"));
        assertEquals("无事不占", r.get("triggered"));
    }

    @Test
    @DisplayName("B6-02 preceptCheck：未承诺 preceptPromise=false → 不诚不占")
    void preceptCheck_notPromise() {
        Map<String, Object> r = run(worker::handlePreceptCheck,
                m("subject", "问" + UUID.randomUUID(), "preceptPromise", false));
        assertEquals(Boolean.FALSE, r.get("pass"));
        assertEquals("不诚不占", r.get("triggered"));
    }

    @Test
    @DisplayName("B6-03 preceptCheck：preceptRighteous=false → 不义不占")
    void preceptCheck_notRighteous() {
        Map<String, Object> r = run(worker::handlePreceptCheck,
                m("subject", "问" + UUID.randomUUID(), "preceptPromise", true, "preceptRighteous", false));
        assertEquals(Boolean.FALSE, r.get("pass"));
        assertEquals("不义不占", r.get("triggered"));
    }

    @Test
    @DisplayName("B6-04 preceptCheck：首占通过、同题重占→重卦不占（SESSION_SUBJECTS 去重）")
    void preceptCheck_passThenDuplicate() {
        String subject = "问" + UUID.randomUUID();
        Map<String, Object> first = run(worker::handlePreceptCheck,
                m("subject", subject, "preceptPromise", true));
        assertEquals(Boolean.TRUE, first.get("pass"));
        assertEquals("", first.get("triggered"));

        Map<String, Object> second = run(worker::handlePreceptCheck,
                m("subject", subject, "preceptPromise", true));
        assertEquals(Boolean.FALSE, second.get("pass"));
        assertEquals("重卦不占", second.get("triggered"));
    }

    // ---------------- 节点 2'：戒律告警 preceptAlert ----------------

    @Test
    @DisplayName("B6-05 preceptAlert：已知戒律文案 + 未知兜底")
    void preceptAlert_knownAndDefault() {
        Map<String, Object> r1 = run(worker::handlePreceptAlert, m("triggeredPrecept", "无事不占"));
        assertEquals("无具体事项不可起卦。", r1.get("alertText"));

        Map<String, Object> r2 = run(worker::handlePreceptAlert, m("triggeredPrecept", "不诚不占"));
        assertEquals("意念不纯，请诚心再问。", r2.get("alertText"));

        Map<String, Object> r3 = run(worker::handlePreceptAlert, m("triggeredPrecept", "未知戒律"));
        assertTrue(String.valueOf(r3.get("alertText")).contains("未可轻占"));
    }

    // ---------------- 节点 2：起卦法路由 routeDivination ----------------

    @Test
    @DisplayName("B6-06 routeDivination：字占=先天/rawNumber 0|0|0；物卦起例=后天")
    void routeDivination_systemRouting() {
        Map<String, Object> r1 = run(worker::handleRouteDivination, m("divinationMethod", "字占"));
        assertEquals(Boolean.TRUE, r1.get("isCharacter"));
        assertEquals("先天", r1.get("system"));
        assertEquals("0|0|0", r1.get("rawNumber"));

        Map<String, Object> r2 = run(worker::handleRouteDivination, m("divinationMethod", "物卦起例"));
        assertEquals(Boolean.FALSE, r2.get("isCharacter"));
        assertEquals("后天", r2.get("system"));
        assertTrue(String.valueOf(r2.get("rawNumber")).contains("|"));
    }

    @Test
    @DisplayName("B6-07 routeDivination：method=null → 后天/默认编码 1|1|2")
    void routeDivination_nullMethod() {
        Map<String, Object> r = run(worker::handleRouteDivination, m());
        assertEquals(Boolean.FALSE, r.get("isCharacter"));
        assertEquals("后天", r.get("system"));
        assertEquals("1|1|2", r.get("rawNumber"));
    }

    // ---------------- 节点 2-字占：characterSplit ----------------

    @Test
    @DisplayName("B6-08 characterSplit：三字取数，返回上下数与取数来源")
    void characterSplit_basic() {
        Map<String, Object> r = run(worker::handleCharacterSplit, m("text", "一二三"));
        assertTrue(r.get("upperNumber") instanceof Integer);
        assertTrue(r.get("lowerNumber") instanceof Integer);
        assertNotNull(r.get("charNumberSource"));
        assertFalse(String.valueOf(r.get("charNumberSource")).isBlank());
    }

    // ---------------- 节点 3：起卦 castHexagram ----------------

    @Test
    @DisplayName("B6-09 castHexagram：非字占由 rawNumber 解码 → 乾为天/动爻6")
    void castHexagram_nonCharacter() {
        Map<String, Object> r = run(worker::handleCastHexagram,
                m("isCharacter", false, "rawNumber", "1|1|6"));
        assertEquals("乾为天", r.get("originalHexagram"));
        assertEquals(6, r.get("movingLinePosition"));
    }

    @Test
    @DisplayName("B6-10 castHexagram：字占由上/下数+时支加时取动爻 → 乾为天/动爻3")
    void castHexagram_character() {
        Map<String, Object> r = run(worker::handleCastHexagram,
                m("isCharacter", true, "upperNumber", 1, "lowerNumber", 1, "hourZhi", "子"));
        assertEquals("乾为天", r.get("originalHexagram"));
        assertEquals(3, r.get("movingLinePosition"));
    }

    // ---------------- 节点 4：定体用 determineBodyUse ----------------

    @Test
    @DisplayName("B6-11 determineBodyUse：动爻在下卦↔上卦，体用互换（地天泰）")
    void determineBodyUse_swap() {
        Map<String, Object> low = run(worker::handleDetermineBodyUse,
                m("originalHexagram", "地天泰", "movingLinePosition", 1));
        assertEquals("坤", low.get("bodyTrigram"));
        assertEquals("乾", low.get("useTrigram"));

        Map<String, Object> up = run(worker::handleDetermineBodyUse,
                m("originalHexagram", "地天泰", "movingLinePosition", 6));
        assertEquals("乾", up.get("bodyTrigram"));
        assertEquals("坤", up.get("useTrigram"));
    }

    // ---------------- 节点 5：互卦 deriveMutualHexagram ----------------

    @Test
    @DisplayName("B6-12 deriveMutualHexagram：乾坤无互→互其变卦（乾为天动1→天风姤）")
    void deriveMutual_pureQian() {
        Map<String, Object> r = run(worker::handleDeriveMutualHexagram,
                m("originalHexagram", "乾为天", "movingLinePosition", 1));
        assertEquals("天风姤", r.get("mutualHexagram"));
    }

    @Test
    @DisplayName("B6-13 deriveMutualHexagram：常卦取中间四爻（地天泰→雷泽归妹）")
    void deriveMutual_normal() {
        Map<String, Object> r = run(worker::handleDeriveMutualHexagram,
                m("originalHexagram", "地天泰", "movingLinePosition", 1));
        assertEquals("雷泽归妹", r.get("mutualHexagram"));
    }

    // ---------------- 节点 6：变卦 deriveChangedHexagram ----------------

    @Test
    @DisplayName("B6-14 deriveChangedHexagram：乾为天动1→天风姤")
    void deriveChanged_basic() {
        Map<String, Object> r = run(worker::handleDeriveChangedHexagram,
                m("originalHexagram", "乾为天", "movingLinePosition", 1));
        assertEquals("天风姤", r.get("changedHexagram"));
    }

    // ---------------- 节点 7：五行生克 analyzeShengKe ----------------

    @Test
    @DisplayName("B6-15 analyzeShengKe：体乾(金)被用离(火)克→第5级")
    void analyzeShengKe_level() {
        Map<String, Object> r = run(worker::handleAnalyzeShengKe,
                m("bodyTrigram", "乾", "useTrigram", "离"));
        assertTrue(String.valueOf(r.get("shengKeType")).startsWith("5·"));
    }

    // ---------------- 节点 8：卦气旺衰 analyzeVitality ----------------

    @Test
    @DisplayName("B6-16 analyzeVitality：显式 season=春 体震(木)→旺")
    void analyzeVitality_seasonGiven() {
        Map<String, Object> r = run(worker::handleAnalyzeVitality,
                m("season", "春", "bodyTrigram", "震"));
        assertEquals("旺", r.get("bodyVitality"));
    }

    @Test
    @DisplayName("B6-17 analyzeVitality：season 缺省由 lunarMonth 推（7月→秋，体震木→死）")
    void analyzeVitality_fromLunarMonth() {
        Map<String, Object> r = run(worker::handleAnalyzeVitality,
                m("lunarMonth", 7, "bodyTrigram", "震"));
        assertEquals("死", r.get("bodyVitality"));
    }

    @Test
    @DisplayName("B6-18 analyzeVitality：体卦缺省由本卦+动爻推（地天泰动1→体坤）")
    void analyzeVitality_bodyDerived() {
        Map<String, Object> r = run(worker::handleAnalyzeVitality,
                m("season", "春", "originalHexagram", "地天泰", "movingLinePosition", 1));
        assertEquals("死", r.get("bodyVitality"));
    }

    // ---------------- 节点 9：万物类象 extractImageSymbol ----------------

    @Test
    @DisplayName("B6-19 extractImageSymbol：常规取象产出非空解读")
    void extractImageSymbol_basic() {
        Map<String, Object> r = run(worker::handleExtractImageSymbol, m(
                "originalHexagram", "乾为天", "mutualHexagram", "乾为天", "changedHexagram", "天风姤",
                "subjectCategory", "人事", "bodyTrigram", "乾", "useTrigram", "乾",
                "divinationMethod", "年月日时起例", "season", "春"));
        assertFalse(String.valueOf(r.get("imageInterpretation")).isBlank());
    }

    @Test
    @DisplayName("B6-20 extractImageSymbol：占物类补入器物/断物之妙")
    void extractImageSymbol_objectMethod() {
        Map<String, Object> r = run(worker::handleExtractImageSymbol, m(
                "originalHexagram", "乾为天", "mutualHexagram", "乾为天", "changedHexagram", "天风姤",
                "subjectCategory", "占物", "bodyTrigram", "乾", "useTrigram", "兑",
                "divinationMethod", "占动物", "season", "春", "omenDesc", "见器物"));
        assertFalse(String.valueOf(r.get("imageInterpretation")).isBlank());
    }

    // ---------------- 节点 10：分类占断 categorizeDivination ----------------

    @Test
    @DisplayName("B6-21 categorizeDivination：天时占走全卦观五行分支")
    void categorizeDivination_tianshi() {
        Map<String, Object> r = run(worker::handleCategorizeDivination, m(
                "subjectCategory", "天时", "originalHexagram", "乾为天",
                "mutualHexagram", "乾为天", "changedHexagram", "天风姤"));
        assertFalse(String.valueOf(r.get("categoryVerdict")).isBlank());
    }

    @Test
    @DisplayName("B6-22 categorizeDivination：非天时走体用生克分类断（人事）")
    void categorizeDivination_else() {
        Map<String, Object> r = run(worker::handleCategorizeDivination, m(
                "subjectCategory", "人事", "originalHexagram", "乾为天", "shengKeType", "3·平（平）",
                "bodyTrigram", "乾", "useTrigram", "离",
                "mutualHexagram", "乾为天", "changedHexagram", "天风姤", "movingLinePosition", 1));
        assertFalse(String.valueOf(r.get("categoryVerdict")).isBlank());
    }

    @Test
    @DisplayName("B6-23 categorizeDivination：一撮金法补入爻诀与〔八卦诀〕宫名")
    void categorizeDivination_yicuo() {
        Map<String, Object> r = run(worker::handleCategorizeDivination, m(
                "subjectCategory", "人事", "originalHexagram", "乾为天", "shengKeType", "3·平（平）",
                "bodyTrigram", "乾", "useTrigram", "离", "mutualHexagram", "乾为天",
                "changedHexagram", "天风姤", "movingLinePosition", 1, "divinationMethod", "一撮金法"));
        assertTrue(String.valueOf(r.get("categoryVerdict")).contains("〔八卦诀〕"));
    }

    // ---------------- 节点 11：参外应 analyzeOmen ----------------

    @Test
    @DisplayName("B6-24 analyzeOmen：无外应→〔静占〕不论外卦")
    void analyzeOmen_blank() {
        Map<String, Object> r = run(worker::handleAnalyzeOmen, m("omenDesc", "  "));
        assertTrue(String.valueOf(r.get("omenVerdict")).contains("〔静占〕"));
    }

    @Test
    @DisplayName("B6-25 analyzeOmen：有外应→三要十应参断")
    void analyzeOmen_present() {
        Map<String, Object> r = run(worker::handleAnalyzeOmen, m(
                "omenDesc", "见圆物", "bodyTrigram", "乾", "season", "春"));
        String v = String.valueOf(r.get("omenVerdict"));
        assertTrue(v.contains("三要十应参断"));
    }

    // ---------------- 节点 12：断吉凶 judgeAuspiciousness ----------------

    @Test
    @DisplayName("B6-26 judgeAuspiciousness：产出吉凶等级与过程描述")
    void judgeAuspiciousness_basic() {
        Map<String, Object> r = run(worker::handleJudgeAuspiciousness, m(
                "shengKeType", "3·体克用（平）", "bodyVitality", "旺",
                "bodyTrigram", "乾", "useTrigram", "离",
                "mutualHexagram", "乾为天", "changedHexagram", "坤为地", "movingLinePosition", 1));
        assertNotNull(r.get("auspiciousness"));
        assertTrue(String.valueOf(r.get("processDescription")).contains("先看体用生克"));
        assertNotNull(r.get("processFlag"));
    }

    // ---------------- 节点 13：定应期 determineResponsePeriod ----------------

    @Test
    @DisplayName("B6-27 determineResponsePeriod：应期含万物应期/先后天诀")
    void determineResponsePeriod_basic() {
        Map<String, Object> r = run(worker::handleDetermineResponsePeriod, m(
                "auspiciousness", "吉", "motion", "行",
                "originalHexagram", "乾为天", "bodyTrigram", "乾", "useTrigram", "乾"));
        assertFalse(String.valueOf(r.get("responsePeriod")).isBlank());
    }

    // ---------------- 节点 14：修身建议 generateAdvice ----------------

    @Test
    @DisplayName("B6-28 generateAdvice：大吉/吉→守正保泰；凶/大凶→积德改行；平→居中持正")
    void generateAdvice_threeBranches() {
        Map<String, Object> ji = run(worker::handleGenerateAdvice, m("auspiciousness", "大吉", "bodyTrigram", "乾", "useTrigram", "乾"));
        assertTrue(String.valueOf(ji.get("advice")).contains("守正保泰"));
        assertNotNull(ji.get("breakdownPlan"));

        Map<String, Object> xiong = run(worker::handleGenerateAdvice, m("auspiciousness", "凶", "bodyTrigram", "乾", "useTrigram", "离"));
        assertTrue(String.valueOf(xiong.get("advice")).contains("积德改行"));
        assertFalse(String.valueOf(xiong.get("breakdownPlan")).isBlank());

        Map<String, Object> ping = run(worker::handleGenerateAdvice, m("auspiciousness", "平", "bodyTrigram", "乾", "useTrigram", "兑"));
        assertTrue(String.valueOf(ping.get("advice")).contains("居中持正"));
    }

    // ---------------- 节点 15：外发告警 sendAlert ----------------

    @Test
    @DisplayName("B6-29 sendAlert：非大吉/大凶→SKIPPED_NON_MAJOR（离线不触 MQ）")
    void sendAlert_skipNonMajor() {
        Map<String, Object> r = run(worker::handleSendAlert, m("auspiciousness", "吉"));
        assertEquals("SKIPPED_NON_MAJOR", r.get("messageStatus"));
    }

    // ---------------- 节点 16：汇总占断 aggregateResult ----------------

    @Test
    @DisplayName("B6-30 aggregateResult：汇总结果含占断事项与占断依据结构表")
    void aggregateResult_basic() {
        Map<String, Object> r = run(worker::handleAggregateResult, m(
                "subject", "测求职", "originalHexagram", "乾为天", "bodyTrigram", "乾", "useTrigram", "离",
                "mutualHexagram", "乾为天", "changedHexagram", "天风姤", "shengKeType", "3·平（平）",
                "bodyVitality", "旺", "auspiciousness", "吉"));
        assertTrue(String.valueOf(r.get("divinationResult")).contains("测求职"));
        assertTrue(String.valueOf(r.get("divinationResult")).contains("【占断事项】"));
        assertFalse(String.valueOf(r.get("divinationFactSheet")).isBlank());
    }

    // ---------------- 节点 17：邵康节解读 interpretResult ----------------

    @Test
    @DisplayName("B6-31 interpretResult：LLM 未装配→解读空串降级不阻断")
    void interpretResult_noLlm() {
        Map<String, Object> r = run(worker::handleInterpretResult,
                m("subject", "测事", "divinationResult", "占断结论若干"));
        assertEquals("", r.get("aiInterpretation"));
        assertEquals("", r.get("aiInterpretationPlain"));
    }

    // ---------------- 节点 18：取卦爻辞 fetchDictum ----------------

    @Test
    @DisplayName("B6-32 fetchDictum：OBDA 未就绪→卦辞/爻辞四项空串降级不阻断")
    void fetchDictum_noBackend() {
        Map<String, Object> r = run(worker::handleFetchDictum,
                m("originalHexagram", "乾为天", "movingLinePosition", 1));
        assertEquals("", r.get("originalHexagramDictumText"));
        assertEquals("", r.get("originalHexagramDictumInterpretation"));
        assertEquals("", r.get("movingLineDictumText"));
        assertEquals("", r.get("movingLineDictumInterpretation"));
    }
}
