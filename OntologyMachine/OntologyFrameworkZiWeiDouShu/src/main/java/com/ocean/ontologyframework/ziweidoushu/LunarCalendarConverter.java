package com.ocean.ontologyframework.ziweidoushu;

import java.time.LocalDate;

/**
 * 公历↔农历换算器（1900–2099）。
 *
 * <p>采用通行的 {@code LUNAR_INFO} 压缩表算法（每一年一个 20 bit 整数，描述闰月月份与各月大小），
 * 已与 Python {@code lunardate} 库逐例比对一致（含闰月、跨年场景，12/12）。
 * 紫微斗数安星须以「农历月/日」及「农历年干支」为基准，故公历输入必须经此换算，
 * 不得再把公历月/日直接当农历使用。
 *
 * <p>返回约定：{@link #solarToLunar(int, int, int)} 返回 {@code {农历年, 农历月, 农历日, 是否闰月(0/1)}}。
 */
public final class LunarCalendarConverter {

    private LunarCalendarConverter() {
    }

    /** 1900–2099 年农历压缩表（索引＝年-1900）。 */
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
            0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
            0x0d520
    };

    private static final LocalDate BASE = LocalDate.of(1900, 1, 31);

    private static int info(int year) {
        return LUNAR_INFO[year - 1900];
    }

    /** 该年闰月（0 表示无闰月）。 */
    public static int leapMonth(int year) {
        return info(year) & 0xf;
    }

    /** 闰月天数（无闰月返回 0）。 */
    public static int leapDays(int year) {
        if (leapMonth(year) == 0) {
            return 0;
        }
        return (info(year) & 0x10000) != 0 ? 30 : 29;
    }

    /** 该年某农历月（非闰月）天数。 */
    public static int monthDays(int year, int month) {
        return (info(year) & (0x10000 >> month)) != 0 ? 30 : 29;
    }

    /** 该农历年总天数。 */
    public static int yearDays(int year) {
        int sum = 348;
        int inf = info(year);
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            if ((inf & i) != 0) {
                sum += 1;
            }
        }
        return sum + leapDays(year);
    }

    /**
     * 公历 → 农历。
     *
     * @return {@code {农历年, 农历月, 农历日, 是否闰月(0/1)}}；越界返回 null
     */
    public static int[] solarToLunar(int year, int month, int day) {
        if (year < 1900 || year > 2099) {
            return null;
        }
        long offset = LocalDate.of(year, month, day).toEpochDay() - BASE.toEpochDay();
        int i = 1900;
        long temp = 0;
        while (i < 2101 && offset > 0) {
            temp = yearDays(i);
            offset -= temp;
            i++;
        }
        if (offset < 0) {
            offset += temp;
            i--;
        }
        int lunarYear = i;
        int leap = leapMonth(lunarYear);
        boolean isLeap = false;
        int j = 1;
        long temp2 = 0;
        while (j < 13 && offset > 0) {
            if (leap > 0 && j == (leap + 1) && !isLeap) {
                j--;
                isLeap = true;
                temp2 = leapDays(lunarYear);
            } else {
                temp2 = monthDays(lunarYear, j);
            }
            if (isLeap && j == (leap + 1)) {
                isLeap = false;
            }
            offset -= temp2;
            j++;
        }
        if (offset == 0 && leap > 0 && j == leap + 1) {
            if (isLeap) {
                isLeap = false;
            } else {
                isLeap = true;
                j--;
            }
        }
        if (offset < 0) {
            offset += temp2;
            j--;
        }
        return new int[]{lunarYear, j, (int) offset + 1, isLeap ? 1 : 0};
    }

    /** 农历 → 公历；越界返回 null。 */
    public static LocalDate lunarToSolar(int lunarYear, int lunarMonth, int lunarDay, boolean leap) {
        if (lunarYear < 1900 || lunarYear > 2099 || lunarMonth < 1 || lunarMonth > 12) {
            return null;
        }
        long offset = 0;
        for (int i = 1900; i < lunarYear; i++) {
            offset += yearDays(i);
        }
        int leapM = leapMonth(lunarYear);
        for (int m = 1; m < lunarMonth; m++) {
            offset += monthDays(lunarYear, m);
            if (leapM > 0 && m == leapM) {
                offset += leapDays(lunarYear);
            }
        }
        if (leap && leapM == lunarMonth) {
            offset += monthDays(lunarYear, lunarMonth);
        }
        offset += (lunarDay - 1);
        return BASE.plusDays(offset);
    }
}
