package com.ocean.ontologyframework.ziweidoushu;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B 类补齐·⑬ 用「构造盘数据矩阵」补齐三大函数残余分支：
 * <ul>
 *   <li>{@code handleDeterminePattern}（@JobWorker）：以多张合成命盘驱动，覆盖富局/贵局/贫贱局/诸格论等格局判据的真/假两侧；</li>
 *   <li>{@code handleInterpretStarNature}（@JobWorker）：以命宫主星在/不在、辅星命中等变量覆盖化气/形性/问答回查分支；</li>
 *   <li>{@code judgeTongYuan}（纯函数）：注入 {@code ONTO_TONGYUAN} 构造行，覆盖支条件/星条件/生干条件/举例式回退分支。</li>
 * </ul>
 * 均为离线（Mockito 深桩 / 反射），不依赖 Camunda/DB/Ontop。
 */
@DisplayName("B类补齐·格局/星情/同垣 构造盘驱动补盘")
class ZiWeiDouShuBClassResidualCoverageTest13 {

    private static final long JOB_KEY = 9101L;
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
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

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setStatic(String field, Object value) {
        try {
            Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void resetStatics() {
        setStatic("ONTO_TONGYUAN", null);
    }

    /** 把 {宫位索引 -> 星名数组} 渲染为 "星@支；…" 的安星串。 */
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

    /**
     * 全 14 主星 + 辅/杂星完整铺设（贴近真实安星，避免缺星致 OBDA 断语 NPE），再按 overrides 局部改位。
     * 与 Test11 通过的整盘同构：所有主星齐备，故 judgeTongYuan 各「举例式」客观断语不会因 branchIndexOf 为 null 而崩。
     */
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

    private static Map<String, Integer> ov(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) { m.put((String) kv[i], (Integer) kv[i + 1]); }
        return m;
    }

    /** 组织一批「命中不同格局簇」的合成盘（全星盘 + 局部覆写）。 */
    private static List<Map<String, Object>> patternCharts() {
        List<Map<String, Object>> list = new ArrayList<>();

        // S1 紫府同宫/日月同临&并明/府相朝垣/火贵/铃贪/左右朝垣/文星朝命/紫微七杀化权（命子=0，三方{0,4,8,6}）
        list.add(chart("子", ov(
                "天府", 0, "太阳", 0, "太阴", 0, "七杀", 0,
                "天相", 4, "贪狼", 8, "火星", 4, "铃星", 8,
                "左辅", 4, "右弼", 8, "文昌", 4, "文曲", 8), ""));

        // S2 机月同梁（命寅=2，三方{2,6,10,8} 同会机/月/同/梁）
        list.add(chart("寅", ov("天机", 2, "太阴", 6, "天同", 10, "天梁", 8), ""));

        // S3 日月夹财（命子=0，夹两侧亥11/丑1，武曲守命）
        list.add(chart("子", ov("武曲", 0, "太阳", 11, "太阴", 1), ""));

        // S3b 财禄夹马（武曲亥/禄存丑夹命子，天马守命）
        list.add(chart("子", ov("天马", 0, "武曲", 11, "禄存", 1), ""));

        // S4 日出扶桑（太阳守卯）/刑囚夹印（天刑廉贞同宫）/坐贵向贵（天魁卯、天钺酉）
        list.add(chart("卯", ov("太阳", 3, "天刑", 3, "廉贞", 3, "天魁", 3, "天钺", 9), ""));

        // S5 君臣庆会（紫微与左辅右弼同守子）
        list.add(chart("子", ov("紫微", 0, "左辅", 0, "右弼", 0), ""));

        // S6 马头带剑（擎羊守午）/七杀朝斗/石中隐玉（巨门守）/对面朝斗（禄存守）
        list.add(chart("午", ov("擎羊", 6, "七杀", 6, "巨门", 6, "禄存", 6), ""));

        // S7 科权禄主（紫微化禄+化权守命）/兼文武（文曲武曲同命）
        list.add(chart("子", ov("紫微", 0, "文曲", 0, "武曲", 0), "紫微化禄；武曲化权；太阴化科；天同化忌"));

        // S8 基线全星盘（无覆写，覆盖大量判据假侧且不抛异常）
        list.add(chart("子", ov(), ""));

        return list;
    }

    // ============ handleDeterminePattern：多张合成盘驱动格局判据真/假两侧 ============
    @Test
    @DisplayName("determinePattern：紫府/日月/机月同梁/夹拱/七杀朝斗/科权禄主等合成盘均完成")
    void determinePatternMatrix() {
        List<Map<String, Object>> charts = patternCharts();
        assertThat(charts).hasSize(9);
        for (int i = 0; i < charts.size(); i++) {
            JobClient c = client();
            worker.handleDeterminePattern(jobWith(charts.get(i)), c);
            verify(c).newCompleteCommand(JOB_KEY);
        }
    }

    // ============ handleInterpretStarNature：命宫主星在/不在、辅星中等、无命宫、空盘 ============
    @Test
    @DisplayName("interpretStarNature：主星命中/辅星命中/无命宫解析/空盘 均完成")
    void interpretStarNatureVariants() {
        List<Map<String, Object>> variants = new ArrayList<>();

        // V1 主星守命且辅星散布（触发化气/形性/问答三块 idx!=null）
        Map<String, Object> v1 = new LinkedHashMap<>();
        v1.put("mainStars", "紫微@子；天机@丑；太阳@寅；武曲@卯；");
        v1.put("auxiliaryStars", "左辅@辰；擎羊@巳；文昌@午；");
        v1.put("chartSkeleton", skFor("子"));
        variants.add(v1);

        // V2 骨架无「命宫」→ mingIdxN<0 分支（不定调句）
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("mainStars", "紫微@子；");
        v2.put("auxiliaryStars", "");
        v2.put("chartSkeleton", "身宫=午;财帛=辰;水二局");
        variants.add(v2);

        // V3 空星盘（所有 row 的 idx 均 null；transformationQi 无本盘定调）
        Map<String, Object> v3 = new LinkedHashMap<>();
        v3.put("mainStars", "");
        v3.put("auxiliaryStars", "");
        v3.put("chartSkeleton", skFor("子"));
        variants.add(v3);

        // V4 命宫主星在命但辅星为空（mingMainFirst 命中、问答无辅）
        Map<String, Object> v4 = new LinkedHashMap<>();
        v4.put("mainStars", "天府@子；太阴@丑；");
        v4.put("auxiliaryStars", "");
        v4.put("chartSkeleton", skFor("子"));
        variants.add(v4);

        for (Map<String, Object> v : variants) {
            JobClient c = client();
            worker.handleInterpretStarNature(jobWith(v), c);
            verify(c).newCompleteCommand(JOB_KEY);
        }
    }

    // ============ judgeTongYuan：注入构造行覆盖支/星/生干条件与举例回退 ============
    @Test
    @DisplayName("judgeTongYuan：支条件命中/未中、星条件、生干已定/未定、举例式回退")
    @SuppressWarnings("unchecked")
    void judgeTongYuanRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"命宫", "-", "-", "-", "举例式断语（诸宫遇福）"}); // hasCond=false → 举例/或注/pend
        rows.add(new String[]{"财帛", "辰", "-", "-", "财帛宫断语"});           // 支条件命中
        rows.add(new String[]{"命宫", "丑", "-", "-", "命宫丑断语"});           // 支条件未中（命在子）
        rows.add(new String[]{"命宫", "-", "贪狼", "-", "见贪狼断语"});         // 星条件（贪狼守命）
        rows.add(new String[]{"命宫", "-", "-", "甲", "生干甲断语"});           // 生干条件（甲）
        setStatic("ONTO_TONGYUAN", rows);

        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(0, Arrays.asList("贪狼"));

        Class<?>[] six = {int.class, Map.class, int.class, String.class, Boolean.class, String.class};
        // 生年干未定（-1）→ 生干条件 rows 走 pend
        String r1 = (String) invoke("judgeTongYuan", six, 0, at, -1, skFor("子"), Boolean.TRUE, "");
        assertThat(r1).contains("同垣");

        // 生年干=甲（0）→ 生干条件命中
        String r2 = (String) invoke("judgeTongYuan", six, 0, at, 0, skFor("子"), Boolean.FALSE, "贪狼化禄");
        assertThat(r2).contains("同垣");

        // 4 参数重载（委托）
        String r3 = (String) invoke("judgeTongYuan",
                new Class<?>[]{int.class, Map.class, int.class, String.class}, 0, at, -1, skFor("子"));
        assertThat(r3).contains("同垣");
    }
}
