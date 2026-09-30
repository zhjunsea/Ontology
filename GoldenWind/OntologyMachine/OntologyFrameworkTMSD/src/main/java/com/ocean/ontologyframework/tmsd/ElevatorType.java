package com.ocean.ontologyframework.tmsd;

/**
 * 升降机配置（设计输入之一）。
 *
 * <p>本体依据：{@code RopeGuidedElevator ⊑ >=1 hasSupport.Support}、
 * {@code LadderGuidedElevator ⊑ =0 hasSupport.Support}（{@code TowerMidSection.owl} v15.8）。
 * 老程序依据：{@code infoW.midSkelW} 写出 {@code H_SUPPORT}（扶持高度）→ 有扶持 → 钢绳导向；
 * {@code tool.support(n, SEC_H_TOTAL, H_PLATFORM)} 即扶持高度算法。
 */
public enum ElevatorType {

    /** 钢绳导向升降机：需要扶持（{@code hasSupport ≥ 1}）。 */
    ROPE_GUIDED("钢绳导向", TmsdVocabulary.C_ROPE_GUIDED_ELEVATOR, true),

    /** 爬梯导向升降机：无扶持（{@code hasSupport = 0}），由爬梯导向。 */
    LADDER_GUIDED("爬梯导向", TmsdVocabulary.C_LADDER_GUIDED_ELEVATOR, false);

    private final String label;
    private final String classIri;
    private final boolean hasSupport;

    ElevatorType(String label, String classIri, boolean hasSupport) {
        this.label = label;
        this.classIri = classIri;
        this.hasSupport = hasSupport;
    }

    public String label() {
        return label;
    }

    public String classIri() {
        return classIri;
    }

    /** 是否需要扶持（本体 {@code hasSupport} 基数约束）。 */
    public boolean hasSupport() {
        return hasSupport;
    }

    /** 由中文字面量解析（"钢绳导向" / "爬梯导向"）。 */
    public static ElevatorType of(String text) {
        if (text == null) {
            return ROPE_GUIDED;
        }
        String t = text.trim();
        if (t.contains("爬梯")) {
            return LADDER_GUIDED;
        }
        if (t.contains("钢绳") || t.contains("钢丝绳")) {
            return ROPE_GUIDED;
        }
        return ROPE_GUIDED;
    }
}
