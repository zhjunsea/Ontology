package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * B 类补齐·⑭ 定向补盘：
 * <ul>
 *   <li><b>回归</b>{@code tongYuanObjectiveFull} 的 A1/A2 空值守卫：盘中缺「天梁」/「巨门」时，
 *       {@code liang == mingIdx} / {@code isFallen("巨门", ju)} 因 Integer 拆箱曾抛 NPE，
 *       现应安全返回「否」（不抛异常）。</li>
 *   <li><b>补盘</b>{@code judgeLongevity}：以手构 {@code at} 命中「破相」两个操作数
 *       （父母宫逢煞加刑 / 疾厄擎羊），覆盖该行残余的未走分支。</li>
 * </ul>
 * 纯函数经反射直调，离线、无 Camunda/DB/Ontop 依赖。
 */
@DisplayName("B类补齐·同垣空值守卫回归 + 寿元破相补盘")
class ZiWeiDouShuBClassResidualCoverageTest14 {

    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String skFor(String ming) {
        return "命宫=" + ming + ";身宫=午;兄弟=丑;夫妻=寅;子女=卯;财帛=辰;疾厄=巳;迁移=午;奴仆=未;官禄=申;田宅=酉;福德=戌;父母=亥;水二局";
    }

    private static final Class<?>[] FULL = {
            String.class, int.class, Map.class, int.class, String.class, Boolean.class, String.class};

    // ============ tongYuanObjectiveFull A1/A2 空值守卫回归（原 NPE） ============

    @Test
    @DisplayName("同垣客观断语：缺天梁（A1）/缺巨门（A2）不抛 NPE，返回「否」")
    void tongYuanObjectiveFullNullGuards() {
        // 稀疏盘：仅紫微在子，缺天梁、缺巨门
        Map<Integer, List<String>> sparse = new HashMap<>();
        sparse.put(0, new ArrayList<>(Arrays.asList("紫微")));

        // A1 天梁守照吉相逢 —— 盘无天梁，原 liang==mingIdx 拆箱 NPE
        assertThatCode(() -> {
            String a1 = (String) invoke("tongYuanObjectiveFull",
                    FULL, "天梁守照吉相逢平生福寿", 0, sparse, -1, skFor("子"), Boolean.TRUE, "");
            assertThat(a1).as("A1 空值路径应返回客观判定串").isNotNull();
            assertThat(a1).contains("否");
        }).doesNotThrowAnyException();

        // A2 巨陷天机为破格 —— 盘无巨门，原 isFallen(String,int) 拆箱 NPE
        assertThatCode(() -> {
            String a2 = (String) invoke("tongYuanObjectiveFull",
                    FULL, "巨陷天机为破格", 0, sparse, -1, skFor("子"), Boolean.TRUE, "");
            assertThat(a2).as("A2 空值路径应返回客观判定串").isNotNull();
            assertThat(a2).contains("否");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("同垣客观断语：天梁守命+三方见吉（A1 真侧）、巨门在盘（A2 有值侧）正常返回")
    void tongYuanObjectiveFullPresentStars() {
        // A1 真侧：天梁守命(子)、并在命宫三方见吉（左辅@辰，辰属子之三方）
        Map<Integer, List<String>> at1 = new LinkedHashMap<>();
        at1.put(0, new ArrayList<>(Arrays.asList("天梁")));
        at1.put(4, new ArrayList<>(Arrays.asList("左辅")));
        String a1 = (String) invoke("tongYuanObjectiveFull",
                FULL, "天梁守照吉相逢平生福寿", 0, at1, -1, skFor("子"), Boolean.TRUE, "");
        assertThat(a1).isNotNull();
        assertThat(a1).contains("〔客观·");

        // A2 有值侧：巨门在盘（寅），不抛异常即可（庙陷取决于表）
        Map<Integer, List<String>> at2 = new LinkedHashMap<>();
        at2.put(0, new ArrayList<>(Arrays.asList("紫微")));
        at2.put(2, new ArrayList<>(Arrays.asList("巨门")));
        assertThatCode(() -> {
            String a2 = (String) invoke("tongYuanObjectiveFull",
                    FULL, "巨陷天机为破格", 0, at2, -1, skFor("子"), Boolean.TRUE, "");
            assertThat(a2).isNotNull();
        }).doesNotThrowAnyException();
    }

    // ============ judgeLongevity「破相」两个操作数补盘 ============

    @SuppressWarnings("unchecked")
    private static List<String> judgeLongevity(int mingIdx, int shenIdx, Map<Integer, List<String>> at) {
        return (List<String>) invoke("judgeLongevity",
                new Class<?>[]{int.class, int.class, Map.class}, mingIdx, shenIdx, at);
    }

    /**
     * 说明：judgeLongevity 破相原文「相貌之中逢杀曜，更加三合又逢刑，疾厄擎羊逢耗使，折伤肢体不和平」
     * （卷一·论定人破相）。旧实现因 {@code inSanFang(fumuIdx, "天刑")} 把「星名」传入只接受「地支串」的
     * {@code inSanFang(int,String)}（内部 {@code indexOf(ZHI,"天刑")=-1} 恒 false），致「父母宫见杀 ∧ 三方见天刑」
     * 恒假、原文断语不可达。**该缺陷已于 2026-11-20 修复**（改用 {@code starInSanFang(at, fumuIdx, "天刑")}，
     * 以天刑地支判三方），以下 {@link #poXiangViaFumuShaAndTianXing()} 即为修复后原路径回归。
     */
    @Test
    @DisplayName("judgeLongevity 聪明：文曲守命且三方会文昌")
    void congMingViaWenChang() {
        // 命在子(0)：三方={0,4,8,6}；文曲守命(0)、文昌入三方(4)
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(0, new ArrayList<>(Arrays.asList("文曲")));
        at.put(4, new ArrayList<>(Arrays.asList("文昌")));
        List<String> g = judgeLongevity(0, -1, at);
        assertThat(g).anyMatch(s -> s.contains("聪明"));
    }

    @Test
    @DisplayName("judgeLongevity 破相：疾厄宫见擎羊")
    void poXiangViaJiEPalaceQingYang() {
        // 命在子(0)：疾厄=floorMod(0-5,12)=7(未)
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(0, new ArrayList<>(Arrays.asList("天府")));
        at.put(7, new ArrayList<>(Arrays.asList("擎羊")));
        List<String> g = judgeLongevity(0, -1, at);
        assertThat(g).anyMatch(s -> s.contains("破相"));
    }

    @Test
    @DisplayName("judgeLongevity 破相：父母宫见杀 且 天刑入三方（原文「相貌之中逢杀曜，更加三合又逢刑」）")
    void poXiangViaFumuShaAndTianXing() {
        // 命在子(0)：父母(相貌)=floorMod(0-11,12)=1(丑)；丑之三方四正={1,5,9,7}
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(1, new ArrayList<>(Arrays.asList("擎羊")));   // 父母宫逢杀曜
        at.put(5, new ArrayList<>(Arrays.asList("天刑")));   // 天刑落巳(5)，属父母(丑)宫三方四正
        List<String> g = judgeLongevity(0, -1, at);
        assertThat(g).anyMatch(s -> s.contains("破相"));
    }
}
