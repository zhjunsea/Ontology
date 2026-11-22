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
 * B 类补齐·零星工具残余分支批次（离线反射）。
 * parsePlacements / palaceStarRelation / brightnessOf / isIdleStar / isAnyStarBlockOpen /
 * detectShaCou / shaCouSatisfied / isJinYuFuJia / isBuShuWuYi / isShuiShangJiaXing /
 * hasJuMenOrSha / distillMarkup / starsSplitClamp / gongFallenWithSha / judgePalaceZhiGrid /
 * ganListMatches / ganListHasSuspectToken。
 */
@DisplayName("B类补齐·零星工具残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest7 {

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
    @DisplayName("parsePlacements：null/顿号分隔/无@")
    @SuppressWarnings("unchecked")
    void parsePlacementsBranches() {
        assertThat((Map<Integer, List<String>>) invoke("parsePlacements", new Class<?>[]{String.class}, (String) null)).isEmpty();
        Map<Integer, List<String>> at = (Map<Integer, List<String>>) invoke("parsePlacements", new Class<?>[]{String.class}, "紫微@子；天机@丑");
        assertThat(at.get(0)).contains("紫微");
        assertThat(at.get(1)).contains("天机");
        assertThat((Map<Integer, List<String>>) invoke("parsePlacements", new Class<?>[]{String.class}, "无位置星")).isEmpty();
    }

    @Test
    @DisplayName("palaceStarRelation：无五行/越界/比和/宫生星/宫克星/星生宫/星克宫")
    void palaceStarRelationBranches() {
        assertThat((String) invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, "无此星", 0)).isNull();
        assertThat((String) invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, "廉贞", 13)).isNull();
        assertThat((String) invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, "天机", 2)).contains("比和");           // 木在寅(木)
        assertThat((String) invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, "廉贞", 2)).contains("宫生星");        // 火在寅(木)
        assertThat((String) invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, "武曲", 6)).contains("受制");          // 金在午(火)
        assertThat((String) invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, "太阳", 1)).contains("星生宫");        // 火在丑(土)
        assertThat((String) invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, "武曲", 2)).contains("星克宫");        // 金在寅(木)
    }

    @Test
    @DisplayName("brightnessOf：越界平和/已知星")
    void brightnessOfBranches() {
        assertThat((String) invoke("brightnessOf", new Class<?>[]{String.class, int.class}, "紫微", 12)).isEqualTo("平和");
        assertThat((String) invoke("brightnessOf", new Class<?>[]{String.class, int.class}, "紫微", -1)).isEqualTo("平和");
        assertThat((String) invoke("brightnessOf", new Class<?>[]{String.class, int.class}, "紫微", 0)).isNotNull();
        assertThat((String) invoke("brightnessOf", new Class<?>[]{String.class, int.class}, "未知星", 3)).isEqualTo("平和");
    }

    @Test
    @DisplayName("isIdleStar：越界/落闲/非落闲/表外星")
    void isIdleStarBranches() {
        assertThat((Boolean) invoke("isIdleStar", new Class<?>[]{String.class, int.class}, "紫微", -1)).isFalse();
        assertThat((Boolean) invoke("isIdleStar", new Class<?>[]{String.class, int.class}, "紫微", 0)).isTrue();   // 紫微落子(闲)
        assertThat((Boolean) invoke("isIdleStar", new Class<?>[]{String.class, int.class}, "紫微", 1)).isFalse();  // 紫微不落丑
        assertThat((Boolean) invoke("isIdleStar", new Class<?>[]{String.class, int.class}, "无此星", 0)).isFalse();
    }

    @Test
    @DisplayName("isAnyStarBlockOpen：识别开篇/非开篇")
    void anyStarBlockOpenBranches() {
        assertThat((Boolean) invoke("isAnyStarBlockOpen", new Class<?>[]{String.class}, "紫微土德之星")).isTrue();
        assertThat((Boolean) invoke("isAnyStarBlockOpen", new Class<?>[]{String.class}, "前言无星")).isFalse();
        assertThat((Boolean) invoke("isAnyStarBlockOpen", new Class<?>[]{String.class}, "紫微")).isFalse(); // 长度不足（<=星名）
    }

    @Test
    @DisplayName("detectShaCou：null/无匹配/多种煞凑短语")
    void detectShaCouBranches() {
        assertThat(invoke("detectShaCou", new Class<?>[]{String.class}, (String) null)).isNull();
        assertThat(invoke("detectShaCou", new Class<?>[]{String.class}, "平常断语")).isNull();

        ZiWeiDouShuOntologyJobWorker.ShaCou a = (ZiWeiDouShuOntologyJobWorker.ShaCou)
                invoke("detectShaCou", new Class<?>[]{String.class}, "羊陀火铃忌冲合，主残疾");
        assertThat(a).isNotNull();
        assertThat(a.stars).contains("擎羊", "陀罗", "火星", "铃星");

        ZiWeiDouShuOntologyJobWorker.ShaCou b = (ZiWeiDouShuOntologyJobWorker.ShaCou)
                invoke("detectShaCou", new Class<?>[]{String.class}, "巨宿羊陀更照冲");
        assertThat(b.stars).contains("巨门", "擎羊", "陀罗");

        ZiWeiDouShuOntologyJobWorker.ShaCou c = (ZiWeiDouShuOntologyJobWorker.ShaCou)
                invoke("detectShaCou", new Class<?>[]{String.class}, "羊陀冲破");
        assertThat(c.phrase).contains("羊陀");

        ZiWeiDouShuOntologyJobWorker.ShaCou d = (ZiWeiDouShuOntologyJobWorker.ShaCou)
                invoke("detectShaCou", new Class<?>[]{String.class}, "羊火交会");
        assertThat(d.stars).contains("擎羊", "火星");
    }

    @Test
    @DisplayName("shaCouSatisfied：null/同宫命中与缺失/三方四正命中")
    void shaCouSatisfiedBranches() {
        assertThat((Boolean) invoke("shaCouSatisfied", new Class<?>[]{ZiWeiDouShuOntologyJobWorker.ShaCou.class, int.class, Map.class},
                (Object) null, 0, at())).isFalse();
        assertThat((Boolean) invoke("shaCouSatisfied", new Class<?>[]{ZiWeiDouShuOntologyJobWorker.ShaCou.class, int.class, Map.class},
                (Object) null, 0, (Object) null)).isFalse();

        ZiWeiDouShuOntologyJobWorker.ShaCou sc = new ZiWeiDouShuOntologyJobWorker.ShaCou();
        sc.stars.add("擎羊");
        sc.scope = "同宫";
        Map<Integer, List<String>> same = at();
        put(same, 3, "擎羊");
        assertThat((Boolean) invoke("shaCouSatisfied", new Class<?>[]{ZiWeiDouShuOntologyJobWorker.ShaCou.class, int.class, Map.class},
                sc, 3, same)).isTrue();
        assertThat((Boolean) invoke("shaCouSatisfied", new Class<?>[]{ZiWeiDouShuOntologyJobWorker.ShaCou.class, int.class, Map.class},
                sc, 3, at())).isFalse();

        ZiWeiDouShuOntologyJobWorker.ShaCou sc3 = new ZiWeiDouShuOntologyJobWorker.ShaCou();
        sc3.stars.add("擎羊");
        sc3.scope = "三方四正";
        Map<Integer, List<String>> tri = at();
        put(tri, (3 + 4) % 12, "擎羊");
        assertThat((Boolean) invoke("shaCouSatisfied", new Class<?>[]{ZiWeiDouShuOntologyJobWorker.ShaCou.class, int.class, Map.class},
                sc3, 3, tri)).isTrue();
    }

    @Test
    @DisplayName("isJinYuFuJia：非紫微守命/越界/日期非法/两侧分居/同侧")
    void isJinYuFuJiaBranches() {
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, java.util.Set.class, java.util.Set.class, String.class, String.class},
                0, new java.util.LinkedHashSet<>(List.of("天府")), new java.util.LinkedHashSet<>(), "酉", "卯")).isFalse();
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, java.util.Set.class, java.util.Set.class, String.class, String.class},
                -1, new java.util.LinkedHashSet<>(List.of("紫微")), new java.util.LinkedHashSet<>(), "酉", "卯")).isFalse();
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, java.util.Set.class, java.util.Set.class, String.class, String.class},
                0, new java.util.LinkedHashSet<>(List.of("紫微")), new java.util.LinkedHashSet<>(), "坏", "卯")).isFalse();
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, java.util.Set.class, java.util.Set.class, String.class, String.class},
                0, new java.util.LinkedHashSet<>(List.of("紫微")), new java.util.LinkedHashSet<>(), "酉", "卯")).isTrue();
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, java.util.Set.class, java.util.Set.class, String.class, String.class},
                0, new java.util.LinkedHashSet<>(List.of("紫微")), new java.util.LinkedHashSet<>(), "卯", "酉")).isTrue();
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, java.util.Set.class, java.util.Set.class, String.class, String.class},
                0, new java.util.LinkedHashSet<>(List.of("紫微")), new java.util.LinkedHashSet<>(), "酉", "申")).isFalse();
    }

    @Test
    @DisplayName("isBuShuWuYi / isShuiShangJiaXing：空/同号/异号/交替")
    void signSequenceBranches() {
        assertThat((Boolean) invoke("isBuShuWuYi", new Class<?>[]{int[].class}, (Object) null)).isFalse();
        assertThat((Boolean) invoke("isBuShuWuYi", new Class<?>[]{int[].class}, (Object) new int[]{})).isFalse();
        assertThat((Boolean) invoke("isBuShuWuYi", new Class<?>[]{int[].class}, new int[]{1, 1, 1})).isTrue();
        assertThat((Boolean) invoke("isBuShuWuYi", new Class<?>[]{int[].class}, new int[]{1, 1, 2})).isFalse();
        assertThat((Boolean) invoke("isBuShuWuYi", new Class<?>[]{int[].class}, new int[]{0, 0})).isFalse();

        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class<?>[]{int[].class}, (Object) null)).isFalse();
        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class<?>[]{int[].class}, new int[]{1, -1, 1})).isFalse();
        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class<?>[]{int[].class}, new int[]{1, -1, 1, -1})).isTrue();
        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class<?>[]{int[].class}, new int[]{1, 1, -1, -1})).isFalse();
        assertThat((Boolean) invoke("isShuiShangJiaXing", new Class<?>[]{int[].class}, new int[]{1, 0, 1, -1})).isFalse();
    }

    @Test
    @DisplayName("hasJuMenOrSha：越界/巨门/煞星/空")
    void hasJuMenOrShaBranches() {
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, -1, at())).isFalse();
        Map<Integer, List<String>> jm = at();
        put(jm, 0, "巨门");
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, 0, jm)).isTrue();
        Map<Integer, List<String>> sha = at();
        put(sha, (0 + 4) % 12, "火星");
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, 0, sha)).isTrue();
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, 0, at())).isFalse();
    }

    @Test
    @DisplayName("distillMarkup：null/空/去括注与竖线")
    void distillMarkupBranches() {
        assertThat((String) invoke("distillMarkup", new Class<?>[]{String.class}, (String) null)).isNull();
        assertThat((String) invoke("distillMarkup", new Class<?>[]{String.class}, "")).isEqualTo("");
        assertThat((String) invoke("distillMarkup", new Class<?>[]{String.class}, "断语〔释读·待考〕｜结论")).isEqualTo("断语；结论");
    }

    @Test
    @DisplayName("starsSplitClamp：目标越界/两邻分居/同侧")
    void starsSplitClampBranches() {
        assertThat((Boolean) invoke("starsSplitClamp", new Class<?>[]{Map.class, String.class, int.class}, at(), "甲星、乙星", -1)).isFalse();
        Map<Integer, List<String>> ok = at();
        put(ok, 1, "甲星");
        put(ok, 11, "乙星");
        assertThat((Boolean) invoke("starsSplitClamp", new Class<?>[]{Map.class, String.class, int.class}, ok, "甲星、乙星", 0)).isTrue();
        Map<Integer, List<String>> sameSide = at();
        put(sameSide, 1, "甲星");
        put(sameSide, 2, "乙星");
        assertThat((Boolean) invoke("starsSplitClamp", new Class<?>[]{Map.class, String.class, int.class}, sameSide, "甲星、乙星", 0)).isFalse();
    }

    @Test
    @DisplayName("gongFallenWithSha：越界/无煞/有煞无陷")
    void gongFallenWithShaBranches() {
        assertThat((Boolean) invoke("gongFallenWithSha", new Class<?>[]{int.class, Map.class}, -1, at())).isFalse();
        assertThat((Boolean) invoke("gongFallenWithSha", new Class<?>[]{int.class, Map.class}, 0, at())).isFalse();
        Map<Integer, List<String>> shaOnly = at();
        put(shaOnly, 0, "擎羊");
        assertThat((Boolean) invoke("gongFallenWithSha", new Class<?>[]{int.class, Map.class}, 0, shaOnly)).isFalse();
        Map<Integer, List<String>> mainOnly = at();
        put(mainOnly, 0, "紫微");
        assertThatCode(() -> invoke("gongFallenWithSha", new Class<?>[]{int.class, Map.class}, 0, mainOnly)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("judgePalaceZhiGrid：命未定/正常遍历（含生年干分支）")
    void judgePalaceZhiGridBranches() {
        assertThat((String) invoke("judgePalaceZhiGrid", new Class<?>[]{int.class, Map.class, int.class}, -1, at(), 0)).contains("命宫未定");
        assertThatCode(() -> invoke("judgePalaceZhiGrid", new Class<?>[]{int.class, Map.class, int.class}, 0, at(), -1)).doesNotThrowAnyException();
        assertThat((String) invoke("judgePalaceZhiGrid", new Class<?>[]{int.class, Map.class, int.class}, 0, at(), 0)).isNotNull();
    }

    @Test
    @DisplayName("ganListMatches / ganListHasSuspectToken：命中/未命中/讹字")
    void ganListBranches() {
        assertThat((Boolean) invoke("ganListMatches", new Class<?>[]{String.class, int.class}, "甲、乙", -1)).isFalse();
        assertThat((Boolean) invoke("ganListMatches", new Class<?>[]{String.class, int.class}, "甲、乙", 0)).isTrue();
        assertThat((Boolean) invoke("ganListMatches", new Class<?>[]{String.class, int.class}, "甲、乙", 2)).isFalse();

        assertThat((Boolean) invoke("ganListHasSuspectToken", new Class<?>[]{String.class}, "甲、申")).isTrue();
        assertThat((Boolean) invoke("ganListHasSuspectToken", new Class<?>[]{String.class}, "甲、乙")).isFalse();
    }
}
