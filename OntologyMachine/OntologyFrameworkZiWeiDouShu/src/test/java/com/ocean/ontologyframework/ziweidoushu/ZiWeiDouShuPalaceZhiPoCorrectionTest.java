package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * palace_zhi_po 失陷破格「星-支」校订核验（离线，无引擎/无 OBDA）。
 *
 * <p>据《全書》卷二庙陷表（c3 L408-457）：天机@子=庙、@午=庙，天机实陷于丑/未；铃星@子=落陷。
 * 原内置表把「子→天机」致恒假（天机@子=庙永不落陷）→ 假死支；今按庙陷表校订：子→铃星、丑/未补天机。
 * 本测试锁定校订后：命丑/未见天机(落陷)→破格可达；命子见铃星(落陷)→破格可达；并反例校验天机@子=庙不误判破格。
 *
 * <p>地支序：子=0 丑=1 寅=2 卯=3 辰=4 巳=5 午=6 未=7 申=8 酉=9 戌=10 亥=11。
 */
@DisplayName("palace_zhi_po 失陷破格 星-支校订核验（天机落陷在丑/未；子取铃星）")
class ZiWeiDouShuPalaceZhiPoCorrectionTest {

    private static String judge(int mingIdx, Map<Integer, List<String>> at) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class
                    .getDeclaredMethod("judgePalaceZhiGrid", int.class, Map.class, int.class);
            m.setAccessible(true);
            return (String) m.invoke(null, mingIdx, at, -1);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Integer, List<String>> at(int idx, String... stars) {
        Map<Integer, List<String>> m = new HashMap<>();
        m.put(idx, new ArrayList<>(Arrays.asList(stars)));
        return m;
    }

    @Test
    @DisplayName("命丑见天机（天机@丑=落陷）→ 命中「失陷破格」")
    void chouTianji() {
        assertThat(judge(1, at(1, "天机"))).contains("失陷破格");
    }

    @Test
    @DisplayName("命未见天机（天机@未=落陷）→ 命中「失陷破格」")
    void weiTianji() {
        assertThat(judge(7, at(7, "天机"))).contains("失陷破格");
    }

    @Test
    @DisplayName("命子见铃星（铃星@子=落陷）→ 命中「失陷破格」（原假死支已可达）")
    void ziLingxingReachable() {
        assertThat(judge(0, at(0, "铃星"))).contains("失陷破格");
    }

    @Test
    @DisplayName("反例·命子见天机（天机@子=庙，非落陷）→ 不命中失陷破格（庙旺不破，正确）")
    void ziTianjiNotBroken() {
        assertThat(judge(0, at(0, "天机"))).doesNotContain("失陷破格");
    }

    @Test
    @DisplayName("回归·命丑见巨门（巨门@丑=不得地≈落陷）→ 命中「失陷破格」")
    void chouJumenRegression() {
        assertThat(judge(1, at(1, "巨门"))).contains("失陷破格");
    }
}
