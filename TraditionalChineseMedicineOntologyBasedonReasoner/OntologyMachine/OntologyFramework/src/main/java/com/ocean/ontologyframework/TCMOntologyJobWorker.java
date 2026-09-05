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
    private Set<OWLClass> liujingSubclasses;
    private Set<OWLClass> fangzhengSubclasses;

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
            bagangSubclasses = getDirectNamedSubclasses(IRI.create(BASE_NS + "Bagang"));
            liujingSubclasses = getDirectNamedSubclasses(IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = getDirectNamedSubclasses(IRI.create(BASE_NS + "Fangzheng"));

            log.info("八纲直接子类数: {}, 六经直接子类数: {}, 方证直接子类数: {}",
                    bagangSubclasses.size(), liujingSubclasses.size(), fangzhengSubclasses.size());
            log.info("TCMOntologyJobWorker 初始化完成，TBox 推理机常驻");
        } catch (Exception e) {
            log.error("初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    /**
     * 获取指定根类的所有直接命名子类（基于 TBox）。
     */
    private Set<OWLClass> getDirectNamedSubclasses(IRI parentIri) {
        OWLClass parent = tboxDf.getOWLClass(parentIri);
        Set<OWLClass> subclasses = new HashSet<>();
        for (OWLSubClassOfAxiom axiom : tboxOntology.getAxioms(AxiomType.SUBCLASS_OF)) {
            OWLClassExpression superClass = axiom.getSuperClass();
            if (superClass.isNamed() && superClass.asOWLClass().equals(parent)) {
                OWLClassExpression subClass = axiom.getSubClass();
                if (subClass.isNamed()) {
                    subclasses.add(subClass.asOWLClass());
                }
            }
        }
        return subclasses;
    }

    /**
     * 创建一个临时本体：复制 TBox 所有公理，并添加指定患者的 ABox 断言。
     */
    private PatientContext createPatientContext(String patientIri,
                                                List<String> symptomIris,
                                                List<String> pulseIris,
                                                List<String> tongueIris,
                                                List<String> fuzhengIris) throws Exception {
        // 创建新的空本体
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology tempOntology = manager.createOntology(IRI.create(BASE_NS + "temp_" + UUID.randomUUID()));

        // 复制 TBox 所有公理
        for (OWLAxiom axiom : tboxOntology.getAxioms()) {
            manager.addAxiom(tempOntology, axiom);
        }

        OWLDataFactory df = manager.getOWLDataFactory();
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(patientIri));

        // 添加患者类型
        OWLClass patientClass = df.getOWLClass(IRI.create(BASE_NS + "Huanzhe"));
        manager.addAxiom(tempOntology, df.getOWLClassAssertionAxiom(patientClass, patient));

        // 添加对象属性断言
        addObjectPropertyAssertions(manager, df, patient, df.getOWLObjectProperty(IRI.create(HAS_SYMPTOM)), symptomIris);
        addObjectPropertyAssertions(manager, df, patient, df.getOWLObjectProperty(IRI.create(HAS_PULSE)), pulseIris);
        addObjectPropertyAssertions(manager, df, patient, df.getOWLObjectProperty(IRI.create(HAS_TONGUE)), tongueIris);
        addObjectPropertyAssertions(manager, df, patient, df.getOWLObjectProperty(IRI.create(HAS_ABDOMINAL)), fuzhengIris);

        // 创建临时推理机
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

    /**
     * 内部类：患者上下文，包含临时本体和推理机。
     */
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
            // 本体由 manager 管理，可省略显式移除
        }
    }

    // ====================================================================
    //  JobWorker：录入四诊信息（构建临时 ABox）
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

            // 创建临时患者上下文
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

            // 将上下文存入缓存
            patientContexts.put(patientIri, context);

            // 可选：打印推理类型（调试用）
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
    //  JobWorker：本体一致性检查（基于临时推理机）
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
    //  JobWorker：八纲分类（使用临时推理机 + 共享的子类集合）
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
    //  JobWorker：六经分类
    // ====================================================================
    @JobWorker(type = "liujing-classification", autoComplete = false)
    public void handleLiujingClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientContext context = patientContexts.get(patientIri);
            if (context == null) throw new IllegalStateException("患者上下文不存在: " + patientIri);

            OWLNamedIndividual patient = context.df.getOWLNamedIndividual(IRI.create(patientIri));
            Set<OWLClass> types = context.reasoner.getTypes(patient, false).getFlattened();

            List<String> liujingTypes = types.stream()
                    .filter(liujingSubclasses::contains)
                    .map(c -> c.getIRI().getFragment())
                    .collect(Collectors.toList());

            String sixChannel = liujingTypes.isEmpty() ? "六经难定" : liujingTypes.get(0);
            boolean isCombined = liujingTypes.size() > 1;

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sixChannel", sixChannel);
            output.put("liujingTypes", liujingTypes);
            output.put("isCombinedChannel", isCombined);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("六经分类完成: {} (合病={})", sixChannel, isCombined);
        } catch (Exception e) {
            log.error("六经分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("LIUJING_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
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

            String explanation = String.format(
                    "六经：%s，方证：%s，推荐方剂：%s。",
                    sixChannel != null ? sixChannel : "未定",
                    fangzheng != null ? fangzheng : "未定",
                    finalFormula != null ? finalFormula : "未定");

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("explanation", explanation);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("诊断解释完成：{}", explanation);

            // 清理患者上下文，释放资源
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