package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 · 数据分支矩阵（离线·反射·无需外部环境）。
 *
 * <p>按 SKILL「数据分支＝按业务数据取值维度（星曜组合/命格/五行局/庙陷/四化/阴阳男女/告警类型…）逐一覆盖」口径，
 * 对以下**数据维度**做确定性穷举（而非随机命中），补齐既有套件未逐值锁定的分支：
 * <ol>
 *   <li><b>命格等第</b>（卷三·论人命入格/看命总纲，{@code judgeMingGeGrade}）——六个等第分支全覆盖：
 *       上上之命 / 上次之命 / 平等 / 平常命 / 下格之命 / 中格；</li>
 *   <li><b>安命吉凶</b>（卷三·论人生时安命吉凶，{@code judgeAnMingJiXiong}）——12 生时支 × 12 命宫支 共 144 组，
 *       断言「时支阴阳 == 命宫阴阳 ⇔ 吉，否则少遂」全等；</li>
 *   <li><b>告警类型</b>（R18 大凶 / R19 寿元关 / R20 重大疾病，{@code judgeAlertTypes}）——三类判据的全部
 *       触发组合（含多类同时命中）与不触发；并核 {@code hasShaPair} 三种双煞配对；</li>
 *   <li><b>生年四化</b>（卷二·安禄权科忌四星变化诀，{@code sihuaStars}）——十天干 甲…癸 各返回禄/权/科/忌 4 星，
 *       逐干与经典表逐字相等。</li>
 * </ol>
 * 每例均断言方法实际产出的分类值，非空跑；纯反射，0 环境依赖。
 */
@DisplayName("紫微斗数·数据分支矩阵：命格等第/安命吉凶/告警三类/生年四化（逐值锁定）")
class ZiWeiDouShuDataBranchMatrixTest {

    /** 十二地支（子…亥），索引与 JobWorker 内一致。 */
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    private static Object invoke(String name, Class<?>[] sig, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("反射调用失败: " + name, e);
        }
    }

    private static Map<Integer, List<String>> chart() {
        return new LinkedHashMap<>();
    }

    private static void put(Map<Integer, List<String>> m, int b, String... stars) {
        m.put(b, new ArrayList<>(Arrays.asList(stars)));
    }

    private static Set<String> set(String... xs) {
        return new HashSet<>(Arrays.asList(xs));
    }

    private static String grade(List<String> hit, Set<String> mingMain, Set<String> sanFang,
                                Map<Integer, List<String>> at, int mingIdx, String fourTransform) {
        return (String) invoke("judgeMingGeGrade",
                new Class<?>[]{List.class, Set.class, Set.class, Map.class, int.class, String.class},
                hit, mingMain, sanFang, at, mingIdx, fourTransform);
    }

    // ========================= 1. 命格等第：六分支 =========================

    @Test
    @DisplayName("judgeMingGeGrade 六等第全覆盖：上上/上次/平等/平常/下格/中格（按经典判据逐一构造）")
    void mingGeGradeAllSixBranches() {
        // ① 下格之命：命宫正曜落陷（太阳@子=陷）＋三方会煞（擎羊）→「居陷地又加杀」。
        Map<Integer, List<String>> b1 = chart();
        put(b1, 0, "太阳", "擎羊");
        assertThat(grade(List.of(), set("太阳"), set("太阳", "擎羊"), b1, 0, null))
                .as("太阳陷@子+擎羊 → 下格之命")
                .contains("下格之命");

        // ② 上上之命：入格 + 命宫庙旺（紫微@午=庙）+ 科权禄齐会三方（无煞）。
        Map<Integer, List<String>> b2 = chart();
        put(b2, 6, "紫微", "天机");   // 命宫午
        put(b2, 10, "太阳");          // 戌（+4）
        put(b2, 2, "武曲");           // 寅（+8）
        String ft2 = "天机化禄;太阳化权;武曲化科;太阴化忌;";
        assertThat(grade(List.of("贵局"), set("紫微"), set("紫微", "天机", "太阳", "武曲"), b2, 6, ft2))
                .as("入格+庙旺+科权禄齐会三方 → 上上之命")
                .contains("上上之命");

        // ③ 上次之命：不入庙（紫微@子=平和）＋加吉（左辅在三方）＋科权禄守命（紫微化权在命宫）。
        Map<Integer, List<String>> b3 = chart();
        put(b3, 0, "紫微");
        assertThat(grade(List.of(), set("紫微"), set("左辅"), b3, 0, "紫微化权;"))
                .as("不入庙+加吉+科权禄守命 → 上次之命")
                .contains("上次之命");

        // ④ 平等：入庙（紫微@午=庙）＋三方不加吉。
        Map<Integer, List<String>> b4 = chart();
        put(b4, 6, "紫微");
        assertThat(grade(List.of(), set("紫微"), set(), b4, 6, null))
                .as("入庙不加吉 → 平等")
                .contains("平等");

        // ⑤ 平常命：不入庙（紫微@子=平和）＋不加吉。
        Map<Integer, List<String>> b5 = chart();
        put(b5, 0, "紫微");
        assertThat(grade(List.of(), set("紫微"), set(), b5, 0, null))
                .as("不入庙不加吉 → 平常命")
                .contains("平常命");

        // ⑥ 中格：入庙＋加吉，但不入格/科权禄不齐会 → 落到末段「中格·吉凶相伴」。
        Map<Integer, List<String>> b6 = chart();
        put(b6, 6, "紫微");
        assertThat(grade(List.of(), set("紫微"), set("左辅"), b6, 6, null))
                .as("入庙+加吉但未达各档条件 → 中格")
                .contains("中格");
    }

    // ========================= 2. 安命吉凶：12×12 =========================

    @Test
    @DisplayName("judgeAnMingJiXiong：12 生时支 × 12 命宫支 全枚举，时支阴阳==命宫阴阳 ⇔「吉/少遂」")
    void anMingJiXiongAllParity() {
        Class<?>[] sig = {int.class, int.class};
        int jiCnt = 0, shaoCnt = 0;
        for (int hb = 0; hb < 12; hb++) {
            for (int mi = 0; mi < 12; mi++) {
                String r = (String) invoke("judgeAnMingJiXiong", sig, hb, mi);
                assertThat(r).as("时支=%s 命宫=%s", ZHI[hb], ZHI[mi]).isNotBlank();
                boolean expectJi = (hb % 2 == 0) == (mi % 2 == 0);
                if (expectJi) {
                    assertThat(r).as("时支=%s(阴阳=%s) 命宫=%s → 应吉", ZHI[hb], hb % 2 == 0 ? "阳" : "阴", ZHI[mi])
                            .contains("吉（命安同组六宫）");
                    jiCnt++;
                } else {
                    assertThat(r).as("时支=%s 命宫=%s → 应少遂", ZHI[hb], ZHI[mi]).contains("少遂");
                    shaoCnt++;
                }
            }
        }
        assertThat(jiCnt).as("阴阳同组应为 72 组").isEqualTo(72);
        assertThat(shaoCnt).as("阴阳异组应为 72 组").isEqualTo(72);
    }

    // ========================= 3. 告警三类 =========================

    @SuppressWarnings("unchecked")
    private static List<String> alertTypes(List<String> ming, List<String> jiE, boolean evil, boolean liangFallen) {
        return (List<String>) invoke("judgeAlertTypes",
                new Class<?>[]{List.class, List.class, boolean.class, boolean.class}, ming, jiE, evil, liangFallen);
    }

    @Test
    @DisplayName("judgeAlertTypes 三类告警全组合：大凶/寿元关/重大疾病 各自及同时触发、及不触发")
    void alertTypesAllCombos() {
        // 不触发：命/疾厄无煞、无凶格、天梁不落陷。
        assertThat(alertTypes(List.of("紫微"), List.of("天府"), false, false))
                .as("无任何凶判据 → 空").isEmpty();

        // 大凶：本命凶格命中。
        assertThat(alertTypes(List.of("紫微"), List.of("天府"), true, false))
                .as("evilPatternHit → 大凶").containsExactly("大凶");

        // 大凶：命宫双煞同宫（擎羊+陀罗）亦触发（不依赖 evilPatternHit）。
        assertThat(alertTypes(List.of("擎羊", "陀罗"), List.of("天府"), false, false))
                .as("命宫羊陀同宫 → 大凶").containsExactly("大凶");

        // 寿元关：天梁在命 + 落陷；或天梁在命 + 命宫见煞（有煞）。
        assertThat(alertTypes(List.of("天梁"), List.of("天府"), false, true))
                .as("天梁落陷 → 寿元关").containsExactly("寿元关");
        assertThat(alertTypes(List.of("天梁", "擎羊"), List.of("天府"), false, false))
                .as("天梁+命宫煞 → 寿元关（羊单见，非双煞）").containsExactly("寿元关");

        // 重大疾病：疾厄宫擎羊/陀罗；或疾厄宫双煞同宫。
        assertThat(alertTypes(List.of("紫微"), List.of("擎羊"), false, false))
                .as("疾厄擎羊 → 重大疾病").containsExactly("重大疾病");
        assertThat(alertTypes(List.of("紫微"), List.of("火星", "铃星"), false, false))
                .as("疾厄火铃同宫 → 重大疾病").containsExactly("重大疾病");

        // 三类同时命中：命宫天梁+羊陀（大凶+寿元关）、疾厄擎羊（重大疾病）。
        assertThat(alertTypes(List.of("天梁", "擎羊", "陀罗"), List.of("擎羊"), false, false))
                .as("三类判据齐见 → 大凶+寿元关+重大疾病")
                .containsExactlyInAnyOrder("大凶", "寿元关", "重大疾病");
    }

    @Test
    @DisplayName("hasShaPair：羊陀/火铃/空劫 三种双煞同宫为真，杂煞为假")
    void shaPairAllThree() {
        Class<?>[] sig = {List.class};
        assertThat((Boolean) invoke("hasShaPair", sig, List.of("擎羊", "陀罗"))).as("羊陀").isTrue();
        assertThat((Boolean) invoke("hasShaPair", sig, List.of("火星", "铃星"))).as("火铃").isTrue();
        assertThat((Boolean) invoke("hasShaPair", sig, List.of("地劫", "天空"))).as("空劫").isTrue();
        assertThat((Boolean) invoke("hasShaPair", sig, List.of("擎羊", "火星"))).as("羊+火非同宫双煞").isFalse();
        assertThat((Boolean) invoke("hasShaPair", sig, List.of("紫微"))).as("无煞").isFalse();
    }

    // ========================= 4. 生年四化：十干 =========================

    @Test
    @DisplayName("sihuaStars：十天干 甲…癸 四化逐字精确（卷二·安禄权科忌四星变化诀）")
    void sihuaAllTenStemsExact() {
        String[][] expect = {
                {"廉贞", "破军", "武曲", "太阳"},   // 甲
                {"天机", "天梁", "紫微", "太阴"},   // 乙
                {"天同", "天机", "文昌", "廉贞"},   // 丙
                {"太阴", "天同", "天机", "巨门"},   // 丁
                {"贪狼", "太阴", "右弼", "天机"},   // 戊
                {"武曲", "贪狼", "天梁", "文曲"},   // 己
                {"太阳", "武曲", "太阴", "天同"},   // 庚
                {"巨门", "太阳", "文曲", "文昌"},   // 辛
                {"天梁", "紫微", "天府", "武曲"},   // 壬
                {"破军", "巨门", "太阴", "贪狼"}    // 癸
        };
        String[] gan = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
        for (int g = 0; g < 10; g++) {
            String[] r = (String[]) invoke("sihuaStars", new Class<?>[]{int.class}, g);
            assertThat(r).as("年干 %s 四化（禄权科忌）", gan[g]).containsExactly(expect[g]);
        }
    }
}
