package com.ocean.ontologyframework.tcm.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SymptomMappingService} 静态文本工具的边界分支离线单测（无需 Spring/LLM/Camunda）。
 * 覆盖 {@code splitUnits}（null/空/前导虚词剥离/过短保留）与 {@code dice}（空/相同/部分重合）。
 */
@DisplayName("SymptomMappingService 文本工具：splitUnits / dice 边界")
class SymptomMappingServiceUtilsTest {

    @Test
    @DisplayName("splitUnits：null / 空 / 前导虚词剥离 / 仅虚词过短保留")
    void splitUnits() {
        assertTrue(SymptomMappingService.splitUnits(null).isEmpty());
        assertTrue(SymptomMappingService.splitUnits("").isEmpty());

        List<String> u = SymptomMappingService.splitUnits("我发热，还咳嗽");
        assertTrue(u.contains("发热"));
        assertTrue(u.contains("咳嗽"));

        List<String> fillers = SymptomMappingService.splitUnits("而且并且同时发热，");
        assertTrue(fillers.contains("发热"));

        // 去掉虚词后不足 2 字 → 保留原词（不空）
        List<String> shortKeep = SymptomMappingService.splitUnits("有点");
        assertEquals(1, shortKeep.size());
    }

    @Test
    @DisplayName("dice：空 / 完全相同 / 部分重合")
    void dice() {
        assertEquals(0.0, SymptomMappingService.dice("", "x"), 1e-9);
        assertEquals(0.0, SymptomMappingService.dice("x", ""), 1e-9);
        assertEquals(1.0, SymptomMappingService.dice("发热恶寒", "发热恶寒"), 1e-9);
        assertEquals(0.5, SymptomMappingService.dice("ab", "ac"), 1e-9);
    }
}
