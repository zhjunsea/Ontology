package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第 31 步「节气精度升级」单元测试（离线，不依赖 DB/Ontop）。
 *
 * <p>说明：精确交节时刻来自 DB（{@code solar_term_time}，经 Ontop OBDA 虚拟访问），离线测试环境不可用，
 * 故此处仅覆盖两段纯逻辑：
 * <ol>
 *   <li>SPARQL {@code xsd:dateTime} 字面量解析（兼容 {@code 'T'}/{@code ' '} 分隔、小数秒、时区 {@code Z}/{@code ±hh:mm}），
 *       非法输入返回 {@code null} 而不抛异常（保证「降级不阻断」）；</li>
 *   <li>DB 不可用/超年份范围时的内置近似兜底（与 Java {@code S_TERM} 同式）之「日」正确性。</li>
 * </ol>
 * 精确路径（OBDA 命中）由 {@link SiZhuBaZiBpmnInMemoryTest} 端到端与入库脚本合规校验共同覆盖。
 */
class SiZhuBaZiSolarTermTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static LocalDateTime invokeParse(String s) throws Exception {
        Method m = WORKER.getDeclaredMethod("parseOntoDateTime", String.class);
        m.setAccessible(true);
        return (LocalDateTime) m.invoke(null, s);
    }

    private static LocalDateTime invokeApprox(int year, int idx) throws Exception {
        Method m = WORKER.getDeclaredMethod("approxSolarTermDateTime", int.class, int.class);
        m.setAccessible(true);
        return (LocalDateTime) m.invoke(null, year, idx);
    }

    @Test
    @DisplayName("第 31 步：SPARQL dateTime 解析兼容 'T'/' '、小数秒、时区；仅日期按 00:00")
    void parseOntoDateTimeVariants() throws Exception {
        LocalDateTime expect = LocalDateTime.of(2024, 2, 4, 16, 20);
        assertThat(invokeParse("2024-02-04T16:20:00")).isEqualTo(expect);
        assertThat(invokeParse("2024-02-04 16:20:00")).isEqualTo(expect);
        assertThat(invokeParse("2024-02-04T16:20:00.0")).isEqualTo(expect);
        assertThat(invokeParse("2024-02-04T16:20:00+08:00")).isEqualTo(expect);
        assertThat(invokeParse("2024-02-04T16:20:00Z")).isEqualTo(expect);
        assertThat(invokeParse("2024-02-04")).isEqualTo(LocalDateTime.of(2024, 2, 4, 0, 0));
    }

    @Test
    @DisplayName("第 31 步：dateTime 解析非法输入返回 null（降级不阻断）")
    void parseOntoDateTimeInvalid() throws Exception {
        assertThat(invokeParse(null)).isNull();
        assertThat(invokeParse("")).isNull();
        assertThat(invokeParse("not-a-datetime")).isNull();
    }

    @Test
    @DisplayName("第 31 步：内置近似兜底（DB 不可用）2024 立春/小寒/冬至「日」正确")
    void approximationFallbackByDay() throws Exception {
        assertThat(invokeApprox(2024, 2).toLocalDate()).isEqualTo(LocalDate.of(2024, 2, 4));   // 立春
        assertThat(invokeApprox(2024, 0).toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 6));   // 小寒
        assertThat(invokeApprox(2024, 23).toLocalDate()).isEqualTo(LocalDate.of(2024, 12, 21)); // 冬至
    }
}
