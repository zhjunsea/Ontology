package com.ocean.ontologyframework;

import com.ocean.ontologyframework.tcm.InferenceResult;
import com.ocean.ontologyframework.tcm.SixChannelInferenceEngine;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.apache.jena.rdf.model.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Profile("TCMBPMN")
public class TCMOntologyJobWorker {

    private static final Logger log = LoggerFactory.getLogger(TCMOntologyJobWorker.class);

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;

    private InsertService insertService;
    private UpdateService updateService;
    private DeleteService deleteService;
    private QueryService queryService;
    private BackendService backendService;

    // 命名空间常量
    private static final String TCM_NS = "http://www.tcm-classics.org/tcm#";
    private static final String BZ_NS = "http://www.tcm-classics.org/bingzheng#";
    private static final String ZZ_NS = "http://www.tcm-classics.org/zhengzhuangtizheng#";
    private static final String FJ_NS = "http://www.tcm-classics.org/fangji#";
    private static final String JJ_NS = "http://www.tcm-classics.org/jianjia#";
    private static final String LJ_NS = "http://www.tcm-classics.org/liujing#";
    private static final String MX_NS = "http://www.tcm-classics.org/maixiang#";
    private static final String BG_NS = "http://www.tcm-classics.org/bagang#";
    // 舌象命名空间（v5.3 新增）
    private static final String TX_NS = "http://www.tcm-classics.org/shexiang#";

    // ==================== 属性IRI常量 ====================
    private static final String HAS_SYMPTOM = TCM_NS + "has_symptom";
    private static final String HAS_PULSE = TCM_NS + "has_pulse";
    //private static final String HAS_TONGUE = TCM_NS + "has_tongue";  // v5.3 新增
    private static final String HAS_OR_SYMPTOM = BZ_NS + "has_or_symptom";
    private static final String HAS_CONCOMITANT = BZ_NS + "has_concomitant";
    private static final String BELONGS_TO_LIUJING = BZ_NS + "belongs_to_liujing";
    private static final String PATTERN_ROLE = BZ_NS + "patternRole";
    private static final String INDICATED_FOR = FJ_NS + "indicated_for";
    // 病证变体关系（v6.0 新增，用于表达合病/兼证/变证与主证的关系）
    private static final String VARIANT_OF = BZ_NS + "variantOf";

    // 六经本体属性
    private static final String LJ_HAS_TYPICAL_SYMPTOM = LJ_NS + "hasTypicalSymptom";
    private static final String LJ_HAS_TYPICAL_PULSE = LJ_NS + "hasTypicalPulse";
    private static final String LJ_HAS_TYPICAL_TONGUE = LJ_NS + "hasTypicalTongue";  // v5.3 新增
    private static final String LJ_HAS_TREATMENT_PRINCIPLE = LJ_NS + "hasTreatmentPrinciple";
    private static final String LJ_HAS_CONTRAINDICATION = LJ_NS + "hasContraindication";
    private static final String LJ_HAS_KEY_SYMPTOM = LJ_NS + "hasKeySymptom";

    // ==================== 占位个体集合 ====================
    private static final Set<String> PLACEHOLDER_SHORT_FORMS = Set.of(
            "FuRenChanHouBing",
            "BenTunQiBing",
            "FuRenZaBing"
    );

    // ================================================================
    // 枚举定义
    // ================================================================
    public enum MatchStatus {
        UNIQUE_MATCH,
        AMBIGUOUS,
        NO_MATCH
    }

    // ==================== 字段 ====================
    private SixChannelInferenceEngine sixChannelEngine;

    @PostConstruct
    public void init() {
        try {
            log.info("初始化 TCMOntologyJobWorker 依赖链...");

            OBDAHandler.init(obdaPropertiesPath, obdaPath);
            OBDAHandler obdaHandler = OBDAHandler.getInstance();
            this.backendService = BackendService.getInstance(mainOntologyPath, obdaHandler);
            if (this.backendService == null) {
                throw new IllegalStateException("BackendService 初始化失败，请检查本体路径和 OBDA 连接");
            }

            this.insertService = new InsertService(this.backendService);
            this.updateService = new UpdateService(this.backendService);
            this.deleteService = new DeleteService(this.backendService);
            this.queryService = new QueryService(this.backendService);

            log.info("TCMOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);

            // 加载方剂 ABox 数据到推理器
            try {
                loadFormulaAbox();
            } catch (Exception e) {
                log.warn("加载方剂 ABox 失败，可能数据库无数据或映射不匹配，忽略此错误继续启动", e);
            }

            log.info("TCMOntologyJobWorker 初始化完成，包含方剂 ABox 数据");
            Set<OWLNamedIndividual> formulaInstances = backendService.getIndividuals(FJ_NS + "Formula");
            log.info("加载后方剂个体数：{}", formulaInstances.size());

            // 初始化六经辨证引擎
            this.sixChannelEngine = new SixChannelInferenceEngine(this.backendService);
            this.sixChannelEngine.buildIndexes();
            log.info("六经辨证引擎初始化完成");

        } catch (Exception e) {
            log.error("TCMOntologyJobWorker 初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    /**
     * 通过 Ontop 加载方剂 ABox 数据
     */
    private void loadFormulaAbox() throws Exception {
        String constructSparql = """
                PREFIX fj: <http://www.tcm-classics.org/fangji#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX tcm: <http://www.tcm-classics.org/tcm#>
                CONSTRUCT {
                    ?formula fj:indicated_for ?pattern .
                }
                WHERE {
                    ?formula a fj:Formula ;
                             fj:primary_pattern ?pattern .
                }
                """;

        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        try {
            backendService.getObdaHandler().loadAboxFromOntop(constructSparql, tbox);
            backendService.getReasonerService().getReasoner().flush();
            log.info("方剂 ABox 加载完成，共添加了方剂实例及其 indicated_for 关系");
        } catch (Exception e) {
            log.error("加载方剂 ABox 时发生错误", e);
            throw e;
        }
    }

    // ==================== 第一步：症状映射（文本→本体IRI） ====================
    // v5.3 修改：拆分为 symptomIris / pulseIris / tongueIris 三个独立列表
    @JobWorker(type = "step1-sizhen", autoComplete = false)
    public void handleStep1SiZhen(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomInputs = (List<String>) vars.get("symptomInputs");
            if (symptomInputs == null || symptomInputs.isEmpty()) {
                throw new IllegalArgumentException("缺少症状输入列表（流程变量 symptomInputs）");
            }

            // v5.3 新增：三个独立列表
            List<String> symptomIris = new ArrayList<>();
            List<String> pulseIris = new ArrayList<>();
            List<String> tongueIris = new ArrayList<>();

            for (String input : symptomInputs) {
                String iri = null;
                // 统一查找：先尝试症状，再尝试脉象，再尝试舌象
                // 根据匹配到的 IRI 命名空间自动分类

                // 1. 先查症状本体
                iri = findMatchInOntology(input,
                        ZZ_NS,
                        Arrays.asList(ZZ_NS + "originalDescription"));

                if (iri != null) {
                    symptomIris.add(iri);
                    log.debug("症状匹配成功: {} -> {}", input, iri);
                    continue;
                }

// 2. 查脉象本体
                iri = findMatchInOntology(input, MX_NS,
                        Arrays.asList(MX_NS + "finger_feeling_description"));

                if (iri != null) {
                    // 脉象可能包含多个（如"脉细数"拆成"细"和"数"）
                    if (input.contains("脉")) {
                        List<String> pulseParts = PulseParser.splitPulseDescriptions(input);
                        List<String> matchedPulseIris = new ArrayList<>();
                        for (String pulsePart : pulseParts) {
                            String pulseIri = findMatchInOntology(pulsePart,
                                    MX_NS,
                                    Arrays.asList(MX_NS + "finger_feeling_description"));
                            if (pulseIri != null && !matchedPulseIris.contains(pulseIri)) {
                                matchedPulseIris.add(pulseIri);
                                log.debug("脉象匹配成功: {} -> {}", pulsePart, pulseIri);
                            }
                        }
                        if (!matchedPulseIris.isEmpty()) {
                            pulseIris.addAll(matchedPulseIris);
                            continue;
                        }
                    }
                    // 单个脉象词（不含"脉"字但匹配到了脉象本体）
                    pulseIris.add(iri);
                    log.debug("脉象匹配成功: {} -> {}", input, iri);
                    continue;
                }

                // 3. 查舌象本体
                iri = findMatchInOntology(input, TX_NS, Collections.emptyList());

                if (iri != null) {
                    tongueIris.add(iri);
                    log.debug("舌象匹配成功: {} -> {}", input, iri);
                    continue;
                }

                // 4. 全部未匹配
                log.warn("症状/脉象/舌象均未找到: {}", input);

                // 4. fallback: 查 shexiang# 命名空间（舌象兜底）
                if (iri == null) {
                    iri = findMatchInOntology(input,
                            TX_NS,
                            Collections.emptyList());
                }
                // 如果 shexiang# 匹配到了且之前不是舌象分支，放入舌象列表
                if (iri != null && !tongueIris.contains(iri) && !pulseIris.contains(iri)) {
                    // 判断是舌象还是症状：如果包含"舌"或匹配到TX_NS则归入舌象
                    if (input.contains("舌")|| input.contains("苔")) {
                        tongueIris.add(iri);
                    } else {
                        symptomIris.add(iri);
                    }
                    continue;
                }

                // 5. fallback: 查 JJ_NS
                if (iri == null) {
                    iri = findMatchInOntology(input,
                            JJ_NS,
                            Collections.emptyList());
                }

                // 6. 全部归入症状列表（脉象和舌象已在上面独立收集）
                if (iri != null) {
                    symptomIris.add(iri);
                    log.debug("匹配成功: {} -> {}", input, iri);
                } else {
                    log.warn("未找到精确匹配: {}", input);
                }
            }

            String caseIri = "clinical/" + "Case_" + System.currentTimeMillis();

            // v5.3 修改：输出三个独立列表
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("symptomIris", symptomIris);
            result.put("pulseIris", pulseIris);       // v5.3 新增
            result.put("tongueIris", tongueIris);      // v5.3 新增
            result.put("symptomLiterals", symptomInputs);
            result.put("clinicalCaseIri", caseIri);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("step1-sizhen 完成 | jobKey={} | 症状{}个 脉象{}个 舌象{}个",
                    job.getKey(), symptomIris.size(), pulseIris.size(), tongueIris.size());

        } catch (Exception e) {
            log.error("step1-sizhen 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP1_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    private String findMatchInOntology(String text, String namespace, List<String> extraPropIris) {
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        List<OWLAnnotationProperty> searchProps = new ArrayList<>();
        searchProps.add(df.getRDFSLabel());
        searchProps.add(df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel")));
        searchProps.add(df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#altLabel")));
        searchProps.add(df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#hiddenLabel")));
        searchProps.add(df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#note")));
        if (extraPropIris != null) {
            for (String iri : extraPropIris) {
                searchProps.add(df.getOWLAnnotationProperty(IRI.create(iri)));
            }
        }
        return queryService.findIndividualByLabel(
                text,
                namespace,
                searchProps,
                true,
                "http://www.w3.org/2004/02/skos/core#Concept",
                "http://www.w3.org/2004/02/skos/core#exactMatch"
        );
    }

    // ==================== 第二步：八纲-六经一体辨证 ====================
    // v5.3 修改：传递 pulseIris 和 tongueIris 给推理引擎
    @JobWorker(type = "step2-bagang-liujing", autoComplete = false)
    public void handleStep2BaGangLiuJing(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            // v5.3 新增：读取脉象和舌象
            @SuppressWarnings("unchecked")
            List<String> pulseIris = (List<String>) vars.get("pulseIris");
            @SuppressWarnings("unchecked")
            List<String> tongueIris = (List<String>) vars.get("tongueIris");

            if (symptomIris == null || symptomIris.isEmpty()) {
                throw new IllegalArgumentException("缺少症状IRI，请先执行第一步");
            }

            // v5.3 修改：传递三个列表给推理引擎
            InferenceResult inferResult = sixChannelEngine.infer(
                    symptomIris,
                    pulseIris != null ? pulseIris : Collections.<String>emptyList(),
                    tongueIris != null ? tongueIris : Collections.<String>emptyList()
            );

            if (inferResult.indeterminate) {
                throw new IllegalStateException(
                        "八纲辨证无法判定：" + String.join("；", inferResult.rationale));
            }

            // ===== 新增：八纲四纲判定 =====
            Map<String, String> eightPrinciples = determineEightPrinciples(
                    inferResult.primaryChannel,
                    new HashSet<>(symptomIris),
                    pulseIris != null ? new HashSet<>(pulseIris) : Collections.emptySet(),
                    tongueIris != null ? new HashSet<>(tongueIris) : Collections.emptySet(),
                    String.valueOf(job.getKey())
            );
            log.info("八纲四纲判定结果: {}", eightPrinciples);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sixChannel", inferResult.primaryChannel);
            result.put("sixChannelLabel", inferResult.primaryChannelLabel);
            result.put("bingWei", inferResult.location);
            result.put("bingWeiIri", inferResult.locationIri);
            result.put("bingXing", inferResult.nature);
            result.put("bingXingIri", inferResult.natureIri);
            result.put("baGangLabel", inferResult.baGangLabel);
            // ===== 新增：输出八纲四纲 =====
            result.put("biaoLi", eightPrinciples.get("表里"));
            result.put("hanRe", eightPrinciples.get("寒热"));
            result.put("xuShi", eightPrinciples.get("虚实"));
            result.put("yinYang", eightPrinciples.get("阴阳"));
            result.put("eightPrinciples", eightPrinciples);
            // ==============================
            result.put("isHeBing", inferResult.isHeBing);
            result.put("heBingChannels", inferResult.heBingChannels);
            result.put("heBingChannelLabels", inferResult.heBingChannelLabels);
            result.put("confidence", inferResult.confidence);
            result.put("isShaoYinDirect", inferResult.isShaoYinDirect);
            result.put("rationale", inferResult.rationale);
            result.put("clinicalCaseIri", vars.get("clinicalCaseIri"));

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("step2-bagang-liujing 完成 | jobKey={} | 六经={} | 八纲={{表里:{}, 寒热:{}, 虚实:{}, 阴阳:{}}} | 合病={} | 置信度={}",
                    job.getKey(), inferResult.primaryChannelLabel,
                    eightPrinciples.get("表里"), eightPrinciples.get("寒热"),
                    eightPrinciples.get("虚实"), eightPrinciples.get("阴阳"),
                    inferResult.isHeBing, inferResult.confidence);

        } catch (Exception e) {
            log.error("step2-bagang-liujing 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP2_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    private String inferSixChannelByJaccard(List<String> symptomIris) {
        if (symptomIris == null || symptomIris.isEmpty()) return null;

        Set<String> patientSymptoms = new HashSet<>(symptomIris);
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        OWLObjectProperty hasSymptomProp = df.getOWLObjectProperty(IRI.create(TCM_NS + "has_symptom"));

        List<String> patternIRIs = Arrays.asList(
                BZ_NS + "TaiYangBingGangZheng",
                BZ_NS + "YangMingBingGangZheng",
                BZ_NS + "ShaoYangBingGangZheng",
                BZ_NS + "TaiYinBingGangZheng",
                BZ_NS + "ShaoYinBingGangZheng",
                BZ_NS + "JueYinBingGangZheng"
        );

        Map<String, Double> scores = new HashMap<>();

        for (String patternIRI : patternIRIs) {
            OWLNamedIndividual pattern = df.getOWLNamedIndividual(IRI.create(patternIRI));
            NodeSet<OWLNamedIndividual> patternSymptoms = reasoner.getObjectPropertyValues(pattern, hasSymptomProp);
            Set<String> patternSymptomSet = patternSymptoms.entities()
                    .map(ind -> ind.getIRI().toString())
                    .collect(Collectors.toSet());

            if (patternSymptomSet.isEmpty()) continue;

            Set<String> intersection = new HashSet<>(patientSymptoms);
            intersection.retainAll(patternSymptomSet);
            double unionSize = patientSymptoms.size() + patternSymptomSet.size() - intersection.size();
            double similarity = unionSize > 0 ? intersection.size() / unionSize : 0.0;

            OWLObjectProperty belongsToChannel = df.getOWLObjectProperty(IRI.create(TCM_NS + "belongs_to_channel"));
            NodeSet<OWLNamedIndividual> channels = reasoner.getObjectPropertyValues(pattern, belongsToChannel);
            for (OWLNamedIndividual channel : channels.entities().collect(Collectors.toList())) {
                String channelIRI = channel.getIRI().toString();
                scores.merge(channelIRI, similarity, Math::max);
            }
        }

        String best = null;
        double maxScore = 0.0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() > maxScore) {
                maxScore = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }


// ===================================================================
// 八纲推理 — 本体驱动版（替换 determineHanRe / determineXuShi / determineYinYang / determineEightPrinciples / determineBiaoLi）
// ===================================================================
// 核心设计原则：
//   1. 八纲归属关系只在本体中维护（tcm:symptom_indicates_bagang）
//   2. 代码只做"收集 → 聚合 → 决策"，不硬编码任何症状关键词
//   3. 新增症状只需在本体中添加 symptom_indicates_bagang 关系，无需改代码
// ===================================================================

// ======================== 常量定义 ========================

    /** 八纲属性IRI：症状 → 八纲 */
    private static final IRI SYMPTOM_INDICATES_BAGANG =
            IRI.create(TCM_NS + "symptom_indicates_bagang");

    /** 八纲概念IRI */
    private static final String BAGANG_NS = "http://www.tcm-classics.org/bagang#";
    private static final IRI BAGANG_BIAO    = IRI.create(BAGANG_NS + "Biao");       // 表
    private static final IRI BAGANG_LI      = IRI.create(BAGANG_NS + "Li");         // 里
    private static final IRI BAGANG_BAN_BIAO_BAN_LI = IRI.create(BAGANG_NS + "BanBiaoBanLi"); // 半表半里
    private static final IRI BAGANG_HAN     = IRI.create(BAGANG_NS + "Han");        // 寒
    private static final IRI BAGANG_RE      = IRI.create(BAGANG_NS + "Re");         // 热
    private static final IRI BAGANG_XU      = IRI.create(BAGANG_NS + "Xu");         // 虚
    private static final IRI BAGANG_SHI     = IRI.create(BAGANG_NS + "Shi");        // 实

    /** 八纲概念IRI → 中文标签的映射 */
    private static final Map<IRI, String> BAGANG_LABEL_MAP = new HashMap<>();
    static {
        BAGANG_LABEL_MAP.put(BAGANG_BIAO, "表证");
        BAGANG_LABEL_MAP.put(BAGANG_LI, "里证");
        BAGANG_LABEL_MAP.put(BAGANG_BAN_BIAO_BAN_LI, "半表半里");
        BAGANG_LABEL_MAP.put(BAGANG_HAN, "寒证");
        BAGANG_LABEL_MAP.put(BAGANG_RE, "热证");
        BAGANG_LABEL_MAP.put(BAGANG_XU, "虚证");
        BAGANG_LABEL_MAP.put(BAGANG_SHI, "实证");
    }

    /** 八纲对（互为对立） */
    private static final Map<String, List<IRI>> PRINCIPLE_PAIRS = new LinkedHashMap<>();
    static {
        PRINCIPLE_PAIRS.put("表里", Arrays.asList(BAGANG_BIAO, BAGANG_LI, BAGANG_BAN_BIAO_BAN_LI));
        PRINCIPLE_PAIRS.put("寒热", Arrays.asList(BAGANG_HAN, BAGANG_RE));
        PRINCIPLE_PAIRS.put("虚实", Arrays.asList(BAGANG_XU, BAGANG_SHI));
    }

// ======================== 入口方法 ========================

    /**
     * 八纲四纲总入口 — 从本体查询所有症状的八纲标注，聚合后决策。
     *
     * @param liujing    六经诊断结果IRI（如 "http://.../TaiYangZhongFeng"）
     * @param symptomIris 症状个体IRI集合
     * @param pulseIris   脉象个体IRI集合
     * @param tongueIris  舌象个体IRI集合
     * @return Map<"表里", "寒热", "虚实", "阴阳">
     */
    private Map<String, String> determineEightPrinciples(
            String liujing,
            Set<String> symptomIris,
            Set<String> pulseIris,
            Set<String> tongueIris,
            String jobKey) {

        // ====== 第一层：从六经直接推导表里和阴阳（胡希恕体系） ======
        String biaoLi = determineBiaoLiFromLiujing(liujing);
        String yinYang = determineYinYangFromLiujing(liujing);

        // ====== 第二层：从症状推导虚实 ======
        Set<String> allIris = new HashSet<>();
        if (symptomIris != null) allIris.addAll(symptomIris);
        if (pulseIris != null) allIris.addAll(pulseIris);
        if (tongueIris != null) allIris.addAll(tongueIris);

        // 收集虚实证据
        Map<IRI, List<String>> bagangEvidence = collectBagangEvidence(allIris);
        String xuShi = decideXuShi(bagangEvidence, liujing);

        // ====== 第三层：寒热判定（保守策略） ======
        String hanRe = decideHanRe(bagangEvidence, liujing, allIris);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("表里", biaoLi);
        result.put("寒热", hanRe);
        result.put("虚实", xuShi);
        result.put("阴阳", yinYang);

        log.info("八纲判定 | jobKey={} | 六经={} | 表里={} | 寒热={} | 虚实={} | 阴阳={}",
                jobKey, getShortForm(liujing), biaoLi, hanRe, xuShi, yinYang);

        return result;
    }

    /**
     * 从六经推导表里（胡希恕体系）
     */
    private String determineBiaoLiFromLiujing(String liujing) {
        if (liujing == null) return "未定";
        String sf = getShortForm(liujing).toLowerCase();
        if (sf.contains("taiyang")) return "表证";
        if (sf.contains("shaoyin")) return "表证";   // 少阴=表阴证
        if (sf.contains("yangming")) return "里证";
        if (sf.contains("taiyin")) return "里证";
        if (sf.contains("shaoyang")) return "半表半里";
        if (sf.contains("jueyin")) return "半表半里";
        return "未定";
    }

    /**
     * 从六经直接推导阴阳（胡希恕体系，最可靠）
     * 太阳=阳，阳明=阳，少阳=阳
     * 太阴=阴，少阴=阴，厥阴=阴
     */
    private String determineYinYangFromLiujing(String liujing) {
        if (liujing == null) return "未定";
        String sf = getShortForm(liujing).toLowerCase();
        if (sf.contains("taiyang")) return "阳证";
        if (sf.contains("yangming")) return "阳证";
        if (sf.contains("shaoyang")) return "阳证";
        if (sf.contains("taiyin")) return "阴证";
        if (sf.contains("shaoyin")) return "阴证";
        if (sf.contains("jueyin")) return "阴证";
        return "未定";
    }

    /**
     * 虚实判定：从症状的八纲标注中收集虚/实证据
     * 太阳中风（汗出+恶风）→ 虚
     * 太阳伤寒（无汗+恶寒+身痛）→ 实
     */
    private String decideXuShi(Map<IRI, List<String>> evidence, String liujing) {
        List<String> xuEvidence = evidence.getOrDefault(BAGANG_XU, Collections.emptyList());
        List<String> shiEvidence = evidence.getOrDefault(BAGANG_SHI, Collections.emptyList());

        boolean hasXu = !xuEvidence.isEmpty();
        boolean hasShi = !shiEvidence.isEmpty();

        if (hasXu && !hasShi) return "虚证";
        if (hasShi && !hasXu) return "实证";
        if (hasXu && hasShi) {
            // 虚实并见，看六经语境
            String sf = getShortForm(liujing).toLowerCase();
            if (sf.contains("jueyin")) return "虚实错杂"; // 厥阴寒热错杂常兼虚实
            return "虚实错杂";
        }

        // 无直接证据时，从六经推导
        String sf = getShortForm(liujing).toLowerCase();
        if (sf.contains("taiyin") || sf.contains("shaoyin")) return "虚证";
        if (sf.contains("yangming")) return "实证";
        return "未定";
    }

    /**
     * 寒热判定（保守策略）：
     * 只有明确的寒证/热证症状才判定，表证阶段的恶寒/发热不判寒热。
     *
     * 热证确据：恶热、大渴、潮热、身热、谵语、小便赤
     * 寒证确据：小便清长、自利不渴、腹中寒、手足厥冷、口中和
     */
    private String decideHanRe(Map<IRI, List<String>> evidence, String liujing, Set<String> allIris) {
        List<String> hanEvidence = evidence.getOrDefault(BAGANG_HAN, Collections.emptyList());
        List<String> reEvidence = evidence.getOrDefault(BAGANG_RE, Collections.emptyList());

        boolean hasHan = !hanEvidence.isEmpty();
        boolean hasRe = !reEvidence.isEmpty();

        // 太阳病阶段：恶寒/发热不判寒热（已在修正1中从本体去掉标注）
        // 如果仍有残留标注，用六经语境过滤
        String sf = getShortForm(liujing).toLowerCase();
        if (sf.contains("taiyang")) {
            // 太阳病：寒热需要非常明确的证据（如兼见阳明里热）
            // 单纯的恶风/发热不构成寒热判定
            if (hasHan && hasRe) return "未定"; // 太阳阶段不轻易判错杂
            if (hasRe && reEvidence.stream().anyMatch(s ->
                    s.contains("恶热") || s.contains("大渴") || s.contains("潮热"))) {
                return "热证"; // 太阳转阳明的明确热象
            }
            return "未定";
        }

        if (hasHan && !hasRe) return "寒证";
        if (hasRe && !hasHan) return "热证";
        if (hasHan && hasRe) {
            if (sf.contains("jueyin")) return "寒热错杂"; // 厥阴提纲
            return "寒热错杂";
        }

        // 无证据时从六经推导
        if (sf.contains("yangming")) return "热证";
        if (sf.contains("taiyin") || sf.contains("shaoyin")) return "寒证";
        return "未定";
    }

    /**
     * 收集所有个体的八纲标注证据
     */
    private Map<IRI, List<String>> collectBagangEvidence(Set<String> allIris) {
        Map<IRI, List<String>> bagangEvidence = new LinkedHashMap<>();
        for (String iriStr : allIris) {
            try {
                OWLNamedIndividual ind = backendService.getIndividual(iriStr);
                if (ind == null) continue;
                OWLObjectPropertyExpression prop = backendService.getObjectProperty(
                        SYMPTOM_INDICATES_BAGANG.toString());
                Set<OWLNamedIndividual> targets = backendService
                        .getObjectPropertyAllValueOfIndividual(ind, prop);
                for (OWLNamedIndividual target : targets) {
                    IRI bagangIri = target.getIRI();
                    String symptomLabel = backendService.resolveLabel(iriStr);
                    bagangEvidence.computeIfAbsent(bagangIri, k -> new ArrayList<>())
                            .add(symptomLabel != null ? symptomLabel : getShortForm(iriStr));
                }
            } catch (Exception e) {
                log.warn("查询八纲标注失败 | iri={} | {}", iriStr, e.getMessage());
            }
        }
        return bagangEvidence;
    }
// ======================== 四纲决策方法 ========================

    /**
     * 对一对八纲（如表里、寒热、虚实）进行决策。
     *
     * 决策规则：
     *   - 仅有对立项A的证据 → 判定为A
     *   - 仅有对立项B的证据 → 判定为B
     *   - A和B都有证据 → 判定为"错杂"（如寒热错杂、虚实错杂）
     *   - 都没有证据 → 返回"未定"
     *
     * @param bagangEvidence  bagangIRI → List<症状标签>
     * @param pairIRIs        该对纲领的八纲概念IRI列表（如 [Han, Re]）
     * @param pairName        中文名称（如 "寒热"）
     * @return 判定结果（如 "寒证"、"热证"、"寒热错杂"、"未定"）
     */
    private String decidePrinciplePair(
            Map<IRI, List<String>> bagangEvidence,
            List<IRI> pairIRIs,
            String pairName) {

        Map<IRI, List<String>> evidenceByOption = new LinkedHashMap<>();
        for (IRI bagangIri : pairIRIs) {
            List<String> evidence = bagangEvidence.getOrDefault(bagangIri, Collections.emptyList());
            if (!evidence.isEmpty()) {
                evidenceByOption.put(bagangIri, evidence);
            }
        }

        if (evidenceByOption.isEmpty()) {
            return "未定";
        } else if (evidenceByOption.size() == 1) {
            Map.Entry<IRI, List<String>> entry = evidenceByOption.entrySet().iterator().next();
            return BAGANG_LABEL_MAP.getOrDefault(entry.getKey(), getShortForm(entry.getKey().toString()));
        } else {
            // 【修正】错杂拼接逻辑：去掉"证"后缀，用"错杂"连接
            List<String> shortLabels = new ArrayList<>();
            for (IRI bagangIri : pairIRIs) {
                if (evidenceByOption.containsKey(bagangIri)) {
                    String fullLabel = BAGANG_LABEL_MAP.getOrDefault(bagangIri, getShortForm(bagangIri.toString()));
                    // 去掉"证"后缀
                    String shortLabel = fullLabel.replace("证", "");
                    shortLabels.add(shortLabel);
                }
            }
            // "寒" + "热" → "寒热错杂"
            return String.join("", shortLabels) + "错杂";
        }
    }

// ======================== 辅助方法 ========================

    /**
     * 从完整 IRI 提取 short form（用于日志和调试）。
     */
    private String getShortForm(String iri) {
        if (iri == null || iri.isEmpty()) return "";
        int lastHash = iri.lastIndexOf('#');
        int lastSlash = iri.lastIndexOf('/');
        int idx = Math.max(lastHash, lastSlash);
        return idx >= 0 ? iri.substring(idx + 1) : iri;
    }

    // ==================== 第三步：辨兼夹与合病 ====================
    @JobWorker(type = "step3-jianjia", autoComplete = false)
    public void handleStep3JianJia(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");

            Boolean isHeBing = (Boolean) vars.get("isHeBing");
            List<String> heBingChannels = (List<String>) vars.get("heBingChannels");

            Set<String> concomitantPathologies = new LinkedHashSet<>();
            if (hasSymptomMatching(symptomIris, JJ_NS + "WaterRetention_Instance")) {
                concomitantPathologies.add(JJ_NS + "WaterRetention_Instance");
            }
            if (hasSymptomMatching(symptomIris, JJ_NS + "BloodStasis_Instance")) {
                concomitantPathologies.add(JJ_NS + "BloodStasis_Instance");
            }
            if (hasSymptomMatching(symptomIris, JJ_NS + "FoodStagnation_Instance")) {
                concomitantPathologies.add(JJ_NS + "FoodStagnation_Instance");
            }
            if (hasSymptomMatching(symptomIris, JJ_NS + "QiStagnation_Instance")) {
                concomitantPathologies.add(JJ_NS + "QiStagnation_Instance");
            }
            if (hasSymptomMatching(symptomIris, JJ_NS + "QiCounterflow_Instance")) {
                concomitantPathologies.add(JJ_NS + "QiCounterflow_Instance");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("concomitantPathologies", new ArrayList<>(concomitantPathologies));
            result.put("isHeBing", isHeBing);
            result.put("heBingChannels", heBingChannels);
            result.put("clinicalCaseIri", vars.get("clinicalCaseIri"));

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("step3 完成 | 兼夹数={} | 合病={}", concomitantPathologies.size(), isHeBing);

        } catch (Exception e) {
            log.error("step3 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP3_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    private boolean hasSymptomMatching(List<String> symptomIris, String concomitantIri) {
        if (symptomIris == null || symptomIris.isEmpty()) return false;
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        if (reasoner == null) {
            log.warn("推理器不可用，无法进行兼夹证匹配");
            return false;
        }

        try {
            OWLNamedIndividual concomitant = df.getOWLNamedIndividual(IRI.create(concomitantIri));
            OWLObjectProperty identificationProp = df.getOWLObjectProperty(
                    IRI.create(JJ_NS + "hasIdentificationSymptom"));

            NodeSet<OWLNamedIndividual> symptomNodes = reasoner.getObjectPropertyValues(concomitant, identificationProp);
            Set<String> identificationSymptoms = symptomNodes.entities()
                    .map(ind -> ind.getIRI().toString())
                    .collect(Collectors.toSet());

            if (identificationSymptoms.isEmpty()) return false;

            for (String symptom : symptomIris) {
                if (identificationSymptoms.contains(symptom)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("匹配兼夹证 {} 时发生异常", concomitantIri, e);
        }
        return false;
    }

    /**
     * 第四步：方证对应匹配器（v5.3 JobWorker版）
     *
     * 核心架构变更（v5.2 -> v5.3）：
     * - 阶段A仅使用 belongs_to_liujing
     * - 阶段B+使用 patternRole 属性过滤非可处方个体
     * - 阶段B2（新增）：脉象完备性校验
     * - 阶段B3（新增）：舌象完备性校验
     * - 阶段F从六经实例读取提纲症状/脉象/舌象/治法/治禁构建完整证候图
     * - 阶段D使用 fangji:indicated_for 反向查找方剂
     */
    @JobWorker(type = "step4-fangzheng-match", autoComplete = false)
    public void handleStep4FangZhengMatch(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String sixChannelIri = (String) vars.get("sixChannel");
            String clinicalCaseIri = (String) vars.get("clinicalCaseIri");

            @SuppressWarnings("unchecked")
            List<String> symptomList = (List<String>) vars.get("symptomIris");
            // v5.3 新增：读取脉象和舌象
            @SuppressWarnings("unchecked")
            List<String> pulseList = (List<String>) vars.get("pulseIris");
            @SuppressWarnings("unchecked")
            List<String> tongueList = (List<String>) vars.get("tongueIris");
            @SuppressWarnings("unchecked")
            List<String> concomitantList = (List<String>) vars.get("concomitantPathologies");

            Set<String> symptomIris = symptomList != null ? new LinkedHashSet<>(symptomList) : Collections.emptySet();
            Set<String> pulseIris = pulseList != null ? new LinkedHashSet<>(pulseList) : Collections.emptySet();
            Set<String> tongueIris = tongueList != null ? new LinkedHashSet<>(tongueList) : Collections.emptySet();
            Set<String> concomitantPathologies = concomitantList != null ? new LinkedHashSet<>(concomitantList) : Collections.emptySet();

            log.info("=======================================================");
            log.info("=======================================================");
            log.info("step4-fangzheng-match v5.3 启动");
            log.info("  六经: {} ({} )", sixChannelIri, backendService.resolveLabel(sixChannelIri));
            log.info("  症状IRI列表:");
            for (String si : symptomIris) {
                log.info("    - {} ({} )", si, backendService.resolveLabel(si));
            }
            log.info("  脉象IRI列表:");
            for (String pi : pulseIris) {
                log.info("    - {} ({} )", pi, backendService.resolveLabel(pi));
            }
            log.info("  舌象IRI列表: {}", tongueIris);
            log.info("  兼夹证: {}", concomitantPathologies);
            log.info("=======================================================");

            // 执行严格逻辑匹配（v5.3 新增 pulseIris 和 tongueIris 参数）
            // ===== 读取八纲判定结果（来自 step2） =====
            @SuppressWarnings("unchecked")
            Map<String, String> eightPrinciples = (Map<String, String>) vars.get("eightPrinciples");

            // [修复] Camunda 工作流引擎对 Map<String,String> 的序列化可能丢失类型信息，
            // 如果 eightPrinciples 为 null，从 step2 单独存储的四个字段重建
            if (eightPrinciples == null || eightPrinciples.isEmpty()) {
                eightPrinciples = new LinkedHashMap<>();
                eightPrinciples.put("表里", (String) vars.get("biaoLi"));
                eightPrinciples.put("寒热", (String) vars.get("hanRe"));
                eightPrinciples.put("虚实", (String) vars.get("xuShi"));
                eightPrinciples.put("阴阳", (String) vars.get("yinYang"));
                log.warn("八纲Map为空（Camunda序列化问题），已从单个字段重建: {}", eightPrinciples);
            }
            log.info("[DEBUG] step4 读取八纲: {}", eightPrinciples);

            Map<String, Object> result = executeStrictMatch(
                    sixChannelIri, symptomIris, pulseIris, tongueIris, concomitantPathologies, clinicalCaseIri, eightPrinciples);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send()
                    .join();

            log.info("step4 完成 | status={} | best={}",
                    result.get("matchStatus"), result.get("bestMatchFormulaIri"));

        } catch (Exception e) {
            log.error("step4-fangzheng-match 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP4_FANGZHENG_FAILED")
                    .errorMessage("方证匹配异常: " + e.getMessage())
                    .send()
                    .join();
        }
    }

    // ================================================================
    // 核心匹配流程编排（v5.3 新增 pulseIris 和 tongueIris）
    // ================================================================

    private Map<String, Object> executeStrictMatch(
            String sixChannelIri,
            Set<String> symptomIris,
            Set<String> pulseIris,
            Set<String> tongueIris,          // v5.3 新增
            Set<String> concomitantPathologies,
            String clinicalCaseIri,
            Map<String, String> eightPrinciples) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clinicalCaseIri", clinicalCaseIri);
        result.put("sixChannelIri", sixChannelIri);

        if (sixChannelIri == null || sixChannelIri.isEmpty()) {
            return executeFallback(symptomIris, pulseIris, tongueIris, result, "缺少六经信息，无法执行方证匹配");        }

        // 阶段A：六经空间约束
        List<OWLNamedIndividual> channelFiltered = executePhaseA(sixChannelIri);
        log.info("阶段A完成：六经空间约束 -> {} 个候选", channelFiltered.size());
        if (channelFiltered.isEmpty()) {
            return executeFallback(symptomIris, pulseIris, tongueIris, result, "六经空间约束后无候选方证");        }

        // 阶段B：主症完备性校验
        List<OWLNamedIndividual> symptomMatched = executePhaseB(channelFiltered, symptomIris);
        log.info("阶段B完成：主症完备性校验 -> {} 个候选", symptomMatched.size());
        if (symptomMatched.isEmpty()) {
            return executeFallback(symptomIris, pulseIris, tongueIris, result, "主症完备性校验后无匹配");        }

        // v5.3 新增：阶段B2 脉象完备性校验
        List<OWLNamedIndividual> pulseMatched = executePhaseBPulse(symptomMatched, pulseIris);
        log.info("阶段B2完成：脉象完备性校验 -> {} 个候选", pulseMatched.size());
        if (pulseMatched.isEmpty()) {
            return executeFallback(symptomIris, pulseIris, tongueIris, result, "脉象完备性校验后无匹配（候选方证的脉象要求未满足）");        }

        // v5.3 新增：阶段B3 舌象完备性校验
        List<OWLNamedIndividual> tongueMatched = executePhaseBTongue(pulseMatched, tongueIris);
        log.info("阶段B3完成：舌象完备性校验 -> {} 个候选", tongueMatched.size());
        if (tongueMatched.isEmpty()) {
            return executeFallback(symptomIris, pulseIris, tongueIris, result, "舌象完备性校验后无匹配（候选方证的舌象要求未满足）");        }

        // 阶段B+：排除非可处方个体
        List<OWLNamedIndividual> prescribablePatterns = executePhaseBPlus(tongueMatched);
        log.info("阶段B+完成：排除非方证 -> {} 个可处方方证", prescribablePatterns.size());
        if (prescribablePatterns.isEmpty()) {
            return executeFallback(symptomIris, pulseIris, tongueIris, result, "排除非方证后无匹配");        }

        // 阶段C：兼夹兼容性校验
        List<OWLNamedIndividual> finalMatched = executePhaseC(prescribablePatterns, concomitantPathologies);
        log.info("阶段C完成：兼夹兼容性校验 -> {} 个最终匹配", finalMatched.size());
        if (finalMatched.isEmpty()) {
            return executeFallback(symptomIris, pulseIris, tongueIris, result, "兼夹兼容性校验后无匹配");        }

        // 阶段D：病证->方剂反向映射
        Map<String, Object> phaseDResult = executePhaseD(finalMatched);
        @SuppressWarnings("unchecked")
        List<String> matchedFormulaIris = (List<String>) phaseDResult.get("formulaIris");
        @SuppressWarnings("unchecked")
        List<String> matchedPatternIris = (List<String>) phaseDResult.get("patternIris");

        // 阶段E：结果判定
        MatchStatus status = determineStatus(matchedFormulaIris.size());
        result.put("matchStatus", status.name());
        result.put("matchedFormulaIris", matchedFormulaIris);
        result.put("matchedPatternIris", matchedPatternIris);

        if (status == MatchStatus.UNIQUE_MATCH) {
            result.put("bestMatchFormulaIri", matchedFormulaIris.get(0));
            result.put("bestMatchFormulaLabel", backendService.resolveLabel(matchedFormulaIris.get(0)));
            result.put("bestMatchPatternIri", matchedPatternIris.get(0));
            result.put("bestMatchPatternLabel", backendService.resolveLabel(matchedPatternIris.get(0)));
        } else if (status == MatchStatus.AMBIGUOUS) {
            result.put("differentialHint", buildDifferentialHint(matchedPatternIris));
        }
        // 在 executeStrictMatch 方法中，阶段E之后增加：

        // ====== 阶段E+：最佳匹配排序（解决AMBIGUOUS） ======
        // [修复] 不再依赖 status 变量，直接检查 matchedFormulaIris 的数量和 null
        log.debug("[DEBUG] 阶段E+ 条件检查: status={}, matchedFormulaIris.size()={}",
                status, matchedFormulaIris != null ? matchedFormulaIris.size() : "null");
        if (matchedFormulaIris != null && matchedFormulaIris.size() >= 2) {
            log.debug("[DEBUG] 阶段E+ 进入成功！开始计算反向覆盖度排序，候选数={}", matchedFormulaIris.size());
            // 计算每个方证与患者症状的"反向包含度"
            // 即：患者症状中有多少比例被该方证覆盖
            List<Map.Entry<String, Double>> scored = new ArrayList<>();
            for (int i = 0; i < matchedPatternIris.size(); i++) {
                String patternIri = matchedPatternIris.get(i);
                OWLNamedIndividual patternInd = backendService.getIndividual(patternIri);
                if (patternInd == null) continue;

                Set<OWLNamedIndividual> patternSymptoms =
                        backendService.safeGetAllObjectPropertyValues(patternInd,
                                backendService.safeGetObjectProperty(HAS_SYMPTOM));
                if (patternSymptoms == null || patternSymptoms.isEmpty()) continue;

                // 方证主症中有多少被患者覆盖（正向）
                long coveredByPatient = patternSymptoms.stream()
                        .filter(s -> symptomIris.contains(s.getIRI().toString()))
                        .count();

                // 患者症状中有多少被方证覆盖（反向）
                Set<String> patternSymptomIris = patternSymptoms.stream()
                        .map(s -> s.getIRI().toString())
                        .collect(Collectors.toSet());
                long patientCovered = symptomIris.stream()
                        .filter(patternSymptomIris::contains)
                        .count();

                // 综合得分 = 正向完备度 × 反向覆盖度
                double forwardScore = (double) coveredByPatient / patternSymptoms.size();
                double reverseScore = (double) patientCovered / symptomIris.size();
                double totalScore = forwardScore * 0.2 + reverseScore * 0.8; // 反向权重更高

                scored.add(Map.entry(matchedFormulaIris.get(i), totalScore));
            }

            scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            if (!scored.isEmpty()) {
                // 如果最高分显著高于第二名（差>0.15），则判定为唯一匹配
                if (scored.size() == 1 || (scored.get(0).getValue() - scored.get(1).getValue()) > 0.15) {
                    status = MatchStatus.UNIQUE_MATCH;
                    result.put("bestMatchFormulaIri", scored.get(0).getKey());
                    result.put("bestMatchFormulaLabel", backendService.resolveLabel(scored.get(0).getKey()));
                    // 找到对应的pattern
                    int bestIdx = matchedFormulaIris.indexOf(scored.get(0).getKey());
                    if (bestIdx >= 0 && bestIdx < matchedPatternIris.size()) {
                        result.put("bestMatchPatternIri", matchedPatternIris.get(bestIdx));
                        result.put("bestMatchPatternLabel",
                                backendService.resolveLabel(matchedPatternIris.get(bestIdx)));
                    }
                    log.info("阶段E+：通过反向覆盖度排序确定唯一匹配 -> {}",
                            backendService.resolveLabel(scored.get(0).getKey()));
                } else {
                    result.put("differentialHint", buildDifferentialHint(matchedPatternIris));
                    result.put("candidateScores", scored.stream()
                            .map(e -> getShortForm(e.getKey()) + "=" + String.format("%.2f", e.getValue()))
                            .collect(Collectors.toList()));
                }
            }
        }
        result.put("matchStatus", status.name());
        // 阶段F：完整证候图构建（v5.3 新增脉象/舌象信息）
        if (status != MatchStatus.NO_MATCH) {
            List<Map<String, Object>> completeViews = new ArrayList<>();
            for (String patternIri : matchedPatternIris) {
                OWLNamedIndividual patternInd = backendService.getIndividual(patternIri);
                if (patternInd != null) {
                    completeViews.add(buildCompletePatternView(patternInd, symptomIris, pulseIris, tongueIris));
                }
            }
            result.put("completePatternViews", completeViews);
        }

        log.info("=======================================================");
        log.info("第四步完成：状态={}, 匹配方剂数={}", status, matchedFormulaIris.size());
        if (status == MatchStatus.UNIQUE_MATCH) {
            log.info("  >>> 最终诊断结论：");
            log.info("    六经: {} ({})", sixChannelIri, backendService.resolveLabel(sixChannelIri));
            log.info("    病证: {} (IRI={})",
                    result.get("bestMatchPatternLabel"), result.get("bestMatchPatternIri"));
            log.info("    方剂: {} (IRI={})",
                    result.get("bestMatchFormulaLabel"), result.get("bestMatchFormulaIri"));

            // ===== 动态构建诊断推理链 =====
            log.info("  >>> 诊断推理链：");

            // 1. 症状串：从 symptomIris + pulseIris 动态拼接
            List<String> symptomLabels = new ArrayList<>();
            if (symptomIris != null) {
                for (String iri : symptomIris) {
                    symptomLabels.add(backendService.resolveLabel(iri));
                }
            }
            if (pulseIris != null) {
                for (String iri : pulseIris) {
                    symptomLabels.add(backendService.resolveLabel(iri));
                }
            }
            log.info("    症状({})", String.join("+", symptomLabels));

            // 2. 八纲：从 eightPrinciples Map 动态拼接，过滤掉"未定"
            log.debug("[DEBUG] executeStrictMatch 中 eightPrinciples = {}", eightPrinciples);
            if (eightPrinciples != null && !eightPrinciples.isEmpty()) {
                List<String> principles = new ArrayList<>();
                String[] keys = {"表里", "寒热", "虚实", "阴阳"};
                for (String key : keys) {
                    String val = eightPrinciples.get(key);
                    if (val != null && !"未定".equals(val)) {
                        principles.add(val);
                    }
                }
                log.info("    --> 八纲：{}", principles.isEmpty() ? "未定" : String.join(" + ", principles));
            } else {
                log.info("    --> 八纲：未定");
            }

            // 3. 六经：从 sixChannelIri 动态获取标签
            log.info("    --> 六经：{}", backendService.resolveLabel(sixChannelIri));

            // 4. 病证：从 result 中取
            log.info("    --> 病证：{}", result.get("bestMatchPatternLabel"));

            // 5. 方剂：从 result 中取
            log.info("    --> 方剂：{}", result.get("bestMatchFormulaLabel"));
        }
        log.info("=======================================================");
        return result;
    }

    // ================================================================
    // 阶段A：六经空间约束
    // ================================================================

    private List<OWLNamedIndividual> executePhaseA(String sixChannelIri) {
        List<OWLNamedIndividual> allPatterns = getAllDiseasePatterns();
        if (allPatterns == null || allPatterns.isEmpty()) {
            log.error("阶段A：未获取到任何DiseasePattern个体");
            return Collections.emptyList();
        }

        OWLObjectProperty liujingProp = backendService.safeGetObjectProperty(BELONGS_TO_LIUJING);
        if (liujingProp == null) {
            log.error("阶段A：belongs_to_liujing属性未定义");
            return Collections.emptyList();
        }

        List<OWLNamedIndividual> filtered = new ArrayList<>();
        for (OWLNamedIndividual pattern : allPatterns) {
            Set<OWLNamedIndividual> liujings = backendService.safeGetAllObjectPropertyValues(pattern, liujingProp);
            if (liujings == null) {
                continue;
            }
            boolean match = liujings.stream()
                    .anyMatch(lj -> lj.getIRI().toString().equals(sixChannelIri));
            if (match) {
                filtered.add(pattern);
                log.debug("  阶段A通过: {}", pattern.getIRI().getShortForm());
            }
        }

        log.info("阶段A 详细结果：");
        for (OWLNamedIndividual p : filtered) {
            log.info("  候选方证: {} (IRI={})",
                    backendService.resolveLabel(p.getIRI().toString()), p.getIRI().toString());
        }

        // ===== v6.0 新增：包含变体模式（variantOf 指向匹配模式的模式）=====
        OWLObjectProperty variantOfProp = backendService.safeGetObjectProperty(VARIANT_OF);
        if (variantOfProp != null) {
            for (OWLNamedIndividual allPattern : allPatterns) {
                // 跳过已包含的模式
                if (filtered.stream().anyMatch(p -> p.getIRI().equals(allPattern.getIRI()))) {
                    continue;
                }
                Set<OWLNamedIndividual> variantTargets = backendService.safeGetAllObjectPropertyValues(
                        allPattern, variantOfProp);
                for (OWLNamedIndividual target : variantTargets) {
                    // 如果该模式的 variantOf 指向一个已匹配的模式，则也包含它
                    if (filtered.stream().anyMatch(p -> p.getIRI().equals(target.getIRI()))) {
                        filtered.add(allPattern);
                        log.info("  阶段A通过(变体): {} (variantOf {}) [继承六经约束]",
                                allPattern.getIRI().getShortForm(),
                                target.getIRI().getShortForm());
                        break;
                    }
                }
            }
        }
        return filtered;
    }

    // ================================================================
    // 阶段B：主症完备性校验
    // ================================================================

    private List<OWLNamedIndividual> executePhaseB(
            List<OWLNamedIndividual> candidates, Set<String> symptomIris) {

        OWLObjectProperty hasSymptom = backendService.safeGetObjectProperty(HAS_SYMPTOM);
        if (hasSymptom == null) {
            log.error("阶段B：has_symptom属性未定义");
            return Collections.emptyList();
        }

        List<OWLNamedIndividual> matched = new ArrayList<>();
        for (OWLNamedIndividual pattern : candidates) {
            Set<OWLNamedIndividual> patternSymptoms =
                    backendService.safeGetAllObjectPropertyValues(pattern, hasSymptom);

            if (patternSymptoms == null || patternSymptoms.isEmpty()) {
                log.debug("  阶段B跳过（无主症）: {}", pattern.getIRI().getShortForm());
                continue;
            }

            boolean allPresent = patternSymptoms.stream()
                    .allMatch(s -> symptomIris.contains(s.getIRI().toString()));

            if (allPresent) {
                matched.add(pattern);
                log.info("  阶段B通过: {} (IRI={}) 主症{}个全匹配",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(), patternSymptoms.size());
            } else {
                Set<String> missing = patternSymptoms.stream()
                        .map(s -> s.getIRI().toString())
                        .filter(iri -> !symptomIris.contains(iri))
                        .collect(Collectors.toSet());
                log.info("  阶段B未通过: {} (IRI={}) 缺{}个症状: {}",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(), missing.size(), missing);
            }
        }

        // ===== v6.0 新增：变体模式的Conditional症状处理 =====
        // 对于有 variantOf 父模式的变体，仅要求"变体特有症状"（父模式不拥有的症状）
        // 如果患者没有变体特有症状，变体模式仍然匹配（作为父模式的变体）
        OWLObjectProperty variantOfPropB = backendService.safeGetObjectProperty(VARIANT_OF);
        if (variantOfPropB != null) {
            List<OWLNamedIndividual> variantAdjusted = new ArrayList<>();
            for (OWLNamedIndividual pattern : matched) {
                Set<OWLNamedIndividual> variantTargetsB = backendService.safeGetAllObjectPropertyValues(
                        pattern, variantOfPropB);
                if (variantTargetsB.isEmpty()) {
                    // 非变体模式，直接通过
                    variantAdjusted.add(pattern);
                    continue;
                }
                // 找到父模式（在 candidates 或 variantAdjusted 中）
                OWLNamedIndividual parent = null;
                for (OWLNamedIndividual vt : variantTargetsB) {
                    final String vtIri = vt.getIRI().toString();
                    if (candidates.stream().anyMatch(p -> p.getIRI().toString().equals(vtIri)) ||
                            variantAdjusted.stream().anyMatch(p -> p.getIRI().toString().equals(vtIri))) {
                        parent = vt;
                        break;
                    }
                }
                if (parent == null) {
                    // 找不到父模式，保守处理：保留该模式
                    variantAdjusted.add(pattern);
                    continue;
                }
                // 获取父模式的症状集合
                final Set<String> parentSymptomIris;
                Set<OWLNamedIndividual> parentSyms = backendService.safeGetAllObjectPropertyValues(
                        parent, hasSymptom);
                if (parentSyms != null) {
                    parentSymptomIris = parentSyms.stream()
                            .map(s -> s.getIRI().toString())
                            .collect(Collectors.toSet());
                } else {
                    parentSymptomIris = Collections.emptySet();
                }
                // 变体特有症状 = 变体症状 - 父模式症状
                final Set<String> variantSpecificSymptoms;
                Set<OWLNamedIndividual> patternSyms = backendService.safeGetAllObjectPropertyValues(
                        pattern, hasSymptom);
                if (patternSyms != null) {
                    variantSpecificSymptoms = patternSyms.stream()
                            .map(s -> s.getIRI().toString())
                            .filter(iri -> !parentSymptomIris.contains(iri))
                            .collect(Collectors.toSet());
                } else {
                    variantSpecificSymptoms = Collections.emptySet();
                }
                if (variantSpecificSymptoms.isEmpty()) {
                    // 变体没有特有症状，直接通过
                    variantAdjusted.add(pattern);
                    continue;
                }
                // 检查变体特有症状：如果患者有该变体的任何症状，则必须满足所有变体特有症状
                boolean patientHasAnyVariantSymptom = patternSyms.stream()
                        .anyMatch(s -> symptomIris.contains(s.getIRI().toString()));
                if (!patientHasAnyVariantSymptom) {
                    // 患者没有该变体的任何症状 → 不匹配（该变体不适用）
                    log.info("  阶段B未通过(变体不适用): {} (IRI={}) 患者无该变体症状",
                            backendService.resolveLabel(pattern.getIRI().toString()),
                            pattern.getIRI().toString());
                    continue;
                }
                // 患者有变体症状 → 检查变体特有症状是否全部匹配
                Set<String> missingVariant = new HashSet<>(variantSpecificSymptoms);
                missingVariant.removeAll(symptomIris);
                if (!missingVariant.isEmpty()) {
                    log.info("  阶段B未通过(变体特有症状缺失): {} (IRI={}) 缺{}个变体症状: {}",
                            backendService.resolveLabel(pattern.getIRI().toString()),
                            pattern.getIRI().toString(),
                            missingVariant.size(), missingVariant);
                    continue;
                }
                log.info("  阶段B通过(变体): {} (IRI={}) 变体特有症状{}个全匹配 [variantOf {}]",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(),
                        variantSpecificSymptoms.size(),
                        parent.getIRI().getShortForm());
                variantAdjusted.add(pattern);
            }
            matched = variantAdjusted;
        }
        return matched;
    }

    // ================================================================
    // v5.3 新增：阶段B2 脉象完备性校验
    // ================================================================

    /**
     * 候选方证的所有 has_pulse 指向的脉象个体，必须全部出现在患者的 pulseIris 中。
     * 如果方证未定义脉象要求（无 has_pulse），则自动通过。
     */
    private List<OWLNamedIndividual> executePhaseBPulse(
            List<OWLNamedIndividual> candidates, Set<String> pulseIris) {

        OWLObjectProperty hasPulse = backendService.safeGetObjectProperty(HAS_PULSE);
        if (hasPulse == null) {
            log.warn("阶段B2：has_pulse属性未定义，跳过脉象校验");
            return candidates;
        }

        if (pulseIris == null || pulseIris.isEmpty()) {
            log.info("阶段B2：患者无脉象输入，跳过脉象校验");
            return candidates;
        }

        List<OWLNamedIndividual> matched = new ArrayList<>();
        for (OWLNamedIndividual pattern : candidates) {
            Set<OWLNamedIndividual> patternPulses =
                    backendService.safeGetAllObjectPropertyValues(pattern, hasPulse);

            if (patternPulses == null || patternPulses.isEmpty()) {
                // 方证未定义脉象要求 -> 自动通过
                log.info("  阶段B+通过（无脉象要求）: {} (IRI={})",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString());
                matched.add(pattern);
                log.debug("  阶段B2通过（无脉象要求）: {}", pattern.getIRI().getShortForm());
                continue;
            }

            boolean allPresent = patternPulses.stream()
                    .allMatch(p -> pulseIris.contains(p.getIRI().toString()));

            if (allPresent) {
                matched.add(pattern);
                log.info("  阶段B2通过: {} (IRI={}) 脉象{}个全匹配",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(), patternPulses.size());
            } else {
                Set<String> missing = patternPulses.stream()
                        .map(p -> p.getIRI().toString())
                        .filter(iri -> !pulseIris.contains(iri))
                        .collect(Collectors.toSet());
                log.info("  阶段B2未通过: {} (IRI={}) 缺脉象{}个: {}",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(), missing.size(), missing);
            }
        }

        return matched;
    }

    // ================================================================
    // v5.3 新增：阶段B3 舌象完备性校验
    // ================================================================

    /**
     * 候选方证的所有 has_tongue 指向的舌象个体，必须全部出现在患者的 tongueIris 中。
     * 如果方证未定义舌象要求（无 has_tongue），则自动通过。
     */
    private List<OWLNamedIndividual> executePhaseBTongue(
            List<OWLNamedIndividual> candidates, Set<String> tongueIris) {

        OWLObjectProperty hasTongue = backendService.safeGetObjectProperty(LJ_HAS_TYPICAL_TONGUE);
        if (hasTongue == null) {
            log.warn("阶段B3：has_tongue属性未定义，跳过舌象校验");
            return candidates;
        }

        if (tongueIris == null || tongueIris.isEmpty()) {
            log.info("阶段B3：患者无舌象输入，跳过舌象校验");
            return candidates;
        }

        List<OWLNamedIndividual> matched = new ArrayList<>();
        for (OWLNamedIndividual pattern : candidates) {
            Set<OWLNamedIndividual> patternTongues =
                    backendService.safeGetAllObjectPropertyValues(pattern, hasTongue);

            if (patternTongues == null || patternTongues.isEmpty()) {
                // 方证未定义舌象要求 -> 自动通过
                matched.add(pattern);
                log.debug("  阶段B3通过（无舌象要求）: {}", pattern.getIRI().getShortForm());
                continue;
            }

            boolean allPresent = patternTongues.stream()
                    .allMatch(t -> tongueIris.contains(t.getIRI().toString()));

            if (allPresent) {
                matched.add(pattern);
                log.info("  阶段B3通过: {} (IRI={}) 舌象{}个全匹配",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(), patternTongues.size());
            } else {
                Set<String> missing = patternTongues.stream()
                        .map(t -> t.getIRI().toString())
                        .filter(iri -> !tongueIris.contains(iri))
                        .collect(Collectors.toSet());
                log.info("  阶段B3未通过: {} (IRI={}) 缺舌象{}个: {}",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(), missing.size(), missing);
            }
        }

        return matched;
    }

    // ================================================================
    // 阶段B+：排除非可处方个体
    // ================================================================

    private List<OWLNamedIndividual> executePhaseBPlus(List<OWLNamedIndividual> candidates) {
        List<OWLNamedIndividual> prescribable = new ArrayList<>();

        for (OWLNamedIndividual pattern : candidates) {
            if (isNonPrescribablePattern(pattern)) {
                log.debug("  排除非方证: {}", pattern.getIRI().getShortForm());
            } else {
                prescribable.add(pattern);
            }
        }

        return prescribable;
    }

    /**
     * 判断个体是否为非可处方方证。
     * 优先级：patternRole > owl:deprecated > 占位集合
     */
    private boolean isNonPrescribablePattern(OWLNamedIndividual pattern) {
        // 策略(1)：显式 patternRole 属性
        try {
            OWLDataProperty roleProp = backendService.getDataProperty(PATTERN_ROLE);
            Set<OWLLiteral> roles = backendService.getDataPropertyValueOfIndividual(pattern, roleProp);
            if (roles != null && !roles.isEmpty()) {
                String role = roles.iterator().next().getLiteral();
                if (!"prescribable".equals(role)) {
                    log.trace("  patternRole={} -> 排除: {}", role, pattern.getIRI().getShortForm());
                    return true;
                }
                return false;
            }
        } catch (Exception e) {
            log.trace("patternRole属性不可用，回退: {}", e.getMessage());
        }

        // 策略(2)：owl:deprecated 检查
        try {
            Set<OWLLiteral> depValues = backendService.getAnnotationValue(
                    pattern, "http://www.w3.org/2002/07/owl#deprecated");
            if (depValues != null) {
                for (OWLLiteral lit : depValues) {
                    if ("true".equals(lit.getLiteral())) {
                        log.trace("  deprecated=true -> 排除: {}", pattern.getIRI().getShortForm());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.trace("deprecated检查不可用: {}", e.getMessage());
        }

        // 策略(3)：占位集合回退
        String shortForm = pattern.getIRI().getShortForm();
        return PLACEHOLDER_SHORT_FORMS.contains(shortForm);
    }

    // ================================================================
    // 阶段C：兼夹兼容性校验
    // ================================================================

    private List<OWLNamedIndividual> executePhaseC(
            List<OWLNamedIndividual> candidates, Set<String> concomitantPathologies) {

        OWLObjectProperty hasConcomitant = backendService.safeGetObjectProperty(HAS_CONCOMITANT);
        if (hasConcomitant == null) {
            log.warn("阶段C：has_concomitant属性未定义，跳过兼夹校验");
            return candidates;
        }

        if (concomitantPathologies == null) {
            concomitantPathologies = Collections.emptySet();
        }

        List<OWLNamedIndividual> passed = new ArrayList<>();
        for (OWLNamedIndividual pattern : candidates) {
            Set<OWLNamedIndividual> patternConcomitants =
                    backendService.safeGetAllObjectPropertyValues(pattern, hasConcomitant);

            if (patternConcomitants == null || patternConcomitants.isEmpty()) {
                // 路径1：纯方，无兼夹要求 -> 通过
                passed.add(pattern);
                log.debug("  阶段C通过（纯方）: {}", pattern.getIRI().getShortForm());
                continue;
            }

            // 路径2/3：有兼夹要求
            Set<String> patternConcomitantIris = patternConcomitants.stream()
                    .map(c -> c.getIRI().toString())
                    .collect(Collectors.toSet());

            Set<String> intersection = new HashSet<>(patternConcomitantIris);
            intersection.retainAll(concomitantPathologies);

            if (!intersection.isEmpty()) {
                // 路径2：交集非空 -> 通过
                passed.add(pattern);
                log.info("  阶段C通过（兼夹匹配）: {} (IRI={}) 交集={}",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(), intersection);
            } else {
                // 路径3：交集为空 -> 不通过
                log.info("  阶段C未通过（兼夹不匹配）: {} (IRI={}) 需要={}",
                        backendService.resolveLabel(pattern.getIRI().toString()),
                        pattern.getIRI().toString(), patternConcomitantIris);
            }
        }

        return passed;
    }

    // ================================================================
    // 阶段D：病证->方剂反向映射
    // ================================================================

    /**
     * 阶段D：病证->方剂反向映射（修复版）
     *
     * 【修复说明】
     * 原代码使用 SPARQL 查询 fj:indicated_for，但 loadFormulaAbox() 加载到推理器ABox中的数据
     * 使用的是 fj:indicated_for 谓词。两者谓词不一致导致Phase D无法正确匹配方剂。
     *
     * 修复策略：优先使用推理器查询 fj:indicated_for（与ABox数据一致），
     * 仅在推理器不可用时回退到SPARQL（同时修正谓词为 fj:indicated_for）。
     */
    private Map<String, Object> executePhaseD(List<OWLNamedIndividual> matchedPatterns) {
        List<String> formulaIris = new ArrayList<>();
        List<String> patternIris = new ArrayList<>();
        Set<String> matchedPatternIriSet = matchedPatterns.stream()
                .map(p -> p.getIRI().toString())
                .collect(Collectors.toSet());

        OWLDataFactory df = backendService.getOntologyService().getDataFactory();

        log.info("=======================================================");
        log.info("阶段D：病证->方剂反向映射");
        log.info("  匹配的病证数: {}", matchedPatterns.size());
        for (OWLNamedIndividual p : matchedPatterns) {
            String label = backendService.resolveLabel(p.getIRI().toString());
            log.info("    病证: {} (IRI={})", label, p.getIRI().toString());
            try {
                OWLObjectProperty hasSymptomProp = df.getOWLObjectProperty(IRI.create(TCM_NS + "has_symptom"));
                if (hasSymptomProp != null) {
                    Set<OWLNamedIndividual> syms = backendService.safeGetAllObjectPropertyValues(p, hasSymptomProp);
                    if (syms != null && !syms.isEmpty()) {
                        log.info("      主症:");
                        for (OWLNamedIndividual s : syms) {
                            log.info("        - {} (IRI={})",
                                    backendService.resolveLabel(s.getIRI().toString()), s.getIRI().toString());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("  无法获取病证主症: {}", e.getMessage());
            }
        }

        // ===== 从病证侧查询 fj:indicated_for（病证 --> 方剂） =====
        try {
            OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
            OWLObjectProperty indicatedForProp = df.getOWLObjectProperty(IRI.create(FJ_NS + "indicated_for"));
            if (indicatedForProp != null && reasoner != null) {
                log.info("阶段D：从病证侧查询 fj:indicated_for 关系");
                log.info("  匹配的病证数: {}", matchedPatterns.size());
                for (OWLNamedIndividual pattern : matchedPatterns) {
                    Set<OWLNamedIndividual> formulas = backendService.safeGetAllObjectPropertyValues(
                            pattern, indicatedForProp);
                    if (formulas == null || formulas.isEmpty()) {
                        log.warn("  [警告] 病证 {} 无 indicated_for 断言",
                                backendService.resolveLabel(pattern.getIRI().toString()));
                        continue;
                    }
                    for (OWLNamedIndividual formula : formulas) {
                        String formulaIri = formula.getIRI().toString();
                        String patternIri = pattern.getIRI().toString();
                        formulaIris.add(formulaIri);
                        patternIris.add(patternIri);
                        log.info("  [方剂匹配] 病证={} (IRI={}) --> 方剂={} (IRI={})",
                                backendService.resolveLabel(patternIri), patternIri,
                                backendService.resolveLabel(formulaIri), formulaIri);
                    }
                }
            } else {
                log.warn("阶段D：推理器或 indicated_for 属性不可用，回退到SPARQL查询");
                fallbackSparqlQuery(matchedPatternIriSet, formulaIris, patternIris);
            }
        } catch (Exception e) {
            log.error("阶段D 推理器查询失败，回退到SPARQL查询", e);
            fallbackSparqlQuery(matchedPatternIriSet, formulaIris, patternIris);
        }

        // 去重（保留顺序）
        Set<String> uniqueFormulas = new LinkedHashSet<>(formulaIris);
        Set<String> uniquePatterns = new LinkedHashSet<>(patternIris);
        log.info("阶段D 完成：匹配方剂数={}, 病证数={}", uniqueFormulas.size(), uniquePatterns.size());
        for (String fi : uniqueFormulas) {
            log.info("  匹配方剂: {} (IRI={})", backendService.resolveLabel(fi), fi);
        }
        log.info("=======================================================");
        return Map.of("formulaIris", new ArrayList<>(uniqueFormulas),
                "patternIris", new ArrayList<>(uniquePatterns));
    }    private void fallbackSparqlQuery(Set<String> matchedPatternIriSet,
                                          List<String> formulaIris, List<String> patternIris) {
        try {
            String sparql = "PREFIX tcm: <http://www.tcm-classics.org/tcm#> " +
                    "PREFIX fj: <http://www.tcm-classics.org/fangji#> " +
                    "CONSTRUCT { ?pattern fj:indicated_for ?formula } " +
                    "WHERE { ?pattern fj:indicated_for ?formula }";
            Model resultModel = OBDAHandler.queryConstruct(sparql);
            if (resultModel != null) {
                StmtIterator it = resultModel.listStatements();
                while (it.hasNext()) {
                    Statement stmt = it.next();
                    Resource formulaRes = stmt.getSubject();
                    RDFNode patternNode = stmt.getObject();
                    if (formulaRes.isURIResource() && patternNode.isURIResource()) {
                        String formulaIri = formulaRes.getURI();
                        String patternIri = patternNode.asResource().getURI();
                        if (matchedPatternIriSet.contains(patternIri)) {
                            formulaIris.add(formulaIri);
                            patternIris.add(patternIri);
                            log.info("  [SPARQL回退-方剂匹配] 方剂={} --> 病证={}",
                                    backendService.resolveLabel(formulaIri),
                                    backendService.resolveLabel(patternIri));
                        }
                    }
                }
                resultModel.close();
            }
        } catch (Exception e) {
            log.error("阶段D SPARQL 回退查询失败", e);
        }
    }

    // ================================================================
    // 阶段E：结果判定
    // ================================================================

    private MatchStatus determineStatus(int matchCount) {
        if (matchCount == 0) {
            return MatchStatus.NO_MATCH;
        }
        if (matchCount == 1) {
            return MatchStatus.UNIQUE_MATCH;
        }
        return MatchStatus.AMBIGUOUS;
    }

    // ================================================================
    // 阶段F：完整证候图构建（v5.3 新增脉象/舌象完整信息）
    // ================================================================

    // v5.3 新增：patientPulseIris 和 patientTongueIris 参数用于计算吻合度
    private Map<String, Object> buildCompletePatternView(
            OWLNamedIndividual pattern,
            Set<String> patientSymptomIris,
            Set<String> patientPulseIris,
            Set<String> patientTongueIris) {

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("patternIri", pattern.getIRI().toString());
        view.put("patternLabel", backendService.resolveLabel(pattern.getIRI().toString()));

        // 方证自身的主症、脉象、舌象
        OWLObjectProperty hasSymptom = backendService.safeGetObjectProperty(HAS_SYMPTOM);
        OWLObjectProperty hasPulse = backendService.safeGetObjectProperty(HAS_PULSE);
        OWLObjectProperty hasTongue = backendService.safeGetObjectProperty(LJ_HAS_TYPICAL_TONGUE);  // v5.3 新增
        OWLObjectProperty hasOrSymptom = backendService.safeGetObjectProperty(HAS_OR_SYMPTOM);

        Set<String> ownSymptoms = backendService.getIriSet(pattern, hasSymptom);
        Set<String> ownPulses = backendService.getIriSet(pattern, hasPulse);
        Set<String> ownTongues = backendService.getIriSet(pattern, hasTongue);  // v5.3 新增

        view.put("ownSymptoms", new ArrayList<>(ownSymptoms));
        view.put("ownPulses", new ArrayList<>(ownPulses));
        view.put("ownTongues", new ArrayList<>(ownTongues));  // v5.3 新增

        // 从六经实例读取提纲信息
        OWLObjectProperty belongsToLiujing = backendService.safeGetObjectProperty(BELONGS_TO_LIUJING);
        OWLObjectProperty ljHasTypicalSymptom = backendService.safeGetObjectProperty(LJ_HAS_TYPICAL_SYMPTOM);
        OWLObjectProperty ljHasTypicalPulse = backendService.safeGetObjectProperty(LJ_HAS_TYPICAL_PULSE);
        OWLObjectProperty ljHasTypicalTongue = backendService.safeGetObjectProperty(LJ_HAS_TYPICAL_TONGUE);  // v5.3 新增
        OWLDataProperty ljHasTreatmentPrinciple = backendService.safeGetDataProperty(LJ_HAS_TREATMENT_PRINCIPLE);
        OWLDataProperty ljHasContraindication = backendService.safeGetDataProperty(LJ_HAS_CONTRAINDICATION);
        OWLDataProperty ljHasKeySymptom = backendService.safeGetDataProperty(LJ_HAS_KEY_SYMPTOM);

        Set<OWLNamedIndividual> liujings = backendService.safeGetAllObjectPropertyValues(pattern, belongsToLiujing);

        Set<String> liujingSymptoms = new LinkedHashSet<>();
        Set<String> liujingPulses = new LinkedHashSet<>();
        Set<String> liujingTongues = new LinkedHashSet<>();  // v5.3 新增
        List<String> liujingLabels = new ArrayList<>();
        List<String> treatmentPrinciples = new ArrayList<>();
        List<String> contraindications = new ArrayList<>();
        List<String> keySymptomTexts = new ArrayList<>();

        if (liujings != null && !liujings.isEmpty()) {
            for (OWLNamedIndividual lj : liujings) {
                liujingLabels.add(backendService.resolveLabel(lj.getIRI().toString()));

                if (ljHasTypicalSymptom != null) {
                    Set<OWLNamedIndividual> typSyms = backendService.safeGetAllObjectPropertyValues(lj, ljHasTypicalSymptom);
                    if (typSyms != null) {
                        typSyms.forEach(s -> liujingSymptoms.add(s.getIRI().toString()));
                    }
                }

                if (ljHasTypicalPulse != null) {
                    Set<OWLNamedIndividual> typPulses = backendService.safeGetAllObjectPropertyValues(lj, ljHasTypicalPulse);
                    if (typPulses != null) {
                        typPulses.forEach(p -> liujingPulses.add(p.getIRI().toString()));
                    }
                }

                // v5.3 新增：读取六经提纲舌象
                if (ljHasTypicalTongue != null) {
                    Set<OWLNamedIndividual> typTongues = backendService.safeGetAllObjectPropertyValues(lj, ljHasTypicalTongue);
                    if (typTongues != null) {
                        typTongues.forEach(t -> liujingTongues.add(t.getIRI().toString()));
                    }
                }

                if (ljHasTreatmentPrinciple != null) {
                    Set<OWLLiteral> tp = backendService.safeGetDataPropertyValues(lj, ljHasTreatmentPrinciple);
                    if (tp != null) {
                        tp.forEach(l -> treatmentPrinciples.add(l.getLiteral()));
                    }
                }

                if (ljHasContraindication != null) {
                    Set<OWLLiteral> ci = backendService.safeGetDataPropertyValues(lj, ljHasContraindication);
                    if (ci != null) {
                        ci.forEach(l -> contraindications.add(l.getLiteral()));
                    }
                }

                if (ljHasKeySymptom != null) {
                    Set<OWLLiteral> ks = backendService.safeGetDataPropertyValues(lj, ljHasKeySymptom);
                    if (ks != null) {
                        ks.forEach(l -> keySymptomTexts.add(l.getLiteral()));
                    }
                }
            }
        }

        // 合并去重
        Set<String> completeSymptoms = new LinkedHashSet<>(ownSymptoms);
        completeSymptoms.addAll(liujingSymptoms);
        Set<String> completePulses = new LinkedHashSet<>(ownPulses);
        completePulses.addAll(liujingPulses);
        Set<String> completeTongues = new LinkedHashSet<>(ownTongues);  // v5.3 新增
        completeTongues.addAll(liujingTongues);

        // v5.3 新增：计算脉象/舌象吻合度
        double pulseMatchDegree = 0.0;
        if (completePulses != null && !completePulses.isEmpty() && patientPulseIris != null) {
            Set<String> intersection = new HashSet<>(completePulses);
            intersection.retainAll(patientPulseIris);
            pulseMatchDegree = (double) intersection.size() / completePulses.size();
        }

        double tongueMatchDegree = 0.0;
        if (completeTongues != null && !completeTongues.isEmpty() && patientTongueIris != null) {
            Set<String> intersection = new HashSet<>(completeTongues);
            intersection.retainAll(patientTongueIris);
            tongueMatchDegree = (double) intersection.size() / completeTongues.size();
        }

        view.put("liujingLabels", liujingLabels);
        view.put("liujingTypicalSymptoms", new ArrayList<>(liujingSymptoms));
        view.put("liujingTypicalPulses", new ArrayList<>(liujingPulses));
        view.put("liujingTypicalTongues", new ArrayList<>(liujingTongues));  // v5.3 新增
        view.put("completeSymptoms", new ArrayList<>(completeSymptoms));
        view.put("completePulses", new ArrayList<>(completePulses));
        view.put("completeTongues", new ArrayList<>(completeTongues));  // v5.3 新增
        view.put("keySymptomTexts", keySymptomTexts);
        view.put("treatmentPrinciples", treatmentPrinciples);
        view.put("contraindications", contraindications);
        // v5.3 新增：吻合度
        view.put("pulseMatchDegree", pulseMatchDegree);
        view.put("tongueMatchDegree", tongueMatchDegree);

        log.debug("  阶段F：完整证候图构建完成 -> {} (六经:{}) 脉象吻合={:.2f} 舌象吻合={:.2f}",
                pattern.getIRI().getShortForm(), liujingLabels, pulseMatchDegree, tongueMatchDegree);

        return view;
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private List<OWLNamedIndividual> getAllDiseasePatterns() {
        try {
            Set<OWLNamedIndividual> individuals = backendService.getIndividuals(TCM_NS + "DiseasePattern");
            return new ArrayList<>(individuals);
        } catch (Exception e) {
            log.error("获取DiseasePattern个体失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ================================================================
    // v6.0 新增：降级策略 — 反向满足度 Top5
    // 当所有阶段均无匹配时，对所有 DiseasePattern 按"反向满足度"排序，返回最相似的 5 个
    // ================================================================
    private Map<String, Object> executeFallback(
            Set<String> symptomIris,
            Set<String> pulseIris,
            Set<String> tongueIris,
            Map<String, Object> result,
            String reason) {
        log.warn("=======================================================");
        log.warn("[v6.0 降级策略] 所有阶段均无完全匹配，原因: {}，启动反向满足度 Top5 排序", reason);
        log.warn("=======================================================");

        // 获取所有 DiseasePattern 个体
        List<OWLNamedIndividual> allPatterns = getAllDiseasePatterns();
        if (allPatterns == null || allPatterns.isEmpty()) {
            log.warn("[v6.0 降级] 未获取到任何 DiseasePattern 个体，返回空结果");
            return buildNoMatchResult(result, "未找到可匹配的方证（无 DiseasePattern 个体）");
        }

        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLObjectProperty hasSymptomProp = df.getOWLObjectProperty(IRI.create(TCM_NS + "has_symptom"));
        OWLObjectProperty hasPulseProp = df.getOWLObjectProperty(IRI.create(TCM_NS + "has_pulse"));
        OWLObjectProperty hasTongueProp = df.getOWLObjectProperty(IRI.create(LJ_HAS_TYPICAL_TONGUE));

        // 计算每个模式的反向满足度
        List<Map<String, Object>> scoredPatterns = new ArrayList<>();
        for (OWLNamedIndividual pattern : allPatterns) {
            // 排除非可处方模式
            if (isNonPrescribablePattern(pattern)) {
                continue;
            }

            // 获取方证的主症集合
            Set<OWLNamedIndividual> patternSymptoms = null;
            try {
                if (hasSymptomProp != null) {
                    patternSymptoms = backendService.safeGetAllObjectPropertyValues(
                            pattern, hasSymptomProp);
                }
            } catch (Exception e) {
                log.debug("  获取主症失败: {} | {}", pattern.getIRI().getShortForm(), e.getMessage());
            }
            if (patternSymptoms == null || patternSymptoms.isEmpty()) {
                continue;
            }

            Set<String> patternSymptomIris = patternSymptoms.stream()
                    .map(s -> s.getIRI().toString())
                    .collect(Collectors.toSet());

            // 正向完备度：方证主症中有多少被患者覆盖
            long coveredByPatient = patternSymptomIris.stream()
                    .filter(symptomIris::contains)
                    .count();
            double forwardScore = (double) coveredByPatient / patternSymptomIris.size();

            // 反向覆盖度：患者症状中有多少被方证覆盖
            long patientCovered = symptomIris.stream()
                    .filter(patternSymptomIris::contains)
                    .count();
            double reverseScore = symptomIris.isEmpty() ? 0.0
                    : (double) patientCovered / symptomIris.size();

            // 综合得分：反向权重更高（0.8），正向为辅（0.2）
            double totalScore = forwardScore * 0.2 + reverseScore * 0.8;

            // 脉象吻合度
            double pulseMatchDegree = 0.0;
            try {
                if (hasPulseProp != null && pulseIris != null && !pulseIris.isEmpty()) {
                    Set<OWLNamedIndividual> patternPulses = backendService.safeGetAllObjectPropertyValues(
                            pattern, hasPulseProp);
                    if (patternPulses != null && !patternPulses.isEmpty()) {
                        Set<String> patternPulseIris = patternPulses.stream()
                                .map(p -> p.getIRI().toString())
                                .collect(Collectors.toSet());
                        Set<String> intersection = new HashSet<>(patternPulseIris);
                        intersection.retainAll(pulseIris);
                        pulseMatchDegree = (double) intersection.size() / patternPulseIris.size();
                    }
                }
            } catch (Exception e) {
                // 忽略脉象查询异常
            }

            // 舌象吻合度
            double tongueMatchDegree = 0.0;
            try {
                if (hasTongueProp != null && tongueIris != null && !tongueIris.isEmpty()) {
                    Set<OWLNamedIndividual> patternTongues = backendService.safeGetAllObjectPropertyValues(
                            pattern, hasTongueProp);
                    if (patternTongues != null && !patternTongues.isEmpty()) {
                        Set<String> patternTongueIris = patternTongues.stream()
                                .map(t -> t.getIRI().toString())
                                .collect(Collectors.toSet());
                        Set<String> intersection = new HashSet<>(patternTongueIris);
                        intersection.retainAll(tongueIris);
                        tongueMatchDegree = (double) intersection.size() / patternTongueIris.size();
                    }
                }
            } catch (Exception e) {
                // 忽略舌象查询异常
            }

            // 获取六经归属
            List<String> liujingLabels = new ArrayList<>();
            try {
                OWLObjectProperty belongsToProp = backendService.safeGetObjectProperty(BELONGS_TO_LIUJING);
                if (belongsToProp != null) {
                    Set<OWLNamedIndividual> liujings = backendService.safeGetAllObjectPropertyValues(
                            pattern, belongsToProp);
                    if (liujings != null) {
                        liujingLabels = liujings.stream()
                                .map(lj -> backendService.resolveLabel(lj.getIRI().toString()))
                                .collect(Collectors.toList());
                    }
                }
            } catch (Exception e) {
                // 忽略
            }

            scoredPatterns.add(Map.of(
                    "patternIri", pattern.getIRI().toString(),
                    "patternLabel", backendService.resolveLabel(pattern.getIRI().toString()),
                    "forwardScore", forwardScore,
                    "reverseScore", reverseScore,
                    "totalScore", totalScore,
                    "pulseMatchDegree", pulseMatchDegree,
                    "tongueMatchDegree", tongueMatchDegree,
                    "liujingLabels", liujingLabels,
                    "coveredSymptoms", coveredByPatient,
                    "totalSymptoms", patternSymptomIris.size()
            ));
        }

        // 按综合得分降序排序，取 Top5
        scoredPatterns.sort((a, b) -> Double.compare(
                (double) b.get("totalScore"), (double) a.get("totalScore")));

        List<Map<String, Object>> top5 = scoredPatterns.subList(
                0, Math.min(5, scoredPatterns.size()));

        log.info("[v6.0 降级] Top5 排序结果:");
        for (int i = 0; i < top5.size(); i++) {
            Map<String, Object> item = top5.get(i);
            log.info("  ({}) {} | 综合={:.2f} | 正向={:.2f} | 反向={:.2f} | 脉象={:.2f} | 舌象={:.2f} | 六经={}",
                    i + 1,
                    item.get("patternLabel"),
                    item.get("totalScore"),
                    item.get("forwardScore"),
                    item.get("reverseScore"),
                    item.get("pulseMatchDegree"),
                    item.get("tongueMatchDegree"),
                    item.get("liujingLabels"));
        }

        // 构建返回结果
        result.put("matchStatus", "NO_MATCH_FALLBACK");
        result.put("noMatchReason", reason);
        result.put("fallbackEnabled", true);
        result.put("top5Candidates", top5);

        // 提取方剂IRI和病证IRI
        List<String> formulaIris = new ArrayList<>();
        List<String> patternIris = new ArrayList<>();
        for (Map<String, Object> item : top5) {
            patternIris.add((String) item.get("patternIri"));
            // 查询方剂
            try {
                OWLObjectProperty indicatedForProp = df.getOWLObjectProperty(
                        IRI.create(FJ_NS + "indicated_for"));
                Set<OWLNamedIndividual> formulas = backendService.safeGetAllObjectPropertyValues(
                        backendService.getIndividual((String) item.get("patternIri")),
                        indicatedForProp);
                if (formulas != null) {
                    for (OWLNamedIndividual f : formulas) {
                        String fi = f.getIRI().toString();
                        if (!formulaIris.contains(fi)) {
                            formulaIris.add(fi);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("  查询方剂失败: {}", e.getMessage());
            }
        }
        result.put("matchedFormulaIris", formulaIris);
        result.put("matchedPatternIris", patternIris);
        result.put("completePatternViews", Collections.emptyList());

        log.warn("[v6.0 降级] 返回 Top5 候选方证，fallbackEnabled=true");
        log.warn("=======================================================");
        return result;
    }

    private Map<String, Object> buildNoMatchResult(Map<String, Object> result, String reason) {
        result.put("matchStatus", MatchStatus.NO_MATCH.name());
        result.put("matchedFormulaIris", Collections.emptyList());
        result.put("matchedPatternIris", Collections.emptyList());
        result.put("noMatchReason", reason);
        result.put("completePatternViews", Collections.emptyList());
        log.warn("第四步结果：NO_MATCH — {}", reason);
        return result;
    }

    private String buildDifferentialHint(List<String> patternIris) {
        StringBuilder sb = new StringBuilder();
        sb.append("多个方证同时匹配，需进一步鉴别：\n");
        for (int i = 0; i < patternIris.size(); i++) {
            sb.append(String.format("  (%d) %s\n", i + 1, backendService.resolveLabel(patternIris.get(i))));
        }
        sb.append("建议：补充或然症信息或脉象/舌象细节以缩小范围。");
        return sb.toString();
    }

    // ==================== 第五步：治法确认与禁忌校验 ====================
    @JobWorker(type = "step5-zhifa-check", autoComplete = false)
    public void handleStep5ZhiFaCheck(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String bestFormulaIri = (String) vars.get("bestMatchFormulaIri");
            String sixChannel = (String) vars.get("sixChannel");

            List<String> treatmentMethods = new ArrayList<>();
            List<String> contraindications = new ArrayList<>();

            if (bestFormulaIri != null) {
                treatmentMethods = queryService.queryPropertyValueInOntology(bestFormulaIri,
                        TCM_NS + "has_treatment_method");
                contraindications = queryService.queryPropertyValueInOntology(bestFormulaIri,
                        TCM_NS + "contraindication");
            }

            // 六经通用禁忌
            if (sixChannel != null && sixChannel.contains("Shaoyang")) {
                contraindications.add("禁汗、禁下、禁吐");
            } else if (sixChannel != null && sixChannel.contains("Taiyang")) {
                contraindications.add("禁下");
            } else if (sixChannel != null && sixChannel.contains("Yangming")) {
                contraindications.add("禁汗");
            } else if (sixChannel != null && (sixChannel.contains("Taiyin") || sixChannel.contains("Shaoyin"))) {
                contraindications.add("禁苦寒攻下");
            } else if (sixChannel != null && sixChannel.contains("Jueyin")) {
                contraindications.add("禁下");
            }

            String treatmentSummary = String.join("；", treatmentMethods) +
                    (contraindications.isEmpty() ? "" : "；禁忌：" + String.join("；", contraindications));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("treatmentMethods", treatmentMethods);
            result.put("contraindications", contraindications);
            result.put("treatmentSummary", treatmentSummary);
            result.put("clinicalCaseIri", vars.get("clinicalCaseIri"));

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("step5-zhifa-check 完成 | jobKey={} | 治法数={}", job.getKey(), treatmentMethods.size());

        } catch (Exception e) {
            log.error("step5-zhifa-check 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP5_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ==================== 第六步：复诊与传变评估 ====================
    @JobWorker(type = "step6-fuzhen", autoComplete = false)
    public void handleStep6FuZhen(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String caseIri = (String) vars.get("clinicalCaseIri");
            @SuppressWarnings("unchecked")
            List<String> followupSymptoms = (List<String>) vars.get("followupSymptoms");
            if (followupSymptoms == null) followupSymptoms = Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<String> originalSymptoms = (List<String>) vars.get("symptomIris");
            boolean allResolved = originalSymptoms != null && !originalSymptoms.isEmpty() &&
                    followupSymptoms.stream().noneMatch(originalSymptoms::contains);

            boolean cured = allResolved || (followupSymptoms.isEmpty() && vars.get("bestMatchFormulaIri") != null);

            boolean hasTransmission = false;
            if (!cured && !followupSymptoms.isEmpty()) {
                String newSix = inferSixChannelByJaccard(followupSymptoms);
                String oldSix = (String) vars.get("sixChannel");
                if (newSix != null && !newSix.equals(oldSix)) {
                    hasTransmission = true;
                }
            }

            // 更新案例状态（简单记录）
            if (hasTransmission) {
                Map<String, String> update = new LinkedHashMap<>();
                update.put(TCM_NS + "belongs_to_channel", inferSixChannelByJaccard(followupSymptoms));
                updateService.updateComponentAutoSplit(
                        Map.of(TCM_NS + "name", caseIri),
                        update
                );
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cured", cured);
            result.put("hasTransmission", hasTransmission);
            result.put("followupSymptomIris", followupSymptoms);
            result.put("clinicalCaseIri", caseIri);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("step6-fuzhen 完成 | jobKey={} | 痊愈={} | 传变={}", job.getKey(), cured, hasTransmission);

        } catch (Exception e) {
            log.error("step6-fuzhen 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP6_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ==================== 辅助方法（保留） ====================
    private String createClinicalCase(List<String> symptomIris) throws OWLOntologyCreationException {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLOntologyManager manager = backendService.getOntologyService().getManager();

        String indNS = "http://www.tcm-classics.org/clinical/";
        String instanceName = "Case_" + System.currentTimeMillis();
        IRI individualIRI = IRI.create(indNS + instanceName);
        OWLNamedIndividual caseInd = df.getOWLNamedIndividual(individualIRI);

        OWLClass clinicalCaseClass = df.getOWLClass(IRI.create(TCM_NS + "ClinicalCase"));
        manager.addAxiom(tbox, df.getOWLClassAssertionAxiom(clinicalCaseClass, caseInd));

        OWLDataProperty nameProp = df.getOWLDataProperty(IRI.create(TCM_NS + "name"));
        manager.addAxiom(tbox, df.getOWLDataPropertyAssertionAxiom(nameProp, caseInd, df.getOWLLiteral(instanceName)));

        OWLObjectProperty hasSymptomProp = df.getOWLObjectProperty(IRI.create(TCM_NS + "has_symptom"));
        for (String symptomIri : symptomIris) {
            if (symptomIri != null && !symptomIri.isBlank()) {
                OWLNamedIndividual symptomInd = df.getOWLNamedIndividual(IRI.create(symptomIri));
                manager.addAxiom(tbox, df.getOWLObjectPropertyAssertionAxiom(hasSymptomProp, caseInd, symptomInd));
            }
        }

        backendService.getReasonerService().getReasoner().flush();
        return individualIRI.toString();
    }
}