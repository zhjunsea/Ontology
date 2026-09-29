package com.ocean.ontologyframework.tmsd;

import java.util.ArrayList;
import java.util.List;

/**
 * 新塔架的中段设计输入（设计流程「输入条件」四项：塔架尺寸 / 机型 / 升降机配置 / 区域，
 * 外加附件连接方式）。
 *
 * <p>几何部分用 {@link TowerGeometry} 表达（与历史设计同一结构，便于逐级比对）；
 * 布局参考沿用历史设计的 {@link LayoutSpec}（爬梯支撑长度/宽度、电缆线夹是否隔开、
 * 灯位置等由电气/主设给定的参考量）。
 */
public record TowerDesignRequest(String caseName,
                                 TowerGeometry geometry,
                                 String model,
                                 ElevatorType elevatorType,
                                 String region,
                                 AccessoryConnectionType connectionType,
                                 LayoutSpec layoutReference) {

    /**
     * 中段段号。
     *
     * <p>主体表 {@code Flange} 排序约定：第 1 个法兰=下段底、第 2 个=下段顶、倒数第 2 个=顶段底、
     * 最后一个=顶段顶。故「下段」= 第 1 段（法兰 1→2）、「顶段」= 最后一段（法兰 N-1→N），
     * **其余各段均为中段** ⇒ 中段 = 第 2 段 .. 第（段数−1）段。不写死具体段号。
     */
    public List<Integer> middleSectionNumbers() {
        List<Integer> out = new ArrayList<>();
        for (int n = 2; n <= geometry.sectionCount() - 1; n++) {
            out.add(n);
        }
        return out;
    }

    /** 第 {@code sectionNo} 段的平台距顶法兰距离（mm）；布局表缺失即报错（不硬编码兜底）。 */
    public double platformDistance(int sectionNo) {
        LayoutSpec.MiddleSection m = layoutReference.middleSection(sectionNo);
        if (m == null) {
            throw new IllegalStateException("项目布局表缺少第 " + sectionNo + " 段（中间段）数据");
        }
        return m.platformDistance();
    }

    /** 第 {@code sectionNo} 段平台所在处内径（mm）。 */
    public double platformInnerDiameter(int sectionNo) {
        int n = sectionNo - 1;
        double h = geometry.sectionCourses(n).totalHeight() - platformDistance(sectionNo);
        return geometry.innerDiameterAt(n, h);
    }

    /** 第 {@code sectionNo} 段的段总高（mm）。 */
    public double sectionLength(int sectionNo) {
        return geometry.sectionCourses(sectionNo - 1).totalHeight();
    }

    /** 第 {@code sectionNo} 段的焊缝位置（mm，距段底；直读 B 列、剔除法兰）。 */
    public List<Integer> weldPositions(int sectionNo) {
        return geometry.weldPositions(sectionNo - 1);
    }

    /** 第 {@code sectionNo} 段的筒节高度序列（mm）。 */
    public List<Double> courseHeights(int sectionNo) {
        return geometry.sectionCourses(sectionNo - 1).heights();
    }

    /** 第 {@code sectionNo} 段的下外径 / 上外径（mm，取非法兰筒节）。 */
    public double[] outerDiameters(int sectionNo) {
        TowerGeometry.SectionCourses sc = geometry.sectionCourses(sectionNo - 1);
        int last = Math.max(sc.outerDiametersTop().size() - 2, 0);
        return new double[]{sc.outerDiametersBottom().get(1), sc.outerDiametersTop().get(last)};
    }

    @Override
    public String toString() {
        return caseName + "（机型=" + model + " 升降机=" + elevatorType.label()
                + " 连接方式=" + connectionType.label() + " 区域=" + region + "）";
    }
}
