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
 * 逐条定向覆盖·第3批：对**小数值域纯函数**做"全枚举"，一次性覆盖其全部分支
 * （ziweiBranch / lucunBranch / liuyangBranch / liutuoBranch / wuhuYinGan / deityName /
 *   isBranch / annualFortuneSign / yunxianFortuneSign）。纯反射离线。
 */
@DisplayName("逐条定向覆盖·小域纯函数全枚举（安星/流曜/宫干/运限符号）")
class ZiWeiDouShuHelperExhaustiveTest {

    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    @Test
    @DisplayName("judgeXiaoErMing：mingIdx 0..11 × {庙旺/庙旺+煞/空} 扫描")
    void judgeXiaoErMingSweep() {
        for (int mi = 0; mi < 12; mi++) {
            assertThat((String) call("judgeXiaoErMing", new Class<?>[]{int.class, Map.class}, mi, mapWith(mi, "太阳"))).isNotNull();
            assertThat((String) call("judgeXiaoErMing", new Class<?>[]{int.class, Map.class}, mi, mapWith(mi, "太阳", "擎羊"))).isNotNull();
            assertThat((String) call("judgeXiaoErMing", new Class<?>[]{int.class, Map.class}, mi, new LinkedHashMap<>())).isNotNull();
        }
        // 命宫左右羊陀相夹（夹羊夹陀）分支
        Map<Integer, List<String>> jia = mapWith(6, "太阳");
        jia.put(5, new ArrayList<>(Arrays.asList("擎羊")));
        jia.put(7, new ArrayList<>(Arrays.asList("陀罗")));
        assertThat((String) call("judgeXiaoErMing", new Class<?>[]{int.class, Map.class}, 6, jia)).isNotNull();
    }

    @Test
    @DisplayName("judgeNanNvTongYi：女命命/身见贪杀羊 + 福德单七杀 扫描")
    void judgeNanNvSweep() {
        String skeleton = "命宫=子;身宫=午;福德=卯;";
        for (String harsh : new String[]{"贪狼", "七杀", "擎羊", "天府", "太阳"}) {
            List<String> ming = new ArrayList<>(Arrays.asList(harsh));
            assertThat((String) call("judgeNanNvTongYi", new Class<?>[]{int.class, List.class, Map.class, String.class},
                    0, ming, mapWith(0, harsh), skeleton)).isNotNull();
        }
        // 七杀单居福德（卯=3）
        Map<Integer, List<String>> at = mapWith(0, "天府");
        at.put(3, new ArrayList<>(Arrays.asList("七杀")));
        assertThat((String) call("judgeNanNvTongYi", new Class<?>[]{int.class, List.class, Map.class, String.class},
                0, new ArrayList<>(Arrays.asList("天府")), at, skeleton)).isNotNull();
    }

    @Test
    @DisplayName("judgeLongevity 追加变体：酉破廉/僧道无主/孤克/贪狼他宫庙(淫荡)/铃陀贪巨")
    void judgeLongevityMoreVariants() {
        // 酉(9)廉贞破军 → 艺术（破廉卯酉，安星真盘；原仅 isNotNull 过宽）
        List<?> youPoLian = (List<?>) call("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 9, 9,
                mapWith(9, "破军", "廉贞"));
        assertThat(youPoLian).as("酉廉贞破军→艺术").anyMatch(s -> String.valueOf(s).contains("艺术"));
        assertThat((List<?>) call("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, 0,
                mapWith(0, "地劫", "擎羊"))).isNotNull();
        Map<Integer, List<String>> gu = mapWith(0, "天同");
        gu.put(1, new ArrayList<>(Arrays.asList("太阳", "擎羊")));
        assertThat((List<?>) call("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, 0, gu)).isNotNull();
        Map<Integer, List<String>> yin = mapWith(0, "天机");
        yin.put(1, new ArrayList<>(Arrays.asList("贪狼")));
        assertThat((List<?>) call("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, 0, yin)).isNotNull();
        assertThat((List<?>) call("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, 0,
                mapWith(0, "铃星", "巨门"))).isNotNull();
    }

    private static Map<Integer, List<String>> mapWith(int p, String... stars) {
        Map<Integer, List<String>> m = new LinkedHashMap<>();
        m.put(p, new ArrayList<>(Arrays.asList(stars)));
        return m;
    }

    private static Object call(String name, Class<?>[] sig, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("反射失败: " + name, e);
        }
    }

    private static Map<Integer, List<String>> chart() {
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        at.put(0, new ArrayList<>(Arrays.asList("紫微", "七杀")));
        at.put(6, new ArrayList<>(Arrays.asList("武曲", "擎羊")));
        at.put(4, new ArrayList<>(Arrays.asList("贪狼", "火星")));
        return at;
    }

    @Test
    @DisplayName("ziweiBranch：bureau∈2..6 × day∈1..30 全枚举")
    void ziweiBranchExhaustive() {
        for (int b = 2; b <= 6; b++) {
            for (int d = 1; d <= 30; d++) {
                int r = (Integer) call("ziweiBranch", new Class<?>[]{int.class, int.class}, b, d);
                assertThat(r).isBetween(0, 11);
            }
        }
    }

    @Test
    @DisplayName("lucun/liuyang/liutuo/wuhuYinGan：年干 0..9 全枚举")
    void liuyaoExhaustive() {
        for (int g = 0; g < 10; g++) {
            assertThat((Integer) call("lucunBranch", new Class<?>[]{int.class}, g)).isBetween(0, 11);
            assertThat((Integer) call("liuyangBranch", new Class<?>[]{int.class}, g)).isBetween(0, 11);
            assertThat((Integer) call("liutuoBranch", new Class<?>[]{int.class}, g)).isBetween(0, 11);
            assertThat((Integer) call("wuhuYinGan", new Class<?>[]{int.class}, g)).isBetween(0, 9);
        }
    }

    @Test
    @DisplayName("deityName：博士十二神 0..11")
    void deityNameExhaustive() {
        for (int i = 0; i < 12; i++) {
            assertThat((String) call("deityName", new Class<?>[]{int.class}, i)).isNotNull();
        }
    }

    @Test
    @DisplayName("isBranch：idx 0..11 × 全部地支")
    void isBranchExhaustive() {
        for (int i = 0; i <= 12; i++) {
            for (String z : ZHI) {
                assertThat((Boolean) call("isBranch", new Class<?>[]{int.class, String.class}, i, z)).isNotNull();
            }
        }
        assertThat((Boolean) call("isBranch", new Class<?>[]{int.class, String.class}, 0, "子")).isTrue();
        assertThat((Boolean) call("isBranch", new Class<?>[]{int.class, String.class}, 0, "丑")).isFalse();
    }

    @Test
    @DisplayName("annualFortuneSign：flowBranch 0..11 × flowGan 0..9 全枚举")
    void annualFortuneSignExhaustive() {
        Map<Integer, List<String>> at = chart();
        for (int fb = 0; fb < 12; fb++) {
            for (int fg = 0; fg < 10; fg++) {
                int s = (Integer) call("annualFortuneSign",
                        new Class<?>[]{Map.class, int.class, int.class}, at, fb, fg);
                assertThat(s).isBetween(-1, 1);
            }
        }
    }

    @Test
    @DisplayName("yunxianFortuneSign：idx 0..11 × fourTransform(null/非空) 全枚举")
    void yunxianFortuneSignExhaustive() {
        Map<Integer, List<String>> at = chart();
        for (int idx = 0; idx < 12; idx++) {
            assertThat((Integer) call("yunxianFortuneSign",
                    new Class<?>[]{Map.class, int.class, String.class}, at, idx, null)).isBetween(-1, 1);
            assertThat((Integer) call("yunxianFortuneSign",
                    new Class<?>[]{Map.class, int.class, String.class}, at, idx, "紫微化权;武曲化禄;")).isBetween(-1, 1);
        }
    }

    @Test
    @DisplayName("ziweiPalaceMeaning：12宫 × sign∈{-1,0,1} 全枚举")
    void ziweiPalaceMeaningExhaustive() {
        String[] pal = {"命宫", "兄弟", "妻妾", "子女", "财帛", "疾厄", "迁移", "奴仆", "官禄", "田宅", "福德", "父母"};
        for (String p : pal) {
            for (int sgn = -1; sgn <= 1; sgn++) {
                assertThat((String) call("ziweiPalaceMeaning", new Class<?>[]{String.class, int.class}, p, sgn)).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("judgeXianPinHouFu：limitSigns 多模式枚举")
    void judgeXianPinHouFuExhaustive() {
        int[][] pats = {
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
                {1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {1, 0, -1, 1, 0, -1, 1, 0, -1, 1, 0, -1}
        };
        for (int[] p : pats) {
            assertThat((String) call("judgeXianPinHouFu", new Class<?>[]{int[].class}, (Object) p)).isNotNull();
        }
    }

    @Test
    @DisplayName("judgeYangTuoDieBing / judgeQiShaReFeng：mingIdx×flowGan×星盘 扫描")
    void yangTuoQiShaSweep() {
        for (int mi = 0; mi < 12; mi++) {
            for (int fg = 0; fg < 10; fg++) {
                assertThat((Boolean) call("judgeYangTuoDieBing",
                        new Class<?>[]{int.class, Map.class, int.class}, mi, mapWith(mi, "擎羊"), fg)).isNotNull();
                assertThat((Boolean) call("judgeQiShaReFeng",
                        new Class<?>[]{int.class, Map.class, int.class}, mi, mapWith(mi, "七杀"), fg)).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("judgeTenGrades：多组合 (mingSet/mingMain/sanFang) 扫描")
    @SuppressWarnings("unchecked")
    void judgeTenGradesSweep() {
        String[][] combos = {
                {"天同", "天梁"}, {"紫微", "武曲"}, {"破军", "贪狼"}, {"天府", "太阴"},
                {"太阳", "巨门"}, {"七杀", "天相"}, {"廉贞", "贪狼", "火星"}, {"文昌", "文曲"}, {"禄存", "天马"},
        };
        for (String[] c : combos) {
            java.util.Set<String> s = new java.util.HashSet<>(Arrays.asList(c));
            assertThat((List<?>) call("judgeTenGrades",
                    new Class<?>[]{java.util.Set.class, java.util.Set.class, java.util.Set.class}, s, s, s)).isNotNull();
        }
        assertThat((List<?>) call("judgeTenGrades",
                new Class<?>[]{java.util.Set.class, java.util.Set.class, java.util.Set.class},
                new java.util.HashSet<String>(), new java.util.HashSet<String>(), new java.util.HashSet<String>())).isNotNull();
    }

    @Test
    @DisplayName("judgeTwoLimitsTaiSui：三符号∈{-1,0,1} 全枚举")
    void judgeTwoLimitsTaiSuiSweep() {
        Map<Integer, List<String>> at = chart();
        for (int ds = -1; ds <= 1; ds++) {
            for (int xs = -1; xs <= 1; xs++) {
                for (int ts = -1; ts <= 1; ts++) {
                    assertThat((String) call("judgeTwoLimitsTaiSui",
                            new Class<?>[]{Map.class, int.class, int.class, int.class, int.class, int.class, int.class},
                            at, 0, 4, 8, ds, xs, ts)).isNotNull();
                }
            }
        }
    }

    @Test
    @DisplayName("judgeTanXingYaoLun：多星盘（聚吉/见煞/本盘）")
    void judgeTanXingYaoLunSweep() {
        String sk = "命宫=子;身宫=午;迁移=午;财帛=辰;官禄=申;福德=寅;";
        Map<Integer, List<String>> good = mapWith(0, "左辅", "右弼", "文昌", "文曲", "天魁", "天钺", "禄存");
        Map<Integer, List<String>> bad = mapWith(0, "擎羊", "陀罗", "火星", "铃星", "地劫", "天空");
        assertThat((String) call("judgeTanXingYaoLun",
                new Class<?>[]{int.class, Map.class, String.class}, 0, good, sk)).isNotNull();
        assertThat((String) call("judgeTanXingYaoLun",
                new Class<?>[]{int.class, Map.class, String.class}, 0, bad, sk)).isNotNull();
        assertThat((String) call("judgeTanXingYaoLun",
                new Class<?>[]{int.class, Map.class, String.class}, 0, chart(), sk)).isNotNull();
    }
}
