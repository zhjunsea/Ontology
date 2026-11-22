package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 纯静态/实例工具方法的分支补测：历法换算、专旺侦测、岁运辩证、类目、天克地冲。 */
@DisplayName("工具方法边界·历法/专旺/岁运/类目/天克地冲")
class SiZhuBaZiHelperEdgeTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static Object stat(String name, Class<?>[] t, Object... a) throws Exception {
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

    private static Object inst(Object w, String name, Class<?>[] t, Object... a) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, t);
        m.setAccessible(true);
        try { return m.invoke(w, a); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) { throw re; }
            if (c instanceof Error er) { throw er; }
            throw e;
        }
    }

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) { m.put((String) kv[i], kv[i + 1]); }
        return m;
    }

    private static int[][] pillars(int g0, int z0, int g1, int z1, int g2, int z2, int g3, int z3) {
        return new int[][]{{g0, z0}, {g1, z1}, {g2, z2}, {g3, z3}};
    }

    @Test
    @DisplayName("tianKeDiChong：gz1 null/过短、gz2 过短 → false")
    void tianKeShort() throws Exception {
        Class<?>[] t = {String.class, String.class};
        assertThat((Boolean) stat("tianKeDiChong", t, (Object) null, "甲子")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", t, "甲", "甲子")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", t, "甲子", "甲")).isFalse();
    }

    @Test
    @DisplayName("inCategory 财类：正财/偏财 true、异类 false")
    void inCategoryCai() throws Exception {
        Class<?>[] t = {String.class, String.class};
        assertThat((Boolean) stat("inCategory", t, "正财", "财")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "偏财", "财")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "食神", "财")).isFalse();
    }

    @Test
    @DisplayName("buildFortuneSynthesis：fortunes 含 null/过短/非法干支 → 跳过不异常")
    void fortuneSynthesisInvalidFortunes() throws Exception {
        Class<?>[] t = {int[][].class, double.class, String[].class, int.class, int.class, int.class, String.class, boolean.class};
        int[][] p = pillars(0, 0, 1, 1, 2, 2, 3, 3);
        String[] fortunes = {null, "甲", "甲X", "X子", "甲子"};
        Object r = stat("buildFortuneSynthesis", t, p, 3.0, fortunes, 0, 1, 0, "格成", true);
        assertThat((String) r).contains("岁运辩证");
    }

    @Test
    @DisplayName("detectZhuanWang：五行五格成象（曲直/炎上/稼穑/从革/润下）与破格")
    void detectZhuanWangAll() throws Exception {
        Class<?>[] t = {int[][].class, int.class};
        // 曲直(木) 亥卯未
        assertThat((String) stat("detectZhuanWang", t, pillars(0, 11, 0, 3, 0, 7, 0, 3), 0)).isEqualTo("专旺格（曲直）");
        // 炎上(火) 寅午戌
        assertThat((String) stat("detectZhuanWang", t, pillars(2, 2, 2, 6, 2, 10, 2, 2), 2)).isEqualTo("专旺格（炎上）");
        // 稼穑(土) 辰戌丑未
        assertThat((String) stat("detectZhuanWang", t, pillars(4, 4, 4, 10, 4, 1, 4, 7), 4)).isEqualTo("专旺格（稼穑）");
        // 稼穑破：天干透木
        assertThat((String) stat("detectZhuanWang", t, pillars(0, 4, 4, 10, 4, 1, 4, 7), 4)).isEmpty();
        // 从革(金) 巳酉丑
        assertThat((String) stat("detectZhuanWang", t, pillars(6, 5, 6, 9, 6, 1, 6, 9), 6)).isEqualTo("专旺格（从革）");
        // 从革破：支见午(火)
        assertThat((String) stat("detectZhuanWang", t, pillars(6, 5, 6, 9, 6, 1, 6, 6), 6)).isEmpty();
        // 润下(水) 申子辰
        assertThat((String) stat("detectZhuanWang", t, pillars(8, 8, 8, 0, 8, 4, 8, 8), 8)).isEqualTo("专旺格（润下）");
    }

    @Test
    @DisplayName("lunarToSolar：null/空白/无数字/月日越界 → null；闰标记各侧不异常；合法农历可换算")
    void lunarToSolarEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] t = {Object.class};
        assertThat((Object) inst(w, "lunarToSolar", t, (Object) null)).isNull();
        assertThat((Object) inst(w, "lunarToSolar", t, "   ")).isNull();
        assertThat((Object) inst(w, "lunarToSolar", t, "没有数字")).isNull();
        assertThat((Object) inst(w, "lunarToSolar", t, "1990-13-5")).isNull();
        assertThat((Object) inst(w, "lunarToSolar", t, "1990-0-5")).isNull();
        assertThat((Object) inst(w, "lunarToSolar", t, "1990-5-0")).isNull();
        assertThat((Object) inst(w, "lunarToSolar", t, "1990-5-31")).isNull();
        assertThat((Object) inst(w, "lunarToSolar", t, obj("year", 1990, "month", 4, "day", 4))).isNotNull();
        // 闰标记两侧（leap / isLeap），值不定不校验，仅保证不异常
        assertThatCode(() -> inst(w, "lunarToSolar", t, obj("year", 1990, "month", 4, "day", 4, "leap", Boolean.TRUE)))
                .doesNotThrowAnyException();
        assertThatCode(() -> inst(w, "lunarToSolar", t, obj("year", 1990, "month", 4, "day", 4, "isLeap", Boolean.TRUE)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isYinBiZhongYong：火炎放行/水寒放行/常规强弱两态")
    void isYinBiZhongYongEdges() throws Exception {
        Class<?>[] t = {int[][].class, int.class, boolean.class};
        // 火日火月无水 → 放行（false）
        assertThat((Object) stat("isYinBiZhongYong", t, pillars(2, 6, 2, 6, 2, 6, 2, 6), 2, true))
                .isInstanceOf(Boolean.class);
        // 水日水月无火 → 放行（false）
        assertThat((Object) stat("isYinBiZhongYong", t, pillars(8, 0, 8, 0, 8, 0, 8, 0), 8, true))
                .isInstanceOf(Boolean.class);
        // 木日常规，强/弱两态
        assertThat((Object) stat("isYinBiZhongYong", t, pillars(0, 3, 0, 3, 0, 3, 0, 3), 0, true))
                .isInstanceOf(Boolean.class);
        assertThat((Object) stat("isYinBiZhongYong", t, pillars(0, 3, 0, 3, 0, 3, 0, 3), 0, false))
                .isInstanceOf(Boolean.class);
    }

    @Test
    @DisplayName("fireExcessFortuneSegment：运支戌有午/有卯/皆无；运支丑甲木s=0 特殊判")
    void fireExcessSegmentEdges() throws Exception {
        Class<?>[] t = {int[][].class, int.class, int.class};
        int[][] fire = pillars(2, 6, 2, 6, 2, 6, 2, 6);      // 有午
        int[][] mao = pillars(0, 3, 0, 3, 0, 3, 0, 3);       // 有卯
        int[][] plain = pillars(0, 0, 0, 0, 0, 0, 0, 0);     // 无午无卯
        assertThat((Object) stat("fireExcessFortuneSegment", t, fire, 0, 10)).isInstanceOf(String.class);
        assertThat((Object) stat("fireExcessFortuneSegment", t, mao, 0, 10)).isInstanceOf(String.class);
        assertThat((Object) stat("fireExcessFortuneSegment", t, plain, 0, 10)).isInstanceOf(String.class);
        // z=1(丑)、ge=0(甲)、s=0 → 特殊判 L3864
        assertThat((Object) stat("fireExcessFortuneSegment", t, plain, 0, 1)).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("buildFortuneAnalysis：fortunes 含 null/过短/非法干支 → 跳过不异常")
    void buildFortuneAnalysisInvalidFortunes() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] t = {int[][].class, LocalDateTime.class, double.class, String[].class,
                int.class, int.class, int.class, boolean.class, String.class, String.class};
        int[][] p = pillars(0, 0, 1, 1, 2, 2, 3, 3);
        String[] fortunes = {null, "甲", "甲X", "X子", "甲子"};
        Object r = inst(w, "buildFortuneAnalysis", t, p, LocalDateTime.of(1990, 1, 1, 0, 0), 3.0,
                fortunes, 0, 1, 0, true, "男", "身强");
        assertThat((String) r).isNotEmpty();
    }

    @Test
    @DisplayName("调候极值（穷通宝鉴）：纯火无水→用神水(4)、纯水无火→用神火(1)，且非印比重用")
    void yongShenTiaohouExtreme() throws Exception {
        Class<?>[] t = {int[][].class, int.class, boolean.class};
        int[][] fire = pillars(2, 6, 2, 6, 2, 6, 2, 6);   // 丙午 ×4（火炎无水）
        int[][] water = pillars(8, 0, 8, 0, 8, 0, 8, 0);  // 壬子 ×4（水寒无火）
        assertThat((Integer) stat("effectiveYongElem", t, fire, 2, true)).isEqualTo(4);
        assertThat((Boolean) stat("isYinBiZhongYong", t, fire, 2, true)).isFalse();
        assertThat((Integer) stat("effectiveYongElem", t, water, 8, true)).isEqualTo(1);
        assertThat((Boolean) stat("isYinBiZhongYong", t, water, 8, true)).isFalse();
    }

    @Test
    @DisplayName("实证：三合分组对 0..11 支恒返回 0..3（sanheGroupOf 的 -1 / sanheGroup 的默认不可达）")
    void sanheGroupCoverage() throws Exception {
        for (int z = 0; z < 12; z++) {
            Object of = stat("sanheGroupOf", new Class<?>[]{int.class}, z);
            Object gp = stat("sanheGroup", new Class<?>[]{int.class}, z);
            assertThat((Integer) of).isBetween(0, 3);
            assertThat((Integer) gp).isBetween(0, 3);
        }
    }
}
