package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定贵局·金舆扶驾 判据自检（离线，无需 MySQL/Ontop/Camunda）。
 *
 * <p>锁定本轮修正：《全書》卷一·定贵局「紫微守命前后有日月来夹是也」之「前后」＝紫微前后两侧分居，
 * 非「紧邻(±1)」。太阳恒在紫微逆三宫（后方 −3），故只须太阴居紫微前方 1~5 宫即成格（可达）。
 * 旧实现误读为紧邻(±1)并改以「帝坐金车」释读替代，已改正。
 *
 * <p>地支序：子=0 丑=1 寅=2 卯=3 辰=4 巳=5 午=6 未=7 申=8 酉=9 戌=10 亥=11。
 */
@DisplayName("定贵局·金舆扶驾（紫微守命，太阳太阴分居前后两侧）判据自检")
class ZiWeiDouShuJinYuFuJiaTest {

    private static boolean hit(int mingIdx, Set<String> mingMain, Set<String> mingSet, String sun, String moon) {
        return ZiWeiDouShuOntologyJobWorker.isJinYuFuJia(mingIdx, mingMain, mingSet, sun, moon);
    }

    @Test
    @DisplayName("紫微守命在子：太阳酉(−3)、太阴巳(+5) → 分居前后两侧，成格（用户示例命位）")
    void ziweiZhiZi() {
        assertThat(hit(0, Set.of("紫微"), Set.of("紫微"), "酉", "巳")).isTrue();
    }

    @Test
    @DisplayName("紫微守命在午：太阳卯(−3)、太阴亥(+5) → 成格")
    void ziweiZhiWu() {
        assertThat(hit(6, Set.of("紫微"), Set.of("紫微"), "卯", "亥")).isTrue();
    }

    @Test
    @DisplayName("紫微守命在丑：太阳戌(−3)、太阴辰(+3) → 成格")
    void ziweiZhiChou() {
        assertThat(hit(1, Set.of("紫微"), Set.of("紫微"), "戌", "辰")).isTrue();
    }

    @Test
    @DisplayName("紫微守命在申：太阳巳(−3)、太阴酉(+1) → 成格")
    void ziweiZhiShen() {
        assertThat(hit(8, Set.of("紫微"), Set.of("紫微"), "巳", "酉")).isTrue();
    }

    @Test
    @DisplayName("反例·紫微守命在辰：太阳太阴俱在丑(同在后方 −3) → 不成格（1989-05-15 02:45 旧命例实盘）")
    void ziweiZhiChen_bothBehind() {
        assertThat(hit(4, Set.of("紫微", "天相"), Set.of("紫微", "天相"), "丑", "丑")).isFalse();
    }

    @Test
    @DisplayName("反例·太阳在后(−3)、太阴亦在后(未+7)，未在前 1~5 → 不成格")
    void moonNotAhead() {
        assertThat(hit(0, Set.of("紫微"), Set.of("紫微"), "酉", "未")).isFalse();
    }

    @Test
    @DisplayName("反例·紫微不守命 → 不成格")
    void notZiweiShouMing() {
        assertThat(hit(0, Set.of("天机"), Set.of("天机"), "酉", "巳")).isFalse();
    }

    @Test
    @DisplayName("守界·太阴缺失（null）→ 不成格")
    void missingMoon() {
        assertThat(hit(0, Set.of("紫微"), Set.of("紫微"), "酉", null)).isFalse();
    }

    @Test
    @DisplayName("守界·命宫序越界 → 不成格")
    void outOfRange() {
        assertThat(hit(12, Set.of("紫微"), Set.of("紫微"), "酉", "巳")).isFalse();
        assertThat(hit(-1, Set.of("紫微"), Set.of("紫微"), "酉", "巳")).isFalse();
    }

    @Test
    @DisplayName("化简后边界·sr/yr 上界=11 仍属后方(>=7)、同一侧不成格、中位6两侧皆非")
    void boundaryAfterSimplify() {
        // sr=11(后)、yr=1(前) → 成格（验证去掉 <=11 后上界=11 仍判定为后方）
        assertThat(hit(0, Set.of("紫微"), Set.of("紫微"), "亥", "丑")).isTrue();
        // sr=1(前)、yr=11(后) → 成格（第二子句）
        assertThat(hit(0, Set.of("紫微"), Set.of("紫微"), "丑", "亥")).isTrue();
        // 两侧皆后(sr=11,yr=11) → 不成格
        assertThat(hit(0, Set.of("紫微"), Set.of("紫微"), "亥", "亥")).isFalse();
        // sr=6（既非 7~11 亦非 1~5）→ 不成格
        assertThat(hit(0, Set.of("紫微"), Set.of("紫微"), "午", "子")).isFalse();
    }

    @Test
    @DisplayName("全组合遍历·sr/yr 各 0..11（覆盖所有布尔操作数侧，并证明化简前后等价）")
    void exhaustiveSides() {
        String[] z = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
        for (int sr = 0; sr <= 11; sr++) {
            for (int yr = 0; yr <= 11; yr++) {
                // 化简后的期望式（floorMod 恒 0..11，故无 sr/yr<=11 上界）
                boolean expect = ((sr >= 7) && (yr >= 1 && yr <= 5))
                        || ((sr >= 1 && sr <= 5) && (yr >= 7));
                // 命子(0)：太阳@z[sr](sr)、太阴@z[yr](yr)
                assertThat(hit(0, Set.of("紫微"), Set.of("紫微"), z[sr], z[yr]))
                        .as("sr=%d yr=%d", sr, yr).isEqualTo(expect);
            }
        }
    }

    @Test
    @DisplayName("紫微仅在 mingSet（mingMain 空）→ 仍判紫微守命（覆盖 ziweiShouMing 第二操作数真侧）")
    void ziweiOnlyInMingSet() {
        assertThat(hit(0, Set.of(), Set.of("紫微"), "酉", "巳")).isTrue();
    }
}
