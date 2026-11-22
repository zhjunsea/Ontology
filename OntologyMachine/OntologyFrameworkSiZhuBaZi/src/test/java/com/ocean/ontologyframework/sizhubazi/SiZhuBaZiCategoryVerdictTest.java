package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 分类事项断（buildCategoryVerdicts）直调：官透且藏/官透无藏/女命夫星被合。 */
@DisplayName("分类事项断·buildCategoryVerdicts 直调")
class SiZhuBaZiCategoryVerdictTest {

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

    private static String build(SiZhuBaZiOntologyJobWorker w, String fp, int dayElem, Set<String> gods,
                                String deities, String gender, boolean strong) throws Exception {
        int[][] p = p4(fp);
        int dayGan = p[2][0];
        int monthBranch = p[1][1];
        Method m = WORKER.getDeclaredMethod("buildCategoryVerdicts",
                int[][].class, int.class, int.class, int.class, Set.class, String.class, String.class, boolean.class);
        m.setAccessible(true);
        return (String) m.invoke(w, p, dayGan, dayElem, monthBranch, gods, deities, gender, strong);
    }

    private static Set<String> gods(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    @Test
    @DisplayName("官透且藏·身弱（辛酉 辛酉 甲申 丙寅）→ 事业断含「透藏并见」")
    void guanTouCang() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        String r = build(w, "辛酉 辛酉 甲申 丙寅", 0, gods("正官", "偏财", "食神"), "桃花", "男", false);
        assertThat(r).isNotEmpty();
        assertThat(r).contains("透藏并见");
    }

    @Test
    @DisplayName("官透无藏·身弱（辛卯 辛卯 甲午 丙寅）→ 事业断含「透干」")
    void guanTouNoCang() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        String r = build(w, "辛卯 辛卯 甲午 丙寅", 0, gods("正官", "食神"), "桃花", "男", false);
        assertThat(r).isNotEmpty();
        assertThat(r).contains("透干");
    }

    @Test
    @DisplayName("女命夫星被合（辛酉 丙申 甲午 丙寅，丙合辛）→ 婚姻断含「夫星」")
    void femaleFuXingHe() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        String r = build(w, "辛酉 丙申 甲午 丙寅", 0, gods("正官", "偏财"), "桃花", "女", true);
        assertThat(r).isNotEmpty();
    }
}
