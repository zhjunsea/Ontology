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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 定点补齐 selectYongShen 的 hasSec 调候变体、buildFortuneAnalysis 的 fireExcess/短运支/非法运支分支。
 */
@DisplayName("定点补齐·调候hasSec变体 / 岁运fireExcess·边界运支")
class SiZhuBaZiGapTargetedTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;
    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    private static int idx(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) { if (arr[i].equals(v)) { return i; } }
        return -1;
    }

    private static int[][] p4(String s) {
        String[] a = s.trim().split("\\s+");
        int[][] p = new int[4][2];
        for (int i = 0; i < 4; i++) {
            p[i][0] = idx(GAN, a[i].substring(0, 1));
            p[i][1] = idx(ZHI, a[i].substring(1, 2));
        }
        return p;
    }

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static void setField(Object t, String n, Object v) throws Exception {
        Class<?> c = t.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(n); f.setAccessible(true); f.set(t, v); return; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(n);
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) { m.put(kv[i], kv[i + 1]); }
        return m;
    }

    private static SiZhuBaZiOntologyJobWorker workerWithObda(Function<String, List<Map<String, String>>> f) throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> f.apply(inv.getArgument(0)));
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    private static final class Cap implements Answer<Object> {
        Object vars;
        @Override public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) { vars = inv.getArgument(0); }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) return null;
            if (rt.isPrimitive()) { return rt == long.class ? (Object) 0L : rt == int.class ? (Object) 0
                    : rt == boolean.class ? (Object) false : rt == double.class ? (Object) 0d
                    : rt == float.class ? (Object) 0f : (Object) null; }
            try { return mock(rt, this); } catch (Throwable t) { return null; }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> drive(SiZhuBaZiOntologyJobWorker w, String handler, Map<String, Object> vars) throws Exception {
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        return cap.vars == null ? null : (Map<String, Object>) ((Map<String, Object>) cap.vars).get("result");
    }

    private static String invokeBuildFortune(SiZhuBaZiOntologyJobWorker w, int[][] p, String[] fortunes,
                                             int yongElem, int jiElem, int dayElem) throws Exception {
        Method m = WORKER.getDeclaredMethod("buildFortuneAnalysis",
                int[][].class, LocalDateTime.class, double.class, String[].class,
                int.class, int.class, int.class, boolean.class, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(w, p, LocalDateTime.of(1990, 5, 15, 10, 30), 8.0, fortunes,
                yongElem, jiElem, dayElem, true, "男", "身强（得令）");
    }

    @Test
    @DisplayName("调候 hasSec 变体：x=null/空/【待考】/—/癸水 全分支（selectYongShen 必完成）")
    void selectYongShenHasSecVariants() throws Exception {
        Object[] xs = {null, "", "【待考】", "—", "癸水"};
        for (Object xv : xs) {
            final List<Map<String, String>> thRows = (xv == null)
                    ? List.of(row("label", "子月甲调候", "p", "丙火", "text", "调候原文", "source", "穷通宝鉴"))
                    : List.of(row("label", "子月甲调候", "p", "丙火", "x", (String) xv, "text", "调候原文", "source", "穷通宝鉴"));
            SiZhuBaZiOntologyJobWorker w = workerWithObda(q -> q.contains("TiaohouClause") ? thRows : new ArrayList<>());
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("fourPillars", "甲子 丙子 甲子 甲子"); // dayGan=甲, monthBranch=子 → 调候 label「子月甲调候」
            vars.put("strength", "身强（得令）");
            vars.put("gender", "男");
            assertThat(drive(w, "handleSelectYongShen", vars)).as("selectYongShen 应完成, x=%s", xv).isNotNull();
        }
    }

    @Test
    @DisplayName("岁运 fireExcess/边界运支：湿燥土·短运支(甲)·非法运支(甲X)·吉凶/冲合刑空 分支")
    void buildFortuneFireExcessEdge() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        int[][] p = new int[][]{{2, 6}, {2, 6}, {2, 6}, {2, 6}}; // 丙午全柱：dayElem=火(1)
        // fireExcess = (dayElem==1 && yongElem==4) 成立：走 fireExcessFortuneSegment 分支；含 辰4/丑1/未7/戌10/午6/子0
        String out = invokeBuildFortune(w, p, new String[]{"甲辰", "乙丑", "丙未", "丁戌", "戊午", "庚子", "甲X", "甲"}, 4, 0, 1);
        assertThat(out).contains("大运方向");
        // 非 fireExcess（yongElem=0 木）→ 覆盖 good 吉/凶/平、冲提纲/冲日支/合日支/刑/旬空等 verdict 分支
        String out2 = invokeBuildFortune(w, p,
                new String[]{"庚申", "辛酉", "壬子", "癸亥", "甲寅", "乙卯", "丙午", "丁巳"}, 0, 3, 1);
        assertThat(out2).contains("大运方向");
    }

    @Test
    @DisplayName("专旺格 5 类（曲直/炎上/稼穑/从革/润下）+ 破格：determinePattern/selectYongShen 必完成")
    void zhuanWangCharts() throws Exception {
        String[] charts = {
                "乙亥 己卯 甲寅 丁未",  // 曲直(木)
                "丙寅 甲午 丙戌 戊午",  // 炎上(火)
                "戊辰 壬戌 戊丑 己未",  // 稼穑(土)
                "辛巳 己酉 辛卯 辛丑",  // 从革(金)
                "壬申 甲子 壬辰 庚子",  // 润下(水)
                "庚亥 己卯 甲寅 丁未",  // 曲直见金→破格
                "丙寅 甲午 丙戌 戊子",  // 炎上见水(子)→破格
        };
        for (String fp : charts) {
            SiZhuBaZiOntologyJobWorker w = workerWithObda(q -> new ArrayList<>());
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("fourPillars", fp);
            v.put("strength", "身强（得令）");
            v.put("gender", "男");
            assertThat(drive(w, "handleDeterminePattern", v)).as("determinePattern 必完成 %s", fp).isNotNull();
            assertThat(drive(w, "handleSelectYongShen", v)).as("selectYongShen 必完成 %s", fp).isNotNull();
        }
    }

    @Test
    @DisplayName("fireExcessFortuneSegment 全分支：10 干 × 12 支直接覆盖（火炎土燥局）")
    void fireExcessSegmentDirect() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        int[][] p = new int[][]{{2, 6}, {2, 3}, {2, 0}, {2, 6}}; // 2午+卯+day子（触发子午冲、寅午、卯戌等）
        Method m = WORKER.getDeclaredMethod("fireExcessFortuneSegment", int[][].class, int.class, int.class);
        m.setAccessible(true);
        for (int g = 0; g < 10; g++) {
            for (int z = 0; z < 12; z++) {
                assertThat((String) m.invoke(null, p, g, z)).as("fireExcessFortuneSegment g=%s z=%s", g, z).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("从格/化气 定向：从财/从杀/从儿/化气 四盘 → determinePattern/selectYongShen 必完成（覆盖从格分支）")
    void externPatternCharts() throws Exception {
        String[] charts = {
                "庚申 辛酉 丙子 庚申", // 从财格（丙火日，金旺无根无助）
                "壬子 癸子 丙子 壬子", // 从杀格（丙火日，水旺无根无助）
                "戊丑 己丑 丙丑 戊丑", // 从儿格（丙火日，土旺无根无助）
                "丙子 辛子 丙子 丙子", // 化气格（丙辛合化水，化神子月得令）
        };
        for (String fp : charts) {
            SiZhuBaZiOntologyJobWorker w = workerWithObda(q -> new ArrayList<>());
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("fourPillars", fp);
            v.put("strength", "身弱（失令、无根、寡助）");
            v.put("gender", "男");
            assertThat(drive(w, "handleDeterminePattern", v)).as("determinePattern 必完成 %s", fp).isNotNull();
            assertThat(drive(w, "handleSelectYongShen", v)).as("selectYongShen 必完成 %s", fp).isNotNull();
        }
    }

    @Test
    @DisplayName("buildPilotBasis / pilotAge：birth=null→空、顺/逆两路、起运岁≥0")
    void pilotBasisAndAgeDirect() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Method mb = WORKER.getDeclaredMethod("buildPilotBasis", LocalDateTime.class, String.class, double.class);
        mb.setAccessible(true);
        assertThat((String) mb.invoke(w, null, "顺", 8.0)).isEmpty();
        LocalDateTime dt = LocalDateTime.of(1990, 5, 15, 10, 30);
        assertThat((String) mb.invoke(w, dt, "顺", 8.0)).isNotEmpty();
        assertThat((String) mb.invoke(w, dt, "逆", 8.0)).isNotEmpty();
        Method pa = WORKER.getDeclaredMethod("pilotAge", LocalDateTime.class, String.class);
        pa.setAccessible(true);
        assertThat((Double) pa.invoke(w, dt, "顺")).isGreaterThanOrEqualTo(0.0);
        assertThat((Double) pa.invoke(w, dt, "逆")).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("pickPattern 操作数：name==null 跳过 / 去重 / 达标 break / nameKw 过滤")
    @SuppressWarnings("unchecked")
    void pickPatternOperandEdges() throws Exception {
        java.util.List<Map<String, String>> rows = new java.util.ArrayList<>();
        rows.add(row("name", null, "text", "x", "src", "s"));
        rows.add(row("name", "食神格", "text", "x", "src", "s"));
        rows.add(row("name", "食神格", "text", "y", "src", "s"));
        rows.add(row("name", "伤官格", "text", "z", "src", "s"));
        SiZhuBaZiOntologyJobWorker w = workerWithObda(q -> q.contains("pattern:Pattern") ? rows : new ArrayList<>());
        Method m = WORKER.getDeclaredMethod("pickPattern", String.class, String.class, String.class, int.class);
        m.setAccessible(true);
        java.util.List<String> r = (java.util.List<String>) m.invoke(w, "pattern:Pattern", "体用", null, 2);
        assertThat(r).hasSize(2);
        java.util.List<String> r2 = (java.util.List<String>) m.invoke(w, "pattern:Pattern", "体用", "伤官", 2);
        assertThat(r2).hasSize(1);
    }

    @Test
    @DisplayName("合婚互补矩阵：6自身×5对偶 生辰，覆盖 complementary 三条件/纳音生克/生肖/神煞 全分支")
    void marriageComplementMatrix() throws Exception {
        String[] selfs = {
                "SOLAR=1950-01-15T02:00;GENDER=男;SUBJECT=男命;MODE=合婚",
                "SOLAR=1965-06-20T10:00;GENDER=女;SUBJECT=女命;MODE=合婚",
                "SOLAR=1978-11-05T14:00;GENDER=男;SUBJECT=男命;MODE=合婚",
                "SOLAR=1990-05-15T08:00;GENDER=男;SUBJECT=男命;MODE=合婚",
                "SOLAR=2001-09-09T18:00;GENDER=女;SUBJECT=女命;MODE=合婚",
                "SOLAR=2012-03-03T23:00;GENDER=男;SUBJECT=男命;MODE=合婚",
        };
        String[] partners = {"1960-02-02 04:00", "1972-07-07 12:00", "1985-12-12 23:30",
                "1999-04-04 16:16", "2008-08-08 08:08"};
        SiZhuBaZiOntologyJobWorker w = newWorker();
        for (String cb : selfs) {
            for (String p : partners) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("chartBase", cb);
                v.put("partnerBirth", p);
                v.put("fourPillars", "庚午 辛巳 庚辰 戊子");
                v.put("strength", "身强（得令）");
                assertThat(drive(w, "handleMarriageMatch", v)).as("合婚必完成 self=%s partner=%s", cb, p).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("神煞逐条跳过分支：DEITY_SKIP/淫/妨害/季节/命局/概念/正官/日干长生/时柱纳音/纳音/流年/日时/日柱年月 + 旬空")
    @SuppressWarnings("unchecked")
    void deitiesSkipBranches() throws Exception {
        java.util.List<Map<String, String>> rows = new java.util.ArrayList<>();
        // 各类跳过行（dn 安全、bp 命中某跳过关键词）
        rows.add(row("dn", "", "bp", "x"));                     // dn 空
        rows.add(row("dn", "旬空神", "bp", "日柱", "bg", "甲子旬")); // 日柱+旬
        rows.add(row("dn", "神", "bp", "旬"));                    // bp=旬
        rows.add(row("dn", "红鸾", "bp", "年支", "bg", "子"));     // DEITY_SKIP
        rows.add(row("dn", "淫欲", "bp", "年支", "bg", "子"));     // 含淫
        rows.add(row("dn", "妨害某", "bp", "年支", "bg", "子"));   // 含妨害
        rows.add(row("dn", "神", "bp", "季节论", "bg", "子"));     // 季节
        rows.add(row("dn", "神", "bp", "命局总论", "bg", "子"));   // 命局
        rows.add(row("dn", "神", "bp", "概念类", "bg", "子"));     // 概念
        rows.add(row("dn", "神", "bp", "正官格", "bg", "子"));     // 正官
        rows.add(row("dn", "神", "bp", "日干长生", "bg", "子"));   // 日干长生
        rows.add(row("dn", "神", "bp", "时柱纳音", "bg", "子"));   // 时柱纳音
        rows.add(row("dn", "神", "bp", "纳音论", "bg", "子"));     // 纳音
        rows.add(row("dn", "神", "bp", "流年", "bg", "子"));       // 流年
        rows.add(row("dn", "神", "bp", "日时断", "bg", "子"));     // 日时
        rows.add(row("dn", "神", "bp", "日柱/年月", "bg", "子"));  // 日柱/年月
        // 有效命中行（覆盖 天乙/桃花/劫煞 标签与 matchDeityRule 各基准）
        rows.add(row("dn", "金神", "bp", "日柱", "bg", "甲午"));          // 日柱命中
        rows.add(row("dn", "天乙贵人", "bp", "年支", "bg", "子", "bh", "寅"));
        rows.add(row("dn", "桃花", "bp", "日支", "bg", "午", "bh", "卯"));
        rows.add(row("dn", "劫煞", "bp", "日支", "bg", "午", "bh", "卯"));
        rows.add(row("dn", "某时煞", "bp", "日干", "bg", "甲", "bh", "子（时）")); // 覆盖 matchDeityRule「（时）」限定
        SiZhuBaZiOntologyJobWorker w = workerWithObda(q -> q.contains("DeityRule") ? rows : new ArrayList<>());
        int[][] p = new int[][]{{0, 0}, {2, 2}, {0, 6}, {3, 3}}; // 甲子 丙寅 甲午 丁卯
        Method m = WORKER.getDeclaredMethod("deitiesFromOntology", int[][].class);
        m.setAccessible(true);
        java.util.List<String> out = (java.util.List<String>) m.invoke(w, (Object) p);
        assertThat(out).isNotNull();
    }

    private static final String[] GAN2 = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI2 = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    private static int idx2(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) { if (arr[i].equals(v)) { return i; } }
        return -1;
    }

    private static int[][] p4b(String s) {
        String[] a = s.trim().split("\\s+");
        int[][] p = new int[4][2];
        for (int i = 0; i < 4; i++) { p[i][0] = idx2(GAN2, a[i].substring(0, 1)); p[i][1] = idx2(ZHI2, a[i].substring(1, 2)); }
        return p;
    }

    private static Object stat2(String name, Class<?>[] t, Object... a) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, t);
        m.setAccessible(true);
        try { return m.invoke(null, a); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) { throw re; }
            if (c instanceof Error er) { throw er; }
            throw e;
        }
    }

    @Test
    @DisplayName("citeDts 变体：null/无关键词/从/化/专旺各关键字/三合三会")
    @SuppressWarnings("unchecked")
    void citeDtsVariants() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithObda(q -> {
            if (q.contains("AssimilationPattern")) return new ArrayList<>(java.util.Arrays.asList(
                    row("name", "从象", "text", "从象原文", "src", "滴天髓"),
                    row("name", "化象", "text", "化象原文", "src", "滴天髓")));
            if (q.contains("ImagePattern")) return new ArrayList<>(java.util.Arrays.asList(
                    row("name", "形象", "text", "形象原文", "src", "滴天髓")));
            if (q.contains("DirectionPattern")) return new ArrayList<>(java.util.Arrays.asList(
                    row("name", "方局", "text", "方局原文", "src", "滴天髓")));
            return new ArrayList<>();
        });
        Method m = WORKER.getDeclaredMethod("citeDts", String.class, String.class);
        m.setAccessible(true);
        assertThat((java.util.List<String>) m.invoke(w, null, null)).isEmpty();
        m.invoke(w, "普通格", "无合无冲");
        assertThat((java.util.List<String>) m.invoke(w, "从格", "")).isNotEmpty();
        assertThat((java.util.List<String>) m.invoke(w, "化格", "")).isNotEmpty();
        for (String kw : new String[]{"专旺", "从强", "曲直", "炎上", "稼穑", "从革", "润下"}) {
            m.invoke(w, kw, "");
        }
        m.invoke(w, "普通格", "三合局");
        m.invoke(w, "普通格", "三会局");
    }

    @Test
    @DisplayName("addBranchHits / addMixedHits：null/空/命中/去重 分支")
    @SuppressWarnings("unchecked")
    void addHitsDirect() throws Exception {
        String[] zhiAt = {"子", "丑", "寅", "卯"};
        String[] ganAt = {"甲", "乙", "丙", "丁"};
        Method ab = WORKER.getDeclaredMethod("addBranchHits", java.util.List.class, String.class, String[].class);
        ab.setAccessible(true);
        java.util.List<Integer> o1 = new java.util.ArrayList<>();
        ab.invoke(null, o1, (Object) null, zhiAt);   // null → return
        ab.invoke(null, o1, "", zhiAt);              // empty → return
        ab.invoke(null, o1, "子丑", zhiAt);          // 命中 0,1
        ab.invoke(null, o1, "子丑", zhiAt);          // 去重
        assertThat(o1).contains(0, 1);
        Method am = WORKER.getDeclaredMethod("addMixedHits", java.util.List.class, String.class, String[].class, String[].class);
        am.setAccessible(true);
        java.util.List<Integer> o2 = new java.util.ArrayList<>();
        am.invoke(null, o2, (Object) null, zhiAt, ganAt);
        am.invoke(null, o2, "", zhiAt, ganAt);
        am.invoke(null, o2, "子甲", zhiAt, ganAt);   // 支子 idx0、干甲 idx0
        assertThat(o2).contains(0);
    }

    @Test
    @DisplayName("buildFortuneAnalysis 强局喜忌：jiElem=食伤/财/官/他 → ssJi/caiJi/guanJi 各分支 + fireExcess伤官")
    void buildFortuneOperands() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        int[][] p = p4b("甲子 甲子 甲子 甲子"); // dayElem=木(0), strong=true
        invokeBuildFortune(w, p, new String[]{"乙丑", "丙寅"}, 0, 1, 0); // ssJi true
        invokeBuildFortune(w, p, new String[]{"乙丑", "丙寅"}, 0, 2, 0); // caiJi true
        invokeBuildFortune(w, p, new String[]{"乙丑", "丙寅"}, 0, 3, 0); // guanJi true
        invokeBuildFortune(w, p, new String[]{"乙丑", "丙寅"}, 0, 0, 0); // 皆 false
        int[][] p2 = p4b("丙午 戊午 丙午 丙午"); // dayElem火(1)、戊土食伤透干；yongElem=4水→fireExcess
        assertThat(invokeBuildFortune(w, p2, new String[]{"壬子"}, 4, 0, 1)).contains("大运方向");
    }

    @Test
    @DisplayName("tianKeDiChong：非法干(X子) → false")
    void tianKeInvalidGan() throws Exception {
        assertThat((Boolean) stat2("tianKeDiChong", new Class<?>[]{String.class, String.class}, "X子", "庚午")).isFalse();
    }
}






