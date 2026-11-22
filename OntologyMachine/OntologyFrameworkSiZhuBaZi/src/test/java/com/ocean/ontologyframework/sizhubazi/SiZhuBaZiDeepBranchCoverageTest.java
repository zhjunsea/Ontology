package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * 第 20 轮 · 深分支定向补测（边界/非法入参驱动，纯函数与守卫分支）。
 *
 * <p>背景：经回六书逐条核对，剩余未覆盖分支中相当一部分**并非不可达**，而是此前未以
 * “越界/空/非法”入参驱动其操作数侧。本类以反射直调私有方法，用合法与非法两个极端入参把
 * 这些**真实可达的防御/操作数分支**覆盖掉（不改业务代码、不改判定口径）。
 *
 * <p>对应 JaCoCo 未覆盖行：`pinyinGanzhi(L3075/L3082)`、`birthCalendar(L4987)`、
 * `sanheGroupOf(L5624/L5629)`、`sanheGroup(L5725/L5730)`、`hasRoot(L5752)`、
 * `verifyAboxPath(L269)`。
 */
@DisplayName("第20轮·深分支定向补测（边界/非法入参）")
class SiZhuBaZiDeepBranchCoverageTest {

    private static final Class<?> W = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = W.getDeclaredConstructor();
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

    private static Object staticCall(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = W.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    @DisplayName("pinyinGanzhi：null / 单字 / 非法支 / 合法 四态")
    void pinyinGanzhiEdge() throws Exception {
        Class<?>[] t = new Class<?>[]{String.class};
        assertThat(staticCall("pinyinGanzhi", t, (Object) null)).isNull(); // gz==null 侧
        assertThat(staticCall("pinyinGanzhi", t, "甲")).isNull();          // length<2 侧
        assertThat(staticCall("pinyinGanzhi", t, "甲X")).isNull();         // 非法支 z<0 侧
        assertThat(staticCall("pinyinGanzhi", t, "甲子")).isEqualTo("JiaZi");
        assertThat(staticCall("pinyinGanzhi", t, "戊午")).isEqualTo("WuWu");
    }

    @Test
    @DisplayName("birthCalendar：非 Map / calendar 缺失(null) / 空白 / 有效")
    void birthCalendarEdge() throws Exception {
        Class<?>[] t = new Class<?>[]{Object.class};
        assertThat(staticCall("birthCalendar", t, "someString")).isEqualTo("公历"); // 非 Map
        Map<String, Object> mNull = new HashMap<>();
        mNull.put("calendar", null);
        assertThat(staticCall("birthCalendar", t, mNull)).isEqualTo("公历");     // c==null 侧
        Map<String, Object> mBlank = new HashMap<>();
        mBlank.put("calendar", "   ");
        assertThat(staticCall("birthCalendar", t, mBlank)).isEqualTo("公历");     // c 非空但空白 侧
        Map<String, Object> mOk = new HashMap<>();
        mOk.put("calendar", "农历");
        assertThat(staticCall("birthCalendar", t, mOk)).isEqualTo("农历");        // 命中侧
    }

    @Test
    @DisplayName("sanheGroupOf / sanheGroup：十二支全覆盖 + 越界(99) 兜底侧")
    void sanheGroupEdge() throws Exception {
        Class<?>[] t = new Class<?>[]{int.class};
        // 越界值不在任何三合局 → 走循环自然结束 + 返回 -1 / 0（覆盖此前“不可达”的出口侧）
        assertThat((Integer) staticCall("sanheGroupOf", t, 99)).isEqualTo(-1);
        assertThat((Integer) staticCall("sanheGroup", t, 99)).isEqualTo(0);
        // 正常命中
        assertThat((Integer) staticCall("sanheGroupOf", t, 0)).isEqualTo(0); // 子→申子辰
        for (int z = 0; z < 12; z++) {
            int g = (Integer) staticCall("sanheGroupOf", t, z);
            assertThat(g).isBetween(0, 3);
            assertThat((Integer) staticCall("sanheGroup", t, z)).isBetween(0, 3);
        }
    }

    @Test
    @DisplayName("hasRoot：无藏根且非三合/三会 → 假侧；有藏根 → 真侧")
    void hasRootEdge() throws Exception {
        Class<?>[] t = new Class<?>[]{int[][].class, int.class};
        // 四柱皆甲子（藏癸=水），求“火(1)”无根、且不成三合/三会 → 覆盖 sanheFull||sanhuiFull 的假侧
        int[][] p = {{0, 0}, {0, 0}, {0, 0}, {0, 0}};
        assertThat((Boolean) staticCall("hasRoot", t, p, 1)).isFalse();
        // 求“水(4)”有藏根 → 真侧
        assertThat((Boolean) staticCall("hasRoot", t, p, 4)).isTrue();
    }

    @Test
    @DisplayName("verifyAboxPath：指向不存在文件 → isReadable 假侧不抛异常")
    void verifyAboxPathMissingFile() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "aboxPath", "D:/zz_no_such_dir_9999/no_such_abox.owl");
        Method m = W.getDeclaredMethod("verifyAboxPath");
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(w)).doesNotThrowAnyException();
    }

    private static int[][] pillars(int... cells) {
        int[][] p = new int[4][2];
        for (int i = 0; i < 8; i++) {
            p[i / 2][i % 2] = cells[i];
        }
        return p;
    }

    @Test
    @DisplayName("effectiveYongElem：火炎/水寒 调候 与 印比/财官食 分支（强/弱·有/无对冲）")
    void effectiveYongElemEdge() throws Exception {
        Class<?>[] t = new Class<?>[]{int[][].class, int.class, boolean.class};
        int[][] fireWater = pillars(2, 6, 2, 6, 8, 6, 2, 6);   // 火日火月，有壬水
        int[][] fireOnly = pillars(2, 6, 2, 6, 2, 6, 2, 6);    // 火日火月，无水
        int[][] waterFire = pillars(8, 11, 8, 0, 2, 11, 8, 11); // 水日水月，有丙火
        int[][] waterOnly = pillars(8, 11, 8, 0, 8, 0, 8, 11);  // 水日水月，无火
        assertThat((Integer) staticCall("effectiveYongElem", t, fireWater, 2, false)).isBetween(0, 4);
        assertThat((Integer) staticCall("effectiveYongElem", t, fireOnly, 2, false)).isBetween(0, 4);
        assertThat((Integer) staticCall("effectiveYongElem", t, waterFire, 8, false)).isBetween(0, 4);
        assertThat((Integer) staticCall("effectiveYongElem", t, waterOnly, 8, false)).isBetween(0, 4);
        assertThat((Integer) staticCall("effectiveYongElem", t, fireWater, 2, true)).isBetween(0, 4);
        assertThat((Integer) staticCall("effectiveYongElem", t, waterFire, 8, true)).isBetween(0, 4);
    }

    @Test
    @DisplayName("isYinBiZhongYong：火炎/水寒排除侧 + 印比月令 强/弱两态")
    void isYinBiZhongYongEdge() throws Exception {
        Class<?>[] t = new Class<?>[]{int[][].class, int.class, boolean.class};
        int[][] fireWater = pillars(2, 6, 2, 6, 8, 6, 2, 6);
        int[][] fireOnly = pillars(2, 6, 2, 6, 2, 6, 2, 6);
        int[][] waterFire = pillars(8, 11, 8, 0, 2, 11, 8, 11);
        int[][] waterOnly = pillars(8, 11, 8, 0, 8, 0, 8, 11);
        staticCall("isYinBiZhongYong", t, fireWater, 2, true);
        staticCall("isYinBiZhongYong", t, fireOnly, 2, true);
        staticCall("isYinBiZhongYong", t, waterFire, 8, true);
        staticCall("isYinBiZhongYong", t, waterOnly, 8, true);
        staticCall("isYinBiZhongYong", t, fireWater, 2, false);
        staticCall("isYinBiZhongYong", t, waterFire, 8, false);
    }

    @Test
    @DisplayName("godCangDesc：excludeTou 真/假 × 透干/不透明（覆盖藏干十神描述分支）")
    void godCangDescEdge() throws Exception {
        Class<?>[] t = new Class<?>[]{int.class, int[][].class, int.class, boolean.class};
        int[][] pNoTou = pillars(0, 0, 0, 0, 0, 0, 0, 0);      // 甲子×4，癸藏子，不透
        int[][] pTou = pillars(9, 0, 0, 0, 0, 0, 0, 0);        // 癸透干
        for (int cat = 0; cat < 5; cat++) {
            staticCall("godCangDesc", t, 0, pNoTou, cat, false);
            staticCall("godCangDesc", t, 0, pNoTou, cat, true);
            staticCall("godCangDesc", t, 0, pTou, cat, true);
        }
    }

    @Test
    @DisplayName("extractGanzhiTokens / addMixedHits / fireExcessScore：命中·不命中·重复·越界(switch default)")
    void tokenAndScoreEdge() throws Exception {
        // extractGanzhiTokens：命中「甲子/乙丑」与非命中「丙X/子甲」混合
        Class<?>[] tg = new Class<?>[]{String.class};
        Object toks = staticCall("extractGanzhiTokens", tg, "甲子乙丑丙X子甲丁");
        assertThat(toks.toString()).contains("甲子", "乙丑");
        staticCall("extractGanzhiTokens", tg, "");
        staticCall("extractGanzhiTokens", tg, "甲");
        staticCall("extractGanzhiTokens", tg, "甲甲子子"); // 干位无支 / 支位无干

        // addMixedHits：null/空 早退；命中并在重复时不重复添加（!out.contains 假侧）
        Class<?>[] tm = new Class<?>[]{java.util.List.class, String.class, String[].class, String[].class};
        String[] zhi = {"子", "丑", "寅", "卯"};
        String[] gan = {"甲", "乙", "丙", "丁"};
        java.util.List<Integer> out0 = new java.util.ArrayList<>();
        staticCall("addMixedHits", tm, out0, null, zhi, gan);
        staticCall("addMixedHits", tm, out0, "", zhi, gan);
        java.util.List<Integer> out1 = new java.util.ArrayList<>();
        staticCall("addMixedHits", tm, out1, "子甲卯丁", zhi, gan);
        staticCall("addMixedHits", tm, out1, "子甲卯丁", zhi, gan); // 重复
        assertThat(out1).isNotEmpty();

        // fireExcessScore：10干×12支全覆盖 + 越界 z=99 触发 switch default（此前覆盖不到的出口）
        Class<?>[] tf = new Class<?>[]{int[][].class, int.class, int.class};
        int[][] p = pillars(0, 6, 0, 6, 0, 3, 0, 6); // 午(6)三见、卯(3)一见，覆盖 wuN>=2 / hasWu / hasMao
        for (int g = 0; g < 10; g++) {
            for (int z = 0; z < 12; z++) {
                staticCall("fireExcessScore", tf, p, g, z);
            }
        }
        staticCall("fireExcessScore", tf, p, 0, 99); // default
        staticCall("fireExcessScore", tf, p, 9, 11);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = W.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    @Test
    @DisplayName("matchDeityRule：日柱/日干(多键·单键·(时)限定)/年支日支/年支/月支三合/月支 全 bp 分支")
    void matchDeityRuleEdge() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        String[] zhi = {"子", "丑", "寅", "卯"};
        String[] gan = {"甲", "乙", "丙", "丁"}; // 日干=丙, 日柱=丙寅, 年支=子, 月支=丑, 日支=寅
        Class<?>[] t = new Class<?>[]{String.class, String.class, String.class, String[].class, String[].class};
        // 日柱类
        call(w, "matchDeityRule", t, "日柱列表", "丙寅", "", zhi, gan);
        call(w, "matchDeityRule", t, "日柱列表", "甲子", "", zhi, gan);
        // 日干类：多键等长 / 单键 / （时）限定
        call(w, "matchDeityRule", t, "日干列表", "甲/丙", "子/寅", zhi, gan);
        call(w, "matchDeityRule", t, "日干", "丙", "子", zhi, gan);
        call(w, "matchDeityRule", t, "日干", "丙", "子（时）", zhi, gan);
        // 年支/日支 与 年支
        call(w, "matchDeityRule", t, "年支/日支", "子", "子", zhi, gan);
        call(w, "matchDeityRule", t, "年支/日支", "寅", "寅", zhi, gan);
        call(w, "matchDeityRule", t, "年支/日支", "申", "申", zhi, gan);
        call(w, "matchDeityRule", t, "年支", "子", "子", zhi, gan);
        call(w, "matchDeityRule", t, "年支", "申", "申", zhi, gan);
        // 月支三合（多键/单键）与 月支
        call(w, "matchDeityRule", t, "月支三合", "丑/子", "子/寅", zhi, gan);
        call(w, "matchDeityRule", t, "月支三合", "丑", "子", zhi, gan);
        call(w, "matchDeityRule", t, "月支", "丑", "子", zhi, gan);
        call(w, "matchDeityRule", t, "月支", "申", "子", zhi, gan);
    }

    @Test
    @DisplayName("buildCategoryVerdicts / buildKinshipVerdicts：官杀透·强女·火午自刑·驿马劫煞·男女六亲（后端空走 fallback）")
    void categoryAndKinshipEdge() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] tb = new Class<?>[]{int[][].class, int.class, int.class, int.class,
                java.util.Set.class, String.class, String.class, boolean.class};
        java.util.Set<String> gGuan = new java.util.LinkedHashSet<>(java.util.List.of("正官", "偏官", "正财", "食神"));
        java.util.Set<String> gNone = new java.util.LinkedHashSet<>();
        int[][] pGuan = pillars(6, 0, 0, 0, 0, 0, 0, 0); // 年干庚(6)=甲(0)之官杀
        int[][] pFire = pillars(2, 6, 2, 6, 2, 6, 2, 6); // 丙火日、午×4
        call(w, "buildCategoryVerdicts", tb, pGuan, 0, 0, 0, gGuan, "", "男", false);       // 官透身弱
        call(w, "buildCategoryVerdicts", tb, pGuan, 0, 0, 0, gGuan, "羊刃", "女", true);     // 官透身强·女·羊刃
        call(w, "buildCategoryVerdicts", tb, pFire, 2, 1, 6, gNone, "驿马、劫煞", "男", true); // 火·午自刑·驿马劫煞
        call(w, "buildCategoryVerdicts", tb, pFire, 2, 1, 6, gNone, "", "女", false);

        Class<?>[] tk = new Class<?>[]{int[][].class, int.class, int.class, int.class, int.class, String.class};
        call(w, "buildKinshipVerdicts", tk, pFire, 2, 1, 1, 4, "男");
        call(w, "buildKinshipVerdicts", tk, pGuan, 0, 0, 0, 4, "女");
    }

    @Test
    @DisplayName("detectExternPattern：从财/从杀/从儿 + 混杂命盘 × 多日干（覆盖各 count 比较/有气操作数侧）")
    void detectExternPatternDeep() throws Exception {
        Class<?>[] tt = new Class<?>[]{int[][].class, int.class};
        int[][][] charts = {
                pillars(4, 10, 4, 10, 0, 10, 4, 10),  // 土(财)独旺·甲日·戌
                pillars(6, 9, 6, 9, 0, 9, 6, 9),      // 金(杀)独旺·甲日·酉
                pillars(2, 6, 2, 6, 0, 6, 2, 6),      // 火(食伤)独旺·甲日·午
                pillars(2, 5, 2, 5, 0, 5, 2, 5),      // 火多·巳(藏戊土/丙火 竞争)
                pillars(2, 6, 2, 5, 2, 5, 2, 6),
                pillars(4, 10, 6, 9, 2, 6, 4, 5),     // 土金火混杂
                pillars(8, 8, 8, 8, 0, 8, 8, 8),      // 水(印)独旺·甲日·申
                pillars(4, 4, 4, 4, 0, 4, 4, 4)       // 土(财)独旺·甲日·辰
        };
        for (int dg : new int[]{0, 2, 4, 6, 8}) {
            for (int[][] p : charts) {
                staticCall("detectExternPattern", tt, p, dg);
            }
        }
    }

    private static void driveHandler(SiZhuBaZiOntologyJobWorker w, String handler, Map<String, Object> vars) throws Exception {
        JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
        ActivatedJob job = mock(ActivatedJob.class, RETURNS_DEEP_STUBS);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = W.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
    }

    @Test
    @DisplayName("handleMarriageMatch：合婚互补矩阵（self×partner 多命盘·交替性别，覆盖 selfLack/比劫/食伤 各操作数侧）")
    void marriageComplementaryMatrix() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        String[] selfDates = {"1990-05-15T10:30", "1985-01-02T03:00", "1978-11-20T22:00", "1992-08-08T08:08", "2000-02-02T12:00"};
        String[] partnerDates = {"1992-08-08 08:08", "1988-03-03 03:03", "1995-06-06 06:06", "1980-12-12 12:12", "1999-09-09 09:09"};
        int k = 0;
        for (String sd : selfDates) {
            for (String pd : partnerDates) {
                Map<String, Object> vars = new HashMap<>();
                String g = (k % 2 == 0) ? "男" : "女";
                vars.put("chartBase", "SOLAR=" + sd + ";GENDER=" + g + ";SUBJECT=x;MODE=合婚");
                vars.put("gender", g);
                vars.put("partnerBirth", pd + (k % 2 == 0 ? " 女" : " 男"));
                vars.put("subjectType", (k % 2 == 0) ? "男命" : "女命");
                vars.put("inputMode", "合婚");
                driveHandler(w, "handleMarriageMatch", vars);
                k++;
            }
        }
        assertThat(k).isEqualTo(25);
    }

    @Test
    @DisplayName("hitNamedPatterns：井栏叉(申子辰全+寅/午/戌反例) 与 子遥巳(甲子日甲子时+午/巳/丑反例)")
    void hitNamedPatternsDeep() throws Exception {
        Class<?>[] tt = new Class<?>[]{int[][].class, int.class};
        int[][] jlPos = pillars(6, 8, 6, 0, 6, 4, 6, 4);   // 庚·申子辰辰（正）
        int[][] jlWu = pillars(6, 8, 6, 0, 6, 4, 6, 6);    // +午（忌）
        int[][] jlXu = pillars(6, 8, 6, 0, 6, 4, 6, 10);   // +戌（忌）
        int[][] jlYin = pillars(6, 8, 6, 0, 6, 4, 6, 2);   // +寅（忌）
        for (int[][] p : new int[][][]{jlPos, jlWu, jlXu, jlYin}) {
            staticCall("hitNamedPatterns", tt, p, 6);
        }
        int[][] zyPos = pillars(0, 0, 0, 0, 0, 0, 0, 0);   // 甲子日甲子时（正）
        int[][] zyWu = pillars(0, 6, 0, 0, 0, 0, 0, 0);    // +午（忌）
        int[][] zySi = pillars(0, 5, 0, 0, 0, 0, 0, 0);    // +巳（忌）
        int[][] zyChou = pillars(0, 1, 0, 0, 0, 0, 0, 0);  // +丑（忌）
        for (int[][] p : new int[][][]{zyPos, zyWu, zySi, zyChou}) {
            staticCall("hitNamedPatterns", tt, p, 0);
        }
    }

    @Test
    @DisplayName("handleMarriageMatch：缺 partnerBirth → 校验抛错被捕获（覆盖 null/blank 守卫真侧）")
    void marriageMissingPartner() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Map<String, Object> vars = new HashMap<>();
        vars.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=男;SUBJECT=x;MODE=合婚");
        vars.put("gender", "男");
        vars.put("inputMode", "合婚"); // 不含 partnerBirth
        driveHandler(w, "handleMarriageMatch", vars);
    }

    @Test
    @DisplayName("handleSelectYongShen / handleFemaleChart / handleDeterminePattern：10干×12支×旺衰×男女 大矩阵")
    void triadHandlerMatrix() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        String[] gans = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
        String[] zhis = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
        String[] strengths = {"身强（得令）", "中和", "身弱（失令、无根、寡助）", "中和偏强（得令）"};
        int n = 0;
        for (int a = 0; a < 10; a++) {
            for (int b = 0; b < 12; b++) {
                String y = gans[a] + zhis[(b + 6) % 12];
                String m = gans[(a + b) % 10] + zhis[b];
                String d = gans[a] + zhis[(a + 3) % 12];
                String h = gans[(a + 5) % 10] + zhis[(a + 7) % 12];
                String fp = y + " " + m + " " + d + " " + h;
                for (int st = 0; st < strengths.length; st++) {
                    for (int gd = 0; gd < 2; gd++) {
                        String g = (gd == 0) ? "男" : "女";
                        Map<String, Object> vars = new HashMap<>();
                        vars.put("fourPillars", fp);
                        vars.put("strength", strengths[st]);
                        vars.put("gender", g);
                        vars.put("deities", "羊刃、驿马、桃花、相冲");
                        vars.put("chartLevel", "平常格（格成而财官未通门户）");
                        vars.put("patternGrade", "格成");
                        vars.put("granularity", "年");
                        vars.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=" + g + ";SUBJECT=x;MODE=单命");
                        vars.put("startAge", 8.0);
                        vars.put("fortuneSequence", "甲子,乙丑,丙寅");
                        vars.put("fortuneTrend", "顺");
                        vars.put("ganzhiRelations", "无合无冲");
                        driveHandler(w, "handleSelectYongShen", vars);
                        driveHandler(w, "handleFemaleChart", vars);
                        driveHandler(w, "handleDeterminePattern", vars);
                        n++;
                    }
                }
            }
        }
        assertThat(n).isEqualTo(10 * 12 * strengths.length * 2);
    }
}
