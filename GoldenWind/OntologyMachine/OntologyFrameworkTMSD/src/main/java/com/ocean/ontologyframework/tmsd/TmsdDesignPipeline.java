package com.ocean.ontologyframework.tmsd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 塔架中段设计流水线（完全正向）：对一组设计输入求解正向方案，并逐条校验本体约束。
 *
 * <p>正向流程不依赖历史设计匹配：{@link TowerDesignEngine#variants()} 给出单一正向方案
 * （附件间距贪心取大：每步优先最大档位，避焊缝才降档），本类对该方案求解全部中段，并用
 * {@link TmsdVocabulary#numericConstraints()}（运行时刻解析自 {@code TowerMidSection.owl} 的
 * 可数值校验 Restriction）逐条判定是否满足，产出「推荐方案」。
 *
 * <p><b>约束判定口径</b>（与本体 Restriction 一一对应，不发明）：
 * <ul>
 *   <li>{@code hasValue v} → 实际值必须等于 v；</li>
 *   <li>{@code >= a 且 <= b} → 实际值必须落在闭区间；</li>
 *   <li>{@code > a} → 实际值必须严格大于 a。</li>
 * </ul>
 * 附件中心间距（{@code accessoryCenterSpacing}）与灯间距（{@code lightToLight*Spacing}）
 * 是「相邻间距」类约束，故取全部相邻间距的最小值与最大值同时判定。
 */
public final class TmsdDesignPipeline {

    private TmsdDesignPipeline() {
    }

    /** 一条约束的校验结果。 */
    public record ConstraintCheck(int sectionNo, String hostClass, String property, String raw,
                                  String actual, boolean pass, String note) {
    }

    /**
     * 一段设计的<b>逐项约束判定结论</b>：{@code noAccessory} 为「无附件段」状态；
     * {@code pass} 为「约束属性 local name → 是否通过」，由本体 Openllet 回读提供（生产路径）
     * 或等价 Java 兜底提供（离线测试路径）。缺失键视为通过（即该约束不适用）。
     */
    public record ConstraintVerdicts(boolean noAccessory, Map<String, Boolean> pass) {

        /** 取某项判定；缺失（不适用）一律视为通过。 */
        public boolean get(String prop) {
            return pass.getOrDefault(prop, Boolean.TRUE);
        }
    }

    /**
     * 逐项约束判定来源。
     *
     * <p>生产路径由 {@code TmsdOntologyService} 的 {@code assessAll} 提供——把各项实测值
     * 物化进本体校验个体，回读 OWL 等价类（合规类）推出的结论；无本体服务时退回
     * {@link #JAVA_JUDGE} 的等价 Java 判定（仅供离线测试路径）。
     */
    public interface ConstraintJudge {
        ConstraintVerdicts assess(TowerDesignRequest req, TowerDesignEngine.SectionDesign sd);
    }

    /**
     * Java 兜底判定（与本体 OWL 等价类等价）：仅供无本体服务的离线测试/缺省路径使用。
     * 口径与本体一致——净距/无附件段/各项数值约束逐项按 {@link TmsdVocabulary} 解析结果判定。
     */
    private static final ConstraintJudge JAVA_JUDGE = TmsdDesignPipeline::javaVerdicts;

    /** Java 兜底判定（{@link #JAVA_JUDGE} 的实现，供无本体路径直接复用）。 */
    static ConstraintVerdicts javaVerdicts(TowerDesignRequest req, TowerDesignEngine.SectionDesign sd) {
        Map<String, Boolean> pass = new LinkedHashMap<>();
        boolean noAccessory = sd.accessoryHeights().isEmpty();
        boolean welded = TmsdVocabulary.C_WELDED_LIGHT.equals(TowerDesignEngine.lightClassIri(sd.lightType()));
        boolean hasSupport = req.elevatorType().hasSupport();

        // ---- 净距（与本体 :约束违规 等价类等价）----
        pass.put("accessoryToWeldDistance", noAccessory
                || TmsdVocabulary.constraint("accessoryToWeldDistance").accepts(sd.minAccessoryToWeldDistance()));
        pass.put("lightStudToWeldDistance", !welded
                || TmsdVocabulary.constraint("lightStudToWeldDistance").accepts(sd.minLightStudToWeldDistance()));

        // ---- 其余逐项数值约束 ----
        pass.put("platformToTopDistance", accepts("platformToTopDistance", sd.platformDistance()));
        if (!noAccessory) {
            double smin = minSpacing(sd.accessoryHeights());
            double smax = maxSpacing(sd.accessoryHeights());
            pass.put("accessoryCenterSpacing",
                    (Double.isNaN(smin) || smin >= TmsdVocabulary.lower("accessoryCenterSpacing") - 1e-6)
                            && (Double.isNaN(smax) || smax <= TmsdVocabulary.upper("accessoryCenterSpacing") + 1e-6));
            pass.put("firstAccessoryToBottom", accepts("firstAccessoryToBottom", sd.firstAccessoryToBottom()));
            pass.put("secondLastToPlatform", accepts("secondLastToPlatform", sd.secondLastToPlatform()));
            pass.put("lastBracketToPlatform", accepts("lastBracketToPlatform", sd.lastBracketToPlatform()));
        }
        pass.put("firstLightHeight", accepts("firstLightHeight", sd.firstLightHeight()));
        boolean singleLight = sd.lightHeights().size() < 2;
        double lmin = singleLight ? Double.NaN : minSpacing(sd.lightHeights());
        double lmax = singleLight ? Double.NaN : maxSpacing(sd.lightHeights());
        pass.put("lightToLightMinSpacing", singleLight || accepts("lightToLightMinSpacing", lmin));
        pass.put("lightToLightMaxSpacing", singleLight || accepts("lightToLightMaxSpacing", lmax));
        if (welded) {
            pass.put("lightStudSpacing", accepts("lightStudSpacing", sd.lightStudSpacing()));
        }
        if (hasSupport) {
            pass.put("supportToWeldDistance", accepts("supportToWeldDistance", sd.supportToWeldDistance()));
        }
        pass.put("rungSpacing", accepts("rungSpacing", sd.rungSpacing()));
        pass.put("firstRungToBottomFlange", accepts("firstRungToBottomFlange", sd.firstRungToBottom()));
        pass.put("lastBracketToTopFlange", accepts("lastBracketToTopFlange", TmsdVocabulary.num("lastBracketToTopFlange")));
        pass.put("lightningStudFlangeDistance", accepts("lightningStudFlangeDistance", TmsdVocabulary.num("lightningStudFlangeDistance")));
        return new ConstraintVerdicts(noAccessory, pass);
    }

    /** 一个设计备选在全部中段上的求解结果。 */
    public record VariantResult(String variantName, String note, TowerDesignEngine.DesignVariant variant,
                                Map<Integer, TowerDesignEngine.SectionDesign> sections,
                                List<ConstraintCheck> checks, boolean satisfied, List<String> failures) {

        /** 该备选下第 {@code sectionNo} 段的设计。 */
        public TowerDesignEngine.SectionDesign section(int sectionNo) {
            return sections.get(sectionNo);
        }

        /** 附件排布模式标签。 */
        public String modeLabel() {
            return variant.accessoryMode().label();
        }
    }

    /** 一个设计输入用例的完整求解结果（推荐 + 备选）。 */
    public record CaseResult(String caseName, TowerDesignRequest request, List<VariantResult> variants) {

        /** 满足全部本体约束的设计备选。 */
        public List<VariantResult> satisfied() {
            List<VariantResult> out = new ArrayList<>();
            for (VariantResult v : variants) {
                if (v.satisfied()) {
                    out.add(v);
                }
            }
            return out;
        }

        /** 被本体约束否决的设计备选。 */
        public List<VariantResult> rejected() {
            List<VariantResult> out = new ArrayList<>();
            for (VariantResult v : variants) {
                if (!v.satisfied()) {
                    out.add(v);
                }
            }
            return out;
        }

        /** 推荐方案：满足约束中附件数量最少（单件受力最大、材料最省）者；并列时取间距最大者。 */
        public VariantResult recommended() {
            VariantResult best = null;
            for (VariantResult v : satisfied()) {
                if (best == null || better(v, best)) {
                    best = v;
                }
            }
            return best;
        }

        private static boolean better(VariantResult a, VariantResult b) {
            int ca = accessoryCount(a);
            int cb = accessoryCount(b);
            if (ca != cb) {
                return ca < cb;
            }
            return spacingOf(a) > spacingOf(b);
        }

        private static int accessoryCount(VariantResult v) {
            int n = 0;
            for (TowerDesignEngine.SectionDesign sd : v.sections().values()) {
                n += sd.accessoryCount();
            }
            return n;
        }

        private static double spacingOf(VariantResult v) {
            double s = 0;
            for (TowerDesignEngine.SectionDesign sd : v.sections().values()) {
                s += sd.accessorySpacing();
            }
            return s;
        }
    }

    /** 求解一个设计输入用例的全部正向备选（净距/无附件段判定走 Java 兜底）。 */
    public static CaseResult design(TowerDesignRequest req) {
        return design(req, JAVA_JUDGE);
    }

    /** 求解一个设计输入用例的全部正向备选；逐项约束判定由 {@code judge} 提供。 */
    public static CaseResult design(TowerDesignRequest req, ConstraintJudge judge) {
        List<VariantResult> results = new ArrayList<>();
        for (TowerDesignEngine.DesignVariant v : TowerDesignEngine.variants()) {
            Map<Integer, TowerDesignEngine.SectionDesign> sections = new LinkedHashMap<>();
            for (int s : req.middleSectionNumbers()) {
                sections.put(s, TowerDesignEngine.designSection(req.geometry(), s, req, req.layoutReference(), v));
            }
            results.add(evaluate(req, v, sections, judge));
        }
        return assemble(req, results);
    }

    /**
     * 对一组「已逐步求值完毕」的段设计做本体约束校验，组装为备选结果。
     *
     * <p>供 BPMN 生产路径的 <b>S8 输出步</b> 使用：此时 S1~S7 已完成全部设计计算，
     * 逐项约束判定在此刻回读本体并逐段校验（“跑完整条流程、最后再验证”）。
     */
    public static VariantResult evaluate(TowerDesignRequest req, TowerDesignEngine.DesignVariant v,
                                         Map<Integer, TowerDesignEngine.SectionDesign> sections,
                                         ConstraintJudge judge) {
        List<ConstraintCheck> checks = new ArrayList<>();
        for (Map.Entry<Integer, TowerDesignEngine.SectionDesign> e : sections.entrySet()) {
            checks.addAll(check(e.getValue(), req, judge.assess(req, e.getValue())));
        }
        List<String> failures = new ArrayList<>();
        for (ConstraintCheck c : checks) {
            if (!c.pass()) {
                failures.add(String.format("第%d段 %s.%s = %s，违反本体约束 [%s]",
                        c.sectionNo(), c.hostClass(), c.property(), c.actual(), c.raw()));
            }
        }
        return new VariantResult(v.name(), v.note(), v, Map.copyOf(sections),
                List.copyOf(checks), failures.isEmpty(), List.copyOf(failures));
    }

    /** 组装一个用例的完整求解结果（推荐 + 备选）。 */
    public static CaseResult assemble(TowerDesignRequest req, List<VariantResult> variants) {
        return new CaseResult(req.caseName(), req, List.copyOf(variants));
    }

    // ============================================================
    // 约束校验
    // ============================================================

    /** 对一段设计逐条校验本体约束（约束数值/文案均取自 {@link TmsdVocabulary} 的解析结果）。 */
    public static List<ConstraintCheck> check(TowerDesignEngine.SectionDesign sd, TowerDesignRequest req) {
        return check(sd, req, JAVA_JUDGE.assess(req, sd));
    }

    /**
     * 对一段设计逐条校验本体约束。<b>每一项的通过与否（{@code pass}）均取自 {@code v}</b>
     * ——生产路径由本体 Openllet 分类回读提供，离线路径由等价 Java 兜底提供；
     * {@code actual} 列仍展示 Java 计算的参考值，仅供报告可读（算在 Java、判在本体）。
     */
    static List<ConstraintCheck> check(TowerDesignEngine.SectionDesign sd, TowerDesignRequest req, ConstraintVerdicts v) {
        List<ConstraintCheck> out = new ArrayList<>();
        int s = sd.sectionNo();
        boolean noAccessory = v.noAccessory();

        out.add(chk(s, "Platform", "platformToTopDistance", raw("platformToTopDistance"),
                fmt(sd.platformDistance()), v.get("platformToTopDistance"),
                "平台到筒顶距离（判定回读本体合规类 :平台到筒顶距离合规）"));

        if (noAccessory) {
            // 本样例几何下附件排布无解（n=0）：附件相关约束不适用，跳过但不否决该方案。
            String note = "附件排布无解（n=0），跳过附件相关约束";
            out.add(chk(s, "Accessory", "accessoryToWeldDistance", raw("accessoryToWeldDistance"), "不适用", true, note));
            out.add(chk(s, "Accessory", "accessoryCenterSpacing", raw("accessoryCenterSpacing"), "不适用", true, note));
            out.add(chk(s, "Accessory", "firstAccessoryToBottom", raw("firstAccessoryToBottom"), "不适用", true, note));
            out.add(chk(s, "Accessory", "secondLastToPlatform", raw("secondLastToPlatform"), "不适用", true, note));
            out.add(chk(s, "CableBracket", "lastBracketToPlatform", raw("lastBracketToPlatform"), "不适用", true, note));
        } else {
            out.add(chk(s, "Accessory", "accessoryToWeldDistance", raw("accessoryToWeldDistance"),
                    fmt(sd.minAccessoryToWeldDistance()), v.get("accessoryToWeldDistance"),
                    "附件上/下边缘与环焊缝最小距离（判定回读本体 :约束违规）"));

            double smin = minSpacing(sd.accessoryHeights());
            double smax = maxSpacing(sd.accessoryHeights());
            out.add(chk(s, "Accessory", "accessoryCenterSpacing", raw("accessoryCenterSpacing"),
                    fmt(smin) + " ~ " + fmt(smax),
                    v.get("accessoryCenterSpacing"),
                    "相邻附件中心间距（取全部相邻间距的最小~最大；判定回读本体 :附件中心间距合规）"));

            out.add(chk(s, "Accessory", "firstAccessoryToBottom", raw("firstAccessoryToBottom"),
                    fmt(sd.firstAccessoryToBottom()), v.get("firstAccessoryToBottom"),
                    "第一个附件到底部距离（判定回读本体 :第一附件到底部合规）"));

            out.add(chk(s, "Accessory", "secondLastToPlatform", raw("secondLastToPlatform"),
                    fmt(sd.secondLastToPlatform()), v.get("secondLastToPlatform"),
                    "倒数第二个配件（末组爬梯支撑）到平台距离（判定回读本体 :倒数第二附件到平台合规）"));

            out.add(chk(s, "CableBracket", "lastBracketToPlatform", raw("lastBracketToPlatform"),
                    fmt(sd.lastBracketToPlatform()), v.get("lastBracketToPlatform"),
                    "最后一个电缆托架到平台距离（平台上方；判定回读本体 :最后电缆托架到平台合规）"));
        }

        out.add(chk(s, "TowerMidSection", "firstLightHeight", raw("firstLightHeight"),
                fmt(sd.firstLightHeight()), v.get("firstLightHeight"),
                "第一个灯安装高度（判定回读本体 :第一灯安装高度合规）"));

        double lmin = sd.lightHeights().size() >= 2 ? minSpacing(sd.lightHeights()) : Double.NaN;
        double lmax = sd.lightHeights().size() >= 2 ? maxSpacing(sd.lightHeights()) : Double.NaN;
        boolean singleLight = sd.lightHeights().size() < 2;
        out.add(chk(s, "TowerMidSection", "lightToLightMinSpacing", raw("lightToLightMinSpacing"),
                singleLight ? "仅 1 个灯（无间距）" : fmt(lmin),
                v.get("lightToLightMinSpacing"), "灯与灯最小间距（判定回读本体 :灯与灯最小间距合规）"));
        out.add(chk(s, "TowerMidSection", "lightToLightMaxSpacing", raw("lightToLightMaxSpacing"),
                singleLight ? "仅 1 个灯（无间距）" : fmt(lmax),
                v.get("lightToLightMaxSpacing"), "灯与灯最大间距（判定回读本体 :灯与灯最大间距合规）"));

        boolean welded = TmsdVocabulary.C_WELDED_LIGHT.equals(TowerDesignEngine.lightClassIri(sd.lightType()));
        if (welded) {
            out.add(chk(s, "WeldedLight", "lightStudSpacing", raw("lightStudSpacing"),
                    fmt(sd.lightStudSpacing()), v.get("lightStudSpacing"),
                    "灯螺柱间距（判定回读本体 :灯螺柱间距合规）"));
            out.add(chk(s, "WeldedLight", "lightStudToWeldDistance", raw("lightStudToWeldDistance"),
                    fmt(sd.minLightStudToWeldDistance()), v.get("lightStudToWeldDistance"),
                    "灯螺柱与环焊缝最小距离（判定回读本体 :约束违规）"));
        }

        if (req.elevatorType().hasSupport()) {
            out.add(chk(s, "Support", "supportToWeldDistance", raw("supportToWeldDistance"),
                    fmt(sd.supportToWeldDistance()), v.get("supportToWeldDistance"),
                    "扶持与环焊缝最小距离（判定回读本体 :扶持到焊缝距离合规）"));
        }

        out.add(chk(s, "Ladder", "rungSpacing", raw("rungSpacing"),
                fmt(sd.rungSpacing()), v.get("rungSpacing"), "梯档间距（判定回读本体 :梯档间距合规）"));
        out.add(chk(s, "Ladder", "firstRungToBottomFlange", raw("firstRungToBottomFlange"),
                fmt(sd.firstRungToBottom()), v.get("firstRungToBottomFlange"),
                "第一个踏棍到下法兰距离（判定回读本体 :第一踏棍到下法兰合规）"));
        double cableTopH = TmsdVocabulary.num("lastBracketToTopFlange");
        out.add(chk(s, "CableBracket", "lastBracketToTopFlange", raw("lastBracketToTopFlange"),
                fmt(cableTopH), v.get("lastBracketToTopFlange"),
                "最后一组电缆夹板到顶法兰上端面距离（引擎即取本体值；判定回读本体 :最后电缆夹板到顶法兰合规）"));
        double studFlangeDistance = TmsdVocabulary.num("lightningStudFlangeDistance");
        out.add(chk(s, "LightningGroundingStud", "lightningStudFlangeDistance", raw("lightningStudFlangeDistance"),
                fmt(studFlangeDistance), v.get("lightningStudFlangeDistance"),
                "防雷螺柱距法兰面距离（判定回读本体 :防雷螺柱距法兰面距离合规）"));
        return out;
    }

    /** 取自本体的约束判定。 */
    private static boolean accepts(String prop, double v) {
        return TmsdVocabulary.constraint(prop).accepts(v);
    }

    /** 取自本体的约束原始文案。 */
    private static String raw(String prop) {
        return TmsdVocabulary.constraint(prop).raw();
    }

    // ============================================================
    // 工具
    // ============================================================

    /** 相邻值间距的最小值；元素少于 2 个时返回 {@link Double#NaN}。 */
    public static double minSpacing(List<Double> values) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 1; i < values.size(); i++) {
            min = Math.min(min, values.get(i) - values.get(i - 1));
        }
        return values.size() < 2 ? Double.NaN : min;
    }

    /** 相邻值间距的最大值；元素少于 2 个时返回 {@link Double#NaN}。 */
    public static double maxSpacing(List<Double> values) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 1; i < values.size(); i++) {
            max = Math.max(max, values.get(i) - values.get(i - 1));
        }
        return values.size() < 2 ? Double.NaN : max;
    }

    private static ConstraintCheck chk(int sectionNo, String host, String prop, String raw,
                                       String actual, boolean pass, String note) {
        return new ConstraintCheck(sectionNo, host, prop, raw, actual, pass, note);
    }

    private static String fmt(double v) {
        if (Double.isNaN(v)) {
            return "不适用";
        }
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.format("%.1f", v);
    }
}
