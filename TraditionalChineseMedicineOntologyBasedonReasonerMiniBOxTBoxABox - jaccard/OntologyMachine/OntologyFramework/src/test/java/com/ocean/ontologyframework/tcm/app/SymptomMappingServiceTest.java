package com.ocean.ontologyframework.tcm.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 症状映射三层策略测试。
 *
 * <p>全部离线运行：L1 为确定性算法，L2 用 {@link LlmClient#setOverride} 注入桩函数。
 */
class SymptomMappingServiceTest {

    private static SymptomCatalog catalog;

    @BeforeAll
    static void setUp() {
        catalog = new SymptomCatalog(SymptomCatalogTest.readAboxDir());
    }

    /** 构造一个「大模型不可用」的映射服务（纯 L1 降级模式）。 */
    private static SymptomMappingService l1Only() {
        LlmClient llm = new LlmClient();
        llm.configure(false, "", "", "stub", 5);
        SymptomMappingService s = new SymptomMappingService(catalog, llm);
        s.configure(3, 0.85, 0.50, 3, 60);
        return s;
    }

    /** 构造一个带 LLM 桩的映射服务。 */
    private static SymptomMappingService withLlm(java.util.function.BiFunction<String, String, String> stub) {
        LlmClient llm = new LlmClient(stub);
        SymptomMappingService s = new SymptomMappingService(catalog, llm);
        s.configure(3, 0.85, 0.50, 3, 60);
        return s;
    }

    private static String frag(String label) {
        return catalog.byLabel(label).orElseThrow().getFragment();
    }

    private static List<String> fragmentsOf(SymptomMappingService.MappingResult r) {
        return r.detail.stream().map(d -> String.valueOf(d.get("fragment"))).toList();
    }

    // ============================================================
    // L1 确定性
    // ============================================================

    @Test
    @DisplayName("L1 精确匹配：规范症状名直接命中，无需确认")
    void l1Exact() {
        var r = l1Only().map("发热，恶寒，下利", null, null, null, 0);
        assertThat(fragmentsOf(r)).containsExactlyInAnyOrder(
                frag("发热"), frag("恶寒"), frag("下利"));
        assertThat(r.needsConfirmation).isFalse();
        assertThat(r.symptomIris).hasSize(3);
        assertThat(r.unmatched).isEmpty();
        assertThat(r.detail).allSatisfy(d -> assertThat(d.get("source")).isEqualTo("EXACT"));
    }

    @Test
    @DisplayName("L1 同义词：口语表达映射到规范实例")
    void l1Alias() {
        var r = l1Only().map("拉肚子，睡不着，手脚冰凉", null, null, null, 0);
        assertThat(fragmentsOf(r)).containsExactlyInAnyOrder(
                frag("下利"), frag("不得眠"), frag("手足厥逆"));
        assertThat(r.needsConfirmation).isFalse();
    }

    @Test
    @DisplayName("L1 切词：无标点的连写句子也能拆出多个症状")
    void l1Segmentation() {
        var r = l1Only().map("发烧怕冷", null, null, null, 0);
        assertThat(fragmentsOf(r)).containsExactlyInAnyOrder(frag("发热"), frag("恶寒"));
        assertThat(r.needsConfirmation).isFalse();
    }

    @Test
    @DisplayName("L1 同义词包含：'老想吐' 命中 '想吐' → 欲呕")
    void l1AliasContains() {
        var r = l1Only().map("老想吐", null, null, null, 0);
        assertThat(fragmentsOf(r)).contains(frag("欲呕"));
        assertThat(r.needsConfirmation).isFalse();
    }

    @Test
    @DisplayName("L1 子串：'口大渴' 命中 '大渴'")
    void l1Substring() {
        var r = l1Only().map("口大渴", null, null, null, 0);
        assertThat(fragmentsOf(r)).contains(frag("大渴"));
        assertThat(r.needsConfirmation).isFalse();
    }

    @Test
    @DisplayName("L1 单字症状：低置信度 → 采纳但需人工确认")
    void l1SingleCharNeedsConfirmation() {
        var r = l1Only().map("想呕", null, null, null, 0);
        assertThat(fragmentsOf(r)).contains(frag("呕"));
        assertThat(r.needsConfirmation).isTrue();
        var d = r.detail.stream().filter(x -> frag("呕").equals(x.get("fragment"))).findFirst().orElseThrow();
        assertThat((Double) d.get("confidence")).isLessThan(0.85);
        assertThat(d.get("needsConfirmation")).isEqualTo(true);
    }

    @Test
    @DisplayName("L1 无候选且大模型不可用 → 进入未匹配，需确认")
    void l1UnmatchedWhenLlmUnavailable() {
        var r = l1Only().map("两边肋骨下面胀痛", null, null, null, 0);
        assertThat(r.unmatched).contains("两边肋骨下面胀痛");
        assertThat(r.needsConfirmation).isTrue();
        assertThat(r.llmAvailable).isFalse();
        assertThat(r.summary).contains("降级");
    }

    @Test
    @DisplayName("表述单元切分：剥离口语填充词，按标点断句")
    void unitSplitting() {
        assertThat(SymptomMappingService.splitUnits("我最近一直有点头晕，还老想吐"))
                .containsExactly("头晕", "想吐");
        assertThat(SymptomMappingService.splitUnits("发热。恶寒；下利、口苦"))
                .containsExactly("发热", "恶寒", "下利", "口苦");
        assertThat(SymptomMappingService.splitUnits("")).isEmpty();
        assertThat(SymptomMappingService.splitUnits(null)).isEmpty();
    }

    // ============================================================
    // L2 大模型
    // ============================================================

    @Test
    @DisplayName("L2 大模型解析：白名单内的片段被采纳")
    void l2ResolvesWithinWhitelist() {
        String target = frag("胸胁苦满");
        var svc = withLlm((sys, user) -> {
            assertThat(sys).contains("只能从候选清单中选择");
            assertThat(user).contains("两边肋骨下面胀痛");
            return "{\"matches\":[{\"text\":\"两边肋骨下面胀痛\",\"fragments\":[\"" + target
                    + "\"],\"confidence\":0.92,\"reason\":\"对应胸胁苦满\"}],\"unmatched\":[]}";
        });
        var r = svc.map("两边肋骨下面胀痛", null, null, null, 0);
        assertThat(fragmentsOf(r)).contains(target);
        assertThat(r.needsConfirmation).isFalse();
        assertThat(r.unmatched).isEmpty();
        assertThat(r.llmAvailable).isTrue();
    }

    @Test
    @DisplayName("L2 防幻觉：白名单外的片段被丢弃，转为未匹配")
    void l2RejectsHallucination() {
        var svc = withLlm((sys, user) ->
                "{\"matches\":[{\"text\":\"两边肋骨下面胀痛\",\"fragments\":[\"NotExist_instance\"],"
                        + "\"confidence\":0.99}],\"unmatched\":[]}");
        var r = svc.map("两边肋骨下面胀痛", null, null, null, 0);
        assertThat(fragmentsOf(r)).doesNotContain("NotExist_instance");
        assertThat(r.unmatched).contains("两边肋骨下面胀痛");
    }

    @Test
    @DisplayName("L2 输出带 markdown 围栏 / 前后噪声时仍能解析")
    void l2TolerantParsing() {
        String target = frag("胸胁苦满");
        var svc = withLlm((sys, user) -> "好的，结果如下：\n```json\n"
                + "{\"matches\":[{\"text\":\"两边肋骨下面胀痛\",\"fragments\":[\"" + target
                + "\"],\"confidence\":0.9}],\"unmatched\":[]}\n```\n以上。");
        var r = svc.map("两边肋骨下面胀痛", null, null, null, 0);
        assertThat(fragmentsOf(r)).contains(target);
    }

    @Test
    @DisplayName("L2 返回 null（超时/网络失败）→ 自动降级为未匹配，不抛异常")
    void l2DegradesOnNull() {
        var svc = withLlm((sys, user) -> null);
        var r = svc.map("两边肋骨下面胀痛", null, null, null, 0);
        assertThat(r.unmatched).contains("两边肋骨下面胀痛");
        assertThat(r.needsConfirmation).isTrue();
    }

    @Test
    @DisplayName("L2 返回非法 JSON → 自动降级为未匹配")
    void l2DegradesOnBadJson() {
        var svc = withLlm((sys, user) -> "这不是 JSON");
        var r = svc.map("两边肋骨下面胀痛", null, null, null, 0);
        assertThat(r.unmatched).contains("两边肋骨下面胀痛");
    }

    // ============================================================
    // L3 门控与幂等
    // ============================================================

    @Test
    @DisplayName("L3 用户确认：confirmed 项以 1.0 采纳，且不再触发待确认")
    void l3UserConfirmation() {
        var svc = l1Only();
        String ou = frag("呕");

        var round0 = svc.map("想呕", null, null, null, 0);
        assertThat(round0.needsConfirmation).isTrue();

        var round1 = svc.map("想呕", List.of(ou), null, null, 1);
        assertThat(round1.needsConfirmation).isFalse();
        assertThat(round1.ambiguous).isEmpty();
        var d = round1.detail.stream().filter(x -> ou.equals(x.get("fragment"))).findFirst().orElseThrow();
        assertThat(d.get("source")).isEqualTo("USER_CONFIRMED");
        assertThat((Double) d.get("confidence")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("L3 用户拒绝：rejected 项被剔除")
    void l3UserRejection() {
        var svc = l1Only();
        String ehan = frag("恶寒");
        var r = svc.map("发热，恶寒", null, List.of(ehan), null, 0);
        assertThat(fragmentsOf(r)).contains(frag("发热"));
        assertThat(fragmentsOf(r)).doesNotContain(ehan);
    }

    @Test
    @DisplayName("L3 用户补充：extra 项以 1.0 采纳")
    void l3UserExtra() {
        var svc = l1Only();
        String kouku = frag("口苦");
        var r = svc.map("发热", null, null, List.of(kouku), 0);
        assertThat(fragmentsOf(r)).contains(frag("发热"), kouku);
        assertThat(r.needsConfirmation).isFalse();
    }

    @Test
    @DisplayName("L3 确认后的重跑：未匹配项不再拦截（避免死循环）")
    void l3UnmatchedNotBlockingAfterConfirmation() {
        var svc = l1Only();
        var r = svc.map("两边肋骨下面胀痛", null, null, null, 1);
        assertThat(r.unmatched).isNotEmpty();
        assertThat(r.needsConfirmation).isFalse();
    }

    @Test
    @DisplayName("L3 回环上限：round >= maxRounds 强制放行")
    void l3MaxRoundsGuard() {
        var svc = l1Only();
        var r = svc.map("想呕", null, null, null, 3);
        assertThat(r.needsConfirmation).isFalse();
    }

    @Test
    @DisplayName("脉象 / 舌象分别归入独立通道")
    void categoryRouting() {
        var r = l1Only().map("浮脉，淡红舌，发热", null, null, null, 0);
        assertThat(r.pulseIris).contains(frag("浮脉"));
        assertThat(r.tongueIris).contains(frag("淡红舌"));
        assertThat(r.symptomIris).contains(frag("发热"));
        assertThat(r.fuzhengIris).isEmpty();
    }

    @Test
    @DisplayName("输出变量契约：toVariables 包含流程所需全部键")
    void variablesContract() {
        var r = l1Only().map("发热", null, null, null, 0);
        Map<String, Object> v = r.toVariables();
        assertThat(v).containsKeys(
                "symptomIris", "pulseIris", "tongueIris", "fuzhengIris",
                "needsConfirmation", "ambiguousSymptoms", "unmatchedTexts",
                "mappingDetail", "mappingSummary", "mappingRound", "llmAvailable");
        assertThat(v.get("symptomIris")).isInstanceOf(List.class);
        assertThat(v.get("needsConfirmation")).isInstanceOf(Boolean.class);
    }

    @Test
    @DisplayName("流程变量输出完整 IRI（与 sizhen-input 下游契约一致）")
    void variablesEmitFullIris() {
        var r = l1Only().map("发热，浮脉，淡红舌", null, null, null, 0);
        Map<String, Object> v = r.toVariables();

        @SuppressWarnings("unchecked")
        List<String> syms = (List<String>) v.get("symptomIris");
        @SuppressWarnings("unchecked")
        List<String> pulses = (List<String>) v.get("pulseIris");
        @SuppressWarnings("unchecked")
        List<String> tongues = (List<String>) v.get("tongueIris");

        // 下游（八纲/六经/方证推理）按 IRI 查找个体，必须是 BASE_NS 开头的完整 IRI
        assertThat(syms).contains(SymptomCatalog.BASE_NS + frag("发热"));
        assertThat(pulses).contains(SymptomCatalog.BASE_NS + frag("浮脉"));
        assertThat(tongues).contains(SymptomCatalog.BASE_NS + frag("淡红舌"));
        assertThat(syms).allSatisfy(i -> assertThat(i).startsWith(SymptomCatalog.BASE_NS));

        // mappingDetail 仍保留 fragment，便于前端展示与人工确认
        assertThat(r.detail).allSatisfy(d -> {
            assertThat((String) d.get("fragment")).doesNotStartWith("http");
            assertThat((String) d.get("iri")).startsWith(SymptomCatalog.BASE_NS);
        });
    }

    @Test
    @DisplayName("字符 Dice 系数：完全相同为 1，无交集为 0")
    void diceMetric() {
        assertThat(SymptomMappingService.dice("发热", "发热")).isEqualTo(1.0);
        assertThat(SymptomMappingService.dice("发热", "恶寒")).isEqualTo(0.0);
        assertThat(SymptomMappingService.dice("", "发热")).isEqualTo(0.0);
    }
}
