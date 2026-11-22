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
 * B 类补齐·判断/断语函数残余分支批次（离线反射）。
 */
@DisplayName("B类补齐·判断与断语函数残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest3 {

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
    @DisplayName("isStarBlockOpen / classifyGe 全分支")
    void stringJudges() {
        assertThat((Boolean) invoke("isStarBlockOpen", new Class[]{String.class, String.class}, (String) null, "紫微")).isFalse();
        assertThat((Boolean) invoke("isStarBlockOpen", new Class[]{String.class, String.class}, "abc", (String) null)).isFalse();
        assertThat((Boolean) invoke("isStarBlockOpen", new Class[]{String.class, String.class}, "紫微", "紫微")).isFalse();
        assertThat((Boolean) invoke("isStarBlockOpen", new Class[]{String.class, String.class}, "紫微在", "紫微")).isFalse();
        assertThat((Boolean) invoke("isStarBlockOpen", new Class[]{String.class, String.class}, "紫微土", "紫微")).isTrue();
        assertThat((Boolean) invoke("isStarBlockOpen", new Class[]{String.class, String.class}, "天机火", "天机")).isTrue();
        assertThat((String) invoke("classifyGe", new Class[]{String.class}, "不贵")).isEqualTo("凶");
        assertThat((String) invoke("classifyGe", new Class[]{String.class}, "不为")).isEqualTo("凶");
        assertThat((String) invoke("classifyGe", new Class[]{String.class}, "财官格")).isEqualTo("吉");
        assertThat((String) invoke("classifyGe", new Class[]{String.class}, "困")).isEqualTo("凶");
        assertThat((String) invoke("classifyGe", new Class[]{String.class}, "财官格困")).isEqualTo("吉凶参");
        assertThat((String) invoke("classifyGe", new Class[]{String.class}, "平平")).isEqualTo("平");
    }

    @Test
    @DisplayName("palaceStarRelation 全生克关系 / combineWord 全组合")
    void relations() {
        String[] stars = {"紫微", "天府", "天梁", "左辅", "禄存", "天机", "贪狼", "太阳", "廉贞",
                "天魁", "天钺", "火星", "铃星", "天马", "地劫", "天空", "武曲", "七杀", "文昌",
                "擎羊", "陀罗", "天同", "太阴", "巨门", "天相", "破军", "文曲", "右弼"};
        for (String s : stars) {
            for (int b = 0; b < 12; b++) {
                assertThat((String) invoke("palaceStarRelation", new Class[]{String.class, int.class}, s, b)).isNotNull();
            }
        }
        assertThat((String) invoke("palaceStarRelation", new Class[]{String.class, int.class}, "X", 0)).isNull();
        assertThat((String) invoke("palaceStarRelation", new Class[]{String.class, int.class}, "紫微", -1)).isNull();
        assertThat((String) invoke("palaceStarRelation", new Class[]{String.class, int.class}, "紫微", 12)).isNull();
        assertThat((String) invoke("combineWord", new Class[]{int.class, int.class}, 1, 1)).isEqualTo("吉");
        assertThat((String) invoke("combineWord", new Class[]{int.class, int.class}, -1, -1)).isEqualTo("凶");
        assertThat((String) invoke("combineWord", new Class[]{int.class, int.class}, 1, -1)).isEqualTo("吉凶相伴");
    }

    @Test
    @DisplayName("judgePalaceZhiGrid 命宫未定 / 全宫支×生年干")
    void palaceGrid() {
        assertThat((String) invoke("judgePalaceZhiGrid", new Class[]{int.class, Map.class, int.class}, -1, at(), 0))
                .contains("宫未定");
        int[] ygs = {-1, 0, 3, 9};
        Map<Integer, List<String>> a = at();
        put(a, 0, "太阴", "贪狼");
        for (int m = 0; m < 12; m++) {
            for (int y : ygs) {
                final int mm = m, yy = y;
                assertThatCode(() -> invoke("judgePalaceZhiGrid", new Class[]{int.class, Map.class, int.class}, mm, a, yy))
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("candidateMingStars / judgeTwoLimitsTaiSui / ziweiHuaNote / judgeXianPinHouFu / judgeLongevity")
    @SuppressWarnings("unchecked")
    void miscJudges() {
        assertThat((List<String>) invoke("candidateMingStars", new Class[]{int.class, int.class, int.class, int.class}, 1, 0, 0, 0)).isEmpty();
        assertThat((List<String>) invoke("candidateMingStars", new Class[]{int.class, int.class, int.class, int.class}, 1, 31, 0, 0)).isEmpty();
        assertThat((List<String>) invoke("candidateMingStars", new Class[]{int.class, int.class, int.class, int.class}, 5, 15, 6, 3)).isNotNull();
        assertThatCode(() -> invoke("candidateMingStars", new Class[]{int.class, int.class, int.class, int.class}, 8, 20, 9, 7)).doesNotThrowAnyException();
        assertThatCode(() -> invoke("candidateMingStars", new Class[]{int.class, int.class, int.class, int.class}, 12, 28, 11, 5)).doesNotThrowAnyException();

        assertThat((String) invoke("judgeTwoLimitsTaiSui", new Class[]{Map.class, int.class, int.class, int.class, int.class, int.class, int.class}, at(), -1, 3, 3, -1, -1, -1)).contains("论二限太岁吉凶");
        Map<Integer, List<String>> a2 = at();
        put(a2, 2, "擎羊");
        put(a2, 8, "七杀");
        assertThat((String) invoke("judgeTwoLimitsTaiSui", new Class[]{Map.class, int.class, int.class, int.class, int.class, int.class, int.class}, a2, 2, 2, 2, 1, 1, 1)).contains("太岁冲羊陀七杀");
        assertThat((String) invoke("judgeTwoLimitsTaiSui", new Class[]{Map.class, int.class, int.class, int.class, int.class, int.class, int.class}, at(), 6, 1, 0, 1, 0, 0)).contains("论二限太岁吉凶");
        assertThat((String) invoke("judgeTwoLimitsTaiSui", new Class[]{Map.class, int.class, int.class, int.class, int.class, int.class, int.class}, at(), 5, 6, 11, 1, 1, 1)).contains("论二限太岁吉凶");

        assertThat((String) invoke("ziweiHuaNote", new Class[]{Map.class, int.class, String.class}, at(), 0, (String) null)).isEmpty();
        assertThat((String) invoke("ziweiHuaNote", new Class[]{Map.class, int.class, String.class}, at(), 0, "")).isEmpty();
        assertThat((String) invoke("ziweiHuaNote", new Class[]{Map.class, int.class, String.class}, at(), -1, "紫微化禄")).isEmpty();
        assertThat((String) invoke("ziweiHuaNote", new Class[]{Map.class, int.class, String.class}, at(), 12, "紫微化禄")).isEmpty();
        Map<Integer, List<String>> h = at();
        put(h, 0, "紫微");
        put(h, 1, "天府");
        assertThat((String) invoke("ziweiHuaNote", new Class[]{Map.class, int.class, String.class}, h, 0, "紫微化禄、天府化权")).isEqualTo("紫微化禄");
        assertThat((String) invoke("ziweiHuaNote", new Class[]{Map.class, int.class, String.class}, at(), 0, "紫微化禄")).isEmpty();

        assertThat((String) invoke("judgeXianPinHouFu", new Class[]{int[].class}, (Object) null)).contains("不判");
        assertThat((String) invoke("judgeXianPinHouFu", new Class[]{int[].class}, (Object) new int[]{1, 1, 1})).contains("不判");
        assertThatCode(() -> invoke("judgeXianPinHouFu", new Class[]{int[].class}, (Object) new int[]{1, 1, 1, -1, -1, -1, -1, -1, -1, 1, 1, 1})).doesNotThrowAnyException();

        assertThat((List<String>) invoke("judgeLongevity", new Class[]{int.class, int.class, Map.class}, -1, 0, at())).isEmpty();
        assertThat((List<String>) invoke("judgeLongevity", new Class[]{int.class, int.class, Map.class}, 12, 0, at())).isEmpty();
        assertThatCode(() -> invoke("judgeLongevity", new Class[]{int.class, int.class, Map.class}, 0, 0, at())).doesNotThrowAnyException();
        Map<Integer, List<String>> l = at();
        put(l, 0, "七杀");
        put(l, 1, "贪狼");
        assertThatCode(() -> invoke("judgeLongevity", new Class[]{int.class, int.class, Map.class}, 0, 6, l)).doesNotThrowAnyException();
    }
}
