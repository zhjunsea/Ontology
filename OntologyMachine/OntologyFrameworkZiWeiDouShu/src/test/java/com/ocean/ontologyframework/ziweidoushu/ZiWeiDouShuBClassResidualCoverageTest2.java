package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * B 类补齐·小工具/判断函数残余分支批次（离线反射，纯函数，每例有业务断言）。
 */
@DisplayName("B类补齐·小工具/判断函数残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest2 {

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
    @DisplayName("字符串/整型小工具：containsAny/eq/firstLine/toInt/splitStarSet/ganList*")
    @SuppressWarnings("unchecked")
    void stringIntUtils() {
        assertThat((Boolean) invoke("containsAny", new Class[]{String.class, String[].class}, "abc", new String[]{"b"})).isTrue();
        assertThat((Boolean) invoke("containsAny", new Class[]{String.class, String[].class}, "abc", new String[]{"z"})).isFalse();
        assertThat((Boolean) invoke("containsAny", new Class[]{String.class, String[].class}, (String) null, new String[]{"b"})).isFalse();
        assertThat((Boolean) invoke("eq", new Class[]{String.class, String.class}, (String) null, (String) null)).isFalse();
        assertThat((Boolean) invoke("eq", new Class[]{String.class, String.class}, "a", "a")).isTrue();
        assertThat((Boolean) invoke("eq", new Class[]{String.class, String.class}, "a", "b")).isFalse();
        assertThat((String) invoke("firstLine", new Class[]{String.class}, (String) null)).isEmpty();
        assertThat((String) invoke("firstLine", new Class[]{String.class}, "abc")).isEqualTo("abc");
        assertThat((String) invoke("firstLine", new Class[]{String.class}, "a\nb")).isEqualTo("a");
        assertThat((Integer) invoke("toInt", new Class[]{String.class, int.class}, (String) null, 7)).isEqualTo(7);
        assertThat((Integer) invoke("toInt", new Class[]{String.class, int.class}, " 5 ", 0)).isEqualTo(5);
        assertThat((Integer) invoke("toInt", new Class[]{String.class, int.class}, "x", 9)).isEqualTo(9);
        assertThat((Set<String>) invoke("splitStarSet", new Class[]{String.class}, (String) null)).isEmpty();
        assertThat((Set<String>) invoke("splitStarSet", new Class[]{String.class}, "紫微、天府")).containsExactlyInAnyOrder("紫微", "天府");
        assertThat((Set<String>) invoke("splitStarSet", new Class[]{String.class}, "紫微、、天府")).containsExactlyInAnyOrder("紫微", "天府");
        assertThat((Boolean) invoke("ganListMatches", new Class[]{String.class, int.class}, "甲、乙", 0)).isTrue();
        assertThat((Boolean) invoke("ganListMatches", new Class[]{String.class, int.class}, "甲、乙", 5)).isFalse();
        assertThat((Boolean) invoke("ganListMatches", new Class[]{String.class, int.class}, "甲", -1)).isFalse();
        assertThat((Boolean) invoke("ganListHasSuspectToken", new Class[]{String.class}, "申")).isTrue();
        assertThat((Boolean) invoke("ganListHasSuspectToken", new Class[]{String.class}, "甲")).isFalse();
    }

    @Test
    @DisplayName("bureauOf 全干支 / xunZhongKongwang 全旬 / sixHePartner 全分支")
    void branchUtils() {
        for (int g = 0; g < 10; g++) {
            for (int b = 0; b < 12; b++) {
                final int gg = g, bb = b;
                assertThatCode(() -> invoke("bureauOf", new Class[]{int.class, int.class}, gg, bb)).doesNotThrowAnyException();
                assertThatCode(() -> invoke("xunZhongKongwang", new Class[]{int.class, int.class}, gg, bb)).doesNotThrowAnyException();
            }
        }
        assertThat((Integer) invoke("sixHePartner", new Class[]{int.class}, -1)).isEqualTo(-1);
        for (int i = 0; i < 12; i++) {
            final int ii = i;
            assertThatCode(() -> invoke("sixHePartner", new Class[]{int.class}, ii)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("宫位判定：huaInPalace/palaceHasLu/isZhuLuoSanXian/hasJuMenOrSha/isBuShuWuYi/isShuiShangJiaXing")
    void palaceJudges() {
        Map<Integer, List<String>> a = at();
        put(a, 0, "紫微", "禄存");
        put(a, 4, "七杀");
        put(a, 8, "破军");
        put(a, 6, "巨门");
        assertThat((Boolean) invoke("huaInPalace", new Class[]{int.class, Map.class, String.class, String.class}, 0, a, (String) null, "化禄")).isFalse();
        assertThat((Boolean) invoke("huaInPalace", new Class[]{int.class, Map.class, String.class, String.class}, -1, a, "紫微化禄", "化禄")).isFalse();
        assertThat((Boolean) invoke("huaInPalace", new Class[]{int.class, Map.class, String.class, String.class}, 0, a, "紫微化权", "化权")).isTrue();
        assertThat((Boolean) invoke("huaInPalace", new Class[]{int.class, Map.class, String.class, String.class}, 1, a, "紫微化权", "化权")).isFalse();
        assertThat((Boolean) invoke("palaceHasLu", new Class[]{int.class, Map.class, String.class}, -1, a, "x")).isFalse();
        assertThat((Boolean) invoke("palaceHasLu", new Class[]{int.class, Map.class, String.class}, 0, a, "x")).isTrue();
        assertThat((Boolean) invoke("isZhuLuoSanXian", new Class[]{int.class, Map.class}, 0, a)).isTrue();
        assertThat((Boolean) invoke("isZhuLuoSanXian", new Class[]{int.class, Map.class}, -1, a)).isFalse();
        assertThat((Boolean) invoke("isZhuLuoSanXian", new Class[]{int.class, Map.class}, 12, a)).isFalse();
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class[]{int.class, Map.class}, 0, a)).isTrue();
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class[]{int.class, Map.class}, -1, a)).isFalse();
        assertThat((Boolean) invoke("isBuShuWuYi", new Class[]{int[].class}, (Object) new int[]{1, 1, 1})).isTrue();
        assertThat((Boolean) invoke("isBuShuWuYi", new Class[]{int[].class}, (Object) new int[]{1, 2})).isFalse();
        assertThat((Boolean) invoke("isBuShuWuYi", new Class[]{int[].class}, (Object) new int[]{0, 0})).isFalse();
        assertThat((Boolean) invoke("isBuShuWuYi", new Class[]{int[].class}, (Object) new int[]{})).isFalse();
        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class[]{int[].class}, (Object) new int[]{1, -1, 1, -1})).isTrue();
        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class[]{int[].class}, (Object) new int[]{1, 1, -1, -1})).isFalse();
        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class[]{int[].class}, (Object) new int[]{1, -1, 0, 1})).isFalse();
        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class[]{int[].class}, (Object) new int[]{1, -1})).isFalse();
    }
}
