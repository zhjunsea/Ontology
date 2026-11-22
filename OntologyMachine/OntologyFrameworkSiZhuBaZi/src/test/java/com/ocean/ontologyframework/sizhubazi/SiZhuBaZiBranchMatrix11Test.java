package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四柱命理 · 分支矩阵补充（第十一阶段）· 具名格局逐格命中（纯离线）。
 *
 * <p>{@code hitNamedPatterns} 实装了《三命通会》卷五/卷六的大量具名/外格，三阶段仅覆盖
 * 魁罡/日贵日德/日禄归时/六乙鼠贵/壬骑龙背。本类对余下具名格逐格以受控命例断言其「命中」：
 * 六甲趋乾、六壬趋艮、天元作禄、天元坐煞、时上一位贵、岁德正官、时上正官、年上七煞、官煞混杂、
 * 财官双美、专禄要制、金神、时墓、禄马同乡、井栏叉、子遥巳、丑遥巳、飞天禄马、倒冲禄马、日刃、
 * 时上偏财、勾陈得位、玄武当权、白虎持势、刑合。
 *
 * <p>断言以 {@code hitNamedPatterns} 返回的 {@code String[]{名, 描述, 出处}} 之首元素「名」为准。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第十一阶段·具名格局逐格命中）")
class SiZhuBaZiBranchMatrix11Test {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;
    private static final String GAN = "甲乙丙丁戊己庚辛壬癸";

    private static int[][] P(String fp) throws Exception {
        Method m = WORKER.getDeclaredMethod("parsePillars", String.class);
        m.setAccessible(true);
        return (int[][]) m.invoke(null, fp);
    }

    private static int dg(char gan) {
        return GAN.indexOf(gan);
    }

    @SuppressWarnings("unchecked")
    private static List<String[]> named(String fp, char dayGan) throws Exception {
        Method m = WORKER.getDeclaredMethod("hitNamedPatterns", int[][].class, int.class);
        m.setAccessible(true);
        return (List<String[]>) m.invoke(null, P(fp), dg(dayGan));
    }

    private static boolean hit(List<String[]> hits, String name) {
        for (String[] h : hits) {
            if (name.equals(h[0])) {
                return true;
            }
        }
        return false;
    }

    private static void assertHit(String fp, char dayGan, String name) throws Exception {
        List<String[]> hs = named(fp, dayGan);
        assertThat(hit(hs, name)).as("应命中具名格：%s（实际命中：%s）", name, names(hs)).isTrue();
    }

    private static String names(List<String[]> hs) {
        StringBuilder sb = new StringBuilder();
        for (String[] h : hs) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(h[0]);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("具名格·六甲趋乾：甲日见亥")
    void npLiuJiaQuQian() throws Exception {
        assertHit("乙亥 丙寅 甲子 丁卯", '甲', "六甲趋乾");
    }

    @Test
    @DisplayName("具名格·六壬趋艮：壬日见寅")
    void npLiuRenQuGen() throws Exception {
        assertHit("壬寅 甲辰 壬戌 丙午", '壬', "六壬趋艮");
    }

    @Test
    @DisplayName("具名格·天元作禄：日支藏正官（庚午·午藏丁正官）")
    void npTianYuanZuoLu() throws Exception {
        assertHit("戊辰 己未 庚午 丙子", '庚', "天元作禄");
    }

    @Test
    @DisplayName("具名格·天元坐煞：日支藏七煞（甲申·申藏庚偏官）")
    void npTianYuanZuoSha() throws Exception {
        assertHit("甲子 丙寅 甲申 丁卯", '甲', "天元坐煞");
    }

    @Test
    @DisplayName("具名格·时上一位贵：时干独见七煞一位")
    void npShiShangYiWeiGui() throws Exception {
        assertHit("甲子 丙寅 甲子 庚午", '甲', "时上一位贵");
    }

    @Test
    @DisplayName("具名格·岁德正官：年干正官")
    void npSuiDeZhengGuan() throws Exception {
        assertHit("辛酉 丙寅 甲子 丁卯", '甲', "岁德正官");
    }

    @Test
    @DisplayName("具名格·时上正官：时干正官")
    void npShiShangZhengGuan() throws Exception {
        assertHit("甲子 丙寅 甲子 辛未", '甲', "时上正官");
    }

    @Test
    @DisplayName("具名格·年上七煞：年干七煞")
    void npNianShangQiSha() throws Exception {
        assertHit("庚申 丙寅 甲子 丁卯", '甲', "年上七煞");
    }

    @Test
    @DisplayName("具名格·官煞混杂：正官与七煞并见")
    void npGuanShaHunZa() throws Exception {
        assertHit("庚申 丙寅 甲子 辛未", '甲', "官煞混杂");
    }

    @Test
    @DisplayName("具名格·财官双美：壬午日自坐财官（不见木旺）")
    void npCaiGuanShuangMei() throws Exception {
        assertHit("壬午 庚戌 壬午 辛丑", '壬', "财官双美");
    }

    @Test
    @DisplayName("具名格·专禄要制：六庚日见巳时")
    void npZhuanLuYaoZhi() throws Exception {
        assertHit("丙寅 己亥 庚午 辛巳", '庚', "专禄要制");
    }

    @Test
    @DisplayName("具名格·金神：时柱乙丑/己巳/癸酉")
    void npJinShen() throws Exception {
        assertHit("甲子 丙寅 甲子 乙丑", '甲', "金神");
    }

    @Test
    @DisplayName("具名格·时墓：时支为日主五行墓库（甲木墓未）")
    void npShiMu() throws Exception {
        assertHit("甲子 丙寅 甲子 丁未", '甲', "时墓");
    }

    @Test
    @DisplayName("具名格·禄马同乡：庚日禄在申、年支午起驿马在申、申现")
    void npLuMaTongXiang() throws Exception {
        assertHit("戊午 己未 庚申 壬午", '庚', "禄马同乡");
    }

    @Test
    @DisplayName("具名格·井栏叉：庚日申子辰全、无寅午戌")
    void npJingLanCha() throws Exception {
        assertHit("庚申 庚子 庚辰 丙子", '庚', "井栏叉");
    }

    @Test
    @DisplayName("具名格·子遥巳：甲子日甲子时、无午巳丑")
    void npZiYaoSi() throws Exception {
        assertHit("甲子 丙寅 甲子 甲子", '甲', "子遥巳");
    }

    @Test
    @DisplayName("具名格·丑遥巳：辛丑日丑多、无巳酉")
    void npChouYaoSi() throws Exception {
        assertHit("辛丑 辛丑 辛丑 丙寅", '辛', "丑遥巳");
    }

    @Test
    @DisplayName("具名格·飞天禄马：壬子日子多冲午、无午")
    void npFeiTianLuMa() throws Exception {
        assertHit("壬子 壬子 壬子 丙寅", '壬', "飞天禄马");
    }

    @Test
    @DisplayName("具名格·倒冲禄马：丙午日午多倒冲子癸、无子")
    void npDaoChongLuMa() throws Exception {
        assertHit("丙午 甲午 丙午 丙寅", '丙', "倒冲禄马");
    }

    @Test
    @DisplayName("具名格·日刃：丙午/戊午/壬子日自坐羊刃（戊午）")
    void npRiRen() throws Exception {
        assertHit("戊午 己未 戊午 乙卯", '戊', "日刃");
    }

    @Test
    @DisplayName("具名格·时上偏财：时干透偏财")
    void npShiShangPianCai() throws Exception {
        assertHit("甲子 丙寅 甲子 戊辰", '甲', "时上偏财");
    }

    @Test
    @DisplayName("具名格·勾陈得位：戊日坐财官、财官俱见")
    void npGouChenDeWei() throws Exception {
        assertHit("戊子 甲寅 戊子 丙辰", '戊', "勾陈得位");
    }

    @Test
    @DisplayName("具名格·玄武当权：壬日坐财官、财官俱见")
    void npXuanWuDangQuan() throws Exception {
        assertHit("壬午 庚戌 壬午 辛丑", '壬', "玄武当权");
    }

    @Test
    @DisplayName("具名格·白虎持势：庚日坐财官、财官俱见")
    void npBaiHuChiShi() throws Exception {
        assertHit("庚寅 丙午 庚寅 丙子", '庚', "白虎持势");
    }

    @Test
    @DisplayName("具名格·刑合：癸日寅时、巳不填实")
    void npXingHe() throws Exception {
        assertHit("癸卯 甲寅 癸卯 甲寅", '癸', "刑合");
    }
}
