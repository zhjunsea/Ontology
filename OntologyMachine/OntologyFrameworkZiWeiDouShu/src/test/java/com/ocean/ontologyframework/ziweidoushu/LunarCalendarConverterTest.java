package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 农历↔公历换算器 {@link LunarCalendarConverter} 单元测试（离线，无需任何外部环境）。
 *
 * <p>补齐原测试缺口：此前该换算器仅有外部 Python {@code lunardate} 对拍，缺少自动化回归。
 * 本类覆盖：已知干支/春节锚点、闰月往返、大范围公历→农历→公历往返（含闰月分支）、
 * 越界与非法入参 null 分支、月/年天数工具。
 */
@DisplayName("农历公历换算器：锚点/闰月/往返/越界边界")
class LunarCalendarConverterTest {

    private static void assertLunar(int y, int m, int d, int ly, int lm, int ld, int leap) {
        int[] r = LunarCalendarConverter.solarToLunar(y, m, d);
        assertThat(r).as("solarToLunar(%d-%d-%d) 不应为 null", y, m, d).isNotNull();
        assertThat(new int[]{r[0], r[1], r[2], r[3]})
                .as("solarToLunar(%d-%d-%d)", y, m, d)
                .containsExactly(ly, lm, ld, leap);
    }

    @Test
    @DisplayName("锚点：1900-01-31 为农历 1900 正月初一（基准年）")
    void baseAnchor() {
        assertLunar(1900, 1, 31, 1900, 1, 1, 0);
    }

    @Test
    @DisplayName("锚点：多个春节（正月初一）公历日期")
    void chineseNewYearAnchors() {
        assertLunar(1990, 1, 27, 1990, 1, 1, 0);
        assertLunar(2000, 2, 5, 2000, 1, 1, 0);
        assertLunar(2010, 2, 14, 2010, 1, 1, 0);
        assertLunar(2020, 1, 25, 2020, 1, 1, 0);
    }

    @Test
    @DisplayName("闰月：闰月年 leapMonth>0 且 leapDays∈{29,30}；无闰年月 leapMonth/leapDays 为 0")
    void leapMonthMeta() {
        int[] leapYears = {2001, 2004, 2006, 2009, 2012, 2014, 2017, 2020};
        for (int y : leapYears) {
            assertThat(LunarCalendarConverter.leapMonth(y)).as("%d 应有闰月", y).isGreaterThan(0);
            assertThat(LunarCalendarConverter.leapDays(y)).isBetween(29, 30);
        }
        int[] commonYears = {2019, 2021, 2022};
        for (int y : commonYears) {
            assertThat(LunarCalendarConverter.leapMonth(y)).as("%d 应无闰月", y).isZero();
            assertThat(LunarCalendarConverter.leapDays(y)).isZero();
        }
    }

    @Test
    @DisplayName("闰月往返：闰月初一 农历→公历→农历 保持一致（isLeap=true 分支）")
    void leapMonthRoundTrip() {
        int[] leapYears = {2001, 2004, 2012, 2014, 2017, 2020};
        for (int y : leapYears) {
            int lm = LunarCalendarConverter.leapMonth(y);
            LocalDate solar = LunarCalendarConverter.lunarToSolar(y, lm, 1, true);
            assertThat(solar).as("闰月 lunarToSolar(%d,闰%d,1,true)", y, lm).isNotNull();
            int[] back = LunarCalendarConverter.solarToLunar(solar.getYear(), solar.getMonthValue(), solar.getDayOfMonth());
            assertThat(back[0]).isEqualTo(y);
            assertThat(back[1]).isEqualTo(lm);
            assertThat(back[2]).isEqualTo(1);
            assertThat(back[3]).as("闰月标志应为 1（y=%d）", y).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("大范围往返：公历→农历→公历 恒等（覆盖 1900–2099 及闰月路径）")
    void broadRoundTrip() {
        for (int y = 1901; y <= 2098; y += 11) {
            for (int m : new int[]{1, 4, 7, 10}) {
                for (int d : new int[]{1, 15, 28}) {
                    if (!LocalDate.of(y, m, d).isBefore(LocalDate.of(2099, 12, 1))) {
                        continue;
                    }
                    int[] lunar = LunarCalendarConverter.solarToLunar(y, m, d);
                    assertThat(lunar).as("solarToLunar(%d-%d-%d)", y, m, d).isNotNull();
                    LocalDate back = LunarCalendarConverter.lunarToSolar(lunar[0], lunar[1], lunar[2], lunar[3] == 1);
                    assertThat(back).as("往返 %d-%d-%d -> 农%d-%d-%d(闰%d)", y, m, d, lunar[0], lunar[1], lunar[2], lunar[3])
                            .isEqualTo(LocalDate.of(y, m, d));
                }
            }
        }
    }

    @Test
    @DisplayName("越界/非法入参 → null")
    void outOfRangeNullOrInvalid() {
        assertThat(LunarCalendarConverter.solarToLunar(1899, 12, 31)).isNull();
        assertThat(LunarCalendarConverter.solarToLunar(2100, 1, 1)).isNull();
        assertThat(LunarCalendarConverter.lunarToSolar(1899, 1, 1, false)).isNull();
        assertThat(LunarCalendarConverter.lunarToSolar(2100, 1, 1, false)).isNull();
        assertThat(LunarCalendarConverter.lunarToSolar(2000, 0, 1, false)).isNull();
        assertThat(LunarCalendarConverter.lunarToSolar(2000, 13, 1, false)).isNull();
    }

    @Test
    @DisplayName("月/年天数工具：monthDays∈{29,30}，yearDays∈[353,385]")
    void monthAndYearDays() {
        for (int y = 1900; y <= 2099; y += 37) {
            int yd = LunarCalendarConverter.yearDays(y);
            assertThat(yd).as("%d 年总天数", y).isBetween(353, 385);
            for (int m = 1; m <= 12; m++) {
                assertThat(LunarCalendarConverter.monthDays(y, m)).as("%d-%d 月天数", y, m).isBetween(29, 30);
            }
        }
    }
}
