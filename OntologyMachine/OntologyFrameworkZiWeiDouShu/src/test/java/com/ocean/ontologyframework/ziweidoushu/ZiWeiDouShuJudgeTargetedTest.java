package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 逐条断语·定向构造命盘（第1批：judgeLongevity）——用精准"星盘(map)"逐条命中
 * 商贾/艺术/僧道/孤克/寿夭/淫荡/残疾/破相/聪明/富足/贫贱/盗贼/驳杂 的判定分支，提升分支覆盖。
 * 纯反射离线，无需环境。
 */
@DisplayName("逐条断语定向覆盖·judgeLongevity（艺术/僧道/寿夭/淫荡/贫贱…诸职业格）")
class ZiWeiDouShuJudgeTargetedTest {

    @SuppressWarnings("unchecked")
    private static List<String> judgeLongevity(int mingIdx, int shenIdx, Map<Integer, List<String>> at) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class
                    .getDeclaredMethod("judgeLongevity", int.class, int.class, Map.class);
            m.setAccessible(true);
            return (List<String>) m.invoke(null, mingIdx, shenIdx, at);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> judgeLongevity(int mingIdx, int shenIdx, Map<Integer, List<String>> at, String fourTransform) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class
                    .getDeclaredMethod("judgeLongevity", int.class, int.class, Map.class, String.class);
            m.setAccessible(true);
            return (List<String>) m.invoke(null, mingIdx, shenIdx, at, fourTransform);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Integer, List<String>> at(int p, String... stars) {
        Map<Integer, List<String>> m = new LinkedHashMap<>();
        m.put(p, new ArrayList<>(Arrays.asList(stars)));
        return m;
    }

    private static void put(Map<Integer, List<String>> m, int p, String... stars) {
        m.put(p, new ArrayList<>(Arrays.asList(stars)));
    }

    private static boolean has(List<String> g, String kw) {
        return g.stream().anyMatch(s -> s.contains(kw));
    }

    @Test
    @DisplayName("守界：mingIdx<0 / >11 → 返回空")
    void guards() {
        assertThat(judgeLongevity(-1, 0, at(0, "紫微"))).isEmpty();
        assertThat(judgeLongevity(12, 0, at(0, "紫微"))).isEmpty();
    }

    @Test
    @DisplayName("商贾：贪月同杀会机梁 → 商贾")
    void shangJiaA() {
        List<String> g = judgeLongevity(0, 0, at(0, "贪狼", "太阴", "天同", "七杀", "天机"));
        assertThat(has(g, "商贾")).isTrue();
    }

    @Test
    @DisplayName("商贾：紫府遇擎羊 → 商贾")
    void shangJiaB() {
        List<String> g = judgeLongevity(0, 0, at(0, "紫微", "天府", "擎羊"));
        assertThat(has(g, "商贾")).isTrue();
    }

    @Test
    @DisplayName("商贾：武曲迁移利市场 → 商贾")
    void shangJiaC() {
        Map<Integer, List<String>> at = at(0, "天机");
        put(at, 6, "武曲");   // 迁移宫 = ming+6
        List<String> g = judgeLongevity(0, 0, at);
        assertThat(has(g, "商贾")).isTrue();
    }

    @Test
    @DisplayName("艺术：武曲贪狼在命(丑未)化忌或加杀 / 巳亥破武(原文未宫按安星订正) / 卯酉破廉 / 天机或天相守命身 → 艺术；未宫破武安星不可达")
    void yiShuVariants() {
        // 原文 c2 L515：寅申巳亥/丑未辰戌 逢贪狼武曲在命「化忌、加杀」→ 艺术（顿号并列，二者得一即合）。
        // 取安星可达之「丑未武曲贪狼同宫」为真盘（贪狼只与紫微/武曲/廉贞共宫，绝不在寅）：未宫武曲贪狼 + 擎羊(己年禄存午→擎羊未) 加杀 → 艺术。
        assertThat(has(judgeLongevity(7, 7, at(7, "武曲", "贪狼", "擎羊")), "艺术")).isTrue();  // 未 武贪加杀（真盘）
        // 「化忌」路径：未宫武曲贪狼坐命，生年贪狼化忌（癸干，癸年擎羊在丑、未宫无煞），无煞亦应判艺术
        String guiJi = "生年干癸：破军化禄→子；巨门化权→丑；太阴化科→辰；贪狼化忌→未；";
        assertThat(has(judgeLongevity(7, 7, at(7, "武曲", "贪狼"), guiJi), "艺术")).isTrue();     // 未 武贪化忌（无煞，真盘）
        // 无化忌且无煞 → 不应判艺术（验证「化忌」半确被纳入，而非加杀退化）；未宫武贪为安星真盘
        assertThat(has(judgeLongevity(7, 7, at(7, "武曲", "贪狼")), "艺术")).isFalse();           // 未 武贪（无化忌无煞）
        // 原文 c2 L517「破武未宫多巧艺，巳亥安命正相宜」按安星订正为「破武巳亥」（武曲破军只同宫于巳/亥）：巳宫破军武曲→艺术
        assertThat(has(judgeLongevity(5, 5, at(5, "破军", "武曲")), "艺术")).isTrue();      // 巳 破武
        // 未宫不可能有破军+武曲（安星硬规则），订正后不应再误报艺术
        assertThat(has(judgeLongevity(7, 7, at(7, "破军", "武曲")), "艺术")).isFalse();    // 未 破武 不可达
        assertThat(has(judgeLongevity(3, 3, at(3, "破军", "廉贞")), "艺术")).isTrue();      // 卯 破廉
        // 原文 L518「天府若居迁动位，平生定是作奇工」：天府坐迁移宫→艺术（真盘：命子宫七杀、迁移午宫武曲天府，紫微在亥）
        Map<Integer, List<String>> fuQianYi = at(0, "七杀");
        put(fuQianYi, 6, "武曲", "天府");
        assertThat(has(judgeLongevity(0, -1, fuQianYi), "艺术")).isTrue();                  // 迁移(午)天府 → 艺术
        // 天府在命宫、迁移无天府 → 不触发该判据
        assertThat(has(judgeLongevity(0, -1, at(0, "天府")), "艺术")).isFalse();             // 天府在命非迁移→非艺术
        // 原文 L518「帝令财星入墓中」：财星＝武曲；入墓＝四墓宫辰戌丑未；须逢化忌/加杀方主技艺（单武曲庙旺坐四墓主富不主艺）。
        // 正例·加杀（真盘：紫微在申→武曲在辰〔四墓〕；乙年禄存在卯→擎羊在辰）：辰宫武曲+擎羊 → 艺术
        assertThat(has(judgeLongevity(4, -1, at(4, "武曲", "擎羊")), "艺术")).isTrue();           // 辰 武曲+擎羊（财星入墓加杀，真盘）
        // 正例·化忌（真盘同上；壬年武曲化忌在辰）：辰宫武曲 + 生年武曲化忌（无煞）→ 艺术（兼覆盖 tanWuHuaJi 武曲化忌半支）
        String renJi = "生年干壬：天梁化禄→子；紫微化权→丑；天府化科→辰；武曲化忌→辰；";
        assertThat(has(judgeLongevity(4, -1, at(4, "武曲"), renJi), "艺术")).isTrue();             // 辰 武曲化忌（财星入墓化忌，真盘）
        // 负例：辰宫仅武曲、无化忌无加杀（武曲庙旺坐四墓，主富）→ 不判艺术
        assertThat(has(judgeLongevity(4, -1, at(4, "武曲")), "艺术")).isFalse();                    // 辰 武曲庙旺（财星入墓无忌杀→非艺术）
        // 原文 L518「天机天相命身中」：「天机天相」为列举两星（天机机巧、天相印巧），「命身中」＝坐命宫或身宫 → 天机或天相守命身→巧艺奇工。
        //   经典自证「巳亥天机天相贵」「巳位天机天相临」皆为「或」列举（巳可逢天机〔紫微在午〕或逢天相〔紫微在卯〕；两星安星恒隔奇数宫、本不同现），
        //   故非「两星须同宫/分居命身」（那才不可达）；按「单星守命身」可达实现。
        // 天机守命（真盘：紫微在丑→天机在子）：命子·天机 → 艺术
        assertThat(has(judgeLongevity(0, 0, at(0, "天机")), "艺术")).isTrue();              // 命子·天机 → 艺术
        // 天相守命（真盘：紫微在申→天相在子）：命子·天相 → 艺术
        assertThat(has(judgeLongevity(0, 0, at(0, "天相")), "艺术")).isTrue();              // 命子·天相 → 艺术
        // 天机守身（真盘：紫微在子→天机在亥；命丑〔空〕、身亥得天机，命身隔偶数宫）：身亥·天机 → 艺术
        Map<Integer, List<String>> jiShen = at(11, "天机");
        assertThat(has(judgeLongevity(1, 11, jiShen), "艺术")).isTrue();                    // 身亥·天机 → 艺术
        // 负例：命身两宫皆无天机/天相（命子紫微、身亦子）→ 不因「机相」判艺术
        assertThat(has(judgeLongevity(0, 0, at(0, "紫微")), "艺术")).isFalse();             // 命身无机相 → 非艺术（此路径）
    }

    @Test
    @DisplayName("艺术兜底：贪/武在命但化忌串不含该星化忌（tanWuHuaJi=FALSE 侧）+ 僧道六星缺天梁(短路)")
    void yiShuAndSengDaoEdgeSides() {
        // 甲干四化无贪狼/武曲化忌：廉贞禄/破军权/武曲科/太阳忌
        String jia = "生年干甲：廉贞化禄→寅；破军化权→卯；武曲化科→巳；太阳化忌→午；";
        // 未(7)贪狼在命(四墓) + 化忌串无贪狼化忌 + 无煞 → 不判艺术（覆盖「贪狼 present、无贪狼化忌」短路侧）
        assertThat(has(judgeLongevity(7, 7, at(7, "贪狼"), jia), "艺术")).isFalse();
        // 辰(4)武曲在命(四墓) + 化忌串无武曲化忌 + 无煞 → 不判艺术（覆盖「武曲 present、无武曲化忌」短路侧）
        assertThat(has(judgeLongevity(4, 7, at(4, "武曲"), jia), "艺术")).isFalse();
        // 命无贪无武、但化忌串非空 → tanWuHuaJi 两侧皆 FALSE（覆盖 both-false 路径）
        assertThat(has(judgeLongevity(0, 0, at(0, "紫微"), jia), "艺术")).isFalse();
        // 僧道六星短路：天机·七杀·破军·太阳·紫微齐、独缺天梁 → 不成立（覆盖「天梁=false」中间短路侧）
        Map<Integer, List<String>> s5 = at(0, "天机", "太阳", "紫微");
        put(s5, 4, "七杀");
        put(s5, 8, "破军");
        assertThat(has(judgeLongevity(0, 0, s5), "僧道")).isFalse();
        // 其余短路侧：前项真、后面某一项假（七杀/破军/太阳），逐一覆盖六星 && 的可达短路
        assertThat(has(judgeLongevity(0, 0, at(0, "天机")), "僧道")).isFalse();                 // 天机✓ 七杀✗
        Map<Integer, List<String>> pb = at(0, "天机", "太阳", "紫微");
        put(pb, 4, "七杀");                                                                     // 天机✓七杀✓ 破军✗
        assertThat(has(judgeLongevity(0, 0, pb), "僧道")).isFalse();
        Map<Integer, List<String>> ps = at(0, "天机", "紫微");
        put(ps, 4, "七杀");
        put(ps, 8, "破军");
        put(ps, 6, "天梁");                                                                     // 天机✓七杀✓破军✓天梁✓ 太阳✗
        assertThat(has(judgeLongevity(0, 0, ps), "僧道")).isFalse();
    }

    @Test
    @DisplayName("僧道：紫微居卯/酉遇劫空 → 僧道")
    void sengDao() {
        assertThat(has(judgeLongevity(3, 3, at(3, "紫微", "地劫")), "僧道")).isTrue();
        assertThat(has(judgeLongevity(9, 9, at(9, "紫微", "天空")), "僧道")).isTrue();
    }

    @Test
    @DisplayName("寿夭：七杀临身/命 → 寿夭")
    void shouYao() {
        assertThat(has(judgeLongevity(0, 0, at(0, "七杀")), "寿夭")).isTrue();
    }

    @Test
    @DisplayName("淫荡：贪狼入庙守命（丑宫）→ 淫荡")
    void yinDang() {
        assertThat(has(judgeLongevity(1, 1, at(1, "贪狼")), "淫荡")).isTrue();
    }

    @Test
    @DisplayName("残疾：命宫落陷加擎羊 → 残疾")
    void canJi() {
        assertThat(has(judgeLongevity(0, 0, at(0, "太阳", "擎羊")), "残疾")).isTrue();
    }

    @Test
    @DisplayName("破相：疾厄宫擎羊 → 破相")
    void poXiang() {
        Map<Integer, List<String>> at = at(0, "天机");
        put(at, 7, "擎羊");   // 疾厄 = ming-5（mingIdx0→7）
        assertThat(has(judgeLongevity(0, 0, at), "破相")).isTrue();
    }

    @Test
    @DisplayName("聪明：命坐文曲且三方会昌曲 → 聪明")
    void congMing() {
        assertThat(has(judgeLongevity(0, 0, at(0, "文曲", "文昌")), "聪明")).isTrue();
    }

    @Test
    @DisplayName("富足：太阴入庙守命无破耗 → 富足")
    void fuZu() {
        assertThat(has(judgeLongevity(0, 0, at(0, "太阴")), "富足")).isTrue();
    }

    @Test
    @DisplayName("贫贱：三方火铃羊陀 + 武廉巨破 → 贫贱")
    void pinJian() {
        assertThat(has(judgeLongevity(0, 0, at(0, "火星", "武曲")), "贫贱")).isTrue();
    }

    @Test
    @DisplayName("盗贼：命逢破/廉/贪 + 三方七杀 → 盗贼")
    void daoZei() {
        assertThat(has(judgeLongevity(0, 0, at(0, "破军", "七杀")), "盗贼")).isTrue();
    }

    @Test
    @DisplayName("一身驳杂：吉凶并见（左辅+擎羊）→ 驳杂")
    void boZa() {
        List<String> g = judgeLongevity(0, 0, at(0, "左辅", "擎羊"));
        assertThat(has(g, "驳杂") || has(g, "身")).isTrue();
    }

    @Test
    @DisplayName("judgeKeQin：yearBranch×hourBranch 全枚举(12×12) → 命中全部克亲组合分支")
    void judgeKeQinExhaustive() throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod("judgeKeQin", int.class, int.class);
        m.setAccessible(true);
        int nonEmpty = 0;
        for (int y = 0; y < 12; y++) {
            for (int h = 0; h < 12; h++) {
                String r = (String) m.invoke(null, y, h);
                assertThat(r).isNotNull();
                if (!r.contains("未见")) {
                    nonEmpty++;
                }
            }
        }
        assertThat(nonEmpty).isGreaterThan(0);
    }
}
