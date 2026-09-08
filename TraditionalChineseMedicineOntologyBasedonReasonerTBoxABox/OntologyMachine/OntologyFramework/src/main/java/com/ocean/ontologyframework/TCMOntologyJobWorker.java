package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.QueryService;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import openllet.owlapi.OpenlletReasonerFactory;
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
    private Set<OWLClass> singleLiujingSubclasses;

    private static final String ANTAGONISTIC = BASE_NS + "antagonistic";
    private static final String FEARING = BASE_NS + "fearing";

    // 患者上下文缓存
    private final Map<String, BackendService.PatientContext> patientContexts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            log.info("初始化 TCMOntologyJobWorker（TBox/ABox 分离模式）...");
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
            OBDAHandler obdaHandler = OBDAHandler.getInstance();
            backendService = BackendService.getInstance(mainOntologyPath, obdaHandler);
            queryService = new QueryService(backendService);

            tboxOntology = backendService.getOntologyService().gettBoxOntology();
            tboxManager = tboxOntology.getOWLOntologyManager();
            tboxDf = tboxManager.getOWLDataFactory();

            tboxReasoner = OpenlletReasonerFactory.getInstance().createReasoner(tboxOntology);
            tboxReasoner.flush();

            // 使用 BackendService 通用方法获取子类
            bagangSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Bagang"));
            liujingSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Fangzheng"));
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

    // ==================== JobWorker：录入四诊信息 ====================
    @JobWorker(type = "sizhen-input", autoComplete = false)
    public void handleSizhenInput(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            List<String> symptomIris = getList(vars, "symptomIris");
            List<String> pulseIris = getList(vars, "pulseIris");
            List<String> tongueIris = getList(vars, "tongueIris");
            List<String> fuzhengIris = getList(vars, "fuzhengIris");

            String patientIri = BASE_NS + "Patient_" + job.getKey();

            // 使用 BackendService 通用方法创建临时上下文
            Map<String, List<String>> objectPropertyAssertions = new LinkedHashMap<>();
            objectPropertyAssertions.put(HAS_SYMPTOM, symptomIris);
            objectPropertyAssertions.put(HAS_PULSE, pulseIris);
            objectPropertyAssertions.put(HAS_TONGUE, tongueIris);
            objectPropertyAssertions.put(HAS_ABDOMINAL, fuzhengIris);

            BackendService.PatientContext context = backendService.createTemporaryContext(
                    patientIri,
                    List.of(BASE_NS + "Huanzhe"),
                    objectPropertyAssertions
            );

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

    // ==================== JobWorker：本体一致性检查 ====================
    @JobWorker(type = "ontology-consistency-check", autoComplete = false)
    public void handleConsistencyCheck(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            BackendService.PatientContext context = patientContexts.get(patientIri);
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

    // ==================== JobWorker：八纲分类（含中文标签） ====================
    @JobWorker(type = "bagang-classification", autoComplete = false)
    public void handleBagangClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            BackendService.PatientContext context = patientContexts.get(patientIri);
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
            bagangResult.put("bagangTypesCn", backendService.resolveLabels(bagangTypes, BASE_NS));

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
                if (chinese != null) result.add(chinese);
            }
        }
        return result;
    }

    // ==================== JobWorker：六经分类（含中文标签） ====================
    @JobWorker(type = "liujing-classification", autoComplete = false)
    public void handleLiujingClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            BackendService.PatientContext context = patientContexts.get(patientIri);
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
                if (isCombined) combinedDiseaseMark = buildCombinedDiseaseMark(liujingTypes);
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sixChannel", sixChannel);
            output.put("sixChannelCn", backendService.resolveLabel(sixChannel, BASE_NS));
            output.put("liujingTypes", liujingTypes);
            output.put("liujingTypesCn", backendService.resolveLabels(liujingTypes, BASE_NS));
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

    private String buildCombinedDiseaseMark(List<String> liujingTypes) {
        if (liujingTypes == null || liujingTypes.size() < 2) return null;

        if (liujingTypes.size() == 3 && liujingTypes.containsAll(List.of("Taiyangbing", "Yangmingbing", "Shaoyangbing")))
            return "三阳合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Shaoyinbing")))
            return "太少两感";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Yangmingbing")))
            return "太阳阳明合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Taiyangbing", "Shaoyangbing")))
            return "太阳少阳合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(List.of("Shaoyangbing", "Yangmingbing")))
            return "少阳阳明合病";

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
        for (String type : sorted) sb.append(nameMap.getOrDefault(type, type));
        sb.append("合病");
        return sb.toString();
    }

    // ==================== JobWorker：方证分类（含中文标签） ====================
    @JobWorker(type = "fangzheng-classification", autoComplete = false)
    public void handleFangzhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            BackendService.PatientContext context = patientContexts.get(patientIri);
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
            Map<String, Integer> necessaryScoreMap = new LinkedHashMap<>();
            Map<String, Integer> possibleScoreMap = new LinkedHashMap<>();

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

            Map<String, Integer> necessaryScoreMapCn = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : necessaryScoreMap.entrySet()) {
                necessaryScoreMapCn.put(backendService.resolveLabel(entry.getKey(), BASE_NS), entry.getValue());
            }
            Map<String, Integer> possibleScoreMapCn = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : possibleScoreMap.entrySet()) {
                possibleScoreMapCn.put(backendService.resolveLabel(entry.getKey(), BASE_NS), entry.getValue());
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("fangzheng", fangzheng);
            output.put("fangzhengCn", backendService.resolveLabel(fangzheng, BASE_NS));
            output.put("fangzhengTypes", fangzhengTypes);
            output.put("candidateFangzhengs", candidateFragments);
            output.put("candidateFangzhengsCn", backendService.resolveLabels(candidateFragments, BASE_NS));
            output.put("candidateNecessaryScores", necessaryScoreMap);
            output.put("candidateNecessaryScoresCn", necessaryScoreMapCn);
            output.put("candidateScores", possibleScoreMap);
            output.put("candidateScoresCn", possibleScoreMapCn);
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

    private List<OWLClass> sortCandidates(List<OWLClass> classes,
                                          Map<String, Integer> necessaryScoreMap,
                                          Map<String, Integer> possibleScoreMap) {
        return classes.stream()
                .sorted((c1, c2) -> {
                    int necessaryCompare = necessaryScoreMap.get(c2.getIRI().getFragment())
                            .compareTo(necessaryScoreMap.get(c1.getIRI().getFragment()));
                    if (necessaryCompare != 0) return necessaryCompare;
                    return possibleScoreMap.get(c2.getIRI().getFragment())
                            .compareTo(possibleScoreMap.get(c1.getIRI().getFragment()));
                })
                .collect(Collectors.toList());
    }

    private Map<String, Integer> buildSortedScoreMap(List<OWLClass> sortedClasses,
                                                     Map<String, Integer> originalScoreMap) {
        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (OWLClass c : sortedClasses) {
            String name = c.getIRI().getFragment();
            sortedMap.put(name, originalScoreMap.get(name));
        }
        return sortedMap;
    }

    private int countNecessaryConditionMatches(BackendService.PatientContext context, OWLClass fangzhengClass, Set<String> patientFacts) {
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
                    if (patientFacts.contains(instanceIri)) count++;
                }
            }
        }
        return count;
    }

    private void collectSomeValuesFillers(OWLClassExpression expr, Set<OWLClassExpression> acc) {
        if (expr instanceof OWLObjectSomeValuesFrom) {
            OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) expr;
            acc.add(some.getFiller());
        } else if (expr instanceof OWLObjectIntersectionOf) {
            for (OWLClassExpression op : ((OWLObjectIntersectionOf) expr).getOperands()) {
                collectSomeValuesFillers(op, acc);
            }
        }
    }

    private Set<IRI> getPossibleSymptomIris(BackendService.PatientContext context, OWLClass fangzhengClass) {
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

    private int countPossibleSymptomMatches(BackendService.PatientContext context, OWLClass fangzhengClass, Set<String> patientFacts) {
        Set<IRI> possibleSymptomIris = getPossibleSymptomIris(context, fangzhengClass);
        int count = 0;
        for (IRI iri : possibleSymptomIris) {
            String iriStr = iri.toString();
            String instanceIri = iriStr.endsWith("_instance") ? iriStr : iriStr + "_instance";
            if (patientFacts.contains(instanceIri)) count++;
        }
        return count;
    }

    // ==================== JobWorker：兼夹证分类 ====================
    @JobWorker(type = "jianjiazheng-classification", autoComplete = false)
    public void handleJianJiaZhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            BackendService.PatientContext context = patientContexts.get(patientIri);
            if (context == null) throw new IllegalStateException("患者上下文不存在: " + patientIri);

            OWLNamedIndividual patient = context.df.getOWLNamedIndividual(IRI.create(patientIri));
            Set<OWLClass> types = context.reasoner.getTypes(patient, false).getFlattened();

            Set<OWLClass> jianJiaSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "JianJiaZheng"));

            List<String> jianJiaTypes = types.stream()
                    .filter(jianJiaSubclasses::contains)
                    .map(c -> c.getIRI().getFragment())
                    .collect(Collectors.toList());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("jianJiaZhengs", jianJiaTypes);
            output.put("jianJiaZhengsCn", backendService.resolveLabels(jianJiaTypes, BASE_NS));
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("兼夹证分类完成: {}", jianJiaTypes);
        } catch (Exception e) {
            log.error("兼夹证分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("JIANJIAZHENG_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ==================== JobWorker：方剂药物推荐 ====================
    @JobWorker(type = "prescription-recommendation", autoComplete = false)
    public void handlePrescriptionRecommendation(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String fangzhengFragment = (String) vars.get("fangzheng");
            if (fangzhengFragment == null || "方证未定".equals(fangzhengFragment)) {
                Map<String, Object> output = new HashMap<>();
                output.put("finalFormula", null);
                output.put("finalFormulaCn", null);
                output.put("herbs", new ArrayList<>());
                output.put("herbsCn", new ArrayList<>());
                output.put("addHerbs", new ArrayList<>());
                output.put("addHerbsCn", new ArrayList<>());
                output.put("warnings", new ArrayList<>());
                client.newCompleteCommand(job.getKey()).variables(output).send().join();
                return;
            }

            String patientIri = (String) vars.get("patientIri");
            BackendService.PatientContext context = patientContexts.get(patientIri);
            if (context == null) throw new IllegalStateException("患者上下文不存在: " + patientIri);

            OWLClass fangzhengClass = context.df.getOWLClass(IRI.create(BASE_NS + fangzhengFragment));
            String formulaIri = extractFormulaFromFangzhengClass(context, fangzhengClass);

            if (formulaIri == null) {
                Map<String, Object> output = new HashMap<>();
                output.put("finalFormula", null);
                output.put("finalFormulaCn", null);
                output.put("herbs", new ArrayList<>());
                output.put("herbsCn", new ArrayList<>());
                output.put("addHerbs", new ArrayList<>());
                output.put("addHerbsCn", new ArrayList<>());
                output.put("warnings", new ArrayList<>());
                client.newCompleteCommand(job.getKey()).variables(output).send().join();
                return;
            }

            OWLNamedIndividual formulaInd = context.df.getOWLNamedIndividual(IRI.create(formulaIri));
            OWLObjectProperty hasIngredientProp = context.df.getOWLObjectProperty(IRI.create(HAS_INGREDIENT));
            Set<OWLNamedIndividual> herbs = context.reasoner.getObjectPropertyValues(formulaInd, hasIngredientProp).getFlattened();
            List<String> herbIris = herbs.stream().map(h -> h.getIRI().toString()).collect(Collectors.toList());

            List<String> addHerbIris = new ArrayList<>();
            List<String> jianJiaZhengs = (List<String>) vars.get("jianJiaZhengs");
            if (jianJiaZhengs != null && !jianJiaZhengs.isEmpty()) {
                for (String jzFragment : jianJiaZhengs) {
                    OWLClass jzClass = context.df.getOWLClass(IRI.create(BASE_NS + jzFragment));
                    Set<IRI> herbsToAdd = getAddHerbIris(context, jzClass);
                    for (IRI herbIri : herbsToAdd) addHerbIris.add(herbIri.toString());
                }
            }

            List<String> warnings = checkIncompatibilities(context, herbIris, addHerbIris);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("finalFormula", formulaIri);
            output.put("finalFormulaCn", backendService.resolveLabel(formulaIri, BASE_NS));
            output.put("candidateFormulas", List.of(formulaIri));
            output.put("herbs", herbIris);
            output.put("herbsCn", backendService.resolveLabels(herbIris, BASE_NS));
            output.put("addHerbs", addHerbIris);
            output.put("addHerbsCn", backendService.resolveLabels(addHerbIris, BASE_NS));
            output.put("warnings", warnings);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("方剂推荐完成: {}，药物: {}, 加减建议: {}, 配伍禁忌警告: {}",
                    formulaIri, herbIris, addHerbIris, warnings);
        } catch (Exception e) {
            log.error("方剂推荐失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("PRESCRIPTION_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    private Set<IRI> getAddHerbIris(BackendService.PatientContext context, OWLClass cls) {
        Set<IRI> result = new HashSet<>();
        OWLOntology ont = context.ontology;
        for (OWLAnnotationAssertionAxiom ax : ont.getAnnotationAssertionAxioms(cls.getIRI())) {
            if (ax.getProperty().getIRI().getFragment().equals("addHerb") &&
                    ax.getValue() instanceof IRI) {
                result.add((IRI) ax.getValue());
            }
        }
        return result;
    }

    // ==================== JobWorker：诊断解释 ====================
    @JobWorker(type = "diagnosis-explanation", autoComplete = false)
    public void handleDiagnosisExplanation(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String sixChannel = (String) vars.get("sixChannel");
            String fangzheng = (String) vars.get("fangzheng");
            String finalFormula = (String) vars.get("finalFormula");
            String patientIri = (String) vars.get("patientIri");
            String combinedDiseaseMark = (String) vars.get("combinedDiseaseMark");

            String sixChannelCn = (String) vars.get("sixChannelCn");
            String fangzhengCn = (String) vars.get("fangzhengCn");
            String finalFormulaCn = (String) vars.get("finalFormulaCn");

            String liujingDisplay = (combinedDiseaseMark != null && !combinedDiseaseMark.isEmpty())
                    ? combinedDiseaseMark
                    : (sixChannelCn != null ? sixChannelCn : sixChannel);
            String fangzhengDisplay = fangzhengCn != null ? fangzhengCn : fangzheng;
            String formulaDisplay = finalFormulaCn != null ? finalFormulaCn : finalFormula;

            String explanation = String.format(
                    "六经：%s，方证：%s，推荐方剂：%s。",
                    liujingDisplay,
                    fangzhengDisplay != null ? fangzhengDisplay : "未定",
                    formulaDisplay != null ? formulaDisplay : "未定");

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("explanation", explanation);
            client.newCompleteCommand(job.getKey()).variables(output).send().join();
            log.info("诊断解释完成：{}", explanation);

            if (patientIri != null) {
                BackendService.PatientContext context = patientContexts.remove(patientIri);
                if (context != null) context.dispose();
            }
        } catch (Exception e) {
            log.error("诊断解释失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("EXPLANATION_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    private List<String> checkIncompatibilities(BackendService.PatientContext context,
                                                List<String> herbIris,
                                                List<String> addHerbIris) {
        List<String> warnings = new ArrayList<>();
        Set<String> allHerbIris = new HashSet<>();
        allHerbIris.addAll(herbIris);
        allHerbIris.addAll(addHerbIris);
        if (allHerbIris.size() < 2) return warnings;

        OWLDataFactory df = context.df;
        OWLReasoner reasoner = context.reasoner;
        OWLObjectProperty antagonisticProp = df.getOWLObjectProperty(IRI.create(ANTAGONISTIC));
        OWLObjectProperty fearingProp = df.getOWLObjectProperty(IRI.create(FEARING));

        List<OWLNamedIndividual> herbIndividuals = allHerbIris.stream()
                .map(iri -> df.getOWLNamedIndividual(IRI.create(iri)))
                .collect(Collectors.toList());

        for (int i = 0; i < herbIndividuals.size(); i++) {
            for (int j = i + 1; j < herbIndividuals.size(); j++) {
                OWLNamedIndividual herb1 = herbIndividuals.get(i);
                OWLNamedIndividual herb2 = herbIndividuals.get(j);

                Set<OWLNamedIndividual> antagValues = reasoner
                        .getObjectPropertyValues(herb1, antagonisticProp).getFlattened();
                if (antagValues.contains(herb2)) {
                    warnings.add(String.format("十八反：%s 反 %s",
                            backendService.resolveLabel(herb1.getIRI().toString(), BASE_NS),
                            backendService.resolveLabel(herb2.getIRI().toString(), BASE_NS)));
                }

                Set<OWLNamedIndividual> fearValues = reasoner
                        .getObjectPropertyValues(herb1, fearingProp).getFlattened();
                if (fearValues.contains(herb2)) {
                    warnings.add(String.format("十九畏：%s 畏 %s",
                            backendService.resolveLabel(herb1.getIRI().toString(), BASE_NS),
                            backendService.resolveLabel(herb2.getIRI().toString(), BASE_NS)));
                }
            }
        }
        return warnings;
    }

    private List<String> getList(Map<String, Object> vars, String key) {
        Object val = vars.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String extractFormulaFromFangzhengClass(BackendService.PatientContext context, OWLClass fangzhengClass) {
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
                if (ind.isNamed()) return ind.asOWLNamedIndividual().getIRI().toString();
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