package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * B 类补齐·③ 整盘/格局/经典表判定残余分支批次（离线反射）。
 */
@DisplayName("B类补齐·格局与经典表判定残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest8 {

    private static final String[] MAIN = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府",
            "太阴", "贪狼", "巨门", "天相", "天梁", "七杀", "破军"};
    private static final String SKEL = "命宫=子;身宫=午;兄弟=丑;夫妻=寅;子女=卯;财帛=辰;疾厄=巳;迁移=午;奴仆=未;官禄=申;田宅=酉;福德=戌;父母=亥";

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Integer, List<String>> at() {
        return new LinkedHashMap<>();
    }

    private static void put(Map<Integer, List<String>> m, int p, String... stars) {
        m.put(p, new ArrayList<>(Arrays.asList(stars)));
    }

    @Test
    @DisplayName("judgeMingGongGe：全支×全干×多主星（3 参）+ 空/越界 + 4 参煞凑")
    void judgeMingGongGeMatrix() {
        for (String star : MAIN) {
            for (int zhi = 0; zhi < 12; zhi++) {
                for (int gan = 0; gan < 10; gan++) {
                    final int z = zhi, g = gan;
                    assertThatCode(() -> invoke("judgeMingGongGe",
                            new Class<?>[]{String.class, int.class, int.class}, star, z, g))
                            .doesNotThrowAnyException();
                }
            }
        }
        assertThatCode(() -> invoke("judgeMingGongGe", new Class<?>[]{String.class, int.class, int.class},
                (String) null, 0, 0)).doesNotThrowAnyException();
        assertThatCode(() -> invoke("judgeMingGongGe", new Class<?>[]{String.class, int.class, int.class},
                "紫微", 11, 9)).doesNotThrowAnyException();

        Map<Integer, List<String>> sha = at();
        put(sha, 0, "紫微", "擎羊", "陀罗");
        put(sha, 4, "火星", "铃星");
        for (String star : MAIN) {
            for (int zhi = 0; zhi < 12; zhi++) {
                final int z = zhi;
                assertThatCode(() -> invoke("judgeMingGongGe",
                        new Class<?>[]{String.class, int.class, int.class, Map.class}, star, z, 0, sha))
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("judgeTongYuan：全命宫×全生干×多种盘（经重载驱动 tongYuanObjective*）")
    void judgeTongYuanMatrix() {
        Map<Integer, List<String>> rich = at();
        put(rich, 0, "紫微");
        put(rich, 4, "贪狼");
        put(rich, 6, "巨门");
        put(rich, 8, "太阴");
        for (int ming = 0; ming < 12; ming++) {
            for (int gan = 0; gan < 10; gan++) {
                final int m = ming, g = gan;
                assertThatCode(() -> invoke("judgeTongYuan",
                        new Class<?>[]{int.class, Map.class, int.class, String.class}, m, rich, g, SKEL))
                        .doesNotThrowAnyException();
                assertThatCode(() -> invoke("judgeTongYuan",
                        new Class<?>[]{int.class, Map.class, int.class, String.class}, m, at(), g, ""))
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("judgeTanXingYaoLun：命未定/全命宫×多种盘")
    void judgeTanXingYaoLunMatrix() {
        assertThat((String) invoke("judgeTanXingYaoLun", new Class<?>[]{int.class, Map.class, String.class}, -1, at(), SKEL))
                .contains("不判");
        Map<Integer, List<String>> rich = at();
        put(rich, 0, "紫微", "左辅", "文昌");
        put(rich, 3, "禄存", "化禄");
        put(rich, 6, "擎羊", "陀罗");
        for (int ming = 0; ming < 12; ming++) {
            final int m = ming;
            assertThatCode(() -> invoke("judgeTanXingYaoLun",
                    new Class<?>[]{int.class, Map.class, String.class}, m, rich, SKEL)).doesNotThrowAnyException();
            assertThatCode(() -> invoke("judgeTanXingYaoLun",
                    new Class<?>[]{int.class, Map.class, String.class}, m, at(), SKEL)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("judgeTaiweiMingGeExtended：全命宫×主星集合/空集合")
    @SuppressWarnings("unchecked")
    void judgeTaiweiMingGeExtendedMatrix() {
        Map<Integer, List<String>> rich = at();
        put(rich, 0, "紫微", "天府");
        put(rich, 4, "天魁", "天钺");
        for (int ming = 0; ming < 12; ming++) {
            final int m = ming;
            Set<String> mingMain = new LinkedHashSet<>(List.of("紫微"));
            Set<String> mingSet = new LinkedHashSet<>(List.of("紫微", "天府", "天魁", "天钺"));
            assertThatCode(() -> assertThat((List<String>) invoke("judgeTaiweiMingGeExtended",
                    new Class<?>[]{int.class, Map.class, Set.class, Set.class, String.class},
                    m, rich, mingMain, mingSet, SKEL)).isNotNull()).doesNotThrowAnyException();
            assertThatCode(() -> invoke("judgeTaiweiMingGeExtended",
                    new Class<?>[]{int.class, Map.class, Set.class, Set.class, String.class},
                    m, at(), new LinkedHashSet<>(), new LinkedHashSet<>(), "")).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("gradeOf / candidateMingStars / verifyCandidateScore / isAnyStarBlockOpen 边界")
    void miscPatternHelperBranches() {
        Map<Integer, List<String>> at = at();
        put(at, 0, "紫微", "天府");
        put(at, 4, "擎羊");
        assertThat((String) invoke("gradeOf", new Class<?>[]{List.class, Map.class, int.class},
                List.of("贵局·紫府朝垣"), at, 0)).contains("格");
        assertThat((String) invoke("gradeOf", new Class<?>[]{List.class, Map.class, int.class},
                List.of(), at, 0)).isNotNull();

        for (int month = 1; month <= 12; month += 3) {
            for (int hour = 0; hour < 12; hour += 3) {
                final int mo = month, hr = hour;
                assertThatCode(() -> invoke("candidateMingStars",
                        new Class<?>[]{int.class, int.class, int.class, int.class}, mo, 15, hr, 0))
                        .doesNotThrowAnyException();
            }
        }
        StringBuilder detail = new StringBuilder();
        assertThat((Integer) invoke("verifyCandidateScore",
                new Class<?>[]{int.class, int.class, int.class, int.class, Set.class, StringBuilder.class},
                1, 15, 0, 0, new LinkedHashSet<>(List.of("紫微")), detail)).isGreaterThanOrEqualTo(0);

        assertThat((Boolean) invoke("isAnyStarBlockOpen", new Class<?>[]{String.class}, "天机木属星，主智慧")).isTrue();
    }
}
