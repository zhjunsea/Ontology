package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四柱命理 · 分支矩阵补充（第十二阶段）· 大运/流年逐运评析（纯离线）。
 *
 * <p>本类补齐此前未被单测直击的岁运评析链路：
 * <ul>
 *   <li>{@code scoreFortuneElem}（身强/身弱两态下食伤·财·官杀·比劫·印的扶抑评分）六支；</li>
 *   <li>{@code elemRoleName}（比劫/食伤/财/官杀/印）五支；</li>
 *   <li>{@code banHeHuaElem}（申子辰化水/亥卯未化木/寅午戌化火/巳酉丑化金/不成）五支；</li>
 *   <li>{@code fireExcessScore} 之「火炎土燥·调候用水」逐运打分；</li>
 *   <li>{@code fireExcessFortuneSegment} 之逐运细判（吉/凶/吉中带劳/湿土晦火等注解）；</li>
 *   <li>{@code buildFortuneAnalysis} 之大运方向（阳男顺/阴男逆/阴女顺）与逐运吉凶结论及分段标题。</li>
 * </ul>
 *
 * <p>断言一律读方法返回值（结构化）或其在 {@code buildFortuneAnalysis} 输出中的文本片段，不臆造判据。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第十二阶段·大运流年逐运评析）")
class SiZhuBaZiBranchMatrix12Test {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static int[][] P(String fp) throws Exception {
        Method m = WORKER.getDeclaredMethod("parsePillars", String.class);
        m.setAccessible(true);
        return (int[][]) m.invoke(null, fp);
    }

    private static String role(int dayElem, int elem) throws Exception {
        Method m = WORKER.getDeclaredMethod("elemRoleName", int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, dayElem, elem);
    }

    private static int score(int e, int yongElem, int dayElem, boolean strong) throws Exception {
        Method m = WORKER.getDeclaredMethod("scoreFortuneElem",
                int.class, int.class, int.class, boolean.class);
        m.setAccessible(true);
        return (int) m.invoke(null, e, yongElem, dayElem, strong);
    }

    private static int banHe(String fp, int z) throws Exception {
        Method m = WORKER.getDeclaredMethod("banHeHuaElem", int[][].class, int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, P(fp), z);
    }

    private static int fireScore(String fp, int g, int z) throws Exception {
        Method m = WORKER.getDeclaredMethod("fireExcessScore", int[][].class, int.class, int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, P(fp), g, z);
    }

    private static String fireSegment(String fp, int g, int z) throws Exception {
        Method m = WORKER.getDeclaredMethod("fireExcessFortuneSegment", int[][].class, int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, P(fp), g, z);
    }

    private static String fortune(String fp, String gender, String[] fortunes,
                                  int yongElem, int jiElem, int dayElem, boolean strong) throws Exception {
        Method m = WORKER.getDeclaredMethod("buildFortuneAnalysis",
                int[][].class, LocalDateTime.class, double.class, String[].class,
                int.class, int.class, int.class, boolean.class, String.class, String.class);
        m.setAccessible(true);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        LocalDateTime birth = LocalDateTime.of(1990, 5, 15, 10, 0);
        return (String) m.invoke(w, P(fp), birth, 3.0, fortunes,
                yongElem, jiElem, dayElem, strong, gender, "身强");
    }

    // ==================================================== scoreFortuneElem（身强六支）

    @Test
    @DisplayName("逐运评分·身强食伤：日主甲(木)见火 → +1（泄为喜）")
    void sfStrongShiShang() throws Exception {
        assertThat(score(1, 0, 0, true)).isEqualTo(1);
    }

    @Test
    @DisplayName("逐运评分·身强财：日主甲(木)见土 → +1（耗为喜）")
    void sfStrongCai() throws Exception {
        assertThat(score(2, 0, 0, true)).isEqualTo(1);
    }

    @Test
    @DisplayName("逐运评分·身强官杀：日主甲(木)见金 → 0（克身本喜然取平）")
    void sfStrongGuan() throws Exception {
        assertThat(score(3, 0, 0, true)).isEqualTo(0);
    }

    @Test
    @DisplayName("逐运评分·身强比劫印：日主甲(木)见木·水 → -1（助旺为忌）")
    void sfStrongBiYin() throws Exception {
        assertThat(score(0, 0, 0, true)).isEqualTo(-1);
        assertThat(score(4, 0, 0, true)).isEqualTo(-1);
    }

    @Test
    @DisplayName("逐运评分·身弱印比：日主甲(木)见木(比)·水(印) → +1（生扶为喜）")
    void sfWeakYinBi() throws Exception {
        assertThat(score(0, 0, 0, false)).isEqualTo(1);
        assertThat(score(4, 0, 0, false)).isEqualTo(1);
    }

    @Test
    @DisplayName("逐运评分·身弱克泄耗：日主甲(木)见火·土·金 → -1（为忌）")
    void sfWeakKeXie() throws Exception {
        assertThat(score(1, 0, 0, false)).isEqualTo(-1);
        assertThat(score(2, 0, 0, false)).isEqualTo(-1);
        assertThat(score(3, 0, 0, false)).isEqualTo(-1);
    }

    // ==================================================== elemRoleName（五支）

    @Test
    @DisplayName("五行角色·同我者比劫：日主甲(木)见木")
    void roleBiJie() throws Exception {
        assertThat(role(0, 0)).isEqualTo("比劫");
    }

    @Test
    @DisplayName("五行角色·我生者食伤：日主甲(木)见火")
    void roleShiShang() throws Exception {
        assertThat(role(0, 1)).isEqualTo("食伤");
    }

    @Test
    @DisplayName("五行角色·我克者财：日主甲(木)见土")
    void roleCai() throws Exception {
        assertThat(role(0, 2)).isEqualTo("财");
    }

    @Test
    @DisplayName("五行角色·克我者官杀：日主甲(木)见金")
    void roleGuanSha() throws Exception {
        assertThat(role(0, 3)).isEqualTo("官杀");
    }

    @Test
    @DisplayName("五行角色·生我者印：日主甲(木)见水")
    void roleYin() throws Exception {
        assertThat(role(0, 4)).isEqualTo("印");
    }

    // ==================================================== banHeHuaElem（五支）

    @Test
    @DisplayName("半合·申子辰化水：命见申、运支子 → 水(4)")
    void banHeShui() throws Exception {
        assertThat(banHe("甲申 丙寅 甲子 丁卯", 0)).isEqualTo(4);
    }

    @Test
    @DisplayName("半合·亥卯未化木：命见亥、运支卯 → 木(0)")
    void banHeMu() throws Exception {
        assertThat(banHe("甲子 丙寅 甲子 丁亥", 3)).isEqualTo(0);
    }

    @Test
    @DisplayName("半合·寅午戌化火：命见寅、运支午 → 火(1)")
    void banHeHuo() throws Exception {
        assertThat(banHe("甲子 丙寅 甲子 丁卯", 6)).isEqualTo(1);
    }

    @Test
    @DisplayName("半合·巳酉丑化金：命见巳、运支酉 → 金(3)")
    void banHeJin() throws Exception {
        assertThat(banHe("甲子 丙寅 甲子 丁巳", 9)).isEqualTo(3);
    }

    @Test
    @DisplayName("半合·不成：运支子而局中无申辰 → -1")
    void banHeNone() throws Exception {
        assertThat(banHe("甲子 丙寅 甲子 丁卯", 0)).isEqualTo(-1);
    }

    // ==================================================== fireExcessScore

    @Test
    @DisplayName("火炎土燥评分·水干辰土 → +2（水金湿土为喜）")
    void fireScore() throws Exception {
        assertThat(fireScore("甲子 丙寅 甲子 丁卯", 8, 4)).isEqualTo(2);
        assertThat(fireScore("甲子 丙寅 甲子 丁卯", 2, 9)).isEqualTo(0);
        assertThat(fireScore("甲子 丙寅 甲子 丁卯", 6, 6)).isEqualTo(0);
    }

    // ==================================================== fireExcessFortuneSegment

    @Test
    @DisplayName("火炎土燥逐运·癸坐子水 → 吉（调候得水、无破）")
    void fireSegmentJi() throws Exception {
        assertThat(fireSegment("甲子 丙寅 甲子 丁卯", 9, 0)).isEqualTo("吉");
    }

    @Test
    @DisplayName("火炎土燥逐运·壬坐丑湿土 → 含「湿土晦火」「丑午相害」注记")
    void fireSegmentChou() throws Exception {
        String s = fireSegment("甲子 丙寅 甲子 丁卯", 8, 1);
        assertThat(s).contains("湿土").contains("丑午相害");
    }

    @Test
    @DisplayName("火炎土燥逐运·庚金坐申（财金得地）→ 吉中带劳（火旺克金、得财亦劳）")
    void fireSegmentCaiLao() throws Exception {
        assertThat(fireSegment("甲子 丙寅 甲子 丁卯", 6, 8)).startsWith("吉中带劳");
    }

    @Test
    @DisplayName("火炎土燥逐运·丙火坐午（全火无制）→ 凶")
    void fireSegmentXiong() throws Exception {
        assertThat(fireSegment("甲寅 丙寅 甲寅 丁卯", 2, 6)).isEqualTo("凶");
    }

    // ==================================================== buildFortuneAnalysis

    @Test
    @DisplayName("大运方向·阳年干男命 → 顺行排运")
    void fortuneDirYangMaleShun() throws Exception {
        String s = fortune("甲子 丙寅 甲子 丁卯", "男", new String[]{}, 1, 0, 0, true);
        assertThat(s).contains("年干甲（阳）·男→顺行排运");
    }

    @Test
    @DisplayName("大运方向·阴年干男命 → 逆行排运")
    void fortuneDirYinMaleNi() throws Exception {
        String s = fortune("乙丑 丙寅 甲子 丁卯", "男", new String[]{}, 1, 0, 0, true);
        assertThat(s).contains("年干乙（阴）·男→逆行排运");
    }

    @Test
    @DisplayName("大运方向·阴年干女命 → 顺行排运")
    void fortuneDirYinFemaleShun() throws Exception {
        String s = fortune("乙丑 丙寅 甲子 丁卯", "女", new String[]{}, 1, 0, 0, true);
        assertThat(s).contains("年干乙（阴）·女→顺行排运");
    }

    @Test
    @DisplayName("逐运评析·分段标题齐全 + 比劫运判「凶（助忌损用）」、食伤运判「吉（喜用得力）」")
    void fortuneVerdictAndSections() throws Exception {
        String s = fortune("甲子 丙寅 甲子 丁卯", "男", new String[]{"甲寅", "丙午"}, 1, 0, 0, true);
        assertThat(s).contains("【大运方向】").contains("【起运依据】")
                .contains("【大运逐运评析】").contains("【流年（");
        assertThat(s).contains("凶（助忌损用）");
        assertThat(s).contains("吉（喜用得力）");
    }
}
