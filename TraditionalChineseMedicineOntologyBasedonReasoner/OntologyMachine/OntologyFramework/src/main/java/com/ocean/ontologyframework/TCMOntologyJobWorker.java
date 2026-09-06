package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.QueryService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    private BackendService backendService;
    private QueryService queryService;

    // TBox 相关（静态知识，不修改）
    private OWLOntology tboxOntology;
    private OWLDataFactory tboxDf;
    private OWLOntologyManager tboxManager;
    private OWLReasoner tboxReasoner;

    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";
    private static final String HAS_SYMPTOM = BASE_NS + "you_zhengzhuang";
    private static final String HAS_PULSE = BASE_NS + "you_maixiang";
    private static final String HAS_TONGUE = BASE_NS + "you_shexiang";
    private static final String HAS_ABDOMINAL = BASE_NS + "you_fuzheng";
    private static final String HAS_PRESCRIPTION = BASE_NS + "you_chufang";
    private static final String HAS_INGREDIENT = BASE_NS + "you_yaowu";

    // 根类的直接子类集合（基于 TBox 计算，全局共享）
    private Set<OWLClass> bagangSubclasses;
    private Set<OWLClass> liujingSubclasses;        // 包括单经病、合病父类及所有合病子类
    private Set<OWLClass> fangzhengSubclasses;
    private Set<OWLClass> hebingSubclasses;         // 所有合病具体类（不含 Hebings 本身）
    private Set<OWLClass> singleLiujingSubclasses;  // 单经病子类（排除合病相关）

    // 患者上下文缓存：key=patientIri，value=临时本体+推理机
    private final Map<String, PatientContext> patientContexts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            log.info("初始化 TCMOntologyJobWorker（TBox/ABox 分离模式）...");
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
            OBDAHandler obdaHandler = OBDAHandler.getInstance();
            backendService = BackendService.getInstance(mainOntologyPath, obdaHandler);
            queryService = new QueryService(backendService);

            // 加载 TBox 本体（包含所有静态公理，不含动态患者个体）
            tboxOntology = backendService.getOntologyService().gettBoxOntology();
            tboxManager = tboxOntology.getOWLOntologyManager();
            tboxDf = tboxManager.getOWLDataFactory();

            // 创建常驻 TBox 推理机，分类一次，后续查询直接复用
            tboxReasoner = OpenlletReasonerFactory.getInstance().createReasoner(tboxOntology);
            tboxReasoner.flush();

            // 计算各根类的直接子类（仅依赖 TBox）
            bagangSubclasses = getAllNamedSubclasses(IRI.create(BASE_NS + "Bagang"));
            liujingSubclasses = getAllNamedSubclasses(IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = getAllNamedSubclasses(IRI.create(BASE_NS + "Fangzheng"));
            hebingSubclasses = getAllNamedSubclasses(IRI.create(BASE_NS + "Hebings"));

            // 特殊处理：太少两感作为独立类，不继承 Hebings，但应视为合病
            hebingSubclasses.add(tboxDf.getOWLClass(IRI.create(BASE_NS + "TaiShaoLiangGan")));

            // 计算单经病子类：从全部六经病子类中排除合病父类及所有合病具体类
            singleLiujingSubclasses = new HashSet<>(liujingSubclasses);
            singleLiujingSubclasses.removeAll(hebingSubclasses);
            singleLiujingSubclasses.remove(tboxDf.getOWLClass(IRI.create(BASE_NS + "Hebings")));

            log.info("八纲子类数: {}, 六经全部子类数: {}, 方证子类数: {}, 合病具体类数: {}, 单经病子类数: {}",
                    bagangSubclasses.size(), liujingSubclasses.size(), fangzhengSubclasses.size(),
                    hebingSubclasses.size(), singleLiujingSubclasses.size());
            log.info("单经病子类: {}", singleLiujingSubclasses.stream().map(c -> c.getIRI().getFragment()).collect(Collectors.toList()));
            log.info("TCMOntologyJobWorker 初始化完成，TBox 推理机常驻");
        } catch (Exception e) {
            log.error("初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    /**
     * 递归获取指定根类的所有命名子类（包括间接子类）。
     */
    private Set<OWLClass> getAllNamedSubclasses(IRI parentIri) {
        OWLClass parent = tboxDf.getOWLClass(parentIri);
        Set<OWLClass> result = new HashSet<>();
        collectSubclasses(parent, result, new HashSet<>());
        return result;
    }

    private void collectSubclasses(OWLClass cls, Set<OWLClass> acc, Set<OWLClass> visited) {
        if (!visited.add(cls)) return;
        for (OWLSubClassOfAxiom axiom : tboxOntology.getAxioms(AxiomType.SUBCLASS_OF)) {
            OWLClassExpression superClass = axiom.getSuperClass();
            if (superClass.isNamed() && superClass.asOWLClass().equals(cls)) {
                OWLClassExpression subClassExpr = axiom.getSubClass();
                if (subClassExpr.isNamed()) {
                    OWLClass subClass = subClassExpr.asOWLClass();
                    acc.add(subClass);
                    collectSubclasses(subClass, acc, visited);
                }
            }
        }
    }

    /**
     * 创建一个临时本体：复制 TBox 所有公理，并添加指定患者的 ABox 断言。
     */
    private PatientContext createPatientContext(String patientIri,
                                                List<String> symptomIris,
                                                List<String> pulseIris,
                                                List<String> tongueIris,
                                                List<String> fuzhengIris) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology tempOntology = manager.createOntology(IRI.create(BASE_NS + "temp_" + UUID.randomUUID()));

        for (OWLAxiom axiom : tboxOntology.getAxioms()) {
            manager.addAxiom(tempOntology, axiom);
        }

        OWLDataFactory df = manager.getOWLDataFactory();
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(patientIri));

        OWLClass patientClass = df.getOWLClass(IRI.create(BASE_NS + "Huanzhe"));
        manager.addAxiom(tempOntology, df.getOWLClassAssertionAxiom(patientClass, patient));

        addObjectPropertyAssertions(manager, df, patient, df.getOWLObjectProperty(IRI.create(HAS_SYMPTOM)), symptomIris);
        addObjectPropertyAssertions(manager, df, patient, df.getOWLObjectProperty(IRI.create(HAS_PULSE)), pulseIris);
        addObjectPropertyAssertions(manager, df, patient, df.getOWLObjectProperty(IRI.create(HAS_TONGUE)), tongueIris);
        addObjectPropertyAssertions(manager, df, patient, df.getOWLObjectProperty(IRI.create(HAS_ABDOMINAL)), fuzhengIris);

        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(tempOntology);
        reasoner.flush();

        return new PatientContext(manager, df, tempOntology, reasoner);
    }

    private void addObjectPropertyAssertions(OWLOntologyManager manager,
                                             OWLDataFactory df,
                                             OWLNamedIndividual subject,
                                             OWLObjectProperty property,
                                             List<String> valueIris) {
        for (String valueIri : valueIris) {
            OWLNamedIndividual value = df.getOWLNamedIndividual(IRI.create(valueIri));
            manager.addAxiom(manager.getOntologies().iterator().next(),
                    df.getOWLObjectPropertyAssertionAxiom(property, subject, value));
        }
    }

    private static class PatientContext {
        final OWLOntologyManager manager;
        final OWLDataFactory df;
        final OWLOntology ontology;
        final OWLReasoner reasoner;

        PatientContext(OWLOntologyManager manager, OWLDataFactory df, OWLOntology ontology, OWLReasoner reasoner) {
            this.manager = manager;
            this.df = df;
            this.ontology = ontology;
            this.reasoner = reasoner;
        }

        void dispose() {
            reasoner.dispose();
        }
    }

    // ====================================================================
    //  JobWorker：录入四诊信息
    // ====================================================================
    @JobWorker(type = "sizhen-input", autoComplete = false)
    public void handleSizhenInput(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            List<String> symptomIris = getList(vars, "symptomIris");
            List<String> pulseIris = getList(vars, "pulseIris");
            List<String> tongueIris = getList(vars, "tongueIris");
            List<String> fuzhengIris = getList(vars, "fuzhengIris");

            String patientIri = BASE_NS + "Patient_" + job.getKey();

            PatientContext context = createPatientContext(patientIri, symptomIris, pulseIris, tongueIris, fuzhengIris);

            boolean consistent = context.reasoner.isConsistent();
            if (!consistent) {
                log.warn("患者 {} 导致本体不一致，丢弃临时上下文", patientIri);
                context.dispose();
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("patientIri", patientIri);
                output.put("recorded", false);
                output.put("inconsistent", true);
                client.newCompleteCommand(job.getKey()).variables(output).send().join();
                return;
            }

            patientContexts.put(patientIri, context);

            Set<OWLClass> types = context.reasoner.getTypes(
                    context.df.getOWLNamedIndividual(IRI.create(patientIri)), false).getFlattened();
            log.info("患者所有推理类型 ({} 个):", types.size());
            types.forEach(t -> log.info("  - {}", t.getIRI()));

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("patientIri", patientIri);
            output.put("recorded", true);
            output.put("inconsistent", false);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("四诊信息录入完成，患者个体: {}", patientIri);
        } catch (Exception e) {
            log.error("sizhen-input 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("SIZHEN_INPUT_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  JobWorker：本体一致性检查
    // ====================================================================
    @JobWorker(type = "ontology-consistency-check", autoComplete = false)
    public void handleConsistencyCheck(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientContext context = patientContexts.get(patientIri);
            boolean consistent = context != null && context.reasoner.isConsistent();

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("consistent", consistent);
            if (!consistent && context != null) {
                Node<OWLClass> unsatClasses = context.reasoner.getUnsatisfiableClasses();
                List<String> unsatIris = unsatClasses.getEntities().stream()
                        .map(c -> c.getIRI().toString())
                        .collect(Collectors.toList());
                output.put("unsatisfiableClasses", unsatIris);
            }
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("一致性检查完成，consistent={}", consistent);
        } catch (Exception e) {
            log.error("一致性检查失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("CONSISTENCY_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  JobWorker：八纲分类
    // ====================================================================
    @JobWorker(type = "bagang-classification", autoComplete = false)
    public void handleBagangClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientContext context = patientContexts.get(patientIri);
            if (context == null) throw new IllegalStateException("患者上下文不存在: " + patientIri);

            OWLNamedIndividual patient = context.df.getOWLNamedIndividual(IRI.create(patientIri));
            Set<OWLClass> types = context.reasoner.getTypes(patient, false).getFlattened();

            List<String> bagangTypes = types.stream()
                    .filter(bagangSubclasses::contains)
                    .map(c -> c.getIRI().getFragment())
                    .collect(Collectors.toList());

            Map<String, Object> bagangResult = new LinkedHashMap<>();
            bagangResult.put("表里", extractFromBagang(bagangTypes, "BanbiaoBanli", "Biao", "Li"));
            bagangResult.put("寒热", extractFromBagang(bagangTypes, "Han", "Re"));
            bagangResult.put("虚实", extractFromBagang(bagangTypes, "Xu", "Shi"));
            bagangResult.put("阴阳", extractFromBagang(bagangTypes, "Yin", "Yang"));
            bagangResult.put("bagangTypes", bagangTypes);
            bagangResult.put("complete", !bagangResult.containsValue("未定"));

            client.newCompleteCommand(job.getKey()).variables(Map.of("bagangResult", bagangResult)).send().join();
            log.info("八纲分类完成: {}", bagangResult);
        } catch (Exception e) {
            log.error("八纲分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("BAGANG_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  JobWorker：六经分类（支持特殊合病太少两感）
    // ====================================================================
    @JobWorker(type = "liujing-classification", autoComplete = false)
    public void handleLiujingClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientContext context = patientContexts.get(patientIri);
            if (context == null) throw new IllegalStateException("患者上下文不存在: " + patientIri);

            OWLNamedIndividual patient = context.df.getOWLNamedIndividual(IRI.create(patientIri));
            Set<OWLClass> types = context.reasoner.getTypes(patient, false).getFlattened();

            // 检查是否为太少两感（独立类）
            OWLClass taiShaoLiangGanClass = context.df.getOWLClass(IRI.create(BASE_NS + "TaiShaoLiangGan"));
            boolean isTaiShaoLiangGan = types.contains(taiShaoLiangGanClass);

            List<String> liujingTypes;
            String sixChannel;
            String combinedDiseaseMark;
            boolean isCombined;

            if (isTaiShaoLiangGan) {
                // 特殊合病：太少两感
                liujingTypes = List.of("TaiShaoLiangGan");
                sixChannel = "TaiShaoLiangGan";
                combinedDiseaseMark = "太少两感";
                isCombined = true;
            } else {
                // 常规：筛选单经病类型（排除合病类）
                liujingTypes = types.stream()
                        .filter(singleLiujingSubclasses::contains)
                        .map(c -> c.getIRI().getFragment())
                        .sorted()
                        .collect(Collectors.toList());

                if (liujingTypes.isEmpty()) {
                    sixChannel = "六经难定";
                    combinedDiseaseMark = null;
                    isCombined = false;
                } else {
                    sixChannel = liujingTypes.get(0);
                    isCombined = liujingTypes.size() > 1;
                    combinedDiseaseMark = isCombined ? buildCombinedDiseaseMark(liujingTypes) : null;
                }
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sixChannel", sixChannel);
            output.put("liujingTypes", liujingTypes);
            output.put("isCombinedChannel", isCombined);
            output.put("combinedDiseaseMark", combinedDiseaseMark);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("六经分类完成: 单经病列表={}, 主病证={}, 合病={}, 合病标记={}",
                    liujingTypes, sixChannel, isCombined, combinedDiseaseMark);
        } catch (Exception e) {
            log.error("六经分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("LIUJING_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 根据单经病列表生成合病中文标记。
     * 支持固定名称（三阳合病、太少两感、常见两经合病）及通用拼接。
     */
    private String buildCombinedDiseaseMark(List<String> liujingTypes) {
        if (liujingTypes == null || liujingTypes.size() < 2) {
            return null;
        }

        // 特殊合病名称优先
        if (liujingTypes.size() == 3 && liujingTypes.containsAll(List.of("Taiyangbing", "Yangmingbing", "Shaoyangbing"))) {
            return "三阳合病";
        }
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Shaoyinbing"))) {
            return "太少两感";
        }
        // 常见两经合病固定名称
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Yangmingbing"))) {
            return "太阳阳明合病";
        }
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Shaoyangbing"))) {
            return "太阳少阳合病";
        }
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Shaoyangbing", "Yangmingbing"))) {
            return "少阳阳明合病";
        }

        // 通用逻辑：按六经顺序排序后拼接
        Map<String, Integer> orderMap = new LinkedHashMap<>();
        orderMap.put("Taiyangbing", 0);
        orderMap.put("Yangmingbing", 1);
        orderMap.put("Shaoyangbing", 2);
        orderMap.put("Taiyinbing", 3);
        orderMap.put("Shaoyinbing", 4);
        orderMap.put("Jueyinbing", 5);

        Map<String, String> nameMap = new LinkedHashMap<>();
        nameMap.put("Taiyangbing", "太阳");
        nameMap.put("Yangmingbing", "阳明");
        nameMap.put("Shaoyangbing", "少阳");
        nameMap.put("Taiyinbing", "太阴");
        nameMap.put("Shaoyinbing", "少阴");
        nameMap.put("Jueyinbing", "厥阴");

        List<String> sorted = new ArrayList<>(liujingTypes);
        sorted.sort(Comparator.comparingInt(type -> orderMap.getOrDefault(type, Integer.MAX_VALUE)));

        StringBuilder sb = new StringBuilder();
        for (String type : sorted) {
            sb.append(nameMap.getOrDefault(type, type));
        }
        sb.append("合病");
        return sb.toString();
    }

    // ====================================================================
    //  JobWorker：方证分类
    // ====================================================================
    @JobWorker(type = "fangzheng-classification", autoComplete = false)
    public void handleFangzhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientContext context = patientContexts.get(patientIri);
            if (context == null) throw new IllegalStateException("患者上下文不存在: " + patientIri);

            OWLNamedIndividual patient = context.df.getOWLNamedIndividual(IRI.create(patientIri));
            Set<OWLClass> types = context.reasoner.getTypes(patient, false).getFlattened();

            List<String> fangzhengTypes = types.stream()
                    .filter(fangzhengSubclasses::contains)
                    .map(c -> c.getIRI().getFragment())
                    .collect(Collectors.toList());

            String fangzheng = fangzhengTypes.isEmpty() ? "方证未定" : fangzhengTypes.get(0);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("fangzheng", fangzheng);
            output.put("fangzhengTypes", fangzhengTypes);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("方证分类完成: {}", fangzheng);
        } catch (Exception e) {
            log.error("方证分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("FANGZHENG_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  JobWorker：方剂药物推荐
    // ====================================================================
    @JobWorker(type = "prescription-recommendation", autoComplete = false)
    public void handlePrescriptionRecommendation(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String fangzhengFragment = (String) vars.get("fangzheng");
            if (fangzhengFragment == null || "方证未定".equals(fangzhengFragment)) {
                Map<String, Object> output = new HashMap<>();
                output.put("finalFormula", null);
                output.put("herbs", new ArrayList<>());
                client.newCompleteCommand(job.getKey()).variables(output).send().join();
                return;
            }

            String patientIri = (String) vars.get("patientIri");
            PatientContext context = patientContexts.get(patientIri);
            if (context == null) throw new IllegalStateException("患者上下文不存在: " + patientIri);

            OWLClass fangzhengClass = context.df.getOWLClass(IRI.create(BASE_NS + fangzhengFragment));
            String formulaIri = extractFormulaFromFangzhengClass(context, fangzhengClass);

            if (formulaIri == null) {
                log.warn("方证 {} 未找到关联方剂", fangzhengFragment);
                Map<String, Object> output = new HashMap<>();
                output.put("finalFormula", null);
                output.put("herbs", new ArrayList<>());
                client.newCompleteCommand(job.getKey()).variables(output).send().join();
                return;
            }

            OWLNamedIndividual formulaInd = context.df.getOWLNamedIndividual(IRI.create(formulaIri));
            OWLObjectProperty hasIngredientProp = context.df.getOWLObjectProperty(IRI.create(HAS_INGREDIENT));
            Set<OWLNamedIndividual> herbs = context.reasoner.getObjectPropertyValues(formulaInd, hasIngredientProp).getFlattened();
            List<String> herbIris = herbs.stream().map(h -> h.getIRI().toString()).collect(Collectors.toList());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("finalFormula", formulaIri);
            output.put("candidateFormulas", List.of(formulaIri));
            output.put("herbs", herbIris);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("方剂推荐完成: {}，药物: {}", formulaIri, herbIris);
        } catch (Exception e) {
            log.error("方剂推荐失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("PRESCRIPTION_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  JobWorker：生成诊断解释（并清理患者上下文）
    // ====================================================================
    @JobWorker(type = "diagnosis-explanation", autoComplete = false)
    public void handleDiagnosisExplanation(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String sixChannel = (String) vars.get("sixChannel");
            String fangzheng = (String) vars.get("fangzheng");
            String finalFormula = (String) vars.get("finalFormula");
            String patientIri = (String) vars.get("patientIri");
            String combinedDiseaseMark = (String) vars.get("combinedDiseaseMark");

            // 根据合病标记决定六经显示内容
            String liujingDisplay;
            if (combinedDiseaseMark != null && !combinedDiseaseMark.isEmpty()) {
                liujingDisplay = combinedDiseaseMark;
            } else {
                liujingDisplay = sixChannel != null ? sixChannel : "未定";
            }

            String explanation = String.format(
                    "六经：%s，方证：%s，推荐方剂：%s。",
                    liujingDisplay,
                    fangzheng != null ? fangzheng : "未定",
                    finalFormula != null ? finalFormula : "未定");

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("explanation", explanation);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("诊断解释完成：{}", explanation);

            if (patientIri != null) {
                PatientContext context = patientContexts.remove(patientIri);
                if (context != null) {
                    context.dispose();
                    log.debug("已清理患者上下文: {}", patientIri);
                }
            }
        } catch (Exception e) {
            log.error("诊断解释失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("EXPLANATION_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ====================================================================
    //  辅助方法
    // ====================================================================
    private List<String> getList(Map<String, Object> vars, String key) {
        Object val = vars.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String extractFromBagang(List<String> bagangTypes, String... candidates) {
        for (String candidate : candidates) {
            for (String type : bagangTypes) {
                if (type.contains(candidate)) {
                    return mapBagangToChinese(candidate);
                }
            }
        }
        return "未定";
    }

    private String mapBagangToChinese(String shortName) {
        switch (shortName) {
            case "Biao": return "表证";
            case "Li": return "里证";
            case "BanbiaoBanli": return "半表半里";
            case "Han": return "寒证";
            case "Re": return "热证";
            case "Xu": return "虚证";
            case "Shi": return "实证";
            case "Yin": return "阴证";
            case "Yang": return "阳证";
            default: return "未定";
        }
    }

    /**
     * 从方证类的等价类或子类公理中提取 has_prescription 的 hasValue 目标。
     */
    private String extractFormulaFromFangzhengClass(PatientContext context, OWLClass fangzhengClass) {
        OWLOntology ont = context.ontology;
        for (OWLEquivalentClassesAxiom eqAxiom : ont.getEquivalentClassesAxioms(fangzhengClass)) {
            for (OWLClassExpression expr : eqAxiom.getClassExpressions()) {
                if (expr.equals(fangzhengClass)) continue;
                String formula = findHasPrescriptionValue(expr);
                if (formula != null) return formula;
            }
        }
        for (OWLSubClassOfAxiom subAxiom : ont.getSubClassAxiomsForSubClass(fangzhengClass)) {
            String formula = findHasPrescriptionValue(subAxiom.getSuperClass());
            if (formula != null) return formula;
        }
        return null;
    }

    private String findHasPrescriptionValue(OWLClassExpression expr) {
        if (expr instanceof OWLObjectHasValue) {
            OWLObjectHasValue hasValue = (OWLObjectHasValue) expr;
            if (hasValue.getProperty().asOWLObjectProperty().getIRI().toString().equals(HAS_PRESCRIPTION)) {
                OWLIndividual ind = hasValue.getFiller();
                if (ind.isNamed()) {
                    return ind.asOWLNamedIndividual().getIRI().toString();
                }
            }
        } else if (expr instanceof OWLObjectIntersectionOf) {
            for (OWLClassExpression op : ((OWLObjectIntersectionOf) expr).getOperands()) {
                String formula = findHasPrescriptionValue(op);
                if (formula != null) return formula;
            }
        }
        return null;
    }
}