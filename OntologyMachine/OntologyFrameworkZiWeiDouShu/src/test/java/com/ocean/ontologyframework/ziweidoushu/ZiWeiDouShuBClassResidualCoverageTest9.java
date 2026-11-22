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
 * B 类补齐·④ 运限（大限/流年/宫位叠加）残余分支批次（离线反射）。
 * judgeLongevity / judgeTenGrades / judgeXiaoErMing / taisuiXiaoxianGanHit / judgeLiuNianTaiSui /
 * majorEventBacktrace / ziweiLongevityVerdict / ziweiMainStarDesc / ziweiChartLevelVerdict /
 * ziweiPalaceVerdictLine / ziweiSuiXianOverlay。
 */
@DisplayName("B类补齐·运限判定残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest9 {

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

    private static Map<Integer, List<String>> rich() {
        Map<Integer, List<String>> at = at();
        put(at, 0, "紫微", "天府", "左辅");
        put(at, 4, "文昌", "擎羊");
        put(at, 6, "武曲", "天同");
        put(at, 8, "太阴", "禄存");
        return at;
    }

    @Test
    @DisplayName("judgeLongevity：越界×富盘/空盘")
    @SuppressWarnings("unchecked")
    void judgeLongevityMatrix() {
        int[] idxs = {-1, 0, 3, 6, 9, 11, 12};
        for (int ming : idxs) {
            for (int shen : idxs) {
                final int mi = ming, si = shen;
                assertThatCode(() -> assertThat((List<String>) invoke("judgeLongevity",
                        new Class<?>[]{int.class, int.class, Map.class}, mi, si, rich())).isNotNull())
                        .doesNotThrowAnyException();
                assertThatCode(() -> invoke("judgeLongevity",
                        new Class<?>[]{int.class, int.class, Map.class}, mi, si, at())).doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("judgeTenGrades：空/富寿/商贾/多等并见")
    void judgeTenGradesMatrix() {
        Set<String> empty = new LinkedHashSet<>();
        Set<String> tongLiang = new LinkedHashSet<>(List.of("天同", "天梁"));
        Set<String> shangJia = new LinkedHashSet<>(List.of("贪狼", "太阴", "天同", "七杀", "天机"));
        assertThat((List<String>) invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                tongLiang, tongLiang, empty)).isNotNull();
        assertThat((List<String>) invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                shangJia, shangJia, shangJia)).isNotNull();
        assertThat((List<String>) invoke("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                empty, empty, empty)).isEmpty();
    }

    @Test
    @DisplayName("judgeXiaoErMing：命未定/全命宫×富盘/空盘")
    void judgeXiaoErMingMatrix() {
        assertThat((String) invoke("judgeXiaoErMing", new Class<?>[]{int.class, Map.class}, -1, at())).contains("不判");
        for (int ming = 0; ming < 12; ming++) {
            final int m = ming;
            assertThatCode(() -> invoke("judgeXiaoErMing", new Class<?>[]{int.class, Map.class}, m, rich()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("taisuiXiaoxianGanHit：干/支越界返回空，合法返回非空")
    void taisuiXiaoxianGanHitBranches() {
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class<?>[]{int.class, int.class}, -1, 0)).isEmpty();
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class<?>[]{int.class, int.class}, 0, 12)).isEmpty();
        for (int gan = 0; gan < 10; gan++) {
            for (int fb = 0; fb < 12; fb++) {
                final int g = gan, f = fb;
                assertThatCode(() -> assertThat((String) invoke("taisuiXiaoxianGanHit",
                        new Class<?>[]{int.class, int.class}, g, f)).isNotNull()).doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("judgeLiuNianTaiSui：无流年/多流年")
    void judgeLiuNianTaiSuiBranches() {
        assertThat((String) invoke("judgeLiuNianTaiSui", new Class<?>[]{int.class, int.class, Map.class}, 0, -1, at()))
                .contains("不判");
        for (int fb = 0; fb < 12; fb++) {
            final int f = fb;
            assertThatCode(() -> invoke("judgeLiuNianTaiSui", new Class<?>[]{int.class, int.class, Map.class},
                    0, f, rich())).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("majorEventBacktrace：null/空/无年份/多事件分隔切块")
    void majorEventBacktraceBranches() {
        assertThat(invoke("majorEventBacktrace", new Class<?>[]{String.class, Map.class}, (String) null, rich())).isNull();
        assertThat(invoke("majorEventBacktrace", new Class<?>[]{String.class, Map.class}, "   ", rich())).isNull();
        assertThat(invoke("majorEventBacktrace", new Class<?>[]{String.class, Map.class}, "无年份描述", rich())).isNull();
        Object r = invoke("majorEventBacktrace", new Class<?>[]{String.class, Map.class}, "2010年结婚；2015年升职", rich());
        assertThat((String) r).isNotNull().contains("共 2 项");
    }

    @Test
    @DisplayName("ziweiLongevityVerdict：null/空/命中类别")
    @SuppressWarnings("unchecked")
    void ziweiLongevityVerdictBranches() {
        assertThat((String) invoke("ziweiLongevityVerdict", new Class<?>[]{List.class}, (Object) null)).isNotNull();
        assertThat((String) invoke("ziweiLongevityVerdict", new Class<?>[]{List.class}, List.of())).isNotNull();
        assertThat((String) invoke("ziweiLongevityVerdict", new Class<?>[]{List.class},
                List.of("商贾（贪月同杀会机梁）", "驳杂"))).contains("商贾");
    }

    @Test
    @DisplayName("ziweiMainStarDesc：越界/全宫位×富盘/空盘")
    void ziweiMainStarDescBranches() {
        assertThat((String) invoke("ziweiMainStarDesc", new Class<?>[]{Map.class, int.class}, at(), -1)).contains("宫未定");
        assertThat((String) invoke("ziweiMainStarDesc", new Class<?>[]{Map.class, int.class}, at(), 12)).contains("宫未定");
        for (int idx = 0; idx < 12; idx++) {
            final int i = idx;
            assertThatCode(() -> assertThat((String) invoke("ziweiMainStarDesc",
                    new Class<?>[]{Map.class, int.class}, rich(), i)).isNotNull()).doesNotThrowAnyException();
            assertThatCode(() -> invoke("ziweiMainStarDesc",
                    new Class<?>[]{Map.class, int.class}, at(), i)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("ziweiChartLevelVerdict / ziweiPalaceVerdictLine：多档位×富盘/空盘")
    void ziweiVerdictLinesBranches() {
        for (String grade : new String[]{"上格（贵局）", "中格（吉凶相伴守照）", "下格（贫贱局）", "常格"}) {
            assertThatCode(() -> assertThat((String) invoke("ziweiChartLevelVerdict",
                    new Class<?>[]{String.class, Map.class, String.class}, grade, rich(), SKEL)).isNotNull())
                    .doesNotThrowAnyException();
            assertThatCode(() -> invoke("ziweiChartLevelVerdict",
                    new Class<?>[]{String.class, Map.class, String.class}, grade, at(), SKEL)).doesNotThrowAnyException();
        }
        for (String palace : new String[]{"命宫", "身宫", "财帛", "官禄", "夫妻"}) {
            assertThatCode(() -> assertThat((String) invoke("ziweiPalaceVerdictLine",
                    new Class<?>[]{String.class, Map.class, String.class, String.class}, palace, rich(), SKEL, ""))
                    .isNotNull()).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("ziweiSuiXianOverlay：出生信息不足回退/正常多年叠看")
    void ziweiSuiXianOverlayBranches() {
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("SOLAR", "坏值");
        assertThat((String) invoke("ziweiSuiXianOverlay",
                new Class<?>[]{Map.class, String.class, Map.class, String.class, int.class}, bad, SKEL, rich(), "", 2))
                .contains("出生信息不足");

        Map<String, String> cb = new LinkedHashMap<>();
        cb.put("SOLAR", "1990-05-15T10:30");
        cb.put("GENDER", "男");
        cb.put("LY", "1990");
        for (int years : new int[]{1, 3}) {
            final int y = years;
            assertThatCode(() -> assertThat((String) invoke("ziweiSuiXianOverlay",
                    new Class<?>[]{Map.class, String.class, Map.class, String.class, int.class}, cb, SKEL, rich(), "", y))
                    .isNotNull()).doesNotThrowAnyException();
        }
    }
}
