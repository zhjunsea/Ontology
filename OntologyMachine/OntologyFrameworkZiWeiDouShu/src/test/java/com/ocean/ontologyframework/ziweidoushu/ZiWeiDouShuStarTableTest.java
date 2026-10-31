package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 · 内置安星/庙陷/神煞表口径自检（不需 MySQL / Ontop / Camunda）。
 *
 * <p>锁定本轮《全書》符合性审查结论：卷二·安紫微定位棋盘图 / 安南北斗諸星訣（紫府系偏移）/
 * 星辰庙陷 / 定十二宫星辰落闲 / 安飞天三杀诀。表值经反射读取生产类的既有内置表与查表方法。
 */
@DisplayName("紫微斗数 · 内置表口径自检（卷二·安星/庙陷/神煞）")
class ZiWeiDouShuStarTableTest {

    private static Object field(String name) throws Exception {
        Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static String brightnessOf(String star, int branchIdx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("brightnessOf", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, star, branchIdx);
    }

    private static boolean isIdleStar(String star, int branchIdx) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class
                .getDeclaredMethod("isIdleStar", String.class, int.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, star, branchIdx);
    }

    private static int[] feiTianSanSha(int yearBranch) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("feiTianSanSha", int.class);
        m.setAccessible(true);
        return (int[]) m.invoke(null, yearBranch);
    }

    @Test
    @DisplayName("紫微定位表：五局初一取值（水二丑/木三辰/金四亥/土五午/火六酉）——卷二·安紫微定位棋盘图")
    void ziweiLayoutTable() throws Exception {
        int[][] zt = (int[][]) field("ZIWEI_TABLE");
        assertThat(zt[0][0]).as("水二局初一→丑").isEqualTo(1);
        assertThat(zt[1][0]).as("木三局初一→辰").isEqualTo(4);
        assertThat(zt[2][0]).as("金四局初一→亥").isEqualTo(11);
        assertThat(zt[3][0]).as("土五局初一→午").isEqualTo(6);
        assertThat(zt[4][0]).as("火六局初一→酉（卷二例：火局正月初一酉宫起紫微）").isEqualTo(9);
    }

    @Test
    @DisplayName("紫府系偏移：紫微系 {0,-1,-3,-4,-5,-8} / 天府系 {0,1,2,3,4,5,6,10}——卷二·安南北斗諸星訣")
    void seriesOffsets() throws Exception {
        int[] zi = (int[]) field("ZIWEI_SERIES_OFF");
        assertThat(zi).containsExactly(0, -1, -3, -4, -5, -8);
        int[] fu = (int[]) field("TIANFU_SERIES_OFF");
        assertThat(fu).containsExactly(0, 1, 2, 3, 4, 5, 6, 10);
    }

    @Test
    @DisplayName("紫微↔天府寅申对称：天府系七杀(6)空三而破军(10)（申子辰三合杀破狼）")
    void pojunOffset() throws Exception {
        int[] fu = (int[]) field("TIANFU_SERIES_OFF");
        assertThat(fu[6]).as("七杀").isEqualTo(6);
        assertThat(fu[7]).as("破军=七杀+4（空三）").isEqualTo(10);
    }

    @Test
    @DisplayName("星辰庙陷表（卷二·星辰庙陷）：紫微@午=庙、太阳@子=陷、太阴@亥=庙、七杀@子=旺、文曲@申=得地、铃星@卯=利益")
    void brightnessTable() throws Exception {
        assertThat(brightnessOf("紫微", 6)).as("紫微@午").isEqualTo("庙");
        assertThat(brightnessOf("太阳", 0)).as("太阳@子").isEqualTo("陷");
        assertThat(brightnessOf("太阴", 11)).as("太阴@亥").isEqualTo("庙");
        assertThat(brightnessOf("七杀", 0)).as("七杀@子").isEqualTo("旺");
        assertThat(brightnessOf("文曲", 8)).as("文曲@申").isEqualTo("得地");
        assertThat(brightnessOf("铃星", 3)).as("铃星@卯").isEqualTo("利益");
        assertThat(brightnessOf("天府", 5)).as("天府@巳").isEqualTo("得地");
    }

    @Test
    @DisplayName("庙陷表外之星（辅佐/杂曜）返回「平和」兜底")
    void brightnessFallback() throws Exception {
        assertThat(brightnessOf("左辅", 0)).isEqualTo("平和");
        assertThat(brightnessOf("天魁", 5)).isEqualTo("平和");
        assertThat(brightnessOf("禄存", 1)).as("禄存不入丑（结构上不可能，兜底平和）").isEqualTo("平和");
    }

    @Test
    @DisplayName("星辰落闲（卷二·定十二宫星辰落闲）：紫微子辰亥 / 破军巳申 / 武曲申 / 天机巳")
    void idleStars() throws Exception {
        assertThat(isIdleStar("紫微", 0)).as("紫微@子").isTrue();
        assertThat(isIdleStar("紫微", 1)).as("紫微@丑（非闲）").isFalse();
        assertThat(isIdleStar("破军", 5)).as("破军@巳").isTrue();
        assertThat(isIdleStar("武曲", 8)).as("武曲@申").isTrue();
        assertThat(isIdleStar("天机", 5)).as("天机@巳").isTrue();
        assertThat(isIdleStar("贪狼", 2)).as("贪狼@寅").isTrue();
    }

    @Test
    @DisplayName("飞天神煞（卷二·安飞天三杀诀）：寅午戌→亥卯未、申子辰→巳酉丑、亥卯未→申子辰、巳酉丑→寅午戌")
    void feiTianSanShaMap() throws Exception {
        assertThat(feiTianSanSha(2)).as("寅年").containsExactly(11, 3, 7);
        assertThat(feiTianSanSha(0)).as("子年").containsExactly(5, 9, 1);
        assertThat(feiTianSanSha(11)).as("亥年").containsExactly(8, 0, 4);
        assertThat(feiTianSanSha(5)).as("巳年").containsExactly(2, 6, 10);
    }

    @Test
    @DisplayName("四化（卷二·安禄权科忌四星变化诀）：甲廉破武阳 / 壬梁紫府武（壬化科＝天府，非通行「左辅化科」异说）")
    void sihuaTable() throws Exception {
        String[][] sh = (String[][]) field("SIHUA");
        assertThat(sh[0]).as("甲").containsExactly("廉贞", "破军", "武曲", "太阳");
        assertThat(sh[8]).as("壬").containsExactly("天梁", "紫微", "天府", "武曲");
        assertThat(sh[9]).as("癸").containsExactly("破军", "巨门", "太阴", "贪狼");
        assertThat(sh[7]).as("辛").containsExactly("巨门", "太阳", "文曲", "文昌");
    }

    @Test
    @DisplayName("火铃（卷二·安火铃二星诀，按年支三合）：寅午戌→火丑铃卯 / 申子辰→火寅 / 亥卯未→火酉")
    void huoLingMap() throws Exception {
        int[] huo = (int[]) field("HUOXING");
        int[] ling = (int[]) field("LINGXING");
        assertThat(huo[2]).as("寅年火@丑").isEqualTo(1);
        assertThat(ling[2]).as("寅年铃@卯").isEqualTo(3);
        assertThat(huo[0]).as("子年火@寅").isEqualTo(2);
        assertThat(ling[0]).as("子年铃@戌").isEqualTo(10);
        assertThat(huo[3]).as("卯年火@酉").isEqualTo(9);
    }
}
