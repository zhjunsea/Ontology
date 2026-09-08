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
    private static final String TX_NS = "http://www.tcm-classics.org/shexiang#";
    private static final String YW_NS = "http://www.tcm-classics.org/yaowu#";

    // 属性IRI常量
    private static final String HAS_SYMPTOM = TCM_NS + "has_symptom";
    private static final String HAS_PULSE = TCM_NS + "has_pulse";
    private static final String LJ_HAS_TYPICAL_TONGUE = LJ_NS + "hasTypicalTongue";
    private static final String HAS_CONCOMITANT = BZ_NS + "has_concomitant";
    private static final String BELONGS_TO_LIUJING = BZ_NS + "belongs_to_liujing";
    private static final String PATTERN_ROLE = BZ_NS + "patternRole";
    private static final String INDICATED_FOR = FJ_NS + "indicated_for";
    private static final String VARIANT_OF = BZ_NS + "variantOf";
    private static final String HAS_EXCLUSION_SYMPTOM = BZ_NS + "has_exclusion_symptom";
    private static final String HAS_MODIFICATION_RULE = BZ_NS + "has_modification_rule";
    private static final String TRIGGER_SYMPTOM = BZ_NS + "trigger_symptom";
    private static final String ACTION_DESCRIPTION = BZ_NS + "action_description";
    private static final String HAS_BAGANG_ELEMENT = TCM_NS + "has_bagang_element";
    private static final String EVIDENCE_LEVEL = ZZ_NS + "evidenceLevel";

    private SixChannelInferenceEngine sixChannelEngine;

    @PostConstruct
    public void init() {
        try {
            log.info("初始化 TCMOntologyJobWorker 依赖链...");
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
            OBDAHandler obdaHandler = OBDAHandler.getInstance();
            this.backendService = BackendService.getInstance(mainOntologyPath, obdaHandler);
            if (this.backendService == null) {
                throw new IllegalStateException("BackendService 初始化失败");
            }
            this.insertService = new InsertService(this.backendService);
            this.updateService = new UpdateService(this.backendService);
            this.deleteService = new DeleteService(this.backendService);
            this.queryService = new QueryService(this.backendService);

            // 加载方剂 ABox
            loadFormulaAbox();

            this.sixChannelEngine = new SixChannelInferenceEngine(this.backendService);
            this.sixChannelEngine.buildIndexes();

            log.info("TCMOntologyJobWorker 初始化完成");
        } catch (Exception e) {
            log.error("初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

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
        backendService.getObdaHandler().loadAboxFromOntop(constructSparql, tbox);
        backendService.getReasonerService().getReasoner().flush();
        log.info("方剂 ABox 加载完成");
    }

    // ====================================================================
//  Step 1: 八纲综合判定（证据链驱动 + 证型高层裁决）
// ====================================================================
    @JobWorker(type = "step1-bagang", autoComplete = false)
    public void handleStep1BaGang(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            @SuppressWarnings("unchecked")
            List<String> pulseIris = (List<String>) vars.get("pulseIris");
            @SuppressWarnings("unchecked")
            List<String> tongueIris = (List<String>) vars.get("tongueIris");

            if (symptomIris == null) symptomIris = Collections.emptyList();
            if (pulseIris == null) pulseIris = Collections.emptyList();
            if (tongueIris == null) tongueIris = Collections.emptyList();

            // ★★★ 新增：输入校验 ★★★
            validateInputIris(symptomIris, "症状");
            validateInputIris(pulseIris, "脉象");
            validateInputIris(tongueIris, "舌象");

            log.info("Step1 收到患者数据：症状数={}, 脉象数={}, 舌象数={}",
                    symptomIris.size(), pulseIris.size(), tongueIris.size());

            // 分别收集三类证据
            Map<IRI, List<String>> symptomEvidence = collectBagangEvidence(new HashSet<>(symptomIris));
            Map<IRI, List<String>> pulseEvidence = collectBagangEvidence(new HashSet<>(pulseIris));
            Map<IRI, List<String>> tongueEvidence = collectBagangEvidence(new HashSet<>(tongueIris));

            // ★ 第一步：基层投票（原有决策逻辑）
            String biaoLi = decideBiaoLi(symptomEvidence, pulseEvidence);
            String hanRe = decideHanRe(symptomEvidence, pulseEvidence, tongueEvidence);
            String xuShi = decideXuShi(symptomEvidence, pulseEvidence, tongueEvidence);
            String yinYang = decideYinYang(symptomEvidence, pulseEvidence, tongueEvidence, symptomIris);

            // ★★★ 关键修正：证据链列表必须在高层裁决之前定义 ★★★
            List<String> evidenceChain = new ArrayList<>();
            evidenceChain.add("症状数: " + symptomIris.size());
            evidenceChain.add("脉象数: " + pulseIris.size());
            evidenceChain.add("舌象数: " + tongueIris.size());

            // ★★★ 第二步：高层裁决（证型预匹配与补全） ★★★
            PatternMatch bestPattern = matchBestPatternForStep1(symptomIris, pulseIris, tongueIris);
            if (bestPattern != null && bestPattern.getScore() >= 0.8) {
                OWLNamedIndividual pattern = backendService.getIndividual(bestPattern.getPatternIri());
                if (pattern != null) {
                    String patternLabel = backendService.resolveLabel(bestPattern.getPatternIri());
                    log.info("Step1 预匹配到高置信度证型：{}，命中率={}", patternLabel, String.format("%.2f", bestPattern.getScore()));
                    Set<OWLNamedIndividual> bagangElements = getBagangElements(pattern);

                    // 辅助函数：检查八纲标签短名是否存在
                    java.util.function.Function<String, Boolean> hasBagangShort = (shortName) ->
                            bagangElements.stream().anyMatch(bg -> {
                                String bgShort = getShortForm(bg.getIRI().toString());
                                return bgShort.equals(shortName);
                            });

                    // 补全表里（仅当为“未定”）
                    if ("未定".equals(biaoLi)) {
                        if (hasBagangShort.apply("Biao")) biaoLi = "表证";
                        else if (hasBagangShort.apply("Li")) biaoLi = "里证";
                        else if (hasBagangShort.apply("BanBiaoBanLi")) biaoLi = "半表半里";
                        if (!"未定".equals(biaoLi)) {
                            evidenceChain.add("高层裁定：由证型 '" + patternLabel + "' 补充病位=" + biaoLi);
                        }
                    }

                    // 补全寒热
                    if ("未定".equals(hanRe)) {
                        if (hasBagangShort.apply("Han")) hanRe = "寒证";
                        else if (hasBagangShort.apply("Re")) hanRe = "热证";
                        if (!"未定".equals(hanRe)) {
                            evidenceChain.add("高层裁定：由证型 '" + patternLabel + "' 补充寒热=" + hanRe);
                        }
                    }

                    // 补全虚实
                    if ("未定".equals(xuShi)) {
                        if (hasBagangShort.apply("Xu")) xuShi = "虚证";
                        else if (hasBagangShort.apply("Shi")) xuShi = "实证";
                        if (!"未定".equals(xuShi)) {
                            evidenceChain.add("高层裁定：由证型 '" + patternLabel + "' 补充虚实=" + xuShi);
                        }
                    }

                    // 补全阴阳
                    if ("未定".equals(yinYang)) {
                        if (hasBagangShort.apply("Yang")) yinYang = "阳证";
                        else if (hasBagangShort.apply("Yin")) yinYang = "阴证";
                        if (!"未定".equals(yinYang)) {
                            evidenceChain.add("高层裁定：由证型 '" + patternLabel + "' 补充阴阳=" + yinYang);
                        }
                    }
                }
            }

            // 完整性检查
            int incompleteCount = 0;
            if ("未定".equals(biaoLi)) incompleteCount++;
            if ("未定".equals(hanRe)) incompleteCount++;
            if ("未定".equals(xuShi)) incompleteCount++;
            if ("未定".equals(yinYang)) incompleteCount++;
            boolean complete = incompleteCount == 0;

            // 兼夹识别
            List<String> jianjiaMarkers = new ArrayList<>();
            if (hasConcomitant(symptomIris, JJ_NS + "WaterRetention_Instance")) jianjiaMarkers.add(JJ_NS + "WaterRetention_Instance");
            if (hasConcomitant(symptomIris, JJ_NS + "BloodStasis_Instance")) jianjiaMarkers.add(JJ_NS + "BloodStasis_Instance");
            if (hasConcomitant(symptomIris, JJ_NS + "FoodStagnation_Instance")) jianjiaMarkers.add(JJ_NS + "FoodStagnation_Instance");
            if (hasConcomitant(symptomIris, JJ_NS + "QiStagnation_Instance")) jianjiaMarkers.add(JJ_NS + "QiStagnation_Instance");

            // 特殊标记检测
            List<String> specialMarkers = detectSpecialMarkers(
                    biaoLi, hanRe, xuShi, yinYang,
                    symptomIris, pulseIris, tongueIris);

            // ★ 注意：此处不再重新定义 evidenceChain（已在上方定义） ★

            // 构建 bagangResult
            Map<String, Object> bagangResult = new LinkedHashMap<>();
            bagangResult.put("表里", biaoLi);
            bagangResult.put("寒热", hanRe);
            bagangResult.put("虚实", xuShi);
            bagangResult.put("阴阳", yinYang);
            bagangResult.put("complete", complete);
            bagangResult.put("incompleteCount", incompleteCount);
            bagangResult.put("specialMarkers", specialMarkers);
            bagangResult.put("evidence", null);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("bagangResult", bagangResult);
            output.put("jianjiaMarkers", jianjiaMarkers);
            output.put("evidenceChain", evidenceChain); // 使用已定义的列表

            client.newCompleteCommand(job.getKey())
                    .variables(output)
                    .send().join();

            log.info("step1-bagang 完成 | 表里={} 寒热={} 虚实={} 阴阳={} 完整={} 特殊标记={}",
                    biaoLi, hanRe, xuShi, yinYang, complete, specialMarkers);

        } catch (Exception e) {
            log.error("step1-bagang 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP1_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  迭代补充问诊（回退到 Step1）
    // ====================================================================
    @JobWorker(type = "iterative-inquiry", autoComplete = false)
    public void handleIterativeInquiry(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("supplementSymptoms", Collections.emptyList());
            client.newCompleteCommand(job.getKey())
                    .variables(output)
                    .send().join();
            log.info("iterative-inquiry 完成（模拟）");
        } catch (Exception e) {
            log.error("iterative-inquiry 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("INQUIRY_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  低置信度输出（参考方）
    // ====================================================================
    @JobWorker(type = "low-confidence-output", autoComplete = false)
    public void handleLowConfidence(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("finalFormula", FJ_NS + "GuiZhiTang");
            output.put("safetyLevel", "低置信度（需医师确认）");
            output.put("contraindicationWarnings", Collections.singletonList("证据不足，仅供参考"));
            client.newCompleteCommand(job.getKey())
                    .variables(output)
                    .send().join();
            log.info("low-confidence-output 完成");
        } catch (Exception e) {
            log.error("low-confidence-output 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("LOW_CONF_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  Step 2: 主病判定 + ★ 合病/并病识别（六经兼病层）
    // ====================================================================
    @JobWorker(type = "step2-primary", autoComplete = false)
    public void handleStep2Primary(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            @SuppressWarnings("unchecked")
            Map<String, Object> bagangResult = (Map<String, Object>) vars.get("bagangResult");
            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            @SuppressWarnings("unchecked")
            List<String> pulseIris = (List<String>) vars.get("pulseIris");
            @SuppressWarnings("unchecked")
            List<String> tongueIris = (List<String>) vars.get("tongueIris");

            if (bagangResult == null) {
                throw new IllegalArgumentException("缺少 bagangResult");
            }
            if (symptomIris == null) symptomIris = Collections.emptyList();
            if (pulseIris == null) pulseIris = Collections.emptyList();
            if (tongueIris == null) tongueIris = Collections.emptyList();

            log.info("Step2 开始，八纲结果: 表里={}, 寒热={}, 虚实={}, 阴阳={}",
                    bagangResult.get("表里"), bagangResult.get("寒热"),
                    bagangResult.get("虚实"), bagangResult.get("阴阳"));

            // ★ ★ ★ 第一步：急症优先检查（最高优先级）
            EmergencyResult emergency = detectEmergency(symptomIris, pulseIris, tongueIris, bagangResult);

            if (emergency.isEmergency) {
                // 急症触发 → 直接跳至 Step 5，输出急症方
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("isEmergency", true);
                output.put("emergencyFormula", emergency.emergencyFormula);
                output.put("emergencyLabel", emergency.emergencyLabel);
                output.put("emergencyRationale", emergency.rationale);
                output.put("primaryDisease", null);
                output.put("combinedDiseaseMark", null);
                output.put("treatmentPriority", "急症优先");

                client.newCompleteCommand(job.getKey())
                        .variables(output)
                        .send().join();

                log.info("step2-primary 完成 | 急症触发！方剂={} 依据={}",
                        emergency.emergencyFormula, emergency.rationale);
                return;
            }

            // ★ 第二步：常规主病判定
            String biaoLi = (String) bagangResult.get("表里");
            String yinYang = (String) bagangResult.get("阴阳");
            String primaryDisease = derivePrimaryDisease(biaoLi, yinYang);

            // ★ 第三步：合病/并病识别（基于证据强度量化）
            CombinedDiseaseResult combinedResult = detectCombinedDiseaseWithStrength(
                    symptomIris, pulseIris, tongueIris, bagangResult);

            // ★ 第四步：治疗策略（表里同病时根据虚实危急确定治疗顺序）
            String treatmentPriority = determineTreatmentPriority(
                    biaoLi,
                    (String) bagangResult.get("虚实"),
                    (String) bagangResult.get("寒热"),
                    combinedResult);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("isEmergency", false);
            output.put("emergencyFormula", null);
            output.put("emergencyLabel", null);
            output.put("emergencyRationale", null);
            output.put("primaryDisease", primaryDisease);
            output.put("combinedDiseaseMark", combinedResult.mark);
            output.put("combinedDiseaseDetail", combinedResult.detail);
            output.put("treatmentPriority", treatmentPriority);

            client.newCompleteCommand(job.getKey())
                    .variables(output)
                    .send().join();

            log.info("step2-primary 完成 | 主病={} 合病={} 治疗策略={}",
                    primaryDisease, combinedResult.mark, treatmentPriority);

        } catch (Exception e) {
            log.error("step2-primary 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP2_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  辅助结果类
    // ====================================================================

    private static class EmergencyResult {
        final boolean isEmergency;
        final String emergencyFormula;
        final String emergencyLabel;
        final String rationale;

        EmergencyResult(boolean isEmergency, String emergencyFormula, String emergencyLabel, String rationale) {
            this.isEmergency = isEmergency;
            this.emergencyFormula = emergencyFormula;
            this.emergencyLabel = emergencyLabel;
            this.rationale = rationale;
        }

        static EmergencyResult noEmergency() {
            return new EmergencyResult(false, null, null, null);
        }
    }

    private static class CombinedDiseaseResult {
        final String mark;
        final String detail;

        CombinedDiseaseResult(String mark, String detail) {
            this.mark = mark;
            this.detail = detail;
        }

        static CombinedDiseaseResult none() {
            return new CombinedDiseaseResult(null, null);
        }
    }

    // ====================================================================
    //  ★ ★ ★ 急症优先检查（完整实现）
    // ====================================================================

    private EmergencyResult detectEmergency(
            List<String> symptomIris,
            List<String> pulseIris,
            List<String> tongueIris,
            Map<String, Object> bagangResult) {

        Set<String> symptoms = new HashSet<>(symptomIris);
        Set<String> pulses = new HashSet<>(pulseIris);
        Set<String> tongues = new HashSet<>(tongueIris);

        // ----- 1. 亡阳检测 -----
        boolean pulseWeiYuJue = pulses.contains(MX_NS + "Pulse_Wei") || pulses.contains(MX_NS + "Pulse_YuJue");
        boolean pulseFuDaWuGen = pulses.contains(MX_NS + "Pulse_FuDaWuGen") || pulses.contains(MX_NS + "Pulse_Fu");
        boolean daHan = symptoms.contains(ZZ_NS + "DaHan") || symptoms.contains(ZZ_NS + "HanChu");
        boolean hanLengXi = symptoms.contains(ZZ_NS + "HanChuQingXi") || symptoms.contains(ZZ_NS + "LengHan");
        boolean zhiJue = symptoms.contains(ZZ_NS + "SiZhiJueLeng") || symptoms.contains(ZZ_NS + "ZhiJue");
        boolean jingShenWeiMi = symptoms.contains(ZZ_NS + "DanYuMei") ||
                symptoms.contains(ZZ_NS + "JingShenWeiMi") ||
                symptoms.contains(ZZ_NS + "ShenPiFaLi");

        if ((pulseWeiYuJue || pulseFuDaWuGen) && daHan && hanLengXi && zhiJue && jingShenWeiMi) {
            return new EmergencyResult(true, FJ_NS + "SiNiTang", "亡阳",
                    "脉微欲绝/浮大无根 + 大汗冷稀 + 肢厥过肘膝 + 精神萎靡 → 四逆汤/通脉四逆汤");
        }

        // ----- 2. 阳明热盛伤津检测 -----
        boolean daRe = symptoms.contains(ZZ_NS + "DaRe") || symptoms.contains(ZZ_NS + "ShenRe");
        boolean daKe = symptoms.contains(ZZ_NS + "DaKe") || symptoms.contains(ZZ_NS + "Ke");
        boolean daHan2 = symptoms.contains(ZZ_NS + "DaHan") || symptoms.contains(ZZ_NS + "HanChu");
        boolean maiHongDa = pulses.contains(MX_NS + "Pulse_Hong") ||
                pulses.contains(MX_NS + "Pulse_HongDa") ||
                pulses.contains(MX_NS + "Pulse_Shi");

        if (daRe && daKe && daHan2 && maiHongDa) {
            return new EmergencyResult(true, FJ_NS + "BaiHuJiaRenShenTang", "阳明热盛伤津",
                    "大热 + 大渴 + 大汗 + 脉洪大有力 → 白虎加人参汤");
        }

        // ----- 3. 亡阴检测 -----
        boolean hanChuRuYou = symptoms.contains(ZZ_NS + "HanChuRuYou");
        boolean maiXiShuWuLi = pulses.contains(MX_NS + "Pulse_Xi") && pulses.contains(MX_NS + "Pulse_Shu");
        boolean shenReZhiWen = symptoms.contains(ZZ_NS + "ShenRe") || symptoms.contains(ZZ_NS + "ZhiWen");
        boolean keYinYin = symptoms.contains(ZZ_NS + "KeYinYin") || symptoms.contains(ZZ_NS + "DaKe");
        boolean chunSheGanHong = tongues.contains(TX_NS + "CrimsonTongue") ||
                tongues.contains(TX_NS + "DryTongue") ||
                symptoms.contains(ZZ_NS + "ChunKouGanZao");

        if (hanChuRuYou && maiXiShuWuLi && shenReZhiWen && keYinYin && chunSheGanHong) {
            return new EmergencyResult(true, FJ_NS + "ZhiGanCaoTang", "亡阴",
                    "汗出如油 + 脉细数无力 + 身热肢温 + 渴引饮 + 唇舌干红 → 炙甘草汤/加减复脉汤");
        }

        // ----- 4. 阳明腑实危重检测 -----
        int fuShiCount = 0;
        if (symptoms.contains(ZZ_NS + "ChaoRe")) fuShiCount++;
        if (symptoms.contains(ZZ_NS + "ZhanYu")) fuShiCount++;
        if (symptoms.contains(ZZ_NS + "FuManYingTong") || symptoms.contains(ZZ_NS + "FuManJuAn")) fuShiCount++;

        boolean buDaBian = symptoms.contains(ZZ_NS + "BuDaBian") || symptoms.contains(ZZ_NS + "DaBianJie");
        boolean shouZuRanRanHanChu = symptoms.contains(ZZ_NS + "ShouZuRanRanHanChu") ||
                symptoms.contains(ZZ_NS + "ShouZuHanChu");

        if (fuShiCount >= 2 && (buDaBian || shouZuRanRanHanChu)) {
            return new EmergencyResult(true, FJ_NS + "DaChengQiTang", "阳明腑实危重",
                    "潮热/谵语/腹满硬痛≥2 + 不大便≥5日/手足濈然汗出≥1 → 大承气汤");
        }

        // ----- 5. 除中检测 -----
        boolean maiWeiXi = pulses.contains(MX_NS + "Pulse_Wei") || pulses.contains(MX_NS + "Pulse_Xi");
        boolean jiuBingWeiZhong = symptoms.contains(ZZ_NS + "JiuBingWeiZhong") ||
                symptoms.contains(ZZ_NS + "WeiZhong");
        boolean tuRanBaoShi = symptoms.contains(ZZ_NS + "TuRanBaoShi") ||
                symptoms.contains(ZZ_NS + "BaoShi");

        // ============================================================
        // ★ 修改六（v27.0）：除中危候 → 抛出异常，不出方
        // 依据：《伤寒论》第333条："此名除中，必死。"
        // ============================================================
        if (maiWeiXi && jiuBingWeiZhong && tuRanBaoShi) {
            log.error("【除中危候】脉微细欲绝 + 久病危重 + 突然暴食，此为死证，不可出方！");
            // 通过 BPMN 错误边界事件捕获，进入人工评估
            throw new IllegalStateException("除中危候：仲景明言'必死'，不可出方，需紧急人工评估");
        }

        return EmergencyResult.noEmergency();
    }

    // ============================================================
    // ★ 修改七（v27.0）：八纲判定支持置信度数值化
    // ============================================================

    /**
     * 判定表里并返回置信度分数
     */
    private Map<String, Double> decideBiaoLiWithConfidence(Map<IRI, List<String>> symptomEvidence,
                                                           Map<IRI, List<String>> pulseEvidence) {
        boolean hasBiao = hasBagang(symptomEvidence, BG_NS + "Biao");
        boolean hasLi = hasBagang(symptomEvidence, BG_NS + "Li");
        boolean hasBan = hasBagang(symptomEvidence, BG_NS + "BanBiaoBanLi");
        boolean pulseBiao = hasBagang(pulseEvidence, BG_NS + "Biao");
        boolean pulseLi = hasBagang(pulseEvidence, BG_NS + "Li");

        int totalEvidence = (hasBiao ? 1 : 0) + (hasLi ? 1 : 0) + (hasBan ? 1 : 0) + (pulseBiao ? 1 : 0) + (pulseLi ? 1 : 0);
        if (totalEvidence == 0) {
            return Map.of("未定", 1.0);
        }

        double biaoScore = (hasBiao ? 1.0 : 0) + (pulseBiao ? 1.0 : 0);
        double liScore = (hasLi ? 1.0 : 0) + (pulseLi ? 1.0 : 0);
        double banScore = hasBan ? 1.0 : 0;

        double total = biaoScore + liScore + banScore;
        if (total == 0) return Map.of("未定", 1.0);

        Map<String, Double> result = new LinkedHashMap<>();
        if (biaoScore > 0) result.put("表证", biaoScore / total);
        if (liScore > 0) result.put("里证", liScore / total);
        if (banScore > 0) result.put("半表半里", banScore / total);
        if (result.isEmpty()) result.put("未定", 1.0);
        return result;
    }

    /**
     * 判定寒热并返回置信度分数
     */
    private Map<String, Double> decideHanReWithConfidence(Map<IRI, List<String>> symptomEvidence,
                                                          Map<IRI, List<String>> pulseEvidence,
                                                          Map<IRI, List<String>> tongueEvidence) {
        boolean pulseHan = hasBagang(pulseEvidence, BG_NS + "Han");
        boolean pulseRe = hasBagang(pulseEvidence, BG_NS + "Re");
        boolean tongueHan = hasBagang(tongueEvidence, BG_NS + "Han");
        boolean tongueRe = hasBagang(tongueEvidence, BG_NS + "Re");
        boolean symHan = hasBagang(symptomEvidence, BG_NS + "Han");
        boolean symRe = hasBagang(symptomEvidence, BG_NS + "Re");

        double hanScore = (pulseHan ? 1.0 : 0) + (tongueHan ? 1.0 : 0) + (symHan ? 1.0 : 0);
        double reScore = (pulseRe ? 1.0 : 0) + (tongueRe ? 1.0 : 0) + (symRe ? 1.0 : 0);
        double total = hanScore + reScore;
        if (total == 0) return Map.of("未定", 1.0);

        Map<String, Double> result = new LinkedHashMap<>();
        if (hanScore > 0) result.put("寒证", hanScore / total);
        if (reScore > 0) result.put("热证", reScore / total);
        if (result.isEmpty()) result.put("未定", 1.0);
        return result;
    }

    /**
     * 判定虚实并返回置信度分数
     */
    private Map<String, Double> decideXuShiWithConfidence(Map<IRI, List<String>> symptomEvidence,
                                                          Map<IRI, List<String>> pulseEvidence,
                                                          Map<IRI, List<String>> tongueEvidence) {
        boolean pulseShi = hasBagang(pulseEvidence, BG_NS + "Shi");
        boolean pulseXu = hasBagang(pulseEvidence, BG_NS + "Xu");
        boolean tongueShi = hasBagang(tongueEvidence, BG_NS + "Shi");
        boolean tongueXu = hasBagang(tongueEvidence, BG_NS + "Xu");
        boolean symShi = hasBagang(symptomEvidence, BG_NS + "Shi");
        boolean symXu = hasBagang(symptomEvidence, BG_NS + "Xu");

        double shiScore = (pulseShi ? 1.0 : 0) + (tongueShi ? 1.0 : 0) + (symShi ? 1.0 : 0);
        double xuScore = (pulseXu ? 1.0 : 0) + (tongueXu ? 1.0 : 0) + (symXu ? 1.0 : 0);
        double total = shiScore + xuScore;
        if (total == 0) return Map.of("未定", 1.0);

        Map<String, Double> result = new LinkedHashMap<>();
        if (shiScore > 0) result.put("实证", shiScore / total);
        if (xuScore > 0) result.put("虚证", xuScore / total);
        if (result.isEmpty()) result.put("未定", 1.0);
        return result;
    }

    /**
     * 判定阴阳并返回置信度分数（含腹诊/肢温裁决）
     */
    private Map<String, Double> decideYinYangWithConfidence(Map<IRI, List<String>> symptomEvidence,
                                                            Map<IRI, List<String>> pulseEvidence,
                                                            Map<IRI, List<String>> tongueEvidence,
                                                            List<String> symptomIris) {
        Map<IRI, List<String>> merged = new LinkedHashMap<>();
        merged.putAll(symptomEvidence);
        merged.putAll(pulseEvidence);
        merged.putAll(tongueEvidence);

        int yangCount = 0;
        int yinCount = 0;
        IRI yangIri = IRI.create(BG_NS + "Yang");
        IRI yinIri = IRI.create(BG_NS + "Yin");

        for (Map.Entry<IRI, List<String>> entry : merged.entrySet()) {
            if (entry.getKey().equals(yangIri)) {
                yangCount = entry.getValue().size();
            } else if (entry.getKey().equals(yinIri)) {
                yinCount = entry.getValue().size();
            }
        }

        double total = yangCount + yinCount;
        if (total == 0) return Map.of("未定", 1.0);

        // 若一方证据数量明显多，直接判
        if (yangCount > 0 && yinCount == 0) {
            return Map.of("阳证", 1.0);
        }
        if (yinCount > 0 && yangCount == 0) {
            return Map.of("阴证", 1.0);
        }

        // 若两者都>0，且数量不等，判为“阴阳错杂”
        if (yangCount != yinCount) {
            return Map.of("阴阳错杂", 1.0);
        }

        // 若相等，则进入腹诊/肢温裁决
        log.info("阴阳证据数量相等（阳={}, 阴={}），进入腹诊/肢温裁决", yangCount, yinCount);
        Set<String> symptomSet = new HashSet<>(symptomIris);
        // 检查腹诊拒按（实证/阳） vs 喜按（虚证/阴）
        boolean hasJuAn = symptomSet.contains(ZZ_NS + "JuAn") || symptomSet.contains(ZZ_NS + "FuManJuAn");
        boolean hasAnZhiRu = symptomSet.contains(ZZ_NS + "AnZhiRu");
        // 肢温或肢冷（利用已有症状）
        boolean hasShouZuRe = symptomSet.contains(ZZ_NS + "ShouZuRe") || symptomSet.contains(ZZ_NS + "ShouZuFanRe");
        boolean hasShouZuJueLeng = symptomSet.contains(ZZ_NS + "ShouZuJueLeng") || symptomSet.contains(ZZ_NS + "SiZhiJueNi");

        if (hasJuAn || hasShouZuRe) {
            log.info("腹诊拒按或手足热，判为阳证");
            return Map.of("阳证", 1.0);
        } else if (hasAnZhiRu || hasShouZuJueLeng) {
            log.info("腹诊喜按或手足厥冷，判为阴证");
            return Map.of("阴证", 1.0);
        } else {
            log.warn("腹诊/肢温无法裁决，保留为'未定'");
            return Map.of("未定", 1.0);
        }
    }

    // ====================================================================
    //  ★ 合病/并病识别（六经兼病层）
    // ====================================================================
    // ============================================================
    // ★ 修改十一（v27.0）：合病判断阈值增加提纲证加权
    // 依据：六经提纲证（如少阳"口苦、咽干、目眩"）具有极高特异性
    // ============================================================

    /**
     * 计算六经证据强度（含提纲证加权）
     * 若存在该经提纲证，强度直接提升至 0.5，确保通过 0.3 阈值
     */
    private double calculateChannelStrengthWithTigangBonus(
            List<String> symptomIris,
            List<String> pulseIris,
            List<String> tongueIris,
            String channelName) {

        double baseStrength = calculateChannelStrength(symptomIris, pulseIris, tongueIris, channelName);

        // 若存在该经提纲证，直接提升至阈值以上
        String[] tigangSymptoms = getTigangSymptomsForChannel(channelName);
        Set<String> patientSymptoms = new HashSet<>(symptomIris);
        for (String tigang : tigangSymptoms) {
            if (patientSymptoms.contains(tigang)) {
                log.info("检测到 {} 提纲证：{}，强度提升至 0.5", channelName, tigang);
                return Math.max(baseStrength, 0.5);
            }
        }
        return baseStrength;
    }

    /**
     * 获取六经提纲证列表
     */
    private String[] getTigangSymptomsForChannel(String channelName) {
        switch (channelName) {
            case "TaiYang":
                return new String[]{ZZ_NS + "EHan", ZZ_NS + "XiangQiang"};
            case "YangMing":
                return new String[]{ZZ_NS + "ERe"};
            case "ShaoYang":
                return new String[]{ZZ_NS + "KouKu", ZZ_NS + "YanGan", ZZ_NS + "MuXuan"};
            case "TaiYin":
                return new String[]{ZZ_NS + "FuMan", ZZ_NS + "ShiBuXia"};
            case "ShaoYin":
                return new String[]{ZZ_NS + "DanYuMei"};
            case "JueYin":
                return new String[]{ZZ_NS + "XiaoKe", ZZ_NS + "QiShangZhuangXin"};
            default:
                return new String[0];
        }
    }

    /**
     * 修改 detectCombinedDiseaseWithStrength 方法，调用加权版本
     */
    private CombinedDiseaseResult detectCombinedDiseaseWithStrength(
            List<String> symptomIris,
            List<String> pulseIris,
            List<String> tongueIris,
            Map<String, Object> bagangResult) {

        // ★ 使用带提纲证加权的方法
        double taiYangStrength = calculateChannelStrengthWithTigangBonus(symptomIris, pulseIris, tongueIris, "TaiYang");
        double yangMingStrength = calculateChannelStrengthWithTigangBonus(symptomIris, pulseIris, tongueIris, "YangMing");
        double shaoYangStrength = calculateChannelStrengthWithTigangBonus(symptomIris, pulseIris, tongueIris, "ShaoYang");
        // 三阴合病判断（扩展）
        double taiYinStrength = calculateChannelStrengthWithTigangBonus(symptomIris, pulseIris, tongueIris, "TaiYin");
        double shaoYinStrength = calculateChannelStrengthWithTigangBonus(symptomIris, pulseIris, tongueIris, "ShaoYin");
        double jueYinStrength = calculateChannelStrengthWithTigangBonus(symptomIris, pulseIris, tongueIris, "JueYin");

        // 后续逻辑保持不变，但阈值已通过提纲证加权
        boolean hasTaiYang = taiYangStrength > 0.3;
        boolean hasYangMing = yangMingStrength > 0.3;
        boolean hasShaoYang = shaoYangStrength > 0.3;
        boolean hasTaiYin = taiYinStrength > 0.3;
        boolean hasShaoYin = shaoYinStrength > 0.3;
        boolean hasJueYin = jueYinStrength > 0.3;

        // 判断合病逻辑（扩展至六经全部组合）
        String biaoLi = (String) bagangResult.get("表里");
        if (biaoLi != null && (biaoLi.contains("同病") || biaoLi.contains("三经"))) {
            String mark = null;
            switch (biaoLi) {
                case "表里同病":
                    mark = "太阳阳明合病";
                    break;
                case "表半同病":
                    mark = "太阳少阳合病";
                    break;
                case "里半同病":
                    mark = "少阳阳明合病";
                    break;
                case "三经同病":
                    mark = "三阳合病";
                    break;
                default:
                    break;
            }
            if (mark != null) {
                String extra = detectBingBing(bagangResult);
                if (extra != null) {
                    mark = mark + "（" + extra + "）";
                }
                return new CombinedDiseaseResult(mark, "根据八纲组合状态判定");
            }
        }

        // ★ 新增：太少两感检测（太阳+少阴）
        if (hasTaiYang && hasShaoYin) {
            return new CombinedDiseaseResult("太少两感",
                    String.format("太阳强度=%.2f, 少阴强度=%.2f，均>0.3", taiYangStrength, shaoYinStrength));
        }

        // ★ 新增：少阳太阴合病检测
        if (hasShaoYang && hasTaiYin) {
            return new CombinedDiseaseResult("少阳太阴合病",
                    String.format("少阳强度=%.2f, 太阴强度=%.2f，均>0.3", shaoYangStrength, taiYinStrength));
        }

        // ★ 新增：阳明太阴合病检测
        if (hasYangMing && hasTaiYin) {
            return new CombinedDiseaseResult("阳明太阴合病",
                    String.format("阳明强度=%.2f, 太阴强度=%.2f，均>0.3", yangMingStrength, taiYinStrength));
        }

        // ★ 新增：少阴厥阴合病检测
        if (hasShaoYin && hasJueYin) {
            return new CombinedDiseaseResult("少阴厥阴合病",
                    String.format("少阴强度=%.2f, 厥阴强度=%.2f，均>0.3", shaoYinStrength, jueYinStrength));
        }

        // 原有三阳合病检测
        if (hasTaiYang && hasYangMing && hasShaoYang) {
            return new CombinedDiseaseResult("三阳合病",
                    String.format("太阳强度=%.2f, 阳明强度=%.2f, 少阳强度=%.2f，均>0.3",
                            taiYangStrength, yangMingStrength, shaoYangStrength));
        } else if (hasTaiYang && hasYangMing) {
            return new CombinedDiseaseResult("太阳阳明合病",
                    String.format("太阳强度=%.2f, 阳明强度=%.2f，均>0.3", taiYangStrength, yangMingStrength));
        } else if (hasTaiYang && hasShaoYang) {
            return new CombinedDiseaseResult("太阳少阳合病",
                    String.format("太阳强度=%.2f, 少阳强度=%.2f，均>0.3", taiYangStrength, shaoYangStrength));
        } else if (hasYangMing && hasShaoYang) {
            return new CombinedDiseaseResult("少阳阳明合病",
                    String.format("少阳强度=%.2f, 阳明强度=%.2f，均>0.3", shaoYangStrength, yangMingStrength));
        } else if (hasTaiYang || hasYangMing || hasShaoYang || hasTaiYin || hasShaoYin || hasJueYin) {
            return CombinedDiseaseResult.none();
        }

        return CombinedDiseaseResult.none();
    }

    private double calculateChannelStrength(
            List<String> symptomIris,
            List<String> pulseIris,
            List<String> tongueIris,
            String channelName) {

        String[] taiYangSymptoms = {
                ZZ_NS + "WuHan",
                ZZ_NS + "TouXiangQiangTong",
                ZZ_NS + "ShenTiTong",
                ZZ_NS + "FaRe"
        };
        String[] yangMingSymptoms = {
                ZZ_NS + "WeiRe",
                ZZ_NS + "FuMan",
                ZZ_NS + "BianMi",
                ZZ_NS + "ChaoRe"
        };
        String[] shaoYangSymptoms = {
                ZZ_NS + "WangLaiHanRe",
                ZZ_NS + "XiongXieKuMan",
                ZZ_NS + "KouKu",
                ZZ_NS + "YanGan",
                ZZ_NS + "MuXuan",
                ZZ_NS + "XinFanXiOu"
        };

        String[] targetSymptoms;
        switch (channelName) {
            case "TaiYang":
                targetSymptoms = taiYangSymptoms;
                break;
            case "YangMing":
                targetSymptoms = yangMingSymptoms;
                break;
            case "ShaoYang":
                targetSymptoms = shaoYangSymptoms;
                break;
            default:
                return 0.0;
        }

        if (targetSymptoms.length == 0) return 0.0;

        Set<String> patientSymptoms = new HashSet<>(symptomIris);
        int matched = 0;
        for (String sym : targetSymptoms) {
            if (patientSymptoms.contains(sym)) {
                matched++;
            }
        }
        return (double) matched / targetSymptoms.length;
    }

    private String detectBingBing(Map<String, Object> bagangResult) {
        @SuppressWarnings("unchecked")
        List<String> symptomSequence = (List<String>) bagangResult.get("symptomSequence");
        if (symptomSequence == null || symptomSequence.isEmpty()) {
            return null;
        }

        boolean hasTableFirst = false;
        boolean hasLiLater = false;
        for (String sym : symptomSequence) {
            if (sym.contains("WuHan") || sym.contains("TouXiang")) {
                hasTableFirst = true;
            }
            if (hasTableFirst && (sym.contains("FuMan") || sym.contains("BianMi") || sym.contains("XiaLi"))) {
                hasLiLater = true;
            }
        }
        if (hasTableFirst && hasLiLater) {
            return "并病：先表后里，表证未罢";
        }
        return null;
    }

    // ====================================================================
    //  ③ 六经治疗原则（表里同病时）
    // ====================================================================

    private String determineTreatmentPriority(
            String biaoLi,
            String xuShi,
            String hanRe,
            CombinedDiseaseResult combinedResult) {

        if (!"表里同病".equals(biaoLi) && !"三经同病".equals(biaoLi)) {
            return "常规治疗";
        }

        boolean isLiShi = "实证".equals(xuShi) || "虚实错杂".equals(xuShi);
        boolean isLiXu = "虚证".equals(xuShi);
        boolean isLiHan = "寒证".equals(hanRe) || "寒热错杂".equals(hanRe);

        boolean isLiWeiJi = false;
        if (combinedResult != null && combinedResult.detail != null) {
            if (combinedResult.detail.contains("里实危急") || combinedResult.detail.contains("危急")) {
                isLiWeiJi = true;
            }
        }

        if (isLiXu && "表证".equals(biaoLi) && isLiHan) {
            return "先补里后解表（表里俱虚寒）";
        }
        if (isLiXu && isLiHan) {
            return "先温里后解表（里虚寒急）";
        }
        if (isLiShi && isLiWeiJi) {
            return "先治里（急下存阴，里实危急）";
        }
        if (isLiShi && isLiWeiJi && "表证".equals(biaoLi)) {
            return "表里双解（表里俱实，里危急）";
        }
        if (isLiShi && !isLiWeiJi && "表证".equals(biaoLi)) {
            return "先解表后攻里（表里俱实，里未危急）";
        }
        return "表里同病，需综合判断";
    }

    /**
     * ★ 修改：Step 3 支持合病六经归属
     * 当 Step 2 已经识别出合病时，Step 3 直接使用合病标记作为六经结果
     */
    @JobWorker(type = "step3-liujing", autoComplete = false)
    public void handleStep3LiuJing(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            Map<String, Object> bagangResult = (Map<String, Object>) vars.get("bagangResult");
            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            @SuppressWarnings("unchecked")
            List<String> pulseIris = (List<String>) vars.get("pulseIris");
            @SuppressWarnings("unchecked")
            List<String> tongueIris = (List<String>) vars.get("tongueIris");
            String combinedDiseaseMark = (String) vars.get("combinedDiseaseMark");
            @SuppressWarnings("unchecked")
            List<String> jianjiaMarkers = (List<String>) vars.get("jianjiaMarkers");

            if (bagangResult == null) {
                throw new IllegalArgumentException("缺少 bagangResult");
            }
            if (symptomIris == null) symptomIris = Collections.emptyList();
            if (pulseIris == null) pulseIris = Collections.emptyList();
            if (tongueIris == null) tongueIris = Collections.emptyList();
            if (jianjiaMarkers == null) jianjiaMarkers = Collections.emptyList();

            String biaoLi = (String) bagangResult.get("表里");
            String hanRe = (String) bagangResult.get("寒热");
            String yinYang = (String) bagangResult.get("阴阳");

            // ★ 1. 确定 sixChannel
            String sixChannel = null;
            boolean isCombined = false;

            if (combinedDiseaseMark != null && !combinedDiseaseMark.isEmpty()) {
                sixChannel = combinedDiseaseMark;
                isCombined = true;
                log.info("检测到合病标记：{}，Step 3 直接输出合病六经状态", combinedDiseaseMark);
            } else {
                boolean isSingleLocation = "表证".equals(biaoLi) || "里证".equals(biaoLi) || "半表半里".equals(biaoLi);
                if ("未定".equals(yinYang) || !isSingleLocation) {
                    outputLiuJingResult(client, job, null, null, "六经难定（病位不单一或阴阳未定）");
                    return;
                }
                String baseChannel = mapToSixChannel(biaoLi, yinYang);
                if (baseChannel == null) {
                    outputLiuJingResult(client, job, null, null, "六经难定（无法映射）");
                    return;
                }
                sixChannel = validateSpecialRules(baseChannel, biaoLi, hanRe, yinYang,
                        symptomIris, pulseIris, tongueIris);
            }

            // ★ 2. 计算 treatmentStrategy（合病/兼夹策略）
            String treatmentStrategy = determineTreatmentStrategy(combinedDiseaseMark, jianjiaMarkers);

            // ★ 3. 输出结果（含 treatmentStrategy）
            outputLiuJingResult(client, job, sixChannel, treatmentStrategy, null);

        } catch (Exception e) {
            log.error("step3-liujing 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP3_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * ★ 新增：根据合病标记和兼夹标记确定治疗策略
     */
    private String determineTreatmentStrategy(String combinedDiseaseMark, List<String> jianjiaMarkers) {
        boolean hasCombined = combinedDiseaseMark != null && !combinedDiseaseMark.isEmpty();
        boolean hasJianjia = jianjiaMarkers != null && !jianjiaMarkers.isEmpty();

        if (hasCombined && hasJianjia) {
            return "HEFANG_JIAYAO";
        } else if (hasCombined) {
            return "HEFANG";
        } else if (hasJianjia) {
            return "JIAYAO";
        } else {
            return "CHUNFANG";
        }
    }

    /**
     * 修改输出方法，增加 treatmentStrategy 参数
     */
    private void outputLiuJingResult(final JobClient client, final ActivatedJob job,
                                     String sixChannel, String treatmentStrategy, String fallbackReason) {
        try {
            Map<String, Object> output = new LinkedHashMap<>();
            if (sixChannel != null) {
                output.put("sixChannel", sixChannel);
                output.put("jingDuration", "初诊");
                boolean isCombined = sixChannel.contains("合病") || sixChannel.contains("并病");
                output.put("isCombinedChannel", isCombined);
                if (treatmentStrategy != null) {
                    output.put("treatmentStrategy", treatmentStrategy);
                }
                log.info("step3-liujing 完成 | 六经={}, 是否为合病={}, 治疗策略={}",
                        sixChannel, isCombined, treatmentStrategy);
            } else {
                output.put("sixChannel", null);
                output.put("jingDuration", "初诊");
                output.put("liujingFallbackReason", fallbackReason != null ? fallbackReason : "六经难定");
                log.warn("step3-liujing 完成 | 六经难定，原因：{}", fallbackReason);
            }
            client.newCompleteCommand(job.getKey())
                    .variables(output)
                    .send().join();
        } catch (Exception e) {
            log.error("输出六经结果失败", e);
        }
    }

    /**
     * ★ 新增：将合病标记映射为六经字符串
     * 用于 Step 3 输出，供后续步骤使用
     */
    private String mapCombinedDiseaseToChannel(String combinedDiseaseMark) {
        if (combinedDiseaseMark == null) return null;

        // 直接使用合病标记本身作为六经标识
        // 这样后续步骤可以通过字符串判断是否为合病
        switch (combinedDiseaseMark) {
            case "太阳阳明合病":
            case "太阳少阳合病":
            case "少阳阳明合病":
            case "三阳合病":
            case "太少两感":
            case "太阳少阴合病":
            case "少阳太阴合病":
            case "阳明太阴合病":
            case "少阴厥阴合病":
                return combinedDiseaseMark;  // 直接返回合病名称
            default:
                // 如果包含 "合病" 或 "并病" 字样，直接返回
                if (combinedDiseaseMark.contains("合病") || combinedDiseaseMark.contains("并病")) {
                    return combinedDiseaseMark;
                }
                return null;
        }
    }

    private String validateSpecialRules(String baseChannel, String biaoLi, String hanRe, String yinYang,
                                        List<String> symptomIris, List<String> pulseIris,
                                        List<String> tongueIris) {
        String channelShort = getShortForm(baseChannel);

        if (isYinChannel(channelShort) && !"阴证".equals(yinYang)) {
            log.warn("三阴病底线不满足：阴阳={}，但基础六经为{}，强制返回六经难定", yinYang, channelShort);
            return null;
        }

        if ("Jueyin".equals(channelShort)) {
            boolean isBan = "半表半里".equals(biaoLi);
            boolean isHanReCuoZa = "寒热错杂".equals(hanRe);
            boolean isYin = "阴证".equals(yinYang);
            if (isBan && isHanReCuoZa && isYin) {
                return baseChannel;
            } else {
                log.warn("厥阴强制条件不满足：病位={}，寒热={}，阴阳={}，返回六经难定",
                        biaoLi, hanRe, yinYang);
                return null;
            }
        }

        if ("Shaoyang".equals(channelShort)) {
            boolean hasXianPulse = pulseIris.contains(MX_NS + "Pulse_Xian");
            boolean isBan = "半表半里".equals(biaoLi);
            int shaoYangSymptomMatch = countTypicalSymptomsMatch(
                    LJ_NS + "Shaoyang_Instance", symptomIris);
            if (hasXianPulse && isBan && shaoYangSymptomMatch >= 1) {
                return baseChannel;
            } else {
                log.warn("少阳特权限制不满足：脉弦={}，病位半={}，主症匹配数={}，返回六经难定",
                        hasXianPulse, isBan, shaoYangSymptomMatch);
                return null;
            }
        }

        return baseChannel;
    }

    private boolean isYinChannel(String shortName) {
        return "Taiyin".equals(shortName) || "Shaoyin".equals(shortName) || "Jueyin".equals(shortName);
    }

    private int countTypicalSymptomsMatch(String channelInstanceIri, List<String> symptomIris) {
        Set<String> patientSymptoms = new HashSet<>(symptomIris);
        OWLObjectProperty hasTypicalSymptom = backendService.safeGetObjectProperty(
                LJ_NS + "hasTypicalSymptom");
        if (hasTypicalSymptom == null) {
            log.warn("hasTypicalSymptom 属性未定义，无法匹配");
            return 0;
        }
        try {
            OWLNamedIndividual channel = backendService.getIndividual(channelInstanceIri);
            if (channel == null) return 0;
            Set<OWLNamedIndividual> symptoms = backendService.safeGetAllObjectPropertyValues(
                    channel, hasTypicalSymptom);
            if (symptoms == null || symptoms.isEmpty()) return 0;
            int matchCount = 0;
            for (OWLNamedIndividual sym : symptoms) {
                if (patientSymptoms.contains(sym.getIRI().toString())) {
                    matchCount++;
                }
            }
            return matchCount;
        } catch (Exception e) {
            log.error("查询六经典型症状失败", e);
            return 0;
        }
    }

    private String getShortForm(String iri) {
        if (iri == null) return "";
        int lastHash = iri.lastIndexOf('#');
        int lastSlash = iri.lastIndexOf('/');
        int idx = Math.max(lastHash, lastSlash);
        return idx >= 0 ? iri.substring(idx + 1) : iri;
    }

    // ====================================================================
    //  Step 4: 方证匹配（统一处理合病与兼夹叠加） + 完整推理日志
    // ====================================================================
    @JobWorker(type = "step4-fangzheng-match", autoComplete = false)
    public void handleStep4FangZhengMatch(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            @SuppressWarnings("unchecked")
            List<String> pulseIris = (List<String>) vars.get("pulseIris");
            @SuppressWarnings("unchecked")
            List<String> tongueIris = (List<String>) vars.get("tongueIris");
            String sixChannel = (String) vars.get("sixChannel");
            String combinedDiseaseMark = (String) vars.get("combinedDiseaseMark");
            @SuppressWarnings("unchecked")
            List<String> jianjiaMarkers = (List<String>) vars.get("jianjiaMarkers");
            @SuppressWarnings("unchecked")
            Map<String, Object> bagangResult = (Map<String, Object>) vars.get("bagangResult");

            if (symptomIris == null) symptomIris = Collections.emptyList();
            if (pulseIris == null) pulseIris = Collections.emptyList();
            if (tongueIris == null) tongueIris = Collections.emptyList();
            if (jianjiaMarkers == null) jianjiaMarkers = Collections.emptyList();
            if (bagangResult == null) bagangResult = new LinkedHashMap<>();

            log.info("========== Step4 开始 ==========");
            log.info("患者症状数: {}, 脉象数: {}, 舌象数: {}", symptomIris.size(), pulseIris.size(), tongueIris.size());
            log.info("六经: {}, 合病标记: {}, 兼夹标记: {}", sixChannel, combinedDiseaseMark, jianjiaMarkers);

            List<OWLNamedIndividual> allPatterns = getPrescribablePatterns();
            if (allPatterns.isEmpty()) {
                throw new IllegalStateException("未找到任何可处方病证个体");
            }
            log.info("候选病证总数: {}", allPatterns.size());

            List<PatternMatch> matches = new ArrayList<>();
            for (OWLNamedIndividual pattern : allPatterns) {
                String patternLabel = backendService.resolveLabel(pattern.getIRI().toString());
                log.debug("  评估病证: {}", patternLabel);
                PatternMatch match = evaluatePattern(pattern, symptomIris, pulseIris, tongueIris,
                        sixChannel, combinedDiseaseMark, jianjiaMarkers, bagangResult);
                if (match != null) {
                    matches.add(match);
                    log.debug("    通过，得分: {}", String.format("%.3f", match.getScore()));
                } else {
                    log.debug("    淘汰");
                }
            }

            String topFormula = null;
            List<String> candidateFormulas = new ArrayList<>();
            String heFangSuggestion = null;
            String modificationSuggestion = "";
            List<String> rejectionRecord = new ArrayList<>();
            String clause = null;
            String bestPatternIri = null;
            String matchStatus = "NO_MATCH"; // 默认

            PatternMatch bestMatch = null;

            // ★ 三级降级策略（合病优先处理）
            if (combinedDiseaseMark != null && !combinedDiseaseMark.isEmpty()) {
                log.info("合病标记存在: {}", combinedDiseaseMark);
                List<String> targetChannels = extractChannelsFromMark(combinedDiseaseMark);

                // 第一级：寻找双归属病证（仲景合方）
                List<PatternMatch> combinedMatches = matches.stream()
                        .filter(pm -> {
                            OWLNamedIndividual pattern = backendService.getIndividual(pm.getPatternIri());
                            if (pattern == null) return false;
                            Set<OWLNamedIndividual> channels = getBelongsToLiuJing(pattern);
                            if (channels == null || channels.isEmpty()) return false;
                            Set<String> channelNames = channels.stream()
                                    .map(ch -> getShortForm(ch.getIRI().toString()))
                                    .collect(Collectors.toSet());
                            return targetChannels.stream().allMatch(channelNames::contains);
                        })
                        .collect(Collectors.toList());

                if (!combinedMatches.isEmpty()) {
                    combinedMatches.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                    bestMatch = combinedMatches.get(0);
                    log.info("合病优先选择双归属病证: {}, 得分: {}",
                            backendService.resolveLabel(bestMatch.getPatternIri()),
                            String.format("%.3f", bestMatch.getScore()));
                } else {
                    log.warn("无符合合病双归属的方证，降级至普通匹配最高分");
                }
            }

            // 第二级：普通匹配最高分（若第一级未选中）
            if (bestMatch == null && !matches.isEmpty()) {
                matches.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                bestMatch = matches.get(0);
                log.info("选择普通匹配最高分: {}, 得分: {}",
                        backendService.resolveLabel(bestMatch.getPatternIri()),
                        String.format("%.3f", bestMatch.getScore()));
            }

            // ★★★ 第三级：兜底策略（关键修正：不直接结束，而是输出参考方） ★★★
            if (bestMatch == null) {
                log.warn("无任何匹配，进入兜底策略");

                // 优先尝试合方底方兜底（如果有合病标记）
                if (combinedDiseaseMark != null && !combinedDiseaseMark.isEmpty()) {
                    String baseFormula = selectHeFangFromOntology(
                            combinedDiseaseMark, bagangResult,
                            symptomIris, pulseIris, tongueIris);
                    if (baseFormula != null) {
                        topFormula = baseFormula;
                        heFangSuggestion = combinedDiseaseMark + " → " + getFormulaLabel(baseFormula);
                        candidateFormulas.add(baseFormula);
                        matchStatus = "PARTIAL_MATCH";
                        log.info("合方底方作为参考方: {}", topFormula);
                    } else {
                        // 合病无合方，使用通用参考方
                        log.warn("合病无合方兜底，使用通用参考方");
                        topFormula = FJ_NS + "GuiZhiTang";
                        candidateFormulas.add(topFormula);
                        heFangSuggestion = "合病无对应仲景合方，仅供参考";
                        matchStatus = "PARTIAL_MATCH";
                        log.info("通用参考方作为兜底: {}", topFormula);
                    }
                } else {
                    // ★★★ 无合病标记时：使用通用参考方（桂枝汤），不是 NO_MATCH ★★★
                    log.warn("无合病标记且无匹配，使用通用参考方（桂枝汤）");
                    topFormula = FJ_NS + "GuiZhiTang";
                    candidateFormulas.add(topFormula);
                    heFangSuggestion = "辨证依据不足，仅供参考（建议医师重新审查）";
                    matchStatus = "PARTIAL_MATCH";
                    log.info("通用参考方作为兜底: {}", topFormula);
                }

                // 兜底方剂也走兼夹加药和禁忌校验，但不设置最佳病证信息
                // 记录兜底原因
                rejectionRecord.add("无匹配病证，使用参考方");
                modificationSuggestion = "建议医师重新审查辨证";

                // ★ 继续执行后续的兼夹加药和特异性加减（不 return） ★
                // 注意：此时 bestPatternIri 为 null，跳过特异性加减

            } else {
                // 有最佳匹配
                bestPatternIri = bestMatch.getPatternIri();
                List<String> formulaIris = findFormulasForPattern(bestPatternIri);
                if (!formulaIris.isEmpty()) {
                    topFormula = formulaIris.get(0);
                    candidateFormulas.addAll(formulaIris);
                }
                clause = getClauseForPattern(bestPatternIri);
                rejectionRecord.addAll(bestMatch.getRejections());
                matchStatus = "MATCHED";
                log.info("最佳匹配病证: {}, 得分: {}, 对应方剂: {}",
                        backendService.resolveLabel(bestPatternIri),
                        String.format("%.3f", bestMatch.getScore()),
                        getFormulaLabel(topFormula));
            }

            // ★ 处理兼夹加药和特异性加减（无论是否兜底，都执行） ★
            if (topFormula != null) {
                // 兼夹加药
                String jianjiaSuggestion = processJianjia(jianjiaMarkers, topFormula, symptomIris);
                if (jianjiaSuggestion != null && !jianjiaSuggestion.isEmpty()) {
                    if (!modificationSuggestion.isEmpty() && !modificationSuggestion.equals("建议医师重新审查辨证")) {
                        modificationSuggestion += "；" + jianjiaSuggestion;
                    } else {
                        modificationSuggestion = jianjiaSuggestion;
                    }
                }
                // 特异性加减（仅当有最佳病证时）
                if (bestPatternIri != null) {
                    OWLNamedIndividual pattern = backendService.getIndividual(bestPatternIri);
                    if (pattern != null) {
                        String specSuggestion = buildSpecificModificationSuggestion(pattern, symptomIris);
                        if (specSuggestion != null && !specSuggestion.isEmpty()) {
                            if (!modificationSuggestion.isEmpty() && !modificationSuggestion.equals("建议医师重新审查辨证")) {
                                modificationSuggestion += "；" + specSuggestion;
                            } else {
                                modificationSuggestion = specSuggestion;
                            }
                        }
                    }
                }
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("top1Formula", topFormula);
            output.put("candidateFormulas", candidateFormulas);
            output.put("heFangSuggestion", heFangSuggestion != null ? heFangSuggestion : "");
            output.put("modificationSuggestion", modificationSuggestion);
            output.put("clauseNumber", clause != null ? clause : "");
            output.put("rejectionRecord", rejectionRecord);
            output.put("matchStatus", matchStatus);

            client.newCompleteCommand(job.getKey())
                    .variables(output)
                    .send().join();

            log.info("step4-fangzheng-match 完成 | top1={}, 加减建议='{}', matchStatus={}",
                    topFormula, modificationSuggestion, matchStatus);

        } catch (Exception e) {
            log.error("step4-fangzheng-match 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP4_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 舌象语义层级映射（本体驱动应从 skos:broader 读取，此处为性能缓存）
     * key=具体舌象, value=其父级/等价舌象集合
     * 例如：CrimsonTongue 是 RedTongue 的加重版，DeepRedTongue 是 CrimsonTongue 的加重版
     */
    private static final Map<String, Set<String>> TONGUE_SEMANTIC_PARENTS = Map.of(
            TX_NS + "CrimsonTongue", Set.of(TX_NS + "RedTongue"),
            TX_NS + "DeepRedTongue", Set.of(TX_NS + "CrimsonTongue", TX_NS + "RedTongue"),
            TX_NS + "YellowDryCoating", Set.of(TX_NS + "YellowCoating"),
            TX_NS + "WhiteSlipperyCoating", Set.of(TX_NS + "WhiteCoating"),
            TX_NS + "CurdyCoating", Set.of(TX_NS + "WhiteCoating")  // 腐苔属于白苔类
    );

    /**
     * 判断患者舌象是否与方证典型舌象语义匹配
     * 支持精确匹配 + 语义层级匹配（子→父方向）
     */
    private boolean isTongueSemanticallyMatched(String patientTongue, String patternTongue) {
        // 精确匹配
        if (patientTongue.equals(patternTongue)) return true;
        // 语义层级：患者舌象的父级包含方证舌象
        Set<String> parents = TONGUE_SEMANTIC_PARENTS.get(patientTongue);
        if (parents != null && parents.contains(patternTongue)) return true;
        // 反向：方证舌象的父级包含患者舌象（方证写的是泛化舌象，患者是具体舌象）
        Set<String> patternParents = TONGUE_SEMANTIC_PARENTS.get(patternTongue);
        if (patternParents != null && patternParents.contains(patientTongue)) return true;
        return false;
    }

    private void sendNoMatch(JobClient client, ActivatedJob job) {
        try {
            Map<String, Object> fallbackOutput = new LinkedHashMap<>();
            fallbackOutput.put("top1Formula", null);
            fallbackOutput.put("candidateFormulas", Collections.emptyList());
            fallbackOutput.put("heFangSuggestion", "");
            fallbackOutput.put("modificationSuggestion", "辨证失败，请人工介入");
            fallbackOutput.put("clauseNumber", "");
            fallbackOutput.put("rejectionRecord", Collections.singletonList("无匹配病证，建议人工介入辨证"));
            fallbackOutput.put("matchStatus", "NO_MATCH");
            client.newCompleteCommand(job.getKey())
                    .variables(fallbackOutput)
                    .send().join();
            log.info("step4-fangzheng-match 完成 | 状态=NO_MATCH");
        } catch (Exception e) {
            log.error("发送NO_MATCH失败", e);
        }
    }

    /**
     * 合病兜底方剂选择（本体驱动，零硬编码）
     *
     * 仅在 Step4 evaluatePattern 未匹配到任何方证时调用。
     * 从本体中查找同时属于合病标记所涉及各经的 prescribable 方证，
     * 按主证匹配度排序取最高分者。
     *
     * @param combinedDiseaseMark 合病标记，如"太阳阳明合病"
     * @param bagangResult        八纲结果（用于八纲一致性过滤）
     * @param symptomIris         患者症状（用于主证匹配评分）
     * @param pulseIris           患者脉象
     * @param tongueIris          患者舌象
     * @return 最佳兜底方剂 IRI，若无则返回 null
     */
    private String selectHeFangFromOntology(String combinedDiseaseMark,
                                            Map<String, Object> bagangResult,
                                            List<String> symptomIris,
                                            List<String> pulseIris,
                                            List<String> tongueIris) {
        if (combinedDiseaseMark == null || combinedDiseaseMark.isEmpty()) {
            return null;
        }

        // 1. 从合病标记解析出目标六经英文名集合
        List<String> targetChannels = extractChannelsFromMark(combinedDiseaseMark);
        if (targetChannels.isEmpty()) {
            log.warn("合病标记 '{}' 无法解析出六经，无法进行本体兜底查询", combinedDiseaseMark);
            return null;
        }
        Set<String> normalizedTargets = targetChannels.stream()
                .map(this::normalizeChannelName)
                .collect(Collectors.toSet());
        log.info("合病兜底本体查询：目标六经={}", normalizedTargets);

        // 2. 遍历所有 prescribable 方证，找出六经归属完全覆盖目标集合的
        List<OWLNamedIndividual> allPatterns = getPrescribablePatterns();
        List<CandidateFormula> candidates = new ArrayList<>();

        Set<String> patientAll = new HashSet<>();
        if (symptomIris != null) patientAll.addAll(symptomIris);
        if (pulseIris != null) patientAll.addAll(pulseIris);
        if (tongueIris != null) patientAll.addAll(tongueIris);

        for (OWLNamedIndividual pattern : allPatterns) {
            Set<OWLNamedIndividual> channels = getBelongsToLiuJing(pattern);
            if (channels.size() < normalizedTargets.size()) {
                continue; // 方证归属经数少于目标经数，不可能是合病方证
            }

            Set<String> patternChannelNames = channels.stream()
                    .map(ch -> normalizeChannelName(getShortForm(ch.getIRI().toString())))
                    .collect(Collectors.toSet());

            // 方证六经集合必须包含所有目标六经
            if (!patternChannelNames.containsAll(normalizedTargets)) {
                continue;
            }

            // 八纲一致性过滤
            if (!checkBagangConsistency(pattern, bagangResult)) {
                continue;
            }

            // 主证完整性检查（兜底时放宽：至少命中1项主证即可）
            Set<String> mainSymptoms = getRequiredSymptomIrisInherited(pattern);
            int mainHit = 0;
            for (String s : mainSymptoms) {
                if (patientAll.contains(s)) mainHit++;
            }
            if (!mainSymptoms.isEmpty() && mainHit == 0) {
                continue; // 主证0命中，即使兜底也不选
            }

            // 计算辅助匹配度用于排序
            double auxScore = calculateAuxiliaryMatchScore(pattern, symptomIris, pulseIris, tongueIris);
            double bonus = calculateEvidenceBonus(pattern, symptomIris, pulseIris, tongueIris);
            double totalScore = auxScore + bonus;

            // 获取对应方剂
            List<String> formulas = findFormulasForPattern(pattern.getIRI().toString());
            if (formulas.isEmpty()) continue;

            CandidateFormula cf = new CandidateFormula();
            cf.formulaIri = formulas.get(0);
            cf.patternLabel = backendService.resolveLabel(pattern.getIRI().toString());
            cf.score = totalScore;
            cf.mainHit = mainHit;
            cf.mainTotal = mainSymptoms.size();
            candidates.add(cf);
        }

        if (candidates.isEmpty()) {
            log.warn("合病兜底本体查询：未找到属于 {} 的任何方证", normalizedTargets);
            return null;
        }

        // 按分数降序排序
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        CandidateFormula best = candidates.get(0);

        log.info("合病兜底本体查询结果：选择 {} (方证={}, 得分={}, 主证命中={}/{})",
                backendService.resolveLabel(best.formulaIri),
                best.patternLabel,
                String.format("%.3f", best.score),
                best.mainHit, best.mainTotal);

        return best.formulaIri;
    }

    /**
     * 内部类：合病兜底候选方剂
     */
    private static class CandidateFormula {
        String formulaIri;
        String patternLabel;
        double score;
        int mainHit;
        int mainTotal;
    }

    // ====================================================================
    //  主证检查方法（新增）
    // ====================================================================

    /**
     * 检查主证是否全部出现（AND 逻辑）
     * @param pattern 方证个体
     * @param patientAll 患者全部症状、脉象、舌象的集合
     * @return true 表示所有主证都存在，false 表示缺失主证
     */
    private boolean checkPrimarySymptomsComplete(OWLNamedIndividual pattern, Set<String> patientAll) {
        Set<String> primarySymptoms = getRequiredSymptomIrisInherited(pattern);
        if (primarySymptoms.isEmpty()) {
            log.debug("淘汰：方证 {} 无主症定义", backendService.resolveLabel(pattern.getIRI().toString()));
            return false;   // ★ 关键改动：无主症则淘汰
        }
        for (String sym : primarySymptoms) {
            if (!patientAll.contains(sym)) {
                log.debug("淘汰：主症缺失 - {}", backendService.resolveLabel(sym));
                return false;
            }
        }
        return true;
    }

    /**
     * 计算辅助特征（或然症、脉象、舌象）的匹配度，用于排序
     */
    /**
     * 计算辅助特征（或然症、脉象、舌象）的匹配度
     * 无辅助特征时得分归零，避免空壳证型获得中间分
     */
    private double calculateAuxiliaryMatchScore(OWLNamedIndividual pattern,
                                                List<String> symptomIris,
                                                List<String> pulseIris,
                                                List<String> tongueIris) {
        Set<String> patientSymptoms = new HashSet<>(symptomIris);
        Set<String> patientPulses = new HashSet<>(pulseIris);
        Set<String> patientTongues = new HashSet<>(tongueIris);

        Set<String> orSymptoms = getOrSymptomIrisInherited(pattern);
        Set<String> requiredPulses = getRequiredPulseIrisInherited(pattern);
        Set<String> requiredTongues = getRequiredTongueIrisInherited(pattern);

        int total = orSymptoms.size() + requiredPulses.size() + requiredTongues.size();
        if (total == 0) {
            return 0.0;   // ★ 关键改动：不再返回 0.5
        }

        int matched = 0;
        for (String sym : orSymptoms) {
            if (patientSymptoms.contains(sym)) matched++;
        }
        for (String pulse : requiredPulses) {
            if (patientPulses.contains(pulse)) matched++;
        }
        for (String tongue : requiredTongues) {
            boolean tongueMatched = false;
            for (String pt : patientTongues) {
                if (isTongueSemanticallyMatched(pt, tongue)) {
                    tongueMatched = true;
                    break;
                }
            }
            if (tongueMatched) matched++;
        }

        return (double) matched / total;
    }

    // ====================================================================
    //  读取或然症（含继承）
    // ====================================================================

    private Set<String> getOrSymptomIrisInherited(OWLNamedIndividual pattern) {
        Set<String> result = new HashSet<>();
        collectOrSymptomsRecursive(pattern, result);
        return result;
    }

    private void collectOrSymptomsRecursive(OWLNamedIndividual pattern, Set<String> acc) {
        OWLObjectProperty orProp = backendService.safeGetObjectProperty(BZ_NS + "has_or_symptom");
        if (orProp != null) {
            Set<OWLNamedIndividual> orSyms = backendService.safeGetAllObjectPropertyValues(pattern, orProp);
            if (orSyms != null) {
                orSyms.forEach(s -> acc.add(s.getIRI().toString()));
            }
        }
        // 继承变体的或然症（如果有 variantOf）
        OWLObjectProperty variantProp = backendService.safeGetObjectProperty(VARIANT_OF);
        Set<OWLNamedIndividual> parents = backendService.safeGetAllObjectPropertyValues(pattern, variantProp);
        if (parents != null) {
            for (OWLNamedIndividual parent : parents) {
                collectOrSymptomsRecursive(parent, acc);
            }
        }
    }

    // ====================================================================
    //  方证匹配评估（主证驱动）
    // ====================================================================

    private PatternMatch evaluatePattern(OWLNamedIndividual pattern,
                                         List<String> symptomIris,
                                         List<String> pulseIris,
                                         List<String> tongueIris,
                                         String patientSixChannel,
                                         String combinedDiseaseMark,
                                         List<String> jianjiaMarkers,
                                         Map<String, Object> bagangResult) {
        String patternLabel = backendService.resolveLabel(pattern.getIRI().toString());
        List<String> rejections = new ArrayList<>();

        log.debug("    检查病证: {}", patternLabel);

        // 1. 六经一致性（统一使用 checkSixChannelConsistency，支持合病）
        if (!checkSixChannelConsistency(pattern, patientSixChannel)) {
            log.debug("      ❌ 淘汰：六经不一致 | 方证={} 患者六经={}", patternLabel, patientSixChannel);
            return null;
        }
        log.debug("      ✅ 六经一致");

        // 2. 八纲一致性
        if (!checkBagangConsistency(pattern, bagangResult)) {
            log.debug("      ❌ 淘汰：八纲不一致 | 方证={}", patternLabel);
            return null;
        }
        log.debug("      ✅ 八纲一致");

        // 3. 主证检查（必须全部满足）
        Set<String> patientAll = new HashSet<>();
        patientAll.addAll(symptomIris);
        patientAll.addAll(pulseIris);
        patientAll.addAll(tongueIris);

        if (!checkPrimarySymptomsComplete(pattern, patientAll)) {
            log.debug("      ❌ 淘汰：主证不完整 | 方证={}", patternLabel);
            return null;
        }
        log.debug("      ✅ 主证完整");

        // 4. 否决症状检查
        if (hasExclusionSymptom(pattern, symptomIris, pulseIris, tongueIris)) {
            log.debug("      ❌ 淘汰：存在否决症状 | 方证={}", patternLabel);
            return null;
        }
        log.debug("      ✅ 无否决症状");

        // 5. 合病标记匹配（如果有合病标记，方证必须属于合病范围内的经）
        if (combinedDiseaseMark != null && !combinedDiseaseMark.isEmpty()) {
            if (!matchesCombinedDisease(pattern, combinedDiseaseMark)) {
                log.debug("      ❌ 淘汰：不合合病标记 {} | 方证={}", combinedDiseaseMark, patternLabel);
                return null;
            }
            log.debug("      ✅ 合病标记匹配");
        }

        // 6. 计算辅助匹配度 + 证据加分
        double auxScore = calculateAuxiliaryMatchScore(pattern, symptomIris, pulseIris, tongueIris);
        double bonus = calculateEvidenceBonus(pattern, symptomIris, pulseIris, tongueIris);
        double totalScore = auxScore + bonus;

        log.debug("      ✅ 通过 | 辅助匹配度={}, 证据加分={}, 总分={}",
                String.format("%.2f", auxScore), String.format("%.2f", bonus), String.format("%.3f", totalScore));

        String detail = buildDetail(pattern, auxScore, bonus);
        return new PatternMatch(pattern.getIRI().toString(), totalScore, detail, rejections);
    }

    // ====================================================================
    //  原有工具方法（未修改）
    // ====================================================================

    private String getFormulaLabel(String formulaIri) {
        if (formulaIri == null) return null;
        return backendService.resolveLabel(formulaIri);
    }

    /**
     * Step1 专用：基于症状集合匹配最佳高置信度证型（双向匹配 F1-score）
     *
     * 计算逻辑：
     *   recall  = 患者命中主症数 / 证型主症总数（症状对证型的覆盖度）
     *   precision = 患者命中主症数 / 患者症状中属于该证型主症集合的数量（证型对症状的解释力）
     *   实际简化：precision = 命中主症数 / 患者全部症状数（因为主症是症状子集，可粗略评估证型解释力）
     *   但更精确：precision = 命中主症数 / (命中主症数 + 患者有而证型无的症状数)
     *   我们采用标准的 F1 = 2 * (precision * recall) / (precision + recall)
     *
     * 同时考虑主症数量：如果主症数量极少（如1个），recall 虽高但 precision 通常低，F1自然被拉低。
     */
    /**
     * Step1 专用：基于症状集合匹配最佳高置信度证型（双向匹配 F1-score）
     * 增加调试日志：打印所有候选病证的匹配详情
     */
    /**
     * Step1 专用：基于症状集合匹配最佳高置信度证型（召回率驱动）
     * 符合经方“但见一证便是”原则，以主症覆盖度为核心
     */
    private PatternMatch matchBestPatternForStep1(List<String> symptomIris,
                                                  List<String> pulseIris,
                                                  List<String> tongueIris) {
        List<OWLNamedIndividual> allPatterns = getPrescribablePatterns();
        if (allPatterns.isEmpty()) {
            log.info("【调试】无可处方的病证，匹配结束");
            return null;
        }

        Set<String> patientAll = new HashSet<>();
        patientAll.addAll(symptomIris);
        patientAll.addAll(pulseIris);
        patientAll.addAll(tongueIris);

        log.info("【调试】患者症状数: {}, 脉象数: {}, 舌象数: {}",
                symptomIris.size(), pulseIris.size(), tongueIris.size());
        log.info("【调试】开始遍历 {} 个可处方的病证", allPatterns.size());

        PatternMatch best = null;
        double bestRecall = -1;
        int bestMainSize = -1;

        for (OWLNamedIndividual pattern : allPatterns) {
            String patternLabel = backendService.resolveLabel(pattern.getIRI().toString());
            Set<String> mainSymptoms = getRequiredSymptomIrisInherited(pattern);
            int totalMain = mainSymptoms.size();

            // 无主症或主症少于2个（避免单症证型干扰）则跳过
            if (mainSymptoms.isEmpty() || totalMain < 2) {
                log.info("【调试】病证: {} → 主症不足（{}个），跳过", patternLabel, totalMain);
                continue;
            }

            // 计算命中数
            int hit = 0;
            for (String sym : mainSymptoms) {
                if (patientAll.contains(sym)) hit++;
            }

            double recall = (double) hit / totalMain;

            // 检查否决症状
            boolean hasExclusion = hasExclusionSymptom(pattern, symptomIris, pulseIris, tongueIris);

            log.info("【调试】病证: {} | 主症数: {} | 命中: {} | 召回: {:.2f} | 否决: {}",
                    patternLabel, totalMain, hit, recall, hasExclusion);

            // 淘汰触发否决者
            if (hasExclusion) {
                continue;
            }

            // 高置信度条件：召回率 >= 0.8
            if (recall >= 0.8) {
                // 择优：先比召回率，再比主症数（主症多者特异性更强）
                if (recall > bestRecall || (recall == bestRecall && totalMain > bestMainSize)) {
                    bestRecall = recall;
                    bestMainSize = totalMain;
                    best = new PatternMatch(
                            pattern.getIRI().toString(),
                            recall,   // 直接使用召回率作为得分
                            String.format("召回率=%.2f (命中%d/%d)", recall, hit, totalMain),
                            new ArrayList<>()
                    );
                }
            }
        }

        if (best != null) {
            log.info("【调试】最终选定高置信度证型: {}，召回率={:.2f}",
                    backendService.resolveLabel(best.getPatternIri()), best.getScore());
        } else {
            log.info("【调试】未找到召回率>=0.8的高置信度证型");
        }

        return best;
    }

    private List<OWLNamedIndividual> getPrescribablePatterns() {
        Set<OWLNamedIndividual> all = backendService.getIndividuals(TCM_NS + "DiseasePattern");
        List<OWLNamedIndividual> prescribable = new ArrayList<>();
        for (OWLNamedIndividual ind : all) {
            try {
                OWLDataProperty roleProp = backendService.getDataProperty(PATTERN_ROLE);
                Set<OWLLiteral> roles = backendService.getDataPropertyValueOfIndividual(ind, roleProp);
                if (roles != null && !roles.isEmpty()) {
                    String role = roles.iterator().next().getLiteral();
                    if ("prescribable".equals(role)) {
                        prescribable.add(ind);
                    }
                    continue;
                }
            } catch (Exception e) {
                // ignore
            }
            if (!isDeprecated(ind)) {
                prescribable.add(ind);
            }
        }
        return prescribable;
    }

    private boolean isDeprecated(OWLNamedIndividual ind) {
        try {
            Set<OWLLiteral> dep = backendService.getAnnotationValue(ind, "http://www.w3.org/2002/07/owl#deprecated");
            return dep != null && dep.stream().anyMatch(l -> "true".equals(l.getLiteral()));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ★ 修改：六经一致性检查（支持合病）
     */
    /**
     * ★ 六经一致性检查（修复版）
     *
     * 原逻辑：URI精确字符串比较 → 合病标记与URI永远不匹配
     * 修复后：
     *   - 单经：患者六经URI ∈ 方证六经集合
     *   - 合病：方证六经集合 ⊆ 患者合病解析出的六经集合
     */
    private boolean checkSixChannelConsistency(OWLNamedIndividual pattern, String patientSixChannel) {
        if (patientSixChannel == null) return true;

        Set<OWLNamedIndividual> patternChannels = getBelongsToLiuJing(pattern);
        if (patternChannels.isEmpty()) return true;

        // 方证六经英文名集合（去掉 _Instance 后缀，统一首字母大写）
        Set<String> patternEnglishNames = patternChannels.stream()
                .map(ch -> normalizeChannelName(getShortForm(ch.getIRI().toString())))
                .collect(Collectors.toSet());

        boolean isCombined = patientSixChannel.contains("合病") || patientSixChannel.contains("并病")

                || patientSixChannel.contains("两感");

        if (isCombined) {
            // 合病模式：方证所有六经都必须在患者合病范围内
            Set<String> patientEnglishNames = extractChannelsFromMark(patientSixChannel).stream()
                    .map(this::normalizeChannelName)
                    .collect(Collectors.toSet());
            if (patientEnglishNames.isEmpty()) {
                log.debug("合病标记 '{}' 无法解析出六经，放行", patientSixChannel);
                return true;
            }
            boolean allMatch = patternEnglishNames.stream().allMatch(patientEnglishNames::contains);
            if (!allMatch) {
                log.debug("合病不匹配: 方证{} ⊄ 患者{}", patternEnglishNames, patientEnglishNames);
            }
            return allMatch;
        } else {
            // 单经模式
            String patientShortName = normalizeChannelName(getShortForm(patientSixChannel));
            return patternEnglishNames.contains(patientShortName);
        }
    }

    /**
     * 标准化六经名称：去掉 _Instance 后缀，确保首字母大写其余小写
     */
    private String normalizeChannelName(String name) {
        if (name == null) return "";
        if (name.endsWith("_Instance")) {
            name = name.substring(0, name.length() - 9);
        }
        // 统一为 "Taiyang", "Yangming" 等格式
        if (name.length() > 1) {
            return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
        }
        return name;
    }

    /**
     * ★ 修改：将英文六经名称映射为中文
     */
    private String mapEnglishToChinese(String english) {
        if (english == null) return null;
        // 去掉可能的 "_Instance" 后缀
        if (english.endsWith("_Instance")) {
            english = english.substring(0, english.length() - 9);
        }
        switch (english) {
            case "Taiyang": return "太阳";
            case "Yangming": return "阳明";
            case "Shaoyang": return "少阳";
            case "Taiyin": return "太阴";
            case "Shaoyin": return "少阴";
            case "Jueyin": return "厥阴";
            default: return null;
        }
    }

    /**
     * 从合病标记中提取六经英文名称列表
     * 例如："太阳阳明合病" → ["Taiyang", "Yangming"]
     *       "太少两感" → ["Taiyang", "Shaoyin"]
     *       "三阳合病" → ["Taiyang", "Yangming", "Shaoyang"]
     */
    private List<String> extractChannelsFromMark(String combinedDiseaseMark) {
        List<String> channels = new ArrayList<>();
        if (combinedDiseaseMark == null || combinedDiseaseMark.isEmpty()) return channels;

        // 特殊合病标记
        if ("三阳合病".equals(combinedDiseaseMark)) {
            return Arrays.asList("Taiyang", "Yangming", "Shaoyang");
        }
        if ("太少两感".equals(combinedDiseaseMark)) {
            return Arrays.asList("Taiyang", "Shaoyin");
        }

        // 通用解析
        Map<String, String> nameMap = Map.of(
                "太阳", "Taiyang", "阳明", "Yangming",
                "少阳", "Shaoyang", "太阴", "Taiyin",
                "少阴", "Shaoyin", "厥阴", "Jueyin"
        );
        for (Map.Entry<String, String> entry : nameMap.entrySet()) {
            if (combinedDiseaseMark.contains(entry.getKey())) {
                channels.add(entry.getValue());
            }
        }
        return channels;
    }

    private Set<String> getRequiredSymptomIrisInherited(OWLNamedIndividual pattern) {
        Set<String> result = new HashSet<>();
        collectSymptomsRecursive(pattern, result);
        return result;
    }

    private void collectSymptomsRecursive(OWLNamedIndividual pattern, Set<String> acc) {
        OWLObjectProperty hasSymptom = backendService.safeGetObjectProperty(HAS_SYMPTOM);
        Set<OWLNamedIndividual> syms = backendService.safeGetAllObjectPropertyValues(pattern, hasSymptom);
        if (syms != null) {
            syms.forEach(s -> acc.add(s.getIRI().toString()));
        }
        OWLObjectProperty variantProp = backendService.safeGetObjectProperty(VARIANT_OF);
        Set<OWLNamedIndividual> parents = backendService.safeGetAllObjectPropertyValues(pattern, variantProp);
        if (parents != null) {
            for (OWLNamedIndividual parent : parents) {
                collectSymptomsRecursive(parent, acc);
            }
        }
    }

    private Set<String> getRequiredPulseIrisInherited(OWLNamedIndividual pattern) {
        Set<String> result = new HashSet<>();
        collectPulsesRecursive(pattern, result);
        return result;
    }

    private void collectPulsesRecursive(OWLNamedIndividual pattern, Set<String> acc) {
        OWLObjectProperty hasPulse = backendService.safeGetObjectProperty(HAS_PULSE);
        Set<OWLNamedIndividual> pulses = backendService.safeGetAllObjectPropertyValues(pattern, hasPulse);
        if (pulses != null) {
            pulses.forEach(p -> acc.add(p.getIRI().toString()));
        }
        OWLObjectProperty variantProp = backendService.safeGetObjectProperty(VARIANT_OF);
        Set<OWLNamedIndividual> parents = backendService.safeGetAllObjectPropertyValues(pattern, variantProp);
        if (parents != null) {
            for (OWLNamedIndividual parent : parents) {
                collectPulsesRecursive(parent, acc);
            }
        }
    }

    private Set<String> getRequiredTongueIrisInherited(OWLNamedIndividual pattern) {
        Set<String> result = new HashSet<>();
        collectTonguesRecursive(pattern, result);
        return result;
    }

    private void collectTonguesRecursive(OWLNamedIndividual pattern, Set<String> acc) {
        OWLObjectProperty hasTongue = backendService.safeGetObjectProperty(BZ_NS + "has_typical_tongue");
        Set<OWLNamedIndividual> tongues = backendService.safeGetAllObjectPropertyValues(pattern, hasTongue);
        if (tongues != null) {
            tongues.forEach(t -> acc.add(t.getIRI().toString()));
        }
        OWLObjectProperty variantProp = backendService.safeGetObjectProperty(VARIANT_OF);
        Set<OWLNamedIndividual> parents = backendService.safeGetAllObjectPropertyValues(pattern, variantProp);
        if (parents != null) {
            for (OWLNamedIndividual parent : parents) {
                collectTonguesRecursive(parent, acc);
            }
        }
    }

    private boolean hasExclusionSymptom(OWLNamedIndividual pattern,
                                        List<String> symptomIris,
                                        List<String> pulseIris,
                                        List<String> tongueIris) {
        OWLObjectProperty exclusionProp = backendService.safeGetObjectProperty(HAS_EXCLUSION_SYMPTOM);
        if (exclusionProp == null) return false;
        Set<OWLNamedIndividual> exclSymptoms = backendService.safeGetAllObjectPropertyValues(pattern, exclusionProp);
        if (exclSymptoms == null) return false;
        Set<String> patientAll = new HashSet<>();
        patientAll.addAll(symptomIris);
        patientAll.addAll(pulseIris);
        patientAll.addAll(tongueIris);
        for (OWLNamedIndividual sym : exclSymptoms) {
            if (patientAll.contains(sym.getIRI().toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkBagangConsistency(OWLNamedIndividual pattern, Map<String, Object> bagangResult) {
        Set<OWLNamedIndividual> patternBagang = getBagangElements(pattern);
        if (patternBagang.isEmpty()) return true;

        String patientBiaoLi = (String) bagangResult.get("表里");
        String patientHanRe = (String) bagangResult.get("寒热");
        String patientXuShi = (String) bagangResult.get("虚实");
        String patientYinYang = (String) bagangResult.get("阴阳");

        boolean hasBiao = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("Biao"));
        boolean hasLi = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("Li"));
        boolean hasBan = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("BanBiaoBanLi"));

        if (hasBiao && !"表证".equals(patientBiaoLi) && !"表里同病".equals(patientBiaoLi) && !"表半同病".equals(patientBiaoLi)) {
            return false;
        }
        if (hasLi && !"里证".equals(patientBiaoLi) && !"表里同病".equals(patientBiaoLi) && !"里半同病".equals(patientBiaoLi)) {
            return false;
        }
        if (hasBan && !"半表半里".equals(patientBiaoLi) && !"表半同病".equals(patientBiaoLi) && !"里半同病".equals(patientBiaoLi)) {
            return false;
        }

        boolean hasHan = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("Han"));
        boolean hasRe = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("Re"));
        if (hasHan && "热证".equals(patientHanRe)) return false;
        if (hasRe && "寒证".equals(patientHanRe)) return false;

        boolean hasXu = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("Xu"));
        boolean hasShi = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("Shi"));
        if (hasXu && "实证".equals(patientXuShi)) return false;
        if (hasShi && "虚证".equals(patientXuShi)) return false;

        boolean hasYang = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("Yang"));
        boolean hasYin = patternBagang.stream().anyMatch(bg -> bg.getIRI().toString().contains("Yin"));
        if (hasYang && "阴证".equals(patientYinYang)) return false;
        if (hasYin && "阳证".equals(patientYinYang)) return false;

        return true;
    }

    private boolean matchesCombinedDisease(OWLNamedIndividual pattern, String combinedDiseaseMark) {
        Set<OWLNamedIndividual> channels = getBelongsToLiuJing(pattern);
        if (channels.isEmpty()) return false;

        // 方证六经英文名
        Set<String> patternEnglishNames = channels.stream()
                .map(ch -> {
                    String shortName = getShortForm(ch.getIRI().toString());
                    return shortName.endsWith("_Instance") ?
                            shortName.substring(0, shortName.length() - 9) : shortName;
                })
                .collect(Collectors.toSet());

        // 患者合病六经英文名
        List<String> combinedEnglishNames = extractChannelsFromMark(combinedDiseaseMark);

        // 方证的每一经都必须在合病范围内
        return combinedEnglishNames.stream().allMatch(patternEnglishNames::contains);
    }

    private double calculateEvidenceBonus(OWLNamedIndividual pattern,
                                          List<String> symptomIris,
                                          List<String> pulseIris,
                                          List<String> tongueIris) {
        double bonus = 0.0;
        OWLDataProperty evidenceProp = backendService.safeGetDataProperty(EVIDENCE_LEVEL);
        if (evidenceProp == null) return 0.0;

        Set<String> allPatient = new HashSet<>();
        allPatient.addAll(symptomIris);
        allPatient.addAll(pulseIris);
        allPatient.addAll(tongueIris);

        Set<String> requiredSymptoms = getRequiredSymptomIrisInherited(pattern);
        for (String symIri : requiredSymptoms) {
            if (!allPatient.contains(symIri)) continue;
            OWLNamedIndividual symptom = backendService.getIndividual(symIri);
            if (symptom == null) continue;
            try {
                Set<OWLLiteral> levels = backendService.getDataPropertyValueOfIndividual(symptom, evidenceProp);
                if (levels != null && !levels.isEmpty()) {
                    String level = levels.iterator().next().getLiteral();
                    if ("高特异铁证".equals(level)) bonus += 0.2;
                    else if ("中特异铁证".equals(level)) bonus += 0.1;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return Math.min(bonus, 0.4);
    }

    private String buildDetail(OWLNamedIndividual pattern, double score, double bonus) {
        return String.format("辅助匹配度: %.2f, 证据加分: %.2f", score - bonus, bonus);
    }

    private String getClauseForPattern(String patternIri) {
        OWLNamedIndividual pattern = backendService.getIndividual(patternIri);
        if (pattern == null) return null;
        try {
            OWLDataProperty clauseProp = backendService.getDataProperty(TCM_NS + "clause_number");
            Set<OWLLiteral> clauses = backendService.getDataPropertyValueOfIndividual(pattern, clauseProp);
            if (clauses != null && !clauses.isEmpty()) {
                return clauses.iterator().next().getLiteral();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private Set<OWLNamedIndividual> getBelongsToLiuJing(OWLNamedIndividual pattern) {
        OWLObjectProperty belongs = backendService.safeGetObjectProperty(BELONGS_TO_LIUJING);
        Set<OWLNamedIndividual> channels = backendService.safeGetAllObjectPropertyValues(pattern, belongs);
        return channels == null ? Collections.emptySet() : channels;
    }

    private Set<OWLNamedIndividual> getBagangElements(OWLNamedIndividual pattern) {
        OWLObjectProperty bagangProp = backendService.safeGetObjectProperty(HAS_BAGANG_ELEMENT);
        Set<OWLNamedIndividual> bagang = backendService.safeGetAllObjectPropertyValues(pattern, bagangProp);
        return bagang == null ? Collections.emptySet() : bagang;
    }

    private List<String> findFormulasForPattern(String patternIri) {
        OWLObjectProperty indicatedProp = backendService.safeGetObjectProperty(INDICATED_FOR);
        if (indicatedProp == null) return Collections.emptyList();
        OWLNamedIndividual pattern = backendService.getIndividual(patternIri);
        if (pattern == null) return Collections.emptyList();
        Set<OWLNamedIndividual> formulas = backendService.safeGetAllObjectPropertyValues(pattern, indicatedProp);
        return formulas.stream().map(f -> f.getIRI().toString()).collect(Collectors.toList());
    }

    // ====================================================================
    //  兼夹处理相关方法
    // ====================================================================

    private String processJianjia(List<String> jianjiaMarkers, String baseFormulaIri, List<String> symptomIris) {
        if (jianjiaMarkers == null || jianjiaMarkers.isEmpty()) return null;

        List<String> suggestions = new ArrayList<>();
        Set<String> markers = new HashSet<>(jianjiaMarkers);
        Set<String> baseFormulaHerbs = getFormulaHerbIris(baseFormulaIri);

        for (String marker : markers) {
            int markerSymptomCount = countMarkerSymptoms(marker, symptomIris);
            boolean isSevere = markerSymptomCount >= 2;

            if (marker.contains("WaterRetention")) {
                if (isSevere) {
                    suggestions.add("水饮重证，建议合方（五苓散类）");
                } else {
                    if (!baseFormulaHerbs.contains("http://www.tcm-classics.org/yaowu#FuLing")) {
                        suggestions.add("加茯苓、白术");
                    } else {
                        suggestions.add("加桂枝以化气行水");
                    }
                }
            } else if (marker.contains("BloodStasis")) {
                if (isSevere) {
                    suggestions.add("瘀血重证，建议合方（桂枝茯苓丸、桃核承气汤类）");
                } else {
                    if (!baseFormulaHerbs.contains("http://www.tcm-classics.org/yaowu#TaoRen")) {
                        suggestions.add("加桃仁、红花");
                    } else {
                        suggestions.add("加桂枝、芍药以化瘀通络");
                    }
                }
            } else if (marker.contains("FoodStagnation")) {
                if (isSevere) {
                    suggestions.add("食积重证，建议合方（承气汤类）");
                } else {
                    suggestions.add("加厚朴、枳实");
                }
            } else if (marker.contains("QiStagnation")) {
                if (isSevere) {
                    suggestions.add("气滞重证，建议合方（四逆散、半夏厚朴汤类）");
                } else {
                    suggestions.add("加柴胡、枳壳");
                }
            }
        }

        return String.join("；", suggestions);
    }

    private Set<String> getFormulaHerbIris(String formulaIri) {
        if (formulaIri == null) return Collections.emptySet();
        Set<String> herbs = new HashSet<>();
        try {
            OWLNamedIndividual formula = backendService.getIndividual(formulaIri);
            if (formula == null) return herbs;
            OWLObjectProperty hasIngredientUse = backendService.safeGetObjectProperty(FJ_NS + "has_ingredient_use");
            Set<OWLNamedIndividual> ingredientUses = backendService.safeGetAllObjectPropertyValues(formula, hasIngredientUse);
            if (ingredientUses != null) {
                OWLObjectProperty usesHerb = backendService.safeGetObjectProperty(FJ_NS + "uses_herb");
                for (OWLNamedIndividual iu : ingredientUses) {
                    Set<OWLNamedIndividual> herbSet = backendService.safeGetAllObjectPropertyValues(iu, usesHerb);
                    if (herbSet != null) {
                        herbSet.forEach(h -> herbs.add(h.getIRI().toString()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取方剂药物失败: {}", e.getMessage());
        }
        return herbs;
    }

    private int countMarkerSymptoms(String markerIri, List<String> symptomIris) {
        if (symptomIris == null || symptomIris.isEmpty()) return 0;
        Set<String> patientSymptoms = new HashSet<>(symptomIris);
        int count = 0;
        try {
            OWLNamedIndividual marker = backendService.getIndividual(markerIri);
            if (marker == null) return 0;
            OWLObjectProperty identProp = backendService.safeGetObjectProperty(JJ_NS + "hasIdentificationSymptom");
            Set<OWLNamedIndividual> identSymptoms = backendService.safeGetAllObjectPropertyValues(marker, identProp);
            if (identSymptoms != null) {
                for (OWLNamedIndividual sym : identSymptoms) {
                    if (patientSymptoms.contains(sym.getIRI().toString())) {
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("统计兼夹症状失败: {}", e.getMessage());
        }
        return count;
    }

    // ============================================================
    // ★ 修改十二（v27.0）：ModificationRule 支持 AND/OR 触发模式
    // 依据：本体的 bz:triggerMode 属性（"ALL" 或 "ANY"）
    // ============================================================

    private String buildSpecificModificationSuggestion(OWLNamedIndividual pattern, List<String> symptomIris) {
        Set<String> patientSymptoms = new HashSet<>(symptomIris);
        OWLObjectProperty modProp = backendService.safeGetObjectProperty(HAS_MODIFICATION_RULE);
        if (modProp == null) return null;
        Set<OWLNamedIndividual> rules = backendService.safeGetAllObjectPropertyValues(pattern, modProp);
        if (rules == null || rules.isEmpty()) return null;

        List<String> suggestions = new ArrayList<>();
        for (OWLNamedIndividual rule : rules) {
            // ★ 读取 triggerMode（新增数据属性）
            String triggerMode = "ALL"; // 默认 ALL（AND 逻辑）
            OWLDataProperty modeProp = backendService.safeGetDataProperty(BZ_NS + "triggerMode");
            if (modeProp != null) {
                try {
                    Set<OWLLiteral> modes = backendService.getDataPropertyValueOfIndividual(rule, modeProp);
                    if (modes != null && !modes.isEmpty()) {
                        triggerMode = modes.iterator().next().getLiteral();
                    }
                } catch (Exception e) {
                    log.debug("读取 triggerMode 失败，使用默认值 ALL");
                }
            }

            OWLObjectProperty triggerProp = backendService.safeGetObjectProperty(TRIGGER_SYMPTOM);
            Set<OWLNamedIndividual> triggers = backendService.safeGetAllObjectPropertyValues(rule, triggerProp);
            if (triggers == null || triggers.isEmpty()) continue;

            // ★ 根据 triggerMode 判断是否触发
            boolean triggered;
            if ("ANY".equals(triggerMode)) {
                // OR 逻辑：任一触发症状存在即可
                triggered = triggers.stream().anyMatch(t -> patientSymptoms.contains(t.getIRI().toString()));
                if (triggered) {
                    String triggerLabels = triggers.stream()
                            .map(t -> backendService.resolveLabel(t.getIRI().toString()))
                            .collect(Collectors.joining(" 或 "));
                    log.debug("OR 规则触发：症状[{}] 至少一项存在", triggerLabels);
                }
            } else {
                // ALL 逻辑（默认）：所有触发症状必须全部存在
                triggered = triggers.stream().allMatch(t -> patientSymptoms.contains(t.getIRI().toString()));
                if (triggered) {
                    String triggerLabels = triggers.stream()
                            .map(t -> backendService.resolveLabel(t.getIRI().toString()))
                            .collect(Collectors.joining("、"));
                    log.debug("AND 规则触发：症状[{}] 全部存在", triggerLabels);
                }
            }
            if (!triggered) continue;

            OWLDataProperty actionProp = backendService.safeGetDataProperty(ACTION_DESCRIPTION);
            if (actionProp == null) continue;
            try {
                Set<OWLLiteral> actions = backendService.getDataPropertyValueOfIndividual(rule, actionProp);
                if (actions != null && !actions.isEmpty()) {
                    String action = actions.iterator().next().getLiteral();
                    String triggerLabels = triggers.stream()
                            .map(t -> backendService.resolveLabel(t.getIRI().toString()))
                            .collect(Collectors.joining("、"));
                    log.info("加减规则触发: 模式={}, 症状[{}] → {}", triggerMode, triggerLabels, action);
                    suggestions.add(action);
                }
            } catch (Exception e) {
                log.warn("读取加减规则描述失败", e);
            }
        }
        if (suggestions.isEmpty()) return null;
        return "针对症状加减：" + String.join("；", suggestions);
    }

    // ====================================================================
    //  内部类 PatternMatch
    // ====================================================================
    private static class PatternMatch {
        private final String patternIri;
        private final double score;
        private final String detail;
        private final List<String> rejections;

        public PatternMatch(String patternIri, double score, String detail, List<String> rejections) {
            this.patternIri = patternIri;
            this.score = score;
            this.detail = detail;
            this.rejections = rejections;
        }

        public String getPatternIri() { return patternIri; }
        public double getScore() { return score; }
        public String getDetail() { return detail; }
        public List<String> getRejections() { return rejections; }
    }

    // ====================================================================
    //  原有的 handleFormulaMatch 保留（但新流程不再使用）
    // ====================================================================
    @JobWorker(type = "task-hefang", autoComplete = false)
    public void handleHeFang(final ActivatedJob job, final JobClient client) {
        handleFormulaMatch(job, client, "hefang");
    }

    @JobWorker(type = "task-jiayao", autoComplete = false)
    public void handleJiaYao(final ActivatedJob job, final JobClient client) {
        handleFormulaMatch(job, client, "jiayao");
    }

    @JobWorker(type = "task-singleformula", autoComplete = false)
    public void handleSingleFormula(final ActivatedJob job, final JobClient client) {
        handleFormulaMatch(job, client, "single");
    }

    private void handleFormulaMatch(final ActivatedJob job, final JobClient client, String mode) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            @SuppressWarnings("unchecked")
            List<String> pulseIris = (List<String>) vars.get("pulseIris");
            @SuppressWarnings("unchecked")
            List<String> tongueIris = (List<String>) vars.get("tongueIris");
            String sixChannel = (String) vars.get("sixChannel");
            String combinedDiseaseMark = (String) vars.get("combinedDiseaseMark");
            @SuppressWarnings("unchecked")
            List<String> jianjiaMarkers = (List<String>) vars.get("jianjiaMarkers");

            if (symptomIris == null) symptomIris = Collections.emptyList();
            if (pulseIris == null) pulseIris = Collections.emptyList();
            if (tongueIris == null) tongueIris = Collections.emptyList();

            List<String> candidateFormulas = new ArrayList<>();
            String top1Formula = null;
            String suggestion = null;

            if (mode.equals("hefang")) {
                if ("太阳少阳合病".equals(combinedDiseaseMark)) {
                    top1Formula = FJ_NS + "ChaiHuGuiZhiTang";
                    suggestion = "柴胡桂枝汤（太阳少阳合病）";
                } else if ("少阳阳明合病".equals(combinedDiseaseMark) || "三阳合病".equals(combinedDiseaseMark)) {
                    top1Formula = FJ_NS + "DaChaiHuTang";
                    suggestion = "大柴胡汤（少阳阳明合病）";
                } else {
                    top1Formula = FJ_NS + "XiaoChaiHuTang";
                    suggestion = "小柴胡汤（合病基础方）";
                }
                candidateFormulas.add(top1Formula);
            } else if (mode.equals("jiayao")) {
                top1Formula = FJ_NS + "GuiZhiTang";
                StringBuilder sb = new StringBuilder("桂枝汤");
                Set<String> jianjiaSet = (jianjiaMarkers != null) ? new HashSet<>(jianjiaMarkers) : Collections.emptySet();
                if (jianjiaSet.contains(JJ_NS + "WaterRetention_Instance")) sb.append(" + 茯苓、白术");
                if (jianjiaSet.contains(JJ_NS + "BloodStasis_Instance")) sb.append(" + 桃仁、红花");
                suggestion = sb.toString();
                candidateFormulas.add(top1Formula);
            } else {
                top1Formula = FJ_NS + "GuiZhiTang";
                suggestion = "桂枝汤证";
                candidateFormulas.add(top1Formula);
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("candidateFormulas", candidateFormulas);
            output.put("top1Formula", top1Formula);
            if (mode.equals("hefang")) {
                output.put("heFangSuggestion", suggestion);
            } else if (mode.equals("jiayao")) {
                output.put("modificationSuggestion", suggestion);
            }

            client.newCompleteCommand(job.getKey())
                    .variables(output)
                    .send().join();

            log.info("{} 完成 | top1={}", mode, top1Formula);

        } catch (Exception e) {
            log.error("{} 失败", mode, e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("MATCH_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  Step 5: 禁忌校验 + 煎服法 + 传变预警 + 处方输出（完整实现）
    // ====================================================================
    @JobWorker(type = "step5-contraindication", autoComplete = false)
    public void handleStep5Contraindication(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> candidateFormulas = (List<String>) vars.get("candidateFormulas");
            String top1Formula = (String) vars.get("top1Formula");
            String sixChannel = (String) vars.get("sixChannel");
            String jingDuration = (String) vars.get("jingDuration");
            String emergencyFormula = (String) vars.get("emergencyFormula");
            String combinedDiseaseMark = (String) vars.get("combinedDiseaseMark");
            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            @SuppressWarnings("unchecked")
            List<String> pulseIris = (List<String>) vars.get("pulseIris");
            @SuppressWarnings("unchecked")
            List<String> tongueIris = (List<String>) vars.get("tongueIris");
            @SuppressWarnings("unchecked")
            Map<String, Object> bagangResult = (Map<String, Object>) vars.get("bagangResult");

            if (symptomIris == null) symptomIris = Collections.emptyList();
            if (pulseIris == null) pulseIris = Collections.emptyList();
            if (tongueIris == null) tongueIris = Collections.emptyList();
            if (bagangResult == null) bagangResult = new LinkedHashMap<>();

            String finalFormula = emergencyFormula != null ? emergencyFormula : top1Formula;

            // ========== ① 禁忌校验（分级收集） ==========
            // criticalWarnings：真正的高风险禁忌（配伍禁忌、毒性药物、方剂禁忌证候）
            // advisoryWarnings：参考性提示（六经治疗原则、一般注意事项）
            List<String> criticalWarnings = new ArrayList<>();
            List<String> advisoryWarnings = new ArrayList<>();

            // ---------- ①-a 六经禁忌（参考性提示） ----------
            if (sixChannel != null) {
                boolean isCombined = sixChannel.contains("合病") || sixChannel.contains("并病")

                        || sixChannel.contains("两感");

                if (isCombined) {
                    // 合病：从 combinedDiseaseMark 中提取各经名称，分别查询禁忌
                    String markToUse = (combinedDiseaseMark != null && !combinedDiseaseMark.isEmpty())
                            ? combinedDiseaseMark
                            : sixChannel;
                    List<String> channelNames = extractChannelsFromMark(markToUse);
                    for (String chName : channelNames) {
                        String channelIri = LJ_NS + chName + "_Instance";
                        OWLNamedIndividual channelInd = backendService.getIndividual(channelIri);
                        if (channelInd != null) {
                            OWLDataProperty contraProp = backendService.safeGetDataProperty(LJ_NS + "hasContraindication");
                            if (contraProp != null) {
                                Set<OWLLiteral> contraValues = backendService.getDataPropertyValueOfIndividual(channelInd, contraProp);
                                if (contraValues != null) {
                                    for (OWLLiteral lit : contraValues) {
                                        // ★ 六经治疗原则 → 参考性提示，非高风险
                                        advisoryWarnings.add(mapEnglishToChinese(chName) + "经治疗注意：" + lit.getLiteral());
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 单一六经
                    OWLNamedIndividual sixChannelInd = backendService.getIndividual(sixChannel);
                    if (sixChannelInd != null) {
                        OWLDataProperty contraProp = backendService.safeGetDataProperty(LJ_NS + "hasContraindication");
                        if (contraProp != null) {
                            Set<OWLLiteral> contraValues = backendService.getDataPropertyValueOfIndividual(sixChannelInd, contraProp);
                            if (contraValues != null) {
                                for (OWLLiteral lit : contraValues) {
                                    // ★ 六经治疗原则 → 参考性提示，非高风险
                                    advisoryWarnings.add("六经治疗注意：" + lit.getLiteral());
                                }
                            }
                        }
                    }
                }
            }

            // ---------- ①-b 方剂禁忌证候（高风险） ----------
            if (finalFormula != null) {
                OWLNamedIndividual formulaInd = backendService.getIndividual(finalFormula);
                if (formulaInd != null) {
                    OWLObjectProperty contraForProp = backendService.safeGetObjectProperty(FJ_NS + "contraindicated_for");
                    if (contraForProp != null) {
                        Set<OWLNamedIndividual> contraPatterns = backendService.safeGetAllObjectPropertyValues(formulaInd, contraForProp);
                        if (contraPatterns != null) {
                            for (OWLNamedIndividual pat : contraPatterns) {
                                String patLabel = backendService.resolveLabel(pat.getIRI().toString());
                                // ★ 方剂禁忌证候 → 高风险（需检查患者是否真的存在该证候）
                                // 先检查患者是否真的匹配该禁忌证候
                                boolean patientHasContraPattern = checkPatientMatchesPattern(pat, symptomIris, pulseIris, tongueIris);
                                if (patientHasContraPattern) {
                                    criticalWarnings.add("【方剂禁忌】" + getFormulaLabel(finalFormula)
                                            + " 禁忌证候：" + patLabel + "（患者疑似存在此证候）");
                                } else {
                                    // 患者不存在该禁忌证候，仅作提示
                                    advisoryWarnings.add("方剂注意：" + getFormulaLabel(finalFormula)
                                            + " 禁忌证候为" + patLabel + "（当前患者未表现此证候）");
                                }
                            }
                        }
                    }
                }
            }

            // ---------- ①-c 十八反十九畏（高风险） ----------
            if (finalFormula != null) {
                Set<String> herbIris = getFormulaHerbIris(finalFormula);
                if (!herbIris.isEmpty()) {
                    List<String> herbConflictWarnings = checkHerbConflicts(herbIris);
                    for (String w : herbConflictWarnings) {
                        // ★ 配伍禁忌 → 高风险
                        criticalWarnings.add("【配伍禁忌】" + w);
                    }
                }
            }

            // ---------- ①-d 有毒药物安全控制（高风险） ----------
            if (finalFormula != null) {
                Set<String> herbIris = getFormulaHerbIris(finalFormula);
                OWLDataProperty categoryProp = backendService.safeGetDataProperty(YW_NS + "shennong_category");
                if (categoryProp != null) {
                    for (String herbIri : herbIris) {
                        OWLNamedIndividual herb = backendService.getIndividual(herbIri);
                        if (herb != null) {
                            try {
                                Set<OWLLiteral> catVals = backendService.getDataPropertyValueOfIndividual(herb, categoryProp);
                                if (catVals != null && !catVals.isEmpty()) {
                                    String category = catVals.iterator().next().getLiteral();
                                    if ("下品".equals(category)) {
                                        // ★ 下品药物 → 高风险
                                        criticalWarnings.add("【毒性药物】方中含"
                                                + backendService.resolveLabel(herbIri)
                                                + "（本经下品），需严格控制剂量");
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("读取药物品级失败: {}", herbIri);
                            }
                        }
                    }
                }
            }

            // ========== ② 判定安全等级 ==========
            boolean hasCriticalContraindication = !criticalWarnings.isEmpty();

            String safetyLevel;
            if (hasCriticalContraindication) {
                safetyLevel = "高风险";
            } else if (!advisoryWarnings.isEmpty()) {
                safetyLevel = "需注意";
            } else {
                safetyLevel = "安全";
            }

            // ========== ③ 传变预警 ==========
            List<String> transmissionWarnings = detectTransmissionWarnings(
                    sixChannel, jingDuration, symptomIris, pulseIris, tongueIris, bagangResult);

            // ========== ④ 干预结果 ==========
            String interventionResult;
            if (hasCriticalContraindication) {
                interventionResult = "存在高风险禁忌，建议调整方剂，需医师确认";
            } else if (!advisoryWarnings.isEmpty()) {
                interventionResult = "有参考性注意事项，需医师复核";
            } else {
                interventionResult = "无需调整";
            }

            // ========== ⑤ 煎服法 + 饮食起居禁忌 + 停药条件 ==========
            DecoctionInfo decoctionInfo = getDecoctionInfo(finalFormula);
            String decoctionMethod = decoctionInfo.decoctionMethod;
            String administrationMethod = decoctionInfo.administrationMethod;
            String dietaryContraindications = decoctionInfo.dietaryContraindications;
            String stopCondition = decoctionInfo.stopCondition;

            if (decoctionMethod == null || decoctionMethod.isEmpty()) {
                String category = getFormulaCategory(finalFormula);
                if (category != null) {
                    if (category.contains("承气汤")) {
                        decoctionMethod = "急煎，大黄后下，芒硝冲服";
                    } else if (category.contains("四逆汤") || category.contains("附子")) {
                        decoctionMethod = "附子先煎半小时，余药同煎";
                    } else {
                        decoctionMethod = "水煎服，日一剂，分两次温服（具体以医师指导为准）";
                    }
                } else {
                    decoctionMethod = "水煎服，日一剂，分两次温服（具体剂量及煎法请遵医嘱）";
                }
            }
            if (administrationMethod == null || administrationMethod.isEmpty()) {
                administrationMethod = "饭前服";
            }
            if (stopCondition == null || stopCondition.isEmpty()) {
                stopCondition = "症状消失或出现不良反应时停用";
            }

            // ========== ⑥ 处方证据链 ==========
            Map<String, Object> evidenceChain = buildPrescriptionEvidenceChain(
                    finalFormula, sixChannel, candidateFormulas, symptomIris, pulseIris, tongueIris);

            // ========== ⑦ 合并所有警告（用于输出展示） ==========
            List<String> allWarnings = new ArrayList<>();
            allWarnings.addAll(criticalWarnings);
            allWarnings.addAll(advisoryWarnings);

            // ========== 构建输出 ==========
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("finalFormula", finalFormula);
            output.put("safetyLevel", safetyLevel);
            output.put("contraindicationWarnings", allWarnings);
            output.put("criticalWarnings", criticalWarnings);
            output.put("advisoryWarnings", advisoryWarnings);
            output.put("transmissionWarnings", transmissionWarnings);
            output.put("interventionResult", interventionResult);
            output.put("decoctionMethod", decoctionMethod);
            output.put("administrationMethod", administrationMethod);
            output.put("dietaryContraindications", dietaryContraindications);
            output.put("stopCondition", stopCondition);
            output.put("prescriptionEvidenceChain", evidenceChain);

            client.newCompleteCommand(job.getKey())
                    .variables(output)
                    .send().join();

            log.info("step5-contraindication 完成 | final={}, 安全等级={}, 高风险禁忌数={}, 参考提示数={}, 总警告数={}",
                    finalFormula, safetyLevel, criticalWarnings.size(), advisoryWarnings.size(), allWarnings.size());
            String criticalDetails = criticalWarnings.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            String advisoryDetails = advisoryWarnings.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            log.info("高风险禁忌详情: [{}]", criticalDetails);
            log.info("参考提示详情: [{}]", advisoryDetails);

        } catch (Exception e) {
            log.error("step5-contraindication 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP5_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * ★ 新增：检查患者是否真的匹配某个禁忌证候
     * 用于判断方剂禁忌证候是否对当前患者构成实际风险
     */
    private boolean checkPatientMatchesPattern(OWLNamedIndividual contraPattern,
                                               List<String> symptomIris,
                                               List<String> pulseIris,
                                               List<String> tongueIris) {
        Set<String> patientAll = new HashSet<>();
        patientAll.addAll(symptomIris);
        patientAll.addAll(pulseIris);
        patientAll.addAll(tongueIris);

        // 获取禁忌证候的主证
        Set<String> contraSymptoms = getRequiredSymptomIrisInherited(contraPattern);
        if (contraSymptoms.isEmpty()) {
            return false; // 无主证定义，无法判断
        }

        // 至少命中一半以上主证才认为患者存在该禁忌证候
        int hit = 0;
        for (String s : contraSymptoms) {
            if (patientAll.contains(s)) hit++;
        }
        return hit >= Math.ceil(contraSymptoms.size() / 2.0);
    }

    // ---------- 辅助方法 ----------

    /**
     * 检查药物十八反十九畏（基于本体关系）
     */
    private List<String> checkHerbConflicts(Set<String> herbIris) {
        List<String> warnings = new ArrayList<>();
        if (herbIris.isEmpty()) return warnings;

        OWLObjectProperty antagonisticProp = backendService.safeGetObjectProperty(YW_NS + "is_antagonistic_to");
        OWLObjectProperty fearingProp = backendService.safeGetObjectProperty(YW_NS + "nineteen_fear_contraindicated_with");
        if (antagonisticProp == null && fearingProp == null) {
            log.warn("十八反/十九畏属性未定义，无法进行配伍禁忌检查");
            return warnings;
        }

        List<String> herbList = new ArrayList<>(herbIris);
        for (int i = 0; i < herbList.size(); i++) {
            String herbA = herbList.get(i);
            OWLNamedIndividual indA = backendService.getIndividual(herbA);
            if (indA == null) continue;

            Set<OWLNamedIndividual> antagonistic = (antagonisticProp != null) ?
                    backendService.safeGetAllObjectPropertyValues(indA, antagonisticProp) : Collections.emptySet();
            Set<OWLNamedIndividual> fearing = (fearingProp != null) ?
                    backendService.safeGetAllObjectPropertyValues(indA, fearingProp) : Collections.emptySet();

            for (int j = i + 1; j < herbList.size(); j++) {
                String herbB = herbList.get(j);
                OWLNamedIndividual indB = backendService.getIndividual(herbB);
                if (indB == null) continue;

                if (antagonistic != null && antagonistic.contains(indB)) {
                    warnings.add("十八反：" + backendService.resolveLabel(herbA) + " 与 " + backendService.resolveLabel(herbB) + " 配伍禁忌");
                }
                if (fearing != null && fearing.contains(indB)) {
                    warnings.add("十九畏：" + backendService.resolveLabel(herbA) + " 与 " + backendService.resolveLabel(herbB) + " 配伍禁忌");
                }
            }
        }
        return warnings;
    }

    /**
     * 获取方剂类别标签（用于智能煎服法）
     */
    private String getFormulaCategory(String formulaIri) {
        if (formulaIri == null) return null;
        try {
            OWLNamedIndividual formula = backendService.getIndividual(formulaIri);
            if (formula == null) return null;
            OWLObjectProperty catProp = backendService.safeGetObjectProperty(FJ_NS + "belongs_to_formula_category");
            Set<OWLNamedIndividual> cats = backendService.safeGetAllObjectPropertyValues(formula, catProp);
            if (cats != null && !cats.isEmpty()) {
                OWLNamedIndividual cat = cats.iterator().next();
                return backendService.resolveLabel(cat.getIRI().toString());
            }
        } catch (Exception e) {
            log.warn("获取方剂类别失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检测传变预警（正向+反向证据）
     */
    private List<String> detectTransmissionWarnings(
            String sixChannel, String jingDuration,
            List<String> symptomIris, List<String> pulseIris, List<String> tongueIris,
            Map<String, Object> bagangResult) {

        List<String> warnings = new ArrayList<>();
        if (sixChannel == null || jingDuration == null) return warnings;

        int days = 0;
        if (jingDuration.contains("第") && jingDuration.contains("日")) {
            try {
                days = Integer.parseInt(jingDuration.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                days = 0;
            }
        }

        Set<String> symptoms = new HashSet<>(symptomIris);
        Set<String> pulses = new HashSet<>(pulseIris);
        Set<String> tongues = new HashSet<>(tongueIris);

        // ----- 太阳→阳明传变 -----
        if (sixChannel.contains("Taiyang")) {
            boolean isDay3OrMore = days >= 3;
            boolean hasKouKeJiaZhong = symptoms.contains(ZZ_NS + "DaKe") || symptoms.contains(ZZ_NS + "Ke");
            boolean hasEHanZhuanERe = symptoms.contains(ZZ_NS + "ERe") && !symptoms.contains(ZZ_NS + "EHan");
            boolean hasReverse = tongues.contains(TX_NS + "WhiteCoating") ||
                    pulses.contains(MX_NS + "Pulse_Chi") ||
                    symptoms.contains(ZZ_NS + "XiaoBianQingChang");

            if (isDay3OrMore && (hasKouKeJiaZhong || hasEHanZhuanERe) && !hasReverse) {
                warnings.add("太阳→阳明：病程≥3日，口渴加重或恶寒转恶热，注意阳明热化");
            }
        }

        // ----- 太阳→少阳传变 -----
        if (sixChannel.contains("Taiyang")) {
            boolean isDay5OrMore = days >= 5;
            boolean hasKouKu = symptoms.contains(ZZ_NS + "KouKu");
            boolean hasXiongXieMan = symptoms.contains(ZZ_NS + "XiongXieKuMan");
            boolean hasWangLaiHanRe = symptoms.contains(ZZ_NS + "WangLaiHanRe");
            boolean hasReverse = !pulses.contains(MX_NS + "Pulse_Xian") &&
                    !symptoms.contains(ZZ_NS + "WangLaiHanRe") &&
                    !symptoms.contains(ZZ_NS + "XiongXieKuMan");

            if (isDay5OrMore && (hasKouKu || hasXiongXieMan || hasWangLaiHanRe) && !hasReverse) {
                warnings.add("太阳→少阳：病程≥5日，见口苦/胸胁满/往来寒热，注意邪传少阳");
            }
        }

        // ----- 少阳→阳明传变 -----
        if (sixChannel.contains("Shaoyang")) {
            boolean hasChaoRe = symptoms.contains(ZZ_NS + "ChaoRe");
            boolean hasBianMi = symptoms.contains(ZZ_NS + "BianMi") || symptoms.contains(ZZ_NS + "DaBianJie");
            boolean hasZhanYu = symptoms.contains(ZZ_NS + "ZhanYu");
            boolean hasPositive = (hasChaoRe || hasBianMi || hasZhanYu);
            boolean hasReverse = pulses.contains(MX_NS + "Pulse_Xian") &&
                    symptoms.contains(ZZ_NS + "WangLaiHanRe") &&
                    !hasChaoRe;
            if (hasPositive && !hasReverse) {
                warnings.add("少阳→阳明：少阳证兼见潮热、大便硬或谵语，注意转属阳明");
            }
        }

        // ----- 少阴热化预警（心烦不得卧）-----
        if (sixChannel.contains("Shaoyin")) {
            String yinYang = (String) bagangResult.get("阴阳");
            if (!"阴证".equals(yinYang)) {
                boolean hasXinFanBuDeMian = symptoms.contains(ZZ_NS + "XinFan") ||
                        symptoms.contains(ZZ_NS + "BuDeMian");
                if (hasXinFanBuDeMian) {
                    warnings.add("少阴热化：脉微细而心烦不得卧，注意黄连阿胶汤证");
                }
            }
        }

        return warnings;
    }

    /**
     * 获取煎服法信息（从方剂本体）
     */
    private DecoctionInfo getDecoctionInfo(String formulaIri) {
        DecoctionInfo info = new DecoctionInfo();
        if (formulaIri == null) return info;

        OWLNamedIndividual formula = backendService.getIndividual(formulaIri);
        if (formula == null) return info;

        OWLDataProperty waterProp = backendService.safeGetDataProperty(FJ_NS + "water_volume");
        OWLDataProperty yieldProp = backendService.safeGetDataProperty(FJ_NS + "final_yield_volume");
        OWLDataProperty doseProp = backendService.safeGetDataProperty(FJ_NS + "single_dose_volume");
        OWLDataProperty adminProp = backendService.safeGetDataProperty(FJ_NS + "administration_method");
        OWLDataProperty dietProp = backendService.safeGetDataProperty(FJ_NS + "dietary_contraindications");
        OWLDataProperty stopProp = backendService.safeGetDataProperty(FJ_NS + "stop_condition");

        try {
            if (waterProp != null) {
                Set<OWLLiteral> vals = backendService.getDataPropertyValueOfIndividual(formula, waterProp);
                if (vals != null && !vals.isEmpty()) {
                    String water = vals.iterator().next().getLiteral();
                    String yield = getPropertyValue(formula, yieldProp);
                    info.decoctionMethod = "以水" + water + "，煮取" + (yield != null ? yield : "适量");
                }
            }
            if (adminProp != null) {
                Set<OWLLiteral> vals = backendService.getDataPropertyValueOfIndividual(formula, adminProp);
                if (vals != null && !vals.isEmpty()) {
                    info.administrationMethod = vals.iterator().next().getLiteral();
                }
            }
            if (dietProp != null) {
                Set<OWLLiteral> vals = backendService.getDataPropertyValueOfIndividual(formula, dietProp);
                if (vals != null && !vals.isEmpty()) {
                    info.dietaryContraindications = vals.iterator().next().getLiteral();
                }
            }
            if (stopProp != null) {
                Set<OWLLiteral> vals = backendService.getDataPropertyValueOfIndividual(formula, stopProp);
                if (vals != null && !vals.isEmpty()) {
                    info.stopCondition = vals.iterator().next().getLiteral();
                }
            }
        } catch (Exception e) {
            log.warn("读取方剂煎服信息失败: {}", e.getMessage());
        }

        return info;
    }

    private String getPropertyValue(OWLNamedIndividual ind, OWLDataProperty prop) {
        if (prop == null) return null;
        try {
            Set<OWLLiteral> vals = backendService.getDataPropertyValueOfIndividual(ind, prop);
            if (vals != null && !vals.isEmpty()) {
                return vals.iterator().next().getLiteral();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 构建处方证据链
     */
    private Map<String, Object> buildPrescriptionEvidenceChain(
            String finalFormula, String sixChannel, List<String> candidateFormulas,
            List<String> symptomIris, List<String> pulseIris, List<String> tongueIris) {

        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("处方", finalFormula != null ? backendService.resolveLabel(finalFormula) : "无");
        chain.put("六经", sixChannel != null ? backendService.resolveLabel(sixChannel) : "未定");
        chain.put("候选方证", candidateFormulas.stream().map(f -> backendService.resolveLabel(f)).collect(Collectors.toList()));
        chain.put("症状依据", symptomIris.stream().map(i -> backendService.resolveLabel(i)).collect(Collectors.toList()));
        chain.put("脉象依据", pulseIris.stream().map(i -> backendService.resolveLabel(i)).collect(Collectors.toList()));
        chain.put("舌象依据", tongueIris.stream().map(i -> backendService.resolveLabel(i)).collect(Collectors.toList()));
        return chain;
    }

    // ---------- 内部类 ----------
    private static class DecoctionInfo {
        String decoctionMethod = "";
        String administrationMethod = "";
        String dietaryContraindications = "";
        String stopCondition = "";
    }

    // ====================================================================
    //  辅助方法：八纲证据收集、决策、兼夹识别、六经映射等
    // ====================================================================

    private Map<IRI, List<String>> collectBagangEvidence(Set<String> iris) {
        Map<IRI, List<String>> evidence = new LinkedHashMap<>();
        OWLObjectProperty prop = backendService.safeGetObjectProperty(TCM_NS + "symptom_indicates_bagang");
        if (prop == null) {
            log.warn("symptom_indicates_bagang 属性未定义");
            return evidence;
        }
        for (String iriStr : iris) {
            try {
                OWLNamedIndividual ind = backendService.getIndividual(iriStr);
                if (ind == null) continue;
                Set<OWLNamedIndividual> targets = backendService.safeGetAllObjectPropertyValues(ind, prop);
                if (targets != null) {
                    for (OWLNamedIndividual target : targets) {
                        IRI bagangIri = target.getIRI();
                        String label = backendService.resolveLabel(iriStr);
                        evidence.computeIfAbsent(bagangIri, k -> new ArrayList<>()).add(label != null ? label : iriStr);
                    }
                }
            } catch (Exception e) {
                log.debug("收集证据异常: {}", iriStr, e);
            }
        }
        return evidence;
    }

    /**
     * 验证所有输入的 IRI 是否在本体中有定义
     * @param iris 输入 IRI 列表
     * @param typeName 类型名称（用于错误信息）
     * @throws IllegalArgumentException 如果存在未定义的 IRI
     */
    private void validateInputIris(List<String> iris, String typeName) {
        if (iris == null || iris.isEmpty()) return;
        List<String> undefined = new ArrayList<>();
        for (String iri : iris) {
            OWLNamedIndividual ind = backendService.getIndividual(iri);
            if (ind == null) {
                undefined.add(iri);
            }
        }
        if (!undefined.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("以下 %s 在本体中未定义：%s", typeName, String.join(", ", undefined))
            );
        }
    }

    private boolean hasBagang(Map<IRI, List<String>> evidence, String iri) {
        IRI target = IRI.create(iri);
        return evidence.containsKey(target) && !evidence.get(target).isEmpty();
    }

    private List<String> detectSpecialMarkers(String biaoLi, String hanRe, String xuShi, String yinYang,
                                              List<String> symptomIris, List<String> pulseIris, List<String> tongueIris) {
        List<String> markers = new ArrayList<>();
        if (checkJueyinMarker(biaoLi, hanRe, symptomIris)) {
            markers.add("疑似厥阴");
        }
        if (checkTaiShaoLiangGan(biaoLi, pulseIris, symptomIris)) {
            markers.add("疑似太少两感");
        }
        return markers;
    }

    private boolean checkJueyinMarker(String biaoLi, String hanRe, List<String> symptomIris) {
        if (!"寒热错杂".equals(hanRe)) return false;
        boolean isBan = "半表半里".equals(biaoLi) || (biaoLi != null && biaoLi.contains("半"));
        if (!isBan) return false;
        Set<String> symptomSet = new HashSet<>(symptomIris);
        String[] jueyinTigang = {
                ZZ_NS + "XiaoKe",
                ZZ_NS + "QiShangZhuangXin",
                ZZ_NS + "XinZhongTengRe",
                ZZ_NS + "JiErBuYuShi",
                ZZ_NS + "ShiZeTuHui"
        };
        for (String iri : jueyinTigang) {
            if (symptomSet.contains(iri)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkTaiShaoLiangGan(String biaoLi, List<String> pulseIris, List<String> symptomIris) {
        if (!"表证".equals(biaoLi)) return false;
        Set<String> pulseSet = new HashSet<>(pulseIris);
        if (!pulseSet.contains(MX_NS + "Pulse_Chen")) return false;
        Set<String> symptomSet = new HashSet<>(symptomIris);
        String[] jingShen = {
                ZZ_NS + "DanYuMei",
                ZZ_NS + "JingShenWeiMi",
                ZZ_NS + "ShenPiFaLi"
        };
        for (String iri : jingShen) {
            if (symptomSet.contains(iri)) {
                return true;
            }
        }
        return false;
    }

    private String decideBiaoLi(Map<IRI, List<String>> symptomEvidence,
                                Map<IRI, List<String>> pulseEvidence) {
        boolean hasBiao = hasBagang(symptomEvidence, BG_NS + "Biao");
        boolean hasLi = hasBagang(symptomEvidence, BG_NS + "Li");
        boolean hasBan = hasBagang(symptomEvidence, BG_NS + "BanBiaoBanLi");
        boolean pulseBiao = hasBagang(pulseEvidence, BG_NS + "Biao");
        boolean pulseLi = hasBagang(pulseEvidence, BG_NS + "Li");

        if (hasBiao && !hasLi && !hasBan) return "表证";
        if (hasLi && !hasBiao && !hasBan) return "里证";
        if (hasBan && !hasBiao && !hasLi) return "半表半里";
        if (hasBiao && hasLi && !hasBan) return "表里同病";
        if (hasBiao && hasBan && !hasLi) return "表半同病";
        if (hasLi && hasBan && !hasBiao) return "里半同病";
        if (hasBiao && hasLi && hasBan) return "三经同病";
        if (pulseBiao && !pulseLi) return "表证";
        if (pulseLi && !pulseBiao) return "里证";
        return "未定";
    }

    private String decideHanRe(Map<IRI, List<String>> symptomEvidence,
                               Map<IRI, List<String>> pulseEvidence,
                               Map<IRI, List<String>> tongueEvidence) {
        boolean pulseHan = hasBagang(pulseEvidence, BG_NS + "Han");
        boolean pulseRe = hasBagang(pulseEvidence, BG_NS + "Re");
        if (pulseHan && !pulseRe) return "寒证";
        if (pulseRe && !pulseHan) return "热证";
        if (pulseHan && pulseRe) return "寒热错杂";

        boolean tongueHan = hasBagang(tongueEvidence, BG_NS + "Han");
        boolean tongueRe = hasBagang(tongueEvidence, BG_NS + "Re");
        boolean symHan = hasBagang(symptomEvidence, BG_NS + "Han");
        boolean symRe = hasBagang(symptomEvidence, BG_NS + "Re");

        int hanCount = (tongueHan ? 1 : 0) + (symHan ? 1 : 0);
        int reCount = (tongueRe ? 1 : 0) + (symRe ? 1 : 0);

        if (hanCount > 0 && reCount == 0) return "寒证";
        if (reCount > 0 && hanCount == 0) return "热证";
        if (hanCount > 0 && reCount > 0) return "寒热错杂";
        return "未定";
    }

    private String decideXuShi(Map<IRI, List<String>> symptomEvidence,
                               Map<IRI, List<String>> pulseEvidence,
                               Map<IRI, List<String>> tongueEvidence) {
        boolean pulseShi = hasBagang(pulseEvidence, BG_NS + "Shi");
        boolean pulseXu = hasBagang(pulseEvidence, BG_NS + "Xu");
        if (pulseShi && !pulseXu) return "实证";
        if (pulseXu && !pulseShi) return "虚证";
        if (pulseShi && pulseXu) return "虚实错杂";

        boolean tongueShi = hasBagang(tongueEvidence, BG_NS + "Shi");
        boolean tongueXu = hasBagang(tongueEvidence, BG_NS + "Xu");
        boolean symShi = hasBagang(symptomEvidence, BG_NS + "Shi");
        boolean symXu = hasBagang(symptomEvidence, BG_NS + "Xu");

        int shiCount = (tongueShi ? 1 : 0) + (symShi ? 1 : 0);
        int xuCount = (tongueXu ? 1 : 0) + (symXu ? 1 : 0);

        if (shiCount > 0 && xuCount == 0) return "实证";
        if (xuCount > 0 && shiCount == 0) return "虚证";
        if (shiCount > 0 && xuCount > 0) return "虚实错杂";
        return "未定";
    }

    private String decideYinYang(Map<IRI, List<String>> symptomEvidence,
                                 Map<IRI, List<String>> pulseEvidence,
                                 Map<IRI, List<String>> tongueEvidence,
                                 List<String> symptomIris) {
        Map<String, Double> conf = decideYinYangWithConfidence(symptomEvidence, pulseEvidence, tongueEvidence, symptomIris);
        return conf.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("未定");
    }

    private boolean hasConcomitant(List<String> symptomIris, String concomitantIri) {
        if (symptomIris == null || symptomIris.isEmpty()) return false;
        OWLObjectProperty prop = backendService.safeGetObjectProperty(JJ_NS + "hasIdentificationSymptom");
        if (prop == null) return false;
        try {
            OWLNamedIndividual concomitant = backendService.getIndividual(concomitantIri);
            if (concomitant == null) return false;
            Set<OWLNamedIndividual> identSymptoms = backendService.safeGetAllObjectPropertyValues(concomitant, prop);
            if (identSymptoms == null) return false;
            Set<String> identIris = identSymptoms.stream().map(s -> s.getIRI().toString()).collect(Collectors.toSet());
            for (String symptom : symptomIris) {
                if (identIris.contains(symptom)) return true;
            }
        } catch (Exception e) {
            log.debug("兼夹匹配异常", e);
        }
        return false;
    }

    private String derivePrimaryDisease(String biaoLi, String yinYang) {
        String primaryBiaoLi = biaoLi;
        if (biaoLi.contains("同病") || biaoLi.contains("三经")) {
            if (biaoLi.startsWith("表")) primaryBiaoLi = "表证";
            else if (biaoLi.startsWith("里")) primaryBiaoLi = "里证";
            else if (biaoLi.startsWith("半")) primaryBiaoLi = "半表半里";
        }
        if ("表证".equals(primaryBiaoLi) && "阳证".equals(yinYang)) return "太阳病";
        if ("表证".equals(primaryBiaoLi) && "阴证".equals(yinYang)) return "少阴病";
        if ("里证".equals(primaryBiaoLi) && "阳证".equals(yinYang)) return "阳明病";
        if ("里证".equals(primaryBiaoLi) && "阴证".equals(yinYang)) return "太阴病";
        if ("半表半里".equals(primaryBiaoLi) && "阳证".equals(yinYang)) return "少阳病";
        if ("半表半里".equals(primaryBiaoLi) && "阴证".equals(yinYang)) return "厥阴病";
        return "六经难定";
    }

    private String mapToSixChannel(String biaoLi, String yinYang) {
        if (!"表证".equals(biaoLi) && !"里证".equals(biaoLi) && !"半表半里".equals(biaoLi)) {
            return null;
        }
        if ("未定".equals(yinYang)) {
            return null;
        }
        if ("表证".equals(biaoLi) && "阳证".equals(yinYang)) return LJ_NS + "Taiyang_Instance";
        if ("表证".equals(biaoLi) && "阴证".equals(yinYang)) return LJ_NS + "Shaoyin_Instance";
        if ("里证".equals(biaoLi) && "阳证".equals(yinYang)) return LJ_NS + "Yangming_Instance";
        if ("里证".equals(biaoLi) && "阴证".equals(yinYang)) return LJ_NS + "Taiyin_Instance";
        if ("半表半里".equals(biaoLi) && "阳证".equals(yinYang)) return LJ_NS + "Shaoyang_Instance";
        if ("半表半里".equals(biaoLi) && "阴证".equals(yinYang)) return LJ_NS + "Jueyin_Instance";
        return null;
    }
}