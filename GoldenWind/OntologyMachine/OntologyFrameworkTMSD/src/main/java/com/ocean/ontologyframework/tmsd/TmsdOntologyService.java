package com.ocean.ontologyframework.tmsd;

import com.ocean.openlletresolver.BackendService;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 塔架中段设计的本体集成服务。
 *
 * <p><b>职责</b>（使用 {@link BackendService} 的方式）：
 * <ol>
 *   <li>把一次设计（输入 + 结果）实例化为 {@code TowerMidSection.owl} 的 ABox 个体
 *       （TowerMidSection / TowerTube / Platform / Ladder / Accessory / CableBracket /
 *       Light / Support / Elevator / WeldSeam / TubeSection）；</li>
 *   <li>调用 {@link BackendService#validateAxioms(Set)} 做<b>临时注入 → 一致性校验 → 立即清除</b>，
 *       用 Openllet 推理机对本体 20 条 Restriction（12 条数值 + 8 条基数）做一致性判定；</li>
 *   <li>用推理机推断个体所属的灯类型 / 升降机类型，作为「步骤 5.4 灯型」「步骤 9 升降机」的
 *       本体侧确认。</li>
 * </ol>
 *
 * <p><b>铁律</b>：本类只读本体、只通过 {@link BackendService} 的公开 API 操作，
 * 不修改 {@code OpenlletResolver} / {@code OntopOBDAHandler} / 本体文件。
 */
public class TmsdOntologyService {

    private static final Logger log = LoggerFactory.getLogger(TmsdOntologyService.class);

    private final BackendService backendService;
    private final OWLDataFactory df;

    public TmsdOntologyService(BackendService backendService) {
        this.backendService = backendService;
        this.df = backendService.getOntologyService().getDataFactory();
    }

    /** 一次本体校验的结论。 */
    public record OntologyReport(boolean consistent, int axiomCount,
                                 List<String> inferredTypes,
                                 String summary) {
    }

    // ============================================================
    // 一致性校验
    // ============================================================

    /**
     * 对一段设计做本体一致性校验。
     *
     * <p>若设计值违反任一 Restriction，则该段的 TowerMidSection / Accessory 等个体会变为
     * <b>不可满足</b>，{@code isConsistent()} 返回 {@code false}，从而被本方法判为不通过。
     *
     * @param tag 个体名前缀（保证同一次运行内唯一）
     * @param req 设计输入
     * @param sd  段设计
     */
    public OntologyReport validate(String tag, TowerDesignRequest req,
                                   TowerDesignEngine.SectionDesign sd) {
        Set<OWLAxiom> axioms = buildAxioms(tag, req, sd);
        boolean consistent;
        try {
            consistent = backendService.validateAxioms(axioms);
        } catch (Exception e) {
            log.warn("[tmsd-ontology] 一致性校验异常，按不通过处理: {}", e.getMessage());
            consistent = false;
        }
        String summary = String.format("第%d段：注入 %d 条 ABox 公理，Openllet 一致性判定=%s",
                sd.sectionNo(), axioms.size(), consistent ? "一致（满足全部 Restriction）" : "不一致（违反 Restriction）");
        return new OntologyReport(consistent, axioms.size(), List.of(), summary);
    }

    /**
     * 用推理机推断灯类型与升降机类型（临时注入 → 查询 → 清除）。
     *
     * @return 推断出的类 local name 列表
     */
    public List<String> inferTypes(String tag, TowerDesignRequest req,
                                   TowerDesignEngine.SectionDesign sd) {
        Set<OWLAxiom> axioms = buildAxioms(tag, req, sd);
        List<String> types = new ArrayList<>();
        try {
            backendService.getOntologyService().getManager()
                    .addAxioms(backendService.getOntologyService().gettBoxOntology(), axioms);
            backendService.getReasonerService().getReasoner().flush();
            for (String local : new String[]{"Light", "Elevator", "Accessory"}) {
                OWLNamedIndividual ind = df.getOWLNamedIndividual(
                        IRI.create(TmsdVocabulary.ind(tag + "_" + local)));
                backendService.getReasonerService().getReasoner()
                        .getTypes(ind, false).getFlattened()
                        .forEach(c -> types.add(c.getIRI().getFragment()));
            }
        } catch (Exception e) {
            log.warn("[tmsd-ontology] 类型推断异常: {}", e.getMessage());
        } finally {
            backendService.getOntologyService().getManager()
                    .removeAxioms(backendService.getOntologyService().gettBoxOntology(), axioms);
            backendService.getReasonerService().getReasoner().flush();
        }
        return types;
    }

    // ============================================================
    // 净距回读（Phase 3：判定声明式化，OWL 等价类分类）
    // ============================================================

    /**
     * 逐项约束判定（生产路径：全部由 Openllet 分类回读，Java 只算值）。
     *
     * <p>把一段设计的实测值物化进本体为「校验个体」，再回读结论：
     * <ul>
     *   <li><b>净距 / 无附件段</b>：沿用 {@code :WeldClearanceCheck} 两两复核个体，
     *       回读 {@code :约束违规} / {@code :无附件段}；</li>
     *   <li><b>其余 14 项数值约束</b>：每项一个 {@code :ValueCheck}（数值约束校验）个体
     *       （断言 {@code :checkProperty} 与 {@code :checkValue}），回读对应「合规等价类」
     *       是否包含该个体。</li>
     * </ul>
     * <b>临时注入 → flush（Openllet 分类）→ 回读 → 立即清除</b>。任一步骤异常返回 {@code null}，
     * 由调用方退回 Java 兜底（{@link TmsdDesignPipeline#javaVerdicts}），保证离线测试路径不受影响。
     */
    public TmsdDesignPipeline.ConstraintVerdicts assessAll(String tag, TowerDesignRequest req,
                                                          TowerDesignEngine.SectionDesign sd) {
        List<OWLNamedIndividual> accChecks = new ArrayList<>();
        List<OWLNamedIndividual> studChecks = new ArrayList<>();
        Set<OWLAxiom> axioms = buildClearanceAxioms(tag, req, sd, accChecks, studChecks);
        List<ValueCheckRef> items = new ArrayList<>();
        addValueChecks(axioms, items, tag, req, sd);

        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLOntologyManager manager = backendService.getOntologyService().getManager();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        try {
            manager.addAxioms(tbox, axioms);
            reasoner.flush();

            Map<String, Boolean> pass = new LinkedHashMap<>();
            Set<OWLNamedIndividual> flagged =
                    reasoner.getInstances(cls(TmsdVocabulary.C_CONSTRAINT_VIOLATION), false).getFlattened();
            boolean accessorySafe = Collections.disjoint(flagged, accChecks);
            boolean studSafe = Collections.disjoint(flagged, studChecks);
            boolean noAccessory = reasoner.getInstances(cls(TmsdVocabulary.C_NO_ACCESSORY_SECTION), false)
                    .getFlattened().contains(ind(tag + "_TowerMidSection"));

            // 退化情形对齐：焊接灯配置下若一段内一个灯都排不出（无螺柱可校验），
            // Java 参考口径判为不通过（minLightStudToWeldDistance=NaN ⇒ 不满足 >下限）。
            // 该「必须有灯」的结构性前提 OWL 未建模，故在此对齐，数值净距判定仍全部声明式。
            boolean welded = TmsdVocabulary.C_WELDED_LIGHT.equals(TowerDesignEngine.lightClassIri(sd.lightType()));
            if (welded && sd.lightHeights().isEmpty()) {
                studSafe = false;
            }
            pass.put("accessoryToWeldDistance", accessorySafe);
            pass.put("lightStudToWeldDistance", studSafe);

            for (ValueCheckRef ref : items) {
                boolean ok = reasoner.getInstances(cls(ref.compliantIri()), false).getFlattened()
                        .contains(ref.ind());
                pass.merge(ref.prop(), ok, (a, b) -> a && b);
            }
            return new TmsdDesignPipeline.ConstraintVerdicts(noAccessory, pass);
        } catch (Exception e) {
            log.warn("[tmsd-ontology] 逐项声明式回读失败，退回 Java 兜底: {}", e.getMessage());
            return null;
        } finally {
            manager.removeAxioms(tbox, axioms);
            reasoner.flush();
        }
    }

    /** 逐项合规校验个体的引用（项标识 / 合规类 IRI / 个体）。 */
    private record ValueCheckRef(String prop, String compliantIri, OWLNamedIndividual ind) {
    }

    /**
     * 为一行的逐项数值约束构建「校验个体」（断言 {@code :ValueCheck} / {@code :checkProperty} / {@code :checkValue}）。
     *
     * <p>适配性（不适用项不建个体，回读时默认通过）与 {@link TmsdDesignPipeline#javaVerdicts} 对齐：
     * 无附件段跳过附件相关；单灯跳过灯间距；非焊接灯跳过灯螺柱间距；无扶持跳过扶持到焊缝距离。
     */
    private void addValueChecks(Set<OWLAxiom> ax, List<ValueCheckRef> items, String tag,
                                TowerDesignRequest req, TowerDesignEngine.SectionDesign sd) {
        boolean noAccessory = sd.accessoryHeights().isEmpty();
        boolean welded = TmsdVocabulary.C_WELDED_LIGHT.equals(TowerDesignEngine.lightClassIri(sd.lightType()));

        item(ax, items, tag, "platformToTopDistance", TmsdVocabulary.C_PLATFORM_TO_TOP_COMPLIANT,
                sd.platformDistance(), null);
        if (!noAccessory) {
            item(ax, items, tag, "accessoryCenterSpacing", TmsdVocabulary.C_ACCESSORY_SPACING_COMPLIANT,
                    TmsdDesignPipeline.minSpacing(sd.accessoryHeights()), "min");
            item(ax, items, tag, "accessoryCenterSpacing", TmsdVocabulary.C_ACCESSORY_SPACING_COMPLIANT,
                    TmsdDesignPipeline.maxSpacing(sd.accessoryHeights()), "max");
            item(ax, items, tag, "firstAccessoryToBottom", TmsdVocabulary.C_FIRST_ACCESSORY_TO_BOTTOM_COMPLIANT,
                    sd.firstAccessoryToBottom(), null);
            item(ax, items, tag, "secondLastToPlatform", TmsdVocabulary.C_SECOND_LAST_TO_PLATFORM_COMPLIANT,
                    sd.secondLastToPlatform(), null);
            item(ax, items, tag, "lastBracketToPlatform", TmsdVocabulary.C_LAST_BRACKET_TO_PLATFORM_COMPLIANT,
                    sd.lastBracketToPlatform(), null);
        }
        item(ax, items, tag, "firstLightHeight", TmsdVocabulary.C_FIRST_LIGHT_HEIGHT_COMPLIANT,
                sd.firstLightHeight(), null);
        if (sd.lightHeights().size() >= 2) {
            item(ax, items, tag, "lightToLightMinSpacing", TmsdVocabulary.C_LIGHT_MIN_SPACING_COMPLIANT,
                    TmsdDesignPipeline.minSpacing(sd.lightHeights()), null);
            item(ax, items, tag, "lightToLightMaxSpacing", TmsdVocabulary.C_LIGHT_MAX_SPACING_COMPLIANT,
                    TmsdDesignPipeline.maxSpacing(sd.lightHeights()), null);
        }
        if (welded) {
            item(ax, items, tag, "lightStudSpacing", TmsdVocabulary.C_LIGHT_STUD_SPACING_COMPLIANT,
                    sd.lightStudSpacing(), null);
        }
        if (req.elevatorType().hasSupport()) {
            item(ax, items, tag, "supportToWeldDistance", TmsdVocabulary.C_SUPPORT_TO_WELD_COMPLIANT,
                    sd.supportToWeldDistance(), null);
        }
        item(ax, items, tag, "rungSpacing", TmsdVocabulary.C_RUNG_SPACING_COMPLIANT, sd.rungSpacing(), null);
        item(ax, items, tag, "firstRungToBottomFlange", TmsdVocabulary.C_FIRST_RUNG_TO_BOTTOM_COMPLIANT,
                sd.firstRungToBottom(), null);
        item(ax, items, tag, "lastBracketToTopFlange", TmsdVocabulary.C_LAST_BRACKET_TO_TOP_COMPLIANT,
                TmsdVocabulary.num("lastBracketToTopFlange"), null);
        item(ax, items, tag, "lightningStudFlangeDistance", TmsdVocabulary.C_LIGHTNING_STUD_COMPLIANT,
                TmsdVocabulary.num("lightningStudFlangeDistance"), null);
    }

    /** 构建一个校验个体；{@code value} 为 {@code NaN}（约束不适用）时跳过。 */
    private void item(Set<OWLAxiom> ax, List<ValueCheckRef> items, String tag, String prop,
                      String compliantIri, double value, String suffix) {
        if (Double.isNaN(value)) {
            return;
        }
        OWLNamedIndividual c = ind(tag + "_Chk_" + prop + (suffix == null ? "" : "_" + suffix));
        ax.add(cls(c, TmsdVocabulary.C_VALUE_CHECK));
        ax.add(dp(c, TmsdVocabulary.DP_CHECK_PROPERTY, prop));
        ax.add(dpDec(c, TmsdVocabulary.DP_CHECK_VALUE, value));
        items.add(new ValueCheckRef(prop, compliantIri, c));
    }

    /**
     * 构建一段设计的「净距两两复核」ABox：每个 (附件, 环焊缝) / (灯螺柱, 环焊缝) 生成一个
     * {@code :WeldClearanceCheck} 个体，附 {@code :净距}（附件为「中心距 − 踏棍宽度/2」的边缘净距；
     * 螺柱为「灯位 ± 螺柱间距/2」到焊缝的距离）；净距下限 100 已常量化进本体等价类；
     * 另断言本段 {@code :附件数量} 供 OWL 等价类推出 {@code :无附件段}。
     *
     * @param accChecks  出参：收集「校验附件」复核个体（供回读分类，非空即代表该段有附件）
     * @param studChecks 出参：收集「校验螺柱」复核个体（供回读分类）
     */
    private Set<OWLAxiom> buildClearanceAxioms(String tag, TowerDesignRequest req,
                                               TowerDesignEngine.SectionDesign sd,
                                               List<OWLNamedIndividual> accChecks,
                                               List<OWLNamedIndividual> studChecks) {
        Set<OWLAxiom> ax = new LinkedHashSet<>();

        OWLNamedIndividual mid = ind(tag + "_TowerMidSection");
        ax.add(cls(mid, TmsdVocabulary.C_TOWER_MID_SECTION));
        ax.add(dp(mid, TmsdVocabulary.DP_ACCESSORY_COUNT, sd.accessoryCount()));

        List<Integer> welds = req.weldPositions(sd.sectionNo());

        // ---- 附件：边缘净距 = 附件中心到焊缝距离 − 边际偏移（踏棍宽度/2） ----
        double edgeOffset = req.layoutReference().middleSection(sd.sectionNo()).rungWidth() / 2.0;
        List<Double> accHeights = sd.accessoryHeights();
        for (int i = 0; i < accHeights.size(); i++) {
            for (int j = 0; j < welds.size(); j++) {
                OWLNamedIndividual c = ind(tag + "_ChkAcc_" + (i + 1) + "_" + (j + 1));
                accChecks.add(c);
                ax.add(cls(c, TmsdVocabulary.C_WELD_CLEARANCE_CHECK));
                ax.add(dpDec(c, TmsdVocabulary.DP_CLEARANCE,
                        Math.abs(accHeights.get(i) - welds.get(j)) - edgeOffset));
            }
        }

        // ---- 焊接灯螺柱：两螺柱位于灯位 ± lightStudSpacing/2 ----
        boolean welded = TmsdVocabulary.C_WELDED_LIGHT.equals(TowerDesignEngine.lightClassIri(sd.lightType()));
        if (welded) {
            double studOffset = sd.lightStudSpacing() / 2.0;
            List<Double> lights = sd.lightHeights();
            for (int i = 0; i < lights.size(); i++) {
                for (int k = 0; k < 2; k++) {
                    double studPos = lights.get(i) + (k == 0 ? -studOffset : studOffset);
                    for (int j = 0; j < welds.size(); j++) {
                        OWLNamedIndividual c = ind(tag + "_ChkStud_" + (i + 1) + "_" + (k + 1) + "_" + (j + 1));
                        studChecks.add(c);
                        ax.add(cls(c, TmsdVocabulary.C_WELD_CLEARANCE_CHECK));
                        ax.add(dpDec(c, TmsdVocabulary.DP_CLEARANCE, Math.abs(studPos - welds.get(j))));
                    }
                }
            }
        }
        return ax;
    }

    // ============================================================
    // ABox 构建
    // ============================================================

    /**
     * 构建一段设计的 ABox 公理集合。
     *
     * <p>结构与本体 Restriction 一一对应：
     * <ul>
     *   <li>{@code TowerMidSection ⊑ =1 hasTowerTube / =1 hasPlatform / =1 hasLadder /
     *       >=1 hasAccessory}；</li>
     *   <li>{@code RopeGuidedElevator ⊑ >=1 hasSupport.Support}、
     *       {@code LadderGuidedElevator ⊑ =0 hasSupport.Support}；</li>
     *   <li>{@code WeldedLight ⊑ =2 mountedOnStud.Stud}。</li>
     * </ul>
     */
    public Set<OWLAxiom> buildAxioms(String tag, TowerDesignRequest req,
                                     TowerDesignEngine.SectionDesign sd) {
        Set<OWLAxiom> ax = new LinkedHashSet<>();

        OWLNamedIndividual mid = ind(tag + "_TowerMidSection");
        ax.add(cls(mid, TmsdVocabulary.C_TOWER_MID_SECTION));

        // ---- 筒体 / 平台 / 爬梯 ----
        OWLNamedIndividual tube = ind(tag + "_TowerTube");
        ax.add(cls(tube, TmsdVocabulary.C_TOWER_TUBE));
        ax.add(obj(mid, TmsdVocabulary.OP_HAS_TOWER_TUBE, tube));

        OWLNamedIndividual platform = ind(tag + "_Platform");
        ax.add(cls(platform, TmsdVocabulary.C_PLATFORM));
        ax.add(obj(mid, TmsdVocabulary.OP_HAS_PLATFORM, platform));
        add(ax, dp(platform, TmsdVocabulary.DP_PLATFORM_TO_TOP_DISTANCE, sd.platformDistance()));
        add(ax, dp(platform, TmsdVocabulary.DP_PLATFORM_LOCATION_INNER_DIAMETER,
                sd.platformInnerDiameter()));

        OWLNamedIndividual ladder = ind(tag + "_Ladder");
        ax.add(cls(ladder, TmsdVocabulary.C_LADDER));
        ax.add(obj(mid, TmsdVocabulary.OP_HAS_LADDER, ladder));
        add(ax, dp(ladder, TmsdVocabulary.DP_LADDER_HEIGHT, sd.ladderLength()));

        // ---- 升降机（步骤9）：钢绳导向需扶持，爬梯导向无扶持 ----
        OWLNamedIndividual elevator = ind(tag + "_Elevator");
        ax.add(cls(elevator, req.elevatorType() == ElevatorType.ROPE_GUIDED
                ? TmsdVocabulary.C_ROPE_GUIDED_ELEVATOR : TmsdVocabulary.C_LADDER_GUIDED_ELEVATOR));
        ax.add(obj(mid, TmsdVocabulary.OP_HAS_ELEVATOR, elevator));
        if (req.elevatorType().hasSupport()) {
            OWLNamedIndividual support = ind(tag + "_Support");
            ax.add(cls(support, TmsdVocabulary.C_SUPPORT));
            ax.add(obj(elevator, TmsdVocabulary.OP_HAS_SUPPORT, support));
            add(ax, dp(support, TmsdVocabulary.DP_SUPPORT_TO_WELD_DISTANCE, sd.supportToWeldDistance()));
            add(ax, dp(support, TmsdVocabulary.DP_SUPPORT_POSITION, sd.supportHeight()));
        }

        // ---- 附件（步骤5）：爬梯支撑（LadderSupport ⊑ Accessory） ----
        List<Double> accHeights = sd.accessoryHeights();
        for (int i = 0; i < accHeights.size(); i++) {
            OWLNamedIndividual a = ind(tag + "_LadderSupport_" + (i + 1));
            ax.add(cls(a, TmsdVocabulary.C_LADDER_SUPPORT));
            ax.add(obj(mid, TmsdVocabulary.OP_HAS_ACCESSORY, a));
            add(ax, dp(a, TmsdVocabulary.DP_LADDER_SUPPORT_POSITION, accHeights.get(i)));
            add(ax, dp(a, TmsdVocabulary.DP_LADDER_SUPPORT_LENGTH, sd.ladderSupportLength()));
            add(ax, dp(a, TmsdVocabulary.DP_ACCESSORY_TO_WELD_DISTANCE, sd.minAccessoryToWeldDistance()));
            add(ax, dp(a, TmsdVocabulary.DP_ACCESSORY_CENTER_SPACING, sd.accessorySpacing()));
            add(ax, dp(a, TmsdVocabulary.DP_FIRST_ACCESSORY_TO_BOTTOM, sd.firstAccessoryToBottom()));
            add(ax, dp(a, TmsdVocabulary.DP_SECOND_LAST_TO_PLATFORM, sd.secondLastToPlatform()));
            add(ax, dp(a, TmsdVocabulary.DP_ACCESSORY_CONNECTION_TYPE, sd.connectionType()));
            add(ax, dp(a, TmsdVocabulary.DP_ACCESSORY_TYPE, sd.accessoryType()));
        }

        // ---- 电缆托架（CableBracket ⊑ Accessory） ----
        List<Double> brackets = sd.cableClampHeights();
        for (int i = 0; i < brackets.size(); i++) {
            OWLNamedIndividual b = ind(tag + "_CableBracket_" + (i + 1));
            ax.add(cls(b, TmsdVocabulary.C_CABLE_BRACKET));
            ax.add(obj(mid, TmsdVocabulary.OP_HAS_ACCESSORY, b));
            add(ax, dp(b, TmsdVocabulary.DP_BRACKET_POSITION, brackets.get(i)));
            add(ax, dp(b, TmsdVocabulary.DP_BRACKET_LENGTH, sd.bracketLength()));
            add(ax, dp(b, TmsdVocabulary.DP_BRACKET_RIGHT_CHORD, sd.bracketRightChord()));
            add(ax, dp(b, TmsdVocabulary.DP_BRACKET_LEFT_CHORD, sd.bracketLeftChord()));
            add(ax, dp(b, TmsdVocabulary.DP_LAST_BRACKET_TO_PLATFORM, sd.lastBracketToPlatform()));
            add(ax, dp(b, TmsdVocabulary.DP_ACCESSORY_TO_WELD_DISTANCE, sd.minAccessoryToWeldDistance()));
        }

        // ---- 照明灯（步骤5.4~5.6）：焊接灯 =2 螺柱；线槽灯无螺柱 ----
        boolean welded = TmsdVocabulary.C_WELDED_LIGHT.equals(TowerDesignEngine.lightClassIri(sd.lightType()));
        List<Double> lights = sd.lightHeights();
        for (int i = 0; i < lights.size(); i++) {
            OWLNamedIndividual l = ind(tag + "_Light_" + (i + 1));
            ax.add(cls(l, welded ? TmsdVocabulary.C_WELDED_LIGHT : TmsdVocabulary.C_TRAY_LIGHT));
            ax.add(obj(mid, TmsdVocabulary.OP_HAS_LIGHT, l));
            add(ax, dp(l, TmsdVocabulary.DP_LIGHT_TYPE, sd.lightType()));
            if (welded) {
                add(ax, dp(l, TmsdVocabulary.DP_LIGHT_STUD_SPACING, sd.lightStudSpacing()));
                add(ax, dp(l, TmsdVocabulary.DP_LIGHT_STUD_TO_WELD_DISTANCE, sd.minLightStudToWeldDistance()));
                for (int k = 1; k <= 2; k++) {
                    OWLNamedIndividual stud = ind(tag + "_Stud_" + (i + 1) + "_" + k);
                    ax.add(cls(stud, TmsdVocabulary.C_STUD));
                    ax.add(obj(l, TmsdVocabulary.OP_MOUNTED_ON_STUD, stud));
                }
            }
        }
        if (!lights.isEmpty()) {
            add(ax, dp(mid, TmsdVocabulary.DP_FIRST_LIGHT_HEIGHT, sd.firstLightHeight()));
            if (lights.size() >= 2) {
                add(ax, dp(mid, TmsdVocabulary.DP_LIGHT_TO_LIGHT_MIN_SPACING,
                        TmsdDesignPipeline.minSpacing(lights)));
                add(ax, dp(mid, TmsdVocabulary.DP_LIGHT_TO_LIGHT_MAX_SPACING,
                        TmsdDesignPipeline.maxSpacing(lights)));
            }
        }

        // ---- 环焊缝（WeldSeam ⊑ =2 connectsTubeSection.TubeSection） ----
        // 注意：:有环焊缝（hasWeldSeam）的 rdfs:domain 是 :塔筒（TowerTube），不是 :塔架中段。
        // 若误挂在 mid（TowerMidSection）上，域推理会把它归为 TowerTube，而 TowerMidSection ⊑ Tower
        // 且 Tower 与 TowerTube 不相交 → 个体不可满足 → 本体不一致（已由冲突解释验证）。
        List<Integer> welds = req.geometry().weldPositions(sd.sectionNo() - 1);
        for (int i = 0; i < welds.size(); i++) {
            OWLNamedIndividual w = ind(tag + "_WeldSeam_" + (i + 1));
            ax.add(cls(w, TmsdVocabulary.C_WELD_SEAM));
            ax.add(obj(tube, TmsdVocabulary.OP_HAS_WELD_SEAM, w));
            add(ax, dp(w, TmsdVocabulary.DP_WELD_SEAM_POSITION, welds.get(i)));
            for (int k = 1; k <= 2; k++) {
                OWLNamedIndividual ts = ind(tag + "_TubeSection_" + (i + 1) + "_" + k);
                ax.add(cls(ts, TmsdVocabulary.C_TUBE_SECTION));
                add(ax, dp(ts, TmsdVocabulary.DP_TUBE_SECTION_NUMBER, (i + 1) * 10 + k));
                ax.add(obj(w, TmsdVocabulary.OP_CONNECTS_TUBE_SECTION, ts));
            }
        }

        // ---- 机型 / 区域（hasKey 身份识别） ----
        OWLNamedIndividual model = ind(tag + "_Model_" + req.model());
        ax.add(cls(model, TmsdVocabulary.C_MODEL));
        add(ax, dp(model, TmsdVocabulary.DP_MODEL_NAME, req.model()));
        ax.add(obj(mid, TmsdVocabulary.OP_HAS_MODEL, model));

        OWLNamedIndividual region = ind(tag + "_Region_" + req.region());
        ax.add(cls(region, TmsdVocabulary.C_REGION));
        add(ax, dp(region, TmsdVocabulary.DP_REGION_NAME, req.region()));
        ax.add(obj(mid, TmsdVocabulary.OP_HAS_REGION, region));

        return ax;
    }

    // ============================================================
    // 工具
    // ============================================================

    private OWLNamedIndividual ind(String localName) {
        return df.getOWLNamedIndividual(IRI.create(TmsdVocabulary.ind(localName)));
    }

    /** 加入公理集合；{@code null}（约束不适用）时跳过。 */
    private static void add(Set<OWLAxiom> ax, OWLAxiom axiom) {
        if (axiom != null) {
            ax.add(axiom);
        }
    }

    private OWLClass cls(String iri) {
        return df.getOWLClass(IRI.create(iri));
    }

    private OWLAxiom cls(OWLNamedIndividual i, String classIri) {
        return df.getOWLClassAssertionAxiom(cls(classIri), i);
    }

    private OWLAxiom obj(OWLNamedIndividual s, String propIri, OWLNamedIndividual o) {
        OWLObjectProperty p = df.getOWLObjectProperty(IRI.create(propIri));
        return df.getOWLObjectPropertyAssertionAxiom(p, s, o);
    }

    /**
     * 数值型数据属性断言；{@code NaN} 时跳过（约束不适用）。
     *
     * <p><b>为何按整毫米（{@code xsd:integer}）断言</b>：本体中所有数值数据属性都声明了
     * {@code rdfs:range}，且 12 条数值约束用 {@code owl:allValuesFrom} + {@code xsd:integer}
     * facet 表达（如 {@code accessoryCenterSpacing ∈ [1400,1960]}、{@code supportToWeldDistance > 100}）。
     * 若以 {@code xsd:double}（如 1786.3）断言，该值不属于 {@code xsd:integer} 值空间，
     * 个体将变为不可满足 → 本体不一致；同理 {@code xsd:double} 也不属于 {@code xsd:decimal}
     * 值域（{@code supportPosition} / {@code ladderSupportPosition} / {@code bracketPosition}）。
     * 而 {@code xsd:integer} 的值空间是 {@code xsd:decimal} 的子集，故统一按整毫米断言
     * 可同时满足 integer 与 decimal 两类值域，也与老工具（towerdesign）以整毫米输出模型参数的
     * 约定一致。
     */
    private OWLAxiom dp(OWLNamedIndividual s, String propIri, double v) {
        if (Double.isNaN(v)) {
            return null;
        }
        OWLDataProperty p = df.getOWLDataProperty(IRI.create(propIri));
        return df.getOWLDataPropertyAssertionAxiom(p, s, df.getOWLLiteral((int) Math.round(v)));
    }

    private OWLAxiom dp(OWLNamedIndividual s, String propIri, int v) {
        OWLDataProperty p = df.getOWLDataProperty(IRI.create(propIri));
        return df.getOWLDataPropertyAssertionAxiom(p, s, df.getOWLLiteral(v));
    }

    /**
     * 数值型数据属性断言（精确十进制）：用于不受 Restriction 约束的 {@code :净距} / {@code :净距下限}，
     * 需保留小数（如边缘净距 = 中心距 − 踏棍宽度/2）以与 Java 判定逐值等价。
     */
    private OWLAxiom dpDec(OWLNamedIndividual s, String propIri, double v) {
        OWLDataProperty p = df.getOWLDataProperty(IRI.create(propIri));
        return df.getOWLDataPropertyAssertionAxiom(p, s,
                df.getOWLLiteral(BigDecimal.valueOf(v).toPlainString(),
                        df.getOWLDatatype(OWL2Datatype.XSD_DECIMAL)));
    }

    private OWLAxiom dp(OWLNamedIndividual s, String propIri, String v) {
        if (v == null) {
            return null;
        }
        OWLDataProperty p = df.getOWLDataProperty(IRI.create(propIri));
        return df.getOWLDataPropertyAssertionAxiom(p, s, df.getOWLLiteral(v));
    }
}
