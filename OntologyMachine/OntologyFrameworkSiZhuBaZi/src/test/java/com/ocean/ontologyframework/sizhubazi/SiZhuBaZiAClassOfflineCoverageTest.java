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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A 类补齐 · 「条文/引据族」离线可补分支覆盖（不需真实 Ontop/MySQL/RabbitMQ/引擎）。
 *
 * <p>背景：{@code readReadingFamily/pickReading/pickPattern/pickYongShen/citeFu/citeDts/
 * citeZiping/citeClassic/citeTechniqueBody/citePatternCatalog/citeTheory/citeAuspiceTimePlace/
 * citeProgeny/clauseDayHour/clauseStemSeat/clauseMonthBranch/clauseKinship/clauseLadyFortunes/
 * clauseSexagenaryNature/clauseTiaohou} 等「经典引据」方法均经 {@link OBDAHandler#executeAboxQuery}
 * 取本体条文；在无库离线场景 {@code backendService==null} 时全部走空集降级分支（原覆盖为 0）。
 *
 * <p>本类以 Mockito 桩 {@link BackendService}+{@link OBDAHandler}，按 SPARQL 体返回受控行，
 * 直接驱动（反射）各读取方法，覆盖「命中/未命中、空/非空、标签/类目过滤、去重、限量、截断、
 * 分句取半句、兜底篇名」等全部数据/逻辑分支；并驱动生产 Handler 验证「六书引据回填接线」生效。
 */
@DisplayName("A类补齐·经典引据族离线分支覆盖（假 OBDA 返回行驱动）")
class SiZhuBaZiAClassOfflineCoverageTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    // ============================================================ 反射 / 桩

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

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @SafeVarargs
    private static List<Map<String, String>> list(Map<String, String>... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }

    /** 假 OBDA：按 SPARQL 体内容返回受控条文行（覆盖整族读取方法的非空分支）。 */
    private static OBDAHandler fakeObda() {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> canned(inv.getArgument(0)));
        return obda;
    }

    /** 把 worker 的 backendService 指向「假 OBDA」的 BackendService 桩。 */
    private static SiZhuBaZiOntologyJobWorker workerWithFakeObda() throws Exception {
        OBDAHandler obda = fakeObda();
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    /** 按 SPARQL 体返回受控行集（覆盖各读取族的键：label/cat/text/src/name/subject/source/p/x 等）。 */
    private static List<Map<String, String>> canned(String q) {
        if (q.contains("DeityRule")) return deityRows();
        if (q.contains("DayHourClause")) return list(row("text", "甲午日丁卯时：日时断原文", "source", "三命通会卷八"));
        if (q.contains("TiaohouClause")) return list(row("label", "寅月甲调候", "p", "丙火", "x", "癸水", "text", "调候原文", "source", "穷通宝鉴"));
        if (q.contains("StemBranchAuspice")) return list(row("subject", "甲乙坐支吉凶", "text", "甲日得时；乙日失时；", "source", "三命通会卷四"));
        if (q.contains("MonthBranchDayStemAuspice")) return list(row("subject", "寅月得日干吉凶", "text", "甲日寅月吉；乙日寅月平；", "source", "三命通会卷四"));
        if (q.contains("KinshipTheory")) return list(row("text", "六亲说原文", "source", "神峰通考"));
        if (q.contains("LadyFortune")) return list(
                row("label", "纯", "cat", "上", "text", "女命纯原文", "source", "三命通会卷七"),
                row("label", "旺夫克子", "cat", "中", "text", "旺夫克子原文", "source", "三命通会卷七"));
        if (q.contains("TimePlaceFortune")) return list(row("label", "论木·时地吉凶", "cat", "", "text", "时地原文", "src", "三命通会卷四"));
        if (q.contains("ProgenyPrediction")) return list(row("label", "孕生男女", "cat", "", "text", "孕生原文", "src", "三命通会卷四"));
        if (q.contains("DynamicStaticTheory")) return list(row("label", "动静说", "cat", "", "text", "动静原文", "src", "神峰通考"));
        if (q.contains("HeadCoveringTheory")) return list(row("label", "盖头说", "cat", "", "text", "盖头原文", "src", "神峰通考"));
        if (q.contains("BingYao")) return list(row("label", "病药说", "text", "病药原文"));
        if (q.contains("AssimilationPattern")) return list(row("label", "从象", "name", "从象", "text", "从象原文", "src", "滴天髓"));
        if (q.contains("ImagePattern")) return list(row("label", "形象", "name", "形象", "text", "形象原文", "src", "滴天髓"));
        if (q.contains("DirectionPattern")) return list(row("label", "方局", "name", "方局", "text", "方局原文", "src", "滴天髓"));
        if (q.contains("CategoryVerdict")) return list(row("text", "分类断语原文", "source", "三命通会"));
        if (q.contains("sexagenaryNatureText")) return list(row("text", "甲午砂中金，性质原文"));
        if (q.contains("ReadingItem")) return readingItems();
        if (q.contains("CategoryFortune")) return fuRows();
        if (q.contains("pattern:Pattern")) return list(row("label", "食神格", "name", "食神格", "text", "食神格原文", "src", "三命通会卷五"));
        return new ArrayList<>();
    }

    private static List<Map<String, String>> readingItems() {
        return list(
                row("label", "甲体象", "cat", "十干体象", "text", "甲木参天，脱胎要火", "src", "三命通会"),
                row("label", "午体象", "cat", "十二支体象", "text", "午火炎上", "src", "三命通会"),
                row("label", "论用神成败救应", "cat", "子平真诠", "text", "论用神成败救应原文", "src", "子平真诠"),
                row("label", "中和", "cat", "滴天髓", "text", "中和原文", "src", "滴天髓"),
                row("label", "论五行生成", "cat", "三命通会", "text", "论五行生成原文", "src", "三命通会"));
    }

    private static List<Map<String, String>> fuRows() {
        return list(
                row("label", "官星赋", "cat", "", "text", "官星赋原文", "src", "渊海子平"),
                row("label", "财神论", "cat", "", "text", "财神论原文", "src", "渊海子平"),
                row("label", "继善篇", "cat", "", "text", "继善篇原文", "src", "渊海子平"),
                row("label", "明通赋", "cat", "", "text", "明通赋原文", "src", "渊海子平"));
    }

    /** 假神煞起例（覆盖日柱/日干/年支/日支/月支三合/月支 各匹配分支 + 旬空/DEITY_SKIP/桃花/天乙/劫煞）。 */
    private static List<Map<String, String>> deityRows() {
        return list(
                row("id", "1", "bp", "日柱", "bg", "甲午", "bh", "", "dn", "金神", "da", "", "cat", "", "src", "s"),
                row("id", "2", "bp", "日干", "bg", "甲/乙", "bh", "子/丑", "dn", "福星贵人", "da", "", "cat", "", "src", "s"),
                row("id", "3", "bp", "年支", "bg", "子", "bh", "寅", "dn", "月德贵人", "da", "", "cat", "", "src", "s"),
                row("id", "4", "bp", "日支", "bg", "午", "bh", "卯", "dn", "将星", "da", "", "cat", "", "src", "s"),
                row("id", "5", "bp", "月支三合", "bg", "寅/卯", "bh", "午/子", "dn", "驿马", "da", "", "cat", "", "src", "s"),
                row("id", "6", "bp", "月支", "bg", "寅", "bh", "卯", "dn", "华盖", "da", "", "cat", "", "src", "s"),
                row("id", "7", "bp", "日柱", "bg", "甲子旬", "bh", "", "dn", "旬空神", "da", "", "cat", "", "src", "s"),
                row("id", "8", "bp", "年支", "bg", "子", "bh", "子", "dn", "红鸾", "da", "", "cat", "", "src", "s"),
                row("id", "9", "bp", "年支", "bg", "子", "bh", "子", "dn", "", "da", "", "cat", "", "src", "s"),
                row("id", "10", "bp", "季节论", "bg", "子", "bh", "子", "dn", "某神", "da", "", "cat", "", "src", "s"),
                row("id", "11", "bp", "日支", "bg", "午", "bh", "卯", "dn", "桃花", "da", "", "cat", "", "src", "s"),
                row("id", "12", "bp", "年支", "bg", "子", "bh", "寅", "dn", "天乙贵人", "da", "", "cat", "", "src", "s"),
                row("id", "13", "bp", "日支", "bg", "午", "bh", "卯", "dn", "劫煞", "da", "", "cat", "", "src", "s"));
    }

    // ---- Handler 受控驱动（Mockito 承接 completeResult 的 result 变量） ----
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
                if (rt == short.class) return (short) 0;
                if (rt == byte.class) return (byte) 0;
                if (rt == char.class) return (char) 0;
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
    private static Map<String, Object> runWith(SiZhuBaZiOntologyJobWorker w, String handler, Map<String, Object> vars) throws Exception {
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

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> v = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            v.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return v;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // ============================================================ 测试

    @Test
    @DisplayName("读取族·readReadingFamily/pickReading/pickPattern/pickYongShen 分支")
    @SuppressWarnings("unchecked")
    void readingFamilyPickers() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithFakeObda();
        List<Map<String, String>> all = (List<Map<String, String>>) call(w, "readReadingFamily",
                new Class<?>[]{String.class, String.class}, "reading:ReadingItem", null);
        assertThat(all).hasSize(5);
        List<Map<String, String>> filtered = (List<Map<String, String>>) call(w, "readReadingFamily",
                new Class<?>[]{String.class, String.class}, "reading:ReadingItem", "子平真诠");
        assertThat(filtered).hasSize(1);
        List<String> pr = (List<String>) call(w, "pickReading",
                new Class<?>[]{String.class, String.class, String.class, String.class, int.class},
                "reading:ReadingItem", "子平真诠", "论用神", "子平真诠", 1);
        assertThat(pr).hasSize(1);
        assertThat(pr.get(0)).contains("子平真诠·论用神成败救应");
        List<String> prNone = (List<String>) call(w, "pickReading",
                new Class<?>[]{String.class, String.class, String.class, String.class, int.class},
                "reading:ReadingItem", "子平真诠", "不存在关键词", "X", 1);
        assertThat(prNone).isEmpty();
        List<String> pp = (List<String>) call(w, "pickPattern",
                new Class<?>[]{String.class, String.class, String.class, int.class}, "pattern:Pattern", "体用", "食神", 1);
        assertThat(pp).hasSize(1);
        List<String> ppNone = (List<String>) call(w, "pickPattern",
                new Class<?>[]{String.class, String.class, String.class, int.class}, "pattern:Pattern", "体用", "伤官", 1);
        assertThat(ppNone).isEmpty();
        List<String> py = (List<String>) call(w, "pickYongShen",
                new Class<?>[]{String.class, String.class, String.class, int.class}, "yongshen:BingYao", "病药", "病药说", 1);
        assertThat(py).hasSize(1);
        List<String> pyNone = (List<String>) call(w, "pickYongShen",
                new Class<?>[]{String.class, String.class, String.class, int.class}, "yongshen:BingYao", "其余", "病药说", 1);
        assertThat(pyNone).isEmpty();
    }

    @Test
    @DisplayName("静态工具·pickFragment/clip/mergeCitations/fuKeywords 全分支")
    void staticHelpers() throws Exception {
        Method pf = WORKER.getDeclaredMethod("pickFragment", String.class, String.class);
        pf.setAccessible(true);
        assertThat((String) pf.invoke(null, null, "甲")).isEmpty();
        assertThat((String) pf.invoke(null, "   ", "甲")).isEmpty();
        assertThat((String) pf.invoke(null, "甲日得时；乙日失时", "甲")).isEqualTo("甲日得时");
        assertThat((String) pf.invoke(null, "得乙日；丙", "乙")).isEqualTo("得乙日");
        assertThat((String) pf.invoke(null, "无此干", "甲")).isEqualTo("无此干");
        Method clip = WORKER.getDeclaredMethod("clip", String.class, int.class);
        clip.setAccessible(true);
        assertThat((String) clip.invoke(null, null, 5)).isEmpty();
        assertThat((String) clip.invoke(null, "abc", 5)).isEqualTo("abc");
        assertThat((String) clip.invoke(null, "abcdefgh", 5)).endsWith("…").hasSize(6);
        Method mc = WORKER.getDeclaredMethod("mergeCitations", String.class, List.class);
        mc.setAccessible(true);
        assertThat((String) mc.invoke(null, "T", null)).isEmpty();
        assertThat((String) mc.invoke(null, "T", new ArrayList<String>())).isEmpty();
        assertThat((String) mc.invoke(null, "T", Arrays.asList("a", "b"))).contains("【T】");
        Method fk = WORKER.getDeclaredMethod("fuKeywords", java.util.Collection.class);
        fk.setAccessible(true);
        List<?> kw = (List<?>) fk.invoke(null, new ArrayList<>(Arrays.asList("正官", "正财", "正印", "食神", "比肩")));
        assertThat(kw.toString()).contains("官", "财", "印", "子", "比");
        assertThat((List<?>) fk.invoke(null, new ArrayList<String>())).isEmpty();
    }

    @Test
    @DisplayName("引据族·citeFu/citeDts/citeZiping/citeClassic/citeTechniqueBody/citePatternCatalog/citeTheory/citeAuspice/citeProgeny/clause*")
    @SuppressWarnings("unchecked")
    void citeCluster() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithFakeObda();
        List<String> fu = (List<String>) call(w, "citeFu", new Class<?>[]{java.util.Collection.class},
                new ArrayList<>(Arrays.asList("官", "财")));
        assertThat(fu).hasSize(2);
        List<String> fuFallback = (List<String>) call(w, "citeFu", new Class<?>[]{java.util.Collection.class},
                new ArrayList<String>());
        assertThat(fuFallback).isNotEmpty();
        assertThat(fuFallback.get(0)).contains("继善篇");
        assertThat((List<String>) call(w, "citeDts", new Class<?>[]{String.class, String.class}, "专旺格", "三合局")).isNotEmpty();
        assertThat((List<String>) call(w, "citeDts", new Class<?>[]{String.class, String.class}, "从格化格", "")).isNotEmpty();
        assertThat((List<String>) call(w, "citeZiping", new Class<?>[]{String.class}, "论用神成败救应")).hasSize(1);
        assertThat((List<String>) call(w, "citeClassic", new Class<?>[]{String.class, String.class}, "滴天髓", "中和")).hasSize(1);
        assertThat((List<String>) call(w, "citeClassic", new Class<?>[]{String.class, String.class}, "三命通会", "论五行生成")).hasSize(1);
        assertThat((List<String>) call(w, "citeTechniqueBody", new Class<?>[]{String.class, String.class}, "甲", "午")).hasSize(2);
        assertThat((List<String>) call(w, "citePatternCatalog", new Class<?>[]{List.class}, new ArrayList<String[]>())).isEmpty();
        List<String[]> hits = new ArrayList<>();
        hits.add(new String[]{"食神格", "x", "y"});
        assertThat((List<String>) call(w, "citePatternCatalog", new Class<?>[]{List.class}, hits)).hasSize(1);
        assertThat((List<String>) call(w, "citeTheory", new Class<?>[]{})).isNotEmpty();
        assertThat((List<String>) call(w, "citeAuspiceTimePlace", new Class<?>[]{String.class}, "木")).isNotEmpty();
        assertThat((List<String>) call(w, "citeProgeny", new Class<?>[]{})).isNotEmpty();
        assertThat((String) call(w, "clauseDayHour", new Class<?>[]{String.class, String.class}, "甲午", "丁卯")).contains("日时断");
        assertThat((String) call(w, "clauseStemSeat", new Class<?>[]{String.class}, "甲")).contains("十干坐支");
        assertThat((String) call(w, "clauseMonthBranch", new Class<?>[]{String.class, String.class}, "寅", "甲")).contains("月支断");
        assertThat((String) call(w, "clauseKinship", new Class<?>[]{})).contains("六亲说");
        assertThat((List<?>) call(w, "clauseLadyFortunes", new Class<?>[]{})).hasSize(2);
        assertThat((String) call(w, "clauseSexagenaryNature", new Class<?>[]{String.class}, "甲午")).contains("砂中金");
        assertThat((String) call(w, "clauseSexagenaryNature", new Class<?>[]{String.class}, "甲")).isNull();
        Map<String, String> th = (Map<String, String>) call(w, "clauseTiaohou", new Class<?>[]{String.class, String.class}, "甲", "寅");
        assertThat(th).isNotNull();
        assertThat(th.get("p")).isEqualTo("丙火");
        assertThat((Map<String, String>) call(w, "clauseTiaohou", new Class<?>[]{String.class, String.class}, "庚", "申")).isNull();
    }

    @Test
    @DisplayName("神煞·deitiesFromOntology 多基准匹配/跳过/桃花·天乙·飞刃分支（假 deity_rule 行）")
    @SuppressWarnings("unchecked")
    void deitiesBranches() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithFakeObda();
        // 甲子 丙寅 甲午 丁卯 → zhiAt=[子,寅,午,卯] ganAt=[甲,丙,甲,丁]
        int[][] p = new int[][]{{0, 0}, {2, 2}, {0, 6}, {3, 3}};
        List<String> out = (List<String>) call(w, "deitiesFromOntology", new Class<?>[]{int[][].class}, (Object) p);
        assertThat(out).isNotNull();
        String joined = String.join("|", out);
        assertThat(joined).contains("金神").contains("天乙贵人").contains("桃花").contains("驿马").contains("华盖");
    }

    @Test
    @DisplayName("神煞·deitiesFromOntology 空规则返回 null（无库降级判定依据）")
    void deitiesEmpty() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker(); // backendService null → queryClauses 空 → rules.isEmpty → null
        int[][] p = new int[][]{{0, 0}, {2, 2}, {0, 6}, {3, 3}};
        assertThat(call(w, "deitiesFromOntology", new Class<?>[]{int[][].class}, (Object) p)).isNull();
    }

    @Test
    @DisplayName("接线·determinePattern/selectYongShen/categoryVerdicts/nayin/child 回填六书引据（假 OBDA）")
    void wiringCitations() throws Exception {
        Map<String, Object> vars = map(
                "fourPillars", "甲午 丙寅 甲午 丁卯",
                "strength", "身强（得令）",
                "gender", "男",
                "deities", "羊刃",
                "ganzhiRelations", "三合局");
        Map<String, Object> dp = runWith(workerWithFakeObda(), "handleDeterminePattern", vars);
        assertThat(str(dp.get("patternResult"))).contains("格局典籍引据");
        Map<String, Object> ys = runWith(workerWithFakeObda(), "handleSelectYongShen", vars);
        assertThat(str(ys.get("yongShenMethods"))).contains("用神典籍引据");
        Map<String, Object> cv = runWith(workerWithFakeObda(), "handleCategoryVerdicts", vars);
        assertThat(str(cv.get("clauseCitations"))).isNotBlank().contains("日时断");
        Map<String, Object> ny = runWith(workerWithFakeObda(), "handleNayinHiddenStems",
                map("fourPillars", "甲午 丙寅 甲午 丁卯"));
        assertThat(str(ny.get("dayPillarTraits"))).contains("六十甲子性质");
        Map<String, Object> ch = runWith(workerWithFakeObda(), "handleChildChart",
                map("fourPillars", "甲午 丙寅 甲午 丁卯"));
        assertThat(str(ch.get("childVerdict"))).contains("子息典籍引据");
    }

    @Test
    @DisplayName("queryClauses·无后端(空)/handler为null(空)/OBDA异常降级(空)")
    @SuppressWarnings("unchecked")
    void queryClausesEdges() throws Exception {
        // 1) backendService == null
        SiZhuBaZiOntologyJobWorker w0 = newWorker();
        assertThat((List<?>) call(w0, "queryClauses", new Class<?>[]{String.class}, "SELECT ?x WHERE {}")).isEmpty();
        // 2) backendService 非空但 getObdaHandler()==null
        BackendService bsNull = mock(BackendService.class);
        when(bsNull.getObdaHandler()).thenReturn(null);
        SiZhuBaZiOntologyJobWorker w1 = newWorker();
        setField(w1, "backendService", bsNull);
        assertThat((List<?>) call(w1, "queryClauses", new Class<?>[]{String.class}, "SELECT ?x WHERE {}")).isEmpty();
        // 3) OBDA 抛异常 → 降级空集
        BackendService bsErr = mock(BackendService.class);
        OBDAHandler obdaErr = mock(OBDAHandler.class);
        when(bsErr.getObdaHandler()).thenReturn(obdaErr);
        when(obdaErr.executeAboxQuery(anyString())).thenThrow(new RuntimeException("boom"));
        SiZhuBaZiOntologyJobWorker w2 = newWorker();
        setField(w2, "backendService", bsErr);
        assertThat((List<?>) call(w2, "queryClauses", new Class<?>[]{String.class}, "SELECT ?x WHERE {}")).isEmpty();
    }

    private static boolean drive(SiZhuBaZiOntologyJobWorker w, String handler, Map<String, Object> vars) throws Exception {
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        return cap.vars != null; // completeResult 被调用即视为正常完成
    }

    @Test
    @DisplayName("多命盘扫描·核心业务 Handler 数据分支矩阵（10干×12支×强/弱/男女）——真断言：核心Handler必完成")
    void sweepBusinessBranches() throws Exception {
        String[] gans = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
        String[] zhis = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
        String[] strengths = {"身强（得令）", "中和", "身弱（失令、无根、寡助）", "中和偏强（得令）"};
        // 仅依赖 fourPillars/strength/gender/deities 的核心 Handler：必须完成（否则是真实缺陷）
        java.util.Set<String> core = new java.util.HashSet<>(Arrays.asList(
                "handleDeterminePattern", "handleSelectYongShen", "handleFemaleChart",
                "handleCategoryVerdicts", "handleNayinHiddenStems", "handleArrangeDeities", "handleFortuneCycles"));
        String[] handlers = {"handleDeterminePattern", "handleSelectYongShen", "handleJudgeChartLevel",
                "handleFemaleChart", "handleCategoryVerdicts", "handleFortuneCycles", "handleNayinHiddenStems",
                "handleArrangeDeities", "handleMarriageMatch"};
        int n = 0;
        for (int a = 0; a < 10; a++) {
            for (int b = 0; b < 12; b++) {
                if (n >= 60) { break; }
                String fp = gans[a] + zhis[b] + " " + gans[(a + 3) % 10] + zhis[(b + 2) % 12] + " "
                        + gans[a] + zhis[(b + 4) % 12] + " " + gans[(a + 5) % 10] + zhis[(b + 6) % 12];
                Map<String, Object> vars = map(
                        "fourPillars", fp,
                        "strength", strengths[n % strengths.length],
                        "gender", (n % 2 == 0 ? "男" : "女"),
                        "deities", "羊刃、桃花、相冲",
                        "chartLevel", "平常格（格成而财官未通门户）",
                        "patternGrade", "格成",
                        "granularity", "时",
                        "chartBase", "SOLAR=1990-05-15T10:30;GENDER=男;SUBJECT=男命;MODE=单命",
                        "startAge", 8.0,
                        "fortuneSequence", "甲子,乙丑,丙寅,丁卯,戊辰,己巳,庚午,辛未",
                        "partnerBirth", "1992-08-08 08:08 女",
                        "fortuneTrend", "顺");
                for (String h : handlers) {
                    boolean completed = drive(workerWithFakeObda(), h, vars);
                    if (core.contains(h)) {
                        assertThat(completed)
                                .as("核心 Handler [%s] 应在合法命盘 [%s] 上正常完成（未完成=真实缺陷）", h, fp)
                                .isTrue();
                    }
                }
                n++;
            }
        }
        assertThat(n).isGreaterThan(0);
    }

    @Test
    @DisplayName("A类深数据·格局/用神 120 盘矩阵（10日干×12月支，覆盖十格/建禄/阳刃/杂气）——真断言必完成")
    void patternAndYongShenMatrix() throws Exception {
        String[] gans = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
        String[] zhis = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
        String[] strengths = {"身强（得令）", "中和", "身弱（失令、无根、寡助）", "中和偏强（失令但得地得势）"};
        int n = 0;
        for (int a = 0; a < 10; a++) {
            for (int mb = 0; mb < 12; mb++) {
                String y = "甲" + zhis[(mb + 6) % 12];
                String m = gans[(a + mb) % 10] + zhis[mb];
                String d = gans[a] + zhis[(a + 3) % 12];
                String h = gans[(a + 5) % 10] + zhis[(a + 7) % 12];
                String fp = y + " " + m + " " + d + " " + h;
                Map<String, Object> vars = map(
                        "fourPillars", fp,
                        "strength", strengths[n % strengths.length],
                        "gender", (n % 2 == 0 ? "男" : "女"),
                        "deities", "羊刃、将星",
                        "ganzhiRelations", (n % 3 == 0 ? "三合局" : "无合无冲"),
                        "chartLevel", "平常格（格成而财官未通门户）",
                        "patternGrade", "格成");
                assertThat(drive(workerWithFakeObda(), "handleDeterminePattern", vars))
                        .as("determinePattern 应在合法盘 [%s] 完成", fp).isTrue();
                assertThat(drive(workerWithFakeObda(), "handleSelectYongShen", vars))
                        .as("selectYongShen 应在合法盘 [%s] 完成", fp).isTrue();
                n++;
            }
        }
        assertThat(n).isEqualTo(120);
    }
}









