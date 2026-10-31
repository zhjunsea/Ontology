package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 四柱命理 · 生辰解析（字符串 / 结构化对象）。
 *
 * <p>覆盖 {@code handleParseBirthInput} 入参容错：前端 {@code index.html} 以结构化对象
 * {@code {calendar, year, month, day, hourZhi, minute}} 提交生日，需与字符串
 * {@code yyyy-MM-dd HH:mm} 等价解析；时辰地支按客户裁定 ④（子初换日 23:00，子→晚子）。
 */
@DisplayName("四柱命理 · 生辰解析（字符串 / 结构化对象）")
class SiZhuBaZiBirthParsingTest {

    private static LocalDateTime resolve(final Object birthVar) throws Exception {
        Method m = SiZhuBaZiOntologyJobWorker.class
                .getDeclaredMethod("resolveBirthDateTime", Object.class);
        m.setAccessible(true);
        return (LocalDateTime) m.invoke(null, birthVar);
    }

    @Test
    @DisplayName("字符串生辰 yyyy-MM-dd HH:mm")
    void stringBirth() throws Exception {
        assertThat(resolve("1990-05-15 08:30"))
                .isEqualTo(LocalDateTime.of(1990, 5, 15, 8, 30));
    }

    @Test
    @DisplayName("结构化生辰（报错日志原样入参）：1978-7-4 午时 minute=null → 11:00")
    void structuredBirthMatchesReportedInput() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calendar", "公历");
        m.put("year", 1978);
        m.put("month", 7);
        m.put("day", 4);
        m.put("hourZhi", "午");
        m.put("minute", null);
        assertThat(resolve(m)).isEqualTo(LocalDateTime.of(1978, 7, 4, 11, 0));
    }

    @Test
    @DisplayName("结构化生辰：hourZhi=子 → 晚子 23 时（子初换日，客户裁定 ④）")
    void structuredZiMapsLateZi() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", 1990);
        m.put("month", 5);
        m.put("day", 15);
        m.put("hourZhi", "子");
        m.put("minute", 30);
        assertThat(resolve(m)).isEqualTo(LocalDateTime.of(1990, 5, 15, 23, 30));
    }

    @Test
    @DisplayName("结构化生辰缺年/月/日：抛 IllegalArgumentException（不臆造）")
    void structuredMissingDateFails() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hourZhi", "午");
        assertThatThrownBy(() -> resolve(m))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
