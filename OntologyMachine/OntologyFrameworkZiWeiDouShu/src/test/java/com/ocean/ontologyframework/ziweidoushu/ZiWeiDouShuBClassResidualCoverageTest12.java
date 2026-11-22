package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * B 类补齐·⑫ 反驳「死支/布尔互补不可测/数据不可达」——用可构造输入与静态表注入覆盖业务残余分支。
 *
 * <p>本批专挑此前被笼统归为「死支/数据不可达」的业务分支，佐证其**实为可测**：
 * 短路的布尔互补（多输入把 A||B 每个操作数都评估到）、数据形态分支（ONTO_PALACE_* 注入 4 列/首星非命中行）、
 * 输入分支（聚吉/聚凶、僧道/破相、太岁临命）。注：judgeLongevity「破武未宫」原支与安星相悖（武曲破军只同宫于巳/亥），已订正为「破武巳亥」并收窄首条加杀条件使之可达。
 */
@DisplayName("B类补齐·反驳死支/布尔互补不可测/数据不可达")
class ZiWeiDouShuBClassResidualCoverageTest12 {

    private static final String SKEL = "命宫=子;身宫=午;兄弟=丑;夫妻=寅;子女=卯;财帛=辰;疾厄=巳;迁移=午;奴仆=未;官禄=申;田宅=酉;福德=戌;父母=亥";

    private static final String[] ONTO_FIELDS = {
            "ONTO_PALACE_ZHI_GE", "ONTO_PALACE_FUGUI"};

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
    void resetStatics() {
        for (String f : ONTO_FIELDS) {
            setStatic(f, null);
        }
    }

    private static Map<Integer, List<String>> at() {
        return new LinkedHashMap<>();
    }

    private static void put(Map<Integer, List<String>> m, int p, String... stars) {
        m.computeIfAbsent(p, k -> new ArrayList<>()).addAll(Arrays.asList(stars));
    }

    private static Set<String> set(String... s) {
        return new LinkedHashSet<>(Arrays.asList(s));
    }

    // ============ judgeTanXingYaoLun：命宫未定 + 聚吉(6090) + 聚凶(6092) ============
    @Test
    @DisplayName("judgeTanXingYaoLun：命宫未定 / 六宫聚吉多 / 六宫俱陷聚凶")
    @SuppressWarnings("unchecked")
    void tanXingYaoLunCases() {
        String undef = (String) invoke("judgeTanXingYaoLun",
                new Class<?>[]{int.class, Map.class, String.class}, -1, at(), SKEL);
        assertThat(undef).contains("命宫未定");

        Map<Integer, List<String>> jiRich = at();
        put(jiRich, 0, "禄存");
        put(jiRich, 6, "禄存");
        put(jiRich, 4, "禄存");
        put(jiRich, 8, "禄存");
        String ji = (String) invoke("judgeTanXingYaoLun",
                new Class<?>[]{int.class, Map.class, String.class}, 0, jiRich, SKEL);
        assertThat(ji).contains("聚吉多");

        Map<Integer, List<String>> shaRich = at();
        put(shaRich, 0, "擎羊");
        put(shaRich, 6, "擎羊");
        put(shaRich, 4, "擎羊");
        put(shaRich, 8, "擎羊");
        String sha = (String) invoke("judgeTanXingYaoLun",
                new Class<?>[]{int.class, Map.class, String.class}, 0, shaRich, SKEL);
        assertThat(sha).contains("俱陷聚凶");
    }

    // ============ judgeLiuNianTaiSui：太岁正临命宫（6023 true 分支） ============
    @Test
    @DisplayName("judgeLiuNianTaiSui：太岁正临命宫（onMing=true）")
    void liuNianTaiSuiOnMing() {
        String r = (String) invoke("judgeLiuNianTaiSui",
                new Class<?>[]{int.class, int.class, Map.class, String.class}, 0, 0, at(), SKEL);
        assertThat(r).contains("正临命宫");
    }

    // ============ judgeLongevity：6308 僧道六星俱全 + 6332 疾厄擎羊 ============
    @Test
    @DisplayName("judgeLongevity：僧道六星俱全（6308 true）/ 破相·疾厄擎羊（6332 true）")
    @SuppressWarnings("unchecked")
    void longevityExtra() {
        Map<Integer, List<String>> seng = at();
        put(seng, 0, "天机", "七杀", "破军", "天梁", "太阳", "紫微");
        List<String> g = (List<String>) invoke("judgeLongevity",
                new Class<?>[]{int.class, int.class, Map.class}, 0, -1, seng);
        assertThat(g).anyMatch(s -> s.contains("僧道"));

        Map<Integer, List<String>> poXiang = at();
        put(poXiang, 0, "擎羊");
        List<String> g2 = (List<String>) invoke("judgeLongevity",
                new Class<?>[]{int.class, int.class, Map.class}, 5, -1, poXiang);
        assertThat(g2).anyMatch(s -> s.contains("破相"));
    }

    // ============ judgeTenGrades：6180 紫微||天府 全操作数 + 6202 左辅||右弼||文昌||文曲 全操作数 ============
    @Test
    @DisplayName("judgeTenGrades：布尔互补全评估（紫微/天府、左辅/右弼/文昌/文曲）")
    @SuppressWarnings("unchecked")
    void tenGradesBooleanComplement() {
        invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                set("武曲", "七杀"), set("武曲", "七杀"), set("武曲", "七杀", "紫微"));
        invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                set("武曲", "七杀"), set("武曲", "七杀"), set("武曲", "七杀", "天府"));
        invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                set("武曲", "七杀"), set("武曲", "七杀"), set("武曲", "七杀"));

        List<String> a = (List<String>) invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                set(), set(), set("紫微", "天府", "禄存", "左辅"));
        List<String> b = (List<String>) invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                set(), set(), set("紫微", "天府", "禄存", "右弼"));
        List<String> c = (List<String>) invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                set(), set(), set("紫微", "天府", "禄存", "文曲"));
        List<String> d = (List<String>) invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                set(), set(), set("紫微", "天府", "禄存"));
        assertThat(a).anyMatch(s -> s.contains("富贵"));
        assertThat(b).anyMatch(s -> s.contains("富贵"));
        assertThat(c).anyMatch(s -> s.contains("富贵"));
        assertThat(d).noneMatch(s -> s.contains("富贵"));
    }

    // ============ judgePalaceZhiGrid：6486 四列行 + 6529 首星非命中 ============
    @Test
    @DisplayName("judgePalaceZhiGrid：4 列得地行（6486）/ 得地富贵首星非命中（6529）")
    void palaceZhiGridDataShape() {
        List<String[]> ge = new ArrayList<>();
        ge.add(new String[]{"子", "", "", "占位原文"});
        setStatic("ONTO_PALACE_ZHI_GE", ge);
        assertThatCode(() -> invoke("judgePalaceZhiGrid",
                new Class<?>[]{int.class, Map.class, int.class}, 0, at(), 0)).doesNotThrowAnyException();

        setStatic("ONTO_PALACE_ZHI_GE", null);
        List<String[]> fg = new ArrayList<>();
        fg.add(new String[]{"子", "贪狼、太阴", "子宫得地太阴星"});
        setStatic("ONTO_PALACE_FUGUI", fg);
        Map<Integer, List<String>> m = at();
        put(m, 0, "太阴");
        String r = (String) invoke("judgePalaceZhiGrid",
                new Class<?>[]{int.class, Map.class, int.class}, 0, m, 0);
        assertThat(r).contains("非「贪狼得地」");
    }
}
