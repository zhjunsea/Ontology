package com.ocean.ontologyframework.tmsd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 塔架中段设计引擎 —— <b>完全正向设计</b>。
 *
 * <p><b>范式</b>：不依赖任何历史设计匹配，由「塔架主体信息表 + 项目布局表」两张输入表 +
 * 设计规则一次性算出下一阶段 3D 模型参数。依据
 * {@code design/塔架中段-正向设计流程.md} v2.2（S0→S8 全部必做）。
 *
 * <p><b>算法来源</b>（逐条对应老工具 towerdesign，不发明）：
 * <ul>
 *   <li>{@code tools/tool.py#get_weld_positions} → {@link TowerGeometry#weldPositions(int)}（直读 B 列、剔除法兰）</li>
 *   <li>{@code tools/tool.py#ls_heights} → {@link #accessoryLayout}（正向化：280 倍数增量 + 边缘避焊缝）</li>
 *   <li>{@code tools/tool.py#lampLay} → {@link #lightHeights}</li>
 *   <li>{@code drawing/infoW.py#midSkelW} 的 {@code windturbine_models} → {@link TmsdVocabulary#modelParams}</li>
 * </ul>
 *
 * <p><b>约束来源（零硬编码）</b>：所有业务数值均<b>运行时刻</b>取自
 * {@link TmsdVocabulary}（由 {@link TmsdOntologyModel} 解析 {@code TowerMidSection.owl} 得到），
 * 引擎内不复制任何业务字面量。
 */
public final class TowerDesignEngine {

    private TowerDesignEngine() {
    }

    /** {@code H{i}_*_Exist} 输出槽位上限（非业务技术常量）。 */
    private static final int EXIST_SLOTS = 20;

    // ==================== 本体约束访问别名（统一取自 TmsdVocabulary，避免复制字面量） ====================

    private static double cnum(String prop) {
        return TmsdVocabulary.num(prop);
    }

    private static double clo(String prop) {
        return TmsdVocabulary.lower(prop);
    }

    private static double chi(String prop) {
        return TmsdVocabulary.upper(prop);
    }

    // ==================== 变型（单一方案） ====================

    /**
     * 附件排布模式（单一方案）：贪心取大（每步优先最大间距，避焊缝才降档），
     * 在满足本体约束前提下使附件数量最少、最省。
     */
    public enum AccessoryMode {
        /** 间距取大：附件数量最少、单件受力最大、材料最省。 */
        PREFER_MAX("间距取大·数量最少");

        private final String label;

        AccessoryMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** 目标间距 = 本体 {@code accessoryCenterSpacing} 上界（贪心取大的起点）。 */
        public double targetSpacing() {
            return chi("accessoryCenterSpacing");
        }
    }

    /** 设计变型。 */
    public record DesignVariant(String name, AccessoryMode accessoryMode, String note) {
    }

    /** 唯一的正向设计方案（附件间距贪心取大）。 */
    public static List<DesignVariant> variants() {
        List<DesignVariant> out = new ArrayList<>();
        List<Double> candidates = TmsdVocabulary.valueSetDesc("accessorySpacingMultiple");
        for (AccessoryMode m : AccessoryMode.values()) {
            out.add(new DesignVariant(m.label(), m,
                    "附件间距贪心取大（每步优先最大档位 " + trim(candidates.get(0)) + "×"
                            + trim(cnum("rungSpacing")) + "mm，避焊缝才降档）；与环焊缝按「上/下边缘」避让 > "
                            + trim(clo("accessoryToWeldDistance")) + "mm"));
        }
        return out;
    }

    // ==================== 单段设计 ====================

    /** 第 {@code sectionNo} 段的完整设计结果。 */
    public record SectionDesign(int sectionNo,
                                double sectionTotalHeight,
                                double platformDistance,
                                double platformHeight,
                                double platformInnerDiameter,
                                double alpha,
                                double upperFlangeOuterDiameter,
                                double upperFlangeInnerDiameter,
                                double upperFlangeThickness,
                                double upperFlangeNeckThickness,
                                double lowerFlangeOuterDiameter,
                                double lowerFlangeInnerDiameter,
                                double lowerFlangeThickness,
                                double lowerFlangeNeckThickness,
                                double ladderLength,
                                double ladderSupportLength,
                                double ladderSupportWidth,
                                double trayLength,
                                double supportHeight,
                                double supportToWeldDistance,
                                List<Double> accessoryHeights,
                                List<Double> accessoryIncrements,
                                List<Double> cableClampHeights,
                                List<Double> cableClampIncrements,
                                double accessorySpacing,
                                double firstAccessoryToBottom,
                                double secondLastToPlatform,
                                double minAccessoryToWeldDistance,
                                double bracketLength,
                                double bracketRightChord,
                                double bracketLeftChord,
                                double lastBracketToPlatform,
                                List<Double> lightHeights,
                                double firstLightHeight,
                                double lightStudSpacing,
                                double minLightStudToWeldDistance,
                                String lightType,
                                String accessoryType,
                                String connectionType,
                                double rungSpacing,
                                double firstRungToBottom,
                                List<Double> lightningStudAngles,
                                Map<String, Object> parameters) {

        /** 附件数量（爬梯支撑组数）。 */
        public int accessoryCount() {
            return accessoryHeights.size();
        }
    }

    /**
     * 计算第 {@code sectionNo} 段的设计。
     *
     * @param geometry  塔架几何（来自主体信息表）
     * @param sectionNo 段号（1-based）
     * @param request   设计输入（机型 / 区域 / 连接方式）
     * @param layout    项目布局表（中间段）
     * @param variant   设计变型
     */
    public static SectionDesign designSection(TowerGeometry geometry, int sectionNo,
                                              TowerDesignRequest request, LayoutSpec layout,
                                              DesignVariant variant) {
        int n = sectionNo - 1;
        TowerGeometry.SectionCourses sc = geometry.sectionCourses(n);
        double secH = sc.totalHeight();

        LayoutSpec.MiddleSection lay = layout.middleSection(sectionNo);
        if (lay == null) {
            throw new IllegalStateException("项目布局表缺少第 " + sectionNo + " 段（中间段）数据，无法设计");
        }
        double hPlatform = lay.platformDistance();
        double platformHeight = round1(secH - hPlatform);
        double platformInnerDiameter = geometry.innerDiameterAt(n, platformHeight);

        TowerGeometry.Flange lower = geometry.flanges().get(n);
        TowerGeometry.Flange upper = geometry.flanges().get(n + 1);

        double daBottom = sc.outerDiametersBottom().get(1);
        double daTop = sc.outerDiametersTop().get(Math.max(sc.outerDiametersTop().size() - 2, 0));
        double alpha = Math.atan((daBottom - daTop) / 2.0 / secH);

        // ---- S5 高度设计：梯子与线槽与塔筒等高（段高恒为 280 整数倍） ----
        double ladderLength = secH;
        double trayLength = secH;
        double ladderSupportLength = lay.ladderSupportLength();
        double ladderSupportWidth = lay.ladderSupportWidth();
        double rungWidth = lay.rungWidth();

        List<Integer> welds = geometry.weldPositions(n);

        // ---- S2 升降机设计（扶持）：直读布局表 (13,n) ----
        double supportHeight = lay.supportHeight();
        double supportToWeld = minDistance(supportHeight, welds);

        // ---- S6 筒节设计：附件（爬梯支撑/电缆托架）排布 ----
        // 避焊缝判据：附件「上/下边缘」到焊缝距离 > 100；边缘偏移取踏棍宽度 W_Rung 的一半（铁律）。
        double edgeOffset = rungWidth / 2.0;
        AccessoryLayout acc = accessoryLayout(platformHeight, welds, edgeOffset);

        // ---- S6 电缆线夹（区域=中国 ⇒ 隔开；增量语义） ----
        List<Double> cableIncrements = cableClampIncrements(acc.increments());
        List<Double> cableAbsolutes = cumulative(cableIncrements);

        // ---- S6 照明：灯型固定焊接灯（取自本体）；首灯与间距取自本体 ----
        String lightType = TmsdVocabulary.stringValue("lightType");
        double lightSpacing = chi("lightToLightMaxSpacing");
        List<Double> lights = lightHeights(platformHeight, welds, lightSpacing, lightType);
        double firstLight = lights.isEmpty() ? clo("firstLightHeight") : lights.get(0);
        double minLightWeld = Double.NaN;
        if (!lights.isEmpty()) {
            double studOffset = cnum("lightStudSpacing") / 2.0;
            minLightWeld = Double.POSITIVE_INFINITY;
            for (double h : lights) {
                for (int w : welds) {
                    minLightWeld = Math.min(minLightWeld, Math.abs(h - studOffset - w));
                    minLightWeld = Math.min(minLightWeld, Math.abs(h + studOffset - w));
                }
            }
        }

        // ---- S6 防雷接地螺柱：角度取自本体（一组 3 个，首 70°、其余 120° 均布） ----
        List<Double> studAngles = new ArrayList<>(TmsdVocabulary.valueSet("lightningStudInstallAngle"));

        // ---- S4 直径设计：电缆托架参数按机型映射（本体 :Model 个体） ----
        double[] cable = TmsdVocabulary.modelParams(request.model());

        // ---- S3 附件类型替换：按连接方式选型（焊接/粘贴，本流程固定焊接） ----
        String accessoryType = request.connectionType() == AccessoryConnectionType.WELDED
                ? "焊接式附件总成" : "粘贴式附件总成";

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("SEC_H_total", secH);
        params.put("DA_TOP", upper.outerDiameter());
        params.put("DI_TOP", upper.innerDiameter());
        params.put("TFL_TOP", upper.thickness());
        params.put("S_TOP", upper.neckThickness());
        params.put("DA_BOTTOM", lower.outerDiameter());
        params.put("DI_BOTTOM", lower.innerDiameter());
        params.put("TFL_BOTTOM", lower.thickness());
        params.put("S_BOTTOM", lower.neckThickness());
        params.put("H_platform", hPlatform);
        params.put("L_LADDER", ladderLength);
        params.put("L_LADDER_I", ladderSupportLength);
        params.put("W_LADDER_I", ladderSupportWidth);
        params.put("Alpha_deg", Math.toDegrees(alpha));
        params.put("L_C", trayLength);
        params.put("H_SUPPORT", supportHeight);
        params.put("RUNG_SPACING", cnum("rungSpacing"));
        params.put("FIRST_RUNG_TO_BOTTOM", cnum("firstRungToBottomFlange"));
        putIncrements(params, "H", "L", acc.increments());
        putIncrements(params, "H", "CABLE", cableIncrements);
        params.put("L_CABLE", cable[0]);
        params.put("L1_CABLE_I", cable[1]);
        params.put("L2_CABLE_I", cable[2]);
        // 老应用 Cable_top_h：最后一组电缆夹板相对顶法兰上端面距离（本体 :最后电缆夹板到顶法兰 hasValue 1000）
        double cableTopH = cnum("lastBracketToTopFlange");
        params.put("di_cable_top", round1(geometry.innerDiameterAt(n, secH - cableTopH)));
        params.put("di_cable_down", round1(geometry.innerDiameterAt(n, cnum("firstAccessoryToBottom"))));
        for (int i = 0; i < lights.size(); i++) {
            params.put("H_LIGHT" + (i + 1), lights.get(i));
        }
        params.put("LIGHT_TYPE", lightType);
        // 老应用：B_B_A/B_T_A 直读项目布局表 (11,n)/(12,n)，不按本体规则推导。
        params.put("B_B_A", lay.lowerLightningStudAngle());
        params.put("B_T_A", lay.upperLightningStudAngle());
        params.put("LIGHTNING_STUD_ANGLES", studAngles);
        params.put("LIGHTNING_STUD_FLANGE_DISTANCE", cnum("lightningStudFlangeDistance"));
        params.put("ACCESSORY_TYPE", accessoryType);
        params.put("ACCESSORY_CONNECTION_TYPE", request.connectionType().ontologyValue());

        return new SectionDesign(sectionNo, secH, hPlatform, platformHeight, platformInnerDiameter,
                Math.toDegrees(alpha),
                upper.outerDiameter(), upper.innerDiameter(), upper.thickness(), upper.neckThickness(),
                lower.outerDiameter(), lower.innerDiameter(), lower.thickness(), lower.neckThickness(),
                ladderLength, ladderSupportLength, ladderSupportWidth, trayLength,
                supportHeight, supportToWeld,
                acc.heights(), acc.increments(), cableAbsolutes, cableIncrements, acc.spacing(),
                cnum("firstAccessoryToBottom"),
                acc.lastToPlatform(), acc.minWeldDistance(),
                cable[0], cable[1], cable[2], cnum("lastBracketToPlatform"),
                lights, firstLight, cnum("lightStudSpacing"), minLightWeld,
                lightType, accessoryType, request.connectionType().ontologyValue(),
                cnum("rungSpacing"), cnum("firstRungToBottomFlange"), studAngles,
                Map.copyOf(params));
    }

    // ==================== 附件排布 ====================

    /**
     * 附件排布结果。
     *
     * @param heights          附件<b>绝对</b>高度序列（距段底，mm）
     * @param increments       {@code H{i}_L} 语义：首元素 = 首组绝对高度(980)，其后为「距上一组的增量」
     * @param spacing          代表间距（平均增量，mm）
     * @param lastToPlatform   最后一组附件到平台的距离（mm）
     * @param minWeldDistance  附件（上/下边缘）到最近环焊缝的最小距离（mm）
     */
    public record AccessoryLayout(List<Double> heights, List<Double> increments, double spacing,
                                  double lastToPlatform, double minWeldDistance) {
    }

    /**
     * 本体约束驱动的附件排布求解（正向贪心取大：从首组高度起始，每步优先最大档位）。
     *
     * <p><b>约束（全部取自本体）</b>：
     * <ul>
     *   <li>第一组附件距段底 {@code firstAccessoryToBottom}（hasValue）；</li>
     *   <li>相邻附件中心间距 = m × 梯档间距（{@code rungSpacing}），
     *       候选 m 取自 {@code accessorySpacingMultiple}（{@code accessoryCenterSpacing}）；</li>
     *   <li>最后一组附件到平台 ∈ {@code secondLastToPlatform}；</li>
     *   <li>附件<b>上/下边缘</b>到环焊缝距离 &gt; {@code accessoryToWeldDistance}，
     *       边缘偏移 = 踏棍宽度 W_Rung 的一半 {@code edgeOffset}。</li>
     * </ul>
     */
    public static AccessoryLayout accessoryLayout(double platformHeight, List<Integer> welds, double edgeOffset) {
        final double first = cnum("firstAccessoryToBottom");
        final double step = cnum("rungSpacing");
        final int[] candidates = TmsdVocabulary.valueSetDesc("accessorySpacingMultiple").stream()
                .mapToInt(Double::intValue).toArray();
        final double bandLo = platformHeight - chi("secondLastToPlatform");
        final double bandHi = platformHeight - clo("secondLastToPlatform");

        if (!edgeSafe(first, welds, edgeOffset)) {
            return new AccessoryLayout(List.of(), List.of(), Double.NaN, Double.NaN, Double.NaN);
        }
        List<Integer> ks = new ArrayList<>();
        ks.add(0);
        java.util.Set<Integer> failed = new java.util.HashSet<>();
        if (!greedyWalk(0, first, step, candidates, bandLo, bandHi, welds, edgeOffset, ks, failed)) {
            return new AccessoryLayout(List.of(), List.of(), Double.NaN, Double.NaN, Double.NaN);
        }
        List<Double> heights = new ArrayList<>(ks.size());
        for (int k : ks) {
            heights.add(first + k * step);
        }
        List<Double> increments = new ArrayList<>(heights.size());
        increments.add(first);
        for (int i = 1; i < heights.size(); i++) {
            increments.add(round1(heights.get(i) - heights.get(i - 1)));
        }
        double lastH = heights.get(heights.size() - 1);
        double avg = heights.size() >= 2 ? round1((lastH - first) / (heights.size() - 1)) : Double.NaN;
        return new AccessoryLayout(List.copyOf(heights), List.copyOf(increments), avg,
                round1(platformHeight - lastH), round1(minEdgeWeldDistance(heights, welds, edgeOffset)));
    }

    /**
     * 贪心取大递归：从网格下标 {@code k}（高度 {@code first + k*step}）出发，尝试落到平台带 {@code [bandLo,bandHi]} 内。
     *
     * <p>每一步优先最大增量 {@code candidates[0]}，成功落带即返回；死路则回退尝试更小增量。
     * 返回 {@code true} 时 {@code ks} 已含从起点到末组的完整下标序列。{@code failed} 记忆化避免指数回退。
     */
    private static boolean greedyWalk(int k, double first, double step, int[] candidates,
                                      double bandLo, double bandHi, List<Integer> welds,
                                      double edgeOffset, List<Integer> ks, java.util.Set<Integer> failed) {
        double h = first + k * step;
        if (h >= bandLo - 1e-6 && h <= bandHi + 1e-6) {
            return true;
        }
        if (failed.contains(k)) {
            return false;
        }
        for (int m : candidates) {
            int k2 = k + m;
            double h2 = first + k2 * step;
            if (h2 > bandHi + 1e-6) {
                continue;
            }
            if (!edgeSafe(h2, welds, edgeOffset)) {
                continue;
            }
            ks.add(k2);
            if (greedyWalk(k2, first, step, candidates, bandLo, bandHi, welds, edgeOffset, ks, failed)) {
                return true;
            }
            ks.remove(ks.size() - 1);
        }
        failed.add(k);
        return false;
    }

    /** 附件上/下边缘是否避开全部环焊缝（边缘到焊缝 &gt; {@code accessoryToWeldDistance}）。 */
    private static boolean edgeSafe(double h, List<Integer> welds, double edgeOffset) {
        double min = clo("accessoryToWeldDistance");
        for (int w : welds) {
            if (Math.abs(h - w) - edgeOffset <= min) {
                return false;
            }
        }
        return true;
    }

    /** 附件上/下边缘到最近环焊缝的最小距离。 */
    private static double minEdgeWeldDistance(List<Double> heights, List<Integer> welds, double edgeOffset) {
        double min = Double.POSITIVE_INFINITY;
        for (double h : heights) {
            min = Math.min(min, minDistance(h, welds) - edgeOffset);
        }
        return min;
    }

    // ==================== 电缆线夹 ====================

    /**
     * 电缆线夹（区域=中国 ⇒ 隔开）：首组 = H1_L，其后每 2 个爬梯支撑增量合并为 1 组线夹。
     *
     * <p>逐行对应老程序 {@code ls_heights} 的 {@code T_or_F == 1} 分支（{@code tool.py} L595–602）。
     * 返回值为 {@code H{i}_CABLE} 的<b>增量</b>序列（首元素 = 首组绝对高度）。
     */
    public static List<Double> cableClampIncrements(List<Double> ladderSupportIncrements) {
        List<Double> cs = new ArrayList<>();
        if (ladderSupportIncrements.isEmpty()) {
            return cs;
        }
        cs.add(ladderSupportIncrements.get(0));
        int i = 1;
        while (i < ladderSupportIncrements.size() - 1) {
            cs.add(round1(ladderSupportIncrements.get(i) + ladderSupportIncrements.get(i + 1)));
            i += 2;
        }
        return cs;
    }

    /** 由增量序列还原绝对高度序列。 */
    public static List<Double> cumulative(List<Double> increments) {
        List<Double> out = new ArrayList<>(increments.size());
        double acc = 0;
        for (double v : increments) {
            acc += v;
            out.add(round1(acc));
        }
        return out;
    }

    // ==================== 灯布置 ====================

    /**
     * 灯布置：第一个灯落在 {@code firstLightHeight} 区间，之后按 {@code spacing}
     * （∈ {@code lightToLightMinSpacing}~{@code lightToLightMaxSpacing}）向上排布，不超过平台高度。
     *
     * <p><b>焊接灯</b>（本体 {@code WeldedLight}）还需满足 {@code lightStudToWeldDistance}：
     * 灯的两个螺柱位于灯位 ±{@code lightStudSpacing/2}，故灯位距环焊缝需 &gt; 相应下限。
     */
    public static List<Double> lightHeights(double platformHeight, List<Integer> welds,
                                            double spacing, String lightType) {
        boolean welded = TmsdVocabulary.C_WELDED_LIGHT.equals(lightClassIri(lightType));
        List<Double> out = new ArrayList<>();

        double firstLo = clo("firstLightHeight");
        double firstHi = chi("firstLightHeight");
        double spacingMin = clo("lightToLightMinSpacing");
        double studOffset = cnum("lightStudSpacing") / 2.0;
        double studWeldMin = clo("lightStudToWeldDistance");

        // 首灯落在本体区间；焊接灯须满足「两个螺柱各自距焊缝 > 100」。
        double h;
        if (welded) {
            h = firstStudSafeLight(firstLo, firstHi, welds, studOffset, studWeldMin);
            if (Double.isNaN(h)) {
                return out;
            }
        } else {
            h = firstLo;
        }
        out.add(round1(h));

        while (true) {
            double next = h + spacing;
            if (next > platformHeight) {
                break;
            }
            double chosen = next;
            if (welded) {
                chosen = Double.NaN;
                for (double cand = next; cand >= h + spacingMin - 1e-9; cand -= 10) {
                    if (studSafe(cand, welds, studOffset, studWeldMin)) {
                        chosen = cand;
                        break;
                    }
                }
            }
            if (Double.isNaN(chosen)) {
                break;
            }
            out.add(round1(chosen));
            h = chosen;
        }
        return out;
    }

    /** 在 [lo,hi] 内以 10mm 步长（非业务）找首个「两个螺柱各自距焊缝 &gt; min」的灯位；无解返回 {@link Double#NaN}。 */
    private static double firstStudSafeLight(double lo, double hi, List<Integer> welds,
                                             double studOffset, double studWeldMin) {
        for (double h = lo; h <= hi + 1e-9; h += 10) {
            if (studSafe(h, welds, studOffset, studWeldMin)) {
                return h;
            }
        }
        return Double.NaN;
    }

    /** 焊接灯两个螺柱（灯位 ±{@code studOffset}）是否都远离全部环焊缝（&gt; {@code studWeldMin}）。 */
    private static boolean studSafe(double led, List<Integer> welds, double studOffset, double studWeldMin) {
        double lo = led - studOffset;
        double hi = led + studOffset;
        for (int w : welds) {
            if (Math.abs(lo - w) <= studWeldMin || Math.abs(hi - w) <= studWeldMin) {
                return false;
            }
        }
        return true;
    }

    // ==================== 通用工具 ====================

    /** 灯型对应的本体类 IRI。 */
    public static String lightClassIri(String lightType) {
        return "线槽灯".equals(lightType) ? TmsdVocabulary.C_TRAY_LIGHT : TmsdVocabulary.C_WELDED_LIGHT;
    }

    /** 老程序 {@code lampLay}/{@code lasupport2w} 的避焊缝语义：返回距最近焊缝的距离。 */
    public static double minDistance(double h, List<Integer> welds) {
        double min = Double.POSITIVE_INFINITY;
        for (int w : welds) {
            min = Math.min(min, Math.abs(h - w));
        }
        return min;
    }

    /** 写入 {@code H{i}_X} 增量与 {@code H{i}_X_Exist}（H1..H20，缺失补 no）。 */
    private static void putIncrements(Map<String, Object> params, String prefix, String suffix,
                                      List<Double> increments) {
        for (int i = 0; i < increments.size(); i++) {
            params.put(prefix + (i + 1) + "_" + suffix, increments.get(i));
        }
        for (int i = 1; i <= EXIST_SLOTS; i++) {
            params.put(prefix + i + "_" + suffix + "_Exist", i <= increments.size() ? "yes" : "no");
        }
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** 去掉小数点（整数原样输出）。 */
    private static String trim(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
