package com.ocean.ontologyframework.ziweidoushu.validation;

import java.util.List;
import java.util.Map;

/**
 * 命例本体合规校验结果（参照 {@code Pizza.ValidationResult} 形态）。
 *
 * @param isValid          是否合规
 * @param violations       不合规原因（逐条）
 * @param originalInstance 被校验的原始命例
 */
public record ValidationResult(
        boolean isValid,
        List<String> violations,
        Map<String, Object> originalInstance
) {
    public static ValidationResult valid(Map<String, Object> instance) {
        return new ValidationResult(true, List.of(), instance);
    }

    public static ValidationResult invalid(List<String> violations, Map<String, Object> instance) {
        Map<String, Object> snapshot = instance != null ? Map.copyOf(instance) : Map.of();
        return new ValidationResult(false, violations, snapshot);
    }
}
