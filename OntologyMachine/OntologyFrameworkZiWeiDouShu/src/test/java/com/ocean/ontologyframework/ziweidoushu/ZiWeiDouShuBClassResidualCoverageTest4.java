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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * B 类补齐·历法/干支/四化/宫位小工具与断语函数残余分支批次（离线反射）。
 */
@DisplayName("B类补齐·历法与宫位工具残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest4 {

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
    @DisplayName("历法/干支/四化/宫位序小工具全分支")
    void calendarUtils() {
        assertThat((Integer) invoke("sexagenaryIndex", new Class[]{int.class, int.class}, 0, 0)).isEqualTo(0);
        assertThat((Integer) invoke("sexagenaryIndex", new Class[]{int.class, int.class}, 0, 1)).isEqualTo(-1);
        assertThat((Integer) invoke("daXianPalace", new Class[]{int.class, int.class, int.class, boolean.class}, 5, 10, 0, true)).isEqualTo(-1);
        assertThat((Integer) invoke("daXianPalace", new Class[]{int.class, int.class, int.class, boolean.class}, 15, 10, 0, true)).isEqualTo(0);
        assertThat((Integer) invoke("daXianPalace", new Class[]{int.class, int.class, int.class, boolean.class}, 15, 10, 3, false)).isEqualTo(3);
        for (int y = -3; y <= 2030; y += 997) {
            final int yy = y;
            assertThatCode(() -> invoke("ganOfYear", new Class[]{int.class}, yy)).doesNotThrowAnyException();
            assertThatCode(() -> invoke("branchOfYear", new Class[]{int.class}, yy)).doesNotThrowAnyException();
        }
        for (int h = 0; h < 24; h++) {
            final int hh = h;
            assertThatCode(() -> invoke("hourBranchOf", new Class[]{int.class}, hh)).doesNotThrowAnyException();
        }
        for (int g = 0; g < 10; g++) {
            for (int b = 0; b < 12; b++) {
                final int gg = g, bb = b;
                assertThatCode(() -> invoke("palaceStemIndex", new Class[]{int.class, int.class}, bb, gg)).doesNotThrowAnyException();
                assertThatCode(() -> invoke("sexagenaryIndex", new Class[]{int.class, int.class}, gg, bb)).doesNotThrowAnyException();
            }
            final int g2 = g;
            assertThatCode(() -> invoke("sihuaStars", new Class[]{int.class}, g2)).doesNotThrowAnyException();
            assertThatCode(() -> invoke("lucunBranch", new Class[]{int.class}, g2)).doesNotThrowAnyException();
            assertThatCode(() -> invoke("liuyangBranch", new Class[]{int.class}, g2)).doesNotThrowAnyException();
            assertThatCode(() -> invoke("liutuoBranch", new Class[]{int.class}, g2)).doesNotThrowAnyException();
            assertThatCode(() -> invoke("wuhuYinGan", new Class[]{int.class}, g2)).doesNotThrowAnyException();
            assertThatCode(() -> invoke("JIEluKongwang", new Class[]{int.class}, g2)).doesNotThrowAnyException();
            assertThatCode(() -> invoke("deityName", new Class[]{int.class}, g2)).doesNotThrowAnyException();
        }
        assertThatCode(() -> invoke("deityName", new Class[]{int.class}, 10)).doesNotThrowAnyException();
        assertThatCode(() -> invoke("deityName", new Class[]{int.class}, 11)).doesNotThrowAnyException();
        assertThatCode(() -> invoke("xiaoXianPalace", new Class[]{int.class, int.class, boolean.class}, 30, 5, true)).doesNotThrowAnyException();
        assertThatCode(() -> invoke("xiaoXianPalace", new Class[]{int.class, int.class, boolean.class}, 30, 5, false)).doesNotThrowAnyException();
        for (int b = 2; b <= 6; b++) {
            for (int d = 1; d <= 30; d += 7) {
                final int bb = b, dd = d;
                assertThatCode(() -> invoke("ziweiBranch", new Class[]{int.class, int.class}, bb, dd)).doesNotThrowAnyException();
            }
        }
        assertThat((Integer) invoke("trailingInt", new Class[]{String.class}, "abc12")).isEqualTo(12);
        assertThat((Integer) invoke("trailingInt", new Class[]{String.class}, "99")).isEqualTo(99);
        assertThatCode(() -> invoke("trailingInt", new Class[]{String.class}, (String) null)).doesNotThrowAnyException();
        assertThatCode(() -> invoke("diffRows", new Class[]{List.class, String[][].class}, new ArrayList<>(), new String[][]{})).doesNotThrowAnyException();
        assertThatCode(() -> invoke("expandTail", new Class[]{List.class, String.class}, new ArrayList<>(), "、")).doesNotThrowAnyException();
        assertThatCode(() -> invoke("verse", new Class[]{String.class, String.class}, "__NOPE__", "fb")).doesNotThrowAnyException();
        assertThatCode(() -> invoke("appendLine", new Class[]{StringBuilder.class, String.class, Object.class}, new StringBuilder(), "L", 1)).doesNotThrowAnyException();
        assertThatCode(() -> invoke("appendLine", new Class[]{StringBuilder.class, String.class, Object.class}, new StringBuilder(), (String) null, (Object) null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("taisuiXiaoxianGanHit / starBranch / inSanFang / verifyStarTraits / extractStatedTraits / starsSplitClamp / isJiaMingQuote / gradeOf")
    @SuppressWarnings("unchecked")
    void judges() {
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class[]{int.class, int.class}, -1, 0)).isEmpty();
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class[]{int.class, int.class}, 10, 0)).isEmpty();
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class[]{int.class, int.class}, 0, 12)).isEmpty();
        for (int g = 0; g < 10; g++) {
            for (int b = 0; b < 12; b++) {
                final int gg = g, bb = b;
                assertThat((String) invoke("taisuiXiaoxianGanHit", new Class[]{int.class, int.class}, gg, bb)).isNotNull();
            }
        }

        assertThat((Integer) invoke("starBranch", new Class[]{String.class, String.class}, (String) null, "紫微")).isNull();
        assertThat((Integer) invoke("starBranch", new Class[]{String.class, String.class}, "紫微", "紫微")).isNull();
        assertThat((Integer) invoke("starBranch", new Class[]{String.class, String.class}, "紫微@", "紫微")).isNull();
        assertThat((Integer) invoke("starBranch", new Class[]{String.class, String.class}, "紫微@X", "紫微")).isNull();
        assertThat((Integer) invoke("starBranch", new Class[]{String.class, String.class}, "紫微@子", "紫微")).isEqualTo(0);

        assertThat((Boolean) invoke("inSanFang", new Class[]{int.class, String.class}, 0, (String) null)).isFalse();
        assertThat((Boolean) invoke("inSanFang", new Class[]{int.class, String.class}, 0, "X")).isFalse();
        assertThat((Boolean) invoke("inSanFang", new Class[]{int.class, String.class}, 0, "子")).isTrue();
        assertThat((Boolean) invoke("inSanFang", new Class[]{int.class, String.class}, 0, "辰")).isTrue();
        assertThat((Boolean) invoke("inSanFang", new Class[]{int.class, String.class}, 0, "申")).isTrue();
        assertThat((Boolean) invoke("inSanFang", new Class[]{int.class, String.class}, 0, "午")).isTrue();
        assertThat((Boolean) invoke("inSanFang", new Class[]{int.class, String.class}, 0, "丑")).isFalse();

        assertThat((List<String>) invoke("verifyStarTraits", new Class[]{String.class}, "X")).isEmpty();
        assertThat((List<String>) invoke("verifyStarTraits", new Class[]{String.class}, "紫微")).isNotNull();

        assertThat((java.util.Set<String>) invoke("extractStatedTraits", new Class[]{String.class, java.util.Set.class}, (String) null, new java.util.HashSet<>(List.of("甲")))).isEmpty();
        assertThat((java.util.Set<String>) invoke("extractStatedTraits", new Class[]{String.class, java.util.Set.class}, "  ", new java.util.HashSet<>(List.of("甲")))).isEmpty();
        assertThat((java.util.Set<String>) invoke("extractStatedTraits", new Class[]{String.class, java.util.Set.class}, "甲乙丙", new java.util.HashSet<>(List.of("甲", "乙"))))
                .containsExactlyInAnyOrder("甲", "乙");

        Map<Integer, List<String>> c = at();
        put(c, 1, "左辅");
        put(c, 11, "右弼");
        assertThat((Boolean) invoke("starsSplitClamp", new Class[]{Map.class, String.class, int.class}, c, "左辅、右弼", -1)).isFalse();
        assertThat((Boolean) invoke("starsSplitClamp", new Class[]{Map.class, String.class, int.class}, c, "左辅、右弼", 0)).isTrue();
        assertThat((Boolean) invoke("starsSplitClamp", new Class[]{Map.class, String.class, int.class}, c, "左辅", 0)).isFalse();

        assertThat((Boolean) invoke("isJiaMingQuote", new Class[]{String.class, String.class}, (String) null, "紫微")).isFalse();
        assertThat((Boolean) invoke("isJiaMingQuote", new Class[]{String.class, String.class}, "无关", "紫微")).isFalse();
        assertThat((Boolean) invoke("isJiaMingQuote", new Class[]{String.class, String.class}, "夹命", "命宫")).isFalse();
        assertThat((Boolean) invoke("isJiaMingQuote", new Class[]{String.class, String.class}, "夹命", "紫微")).isTrue();
        assertThat((Boolean) invoke("isJiaMingQuote", new Class[]{String.class, String.class}, "夹身", "紫微")).isTrue();

        assertThat((String) invoke("gradeOf", new Class[]{List.class, Map.class, int.class}, new ArrayList<>(), at(), 0)).contains("常格");
        assertThat((String) invoke("gradeOf", new Class[]{List.class, Map.class, int.class}, List.of("贵局"), at(), 0)).contains("上格");
        assertThat((String) invoke("gradeOf", new Class[]{List.class, Map.class, int.class}, List.of("富局"), at(), 0)).contains("富局");
        assertThat((String) invoke("gradeOf", new Class[]{List.class, Map.class, int.class}, List.of("贫贱局"), at(), 0)).contains("贫贱局");
        assertThat((String) invoke("gradeOf", new Class[]{List.class, Map.class, int.class}, List.of("杂X"), at(), 0)).contains("杂局");
    }
}
