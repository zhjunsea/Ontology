package com.ocean.ontologyframework.ziweidoushu;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 类「罕见格局组合」定向补盘（纯离线、无引擎/无 MQ/无 OBDA）。
 *
 * <p>针对「判定族之外」的三个方法，用 crafted 命盘（不校验安星真伪，只求真值命中判据）与反射直调，
 * 补齐此前未覆盖分支：{@code handleDeterminePattern}（@JobWorker，罕见具名格局组合）、
 * {@code palaceStarRelation}（星—宫五行生克全部关系）、{@code isJinYuFuJia}（金舆扶驾两侧支+非法早返回）。
 * 口径：仅覆盖「真值可达」业务判定支，不臆造、不改主代码；数据防御/异常兜底支仍据实保留不虚补。
 */
@DisplayName("C类补齐·罕见格局组合 crafted 定向补盘（判定族之外）")
class ZiWeiDouShuCClassCraftedCoverageTest {

    private static final long JOB_KEY = 9201L;
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String CHART_BASE = "SOLAR=1990-05-15T10:30;GENDER=男;LM=4;LD=21;LY=1990";

    private final ZiWeiDouShuOntologyJobWorker worker = new ZiWeiDouShuOntologyJobWorker();

    private ActivatedJob jobWith(Map<String, Object> vars) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(JOB_KEY);
        when(job.getVariablesAsMap()).thenReturn(vars);
        return job;
    }

    private static JobClient client() {
        return mock(JobClient.class, RETURNS_DEEP_STUBS);
    }

    private static Object invokeStatic(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String place(Map<Integer, String[]> p) {
        StringBuilder sb = new StringBuilder();
        p.forEach((b, ss) -> {
            for (String s : ss) {
                sb.append(s).append("@").append(ZHI[b]).append("；");
            }
        });
        return sb.toString();
    }

    private static String skFor(String ming) {
        return "命宫=" + ming + ";身宫=午;兄弟=丑;夫妻=寅;子女=卯;财帛=辰;疾厄=巳;迁移=午;奴仆=未;官禄=申;田宅=酉;福德=戌;父母=亥;水二局";
    }

    private static Map<String, Integer> ov(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }
    /** 全 14 主星 + 辅/杂星完整铺设（贴近真实安星，避免 OBDA/断语 NPE），再按 overrides 局部改位。 */
    private static Map<String, Object> chart(String ming, Map<String, Integer> overrides, String four) {
        String[] main14 = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府", "太阴",
                "贪狼", "巨门", "天相", "天梁", "七杀", "破军"};
        String[] aux = {"左辅", "右弼", "文昌", "文曲", "天魁", "天钺", "禄存", "擎羊",
                "陀罗", "火星", "铃星", "天空", "地劫", "天马", "天刑"};
        String[] misc = {"红鸾", "天喜", "台辅", "封诰", "三台", "八座", "恩光", "天贵", "天官", "天福",
                "天厨", "天巫", "天才", "天寿", "孤辰", "寡宿", "华盖", "咸池", "龙池", "凤阁",
                "天德", "月德", "解神", "天哭", "天虚", "天姚"};
        Map<String, Integer> pos = new LinkedHashMap<>();
        for (int i = 0; i < main14.length; i++) { pos.put(main14[i], i % 12); }
        for (int i = 0; i < aux.length; i++) { pos.put(aux[i], (i + 3) % 12); }
        for (int i = 0; i < misc.length; i++) { pos.put(misc[i], (i + 6) % 12); }
        pos.putAll(overrides);
        Map<Integer, List<String>> byB = new LinkedHashMap<>();
        pos.forEach((s, b) -> byB.computeIfAbsent(b, k -> new ArrayList<>()).add(s));
        Map<Integer, String[]> conv = new LinkedHashMap<>();
        byB.forEach((b, l) -> conv.put(b, l.toArray(new String[0])));

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("chartBase", CHART_BASE);
        v.put("chartSkeleton", skFor(ming));
        v.put("mainStars", place(conv));
        v.put("auxiliaryStars", "");
        v.put("miscStars", "");
        v.put("fourTransform", four);
        return v;
    }

    /** 一批覆盖罕见格局组合的合成盘。 */
    private static List<Map<String, Object>> craftedCharts() {
        List<Map<String, Object>> list = new ArrayList<>();

        // H1 机月同梁（命申，三方四正{申子辰午}同会机/月/同/梁）——覆盖 mingIdx==申 操作数
        list.add(chart("申", ov("天机", 8, "太阴", 0, "天同", 4, "天梁", 6), ""));
        // H1b 机月同梁早假：命寅、天机在三方、太阴不在三方（短路假侧）
        list.add(chart("寅", ov("天机", 2, "太阴", 1), ""));
        // H2 杀破狼：命子，七杀/破军/贪狼俱在三方四正
        list.add(chart("子", ov("七杀", 0, "破军", 4, "贪狼", 8), ""));
        // H3a 财荫夹印 rel=1：天相守命，天梁在 +1，禄存在另侧 -1
        list.add(chart("子", ov("天相", 0, "天梁", 1, "禄存", 11), ""));
        // H3b 财荫夹印 rel=11：天梁在 -1，禄存在另侧 +1（else 分支）
        list.add(chart("子", ov("天相", 0, "天梁", 11, "禄存", 1), ""));
        // H4 荫印拱身：身宫(午)坐天梁、不坐空亡
        list.add(chart("子", ov("天梁", 6), ""));
        // H5 金灿光辉：命午，主星独太阳（天府移走，命宫主星数==1）
        list.add(chart("午", ov("太阳", 6, "天府", 0), ""));
        // H6 坐贵向贵（同宫）：魁钺同守命
        list.add(chart("子", ov("天魁", 0, "天钺", 0), ""));
        // H7 贪火相逢：命午，贪狼火星同宫且不陷
        list.add(chart("午", ov("贪狼", 6, "火星", 6), ""));
        // H8 巨机居卯：命卯，巨门天机守命
        list.add(chart("卯", ov("巨门", 3, "天机", 3), ""));
        // H9a 羊刃入庙：命丑坐擎羊
        list.add(chart("丑", ov("擎羊", 1), ""));
        // H9b 羊刃入庙：命辰坐擎羊
        list.add(chart("辰", ov("擎羊", 4), ""));
        // H10 兼文武：身宫(午)见文曲武曲
        list.add(chart("子", ov("文曲", 6, "武曲", 6), ""));
        // H11 禄逢两杀 / 马落空亡：禄存·天马·地劫·截空俱在命子（空亡）
        list.add(chart("子", ov("禄存", 0, "地劫", 0, "截空", 0, "天马", 0), ""));
        // H12 杀聚：命子聚羊陀火（≥2 煞）+ 身宫(午)铃星（「四煞分守身命」新口径：命、身各见煞）+ 落陷
        list.add(chart("子", ov("擎羊", 0, "陀罗", 0, "火星", 0, "铃星", 6), ""));

        // H12b 同盘但无身宫（shenIdx=-1，覆盖 shaCntShen 三元假侧）
        Map<String, Object> noShen = chart("子", ov("擎羊", 0, "陀罗", 0, "火星", 0, "铃星", 0), "");
        noShen.put("chartSkeleton", "命宫=子;水二局");
        list.add(noShen);

        // H13 基线全星盘命子（无覆写）：覆盖 杀破狼/巨机 等复合判据短路假侧
        list.add(chart("子", ov(), ""));
        // H14 财荫夹印 2261 假侧：天相守命、天梁 rel=5（非 ±1）
        list.add(chart("子", ov("天相", 0, "天梁", 5), ""));
        // H15 明珠出海三支（未/卯/亥、辰/辰/戌、戌/戌/辰）
        list.add(chart("未", ov("太阳", 3, "太阴", 11), ""));
        list.add(chart("辰", ov("太阳", 4, "太阴", 10), ""));
        list.add(chart("戌", ov("太阴", 10, "太阳", 4), ""));

        // H16 生年干解析异常兜底：SOLAR 畸形 → parse 抛异常走 catch(L2465-2467)
        Map<String, Object> badSolar = chart("子", ov(), "");
        badSolar.put("chartBase", "SOLAR=not-a-datetime;GENDER=男");
        list.add(badSolar);
        // H17 SOLAR 缺失(null) / H18 SOLAR 空白 → L2460 复合假侧
        Map<String, Object> noSolar = chart("子", ov(), "");
        noSolar.put("chartBase", "GENDER=男");
        list.add(noSolar);
        Map<String, Object> blankSolar = chart("子", ov(), "");
        blankSolar.put("chartBase", "SOLAR=;GENDER=男");
        list.add(blankSolar);

        return list;
    }
    @Test
    @DisplayName("handleDeterminePattern：罕见格局 crafted 盘逐张完成（verify 无异常）")
    void determinePatternCrafted() {
        List<Map<String, Object>> charts = craftedCharts();
        assertThat(charts).hasSize(24);
        for (int i = 0; i < charts.size(); i++) {
            JobClient c = client();
            worker.handleDeterminePattern(jobWith(charts.get(i)), c);
            verify(c).newCompleteCommand(JOB_KEY);
        }
    }

    @Test
    @DisplayName("palaceStarRelation：遍历 14 主星 × 12 支覆盖 比和/宫生星/宫克星/星生宫/星克宫，含非法输入")
    void palaceStarRelationAllRelations() {
        String[] stars = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府", "太阴",
                "贪狼", "巨门", "天相", "天梁", "七杀", "破军"};
        List<String> outs = new ArrayList<>();
        for (String s : stars) {
            for (int b = 0; b <= 11; b++) {
                Object r = invokeStatic("palaceStarRelation",
                        new Class<?>[]{String.class, int.class}, s, b);
                assertThat(r).isNotNull();
                outs.add((String) r);
            }
        }
        String joined = String.join("|", outs);
        assertThat(joined).contains("→比和");
        assertThat(joined).contains("→宫生星（得生）");
        assertThat(joined).contains("→宫克星（受制：");
        assertThat(joined).contains("→星生宫（泄气）");
        assertThat(joined).contains("→星克宫");
        assertThat(invokeStatic("palaceStarRelation", new Class<?>[]{String.class, int.class}, "未知星", 0)).isNull();
        assertThat(invokeStatic("palaceStarRelation", new Class<?>[]{String.class, int.class}, "紫微", -1)).isNull();
        assertThat(invokeStatic("palaceStarRelation", new Class<?>[]{String.class, int.class}, "紫微", 12)).isNull();
    }

    @Test
    @DisplayName("isJinYuFuJia：两侧支(一后一前)+三处非法早返回均覆盖")
    @SuppressWarnings("unchecked")
    void isJinYuFuJiaSidesAndGuards() {
        Set<String> main = new HashSet<>(Arrays.asList("紫微"));
        Set<String> empty = new HashSet<>();
        Class<?>[] sig = new Class<?>[]{int.class, Set.class, Set.class, String.class, String.class};

        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig,
                new Object[]{0, new HashSet<>(Arrays.asList("天府")), empty, "酉", "巳"})).isFalse();
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{-1, main, empty, "酉", "巳"})).isFalse();
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{12, main, empty, "酉", "巳"})).isFalse();
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{0, main, empty, "X", "巳"})).isFalse();
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{0, main, empty, "酉", "X"})).isFalse();
        // 第一侧真：命子(太阳酉 sr=9 后、太阴巳 yr=5 前)
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{0, main, empty, "酉", "巳"})).isTrue();
        // 第二侧真：命子(太阳寅 sr=2 前、太阴酉 yr=9 后)
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{0, main, empty, "寅", "酉"})).isTrue();
        // 同侧（不夹）→ false
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{0, main, empty, "酉", "戌"})).isFalse();
        // 边界：太阴与命同宫 yr=0 → B(yr>=1)=false；太阳与命同宫 sr=0 → C(sr>=1)=false
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{0, main, empty, "酉", "子"})).isFalse();
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{0, main, empty, "子", "酉"})).isFalse();
        // 边界：太阳在 1..5 且太阴 yr<7 → D(yr>=7)=false
        assertThat((Boolean) invokeStatic("isJinYuFuJia", sig, new Object[]{0, main, empty, "寅", "巳"})).isFalse();
    }
    @Test
    @DisplayName("palaceStarRelation 值域完备：5 元素×12 支穷尽 25 组(se,pe)，绝无「→无明显生克」回落（L4780 不可达之铁证）")
    void palaceStarRelationResidueExhaustive() {
        // 每元素各取一代表星：木0天机 / 火1太阳 / 土2紫微 / 金3武曲 / 水4天同
        String[] repPerElem = {"天机", "太阳", "紫微", "武曲", "天同"};
        String[] suffixes = {"→比和", "→宫生星（得生）", "→宫克星（受制：", "→星生宫（泄气）", "→星克宫"};
        java.util.Set<String> hitKinds = new java.util.HashSet<>();
        int n = 0;
        for (String s : repPerElem) {                 // 覆盖星五行 se ∈ {0,1,2,3,4}
            for (int b = 0; b <= 11; b++) {           // 地支五行 pe ∈ {0,1,2,3,4}（12 支覆盖全 5 行）
                Object r = invokeStatic("palaceStarRelation",
                        new Class<?>[]{String.class, int.class}, s, b);
                assertThat(r).as("星%s@支%d 不得为空", s, b).isNotNull();
                String out = (String) r;
                // 铁证：Z5 差值 d=(pe-se) mod 5 五类穷尽（d=0比和/d=4宫生星/d=3宫克星/d=1星生宫/d=2星克宫），
                // 任何合法(星,宫)五行组合必落入五关系之一，永不触达 L4780「→无明显生克」——该行数学上不可达。
                assertThat(out).as("星%s@%d 结果=%s", s, b, out).doesNotContain("无明显生克");
                boolean matched = false;
                for (String sf : suffixes) {
                    if (out.contains(sf)) { matched = true; hitKinds.add(sf); break; }
                }
                assertThat(matched).as("星%s@%d 应归入 5 类生克之一：%s", s, b, out).isTrue();
                n++;
            }
        }
        assertThat(n).as("应穷尽 5 元素×12 支 = 60 组").isEqualTo(60);
        assertThat(hitKinds).as("5 类生克关系应全部出现").hasSize(5);
    }
}

