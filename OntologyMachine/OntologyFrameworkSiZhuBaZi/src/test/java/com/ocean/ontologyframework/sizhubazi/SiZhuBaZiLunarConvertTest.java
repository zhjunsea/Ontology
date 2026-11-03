package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四柱命理 · 农历→公历换算（含闰月）。
 *
 * <p>覆盖原挂【待考】的 {@code lunar_convert} 项：节点 1 {@code parseBirthInput} 对「农历」输入
 * 经 {@code lunarToSolar} 换算为公历（内置 1900–2100 权威农历数据表 {@code LUNAR_INFO}）；
 * 年份越界或该月无闰月却标闰时返回 null（不臆造换算，★铁律 8/10）。
 * <p>校验用例取公认春节日期（正月初一）与闰月首日。
 */
@DisplayName("四柱命理 · 农历→公历换算（含闰月）")
class SiZhuBaZiLunarConvertTest {

    private static LocalDateTime lunarToSolar(final Object birthVar) throws Exception {
        Constructor<SiZhuBaZiOntologyJobWorker> c = SiZhuBaZiOntologyJobWorker.class
                .getDeclaredConstructor();
        c.setAccessible(true);
        SiZhuBaZiOntologyJobWorker w = c.newInstance();
        Method m = SiZhuBaZiOntologyJobWorker.class
                .getDeclaredMethod("lunarToSolar", Object.class);
        m.setAccessible(true);
        return (LocalDateTime) m.invoke(w, birthVar);
    }

    private static Map<String, Object> lunarMap(int y, int mo, int d, String hourZhi, Boolean leap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", y);
        m.put("month", mo);
        m.put("day", d);
        m.put("hourZhi", hourZhi);
        m.put("leap", leap);
        return m;
    }

    @Test
    @DisplayName("结构化农历（非闰）：2024 正月初一 子时 → 2024-02-10 23:00")
    void structuredLunarNewYear() throws Exception {
        assertThat(lunarToSolar(lunarMap(2024, 1, 1, "子", false)))
                .isEqualTo(LocalDateTime.of(2024, 2, 10, 23, 0));
    }

    @Test
    @DisplayName("结构化农历（非闰）：2000 正月初一 午时 → 2000-02-05 11:00")
    void structuredLunar2000() throws Exception {
        assertThat(lunarToSolar(lunarMap(2000, 1, 1, "午", false)))
                .isEqualTo(LocalDateTime.of(2000, 2, 5, 11, 0));
    }

    @Test
    @DisplayName("结构化农历（闰月）：2020 闰四月初一 → 2020-05-23")
    void structuredLeapMonth2020() throws Exception {
        assertThat(lunarToSolar(lunarMap(2020, 4, 1, "子", true)))
                .isEqualTo(LocalDateTime.of(2020, 5, 23, 23, 0));
    }

    @Test
    @DisplayName("结构化农历（闰月）：2023 闰二月初一 → 2023-03-22")
    void structuredLeapMonth2023() throws Exception {
        assertThat(lunarToSolar(lunarMap(2023, 2, 1, "子", true)))
                .isEqualTo(LocalDateTime.of(2023, 3, 22, 23, 0));
    }

    @Test
    @DisplayName("字符串农历（含「闰」）：「闰2020-4-1 12:00」→ 2020-05-23 12:00")
    void stringLeapMonth() throws Exception {
        assertThat(lunarToSolar("闰2020-4-1 12:00"))
                .isEqualTo(LocalDateTime.of(2020, 5, 23, 12, 0));
    }

    @Test
    @DisplayName("边界：1900 正月初一 → 1900-01-31；2100 正月初一 → 2100-02-09")
    void boundaryYears() throws Exception {
        assertThat(lunarToSolar(lunarMap(1900, 1, 1, "子", false)).toLocalDate())
                .isEqualTo(java.time.LocalDate.of(1900, 1, 31));
        assertThat(lunarToSolar(lunarMap(2100, 1, 1, "子", false)).toLocalDate())
                .isEqualTo(java.time.LocalDate.of(2100, 2, 9));
    }

    @Test
    @DisplayName("越界年份（1899/2101）→ null（不臆造）")
    void outOfRangeReturnsNull() throws Exception {
        assertThat(lunarToSolar(lunarMap(1899, 1, 1, "子", false))).isNull();
        assertThat(lunarToSolar(lunarMap(2101, 1, 1, "子", false))).isNull();
    }

    @Test
    @DisplayName("该月无闰月却标闰 → null（2024 无闰四月，2023 有闰二月）")
    void invalidLeapReturnsNull() throws Exception {
        assertThat(lunarToSolar(lunarMap(2024, 4, 1, "子", true))).isNull();
    }
}
