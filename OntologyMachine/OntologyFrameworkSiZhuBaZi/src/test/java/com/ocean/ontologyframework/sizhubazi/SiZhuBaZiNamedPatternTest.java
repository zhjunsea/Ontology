package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 具名格侦测（hitNamedPatterns）直调：六阴朝阳/井栏叉/子遥巳/玄武当权 等可达分支。 */
@DisplayName("具名格·hitNamedPatterns 直调")
class SiZhuBaZiNamedPatternTest {

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

    @SuppressWarnings("unchecked")
    private static List<String[]> hits(int[][] p, int dayGan) throws Exception {
        Method m = WORKER.getDeclaredMethod("hitNamedPatterns", int[][].class, int.class);
        m.setAccessible(true);
        return (List<String[]>) m.invoke(null, p, dayGan);
    }

    private static boolean has(List<String[]> hs, String name) {
        for (String[] h : hs) { if (name.equals(h[0])) { return true; } }
        return false;
    }

    @Test
    @DisplayName("六阴朝阳：辛日、时戊子、子字一位（丁酉 辛酉 辛酉 戊子）")
    void liuYinChaoYang() throws Exception {
        assertThat(has(hits(p4("丁酉 辛酉 辛酉 戊子"), 7), "六阴朝阳")).isTrue();
    }

    @Test
    @DisplayName("井栏叉：庚日申子辰全、无寅午戌（庚申 庚子 庚辰 庚辰）")
    void jingLanCha() throws Exception {
        assertThat(has(hits(p4("庚申 庚子 庚辰 庚辰"), 6), "井栏叉")).isTrue();
    }

    @Test
    @DisplayName("子遥巳：甲子日甲子时、无午巳丑（甲子 丙寅 甲子 甲子）")
    void ziYaoSi() throws Exception {
        assertThat(has(hits(p4("甲子 丙寅 甲子 甲子"), 0), "子遥巳")).isTrue();
    }

    @Test
    @DisplayName("玄武当权：壬日坐财官、财官俱见（丙午 甲午 壬午 丙午）")
    void xuanWuDangQuan() throws Exception {
        assertThat(has(hits(p4("丙午 甲午 壬午 丙午"), 8), "玄武当权")).isTrue();
    }

    @Test
    @DisplayName("六阴朝阳 反例：辛日时戊子但子多（戊子 辛酉 辛酉 戊子）→ 不成格")
    void liuYinSubMultiple() throws Exception {
        assertThat(has(hits(p4("戊子 辛酉 辛酉 戊子"), 7), "六阴朝阳")).isFalse();
    }

    @Test
    @DisplayName("井栏叉 反例：庚日缺辰（庚申 庚子 庚子 庚子）→ 不成格")
    void jingLanNoChen() throws Exception {
        assertThat(has(hits(p4("庚申 庚子 庚子 庚子"), 6), "井栏叉")).isFalse();
    }

    @Test
    @DisplayName("井栏叉 反例：庚日申子辰全但见午冲（庚申 庚子 庚辰 庚午）→ 不成格")
    void jingLanYouWu() throws Exception {
        assertThat(has(hits(p4("庚申 庚子 庚辰 庚午"), 6), "井栏叉")).isFalse();
    }

    @Test
    @DisplayName("子遥巳 反例：甲子日甲子时但见午（甲子 甲午 甲子 甲子）→ 不成格")
    void ziYaoYouWu() throws Exception {
        assertThat(has(hits(p4("甲子 甲午 甲子 甲子"), 0), "子遥巳")).isFalse();
    }

    @Test
    @DisplayName("玄武当权 反例：壬日不坐财官（壬子 壬子 壬子 壬子）→ 不成格")
    void xuanWuNoCaiGuan() throws Exception {
        assertThat(has(hits(p4("壬子 壬子 壬子 壬子"), 8), "玄武当权")).isFalse();
    }
}
