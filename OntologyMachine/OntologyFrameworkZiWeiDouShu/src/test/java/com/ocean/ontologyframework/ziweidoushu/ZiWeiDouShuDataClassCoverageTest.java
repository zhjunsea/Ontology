package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 JobWorker 各「数据分类」判定方法的穷举覆盖（离线·反射，无需环境）。
 *
 * <p>针对重点关注的"各种数据分类"——庙陷 7 级、五行生克 5 类、星曜五行、五行局、
 * 命格四档、三方四正吉煞 4 态、运限吉凶 3 态、卷二↔卷三庙陷异文、落宫时辰——逐一按取值域穷举，
 * 断言每个分类的所有取值都能被判定方法产出（而非只覆盖正常盘偶然命中的少数类别）。
 */
@DisplayName("紫微斗数数据分类穷举：庙陷/生克/五行/命格/三方/运限/异文/时辰")
class ZiWeiDouShuDataClassCoverageTest {

    private static final String[] STARS = {
            "紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府", "太阴", "贪狼", "巨门",
            "天相", "天梁", "七杀", "破军", "文昌", "文曲", "禄存", "擎羊", "陀罗", "火星", "铃星"};

    private static Object call(String name, Class<?>[] sig, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("反射调用失败: " + name, e);
        }
    }

    @Test
    @DisplayName("brightnessOf：21 星 × 12 宫穷举，产出全部 7 个庙陷等第，越界/未知星兜底平和")
    void brightnessExhaustive() {
        Set<String> levels = new LinkedHashSet<>();
        for (String star : STARS) {
            for (int b = 0; b < 12; b++) {
                String lv = (String) call("brightnessOf", new Class<?>[]{String.class, int.class}, star, b);
                assertThat(lv).as("%s@%d", star, b).isNotBlank();
                levels.add(lv);
            }
        }
        assertThat(levels).as("应覆盖全部庙陷等第").containsExactlyInAnyOrder(
                "庙", "旺", "得地", "利益", "平和", "不得地", "陷");
        assertThat((String) call("brightnessOf", new Class<?>[]{String.class, int.class}, "紫微", -1)).isEqualTo("平和");
        assertThat((String) call("brightnessOf", new Class<?>[]{String.class, int.class}, "紫微", 12)).isEqualTo("平和");
        assertThat((String) call("brightnessOf", new Class<?>[]{String.class, int.class}, "不存在星", 0)).isEqualTo("平和");
    }

    @Test
    @DisplayName("isFallen：与庙陷等第一致（陷/不得地为 true，其余 false）")
    void isFallenConsistency() {
        for (String star : STARS) {
            for (int b = 0; b < 12; b++) {
                String lv = (String) call("brightnessOf", new Class<?>[]{String.class, int.class}, star, b);
                boolean fallen = (Boolean) call("isFallen", new Class<?>[]{String.class, int.class}, star, b);
                boolean expected = "陷".equals(lv) || "不得地".equals(lv);
                assertThat(fallen).as("%s@%d 等第=%s", star, b, lv).isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("palaceStarRelation：生克 5 类（比和/宫生星/宫克星/星生宫/星克宫）穷举全覆盖，非星返回 null")
    void shengKeAllFiveClasses() {
        Set<String> cats = new LinkedHashSet<>();
        for (String star : STARS) {
            for (int b = 0; b < 12; b++) {
                String r = (String) call("palaceStarRelation", new Class<?>[]{String.class, int.class}, star, b);
                assertThat(r).as("%s@%d", star, b).isNotNull();
                if (r.contains("比和")) cats.add("比和");
                else if (r.contains("宫生星")) cats.add("宫生星");
                else if (r.contains("宫克星")) cats.add("宫克星");
                else if (r.contains("星生宫")) cats.add("星生宫");
                else if (r.contains("星克宫")) cats.add("星克宫");
            }
        }
        assertThat(cats).as("应覆盖五行生克全部 5 类关系").containsExactlyInAnyOrder(
                "比和", "宫生星", "宫克星", "星生宫", "星克宫");
        assertThat((String) call("palaceStarRelation", new Class<?>[]{String.class, int.class}, "不存在星", 0)).isNull();
    }

    @Test
    @DisplayName("starElement：五行 0~4 全命中 + 未知星 -1")
    void starElementAllFive() {
        assertThat((Integer) call("starElement", new Class<?>[]{String.class}, "紫微")).isEqualTo(2); // 土
        assertThat((Integer) call("starElement", new Class<?>[]{String.class}, "天机")).isEqualTo(0); // 木
        assertThat((Integer) call("starElement", new Class<?>[]{String.class}, "太阳")).isEqualTo(1); // 火
        assertThat((Integer) call("starElement", new Class<?>[]{String.class}, "武曲")).isEqualTo(3); // 金
        assertThat((Integer) call("starElement", new Class<?>[]{String.class}, "天同")).isEqualTo(4); // 水
        assertThat((Integer) call("starElement", new Class<?>[]{String.class}, "不存在星")).isEqualTo(-1);
    }

    @Test
    @DisplayName("bureauName：水二/木三/金四/土五/火六 + 未定兜底")
    void bureauNameAll() {
        assertThat((String) call("bureauName", new Class<?>[]{int.class}, 2)).isEqualTo("水二局");
        assertThat((String) call("bureauName", new Class<?>[]{int.class}, 3)).isEqualTo("木三局");
        assertThat((String) call("bureauName", new Class<?>[]{int.class}, 4)).isEqualTo("金四局");
        assertThat((String) call("bureauName", new Class<?>[]{int.class}, 5)).isEqualTo("土五局");
        assertThat((String) call("bureauName", new Class<?>[]{int.class}, 6)).isEqualTo("火六局");
        assertThat((String) call("bureauName", new Class<?>[]{int.class}, 1)).contains("待考");
        assertThat((String) call("bureauName", new Class<?>[]{int.class}, 7)).contains("待考");
    }

    @Test
    @DisplayName("bureauFromSkeleton：五种局名可回读 + 无关/null 兜底 0")
    void bureauFromSkeletonAll() {
        for (int b = 2; b <= 6; b++) {
            String name = (String) call("bureauName", new Class<?>[]{int.class}, b);
            assertThat((Integer) call("bureauFromSkeleton", new Class<?>[]{String.class}, "前缀" + name + "后缀")).isEqualTo(b);
        }
        assertThat((Integer) call("bureauFromSkeleton", new Class<?>[]{String.class}, "无关文本")).isZero();
        assertThat((Integer) call("bureauFromSkeleton", new Class<?>[]{String.class}, new Object[]{null})).isZero();
    }

    @Test
    @DisplayName("ziweiGradeTier：上/中上/中/下/常 五档 + null/空白/未知 兜底")
    void gradeTierAll() {
        assertThat((String) call("ziweiGradeTier", new Class<?>[]{String.class}, "中上格（依据）")).isEqualTo("中上格");
        assertThat((String) call("ziweiGradeTier", new Class<?>[]{String.class}, "上格·富贵")).isEqualTo("上格");
        assertThat((String) call("ziweiGradeTier", new Class<?>[]{String.class}, "下格")).isEqualTo("下格");
        assertThat((String) call("ziweiGradeTier", new Class<?>[]{String.class}, "中格")).isEqualTo("中格");
        assertThat((String) call("ziweiGradeTier", new Class<?>[]{String.class}, "常格")).isEqualTo("常格");
        assertThat((String) call("ziweiGradeTier", new Class<?>[]{String.class}, new Object[]{null})).isEqualTo("常格");
        assertThat((String) call("ziweiGradeTier", new Class<?>[]{String.class}, "")).isEqualTo("常格");
        assertThat((String) call("ziweiGradeTier", new Class<?>[]{String.class}, "乱码")).isEqualTo("中格");
    }

    @Test
    @DisplayName("ziweiSanFangDesc：会吉无煞/会吉亦见煞/无吉而见煞/无吉无煞 四态")
    void sanFangDescAll() {
        assertThat((String) call("ziweiSanFangDesc", new Class<?>[]{boolean.class, int.class}, true, 0)).isEqualTo("会吉无煞");
        assertThat((String) call("ziweiSanFangDesc", new Class<?>[]{boolean.class, int.class}, true, 2)).startsWith("会吉亦见煞");
        assertThat((String) call("ziweiSanFangDesc", new Class<?>[]{boolean.class, int.class}, false, 3)).isEqualTo("无吉而见煞3");
        assertThat((String) call("ziweiSanFangDesc", new Class<?>[]{boolean.class, int.class}, false, 0)).isEqualTo("无吉无煞");
    }

    @Test
    @DisplayName("ziweiLimitMeaning：吉/凶/平（其余）三态")
    void limitMeaningAll() {
        assertThat((String) call("ziweiLimitMeaning", new Class<?>[]{String.class}, "吉")).contains("顺遂");
        assertThat((String) call("ziweiLimitMeaning", new Class<?>[]{String.class}, "凶")).contains("多阻");
        assertThat((String) call("ziweiLimitMeaning", new Class<?>[]{String.class}, "平")).contains("平稳");
    }

    @Test
    @DisplayName("brightnessVariantNote：卷二↔卷三异文命中（天机@辰/文曲@寅）+ 无异文/越界 null")
    void brightnessVariantNoteAll() {
        String tianJiChen = (String) call("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "天机", 4); // 辰
        assertThat(tianJiChen).isNotNull().contains("卷二").contains("卷三");
        String wenQuYin = (String) call("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "文曲", 2); // 寅
        assertThat(wenQuYin).isNotNull().contains("卷二").contains("卷三");
        assertThat((String) call("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "紫微", 0)).isNull();
        assertThat((String) call("brightnessVariantNote", new Class<?>[]{String.class, int.class}, "天机", 12)).isNull();
    }

    @Test
    @DisplayName("hourBranchOf：23–1 子、1–3 丑… 全 24 时辰落在 0~11 且边界正确")
    void hourBranchAll() {
        for (int h = 0; h < 24; h++) {
            int b = (Integer) call("hourBranchOf", new Class<?>[]{int.class}, h);
            assertThat(b).as("hour=%d", h).isBetween(0, 11);
        }
        assertThat((Integer) call("hourBranchOf", new Class<?>[]{int.class}, 0)).isZero();
        assertThat((Integer) call("hourBranchOf", new Class<?>[]{int.class}, 23)).isZero();
        assertThat((Integer) call("hourBranchOf", new Class<?>[]{int.class}, 1)).isEqualTo(1);
        assertThat((Integer) call("hourBranchOf", new Class<?>[]{int.class}, 12)).isEqualTo(6);
    }

    @Test
    @DisplayName("sihuaStars：生年四化 ×10 干穷举，每干返回禄/权/科/忌 4 星且非空")
    void sihuaAllTenStems() {
        String[] expectedJia = {"廉贞", "破军", "武曲", "太阳"};
        for (int gan = 0; gan < 10; gan++) {
            String[] r = (String[]) call("sihuaStars", new Class<?>[]{int.class}, gan);
            assertThat(r).as("年干索引 %d 四化", gan).hasSize(4);
            for (String star : r) {
                assertThat(star).as("年干 %d 四化星", gan).isNotBlank();
            }
        }
        assertThat((String[]) call("sihuaStars", new Class<?>[]{int.class}, 0)).containsExactly(expectedJia);
    }

    @Test
    @DisplayName("bureauOf：纳音五行 → 五行局，穷举 60 甲子覆盖 水二/木三/金四/土五/火六 全部 5 类")
    void bureauOfAllFiveViaNayin() {
        Set<Integer> bureaus = new LinkedHashSet<>();
        for (int g = 0; g < 10; g++) {
            for (int b = 0; b < 12; b++) {
                int bureau = (Integer) call("bureauOf", new Class<?>[]{int.class, int.class}, g, b);
                if (bureau != 0) {
                    bureaus.add(bureau);
                }
            }
        }
        assertThat(bureaus).as("应覆盖全部 5 种五行局").containsExactlyInAnyOrder(2, 3, 4, 5, 6);
        // 非法干支组合（阴阳不配）走 idx<0 → 返回 0
        assertThat((Integer) call("bureauOf", new Class<?>[]{int.class, int.class}, 0, 1)).isZero();
    }
}
