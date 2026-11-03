package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四柱命理 · 经典起例单元测试（离线，不依赖 DB/Ontop/RabbitMQ）。
 *
 * <p>为响应「按章节逐字逐句检查」的要求，对六书核心起例以<strong>可执行证据</strong>逐条核验，
 * 每条断言均标注典籍出处与原文口诀，便于与经典逐字对照：
 * <ul>
 *   <li>年柱 —— 以立春分年（《渊海子平》卷之一·论年上起月）；</li>
 *   <li>月柱 —— 五虎遁（《渊海子平》「甲己之年丙作首」）；</li>
 *   <li>日柱 —— 干支纪日 JDN 锚定（《三命通会》卷一·论日）；</li>
 *   <li>时柱 —— 五鼠遁（《渊海子平》「甲己还加甲」）；</li>
 *   <li>纳音 60 组 —— 《三命通会》卷一·总论纳音；</li>
 *   <li>天乙贵人 —— 《三命通会》卷二·论天乙贵人；</li>
 *   <li>寄生十二宫（长生）—— 《三命通会》卷二·论天干阴阳生死；</li>
 *   <li>命宫起例 —— 《三命通会》卷二·论坐命官「乃丁卯宫也」原例。</li>
 * </ul>
 *
 * <p>说明：年/月柱按精确交节时刻判定（第 31 步），精确时刻来自 DB {@code solar_term_time}；
 * 离线环境 DB 不可用，自动回退内置近似（误差 &lt; 1 天，流程不阻断），故测试用例均选在
 * 交节前后「安全区」内的日期，回退算法与精确算法结论一致。
 */
@DisplayName("四柱命理 · 经典起例核验（五虎遁/五鼠遁/纳音/天乙/命宫）")
class SiZhuBaZiClassicRulesTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static final String[] GAN =
            {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI =
            {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    // ---------------------------------------------------------------- 反射工具

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static Object invokeStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object invokeInstance(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static String gz(int[] p) {
        return GAN[Math.floorMod(p[0], 10)] + ZHI[Math.floorMod(p[1], 12)];
    }

    private static int[] yearPillar(LocalDateTime d) throws Exception {
        return (int[]) invokeInstance(newWorker(), "yearPillar", new Class<?>[]{LocalDateTime.class}, d);
    }

    private static int[] monthPillar(LocalDateTime d) throws Exception {
        return (int[]) invokeInstance(newWorker(), "monthPillar", new Class<?>[]{LocalDateTime.class}, d);
    }

    // ---------------------------------------------------------------- 年柱（立春分年）

    @Test
    @DisplayName("年柱：以立春分年（2024-02-03 立春前 → 癸卯；2024-02-10 立春后 → 甲辰）")
    void yearPillarByLiChun() throws Exception {
        assertThat(gz(yearPillar(LocalDateTime.of(2024, 2, 3, 12, 0)))).isEqualTo("癸卯");
        assertThat(gz(yearPillar(LocalDateTime.of(2024, 2, 10, 12, 0)))).isEqualTo("甲辰");
        assertThat(gz(yearPillar(LocalDateTime.of(2019, 2, 3, 12, 0)))).isEqualTo("戊戌");
        assertThat(gz(yearPillar(LocalDateTime.of(2019, 2, 10, 12, 0)))).isEqualTo("己亥");
    }

    // ---------------------------------------------------------------- 月柱（五虎遁）

    @Test
    @DisplayName("月柱·五虎遁：甲己丙作首 / 乙庚戊为头 / 丙辛庚寅起 / 丁壬壬位顺 / 戊癸甲寅求")
    void monthPillarWuHuDun() throws Exception {
        // 寅月（立春后、惊蛰前），地支恒为寅；天干随生年按五虎遁。
        assertThat(gz(monthPillar(LocalDateTime.of(2024, 3, 1, 12, 0)))).isEqualTo("丙寅"); // 甲辰年 甲己→丙
        assertThat(gz(monthPillar(LocalDateTime.of(2023, 2, 10, 12, 0)))).isEqualTo("甲寅"); // 癸卯年 戊癸→甲
        assertThat(gz(monthPillar(LocalDateTime.of(2020, 2, 10, 12, 0)))).isEqualTo("戊寅"); // 庚子年 乙庚→戊
        assertThat(gz(monthPillar(LocalDateTime.of(2019, 2, 10, 12, 0)))).isEqualTo("丙寅"); // 己亥年 甲己→丙
        assertThat(gz(monthPillar(LocalDateTime.of(2018, 2, 10, 12, 0)))).isEqualTo("甲寅"); // 戊戌年 戊癸→甲
    }

    @Test
    @DisplayName("月柱·五虎遁：子月（大雪后小寒前）天干亦符五虎遁（2024-01-01 → 甲子月）")
    void monthPillarZiMonth() throws Exception {
        // 2024-01-01 在大雪(2023-12-07)后、小寒(2024-01-06)前 → 子月；生年癸卯，戊癸→甲寅，子月为甲子。
        assertThat(gz(monthPillar(LocalDateTime.of(2024, 1, 1, 12, 0)))).isEqualTo("甲子");
    }

    // ---------------------------------------------------------------- 日柱（JDN 锚定）

    @Test
    @DisplayName("日柱·JDN 锚定：2000-01-01=戊午（J2000.0）、2024-01-01=甲子、1949-10-01=甲子")
    void dayPillarJdn() throws Exception {
        assertThat(gz((int[]) invokeStatic("dayPillar", new Class<?>[]{LocalDate.class},
                LocalDate.of(2000, 1, 1)))).isEqualTo("戊午");
        assertThat(gz((int[]) invokeStatic("dayPillar", new Class<?>[]{LocalDate.class},
                LocalDate.of(2024, 1, 1)))).isEqualTo("甲子");
        assertThat(gz((int[]) invokeStatic("dayPillar", new Class<?>[]{LocalDate.class},
                LocalDate.of(1949, 10, 1)))).isEqualTo("甲子");
    }

    // ---------------------------------------------------------------- 时柱（五鼠遁）

    @Test
    @DisplayName("时柱·五鼠遁：甲己还加甲 / 乙庚丙作初 / 丙辛从戊起 / 丁壬庚子居 / 戊癸壬子真")
    void hourPillarWuShuDun() throws Exception {
        // 子时（23:00–00:59，hour=23）
        String[] expectZi = {"甲子", "丙子", "戊子", "庚子", "壬子",
                             "甲子", "丙子", "戊子", "庚子", "壬子"};
        for (int dayGan = 0; dayGan < 10; dayGan++) {
            int[] p = (int[]) invokeStatic("hourPillar", new Class<?>[]{int.class, int.class}, dayGan, 23);
            assertThat(gz(p)).as("日干 %s 子时", GAN[dayGan]).isEqualTo(expectZi[dayGan]);
        }
        // 午时（11:00–12:59，hour=12）；甲日午时＝庚午（子甲→午为第 7 位）
        assertThat(gz((int[]) invokeStatic("hourPillar", new Class<?>[]{int.class, int.class}, 0, 12)))
                .isEqualTo("庚午");
    }

    // ---------------------------------------------------------------- 纳音（60 组全表）

    @Test
    @DisplayName("纳音·六十甲子全表（30 名，索引=甲子序/2）：逐组与《三命通会》总论纳音核对")
    void nayinFullTable() throws Exception {
        String[] expect = {
                "海中金", "炉中火", "大林木", "路旁土", "剑锋金", "山头火", "涧下水", "城头土", "白蜡金", "杨柳木",
                "泉中水", "屋上土", "霹雳火", "松柏木", "长流水", "沙中金", "山下火", "平地木", "壁上土", "金箔金",
                "覆灯火", "天河水", "大驿土", "钗钏金", "桑柘木", "大溪水", "沙中土", "天上火", "石榴木", "大海水"
        };
        for (int n = 0; n < 60; n++) {
            String got = (String) invokeStatic("nayinOf", new Class<?>[]{int.class, int.class}, n % 10, n % 12);
            assertThat(got).as("六十甲子第 %d 位 %s%s 纳音", n, GAN[n % 10], ZHI[n % 12])
                    .isEqualTo(expect[n / 2]);
        }
        // 抽查锚点
        assertThat(invokeStatic("nayinOf", new Class<?>[]{int.class, int.class}, 0, 0)).isEqualTo("海中金"); // 甲子
        assertThat(invokeStatic("nayinOf", new Class<?>[]{int.class, int.class}, 8, 8)).isEqualTo("剑锋金"); // 壬申
        assertThat(invokeStatic("nayinOf", new Class<?>[]{int.class, int.class}, 8, 10)).isEqualTo("大海水"); // 壬戌
    }

    // ---------------------------------------------------------------- 天乙贵人

    @Test
    @DisplayName("天乙贵人·《三命通会》卷二：甲戊庚牛羊 / 乙己鼠猴乡 / 丙丁猪鸡位 / 壬癸兔蛇藏 / 六辛逢马虎")
    void tianYiGuiRen() throws Exception {
        Field f = WORKER.getDeclaredField("TIANYI");
        f.setAccessible(true);
        int[][] t = (int[][]) f.get(null);
        // 甲戊庚 → 丑未
        assertThat(new int[]{t[0][0], t[0][1]}).containsExactly(1, 7);   // 甲
        assertThat(new int[]{t[4][0], t[4][1]}).containsExactly(1, 7);   // 戊
        assertThat(new int[]{t[6][0], t[6][1]}).containsExactly(1, 7);   // 庚
        // 乙己 → 子申
        assertThat(new int[]{t[1][0], t[1][1]}).containsExactly(0, 8);   // 乙
        assertThat(new int[]{t[5][0], t[5][1]}).containsExactly(0, 8);   // 己
        // 丙丁 → 亥酉
        assertThat(new int[]{t[2][0], t[2][1]}).containsExactly(11, 9);  // 丙
        assertThat(new int[]{t[3][0], t[3][1]}).containsExactly(11, 9);  // 丁
        // 辛 → 午寅
        assertThat(new int[]{t[7][0], t[7][1]}).containsExactly(6, 2);   // 辛
        // 壬癸 → 卯巳
        assertThat(new int[]{t[8][0], t[8][1]}).containsExactly(3, 5);   // 壬
        assertThat(new int[]{t[9][0], t[9][1]}).containsExactly(3, 5);   // 癸
    }

    // ---------------------------------------------------------------- 寄生十二宫（长生）

    @Test
    @DisplayName("长生·《三命通会》卷二论天干阴阳生死：甲亥 / 丙戊寅 / 庚巳 / 壬申 / 乙午 / 丁己酉 / 辛子 / 癸卯")
    void changShengStage() throws Exception {
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 0, 11)).isEqualTo("长生"); // 甲亥
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 2, 2)).isEqualTo("长生");  // 丙寅
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 4, 2)).isEqualTo("长生");  // 戊寅
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 6, 5)).isEqualTo("长生");  // 庚巳
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 8, 8)).isEqualTo("长生");  // 壬申
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 1, 6)).isEqualTo("长生");  // 乙午
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 3, 9)).isEqualTo("长生");  // 丁酉
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 5, 9)).isEqualTo("长生");  // 己酉
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 7, 0)).isEqualTo("长生");  // 辛子
        assertThat(invokeStatic("stageOf", new Class<?>[]{int.class, int.class}, 9, 3)).isEqualTo("长生");  // 癸卯
    }

    // ---------------------------------------------------------------- 命宫起例

    @Test
    @DisplayName("命宫·《三命通会》卷二原例：甲子年三月（辰月）戌时 → 丁卯宫")
    void mingGongClassicExample() throws Exception {
        // 辰=4、戌=10、甲=0
        assertThat(invokeStatic("mingGong", new Class<?>[]{int.class, int.class, int.class}, 4, 10, 0))
                .isEqualTo("丁卯");
    }

    // ---------------------------------------------------------------- 十神

    @Test
    @DisplayName("十神·对日主（《渊海子平》卷之二·各十神论）：甲日十干依次 比劫食伤财官印，丙日依次 印比劫食伤财官")
    void tenGodIndexTable() throws Exception {
        // 甲（阳木）为日主：甲比 乙劫 丙食 丁伤 戊偏财 己正财 庚偏官(七杀) 辛正官 壬偏印 癸正印
        int[] expectJia = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int t = 0; t < 10; t++) {
            assertThat(invokeStatic("tenGodIndex", new Class<?>[]{int.class, int.class}, 0, t))
                    .as("甲日主 对 %s", GAN[t]).isEqualTo(expectJia[t]);
        }
        // 丙（阳火）为日主：甲偏印 乙正印 丙比 丁劫 戊食 己伤 庚偏财 辛正财 壬偏官 癸正官
        int[] expectBing = {8, 9, 0, 1, 2, 3, 4, 5, 6, 7};
        for (int t = 0; t < 10; t++) {
            assertThat(invokeStatic("tenGodIndex", new Class<?>[]{int.class, int.class}, 2, t))
                    .as("丙日主 对 %s", GAN[t]).isEqualTo(expectBing[t]);
        }
    }

    // ---------------------------------------------------------------- 地支藏干

    @Test
    @DisplayName("地支藏干·《渊海子平》又地支藏遁歌：子癸 丑己癸辛 寅甲丙戊 卯乙 辰戊乙癸 巳丙庚戊 午丁己 未己丁乙 申庚壬戊 酉辛 戌戊辛丁 亥壬甲")
    void hiddenStemsTable() throws Exception {
        String[][] expect = {
                {"癸"}, {"己", "癸", "辛"}, {"甲", "丙", "戊"}, {"乙"}, {"戊", "乙", "癸"}, {"丙", "庚", "戊"},
                {"丁", "己"}, {"己", "丁", "乙"}, {"庚", "壬", "戊"}, {"辛"}, {"戊", "辛", "丁"}, {"壬", "甲"}
        };
        String[][] got = (String[][]) staticField("ZHI_HIDDEN");
        for (int i = 0; i < 12; i++) {
            assertThat(got[i]).as("%s 藏干", ZHI[i]).containsExactly(expect[i]);
        }
    }

    // ---------------------------------------------------------------- 三合局 + 神煞（驿马/桃花/华盖/将星）

    @Test
    @DisplayName("三合局 + 神煞·《三命通会》卷二：申子辰马寅酉辰子 / 寅午戌马申卯戌午 / 巳酉丑马亥午丑酉 / 亥卯未马巳子未卯")
    void sanheAndShenSha() throws Exception {
        int[][] sanhe = {{8, 0, 4}, {2, 6, 10}, {5, 9, 1}, {11, 3, 7}};
        int[] yima = (int[]) staticField("YIMA");       // 驿马
        int[] taohua = (int[]) staticField("TAOHUA");   // 桃花/咸池
        int[] huagai = (int[]) staticField("HUAGAI");   // 华盖
        int[] jiangxing = (int[]) staticField("JIANGXING"); // 将星
        int[] expectYima = {2, 8, 11, 5};
        int[] expectTaohua = {9, 3, 6, 0};
        int[] expectHuagai = {4, 10, 1, 7};
        int[] expectJiangxing = {0, 6, 9, 3};
        for (int g = 0; g < 4; g++) {
            for (int z : sanhe[g]) {
                assertThat(invokeStatic("sanheGroup", new Class<?>[]{int.class}, z))
                        .as("%s 应属三合组 %d", ZHI[z], g).isEqualTo(g);
            }
            assertThat(yima[g]).as("驿马组 %d", g).isEqualTo(expectYima[g]);
            assertThat(taohua[g]).as("桃花组 %d", g).isEqualTo(expectTaohua[g]);
            assertThat(huagai[g]).as("华盖组 %d", g).isEqualTo(expectHuagai[g]);
            assertThat(jiangxing[g]).as("将星组 %d", g).isEqualTo(expectJiangxing[g]);
        }
    }

    // ---------------------------------------------------------------- 六合 / 六冲 / 六害

    @Test
    @DisplayName("地支关系·《三命通会》卷二：六合(子丑寅亥卯戌辰酉巳申午未) / 六冲(子午丑未寅申卯酉辰戌巳亥) / 六害(子未丑午寅巳卯辰申亥酉戌)")
    void liuheChongHai() throws Exception {
        int[][] liuhe = (int[][]) staticField("LIUHE");
        int[][] liuchong = (int[][]) staticField("LIUCHONG");
        int[][] liuhai = (int[][]) staticField("LIUHAI");
        int[][] eHe = {{0, 1}, {2, 11}, {3, 10}, {4, 9}, {5, 8}, {6, 7}};
        int[][] eChong = {{0, 6}, {1, 7}, {2, 8}, {3, 9}, {4, 10}, {5, 11}};
        int[][] eHai = {{0, 7}, {1, 6}, {2, 5}, {3, 4}, {8, 11}, {9, 10}};
        for (int i = 0; i < 6; i++) {
            assertThat(liuhe[i]).as("六合 %d", i).containsExactly(eHe[i]);
            assertThat(liuchong[i]).as("六冲 %d", i).containsExactly(eChong[i]);
            assertThat(liuhai[i]).as("六害 %d", i).containsExactly(eHai[i]);
        }
    }

    // ---------------------------------------------------------------- 天干五合（化气）

    @Test
    @DisplayName("天干五合·《三命通会》论十干合/化气：甲己化土 乙庚化金 丙辛化水 丁壬化木 戊癸化火；甲庚不合")
    void ganWuHeHua() throws Exception {
        int[] pairs = {5, 6, 7, 8, 9};        // 己庚辛壬癸
        int[] expectHua = {0, 1, 2, 3, 4};    // 土金水木火
        for (int i = 0; i < 5; i++) {
            assertThat(invokeStatic("ganWuHe", new Class<?>[]{int.class, int.class}, i, pairs[i]))
                    .as("%s%s 应合化", GAN[i], GAN[pairs[i]]).isEqualTo(expectHua[i]);
        }
        assertThat(invokeStatic("ganWuHe", new Class<?>[]{int.class, int.class}, 0, 6)).isEqualTo(-1); // 甲庚
    }

    // ---------------------------------------------------------------- 相刑

    @Test
    @DisplayName("三刑·《三命通会》卷二·论三刑：寅巳申/丑戌未三刑；子卯相刑；辰午酉亥自刑；子丑不刑")
    void sanXing() throws Exception {
        // 寅巳申 三刑（组内两两相刑）
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 2, 5)).isTrue();
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 5, 8)).isTrue();
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 2, 8)).isTrue();
        // 丑戌未 三刑
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 1, 10)).isTrue();
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 10, 7)).isTrue();
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 1, 7)).isTrue();
        // 子卯 相刑
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 0, 3)).isTrue();
        // 辰午酉亥 自刑
        for (int z : new int[]{4, 6, 9, 11}) {
            assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, z, z))
                    .as("%s 自刑", ZHI[z]).isTrue();
        }
        // 反例
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 0, 1)).isFalse();
        assertThat((Boolean) invokeStatic("xingPair", new Class<?>[]{int.class, int.class}, 2, 6)).isFalse();
    }

    // ---------------------------------------------------------------- 大运排法（自月柱顺/逆排六十甲子）

    @Test
    @DisplayName("大运排法·自月柱起顺/逆行六十甲子（月柱丙寅 → 顺 丁卯戊辰己巳 / 逆 乙丑甲子癸亥）")
    void greatFortunesDirection() throws Exception {
        int[] month = {2, 2}; // 丙寅
        @SuppressWarnings("unchecked")
        java.util.List<String> shun = (java.util.List<String>) invokeStatic(
                "greatFortunes", new Class<?>[]{int[].class, String.class, int.class}, month, "顺", 3);
        assertThat(shun).containsExactly("丁卯", "戊辰", "己巳");
        @SuppressWarnings("unchecked")
        java.util.List<String> ni = (java.util.List<String>) invokeStatic(
                "greatFortunes", new Class<?>[]{int[].class, String.class, int.class}, month, "逆", 3);
        assertThat(ni).containsExactly("乙丑", "甲子", "癸亥");
    }

    // ---------------------------------------------------------------- 十干禄 / 羊刃

    @Test
    @DisplayName("禄与羊刃·《渊海子平》论禄 / 《三命通会》论阳刃：甲禄寅刃卯、丙戊禄巳刃午、庚禄申刃酉、壬禄亥刃子；阴干无刃")
    void luAndYangren() throws Exception {
        int[] lu = (int[]) staticField("LU");
        int[] yangren = (int[]) staticField("YANGREN");
        int[] expectLu = {2, 3, 5, 6, 5, 6, 8, 9, 11, 0};
        for (int g = 0; g < 10; g++) {
            assertThat(lu[g]).as("%s 禄", GAN[g]).isEqualTo(expectLu[g]);
        }
        // 阳干有刃，阴干无刃（-1）
        int[] expectRen = {3, -1, 6, -1, 6, -1, 9, -1, 0, -1};
        for (int g = 0; g < 10; g++) {
            assertThat(yangren[g]).as("%s 羊刃", GAN[g]).isEqualTo(expectRen[g]);
        }
    }

    // ---------------------------------------------------------------- 反射工具（静态字段）

    private static Object staticField(String name) throws Exception {
        Field f = WORKER.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }
}
