package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 小函数分支补齐：tenGodIndex 全 10×10、parseStrength/parsePatternGrade/strengthLabel 各档、
 * effectiveYongElem/isYinBiZhongYong、toDouble、parseChartBase/computePillars、parseDateTime、
 * stripCitations/verdictOr、partnerContains、extractLongitude、pinyinGanzhi/stemGroup 等。
 */
@DisplayName("小函数分支补齐（十神索引/档位解析/用神/工具）")
class SiZhuBaZiSmallFnTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;
    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

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

    private static int idx(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) { if (arr[i].equals(v)) { return i; } }
        return -1;
    }

    private static int[][] p4(String s) {
        String[] a = s.trim().split("\\s+");
        int[][] p = new int[4][2];
        for (int i = 0; i < 4; i++) { p[i][0] = idx(GAN, a[i].substring(0, 1)); p[i][1] = idx(ZHI, a[i].substring(1, 2)); }
        return p;
    }

    private static boolean flag(Object bean, String field) throws Exception {
        Field f = bean.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getBoolean(bean);
    }

    @Test
    @DisplayName("tenGodIndex 全 10×10：覆盖比肩/劫财/食神/伤官/偏财/正财/偏官/正官/偏印/正印 各分支")
    void tenGodIndexAll() throws Exception {
        for (int d = 0; d < 10; d++) {
            for (int t = 0; t < 10; t++) {
                int idx = (Integer) stat("tenGodIndex", new Class<?>[]{int.class, int.class}, d, t);
                assertThat(idx).isBetween(0, 9);
            }
        }
    }

    @Test
    @DisplayName("parseStrength：null/空/身强/中和/中和偏强/中和偏弱/身弱/中和身弱 各档标志位")
    void parseStrengthAll() throws Exception {
        Class<?>[] t = {String.class};
        assertThat(flag(stat("parseStrength", t, (Object) null), "strong")).isFalse();
        assertThat(flag(stat("parseStrength", t, ""), "neutral")).isFalse();
        assertThat(flag(stat("parseStrength", t, "身强（得令）"), "strong")).isTrue();
        assertThat(flag(stat("parseStrength", t, "中和偏强（得令）"), "strong")).isTrue();
        assertThat(flag(stat("parseStrength", t, "中和"), "neutral")).isTrue();
        assertThat(flag(stat("parseStrength", t, "中和偏弱（失令、克泄耗偏多）"), "mildWeak")).isTrue();
        assertThat(flag(stat("parseStrength", t, "身弱（失令、无根、寡助）"), "severeWeak")).isTrue();
        assertThat(flag(stat("parseStrength", t, "中和身弱"), "neutral")).isFalse();
    }

    @Test
    @DisplayName("parsePatternGrade：格成/格将成/建禄/月劫/比劫/格有破/格不成/格有绊/专旺/null")
    void parsePatternGradeAll() throws Exception {
        Class<?>[] t = {String.class};
        assertThat(flag(stat("parsePatternGrade", t, "格成"), "geCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格将成（待引透）"), "geJiangCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "建禄格成立"), "jianLuCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "月劫格成立"), "jianLuCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "比劫格成立"), "jianLuCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格有破"), "geBreak")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格不成"), "geBuCheng")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "格有绊"), "geBan")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, "专旺成象"), "zhuanWang")).isTrue();
        assertThat(flag(stat("parsePatternGrade", t, (Object) null), "geCheng")).isFalse();
    }

    @Test
    @DisplayName("strengthLabel：null/无括号/中文括号/英文括号")
    void strengthLabelAll() throws Exception {
        Class<?>[] t = {String.class};
        assertThat((String) stat("strengthLabel", t, (Object) null)).isEmpty();
        assertThat((String) stat("strengthLabel", t, "身强")).isEqualTo("身强");
        assertThat((String) stat("strengthLabel", t, "中和偏弱（失令）")).isEqualTo("中和偏弱");
        assertThat((String) stat("strengthLabel", t, "身强(得令)")).isEqualTo("身强");
    }

    @Test
    @DisplayName("effectiveYongElem / isYinBiZhongYong：火炎(→水4)/水寒(→火1)/印比强/非印比/非强")
    void yongElemEdges() throws Exception {
        int[][] fire = p4("丙午 丙午 丙午 丙午");
        int[][] water = p4("壬子 壬子 壬子 壬子");
        int[][] yinbi = p4("甲子 丙子 甲子 甲子");
        int[][] feiyinbi = p4("甲午 甲午 甲午 甲午");
        assertThat((Integer) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, fire, 2, true)).isEqualTo(4);
        assertThat((Integer) stat("effectiveYongElem", new Class<?>[]{int[][].class, int.class, boolean.class}, water, 8, true)).isEqualTo(1);
        assertThat((Boolean) stat("isYinBiZhongYong", new Class<?>[]{int[][].class, int.class, boolean.class}, fire, 2, true)).isFalse();
        assertThat((Boolean) stat("isYinBiZhongYong", new Class<?>[]{int[][].class, int.class, boolean.class}, water, 8, true)).isFalse();
        assertThat((Boolean) stat("isYinBiZhongYong", new Class<?>[]{int[][].class, int.class, boolean.class}, yinbi, 0, true)).isTrue();
        assertThat((Boolean) stat("isYinBiZhongYong", new Class<?>[]{int[][].class, int.class, boolean.class}, feiyinbi, 0, true)).isFalse();
        assertThat((Boolean) stat("isYinBiZhongYong", new Class<?>[]{int[][].class, int.class, boolean.class}, yinbi, 0, false)).isFalse();
    }

    @Test
    @DisplayName("toDouble：null/非数字/数字串/Number")
    void toDoubleAll() throws Exception {
        Class<?>[] t = {Object.class, double.class};
        assertThat((Double) stat("toDouble", t, (Object) null, 9.0)).isEqualTo(9.0);
        assertThat((Double) stat("toDouble", t, "abc", 9.0)).isEqualTo(9.0);
        assertThat((Double) stat("toDouble", t, "3.5", 9.0)).isEqualTo(3.5);
        assertThat((Double) stat("toDouble", t, 2, 9.0)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("parseChartBase / computePillars：null/无等号段/晚子两路")
    void chartBaseEdges() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> m = (Map<String, String>) stat("parseChartBase", new Class<?>[]{String.class}, (Object) null);
        assertThat(m).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, String> m2 = (Map<String, String>) stat("parseChartBase", new Class<?>[]{String.class}, "a=1;bb;c=2");
        assertThat(m2).containsEntry("a", "1").containsEntry("c", "2").doesNotContainKey("bb");
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Map<String, String> cb1 = new LinkedHashMap<>();
        cb1.put("SOLAR", "1990-05-15T23:30"); cb1.put("LATEZI", "true");
        assertThat(inst(w, "computePillars", new Class<?>[]{Map.class}, cb1)).isNotNull();
        Map<String, String> cb2 = new LinkedHashMap<>();
        cb2.put("SOLAR", "1990-05-15T08:30"); cb2.put("LATEZI", "false");
        assertThat(inst(w, "computePillars", new Class<?>[]{Map.class}, cb2)).isNotNull();
    }

    @Test
    @DisplayName("parseDateTime：多格式/无时刻/有时无分")
    void parseDateTimeAll() throws Exception {
        Class<?>[] t = {String.class};
        assertThat(stat("parseDateTime", t, "1990-05-15")).isNotNull();
        assertThat(stat("parseDateTime", t, "1990-05-15 08")).isNotNull();
        assertThat(stat("parseDateTime", t, "1990-05-15 08:30")).isNotNull();
        assertThat(stat("parseDateTime", t, "1990年5月15日8时30分")).isNotNull();
        assertThat(stat("parseDateTime", t, "1990/5/5")).isNotNull();
    }

    @Test
    @DisplayName("stripCitations/verdictOr：null/无标注/含书名出处/模板未命中回退")
    void citationEdges() throws Exception {
        Class<?>[] t = {String.class};
        assertThat(stat("stripCitations", t, (Object) null)).isNull();
        assertThat((String) stat("stripCitations", t, "结论如常")).isEqualTo("结论如常");
        assertThat((String) stat("stripCitations", t, "结论（《书》出处：某）【出处：某】")).isEqualTo("结论");
        SiZhuBaZiOntologyJobWorker w = newWorker();
        assertThat((String) inst(w, "verdictOr", new Class<?>[]{String.class, String.class, String.class}, "合婚", "k", "兜底结论")).isEqualTo("兜底结论");
    }

    @Test
    @DisplayName("partnerContains/extractLongitude/pinyinGanzhi/stemGroup：边界")
    void miscSmallFn() throws Exception {
        int[][] partner = p4("甲子 乙丑 丙寅 丁卯");
        assertThat((Boolean) stat("partnerContains", new Class<?>[]{int[][].class, String[].class}, partner, new String[]{"木"})).isTrue();
        assertThat((Boolean) stat("partnerContains", new Class<?>[]{int[][].class, String[].class}, partner, new String[]{"金"})).isFalse();
        assertThat(stat("extractLongitude", new Class<?>[]{String.class}, "-300")).isNull();
        Class<?>[] s1 = {String.class};
        assertThat((String) stat("pinyinGanzhi", s1, "甲子")).isEqualTo("JiaZi");
        assertThat(stat("pinyinGanzhi", s1, "甲")).isNull();
        assertThat((String) stat("stemGroup", s1, "甲")).isEqualTo("甲乙");
        assertThat((String) stat("stemGroup", s1, "X")).isEmpty();
    }
}


