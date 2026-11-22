package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四柱命理 · 分支矩阵补充（第九阶段）· 外格侦测 / 专旺成象 / 相忌五行 / 六亲断语（纯离线）。
 *
 * <p>本类针对此前仅被间接覆盖的**纯算理方法**做分支级断言：
 * <ul>
 *   <li>专旺成象（detectZhuanWang）：曲直/炎上/稼穑/从革/润下 五格「成象」与「破格」。</li>
 *   <li>外格侦测（detectExternPattern）：从财格 / 从杀格 / 从儿格 / 化气格 / 无外格。</li>
 *   <li>相神·忌神五行（xiangShenElem/jiShenElem）：顺用（吉神）与逆用（偏官/伤官）。</li>
 *   <li>六亲断语（buildKinshipVerdicts）：四柱宫位结构 + 配偶宫坐星（男财/女官）+ 男比劫夺财 + 时柱子星。</li>
 * </ul>
 *
 * <p>断言以程序返回值为唯一真相源，不反向解析判词。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第九阶段·外格侦测/专旺成象/相忌五行/六亲断语）")
class SiZhuBaZiBranchMatrix9Test {

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

    private static String detectZW(String fp, char dayGan) throws Exception {
        Method m = WORKER.getDeclaredMethod("detectZhuanWang", int[][].class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, P(fp), dg(dayGan));
    }

    private static String detectEP(String fp, char dayGan) throws Exception {
        Method m = WORKER.getDeclaredMethod("detectExternPattern", int[][].class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, P(fp), dg(dayGan));
    }

    private static int xiangShen(String fp) throws Exception {
        Method m = WORKER.getDeclaredMethod("xiangShenElem", int[][].class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, (Object) P(fp));
    }

    private static int jiShen(String fp) throws Exception {
        Method m = WORKER.getDeclaredMethod("jiShenElem", int[][].class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, (Object) P(fp));
    }

    private static String kinship(String fp, char dayGan, int dayElem, int yong, int ji, String gender) throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Method m = WORKER.getDeclaredMethod("buildKinshipVerdicts",
                int[][].class, int.class, int.class, int.class, int.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(w, P(fp), dg(dayGan), dayElem, yong, ji, gender);
    }

    // ==================================================== 一、专旺成象（detectZhuanWang）

    @Test
    @DisplayName("专旺·曲直成象：亥卯未全、无金（戊酉申）")
    void zhuanWangQuZhiCheng() throws Exception {
        assertThat(detectZW("乙亥 己卯 甲子 丁未", '甲')).isEqualTo("专旺格（曲直）");
    }

    @Test
    @DisplayName("专旺·曲直破格：亥卯未全但见酉（金）")
    void zhuanWangQuZhiPo() throws Exception {
        assertThat(detectZW("乙亥 己卯 丁酉 丁未", '甲')).isEmpty();
    }

    @Test
    @DisplayName("专旺·炎上成象：寅午戌全、无水（子亥）")
    void zhuanWangYanShangCheng() throws Exception {
        assertThat(detectZW("丙寅 庚午 丙戌 甲午", '丙')).isEqualTo("专旺格（炎上）");
    }

    @Test
    @DisplayName("专旺·炎上破格：寅午戌全但见子（水）")
    void zhuanWangYanShangPo() throws Exception {
        assertThat(detectZW("丙寅 庚午 丙戌 甲子", '丙')).isEmpty();
    }

    @Test
    @DisplayName("专旺·稼穑成象：辰戌丑未全、无木（寅卯）")
    void zhuanWangJiaSeCheng() throws Exception {
        assertThat(detectZW("戊辰 壬戌 己丑 癸未", '戊')).isEqualTo("专旺格（稼穑）");
    }

    @Test
    @DisplayName("专旺·稼穑破格：辰戌丑未全但天干透木（甲）")
    void zhuanWangJiaSePo() throws Exception {
        assertThat(detectZW("甲辰 壬戌 己丑 癸未", '戊')).isEmpty();
    }

    @Test
    @DisplayName("专旺·从革成象：申酉戌全、无火（午）")
    void zhuanWangCongGeCheng() throws Exception {
        assertThat(detectZW("庚申 乙酉 庚戌 壬子", '庚')).isEqualTo("专旺格（从革）");
    }

    @Test
    @DisplayName("专旺·润下成象：申子辰全、无燥土（戌未）")
    void zhuanWangRunXiaCheng() throws Exception {
        assertThat(detectZW("壬申 庚子 壬辰 甲寅", '壬')).isEqualTo("专旺格（润下）");
    }

    // ==================================================== 二、外格侦测（detectExternPattern）

    @Test
    @DisplayName("外格·从财格：日主无根无印无助、土财一气独旺有气")
    void externCongCai() throws Exception {
        assertThat(detectEP("戊戌 戊戌 甲戌 戊戌", '甲')).isEqualTo("从财格");
    }

    @Test
    @DisplayName("外格·从杀格：日主无根、金（官杀）一气独旺透干")
    void externCongSha() throws Exception {
        assertThat(detectEP("庚酉 庚酉 甲酉 庚酉", '甲')).isEqualTo("从杀格");
    }

    @Test
    @DisplayName("外格·从儿格：日主无根、火（食伤）一气独旺透干")
    void externCongEr() throws Exception {
        assertThat(detectEP("丙午 丙午 甲午 丙午", '甲')).isEqualTo("从儿格");
    }

    @Test
    @DisplayName("外格·化气格：甲己合、化神土得月令（戌）、日主无根")
    void externHuaQi() throws Exception {
        assertThat(detectEP("庚酉 己戌 甲酉 丙巳", '甲')).isEqualTo("化气格（化土）");
    }

    @Test
    @DisplayName("外格·无外格：有根有助、未成专旺/从/化（返回空串）")
    void externNone() throws Exception {
        assertThat(detectEP("甲子 丙寅 戊午 庚申", '甲')).isEmpty();
    }

    // ==================================================== 三、相神·忌神五行（xiangShenElem/jiShenElem）

    @Test
    @DisplayName("相忌·顺用（正官格）：相神取生用神者、忌神取克用神者")
    void xiangJiShunYong() throws Exception {
        // 戊辰 己酉 甲子 戊辰：用神金(3)，正官吉神顺用 → 相神土(2)、忌神火(1)
        assertThat(xiangShen("戊辰 己酉 甲子 戊辰")).isEqualTo(2);
        assertThat(jiShen("戊辰 己酉 甲子 戊辰")).isEqualTo(1);
    }

    @Test
    @DisplayName("相忌·逆用（伤官格）：相神取制用神者、忌神取生用神者")
    void xiangJiNiYong() throws Exception {
        // 己酉 丙午 甲子 庚午：用神火(1)，伤官凶神逆用 → 相神水(4)、忌神木(0)
        assertThat(xiangShen("己酉 丙午 甲子 庚午")).isEqualTo(4);
        assertThat(jiShen("己酉 丙午 甲子 庚午")).isEqualTo(0);
    }

    // ==================================================== 四、六亲断语（buildKinshipVerdicts）

    @Test
    @DisplayName("六亲·四柱宫位结构齐备：年/月/日/时四宫 + 依据")
    void kinshipStructure() throws Exception {
        String v = kinship("甲子 丙寅 戊午 庚申", '戊', 2, 2, 0, "男");
        assertThat(v).contains("六亲吉凶").contains("① 年柱").contains("② 月柱")
                .contains("③ 日支").contains("④ 时柱").contains("依据：");
    }

    @Test
    @DisplayName("六亲·男命配偶宫坐妻星（财）：日支藏财")
    void kinshipMaleSpouseStar() throws Exception {
        // 戊日·子支藏癸(水=戊之财) → 男命配偶宫坐妻星（财）
        String v = kinship("甲子 丙寅 戊子 庚申", '戊', 2, 2, 0, "男");
        assertThat(v).contains("配偶宫坐妻星（财）").contains("主得配偶之助");
    }

    @Test
    @DisplayName("六亲·女命配偶宫坐夫星（官）：日支藏官")
    void kinshipFemaleSpouseStar() throws Exception {
        // 甲日·酉支藏辛(金=甲之正官) → 女命配偶宫坐夫星（官）
        String v = kinship("甲子 丙寅 甲酉 庚申", '甲', 0, 1, 3, "女");
        assertThat(v).contains("配偶宫坐夫星（官）").contains("主得配偶之助");
    }

    @Test
    @DisplayName("六亲·男命比劫夺财：比劫旺于财且财透干 → 防妻缘争竞")
    void kinshipMaleBiJieDuoCai() throws Exception {
        // 甲日·比劫众多(甲甲甲+寅卯藏)、戊财透干 → 男命比劫夺财
        String v = kinship("甲寅 甲卯 甲子 戊辰", '甲', 0, 1, 3, "男");
        assertThat(v).contains("比劫夺财").contains("宜防妻缘争竞");
    }

    @Test
    @DisplayName("六亲·时柱子星（男命官杀为子）：时支藏官")
    void kinshipMaleHourZiStar() throws Exception {
        // 甲日·时支酉藏辛(金=甲之官杀) → 时坐子星（官杀）
        String v = kinship("甲子 丙寅 甲子 甲酉", '甲', 0, 1, 3, "男");
        assertThat(v).contains("时坐子星（官杀）").contains("子女可期");
    }
}
