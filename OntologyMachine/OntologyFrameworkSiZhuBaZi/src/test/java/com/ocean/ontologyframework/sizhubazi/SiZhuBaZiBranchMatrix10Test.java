package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四柱命理 · 分支矩阵补充（第十阶段）· 六亲宫位细分 / 成败救应「未破」边角（纯离线）。
 *
 * <p>本类补齐 {@code buildKinshipVerdicts} 年/月/日/时四宫此前的细分分支，
 * 以及 {@code detectChengBai} 六格在「忌神不透、不构成破格」时的 {@code null} 返回分支：
 * <ul>
 *   <li>年宫：印临祖位（财不伤印 / 财旺克印）、祖宫乏印财旺克印、祖宫平常；偏财临祖；比劫透干夺财。</li>
 *   <li>月宫：官杀偏重 / 比劫+财透争产 / 月门和顺。</li>
 *   <li>日支（配偶宫）：逢冲 / 逢合（婚宫得合）/ 逢害 / 相刑破。</li>
 *   <li>时柱（子息宫）：逢冲 / 与某支伏吟 / 逢破；女命印透克食伤。</li>
 *   <li>成败救应：正官非伤官见官、偏官有印、正财无比劫、正印无财、食神无偏印、伤官无正官 → 均返回 null（不构成破格）。</li>
 * </ul>
 *
 * <p>断言以程序返回值为唯一真相源，不反向解析判词。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第十阶段·六亲宫位细分/成败救应未破边角）")
class SiZhuBaZiBranchMatrix10Test {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;
    private static final String GAN = "甲乙丙丁戊己庚辛壬癸";

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

    private static int dg(char gan) {
        return GAN.indexOf(gan);
    }

    private static String kinship(String fp, char dayGan, int dayElem, int yong, int ji, String gender) throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Method m = WORKER.getDeclaredMethod("buildKinshipVerdicts",
                int[][].class, int.class, int.class, int.class, int.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(w, P(fp), dg(dayGan), dayElem, yong, ji, gender);
    }

    private static String[] chengBai(String tg, char dayGan, String fp, boolean strong) throws Exception {
        Method m = WORKER.getDeclaredMethod("detectChengBai",
                String.class, int.class, int[][].class, boolean.class);
        m.setAccessible(true);
        return (String[]) m.invoke(null, tg, dg(dayGan), P(fp), strong);
    }

    // ==================================================== 一、年柱宫（祖上·父母宫）

    @Test
    @DisplayName("六亲·年宫：印临祖位而财不伤印（印旺于财）")
    void kinshipYearYinInYear() throws Exception {
        // 壬子 丙寅 甲子 庚申：年干壬(偏印)临祖·财(戊土2)不敌印(水4)
        String v = kinship("壬子 丙寅 甲子 庚申", '甲', 0, 1, 3, "男");
        assertThat(v).contains("印临祖位而财不伤印");
    }

    @Test
    @DisplayName("六亲·年宫：祖宫乏印而财旺克印（无印于年、财旺）")
    void kinshipYearCaiKeYin() throws Exception {
        // 戊戌 己未 甲子 戊戌：年无印、全局财(土)远旺于印(水)
        String v = kinship("戊戌 己未 甲子 戊戌", '甲', 0, 1, 3, "男");
        assertThat(v).contains("祖宫乏印而财旺克印").contains("偏财（父星）临祖位");
    }

    @Test
    @DisplayName("六亲·年宫：祖宫十神平常（无印临、财不克印）")
    void kinshipYearPingChang() throws Exception {
        // 甲午 壬子 甲子 壬申：年无印、全局印(水)不弱于财 → 祖宫平常
        String v = kinship("甲午 壬子 甲子 壬申", '甲', 0, 1, 3, "男");
        assertThat(v).contains("祖宫十神平常");
    }

    @Test
    @DisplayName("六亲·年宫：比劫透干夺财（比劫旺于财且透干）")
    void kinshipYearBiJieTouGan() throws Exception {
        // 甲寅 甲卯 甲子 己巳：比劫众多透干、财弱
        String v = kinship("甲寅 甲卯 甲子 己巳", '甲', 0, 1, 3, "男");
        assertThat(v).contains("比劫透干夺财");
    }

    // ==================================================== 二、月柱宫（父母·兄弟门户宫）

    @Test
    @DisplayName("六亲·月宫：官杀偏重、比劫帮身有限")
    void kinshipMonthGuanShaZhong() throws Exception {
        // 庚申 庚酉 甲子 辛酉：官杀(金)远多于比劫(木)
        String v = kinship("庚申 庚酉 甲子 辛酉", '甲', 0, 1, 3, "男");
        assertThat(v).contains("官杀偏重，比劫帮身有限");
    }

    @Test
    @DisplayName("六亲·月宫：比劫现月门且财透 → 兄弟有助亦主分财争产")
    void kinshipMonthBiJieFenCai() throws Exception {
        // 戊辰 甲寅 甲子 戊辰：月干甲(比劫)现、财(戊)透干
        String v = kinship("戊辰 甲寅 甲子 戊辰", '甲', 0, 1, 3, "男");
        assertThat(v).contains("兄弟有助亦主分财争产");
    }

    @Test
    @DisplayName("六亲·月宫：月门十神和顺（非官杀重、无比劫）")
    void kinshipMonthHeShun() throws Exception {
        // 甲子 己巳 甲子 庚午：月干己(财)、月支巳无比劫、官杀不比劫多
        String v = kinship("甲子 己巳 甲子 庚午", '甲', 0, 1, 3, "男");
        assertThat(v).contains("月门十神和顺");
    }

    // ==================================================== 三、日支（配偶宫）

    @Test
    @DisplayName("六亲·日支：逢冲 → 婚宫动荡")
    void kinshipDayChong() throws Exception {
        // 甲子 丙午 甲子 庚午：日支子逢午冲
        String v = kinship("甲子 丙午 甲子 庚午", '甲', 0, 1, 3, "男");
        assertThat(v).contains("日支逢冲").contains("婚宫动荡");
    }

    @Test
    @DisplayName("六亲·日支：逢合 → 婚宫得合")
    void kinshipDayHe() throws Exception {
        // 甲子 乙丑 甲子 丙寅：日支子与丑六合
        String v = kinship("甲子 乙丑 甲子 丙寅", '甲', 0, 1, 3, "男");
        assertThat(v).contains("日支逢合").contains("婚宫得合");
    }

    @Test
    @DisplayName("六亲·日支：逢害 → 夫妻易生嫌隙")
    void kinshipDayHai() throws Exception {
        // 甲子 乙未 甲子 丙寅：日支子与未六害
        String v = kinship("甲子 乙未 甲子 丙寅", '甲', 0, 1, 3, "男");
        assertThat(v).contains("日支逢害").contains("夫妻易生嫌隙");
    }

    @Test
    @DisplayName("六亲·日支：相刑破 → 婚宫动")
    void kinshipDayXingPo() throws Exception {
        // 甲子 乙卯 甲子 丙寅：日支子与卯相刑（子卯刑）
        String v = kinship("甲子 乙卯 甲子 丙寅", '甲', 0, 1, 3, "男");
        assertThat(v).contains("日支与别支相刑/破").contains("婚宫动");
    }

    // ==================================================== 四、时柱（子息宫）

    @Test
    @DisplayName("六亲·时柱：逢冲 → 晚年子息宫动")
    void kinshipHourChong() throws Exception {
        // 甲子 丙午 甲子 甲午：时支午与年支子冲
        String v = kinship("甲子 丙午 甲子 甲午", '甲', 0, 1, 3, "男");
        assertThat(v).contains("时支逢冲").contains("子息宫动");
    }

    @Test
    @DisplayName("六亲·时柱：与某支伏吟 → 时支与年支子子伏吟")
    void kinshipHourFuYin() throws Exception {
        // 甲子 丙寅 甲寅 甲子：时支子与年支子同（子非自刑→伏吟）
        String v = kinship("甲子 丙寅 甲寅 甲子", '甲', 0, 1, 3, "男");
        assertThat(v).contains("时支与年支").contains("伏吟");
    }

    @Test
    @DisplayName("六亲·时柱：逢破 → 时支逢午卯破")
    void kinshipHourPo() throws Exception {
        // 甲子 丙午 甲子 乙卯：时支卯与月支午相破（卯午破）
        String v = kinship("甲子 丙午 甲子 乙卯", '甲', 0, 1, 3, "男");
        assertThat(v).contains("时支逢午卯破");
    }

    @Test
    @DisplayName("六亲·时柱（女命）：印透克食伤 → 子星受制")
    void kinshipFemaleYinKeShiShang() throws Exception {
        // 壬子 壬子 甲子 丙寅：女命印(水)远多于食伤(火)且印透干
        String v = kinship("壬子 壬子 甲子 丙寅", '甲', 0, 2, 1, "女");
        assertThat(v).contains("印透克食伤").contains("子星（食伤）受制");
    }

    // ==================================================== 五、成败救应「未破」边角（detectChengBai→null）

    @Test
    @DisplayName("成败·正官非伤官见官 → 返回 null（不构成破格）")
    void chengBaiZhengGuanNoShangGuan() throws Exception {
        // 戊辰 辛酉 甲子 丙寅：透财/正官/食神，无伤官
        assertThat(chengBai("正官", '甲', "戊辰 辛酉 甲子 丙寅", true)).isNull();
    }

    @Test
    @DisplayName("成败·偏官有印（化杀） → 返回 null（不构成无制破格）")
    void chengBaiPianGuanWithYin() throws Exception {
        // 壬申 庚午 甲子 丙寅：透偏印(壬)、偏官(庚)、食神(丙)
        assertThat(chengBai("偏官", '甲', "壬申 庚午 甲子 丙寅", true)).isNull();
    }

    @Test
    @DisplayName("成败·正财无比劫 → 返回 null（不构成比劫夺财）")
    void chengBaiZhengCaiNoBiJie() throws Exception {
        // 戊辰 己酉 甲子 丙寅：透偏财/正财/食神，无比劫
        assertThat(chengBai("正财", '甲', "戊辰 己酉 甲子 丙寅", true)).isNull();
    }

    @Test
    @DisplayName("成败·正印无财 → 返回 null（不构成贪财坏印）")
    void chengBaiZhengYinNoCai() throws Exception {
        // 壬子 癸酉 甲子 丙寅：透偏印/正印/食神，无财
        assertThat(chengBai("正印", '甲', "壬子 癸酉 甲子 丙寅", true)).isNull();
    }

    @Test
    @DisplayName("成败·食神无偏印 → 返回 null（不构成枭神夺食）")
    void chengBaiShiShenNoPianYin() throws Exception {
        // 丙午 丁巳 甲子 戊辰：透食神/伤官/偏财，无偏印
        assertThat(chengBai("食神", '甲', "丙午 丁巳 甲子 戊辰", true)).isNull();
    }

    @Test
    @DisplayName("成败·伤官无正官 → 返回 null（不构成伤官见官）")
    void chengBaiShangGuanNoZhengGuan() throws Exception {
        // 丙午 丁巳 甲子 戊辰：透伤官/食神/偏财，无正官
        assertThat(chengBai("伤官", '甲', "丙午 丁巳 甲子 戊辰", true)).isNull();
    }
}
