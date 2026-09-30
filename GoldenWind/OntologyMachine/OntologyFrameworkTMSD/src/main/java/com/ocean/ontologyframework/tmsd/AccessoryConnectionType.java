package com.ocean.ontologyframework.tmsd;

/**
 * 附件连接方式（设计输入之一，本体 {@code :附件连接方式} 数据属性，xsd:string）。
 *
 * <p>本体依据：{@code Accessory ⊑ ∃accessoryConnectionType}（{@code TowerMidSection.owl} v15.8，
 * 分类值建模：物理对象上的 {@code xsd:string} 数据属性）。
 *
 * <p>老程序依据：{@code tool.lasupport2w()}（支撑距焊缝 ≥125mm）、{@code tool.support()} 与
 * {@code tool.ls_heights()}（焊缝避让 {@code safety_distance = 200}）——三者都要求附件与环焊缝
 * 保持距离，只有<b>焊接在筒壁上</b>的附件才有此要求，故历史设计为「焊接式」。
 */
public enum AccessoryConnectionType {

    /** 焊接式：附件焊在筒壁上，需与环焊缝保持距离。 */
    WELDED("焊接式", "焊接式"),

    /** 粘贴式：附件粘接在筒壁上，无焊缝避让要求。 */
    BONDED("粘贴式", "粘贴式");

    private final String label;
    private final String ontologyValue;

    AccessoryConnectionType(String label, String ontologyValue) {
        this.label = label;
        this.ontologyValue = ontologyValue;
    }

    public String label() {
        return label;
    }

    /** 写入本体 {@code :附件连接方式} 的字面量。 */
    public String ontologyValue() {
        return ontologyValue;
    }

    /** 由中文字面量解析。 */
    public static AccessoryConnectionType of(String text) {
        if (text == null) {
            return WELDED;
        }
        String t = text.trim();
        if (t.contains("粘贴") || t.contains("粘接") || t.contains("胶")) {
            return BONDED;
        }
        return WELDED;
    }
}
