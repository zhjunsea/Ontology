package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code judgeTongYuan}（卷三·诸星同垣各司所宜 c4 L178-477）**数据驱动分支的等价可达补测**。
 *
 * <p>背景：258 行权威语料 {@code tongyuan_rule}（java 内置 {@code STAR_TONGYUAN} 同源）中，</p>
 * <ul>
 *   <li>主语集合仅含「命宫/身宫/纳音/财帛/各星名」，<b>不含</b> {@code 财宅}/{@code 财福}——该二者仅作为
 *       经典「宫位并称」出现在断语文字中（TY_0255「财宅横发」、TY_0257「守财福之位」）；</li>
 *   <li>{@code (三方)} 前缀（并见星须落三方四正）代码已实现，但现行语料 0 行标注；</li>
 *   <li>「夹身」引文仅 TY_0243（主语=身宫，无附加条件），无「星曜作主语」的夹身行。</li>
 * </ul>
 *
 * <p>故这些分支并非「坏味道」，而是 {@code judgeTongYuan} 对**经典确实存在的表达**
 * （宫位并称 / 三方会照并见 / 星曜夹身格）的正确支持，只是这 258 行语料 + 本盘数据未触发。
 * 本类沿用既有范式（{@code ZiWeiDouShuBClassResidualCoverageTest13}）注入 {@code ONTO_TONGYUAN}
 * 合成行，逐一驱动这些分支，证明其行为正确、可达。纯离线（反射注入静态缓存），不依赖 Camunda/DB/Ontop。</p>
 */
@DisplayName("judgeTongYuan 数据驱动分支注入补测（财宅/财福/三方/夹身/待考分类）")
class ZiWeiDouShuJudgeTongYuanInjectionTest {

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setStatic(String field, Object value) {
        try {
            Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void reset() {
        setStatic("ONTO_TONGYUAN", null);
    }

    private static final Class<?>[] SIX = {int.class, Map.class, int.class, String.class, Boolean.class, String.class};

    /** 身宫=丑(1)：供「星曜夹身」用例令 target=1，紫微@子(0)/天机@寅(2) 恰分处左右两邻。 */
    private static final String SK = "命宫=子;身宫=丑;兄弟=寅;夫妻=卯;子女=辰;财帛=巳;疾厄=午;迁移=未;"
            + "奴仆=申;官禄=酉;田宅=戌;福德=亥;水二局";

    /** 盘面：0宫[紫微,破军]、2宫[天机]、4宫[贪狼,擎羊]（贪狼落命宫三方四正，擎羊充煞）。 */
    private static Map<Integer, List<String>> at() {
        Map<Integer, List<String>> m = new LinkedHashMap<>();
        m.put(0, Arrays.asList("紫微", "破军"));
        m.put(2, Arrays.asList("天机"));
        m.put(4, Arrays.asList("贪狼", "擎羊"));
        return m;
    }

    private static String run(int mingIdx, int yearGan, String skel, Map<Integer, List<String>> at) {
        return (String) invoke("judgeTongYuan", SIX, mingIdx, at, yearGan, skel, Boolean.TRUE, "");
    }

    @Test
    @DisplayName("财宅/财福并称、三方并见正负、夹身真/假、生干三态、待考按因分类、三段命中")
    void branchMatrix() {
        List<String[]> rows = new ArrayList<>();
        // —— 宫位并称主语（经典「财宅」=财帛宫、「财福」=福德位）：覆盖 star.equals 别名/专支 ——
        rows.add(new String[]{"财宅", "-", "-", "-", "财宅横发资财（经典并称）"});
        rows.add(new String[]{"财福", "-", "-", "-", "权禄守财福之位（经典并称）"});
        // —— (三方) 并见：正例（贪狼@4 在命三方四正）+ 负例（天机@2 不在）——
        rows.add(new String[]{"命宫", "-", "(三方)贪狼", "-", "三方会贪狼"});
        rows.add(new String[]{"命宫", "-", "(三方)天机", "-", "三方会天机（不在三方）"});
        // —— 同宫并见命中 / 同宫不符 / 并见星不在盘（sb==null）——
        rows.add(new String[]{"命宫", "-", "紫微", "-", "同宫见紫微"});
        rows.add(new String[]{"命宫", "-", "天相", "-", "同宫见天相（不在命宫）"});
        // —— 星曜夹身格：真（紫微@0、天机@2 夹身宫@1）+ 假（破军孤星不能成夹）——
        //    以生年干「甲」作已定条件（不依赖支），避免 splitStarSet 为 HashSet 无序致 branch 抖动
        rows.add(new String[]{"紫微、天机", "-", "-", "甲", "紫府夹身贵格"});
        rows.add(new String[]{"破军", "-", "-", "甲", "破军夹身败局"});
        // —— 生年干条件：命中（甲=0）/ 不中 ——
        rows.add(new String[]{"命宫", "-", "-", "甲", "甲年生人断语"});
        rows.add(new String[]{"命宫", "-", "-", "乙", "乙年生人断语"});
        // —— 自含条件客观（命无正曜→objJudged）/ OR 复核（加煞→orJudged）——
        rows.add(new String[]{"命宫", "-", "-", "-", "命无正曜"});
        rows.add(new String[]{"命宫", "-", "-", "-", "加煞冲破"});
        // —— 待考按因四类：败地→cn；运限→cy；夹→cc；性质→cx ——
        rows.add(new String[]{"命宫", "-", "-", "-", "败地无依"});
        rows.add(new String[]{"命宫", "-", "-", "-", "运限不利"});
        rows.add(new String[]{"命宫", "-", "-", "-", "泛论夹拱"});
        rows.add(new String[]{"命宫", "-", "-", "-", "性情温良"});
        setStatic("ONTO_TONGYUAN", rows);

        // A：生年干已定（甲=0），命宫=子
        String a = run(0, 0, SK, at());
        assertThat(a).contains("同垣").contains("自含条件客观").contains("OR条件客观复核").contains("待考·按因");
        // B：生年干未定（-1）→ 生干条件行走 pend（yearGan<0 分支）
        String b = run(0, -1, SK, at());
        assertThat(b).contains("同垣").contains("生年干未定·待考");
    }

    @Test
    @DisplayName("夹身真分支：starsSplitClamp 命中则计入 hitted")
    void jiaShenHit() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"紫微、天机", "-", "-", "甲", "紫府夹身贵格"});
        setStatic("ONTO_TONGYUAN", rows);
        String r = run(0, 0, SK, at());
        assertThat(r).contains("本盘命中 1 条");
    }

    @Test
    @DisplayName("branch<0 边界：身宫未知 skeleton 缺宫 → 支条件否决")
    void branchNegative() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"身宫", "子", "-", "-", "身宫断语"});
        setStatic("ONTO_TONGYUAN", rows);
        String skNoShen = "命宫=子;财帛=巳;福德=亥;水二局"; // 无「身宫=」→ palaceBranch 返回 -1
        String r = run(0, 0, skNoShen, at());
        assertThat(r).contains("同垣").contains("（无客观命中）");
    }

    @Test
    @DisplayName("hitted 空侧 + 三段空侧：全部条件不中、无待考")
    void emptySides() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"命宫", "丑", "-", "-", "命宫丑断语"}); // 命在子，支不符→condOk=false，且 hasCond=true 不进待考
        setStatic("ONTO_TONGYUAN", rows);
        String r = run(0, 0, SK, at());
        assertThat(r).contains("同垣").contains("（无客观命中）");
        assertThat(r).doesNotContain("自含条件客观").doesNotContain("OR条件客观复核").doesNotContain("待考·按因");
    }

    @Test
    @DisplayName("三段空侧：仅命中、无自含/OR/待考")
    void onlyHitSides() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"命宫", "子", "-", "甲", "甲年命宫断语"}); // 命中，obj/or/pend 均空
        setStatic("ONTO_TONGYUAN", rows);
        String r = run(0, 0, SK, at());
        assertThat(r).contains("本盘命中 1 条");
        assertThat(r).doesNotContain("自含条件客观").doesNotContain("OR条件客观复核").doesNotContain("待考·按因");
    }

    @Test
    @DisplayName("待考按因四类 OR 全分支：纳音/败地/绝处/运限/夹/性质")
    void pendCauseCategories() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"命宫", "-", "-", "-", "纳音生旺"});
        rows.add(new String[]{"命宫", "-", "-", "-", "败地无依"});
        rows.add(new String[]{"命宫", "-", "-", "-", "绝处逢生"});
        rows.add(new String[]{"命宫", "-", "-", "-", "运限不利"});
        rows.add(new String[]{"命宫", "-", "-", "-", "泛论夹拱"});
        rows.add(new String[]{"命宫", "-", "-", "-", "性情温良"});
        setStatic("ONTO_TONGYUAN", rows);
        String r = run(0, 0, SK, at());
        assertThat(r).contains("待考·按因");
    }
}
