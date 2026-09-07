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
    private Set<OWLClass> liujingSubclasses;        // 六经病子类（目前只有单经病）
    private Set<OWLClass> fangzhengSubclasses;
    private Set<OWLClass> singleLiujingSubclasses;  // 等同于 liujingSubclasses，保留以兼容命名

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

            // 计算各根类的子类
            bagangSubclasses = getAllNamedSubclasses(IRI.create(BASE_NS + "Bagang"));
            liujingSubclasses = getAllNamedSubclasses(IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = getAllNamedSubclasses(IRI.create(BASE_NS + "Fangzheng"));

            // 单经病子类 = 六经病子类（本体中已无合病类）
            singleLiujingSubclasses = new HashSet<>(liujingSubclasses);

            log.info("八纲子类数: {}, 六经子类数: {}, 方证子类数: {}",
                    bagangSubclasses.size(), liujingSubclasses.size(), fangzhengSubclasses.size());
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
    //  JobWorker：八纲分类（输出多值列表）
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
            List<String> biaoli = extractMultipleFromBagang(bagangTypes, "Biao", "Li", "BanbiaoBanli");
            List<String> hanre = extractMultipleFromBagang(bagangTypes, "Han", "Re");
            List<String> xushi = extractMultipleFromBagang(bagangTypes, "Xu", "Shi");
            List<String> yinyang = extractMultipleFromBagang(bagangTypes, "Yin", "Yang");

            bagangResult.put("表里", biaoli);
            bagangResult.put("寒热", hanre);
            bagangResult.put("虚实", xushi);
            bagangResult.put("阴阳", yinyang);
            bagangResult.put("bagangTypes", bagangTypes);

            boolean complete = !biaoli.isEmpty() && !hanre.isEmpty() && !xushi.isEmpty() && !yinyang.isEmpty();
            bagangResult.put("complete", complete);

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

    /**
     * 从 bagangTypes 中提取指定维度的所有标签（中文）。
     * @param bagangTypes 所有八纲类型 fragment 列表
     * @param candidates 该维度可能的候选类型
     * @return 匹配的中文标签列表（可能为空）
     */
    private List<String> extractMultipleFromBagang(List<String> bagangTypes, String... candidates) {
        Map<String, String> map = new HashMap<>();
        map.put("Biao", "表证");
        map.put("Li", "里证");
        map.put("BanbiaoBanli", "半表半里");
        map.put("Han", "寒证");
        map.put("Re", "热证");
        map.put("Xu", "虚证");
        map.put("Shi", "实证");
        map.put("Yin", "阴证");
        map.put("Yang", "阳证");

        Set<String> candidateSet = new HashSet<>(Arrays.asList(candidates));
        List<String> result = new ArrayList<>();
        for (String type : bagangTypes) {
            if (candidateSet.contains(type)) {
                String chinese = map.get(type);
                if (chinese != null) {
                    result.add(chinese);
                }
            }
        }
        return result;
    }

    // ====================================================================
    //  JobWorker：六经分类（统一基于单经病列表）
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
                    .filter(singleLiujingSubclasses::contains)
                    .map(c -> c.getIRI().getFragment())
                    .sorted()
                    .collect(Collectors.toList());

            String sixChannel;
            String combinedDiseaseMark = null;
            boolean isCombined = liujingTypes.size() > 1;

            if (liujingTypes.isEmpty()) {
                sixChannel = "六经难定";
            } else {
                sixChannel = liujingTypes.get(0);
                if (isCombined) {
                    combinedDiseaseMark = buildCombinedDiseaseMark(liujingTypes);
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

        if (liujingTypes.size() == 3 && liujingTypes.containsAll(List.of("Taiyangbing", "Yangmingbing", "Shaoyangbing"))) {
            return "三阳合病";
        }
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Shaoyinbing"))) {
            return "太少两感";
        }
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Yangmingbing"))) {
            return "太阳阳明合病";
        }
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Shaoyangbing"))) {
            return "太阳少阳合病";
        }
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Shaoyangbing", "Yangmingbing"))) {
            return "少阳阳明合病";
        }

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
    //  JobWorker：方证分类（支持或然症排序与候选推荐）
    // ====================================================================
    // ====================================================================
    //  JobWorker：方证分类（支持或然症排序与候选推荐）
    // ====================================================================
    // ====================================================================
    //  JobWorker：方证分类（支持主症不全时按主症命中数排序推荐）
    // ====================================================================
    // ====================================================================
    //  JobWorker：方证分类（主证命中数优先，或然症次之）
    // ====================================================================
    @JobWorker(type = "fangzheng-classification", autoComplete = false)
    public void handleFangzhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientContext context = patientContexts.get(patientIri);
            if (context == null) throw new IllegalStateException("患者上下文不存在: " + patientIri);

            OWLNamedIndividual patient = context.df.getOWLNamedIndividual(IRI.create(patientIri));
            Set<OWLClass> types = context.reasoner.getTypes(patient, false).getFlattened();

            List<OWLClass> fangzhengClasses = types.stream()
                    .filter(fangzhengSubclasses::contains)
                    .collect(Collectors.toList());

            Map<String, Object> vars = job.getVariablesAsMap();
            List<String> symptomIris = getList(vars, "symptomIris");
            List<String> pulseIris = getList(vars, "pulseIris");
            List<String> tongueIris = getList(vars, "tongueIris");
            List<String> fuzhengIris = getList(vars, "fuzhengIris");
            Set<String> patientFacts = new HashSet<>();
            patientFacts.addAll(symptomIris);
            patientFacts.addAll(pulseIris);
            patientFacts.addAll(tongueIris);
            patientFacts.addAll(fuzhengIris);

            String fangzheng;
            List<String> fangzhengTypes = new ArrayList<>();
            List<String> candidateFragments = new ArrayList<>();
            Map<String, Integer> necessaryScoreMap = new LinkedHashMap<>(); // 主证命中数
            Map<String, Integer> possibleScoreMap = new LinkedHashMap<>();  // 或然症命中数

            if (fangzhengClasses.isEmpty()) {
                fangzheng = "方证未定";
                List<OWLClass> allFangzhengClasses = new ArrayList<>(fangzhengSubclasses);
                for (OWLClass fzClass : allFangzhengClasses) {
                    int nScore = countNecessaryConditionMatches(context, fzClass, patientFacts);
                    int pScore = countPossibleSymptomMatches(context, fzClass, patientFacts);
                    necessaryScoreMap.put(fzClass.getIRI().getFragment(), nScore);
                    possibleScoreMap.put(fzClass.getIRI().getFragment(), pScore);
                }
                List<OWLClass> sorted = sortCandidates(allFangzhengClasses, necessaryScoreMap, possibleScoreMap);
                candidateFragments = sorted.stream()
                        .map(c -> c.getIRI().getFragment())
                        .collect(Collectors.toList());
                necessaryScoreMap = buildSortedScoreMap(sorted, necessaryScoreMap);
                possibleScoreMap = buildSortedScoreMap(sorted, possibleScoreMap);
                fangzhengTypes = new ArrayList<>();
            } else {
                for (OWLClass fzClass : fangzhengClasses) {
                    int nScore = countNecessaryConditionMatches(context, fzClass, patientFacts);
                    int pScore = countPossibleSymptomMatches(context, fzClass, patientFacts);
                    necessaryScoreMap.put(fzClass.getIRI().getFragment(), nScore);
                    possibleScoreMap.put(fzClass.getIRI().getFragment(), pScore);
                }
                List<OWLClass> sorted = sortCandidates(fangzhengClasses, necessaryScoreMap, possibleScoreMap);

                final Map<String, Integer> finalNecessaryMap = necessaryScoreMap;
                final Map<String, Integer> finalPossibleMap = possibleScoreMap;

                int maxNecessary = finalNecessaryMap.get(sorted.get(0).getIRI().getFragment());
                int maxPossible = finalPossibleMap.get(sorted.get(0).getIRI().getFragment());

                List<OWLClass> topClasses = sorted.stream()
                        .filter(c -> finalNecessaryMap.get(c.getIRI().getFragment()) == maxNecessary &&
                                finalPossibleMap.get(c.getIRI().getFragment()) == maxPossible)
                        .collect(Collectors.toList());

                fangzheng = topClasses.get(0).getIRI().getFragment();
                fangzhengTypes = topClasses.stream()
                        .map(c -> c.getIRI().getFragment())
                        .collect(Collectors.toList());
                candidateFragments = sorted.stream()
                        .map(c -> c.getIRI().getFragment())
                        .collect(Collectors.toList());
                necessaryScoreMap = buildSortedScoreMap(sorted, necessaryScoreMap);
                possibleScoreMap = buildSortedScoreMap(sorted, possibleScoreMap);
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("fangzheng", fangzheng);
            output.put("fangzhengTypes", fangzhengTypes);
            output.put("candidateFangzhengs", candidateFragments);
            output.put("candidateNecessaryScores", necessaryScoreMap); // 主证命中数
            output.put("candidateScores", possibleScoreMap);           // 或然症命中数
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("方证分类完成: {} (候选: {}, 主证得分: {}, 或然得分: {})",
                    fangzheng, candidateFragments, necessaryScoreMap, possibleScoreMap);
        } catch (Exception e) {
            log.error("方证分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("FANGZHENG_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 排序：先主证命中数降序，再或然症命中数降序。
     */
    private List<OWLClass> sortCandidates(List<OWLClass> classes,
                                          Map<String, Integer> necessaryScoreMap,
                                          Map<String, Integer> possibleScoreMap) {
        return classes.stream()
                .sorted((c1, c2) -> {
                    int necessaryCompare = necessaryScoreMap.get(c2.getIRI().getFragment())
                            .compareTo(necessaryScoreMap.get(c1.getIRI().getFragment()));
                    if (necessaryCompare != 0) {
                        return necessaryCompare;
                    }
                    return possibleScoreMap.get(c2.getIRI().getFragment())
                            .compareTo(possibleScoreMap.get(c1.getIRI().getFragment()));
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建按已排序顺序排列的得分 Map（降序）。
     */
    private Map<String, Integer> buildSortedScoreMap(List<OWLClass> sortedClasses,
                                                     Map<String, Integer> originalScoreMap) {
        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (OWLClass c : sortedClasses) {
            String name = c.getIRI().getFragment();
            sortedMap.put(name, originalScoreMap.get(name));
        }
        return sortedMap;
    }

    /**
     * 计算患者事实与方证决定性主症的命中数量。
     * 从方证等价类中提取所有 someValuesFrom 约束的 filler 类，并检查患者是否具有相应个体。
     */
    private int countNecessaryConditionMatches(PatientContext context, OWLClass fangzhengClass, Set<String> patientFacts) {
        Set<OWLClassExpression> necessaryFillers = new HashSet<>();
        OWLOntology ont = context.ontology;
        for (OWLEquivalentClassesAxiom ax : ont.getEquivalentClassesAxioms(fangzhengClass)) {
            for (OWLClassExpression expr : ax.getClassExpressions()) {
                if (expr.equals(fangzhengClass)) continue;
                collectSomeValuesFillers(expr, necessaryFillers);
            }
        }
        int count = 0;
        for (OWLClassExpression filler : necessaryFillers) {
            if (filler instanceof OWLClass) {
                OWLClass fillerClass = (OWLClass) filler;
                if (!fillerClass.isOWLThing() && !fillerClass.isOWLNothing()) {
                    String instanceIri = fillerClass.getIRI().toString() + "_instance";
                    if (patientFacts.contains(instanceIri)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 递归收集交集表达式中的 someValuesFrom filler。
     */
    private void collectSomeValuesFillers(OWLClassExpression expr, Set<OWLClassExpression> acc) {
        if (expr instanceof OWLObjectSomeValuesFrom) {
            OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) expr;
            acc.add(some.getFiller());
        } else if (expr instanceof OWLObjectIntersectionOf) {
            for (OWLClassExpression op : ((OWLObjectIntersectionOf) expr).getOperands()) {
                collectSomeValuesFillers(op, acc);
            }
        }
        // 忽略并集
    }

    /**
     * 获取方证类上通过 possibleSymptom 注释属性关联的或然症 IRI 集合。
     */
    private Set<IRI> getPossibleSymptomIris(PatientContext context, OWLClass fangzhengClass) {
        Set<IRI> result = new HashSet<>();
        OWLOntology ont = context.ontology;
        for (OWLAnnotationAssertionAxiom ax : ont.getAnnotationAssertionAxioms(fangzhengClass.getIRI())) {
            if (ax.getProperty().getIRI().getFragment().equals("possibleSymptom") &&
                    ax.getValue() instanceof IRI) {
                result.add((IRI) ax.getValue());
            }
        }
        return result;
    }

    /**
     * 计算患者事实与方证或然症的命中数量。
     */
    private int countPossibleSymptomMatches(PatientContext context, OWLClass fangzhengClass, Set<String> patientFacts) {
        Set<IRI> possibleSymptomIris = getPossibleSymptomIris(context, fangzhengClass);
        int count = 0;
        for (IRI iri : possibleSymptomIris) {
            String iriStr = iri.toString();
            String instanceIri = iriStr.endsWith("_instance") ? iriStr : iriStr + "_instance";
            if (patientFacts.contains(instanceIri)) {
                count++;
            }
        }
        return count;
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