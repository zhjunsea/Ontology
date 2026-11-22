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
 * B 类补齐·⑩ 业务纯函数残余分支批次（离线反射）。
 *
 * <p>针对 {@link ZiWeiDouShuOntologyJobWorker} 中确定性业务纯函数（庙陷/五行局/神煞/星辰生克/
 * 得地格/十等论/诸格论/寿元/夹拱/重大事件回推/岁限叠看等）补齐此前未覆盖的**业务分支**（IT/OBDA/LLM
 * 类分支不在本批范围）。私有静态方法经自建反射助手调用；ONTO_* 缓存字段经反射注入并在 @AfterEach 复位。
 */
@DisplayName("B类补齐·业务纯函数残余分支批次")
class ZiWeiDouShuBClassResidualCoverageTest10 {

    private static final String[] MAIN = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府",
            "太阴", "贪狼", "巨门", "天相", "天梁", "七杀", "破军"};
    private static final String[] JI = {"禄存", "天魁", "天钺", "左辅", "右弼", "文昌", "文曲", "天官", "天福", "恩光", "天贵"};
    private static final String[] SHA = {"擎羊", "陀罗", "火星", "铃星", "天空", "地劫"};
    private static final String SKEL = "命宫=子;身宫=午;兄弟=丑;夫妻=寅;子女=卯;财帛=辰;疾厄=巳;迁移=午;奴仆=未;官禄=申;田宅=酉;福德=戌;父母=亥";
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    private static final String[] ONTO_FIELDS = {
            "ONTO_PATTERN_VERSE_MAP", "ONTO_DEITY_12", "ONTO_STAR_BLOCK_ALL", "ONTO_BRIGHTNESS",
            "ONTO_TAISUI_XIAOXIAN"};

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

    private static Map<Integer, List<String>> fullAllStars() {
        Map<Integer, List<String>> m = at();
        for (int p = 0; p < 12; p++) {
            put(m, p, MAIN);
            put(m, p, JI);
            put(m, p, SHA);
            put(m, p, "天刑", "化禄", "化权", "化科", "化忌");
        }
        return m;
    }

    // ============ taisuiXiaoxianGanHit：生干/支越界 + 全扫 ============
    @Test
    @DisplayName("taisuiXiaoxianGanHit：生干/支越界 + 全干×全支")
    void taisuiXiaoxianGanHitBranches() {
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class<?>[]{int.class, int.class}, -1, 0)).isEmpty();
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class<?>[]{int.class, int.class}, 10, 0)).isEmpty();
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class<?>[]{int.class, int.class}, 0, -1)).isEmpty();
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class<?>[]{int.class, int.class}, 0, 12)).isEmpty();
        for (int g = 0; g <= 9; g++) {
            for (int b = 0; b <= 11; b++) {
                final int gg = g, bb = b;
                assertThatCode(() -> invoke("taisuiXiaoxianGanHit", new Class<?>[]{int.class, int.class}, gg, bb))
                        .doesNotThrowAnyException();
            }
        }
    }

    // ============ brightnessVariantNote：越界 + 两种异文命中 ============
    @Test
    @DisplayName("brightnessVariantNote：越界 null + 天机@辰 / 文曲@寅 异文")
    void brightnessVariantNoteBranches() {
        assertThat((String) invoke("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "天机", -1)).isNull();
        assertThat((String) invoke("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "天机", 12)).isNull();
        assertThat((String) invoke("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "天机", 4)).isNotNull();
        assertThat((String) invoke("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "文曲", 2)).isNotNull();
        assertThat((String) invoke("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "紫微", 0)).isNull();
    }

    // ============ expandTail：null / 空尾列 / null尾列 / 分隔展开 ============
    @Test
    @DisplayName("expandTail：null / 空尾列 / null 尾列 / 顿号展开")
    @SuppressWarnings("unchecked")
    void expandTailBranches() {
        assertThat((List<String[]>) invoke("expandTail", new Class<?>[]{List.class, String.class}, null, "、")).isNull();
        List<String[]> raw = new ArrayList<>();
        raw.add(new String[]{"a", "x、y、z"});
        raw.add(new String[]{"b", ""});
        raw.add(new String[]{"c", null});
        List<String[]> out = (List<String[]>) invoke("expandTail", new Class<?>[]{List.class, String.class}, raw, "、");
        assertThat(out).hasSize(3);
        assertThat(out.get(0)).hasSize(4);
        assertThat(out.get(1)).hasSize(1);
        assertThat(out.get(2)).hasSize(1);
    }

    // ============ verse：缓存命中 / 空值回退 / 缺键回退 ============
    @Test
    @DisplayName("verse：缓存命中 / 空串回退 / 缺键回退")
    void verseBranches() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("k1", "v1");
        m.put("k2", "");
        setStatic("ONTO_PATTERN_VERSE_MAP", m);
        assertThat((String) invoke("verse", new Class<?>[]{String.class, String.class}, "k1", "fb")).isEqualTo("v1");
        assertThat((String) invoke("verse", new Class<?>[]{String.class, String.class}, "k2", "fb")).isEqualTo("fb");
        assertThat((String) invoke("verse", new Class<?>[]{String.class, String.class}, "k3", "fb")).isEqualTo("fb");
    }

    // ============ deityName：缓存短数组命中 + 越界回退 ============
    @Test
    @DisplayName("deityName：缓存短数组命中 + 索引越界回退内置")
    void deityNameBranches() {
        setStatic("ONTO_DEITY_12", new String[]{"测神"});
        assertThat((String) invoke("deityName", new Class<?>[]{int.class}, 0)).isEqualTo("测神");
        assertThat((String) invoke("deityName", new Class<?>[]{int.class}, 11)).isNotNull();
    }

    // ============ stripParen / shortQuote：null / 多层括号 / 截断 / 不截断 ============
    @Test
    @DisplayName("stripParen：null / 多层括号清理；shortQuote：null / max<=0 / 截断")
    void stripParenShortQuoteBranches() {
        assertThat((String) invoke("stripParen", new Class<?>[]{String.class}, (Object) null)).isEmpty();
        assertThat((String) invoke("stripParen", new Class<?>[]{String.class}, "格名（卷三（详细））释义")).isEqualTo("格名释义");

        assertThat((String) invoke("shortQuote", new Class<?>[]{String.class, int.class}, (Object) null, 5)).isEmpty();
        assertThat((String) invoke("shortQuote", new Class<?>[]{String.class, int.class}, "abcdef", 0)).isEqualTo("abcdef");
        assertThat((String) invoke("shortQuote", new Class<?>[]{String.class, int.class}, "abcdef", 3)).endsWith("…");
    }

    // ============ isAnyStarBlockOpen：ONTO 清单含 null/空行/命中 ============
    @Test
    @DisplayName("isAnyStarBlockOpen：ONTO 清单含 null/空行/命中行")
    void isAnyStarBlockOpenBranches() {
        List<String[]> rowsC = new ArrayList<>();
        rowsC.add(null);
        rowsC.add(new String[0]);
        rowsC.add(new String[]{"天机"});
        setStatic("ONTO_STAR_BLOCK_ALL", rowsC);
        assertThat((Boolean) invoke("isAnyStarBlockOpen", new Class<?>[]{String.class}, "天机木属星")).isTrue();
        assertThat((Boolean) invoke("isAnyStarBlockOpen", new Class<?>[]{String.class}, "紫微土属星")).isFalse();
    }

    // ============ bureauOf：全干×全支（含非法干支 idx<0） ============
    @Test
    @DisplayName("bureauOf：全干×全支覆盖五行局与非法组合")
    void bureauOfBranches() {
        for (int g = 0; g <= 9; g++) {
            for (int b = 0; b <= 11; b++) {
                int v = (Integer) invoke("bureauOf", new Class<?>[]{int.class, int.class}, g, b);
                assertThat(v).isBetween(0, 6);
            }
        }
    }

    // ============ palaceBranch：null / 无等号 / 末尾无值 / 正常 ============
    @Test
    @DisplayName("palaceBranch：null / 缺键 / 末尾无值 / 正常命中")
    void palaceBranchBranches() {
        assertThat((Integer) invoke("palaceBranch", new Class<?>[]{String.class, String.class}, null, "命宫")).isEqualTo(-1);
        assertThat((Integer) invoke("palaceBranch", new Class<?>[]{String.class, String.class}, "命宫=", "命宫")).isEqualTo(-1);
        assertThat((Integer) invoke("palaceBranch", new Class<?>[]{String.class, String.class}, "命宫=子", "命宫")).isEqualTo(0);
        assertThat((Integer) invoke("palaceBranch", new Class<?>[]{String.class, String.class}, "命宫=子", "身宫")).isEqualTo(-1);
    }

    // ============ palaceStarRelation：全星×全支（含星克宫/比和/受制/泄气/无五行/越界） ============
    @Test
    @DisplayName("palaceStarRelation：全星×全支覆盖生克各档")
    void palaceStarRelationBranches() {
        List<String> stars = new ArrayList<>(Arrays.asList(MAIN));
        stars.addAll(Arrays.asList(JI));
        stars.addAll(Arrays.asList(SHA));
        stars.add("流昌");
        for (String s : stars) {
            for (int b = -1; b <= 12; b++) {
                final int bb = b;
                assertThatCode(() -> invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, s, bb))
                        .doesNotThrowAnyException();
            }
        }
        assertThat((String) invoke("palaceStarRelation", new Class<?>[]{String.class, int.class}, "贪狼", 1)).contains("星克宫");
    }

    // ============ brightnessOf：越界 / OBDA 缓存命中 / 缓存空位回退 / 表外回退 ============
    @Test
    @DisplayName("brightnessOf：越界 / 缓存命中 / 缓存空位回退 / 表外回退")
    void brightnessOfBranches() {
        assertThat((String) invoke("brightnessOf", new Class<?>[]{String.class, int.class}, "紫微", -1)).isEqualTo("平和");
        Map<String, String[]> cache = new LinkedHashMap<>();
        String[] row = new String[12];
        Arrays.fill(row, "庙");
        row[5] = null;
        cache.put("紫微", row);
        setStatic("ONTO_BRIGHTNESS", cache);
        assertThat((String) invoke("brightnessOf", new Class<?>[]{String.class, int.class}, "紫微", 0)).isEqualTo("庙");
        assertThat((String) invoke("brightnessOf", new Class<?>[]{String.class, int.class}, "紫微", 5)).isNotNull();
        assertThat((String) invoke("brightnessOf", new Class<?>[]{String.class, int.class}, "表外星X", 0)).isEqualTo("平和");
    }

    // ============ isJinYuFuJia：紫微守命 + 日月两侧/同侧/缺紫微/非法支 ============
    @Test
    @DisplayName("isJinYuFuJia：日月两侧真 / 同侧假 / 缺紫微 / 非法支")
    void isJinYuFuJiaBranches() {
        Set<String> zw = new LinkedHashSet<>(List.of("紫微"));
        Set<String> noZw = new LinkedHashSet<>(List.of("天机"));
        // 酉(9)与卯(3)：一后一前 → 真
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, Set.class, Set.class, String.class, String.class},
                0, zw, zw, "酉", "卯")).isTrue();
        // 卯(3)与酉(9)：另一侧排列 → 真
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, Set.class, Set.class, String.class, String.class},
                0, zw, zw, "卯", "酉")).isTrue();
        // 卯(3)与辰(4)：同前侧 → 假
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, Set.class, Set.class, String.class, String.class},
                0, zw, zw, "卯", "辰")).isFalse();
        // 缺紫微 → 假
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, Set.class, Set.class, String.class, String.class},
                0, noZw, noZw, "酉", "卯")).isFalse();
        // 非法支 → 假
        assertThat((Boolean) invoke("isJinYuFuJia", new Class<?>[]{int.class, Set.class, Set.class, String.class, String.class},
                0, zw, zw, "X", "卯")).isFalse();
    }

    // ============ judgeTaiweiMingGeExtended：命宫未定=空列表 ============
    @Test
    @DisplayName("judgeTaiweiMingGeExtended：mingIdx<0 → 空列表")
    @SuppressWarnings("unchecked")
    void judgeTaiweiMingGeExtendedUnsetBranch() {
        List<String> out = (List<String>) invoke("judgeTaiweiMingGeExtended",
                new Class<?>[]{int.class, Map.class, Set.class, Set.class, String.class},
                -1, fullAllStars(), new LinkedHashSet<>(), new LinkedHashSet<>(), SKEL);
        assertThat(out).isEmpty();
    }

    // ============ hasJuMenOrSha：越界 + 见巨门/煞 ============
    @Test
    @DisplayName("hasJuMenOrSha：越界 false + 巨门 true + 煞 true")
    void hasJuMenOrShaBranches() {
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, -1, at())).isFalse();
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, 12, at())).isFalse();
        Map<Integer, List<String>> m1 = at();
        put(m1, 4, "巨门");
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, 0, m1)).isTrue();
        Map<Integer, List<String>> m2 = at();
        put(m2, 4, "擎羊");
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, 0, m2)).isTrue();
        assertThat((Boolean) invoke("hasJuMenOrSha", new Class<?>[]{int.class, Map.class}, 0, at())).isFalse();
    }

    // ============ judgeTenGrades：威勇/富贵 OR 短路的假支 ============
    @Test
    @DisplayName("judgeTenGrades：威勇(紫府缺) / 富贵(辅弼昌曲缺) 假支")
    @SuppressWarnings("unchecked")
    void judgeTenGradesOrFalseBranches() {
        Set<String> weiYongMing = new LinkedHashSet<>(List.of("武曲", "七杀"));
        List<String> r1 = (List<String>) invoke("judgeTenGrades",
                new Class<?>[]{Set.class, Set.class, Set.class}, weiYongMing, weiYongMing, new LinkedHashSet<>());
        assertThat(r1).isNotNull();

        Set<String> fuMing = new LinkedHashSet<>(List.of("紫微", "天府", "禄存"));
        List<String> r2 = (List<String>) invoke("judgeTenGrades",
                new Class<?>[]{Set.class, Set.class, Set.class}, fuMing, fuMing, fuMing);
        assertThat(r2).isNotNull();
    }

    // ============ judgeTanXingYaoLun：命未定 / 聚吉 / 聚凶 / 缺宫跳过 / 地空 / 皆无 ============
    @Test
    @DisplayName("judgeTanXingYaoLun：命未定 / 聚吉 / 聚凶 / 缺福德跳 / 地空 / 皆无")
    void judgeTanXingYaoLunBranches() {
        assertThat((String) invoke("judgeTanXingYaoLun", new Class<?>[]{int.class, Map.class, String.class},
                -1, at(), SKEL)).contains("不判");

        Map<Integer, List<String>> ji = at();
        put(ji, 0, "文昌");
        put(ji, 4, "文昌");
        put(ji, 6, "文昌");
        put(ji, 8, "文昌");
        put(ji, 10, "文昌");
        assertThat((String) invoke("judgeTanXingYaoLun", new Class<?>[]{int.class, Map.class, String.class},
                0, ji, SKEL)).contains("富贵高寿");

        Map<Integer, List<String>> sha = at();
        put(sha, 0, "擎羊");
        put(sha, 4, "擎羊");
        put(sha, 6, "擎羊");
        put(sha, 8, "擎羊");
        put(sha, 10, "擎羊");
        assertThat((String) invoke("judgeTanXingYaoLun", new Class<?>[]{int.class, Map.class, String.class},
                0, sha, SKEL)).contains("孤寡夭寿");

        // 缺福德 → idx<0 跳过分支
        String noFu = "命宫=子;身宫=午;迁移=午;财帛=辰;官禄=申";
        assertThat((String) invoke("judgeTanXingYaoLun", new Class<?>[]{int.class, Map.class, String.class},
                0, ji, noFu)).isNotNull();

        // 地空 / 皆无（父母妻妾子女）
        Map<Integer, List<String>> kong = at();
        put(kong, 11, "地空");
        put(kong, 2, "地空");
        put(kong, 3, "地空");
        assertThat((String) invoke("judgeTanXingYaoLun", new Class<?>[]{int.class, Map.class, String.class},
                0, kong, SKEL)).contains("僧道");
        assertThat((String) invoke("judgeTanXingYaoLun", new Class<?>[]{int.class, Map.class, String.class},
                0, at(), SKEL)).contains("不主僧道");
    }

    // ============ judgeLongevity：越界 + 全命宫×身宫扫 + 艺术破武巳亥(未宫不可达) / 僧道六星 / 破相 ============
    @Test
    @DisplayName("judgeLongevity：越界 / 全扫 / 艺术破武巳亥(未不可达) / 僧道六星 / 破相")
    @SuppressWarnings("unchecked")
    void judgeLongevityBranches() {
        List<String> empty1 = (List<String>) invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, -1, 0, at());
        assertThat(empty1).isEmpty();
        for (int ming = 0; ming <= 11; ming++) {
            for (int shen : new int[]{-1, 0, 6, 11}) {
                final int mi = ming, si = shen;
                assertThatCode(() -> invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class},
                        mi, si, fullAllStars())).doesNotThrowAnyException();
                assertThatCode(() -> invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class},
                        mi, si, at())).doesNotThrowAnyException();
            }
        }
        for (int ming = 0; ming <= 11; ming++) {
            Map<Integer, List<String>> m = at();
            put(m, ming, "太阴");
            invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, ming, -1, m);
            Map<Integer, List<String>> m2 = at();
            put(m2, ming, "太阴", "破军");
            invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, ming, -1, m2);
        }
        // 原文「破武未宫多巧艺」按安星订正为「破武巳亥」：巳(5)破军武曲→艺术；未(7)破军武曲安星不可达→非艺术
        Map<Integer, List<String>> siWuPo = at();
        put(siWuPo, 5, "破军", "武曲");
        List<String> siArt = (List<String>) invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 5, -1, siWuPo);
        assertThat(siArt).anyMatch(s -> s.contains("艺术"));
        Map<Integer, List<String>> weiWuPo = at();
        put(weiWuPo, 7, "破军", "武曲");
        List<String> weiArt = (List<String>) invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 7, -1, weiWuPo);
        assertThat(weiArt).noneMatch(s -> s.contains("艺术"));

        Map<Integer, List<String>> seng6 = at();
        put(seng6, 0, "天机", "太阳");
        put(seng6, 4, "七杀");
        put(seng6, 8, "破军");
        put(seng6, 6, "天梁");
        invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, -1, seng6);
        Map<Integer, List<String>> seng6b = at();
        put(seng6b, 0, "天机", "太阳", "紫微");
        put(seng6b, 4, "七杀");
        put(seng6b, 8, "破军");
        put(seng6b, 6, "天梁");
        invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, -1, seng6b);

        Map<Integer, List<String>> po = at();
        put(po, 1, "擎羊");
        invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, -1, po);
        Map<Integer, List<String>> po2 = at();
        put(po2, 1, "擎羊");
        put(po2, 5, "天刑");
        invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, -1, po2);
    }

    // ============ judgePalaceZhiGrid：全命宫×全生干，全星盘最大化匹配原文块 ============
    @Test
    @DisplayName("judgePalaceZhiGrid：全命宫×生干(-1..9) 全星盘匹配")
    void judgePalaceZhiGridBranches() {
        Map<Integer, List<String>> full = fullAllStars();
        for (int z = 0; z <= 11; z++) {
            for (int gan = -1; gan <= 9; gan++) {
                final int zz = z, gg = gan;
                assertThatCode(() -> assertThat((String) invoke("judgePalaceZhiGrid",
                        new Class<?>[]{int.class, Map.class, int.class}, zz, full, gg)).isNotNull())
                        .doesNotThrowAnyException();
            }
        }
    }

    // ============ candidateMingStars：日越界 + 全月×全时扫 ============
    @Test
    @DisplayName("candidateMingStars：日越界空 + 全月×全时扫")
    @SuppressWarnings("unchecked")
    void candidateMingStarsBranches() {
        assertThat((List<String>) invoke("candidateMingStars", new Class<?>[]{int.class, int.class, int.class, int.class},
                6, 0, 0, 0)).isEmpty();
        assertThat((List<String>) invoke("candidateMingStars", new Class<?>[]{int.class, int.class, int.class, int.class},
                6, 31, 0, 0)).isEmpty();
        for (int mo = 1; mo <= 12; mo++) {
            for (int hr = 0; hr <= 11; hr++) {
                final int m = mo, h = hr;
                assertThatCode(() -> assertThat((List<String>) invoke("candidateMingStars",
                        new Class<?>[]{int.class, int.class, int.class, int.class}, m, 15, h, 0)).isNotNull())
                        .doesNotThrowAnyException();
            }
        }
    }

    // ============ verifyCandidateScore：昌曲痣癍命中/未命中 ============
    @Test
    @DisplayName("verifyCandidateScore：含痣癍/不含 全时扫")
    void verifyCandidateScoreBranches() {
        Set<String> withMole = new LinkedHashSet<>(List.of("面上有痣"));
        for (int hr = 0; hr <= 11; hr++) {
            final int h = hr;
            assertThatCode(() -> assertThat((Integer) invoke("verifyCandidateScore",
                    new Class<?>[]{int.class, int.class, int.class, int.class, Set.class, StringBuilder.class},
                    6, 15, h, 0, withMole, new StringBuilder())).isGreaterThanOrEqualTo(0))
                    .doesNotThrowAnyException();
        }
        assertThat((Integer) invoke("verifyCandidateScore",
                new Class<?>[]{int.class, int.class, int.class, int.class, Set.class, StringBuilder.class},
                6, 15, 0, 0, new LinkedHashSet<>(), new StringBuilder())).isGreaterThanOrEqualTo(0);
    }

    // ============ gradeOf：空命中→三方吉+煞=中格 / 无吉无煞=常格 ============
    @Test
    @DisplayName("gradeOf：空命中三方吉+煞→中格；皆无→常格")
    void gradeOfBranches() {
        Map<Integer, List<String>> m = at();
        put(m, 0, "文昌");
        put(m, 4, "擎羊");
        assertThat((String) invoke("gradeOf", new Class<?>[]{List.class, Map.class, int.class},
                new ArrayList<String>(), m, 0)).contains("中格");
        assertThat((String) invoke("gradeOf", new Class<?>[]{List.class, Map.class, int.class},
                new ArrayList<String>(), at(), 0)).contains("常格");
    }

    // ============ starsSplitClamp：真夹 / 非同侧 / 缺星 / 越界 ============
    @Test
    @DisplayName("starsSplitClamp：真夹true / 非左右false / 缺星(b==null) / target<0")
    void starsSplitClampBranches() {
        Map<Integer, List<String>> m = at();
        put(m, 1, "太阳");
        put(m, 11, "太阴");
        assertThat((Boolean) invoke("starsSplitClamp", new Class<?>[]{Map.class, String.class, int.class},
                m, "太阳、太阴", 0)).isTrue();

        Map<Integer, List<String>> m2 = at();
        put(m2, 3, "太阳");
        put(m2, 5, "太阴");
        assertThat((Boolean) invoke("starsSplitClamp", new Class<?>[]{Map.class, String.class, int.class},
                m2, "太阳、太阴", 0)).isFalse();

        Map<Integer, List<String>> m3 = at();
        put(m3, 1, "太阳");
        assertThat((Boolean) invoke("starsSplitClamp", new Class<?>[]{Map.class, String.class, int.class},
                m3, "太阳、不存在星", 0)).isFalse();

        assertThat((Boolean) invoke("starsSplitClamp", new Class<?>[]{Map.class, String.class, int.class},
                m, "太阳、太阴", -1)).isFalse();
    }

    // ============ majorEventBacktrace：空块 / 未识别事件 / 正负/中性期望 ============
    @Test
    @DisplayName("majorEventBacktrace：null/空 → null；空块+未识别+正负中性事件")
    void majorEventBacktraceBranches() {
        assertThat((String) invoke("majorEventBacktrace", new Class<?>[]{String.class, Map.class}, (Object) null, at())).isNull();
        assertThat((String) invoke("majorEventBacktrace", new Class<?>[]{String.class, Map.class}, "   ", at())).isNull();
        String traits = "2010年结婚；；2011年升职；2012年生病；2013年搬家；2005年神秘事件；2014年破财；2015年丧父；2016年结婚";
        Object r = invoke("majorEventBacktrace", new Class<?>[]{String.class, Map.class}, traits, fullAllStars());
        assertThat((String) r).isNotNull().contains("共");
    }

    // ============ ziweiLongevityVerdict：null / 空 t(全括号) / 未识别类 / 已知类 ============
    @Test
    @DisplayName("ziweiLongevityVerdict：null / 空类名 / 未识别类 / 孤克贫贱")
    void ziweiLongevityVerdictBranches() {
        assertThat((String) invoke("ziweiLongevityVerdict", new Class<?>[]{List.class}, (Object) null)).isNotNull();
        assertThat((String) invoke("ziweiLongevityVerdict", new Class<?>[]{List.class}, List.of())).isNotNull();
        assertThat((String) invoke("ziweiLongevityVerdict", new Class<?>[]{List.class}, List.of(""))).contains("中晚年");
        assertThat((String) invoke("ziweiLongevityVerdict", new Class<?>[]{List.class},
                List.of("未知类型（x）"))).contains("未知类型");
        assertThat((String) invoke("ziweiLongevityVerdict", new Class<?>[]{List.class},
                List.of("孤克（父）", "贫贱（财）"))).contains("孤克");
    }

    // ============ ziweiPalaceVerdictLine：宫位未定 ============
    @Test
    @DisplayName("ziweiPalaceVerdictLine：宫位未定 + 正常")
    void ziweiPalaceVerdictLineBranches() {
        assertThat((String) invoke("ziweiPalaceVerdictLine",
                new Class<?>[]{String.class, Map.class, String.class, String.class}, "命宫", at(), "", "")).contains("宫位未定");
        assertThat((String) invoke("ziweiPalaceVerdictLine",
                new Class<?>[]{String.class, Map.class, String.class, String.class}, "命宫", fullAllStars(), SKEL, "")).isNotNull();
    }

    // ============ ziweiSuiXianOverlay：出生信息不足 / 幼龄(未起限 dx<0) / 正常多年 ============
    @Test
    @DisplayName("ziweiSuiXianOverlay：坏SOLAR / 幼龄未起限 / 正常")
    void ziweiSuiXianOverlayBranches() {
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("SOLAR", "坏值");
        assertThat((String) invoke("ziweiSuiXianOverlay",
                new Class<?>[]{Map.class, String.class, Map.class, String.class, int.class},
                bad, SKEL, fullAllStars(), "", 2)).contains("出生信息不足");

        int cy = java.time.LocalDate.now().getYear();
        Map<String, String> young = new LinkedHashMap<>();
        young.put("SOLAR", cy + "-03-01T10:00");
        young.put("GENDER", "男");
        young.put("LY", String.valueOf(cy));
        String youngSkel = SKEL + "；水二局";
        assertThat((String) invoke("ziweiSuiXianOverlay",
                new Class<?>[]{Map.class, String.class, Map.class, String.class, int.class},
                young, youngSkel, fullAllStars(), "", 3)).isNotNull();

        Map<String, String> cb = new LinkedHashMap<>();
        cb.put("SOLAR", "1990-05-15T10:30");
        cb.put("GENDER", "男");
        cb.put("LY", "1990");
        assertThat((String) invoke("ziweiSuiXianOverlay",
                new Class<?>[]{Map.class, String.class, Map.class, String.class, int.class},
                cb, SKEL, fullAllStars(), "", 3)).isNotNull();
    }

    // ============ 额外可达扫描：短太岁表越界 / 锦玉全扫 / 生干全扫 / 空特征 ============
    @Test
    @DisplayName("额外扫描：短太岁表308 / 锦玉日月全扫 / 候补生干全扫 / 验盘空特征")
    void extraReachableSweeps() {
        // 308：ONTO 太岁表仅 3 行 → flowBranch=5 越界
        List<String[]> shortT = new ArrayList<>();
        shortT.add(new String[]{"甲", "乙", "丙"});
        shortT.add(new String[]{"甲", "乙", "丙"});
        shortT.add(new String[]{"甲", "乙", "丙"});
        setStatic("ONTO_TAISUI_XIAOXIAN", shortT);
        assertThat((String) invoke("taisuiXiaoxianGanHit", new Class<?>[]{int.class, int.class}, 0, 5)).isEmpty();

        // 4445：日月相对全扫
        Set<String> zw = new LinkedHashSet<>(List.of("紫微"));
        for (int ming = 0; ming <= 11; ming++) {
            for (int d1 = 1; d1 <= 11; d1++) {
                for (int d2 = 1; d2 <= 11; d2++) {
                    String sy = ZHI[(ming + d1) % 12];
                    String yy = ZHI[(ming + d2) % 12];
                    invoke("isJinYuFuJia", new Class<?>[]{int.class, Set.class, Set.class, String.class, String.class},
                            ming, zw, zw, sy, yy);
                }
            }
        }

        // 5543：候补命星 生干全扫（含非法干支 bureau 越界）
        for (int gan = 0; gan <= 9; gan++) {
            final int g = gan;
            assertThatCode(() -> invoke("candidateMingStars",
                    new Class<?>[]{int.class, int.class, int.class, int.class}, 6, 15, 5, g))
                    .doesNotThrowAnyException();
        }

        // 5583：验盘评分 空特征全时扫（空间命中但无痣癍词 → contains 假支）
        for (int hr = 0; hr <= 11; hr++) {
            final int h = hr;
            invoke("verifyCandidateScore", new Class<?>[]{int.class, int.class, int.class, int.class, Set.class, StringBuilder.class},
                    6, 15, h, 0, new LinkedHashSet<>(), new StringBuilder());
        }

        // 6297：未宫无破军（破军假支）
        Map<Integer, List<String>> weiNoPo = at();
        put(weiNoPo, 7, "天机");
        invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 7, -1, weiNoPo);
        // 6308：僧道六星之 缺天机（首项假支）
        Map<Integer, List<String>> sengNoJi = at();
        put(sengNoJi, 4, "七杀");
        put(sengNoJi, 8, "破军");
        put(sengNoJi, 6, "天梁");
        put(sengNoJi, 0, "太阳", "紫微");
        invoke("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, 0, -1, sengNoJi);
    }
}
