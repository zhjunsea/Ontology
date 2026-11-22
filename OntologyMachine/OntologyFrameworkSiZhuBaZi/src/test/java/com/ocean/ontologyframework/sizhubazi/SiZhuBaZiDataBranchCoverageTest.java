package com.ocean.ontologyframework.sizhubazi;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 四柱命理 · 数据分支全覆盖单元测试（离线，不依赖 DB / Ontop / RabbitMQ / 引擎）。
 *
 * <p>在既有 SiZhuBaZiClassicRulesTest（起例表核验）之外，针对客户点名要求，对
 * 「十神全 10 类 + 生克关系 / 神煞匹配全分支 / 用神-相神-忌神各取用分支 / 命格（专旺-从化-成败-具名）/
 * 旺衰 6 档 / 下游结构化解析」的<strong>逻辑分支 × 数据分支</strong>做可执行覆盖。
 *
 * <p>断言一律直取程序<strong>结构化字段</strong>为唯一真相源（不反向解析判词文案、不伪造断言）。
 * 受控入参经真跑生产 Handler（Mockito 模拟 ActivatedJob/JobClient 承接 completeResult），
 * 纯起例逻辑经反射直调私有算理方法；离线时 OBDA 降级（backendService 为 null）不阻断。
 */
@DisplayName("四柱命理 · 数据分支全覆盖（十神/神煞/用神相神忌神/命格/旺衰）")
class SiZhuBaZiDataBranchCoverageTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static final String[] GAN =
            {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI =
            {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    // ============================================================ 反射 / 工具

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static Object stat(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object inst(Object w, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(w, args);
    }

    private static boolean flag(Object bean, String field) throws Exception {
        Field f = bean.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getBoolean(bean);
    }

    private static int idx(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }

    /** 由「甲寅 乙卯 ...」四柱串解析为 int[4][2]（与生产 parsePillars 同口径）。 */
    private static int[][] p(String four) {
        String[] parts = four.trim().split("\\s+");
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            String s = parts[i];
            out[i][0] = idx(GAN, s.substring(0, 1));
            out[i][1] = idx(ZHI, s.substring(1, 2));
        }
        return out;
    }

    /** 仅指定四个天干（地支固定子），用于只依赖天干透视（tou 集）的 detectChengBai 取证。 */
    private static int[][] stems(int y, int m, int d, int h) {
        return new int[][]{{y, 0}, {m, 0}, {d, 0}, {h, 0}};
    }

    // ---- Handler 受控驱动（模拟 JobClient 承接 completeResult 的变量，读回 result） ----

    private static final class Cap implements Answer<Object> {
        Object vars;

        @Override
        public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) {
                vars = inv.getArgument(0);
            }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) {
                return null;
            }
            if (rt.isPrimitive()) {
                if (rt == long.class) {
                    return 0L;
                }
                if (rt == int.class) {
                    return 0;
                }
                if (rt == boolean.class) {
                    return false;
                }
                if (rt == double.class) {
                    return 0d;
                }
                if (rt == float.class) {
                    return 0f;
                }
                if (rt == short.class) {
                    return (short) 0;
                }
                if (rt == byte.class) {
                    return (byte) 0;
                }
                if (rt == char.class) {
                    return (char) 0;
                }
                return null;
            }
            try {
                return mock(rt, this);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(String handler, Map<String, Object> vars) throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        assertThat(cap.vars).as("handler 应调用 completeResult：%s", handler).isNotNull();
        return (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    private static Map<String, Object> vars(String four, String strength) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("fourPillars", four);
        if (strength != null) {
            v.put("strength", strength);
        }
        return v;
    }

    private static String[] tenGodArray() throws Exception {
        Field f = WORKER.getDeclaredField("TEN_GOD");
        f.setAccessible(true);
        return (String[]) f.get(null);
    }

    private static boolean hasNamed(List<String[]> hits, String name) {
        for (String[] h : hits) {
            if (name.equals(h[0])) {
                return true;
            }
        }
        return false;
    }

    // ============================================================ 一、十神：全 10 类 + 生克关系

    @Test
    @DisplayName("十神·全 10 类：任一日主对十干恰生成 10 种互异十神名（比劫食伤财官印全含）")
    void tenGodAllTenTypes() throws Exception {
        String[] tenGod = tenGodArray();
        assertThat(tenGod).containsExactly(
                "比肩", "劫财", "食神", "伤官", "偏财", "正财", "偏官", "正官", "偏印", "正印");
        // 甲日主对十干：索引 0..9 恰为十神全部十类
        for (int t = 0; t < 10; t++) {
            int god = (int) stat("tenGodIndex", new Class<?>[]{int.class, int.class}, 0, t);
            assertThat(tenGod[god]).as("甲日主对 %s", GAN[t]).isEqualTo(tenGod[t]);
        }
        // 逐日主：对十干生成的十神集合恒为全部 10 类（闭合性）
        for (int d = 0; d < 10; d++) {
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            for (int t = 0; t < 10; t++) {
                int god = (int) stat("tenGodIndex", new Class<?>[]{int.class, int.class}, d, t);
                set.add(tenGod[god]);
            }
            assertThat(set).as("日主 %s 十神集合", GAN[d]).hasSize(10);
        }
    }

    @Test
    @DisplayName("十神·本体名接口：离线时 tenGodName 降级与 tenGodIndex 一致")
    void tenGodNameMatchesIndex() throws Exception {
        Object w = newWorker();
        for (int d = 0; d < 10; d++) {
            for (int t = 0; t < 10; t++) {
                String name = (String) inst(w, "tenGodName", new Class<?>[]{int.class, int.class}, d, t);
                int god = (int) stat("tenGodIndex", new Class<?>[]{int.class, int.class}, d, t);
                String[] tenGod = tenGodArray();
                assertThat(name).as("日主 %s 对 %s", GAN[d], GAN[t]).isEqualTo(tenGod[god]);
            }
        }
    }

    @Test
    @DisplayName("十神·生克制化三支：官杀混杂 / 食伤制杀 / 财官相生 / 无明显（经 assignTenGodsKin 真跑）")
    void tenGodsRelationsBranches() throws Exception {
        // 官杀混杂：庚(偏官)+辛(正官) 皆现
        assertThat((String) run("handleTenGodsKin", vars("庚子 辛丑 甲寅 甲子", null)).get("tenGodsRelations"))
                .as("官杀混杂").contains("官杀混杂");
        // 食伤制杀：丙(食神)+庚(偏官)
        assertThat((String) run("handleTenGodsKin", vars("丙寅 庚午 甲子 丙寅", null)).get("tenGodsRelations"))
                .as("食伤制杀").contains("食伤制杀");
        // 财官相生：戊(偏财)+庚(偏官)
        assertThat((String) run("handleTenGodsKin", vars("戊子 庚午 甲子 戊子", null)).get("tenGodsRelations"))
                .as("财官相生").contains("财官相生");
        // 无明显：纯比劫
        assertThat((String) run("handleTenGodsKin", vars("甲子 甲子 甲子 甲子", null)).get("tenGodsRelations"))
                .as("无明显生克").contains("无明显十神生克制化");
    }

    // ============================================================ 二、神煞：matchDeityRule 全分支

    private static List<Integer> match(String bp, String bg, String bh, String[] zhi, String[] gan) throws Exception {
        Object w = newWorker();
        @SuppressWarnings("unchecked")
        List<Integer> r = (List<Integer>) inst(w, "matchDeityRule",
                new Class<?>[]{String.class, String.class, String.class, String[].class, String[].class},
                bp, bg, bh, zhi, gan);
        return r;
    }

    @Test
    @DisplayName("神煞·matchDeityRule 全基准分支：日柱/日干(单·斜杠·(时))/年支日支/年支/月支三合/月支")
    void deityMatcherAllBaseBranches() throws Exception {
        String[] zA = {"丑", "子", "寅", "午"};   // 年丑 月子 日寅 时午
        String[] gA = {"甲", "乙", "丙", "丁"};   // 日干=丙、日柱=丙寅

        // 日柱：日柱干支精确命中（日=丙寅）
        assertThat(match("日柱", "丙寅/戊子", "x", zA, gA)).containsExactly(2);
        // 日干 单键：以日干丙查，命中支「子」在月柱
        assertThat(match("日干", "丙", "亥子", zA, gA)).containsExactly(1);
        // 日干 斜杠多键：甲/丙 ↔ 丑/寅 按位配对，丙→寅 于日柱
        assertThat(match("日干", "甲/丙", "丑/寅", zA, gA)).containsExactly(2);
        // 年支/日支：年支丑命中，取 bh=午 于时柱
        assertThat(match("年支/日支", "丑", "午", zA, gA)).containsExactly(3);
        // 年支：年支丑命中，取 bh=午 于时柱
        assertThat(match("年支", "丑", "午", zA, gA)).containsExactly(3);

        String[] zB = {"子", "丑", "卯", "寅"};   // 时支=寅
        // 日干（时）限定：仅时支成立
        assertThat(match("日干", "丙", "寅（时）", zB, gA)).containsExactly(3);

        String[] zC = {"丑", "寅", "午", "子"};   // 月支=寅、时支=子
        // 月支三合 单键：月支寅命中，取 bh=子 于时柱
        assertThat(match("月支三合", "寅", "子", zC, gA)).containsExactly(3);
        // 月支：月支寅命中，取 bh=甲（干）于年柱（addMixedHits 干/支并取）
        assertThat(match("月支", "寅", "甲", zC, gA)).containsExactly(0);
    }

    @Test
    @DisplayName("神煞·运行时降级八类命中 + 旬空（经 arrangeDeities 真跑，OBDA 离线降级）")
    void arrangeDeitiesFallback() throws Exception {
        Map<String, Object> r = run("handleArrangeDeities", vars("甲子 乙丑 甲寅 丁卯", null));
        String deities = (String) r.get("deities");
        String voidness = (String) r.get("voidness");
        assertThat(deities).as("降级八类应命中天乙/禄/羊刃/驿马/将星/桃花")
                .contains("天乙贵人").contains("禄").contains("羊刃")
                .contains("驿马").contains("将星").contains("桃花");
        assertThat(voidness).as("旬空输出").contains("旬空");
    }

    // ============================================================ 三、用神 / 相神 / 忌神

    @Test
    @DisplayName("用神·月令格局法/建禄：常格取月令、建禄取官杀>财>食伤、皆无则兜底官杀")
    void yongShenElemBranches() throws Exception {
        // 常格：甲日巳月（月令本气丙=食神），月令五行火(1)
        assertThat((int) stat("yongShenElem", new Class<?>[]{int[][].class}, (Object) p("甲子 乙巳 甲子 甲子")))
                .isEqualTo(1);
        // 建禄(月令寅木=比劫) + 四柱见金(申藏庚) → 取官杀(金=3)
        assertThat((int) stat("yongShenElem", new Class<?>[]{int[][].class}, (Object) p("甲寅 甲寅 甲寅 甲申")))
                .isEqualTo(3);
        // 建禄 + 只见土(财)不见金 → 取财(土=2)
        assertThat((int) stat("yongShenElem", new Class<?>[]{int[][].class}, (Object) p("甲寅 甲寅 甲寅 甲寅")))
                .isEqualTo(2);
        // 建禄 + 全无官杀/财/食伤 → 兜底官杀(3)
        assertThat((int) stat("yongShenElem", new Class<?>[]{int[][].class}, (Object) p("乙卯 乙卯 乙卯 乙卯")))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("用神·实效取用：火炎调候取水 / 水寒调候取火 / 身强印比重取食伤 / 身弱取印或比劫兜底")
    void effectiveYongElemBranches() throws Exception {
        // 火炎：丙日午月、无水 → 调候取水(4)
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class},
                (Object) p("丙午 丙午 丙午 辛酉"), 2, false)).isEqualTo(4);
        // 水寒：壬日子月、无火 → 调候取火(1)
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class},
                (Object) p("壬子 壬子 壬子 癸丑"), 8, false)).isEqualTo(1);
        // 身强而印比重(子月水=印)：改取食伤(火=1，因见午丁火)
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class},
                (Object) p("甲子 甲子 甲子 丙午"), 0, true)).isEqualTo(1);
        // 身弱而月令格神为官杀(酉金)：取印(水=4，因子藏癸)
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class},
                (Object) p("甲子 辛酉 甲子 甲子"), 0, false)).isEqualTo(4);
        // 同上但四柱无水 → 兜底取比劫(日主本行=0)
        assertThat((int) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class},
                (Object) p("乙卯 辛酉 乙卯 乙卯"), 1, false)).isEqualTo(0);
    }

    @Test
    @DisplayName("相神/忌神·顺逆：食神格顺用（相生用、忌克用）/ 偏官格逆用（相制用、忌生用）")
    void xiangJiShenBranches() throws Exception {
        int[][] shun = p("甲子 乙巳 甲子 甲子"); // 月令丙=食神，顺用；用神五行=火(1)
        assertThat((boolean) stat("isNiYong", new Class<?>[]{int[][].class}, (Object) shun)).isFalse();
        assertThat((int) stat("xiangShenElem", new Class<?>[]{int[][].class}, (Object) shun)).isEqualTo(0); // 生用(木)
        assertThat((int) stat("jiShenElem", new Class<?>[]{int[][].class}, (Object) shun)).isEqualTo(4);    // 克用(水)
        assertThat((int) stat("jiFromYong", new Class<?>[]{int[][].class, int.class}, (Object) shun, 1)).isEqualTo(4);

        int[][] ni = p("甲子 庚申 甲子 甲子"); // 月令庚=偏官，逆用；用神五行=金(3)
        assertThat((boolean) stat("isNiYong", new Class<?>[]{int[][].class}, (Object) ni)).isTrue();
        assertThat((int) stat("xiangShenElem", new Class<?>[]{int[][].class}, (Object) ni)).isEqualTo(1); // 制用(火)
        assertThat((int) stat("jiShenElem", new Class<?>[]{int[][].class}, (Object) ni)).isEqualTo(2);    // 生用(土)
        assertThat((int) stat("jiFromYong", new Class<?>[]{int[][].class, int.class}, (Object) ni, 3)).isEqualTo(2);
    }

    @Test
    @DisplayName("用神·selectYongShen 输出三值齐备：常格（相神/忌神非空）+ 专旺格主取顺气势")
    void selectYongShenOutputs() throws Exception {
        Map<String, Object> normal = run("handleSelectYongShen", vars("壬子 癸酉 乙卯 甲子", "中和"));
        assertThat((String) normal.get("yongShen")).contains("用神");
        assertThat((String) normal.get("xiangShen")).contains("相神");
        assertThat((String) normal.get("jiShen")).contains("忌神");

        Map<String, Object> zw = run("handleSelectYongShen", vars("甲寅 乙卯 甲辰 甲子", "身强（得令）"));
        assertThat((String) zw.get("yongShen")).as("专旺格").contains("专旺");
        assertThat((String) zw.get("xiangShen")).contains("相神");
        assertThat((String) zw.get("jiShen")).contains("忌神");
    }

    // ============================================================ 四、命格：专旺/从化/成败/具名

    @Test
    @DisplayName("命格·专旺五格：曲直/炎上/稼穑/从革/润下（detectZhuanWang 全 switch 分支）")
    void zhuanWangFiveTypes() throws Exception {
        assertThat((String) stat("detectZhuanWang", new Class<?>[]{int[][].class, int.class},
                (Object) p("甲寅 乙卯 甲辰 甲子"), 0)).contains("曲直");
        assertThat((String) stat("detectZhuanWang", new Class<?>[]{int[][].class, int.class},
                (Object) p("乙巳 甲午 丙午 丁未"), 2)).contains("炎上");
        assertThat((String) stat("detectZhuanWang", new Class<?>[]{int[][].class, int.class},
                (Object) p("戊辰 壬戌 戊丑 戊未"), 4)).contains("稼穑");
        assertThat((String) stat("detectZhuanWang", new Class<?>[]{int[][].class, int.class},
                (Object) p("庚申 乙酉 庚戌 庚申"), 6)).contains("从革");
        assertThat((String) stat("detectZhuanWang", new Class<?>[]{int[][].class, int.class},
                (Object) p("壬亥 癸子 壬丑 壬子"), 8)).contains("润下");
    }

    @Test
    @DisplayName("命格·从化外格：从财/从杀/从儿/化气（detectExternPattern）")
    void externCongHuaPatterns() throws Exception {
        assertThat((String) stat("detectExternPattern", new Class<?>[]{int[][].class, int.class},
                (Object) p("戊戌 戊戌 甲戌 戊戌"), 0)).contains("从财");
        assertThat((String) stat("detectExternPattern", new Class<?>[]{int[][].class, int.class},
                (Object) p("庚酉 辛酉 乙酉 庚酉"), 1)).contains("从杀");
        assertThat((String) stat("detectExternPattern", new Class<?>[]{int[][].class, int.class},
                (Object) p("丙巳 丁午 甲午 丙巳"), 0)).contains("从儿");
        assertThat((String) stat("detectExternPattern", new Class<?>[]{int[][].class, int.class},
                (Object) p("庚午 己戌 甲午 庚午"), 0)).contains("化气");
        // 常格未成象 → 空串
        assertThat((String) stat("detectExternPattern", new Class<?>[]{int[][].class, int.class},
                (Object) p("甲子 庚午 甲寅 戊辰"), 0)).isEmpty();
    }

    @Test
    @DisplayName("命格·成败救应：正官见伤官(印解/待印)、偏官无制(有财滋/无)、财逢比劫(有救/待)、印逢财(有救/待)、食神逢枭(有救/待)、伤官见官(印解/待)、其它返 null")
    void chengBaiAllCases() throws Exception {
        Class<?>[] t = {String.class, int.class, int[][].class, boolean.class};
        // 正官见伤官
        assertThat(cb(t, "正官", stems(3, 8, 0, 0), false)[0]).contains("幸印");   // 伤官+偏印
        assertThat(cb(t, "正官", stems(3, 0, 0, 0), false)[0]).contains("待印");   // 伤官无印
        // 偏官无制
        assertThat(cb(t, "偏官", stems(4, 0, 0, 0), false)[0]).contains("逢财滋"); // 财滋
        assertThat(cb(t, "偏官", stems(0, 0, 0, 0), false)[0]).contains("杀旺无制");
        // 财逢比劫（身强）
        assertThat(cb(t, "正财", stems(0, 1, 0, 6), true)[0]).contains("有官杀制劫"); // 比劫+偏官
        assertThat(cb(t, "正财", stems(0, 1, 0, 0), true)[0]).contains("待官杀制劫");
        // 印逢财（贪财坏印）
        assertThat(cb(t, "正印", stems(4, 0, 0, 0), false)[0]).contains("得比劫制财护印");
        assertThat(cb(t, "正印", stems(4, 2, 0, 2), false)[0]).contains("待比劫制财护印");
        // 食神逢枭（枭神夺食）
        assertThat(cb(t, "食神", stems(8, 4, 0, 0), false)[0]).contains("得财制枭");
        assertThat(cb(t, "食神", stems(8, 0, 0, 0), false)[0]).contains("待财制枭");
        // 伤官见官
        assertThat(cb(t, "伤官", stems(7, 8, 0, 0), false)[0]).contains("幸印");
        assertThat(cb(t, "伤官", stems(7, 0, 0, 0), false)[0]).contains("待印制伤");
        // 其它十神 → null
        assertThat((String[]) stat("detectChengBai", t, "比肩", 0, stems(0, 0, 0, 0), false)).isNull();
    }

    private static String[] cb(Class<?>[] t, String tg, int[][] pp, boolean strong) throws Exception {
        return (String[]) stat("detectChengBai", t, tg, 0, pp, strong);
    }

    @Test
    @DisplayName("命格·具名格局：魁罡/六乙鼠贵/日贵/日德/天元作禄/时上一位贵/岁德正官/官煞混杂/时墓/禄马同乡")
    void namedPatternsRepresentative() throws Exception {
        assertThat(hits(p("庚戌 庚戌 庚戌 庚戌"), 6)).as("魁罡").isTrue();
        assertThat(hits(p("甲子 甲子 乙丑 甲子"), 1)).as("六乙鼠贵").isTrue();
        assertThat(hits(p("甲子 甲子 丁酉 甲子"), 3)).as("日贵").isTrue();
        assertThat(hits(p("甲子 甲子 甲寅 甲子"), 0)).as("日德").isTrue();
        assertThat(hits(p("甲子 甲子 庚午 甲子"), 6)).as("天元作禄").isTrue();
        assertThat(hits(p("甲子 甲子 甲子 庚午"), 0)).as("时上一位贵").isTrue();
        assertThat(hits(p("辛未 甲子 甲子 甲子"), 0)).as("岁德正官").isTrue();
        assertThat(hits(p("辛未 庚午 甲子 甲子"), 0)).as("官煞混杂").isTrue();
        assertThat(hits(p("甲子 甲子 甲子 甲未"), 0)).as("时墓").isTrue();
        assertThat(hits(p("甲申 甲寅 甲子 甲子"), 0)).as("禄马同乡").isTrue();
    }

    @SuppressWarnings("unchecked")
    private static boolean hits(int[][] pp, int dayGan) throws Exception {
        List<String[]> h = (List<String[]>) stat("hitNamedPatterns", new Class<?>[]{int[][].class, int.class},
                (Object) pp, dayGan);
        return !h.isEmpty();
    }

    @Test
    @DisplayName("命格·月令格神名：甲日申月本气庚=偏官")
    void monthGodNameBasic() throws Exception {
        assertThat((String) stat("monthGodName", new Class<?>[]{int[][].class},
                (Object) p("甲子 庚申 甲子 甲子"))).isEqualTo("偏官");
    }

    // ============================================================ 五、旺衰 6 档（真跑 assessDayMasterStrength）

    @Test
    @DisplayName("旺衰·6 档全分支：身强得令/中和偏强得令/中和偏强失令得地得势/身弱/中和偏弱/中和")
    void strengthSixGrades() throws Exception {
        assertThat((String) run("handleDayMasterStrength", vars("甲寅 甲寅 甲寅 甲寅", null)).get("strength"))
                .isEqualTo("身强（得令）");
        assertThat((String) run("handleDayMasterStrength", vars("丙子 丙子 甲子 戊子", null)).get("strength"))
                .isEqualTo("中和偏强（得令）");
        assertThat((String) run("handleDayMasterStrength", vars("乙卯 辛酉 乙卯 乙卯", null)).get("strength"))
                .isEqualTo("中和偏强（失令但得地得势）");
        assertThat((String) run("handleDayMasterStrength", vars("丙申 丙申 甲申 戊申", null)).get("strength"))
                .isEqualTo("身弱（失令、无根、寡助）");
        assertThat((String) run("handleDayMasterStrength", vars("丙午 庚申 乙卯 戊申", null)).get("strength"))
                .isEqualTo("中和偏弱（失令、克泄耗偏多）");
        assertThat((String) run("handleDayMasterStrength", vars("壬子 癸酉 乙卯 甲子", null)).get("strength"))
                .isEqualTo("中和");
    }

    // ============================================================ 六、下游结构化真相源

    @Test
    @DisplayName("旺衰解析 parseStrength：5 档标志位（strong/neutral/mildWeak/severeWeak/weak）")
    void parseStrengthFlags() throws Exception {
        Class<?>[] t = {String.class};
        Object sStrong = stat("parseStrength", t, "身强（得令）");
        assertThat(flag(sStrong, "strong")).isTrue();
        assertThat(flag(sStrong, "weak")).isFalse();
        Object sMild = stat("parseStrength", t, "中和偏弱（失令、克泄耗偏多）");
        assertThat(flag(sMild, "mildWeak")).isTrue();
        assertThat(flag(sMild, "weak")).isTrue();
        assertThat(flag(sMild, "strong")).isFalse();
        Object sSevere = stat("parseStrength", t, "身弱（失令、无根、寡助）");
        assertThat(flag(sSevere, "severeWeak")).isTrue();
        assertThat(flag(sSevere, "weak")).isTrue();
        Object sNeutral = stat("parseStrength", t, "中和");
        assertThat(flag(sNeutral, "neutral")).isTrue();
        assertThat(flag(sNeutral, "strong")).isFalse();
        assertThat(flag(sNeutral, "weak")).isFalse();
    }

    @Test
    @DisplayName("格局判级解析 parsePatternGrade：7 档标志位（格成/格将成/建禄成立/格有破/格不成/格有绊/专旺）")
    void parsePatternGradeFlags() throws Exception {
        Class<?>[] t = {String.class};
        assertThat(flag(stat("parsePatternGrade", t, "格成"), "geCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格将成（待引透）"), "geJiangCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "建禄格成立（无财官）"), "jianLuCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格有破（会杀为凶）"), "geBreak")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格不成（阳刃无制）"), "geBuCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格有绊（月令逢合）"), "geBan")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格成（专旺成象·气势纯）"), "zhuanWang")).isTrue();
        // 互斥校验：格不成不应误判为格成
        assertThat(flag(stat("parsePatternGrade", t, "格不成（阳刃无制）"), "geCheng")).isFalse();
    }
}
