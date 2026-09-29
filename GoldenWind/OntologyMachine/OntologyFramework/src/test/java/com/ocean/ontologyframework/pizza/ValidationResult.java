package com.ocean.ontologyframework.pizza;

import java.util.List;
import java.util.Map;

public record ValidationResult(
        boolean isValid,
        List<String> violations,
        Map<String, Object> originalInstance
) {
    /**
     * 创建合规结果
     */
    public static ValidationResult valid(Map<String, Object> instance) {
        return new ValidationResult(true, List.of(), instance);
    }

    /**
     * 创建不合规结果
     */
    public static ValidationResult invalid(List<String> violations, Map<String, Object> instance) {
        // ✅ 防御性拷贝：防止外部后续修改原始Map导致验证结果失真
        Map<String, Object> snapshot = instance != null
                ? Map.copyOf(instance)
                : Map.of();
        return new ValidationResult(false, violations, snapshot);
    }
}