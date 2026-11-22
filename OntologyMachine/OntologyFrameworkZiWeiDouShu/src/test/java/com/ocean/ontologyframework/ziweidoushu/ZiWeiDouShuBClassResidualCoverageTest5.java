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
 * B 类补齐·三方四正/运限/合夹/临宫判断残余分支批次（离线反射）。
 */
@DisplayName("B类补齐·三方运限与合夹判断残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest5 {

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
    @DisplayName("isHuaInSanFang / 三方计数 / yunxianFortuneSign / annualFortuneSign / fortuneGlyph / jiaPair / isKongJie / hasSha")
    void sanFangAndFortune() {
        Map<Integer, List<String>> a = at();
        put(a, 0, "紫微", "左辅");
        put(a, 4, "文昌");
        assertThat((Boolean) invoke("isHuaInSanFang", new Class[]{int.class, Map.class, String.class}, 0, a, (String) null)).isFalse();
        assertThat((Boolean) invoke("isHuaInSanFang", new Class[]{int.class, Map.class, String.class}, 0, a, "紫微化禄、紫微化权、紫微化科")).isTrue();
        assertThat((Boolean) invoke("isHuaInSanFang", new Class[]{int.class, Map.class, String.class}, 0, a, "紫微化禄")).isFalse();

        assertThat((Integer) invoke("countInSanFang", new Class[]{Map.class, int.class, Set.class}, a, 0, new LinkedHashSet<>(List.of("紫微", "文昌")))).isEqualTo(2);
        assertThat((Integer) invoke("countInSanFang", new Class[]{Map.class, int.class, Set.class}, a, 0, new LinkedHashSet<>(List.of("无此星")))).isEqualTo(0);
        assertThat((Boolean) invoke("hasJiInSanFang", new Class[]{Map.class, int.class}, a, 0)).isIn(true, false);
        assertThat((Integer) invoke("shaCountInSanFang", new Class[]{Map.class, int.class}, a, 0)).isGreaterThanOrEqualTo(0);
        assertThat((Boolean) invoke("palaceNetJi", new Class[]{Map.class, int.class}, a, 0)).isIn(true, false);
        assertThat((Boolean) invoke("palaceNetJi", new Class[]{Map.class, int.class}, a, -1)).isFalse();

        assertThat((Integer) invoke("yunxianFortuneSign", new Class[]{Map.class, int.class, String.class}, a, -1, (String) null)).isEqualTo(0);
        assertThat((Integer) invoke("yunxianFortuneSign", new Class[]{Map.class, int.class, String.class}, a, 12, (String) null)).isEqualTo(0);
        assertThat((Integer) invoke("yunxianFortuneSign", new Class[]{Map.class, int.class, String.class}, a, 0, (String) null)).isBetween(-1, 1);
        assertThat((Integer) invoke("yunxianFortuneSign", new Class[]{Map.class, int.class, String.class}, a, 0, "紫微化禄、紫微化忌")).isBetween(-1, 1);

        assertThat((Integer) invoke("annualFortuneSign", new Class[]{Map.class, int.class, int.class}, a, -1, 0)).isEqualTo(0);
        assertThat((Integer) invoke("annualFortuneSign", new Class[]{Map.class, int.class, int.class}, a, 12, 0)).isEqualTo(0);
        assertThat((Integer) invoke("annualFortuneSign", new Class[]{Map.class, int.class, int.class}, a, 0, -1)).isEqualTo(0);
        assertThat((Integer) invoke("annualFortuneSign", new Class[]{Map.class, int.class, int.class}, a, 0, 10)).isEqualTo(0);
        for (int b = 0; b < 12; b++) {
            for (int g = 0; g < 10; g++) {
                final int bb = b, gg = g;
                assertThatCode(() -> invoke("annualFortuneSign", new Class[]{Map.class, int.class, int.class}, a, bb, gg)).doesNotThrowAnyException();
            }
        }

        assertThat((String) invoke("fortuneGlyph", new Class[]{int.class}, 1)).isEqualTo("吉");
        assertThat((String) invoke("fortuneGlyph", new Class[]{int.class}, -1)).isEqualTo("凶");
        assertThat((String) invoke("fortuneGlyph", new Class[]{int.class}, 0)).isEqualTo("平");
        assertThat((String) invoke("fortuneWord", new Class[]{int.class}, 1)).isEqualTo("全吉");
        assertThat((String) invoke("fortuneWord", new Class[]{int.class}, -1)).isEqualTo("全凶");
        assertThat((String) invoke("fortuneWord", new Class[]{int.class}, 0)).isEqualTo("全平");

        assertThat((Boolean) invoke("jiaPair", new Class[]{List.class, List.class, String.class, String.class}, List.of("擎羊"), List.of("陀罗"), "擎羊", "陀罗")).isTrue();
        assertThat((Boolean) invoke("jiaPair", new Class[]{List.class, List.class, String.class, String.class}, List.of("陀罗"), List.of("擎羊"), "擎羊", "陀罗")).isTrue();
        assertThat((Boolean) invoke("jiaPair", new Class[]{List.class, List.class, String.class, String.class}, List.of("擎羊"), List.of("擎羊"), "擎羊", "陀罗")).isFalse();

        assertThat((Boolean) invoke("isKongJie", new Class[]{Set.class}, new LinkedHashSet<>(List.of("地劫")))).isTrue();
        assertThat((Boolean) invoke("isKongJie", new Class[]{Set.class}, new LinkedHashSet<>(List.of("天空")))).isTrue();
        assertThat((Boolean) invoke("isKongJie", new Class[]{Set.class}, new LinkedHashSet<>(List.of("紫微")))).isFalse();

        assertThat((Boolean) invoke("hasSha", new Class[]{List.class}, List.of("擎羊"))).isTrue();
        assertThat((Boolean) invoke("hasSha", new Class[]{List.class}, List.of("紫微"))).isFalse();
    }

    @Test
    @DisplayName("judgeYangTuoDieBing / judgeQiShaReFeng / judgeLiuNianTaiSui / palaceNameOf / judgeDaXianShiNian / judgeTenGrades")
    @SuppressWarnings("unchecked")
    void judgeBranches() {
        assertThat((Boolean) invoke("judgeYangTuoDieBing", new Class[]{int.class, Map.class, int.class}, -1, at(), 0)).isFalse();
        assertThat((Boolean) invoke("judgeYangTuoDieBing", new Class[]{int.class, Map.class, int.class}, 0, at(), 0)).isFalse();
        Map<Integer, List<String>> yt = at();
        put(yt, 0, "擎羊");
        for (int g = 0; g < 10; g++) {
            final int gg = g;
            assertThatCode(() -> invoke("judgeYangTuoDieBing", new Class[]{int.class, Map.class, int.class}, 0, yt, gg)).doesNotThrowAnyException();
        }
        Map<Integer, List<String>> tuo = at();
        put(tuo, 6, "陀罗");
        assertThatCode(() -> invoke("judgeYangTuoDieBing", new Class[]{int.class, Map.class, int.class}, 0, tuo, 3)).doesNotThrowAnyException();

        assertThat((Boolean) invoke("judgeQiShaReFeng", new Class[]{int.class, Map.class, int.class}, -1, at(), 0)).isFalse();
        assertThat((Boolean) invoke("judgeQiShaReFeng", new Class[]{int.class, Map.class, int.class}, 0, at(), 0)).isFalse();
        Map<Integer, List<String>> qs = at();
        put(qs, 0, "七杀");
        for (int g = 0; g < 10; g++) {
            final int gg = g;
            assertThatCode(() -> invoke("judgeQiShaReFeng", new Class[]{int.class, Map.class, int.class}, 0, qs, gg)).doesNotThrowAnyException();
        }
        Map<Integer, List<String>> qsOut = at();
        put(qsOut, 5, "七杀");
        assertThat((Boolean) invoke("judgeQiShaReFeng", new Class[]{int.class, Map.class, int.class}, 0, qsOut, 0)).isFalse();

        assertThat((String) invoke("judgeLiuNianTaiSui", new Class[]{int.class, int.class, Map.class}, 0, -1, at())).contains("不判");
        assertThat((String) invoke("judgeLiuNianTaiSui", new Class[]{int.class, int.class, Map.class}, 0, 0, at())).contains("论流年太岁");
        assertThat((String) invoke("judgeLiuNianTaiSui", new Class[]{int.class, int.class, Map.class}, 0, 5, at())).contains("论流年太岁");

        assertThat((String) invoke("palaceNameOf", new Class[]{String.class, int.class}, (String) null, 0)).isNull();
        assertThat((String) invoke("palaceNameOf", new Class[]{String.class, int.class}, "x", -1)).isNull();
        assertThatCode(() -> invoke("palaceNameOf", new Class[]{String.class, int.class}, "x", 0)).doesNotThrowAnyException();

        assertThat((String) invoke("judgeDaXianShiNian", new Class[]{int.class, Map.class, int.class}, -1, at(), 0)).contains("暂不判");
        assertThat((String) invoke("judgeDaXianShiNian", new Class[]{int.class, Map.class, int.class}, 0, at(), 0)).contains("十年祸福");
        Map<Integer, List<String>> dx1 = at();
        put(dx1, 0, "擎羊");
        assertThat((String) invoke("judgeDaXianShiNian", new Class[]{int.class, Map.class, int.class}, 0, dx1, 0)).contains("十年祸福");
        Map<Integer, List<String>> dx2 = at();
        put(dx2, 0, "擎羊", "陀罗");
        assertThat((String) invoke("judgeDaXianShiNian", new Class[]{int.class, Map.class, int.class}, 0, dx2, 0)).contains("十年祸福");

        Set<String> empty = new LinkedHashSet<>();
        Set<String> mm = new LinkedHashSet<>(List.of("天同", "天梁"));
        assertThat((List<String>) invoke("judgeTenGrades", new Class[]{Set.class, Set.class, Set.class}, mm, mm, empty)).isNotEmpty();
        assertThat((List<String>) invoke("judgeTenGrades", new Class[]{Set.class, Set.class, Set.class}, empty, empty, empty)).isEmpty();
    }
}
