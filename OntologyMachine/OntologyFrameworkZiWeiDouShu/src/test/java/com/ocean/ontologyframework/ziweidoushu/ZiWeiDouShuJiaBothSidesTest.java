package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「二星来夹」真夹判据自检（离线）：星 A、星 B 须分居命宫前后两侧（一在 ming−1、一在 ming+1），
 * 排除「二星同处一侧邻宫（同宫）」的凑数。用于 日月夹财/日月夹命/财禄夹马/财印夹禄。
 *
 * <p>地支序：子=0 丑=1 寅=2 卯=3 辰=4 巳=5 午=6 未=7 申=8 酉=9 戌=10 亥=11。
 */
@DisplayName("真夹判据：二星分居命宫两侧（排除同宫凑数）")
class ZiWeiDouShuJiaBothSidesTest {

    private static Map<Integer, List<String>> at(Object... pairs) {
        Map<Integer, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            int pos = (Integer) pairs[i];
            String star = (String) pairs[i + 1];
            m.computeIfAbsent(pos, k -> new ArrayList<>()).add(star);
        }
        return m;
    }

    private static boolean both(int mingIdx, Map<Integer, List<String>> at, String a, String b) {
        return ZiWeiDouShuOntologyJobWorker.isJiaBothSides(mingIdx, at, a, b);
    }

    @Test
    @DisplayName("命宫在丑：太阳在寅(+1)、太阴在子(−1) → 真夹")
    void genuine() {
        assertThat(both(1, at(0, "太阴", 2, "太阳"), "太阳", "太阴")).isTrue();
    }

    @Test
    @DisplayName("命宫在子：太阳亥(−1)、太阴丑(+1)（次序无关）→ 真夹")
    void genuineReversed() {
        assertThat(both(0, at(11, "太阳", 1, "太阴"), "太阳", "太阴")).isTrue();
    }

    @Test
    @DisplayName("命宫在丑：太阳、太阴同在寅(+1)（同宫凑数）→ 非真夹")
    void sameCell() {
        assertThat(both(1, at(2, "太阳", 2, "太阴"), "太阳", "太阴")).isFalse();
    }

    @Test
    @DisplayName("命宫在丑：太阳、太阴同在子(−1)（同宫凑数）→ 非真夹")
    void sameCellOtherSide() {
        assertThat(both(1, at(0, "太阳", 0, "太阴"), "太阳", "太阴")).isFalse();
    }

    @Test
    @DisplayName("缺一星（太阴不在盘）→ 非真夹")
    void missing() {
        assertThat(both(1, at(2, "太阳"), "太阳", "太阴")).isFalse();
    }

    @Test
    @DisplayName("命宫序越界 → 非真夹")
    void outOfRange() {
        assertThat(both(12, at(0, "太阴", 2, "太阳"), "太阳", "太阴")).isFalse();
        assertThat(both(-1, at(0, "太阴", 2, "太阳"), "太阳", "太阴")).isFalse();
    }
}
