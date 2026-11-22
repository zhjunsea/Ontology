package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四柱命理 · 分支矩阵补充（第十三阶段）· 岁运辩证 / 分类事项断（纯离线）。
 *
 * <p>本类补齐此前未被单测直击的两条输出链路：
 * <ul>
 *   <li>{@code buildFortuneSynthesis}（岁运辩证·凭本命用神，静态纯函数）：标题头、逐运 good&gt;=1/&lt;=-1/==0 三结论、
 *       火炎土燥 fireExcess 分支、运支冲提纲覆盖注记；</li>
 *   <li>{@code buildCategoryVerdicts}（分类事项断，实例方法）：①事业功名（官透身非强·官透身强·官藏·无官）、
 *       ②财运（财透·财藏·无财）、③婚姻（日支逢冲）、④寿夭（羊刃）、⑤飘荡（驿马）；离线时
 *       {@code verdictOr} 回退内联 fallback，断言其关键判据文本。</li>
 *   <li>{@code verdictOr}：离线无名录命中时回退 fallback，并经 {@code stripCitations} 剥离《》书名。</li>
 * </ul>
 *
 * <p>断言读方法返回的结构化文本片段，不反向解析判词、不臆造判据。
 */
@DisplayName("四柱命理 · 分支矩阵补充（第十三阶段·岁运辩证/分类事项断）")
class SiZhuBaZiBranchMatrix13Test {

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

    private static String fsynth(String fp, double startAge, String[] fortunes,
                                 int yongElem, int jiElem, int dayElem, String patternGrade, boolean strong) throws Exception {
        Method m = WORKER.getDeclaredMethod("buildFortuneSynthesis",
                int[][].class, double.class, String[].class,
                int.class, int.class, int.class, String.class, boolean.class);
        m.setAccessible(true);
        return (String) m.invoke(null, P(fp), startAge, fortunes,
                yongElem, jiElem, dayElem, patternGrade, strong);
    }

    private static String category(String fp, int dayGan, int dayElem, int monthBranch,
                                   Set<String> gods, String deities, String gender, boolean strong) throws Exception {
        Method m = WORKER.getDeclaredMethod("buildCategoryVerdicts",
                int[][].class, int.class, int.class, int.class,
                Set.class, String.class, String.class, boolean.class);
        m.setAccessible(true);
        return (String) m.invoke(newWorker(), P(fp), dayGan, dayElem, monthBranch, gods, deities, gender, strong);
    }

    private static String verdictOr(String cat, String key, String fallback) throws Exception {
        Method m = WORKER.getDeclaredMethod("verdictOr", String.class, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(newWorker(), cat, key, fallback);
    }

    // ==================================================== buildFortuneSynthesis

    @Test
    @DisplayName("岁运辩证·标题头：含「岁运辩证·凭本命用神」与身强弱")
    void fsHeader() throws Exception {
        String s = fsynth("甲子 丙寅 甲子 丁卯", 3.0, new String[]{}, 2, 0, 0, "格成", true);
        assertThat(s).contains("【岁运辩证·凭本命用神】用神").contains("身强");
    }

    @Test
    @DisplayName("岁运辩证·身强食伤运（good>=1）→ 扶助得力，格愈成、事顺")
    void fsGoodPositive() throws Exception {
        String s = fsynth("甲子 丙寅 甲子 丁卯", 3.0, new String[]{"丙午"}, 2, 0, 0, "格成", true);
        assertThat(s).contains("扶助得力，格愈成、事顺");
    }

    @Test
    @DisplayName("岁运辩证·身强比劫运（good<=-1）→ 助忌损用，格受摇、宜谨")
    void fsGoodNegative() throws Exception {
        String s = fsynth("甲子 丙寅 甲子 丁卯", 3.0, new String[]{"甲寅"}, 2, 0, 0, "格成", true);
        assertThat(s).contains("助忌损用，格受摇、宜谨");
    }

    @Test
    @DisplayName("岁运辩证·身强官杀运（good==0）→ 得失相参，平中带变")
    void fsGoodZero() throws Exception {
        String s = fsynth("甲子 丙寅 甲子 丁卯", 3.0, new String[]{"辛酉"}, 2, 0, 0, "格成", true);
        assertThat(s).contains("得失相参，平中带变");
    }

    @Test
    @DisplayName("岁运辩证·运支冲提纲 → 覆盖注记「运支冲提纲，格局动摇，宜谨（…）」")
    void fsChongGang() throws Exception {
        String s = fsynth("甲子 丙子 甲子 丁卯", 3.0, new String[]{"丙午"}, 2, 0, 0, "格成", true);
        assertThat(s).contains("运支冲提纲，格局动摇，宜谨（扶助得力");
    }

    @Test
    @DisplayName("岁运辩证·火炎土燥局 → 逐运取 fireExcessFortuneSegment（癸亥运判「吉」）")
    void fsFireExcess() throws Exception {
        String s = fsynth("丙寅 庚寅 丙子 戊子", 3.0, new String[]{"癸亥"}, 4, 0, 1, "格成", true);
        assertThat(s).contains("→吉。");
    }

    // ==================================================== buildCategoryVerdicts · 事业功名

    @Test
    @DisplayName("分类断·事业·官杀透干且身非强 → 官杀克身为压力、宜印化")
    void bcGuanTouWeak() throws Exception {
        String s = category("庚申 丙寅 甲子 丁卯", 0, 0, 2, Set.of("偏官"), "", "男", false);
        assertThat(s).contains("官杀克身为压力而非纯为用——宜以印化杀生身");
    }

    @Test
    @DisplayName("分类断·事业·官杀透干且身强 → 官杀为功名之神、有功名之机")
    void bcGuanTouStrong() throws Exception {
        String s = category("庚申 丙寅 甲子 丁卯", 0, 0, 2, Set.of("偏官"), "", "男", true);
        assertThat(s).contains("官杀为功名之神");
    }

    @Test
    @DisplayName("分类断·事业·官杀藏支不透 → 本命官杀藏而不透、须岁运引透")
    void bcGuanCang() throws Exception {
        String s = category("甲子 丙寅 甲申 丁卯", 0, 0, 2, Set.of("偏官"), "", "男", false);
        assertThat(s).contains("本命官杀藏而不透");
    }

    @Test
    @DisplayName("分类断·事业·无官杀 → 本命官杀不显、功名宜循他途")
    void bcNoGuan() throws Exception {
        String s = category("甲子 丙寅 甲子 丁卯", 0, 0, 2, Set.of(), "", "男", false);
        assertThat(s).contains("本命官杀不显，功名宜循他途");
    }

    // ==================================================== buildCategoryVerdicts · 财运

    @Test
    @DisplayName("分类断·财运·财星透干 → 本命财星透干、财为养命之源")
    void bcCaiTou() throws Exception {
        String s = category("戊辰 丙寅 甲子 丁卯", 0, 0, 2, Set.of("偏财"), "", "男", true);
        assertThat(s).contains("本命财星透干");
    }

    @Test
    @DisplayName("分类断·财运·财星藏支 → 本命财星藏支（须岁运引透）")
    void bcCaiCang() throws Exception {
        String s = category("甲子 丙寅 甲子 丁卯", 0, 0, 2, Set.of("偏财"), "", "男", true);
        assertThat(s).contains("本命财星藏支");
    }

    @Test
    @DisplayName("分类断·财运·无财星 → 本命财星不显、须靠食伤生财")
    void bcNoCai() throws Exception {
        String s = category("甲子 丙子 甲子 丁卯", 0, 0, 0, Set.of(), "", "男", true);
        assertThat(s).contains("本命财星不显，须靠食伤生财");
    }

    // ==================================================== buildCategoryVerdicts · 婚姻/寿夭/飘荡

    @Test
    @DisplayName("分类断·婚姻·日支逢冲 → 本命日支逢冲、婚宫受动")
    void bcMarriageChong() throws Exception {
        String s = category("甲午 丙寅 甲子 丁卯", 0, 0, 2, Set.of("偏财"), "", "男", true);
        assertThat(s).contains("本命日支逢冲");
    }

    @Test
    @DisplayName("分类断·寿夭·见羊刃 → 本命见羊刃、性刚易折")
    void bcShouYangren() throws Exception {
        String s = category("甲子 丙寅 甲子 丁卯", 0, 0, 2, Set.of(), "羊刃", "男", true);
        assertThat(s).contains("本命见羊刃，性刚易折");
    }

    @Test
    @DisplayName("分类断·飘荡·见驿马 → 本命见驿马、主动荡迁远")
    void bcPiaoYiMa() throws Exception {
        String s = category("甲子 丙寅 甲子 丁卯", 0, 0, 2, Set.of(), "驿马", "男", true);
        assertThat(s).contains("本命见驿马，主动荡迁远");
    }

    // ==================================================== verdictOr

    @Test
    @DisplayName("verdictOr·离线无命中 → 回退 fallback 且剥离《》典籍书名")
    void verdictOrFallback() throws Exception {
        String s = verdictOr("事业", "never_hit_key", "见《滴天髓》身强官弱宜印");
        assertThat(s).isEqualTo("见身强官弱宜印");
    }
}
