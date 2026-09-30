package com.ocean.ontologyframework.tmsd;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目布局表（{@code 项目布局表.xlsx}）。
 *
 * <p>sheet 与行的对应关系逐条抄自
 * {@code design/towerdesign-main/towerdesign/tools/tool.py#Creo_tool.__init__}
 * （{@code stan_layGeo = read(lay_path, 1)}，即第 2 个 sheet「中间段」）与
 * {@code infoW.py#midSkelW} 的 {@code stan_layGeo.cell_value(行, n)} 用法：
 * <ul>
 *   <li>行 3（0-based 2）→ 平台位置 H_platform</li>
 *   <li>行 4（3）→ 爬梯支撑长度 L_LADDER_I</li>
 *   <li>行 5（4）→ 爬梯支撑宽度 W_LADDER_I</li>
 *   <li>行 6（5）→ 机型 Model</li>
 *   <li>行 7（6）→ 电缆线夹是否隔开 T_or_F</li>
 *   <li>行 8（7）→ 下灯位置 H_LIGHT2FL</li>
 *   <li>行 9（8）→ 上灯位置 H_LIGHT2PLATFORM</li>
 *   <li>行 10（9）→ 安全锚点高度 H_AP</li>
 *   <li>行 11（10）→ 下端防雷螺柱角度 B_B_A</li>
 *   <li>行 12（11）→ 上端防雷螺柱角度 B_T_A</li>
 *   <li>行 13（12）→ 扶持高度 H_SUPPORT</li>
 *   <li>行 14（13）→ 踏棍宽度 W_Rung（附件竖直向宽度，避焊缝边缘偏移 = W_Rung/2）</li>
 *   <li>列 2..6（0-based 1..5）→ 第二段..第六段</li>
 * </ul>
 */
public final class LayoutSpec {

    /** 中间段一行布局（按段，列 = 第二段..第六段）。 */
    public record MiddleSection(int sectionNo, double platformDistance, double ladderSupportLength,
                                double ladderSupportWidth, double rungWidth, String model,
                                boolean cableClampSeparated,
                                double lowerLightPosition, double upperLightPosition,
                                double safetyAnchorHeight, double lowerLightningStudAngle,
                                double upperLightningStudAngle, double supportHeight) {
    }

    private final List<MiddleSection> middleSections;
    private final Map<String, Double> bottomSectionParams;
    private final Map<String, Double> topSectionParams;
    private final String sourceFile;

    public LayoutSpec(List<MiddleSection> middleSections, Map<String, Double> bottomSectionParams,
                      Map<String, Double> topSectionParams, String sourceFile) {
        this.middleSections = List.copyOf(middleSections);
        this.bottomSectionParams = Map.copyOf(new LinkedHashMap<>(bottomSectionParams));
        this.topSectionParams = Map.copyOf(new LinkedHashMap<>(topSectionParams));
        this.sourceFile = sourceFile;
    }

    public List<MiddleSection> middleSections() {
        return middleSections;
    }

    public Map<String, Double> bottomSectionParams() {
        return bottomSectionParams;
    }

    public Map<String, Double> topSectionParams() {
        return topSectionParams;
    }

    public String sourceFile() {
        return sourceFile;
    }

    /** 取第 {@code sectionNo} 段（2..6）的布局；找不到返回 {@code null}。 */
    public MiddleSection middleSection(int sectionNo) {
        for (MiddleSection m : middleSections) {
            if (m.sectionNo() == sectionNo) {
                return m;
            }
        }
        return null;
    }

    /** 取中间段机型（各段一致时返回该值，否则返回第一段）。 */
    public String model() {
        return middleSections.isEmpty() ? null : middleSections.get(0).model();
    }
}
