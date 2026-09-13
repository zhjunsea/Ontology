package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.ObdaQueryUtils;
import com.ocean.openlletresolver.*;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
    private OWLDataFactory tboxDf;

    // ==================== 分类元数据 ====================
    private Set<OWLClass> bagangSubclasses;
    private Set<OWLClass> liujingSubclasses;
    private Set<OWLClass> fangzhengSubclasses;
    private Set<OWLClass> jianJiaSubclasses;

    /** 方证 → 八纲集合缓存 */
    private Map<OWLClass, Set<OWLClass>> fangzhengBagangMap = new HashMap<>();

    /** 症状 → 包含该症状限制的方证集合 */
    private Map<String, Set<OWLClass>> symptomToFangzhengIndex;

    /** 方证 → 必需 fragment 总数 */
    private Map<OWLClass, Integer> fangzhengRequiredCount = new HashMap<>();

    /** 复合脉 → 组成原子脉 */
    private Map<OWLClass, Set<OWLClass>> compositePulseMap = new HashMap<>();

    /** 复合症状 → 组成原子症状 */
    private Map<OWLClass, Set<OWLClass>> compositeSymptomMap = new HashMap<>();

    /** 十八反 */
    private Map<String, Set<String>> shibafanMap = new HashMap<>();

    /** 十九畏 */
    private Map<String, Set<String>> shijiuweiMap = new HashMap<>();

    /** 药物 fragment → 中文 label */
    private Map<String, String> yaowuLabelMap = new HashMap<>();

    /** 方证 → 六经集合 */
    private Map<OWLClass, Set<OWLClass>> fangzhengLiujingMap = new HashMap<>();

    private static final int FALLBACK_TOP_N = 20;
    private static final int CANDIDATE_DISPLAY_TOP_N = 5;

    private static final Set<String> SIX_CHANNEL_WHITELIST = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing"
    );

    private static final Set<String> TOP_LEVEL_CLASSES = Set.of(
            "Huanzhe", "SizhenXinxi", "Zhengzhuang", "Maixiang",
            "Shexiang", "Fuzheng", "Tizhi", "Bagang", "Liujingbing",
            "Fangzheng", "Fangji", "Yaowu", "Yaozheng", "JianJiaZheng"
    );

    // ==================== 缓存 ====================
    private final MiniReasoningContextManager miniCtxMgr = new MiniReasoningContextManager();
    private final Map<String, PatientInput> patientInputs = new ConcurrentHashMap<>();
    private final Map<String, Set<OWLClass>> fangzhengCandidatesCache = new ConcurrentHashMap<>();

    // ==================== 常量 ====================
    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";
    private static final String HAS_SYMPTOM = BASE_NS + "you_zhengzhuang";
    private static final String HAS_PULSE = BASE_NS + "you_maixiang";
    private static final String HAS_TONGUE = BASE_NS + "you_shexiang";
    private static final String HAS_ABDOMINAL = BASE_NS + "you_fuzheng";
    private static final String HAS_PRESCRIPTION = BASE_NS + "you_chufang";

    private static final String STAGE_LJ = "#STAGE_LJ";
    private static final String STAGE_FZ = "#STAGE_FZ";
    private static final String INSTANCE_SUFFIX = "_instance";

    private static final Map<String, String> LIUJING_MUTEX_PAIRS = Map.of(
            "Shaoyangbing", "Jueyinbing"
    );

    // ==================== 内部类型 ====================

    private static class PatientInput {
        final String patientIri;
        final List<String> symptomIris;
        final List<String> pulseIris;
        final List<String> tongueIris;
        final List<String> fuzhengIris;

        PatientInput(String p, List<String> s, List<String> pl, List<String> t, List<String> f) {
            patientIri = p;
            symptomIris = s;
            pulseIris = pl;
            tongueIris = t;
            fuzhengIris = f;
        }
    }

    private static class ScoredFangzheng {
        final OWLClass cls;
        final int hits;
        final int required;

        ScoredFangzheng(OWLClass cls, int hits, int required) {
            this.cls = cls;
            this.hits = hits;
            this.required = required;
        }

        String fragment() { return cls.getIRI().getFragment(); }
        double ratio() { return required == 0 ? 0.0 : (double) hits / required; }

        @Override
        public String toString() {
            return String.format("%s(hits=%d/%d, ratio=%.2f)",
                    fragment(), hits, required, ratio());
        }
    }

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        try {
            log.info("==================== 初始化开始 ====================");
            log.info("初始化 TCMOntologyJobWorker（模式: 两阶段 + 精确抽取 + 递归闭包）...");

            long tBackend = System.currentTimeMillis();
            com.ocean.ontopobdahandler.OBDAHandler.init(obdaPropertiesPath, obdaPath);
            backendService = BackendService.getInstance(
                    mainOntologyPath,
                    com.ocean.ontopobdahandler.OBDAHandler.getInstance());
            queryService = new QueryService(backendService);
            tboxDf = backendService.getOntologyService().gettBoxOntology()
                    .getOWLOntologyManager().getOWLDataFactory();
            log.info("[init] BackendService 初始化完成，耗时 {} ms",
                    System.currentTimeMillis() - tBackend);

            long tMeta = System.currentTimeMillis();
            liujingSubclasses = backendService.getAllNamedSubclasses(
                    IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = backendService.getAllNamedSubclasses(
                    IRI.create(BASE_NS + "Fangzheng"));
            jianJiaSubclasses = backendService.getAllNamedSubclasses(
                    IRI.create(BASE_NS + "JianJiaZheng"));
            bagangSubclasses = backendService.getDirectNamedSubclasses(
                    IRI.create(BASE_NS + "Bagang"));

            log.info("[init] Bagang 直接子类 {} 个: {}",
                    bagangSubclasses.size(),
                    bagangSubclasses.stream()
                            .map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()));
            log.info("[init] 元数据扫描完成，耗时 {} ms", System.currentTimeMillis() - tMeta);
            log.info("八纲子类={}(已过滤方证/六经/兼夹), 六经全部子树={}, 方证={}, 兼夹证={}",
                    bagangSubclasses.size(), liujingSubclasses.size(),
                    fangzhengSubclasses.size(), jianJiaSubclasses.size());

            long tIdx = System.currentTimeMillis();
            buildSymptomIndex();
            log.info("[init] 症状-方证倒排索引构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tIdx);

            long tFzBagang = System.currentTimeMillis();
            buildFangzhengBagangMap();
            log.info("[init] 方证-八纲缓存构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tFzBagang);

            long tComp = System.currentTimeMillis();
            buildCompositeMaps();
            log.info("[init] 复合-原子映射构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tComp);

            long tInc = System.currentTimeMillis();
            loadIncompatibilitiesFromObda();
            log.info("[init] 配伍禁忌索引构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tInc);

            long tFzLj = System.currentTimeMillis();
            buildFangzhengLiujingMap();
            log.info("[init] 方证-六经缓存构建完成，耗时 {} ms",
                    System.currentTimeMillis() - tFzLj);

            log.info("==================== 初始化完成 ====================");
        } catch (Exception e) {
            log.error("初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    // ============================================================
    // 方证-八纲缓存构建（通用化：OntologyModuleUtils.findRelatedClasses）
    // ============================================================

    private void buildFangzhengBagangMap() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<OWLClass, Set<OWLClass>> map = new HashMap<>();

        for (OWLClass fz : fangzhengSubclasses) {
            Set<OWLClass> bagangs =
                    OntologyModuleUtils.findRelatedClasses(tbox, fz, bagangSubclasses);
            if (!bagangs.isEmpty()) map.put(fz, bagangs);
        }

        fangzhengBagangMap = map;
        log.info("[init] 方证-八纲缓存: {} 个方证有八纲归属", map.size());

        map.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getIRI().getFragment()))
                .limit(5)
                .forEach(e -> log.info("[init] 抽样: {} → {}",
                        e.getKey().getIRI().getFragment(),
                        e.getValue().stream().map(c -> c.getIRI().getFragment())
                                .sorted().collect(Collectors.toList())));
    }

    // ============================================================
    // 症状-方证倒排索引
    // ============================================================

    private void buildSymptomIndex() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<String, Set<OWLClass>> index = new HashMap<>();
        Map<OWLClass, Integer> requiredCount = new HashMap<>();
        Set<IRI> propIris = Set.of(
                IRI.create(HAS_SYMPTOM),
                IRI.create(HAS_PULSE),
                IRI.create(HAS_TONGUE),
                IRI.create(HAS_ABDOMINAL));

        int covered = 0;
        for (OWLClass fz : fangzhengSubclasses) {
            Set<String> syms = OntologyModuleUtils.collectRestrictionFillers(tbox, fz, propIris);
            if (!syms.isEmpty()) covered++;
            requiredCount.put(fz, syms.size());
            for (String s : syms) {
                index.computeIfAbsent(s, k -> new HashSet<>()).add(fz);
            }
        }
        symptomToFangzhengIndex = index;
        fangzhengRequiredCount = requiredCount;
        log.info("[init] 症状-方证索引: 键数={}, 覆盖方证={}/{}",
                index.size(), covered, fangzhengSubclasses.size());
    }

    private Set<OWLClass> topBySymptomOverlap(Set<String> patientSymptoms, int topN) {
        if (symptomToFangzhengIndex == null
                || patientSymptoms == null || patientSymptoms.isEmpty()) {
            return Collections.emptySet();
        }
        Map<OWLClass, Integer> scores = new HashMap<>();
        for (String sym : patientSymptoms) {
            Set<OWLClass> fzs = symptomToFangzhengIndex.get(sym);
            if (fzs == null) continue;
            for (OWLClass fz : fzs) scores.merge(fz, 1, Integer::sum);
        }
        return scores.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    if (c != 0) return c;
                    return a.getKey().getIRI().getFragment()
                            .compareTo(b.getKey().getIRI().getFragment());
                })
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<ScoredFangzheng> rankCandidatesWithScores(
            Set<OWLClass> candidates, Set<String> patientSymptoms, int topN) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();

        if (patientSymptoms == null || patientSymptoms.isEmpty()) {
            return candidates.stream()
                    .sorted(Comparator.comparing(c -> c.getIRI().getFragment()))
                    .limit(topN)
                    .map(c -> new ScoredFangzheng(c, 0,
                            fangzhengRequiredCount.getOrDefault(c, 0)))
                    .collect(Collectors.toList());
        }

        Map<OWLClass, Integer> hitCounts = new HashMap<>();
        for (String sym : patientSymptoms) {
            Set<OWLClass> fzs = symptomToFangzhengIndex.get(sym);
            if (fzs == null) continue;
            for (OWLClass fz : fzs) {
                if (candidates.contains(fz)) {
                    hitCounts.merge(fz, 1, Integer::sum);
                }
            }
        }

        return candidates.stream()
                .map(c -> new ScoredFangzheng(c,
                        hitCounts.getOrDefault(c, 0),
                        fangzhengRequiredCount.getOrDefault(c, 0)))
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.hits, a.hits);
                    if (cmp != 0) return cmp;
                    cmp = Double.compare(b.ratio(), a.ratio());
                    if (cmp != 0) return cmp;
                    return a.fragment().compareTo(b.fragment());
                })
                .limit(topN)
                .collect(Collectors.toList());
    }

    private boolean hasBagangOrLiujing(Set<OWLClass> stage1Types) {
        if (stage1Types == null || stage1Types.isEmpty()) return false;
        for (OWLClass c : stage1Types) {
            if (bagangSubclasses.contains(c) || liujingSubclasses.contains(c)) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 复合-原子映射（通用化）
    // ============================================================

    private void buildCompositeMaps() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        IRI pulseIri = IRI.create(BASE_NS + "Maixiang");
        IRI symIri = IRI.create(BASE_NS + "Zhengzhuang");

        compositePulseMap = OntologyModuleUtils.buildIntersectionCompositeMap(
                tbox, pulseIri, backendService.getAllNamedSubclasses(pulseIri));
        compositeSymptomMap = OntologyModuleUtils.buildIntersectionCompositeMap(
                tbox, symIri, backendService.getAllNamedSubclasses(symIri));

        log.info("[init] 复合脉映射: {} 个", compositePulseMap.size());
        log.info("[init] 复合症状映射: {} 个", compositeSymptomMap.size());
    }

    // ============================================================
    // 十八反/十九畏（通用化 OBDA 工具）
    // ============================================================

    private void loadIncompatibilitiesFromObda() {
        try {
            String yaowuQ = """
                PREFIX : <http://www.tcm-classics.org/jingfang#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?iri ?label WHERE {
                    ?iri a :Yaowu .
                    OPTIONAL { ?iri rdfs:label ?label . }
                }
                """;
            for (Map<String, String> row :
                    backendService.getObdaHandler().executeAboxQuery(yaowuQ)) {
                String iri = row.get("iri");
                String label = row.get("label");
                if (iri == null) continue;
                String frag = ObdaQueryUtils.fragmentOf(iri);
                if (frag == null) continue;
                yaowuLabelMap.put(frag,
                        (label != null && !label.isBlank()) ? label.trim() : frag);
            }

            String antQ = """
                PREFIX : <http://www.tcm-classics.org/jingfang#>
                SELECT ?a ?b WHERE { ?a :antagonistic ?b . }
                """;
            shibafanMap = ObdaQueryUtils.loadUndirectedRelationFromObda(
                    backendService.getObdaHandler(), antQ);

            String fearQ = """
                PREFIX : <http://www.tcm-classics.org/jingfang#>
                SELECT ?a ?b WHERE { ?a :fearing ?b . }
                """;
            shijiuweiMap = ObdaQueryUtils.loadUndirectedRelationFromObda(
                    backendService.getObdaHandler(), fearQ);

            log.info("[init] 十八反: {} 种药物, {} 组关系",
                    shibafanMap.size(),
                    shibafanMap.values().stream().mapToInt(Set::size).sum() / 2);
            log.info("[init] 十九畏: {} 种药物, {} 组关系",
                    shijiuweiMap.size(),
                    shijiuweiMap.values().stream().mapToInt(Set::size).sum() / 2);
            log.info("[init] 药物 label 映射: {} 条", yaowuLabelMap.size());

        } catch (Exception e) {
            log.error("[init] 通过 OBDA 加载配伍禁忌失败", e);
            shibafanMap = Collections.emptyMap();
            shijiuweiMap = Collections.emptyMap();
            yaowuLabelMap = Collections.emptyMap();
        }
    }

    private String labelOf(String fragment) {
        return yaowuLabelMap.getOrDefault(fragment, fragment);
    }

    // ============================================================
    // 阶段 1：六经 + 兼夹证
    // ============================================================

    private Set<OWLAxiom> extractLiujingModule(PatientInput input) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLClass> initial = new HashSet<>();

        Set<String> patientFrags = collectPatientFrags(input);
        for (String f : patientFrags) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }

        for (String six : SIX_CHANNEL_WHITELIST) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + six)));
        }

        initial.addAll(bagangSubclasses);
        initial.addAll(jianJiaSubclasses);

        for (String t : TOP_LEVEL_CLASSES) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + t)));
        }

        initial.addAll(OntologyModuleUtils.collectIndividualTypes(
                tbox, collectAllIndividualIris(input), BASE_NS));

        Set<OWLClass> keepClasses = OntologyModuleUtils.collectClassClosure(tbox, initial);
        Set<OWLAxiom> tboxOnly = OntologyModuleUtils.extractTBoxModule(tbox, keepClasses);

        log.info("[阶段1] 患者症状={}, 初始={}, 闭包={}, mini 公理={}",
                patientFrags.size(), initial.size(), keepClasses.size(), tboxOnly.size());
        return tboxOnly;
    }

    // ============================================================
    // 阶段 2：方证
    // ============================================================

    private Set<OWLAxiom> extractFangzhengModule(PatientInput input,
                                                 Set<OWLClass> stage1Types) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLClass> initial = new HashSet<>();

        Set<String> patientFrags = collectPatientFrags(input);
        for (String f : patientFrags) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }

        Set<OWLClass> relevantFangzheng = filterFangzheng(tbox, stage1Types, patientFrags);
        fangzhengCandidatesCache.put(input.patientIri, relevantFangzheng);
        initial.addAll(relevantFangzheng);

        for (String t : TOP_LEVEL_CLASSES) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + t)));
        }

        initial.addAll(OntologyModuleUtils.collectIndividualTypes(
                tbox, collectAllIndividualIris(input), BASE_NS));

        Set<OWLClass> keepClasses = OntologyModuleUtils.collectClassClosure(tbox, initial);
        Set<OWLAxiom> tboxOnly = OntologyModuleUtils.extractTBoxModule(tbox, keepClasses);

        log.info("[阶段2] 筛选方证={}/{}, 初始={}, 闭包={}, mini 公理={}",
                relevantFangzheng.size(), fangzhengSubclasses.size(),
                initial.size(), keepClasses.size(), tboxOnly.size());
        return tboxOnly;
    }

    private List<String> collectAllIndividualIris(PatientInput input) {
        List<String> all = new ArrayList<>();
        all.addAll(input.symptomIris);
        all.addAll(input.pulseIris);
        all.addAll(input.tongueIris);
        all.addAll(input.fuzhengIris);
        return all;
    }

    private Set<String> collectPatientFrags(PatientInput input) {
        Set<String> frags = new HashSet<>();
        input.symptomIris.forEach(i -> frags.add(frag(i)));
        input.pulseIris.forEach(i -> frags.add(frag(i)));
        input.tongueIris.forEach(i -> frags.add(frag(i)));
        input.fuzhengIris.forEach(i -> frags.add(frag(i)));
        return frags;
    }

    private Set<OWLClass> filterFangzheng(OWLOntology tbox,
                                          Set<OWLClass> stage1Types,
                                          Set<String> patientSymptomFrags) {
        Set<OWLClass> result = new HashSet<>();
        if (fangzhengSubclasses == null) return result;

        if (!hasBagangOrLiujing(stage1Types)) {
            Set<OWLClass> direct = topBySymptomOverlap(patientSymptomFrags, FALLBACK_TOP_N);
            log.info("[阶段2] 八纲/六经为空，症状直配得 {} 个方证", direct.size());
            if (!direct.isEmpty()) return direct;
            log.warn("[阶段2] 症状直配为空，回退全部方证（{} 个）",
                    fangzhengSubclasses.size());
            result.addAll(fangzhengSubclasses);
            return result;
        }

        Set<OWLClass> symptomMatching = new HashSet<>();
        for (String sym : patientSymptomFrags) {
            Set<OWLClass> fzs = symptomToFangzhengIndex.get(sym);
            if (fzs != null) symptomMatching.addAll(fzs);
        }

        Set<OWLClass> stage1Extended = buildStage1Extended(tbox, stage1Types);
        Set<OWLClass> categoryMatching = new HashSet<>();
        for (OWLClass fz : fangzhengSubclasses) {
            if (matchesCategory(tbox, fz, stage1Extended)) {
                categoryMatching.add(fz);
            }
        }

        Set<OWLClass> intersection = new HashSet<>(symptomMatching);
        intersection.retainAll(categoryMatching);
        if (!intersection.isEmpty()) {
            log.info("[阶段2] 症状∩类别 命中 {} 个", intersection.size());
            return intersection;
        }

        if (!symptomMatching.isEmpty()) {
            log.warn("[阶段2] 症状∩类别为空，降级为仅症状命中 {} 个", symptomMatching.size());
            return symptomMatching;
        }

        if (!categoryMatching.isEmpty()) {
            log.warn("[阶段2] 症状命中为空，降级为仅类别命中 {} 个", categoryMatching.size());
            return categoryMatching;
        }

        log.warn("[阶段2] 全部为空，回退全部方证 {} 个", fangzhengSubclasses.size());
        result.addAll(fangzhengSubclasses);
        return result;
    }

    private Set<OWLClass> buildStage1Extended(OWLOntology tbox, Set<OWLClass> stage1Types) {
        Set<OWLClass> extended = new HashSet<>(stage1Types);
        for (OWLClass c : stage1Types) {
            for (OWLSubClassOfAxiom ax :
                    tbox.subClassAxiomsForSubClass(c).collect(Collectors.toList())) {
                ax.getSuperClass().classesInSignature()
                        .filter(x -> !x.isOWLThing() && !x.isOWLNothing())
                        .forEach(extended::add);
            }
            for (OWLEquivalentClassesAxiom ax :
                    tbox.equivalentClassesAxioms(c).collect(Collectors.toList())) {
                for (OWLClassExpression e : ax.getClassExpressions()) {
                    if (e.isOWLClass() && e.asOWLClass().equals(c)) continue;
                    e.classesInSignature()
                            .filter(x -> !x.isOWLThing() && !x.isOWLNothing())
                            .forEach(extended::add);
                }
            }
        }
        return extended;
    }

    private boolean matchesCategory(OWLOntology tbox,
                                    OWLClass fz,
                                    Set<OWLClass> stage1Extended) {
        for (OWLEquivalentClassesAxiom ax :
                tbox.equivalentClassesAxioms(fz).collect(Collectors.toList())) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.isOWLClass() && e.asOWLClass().equals(fz)) continue;
                if (e.classesInSignature().anyMatch(stage1Extended::contains)) {
                    return true;
                }
            }
        }
        for (OWLSubClassOfAxiom ax :
                tbox.subClassAxiomsForSubClass(fz).collect(Collectors.toList())) {
            if (ax.getSuperClass().classesInSignature()
                    .anyMatch(stage1Extended::contains)) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 上下文构建（走 MiniReasoningContextManager）
    // ============================================================

    private <T> T withLiujingReasoner(PatientInput input, Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri + STAGE_LJ;
        return miniCtxMgr.withContext(
                cacheKey,
                () -> extractLiujingModule(input),
                df -> buildPatientAxioms(df, input),
                action);
    }

    private <T> T withFangzhengReasoner(PatientInput input,
                                        Set<OWLClass> stage1Types,
                                        Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri + STAGE_FZ;
        return miniCtxMgr.withContext(
                cacheKey,
                () -> extractFangzhengModule(input, stage1Types),
                df -> buildPatientAxioms(df, input),
                action);
    }

    // ==================== 患者 ABox ====================

    private Set<OWLAxiom> buildPatientAxioms(OWLDataFactory df, PatientInput input) {
        Set<OWLAxiom> axioms = new HashSet<>();
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(input.patientIri));
        axioms.add(df.getOWLClassAssertionAxiom(
                df.getOWLClass(IRI.create(BASE_NS + "Huanzhe")), patient));

        OWLObjectProperty hasSymptom = df.getOWLObjectProperty(IRI.create(HAS_SYMPTOM));
        OWLObjectProperty hasPulse = df.getOWLObjectProperty(IRI.create(HAS_PULSE));
        OWLObjectProperty hasTongue = df.getOWLObjectProperty(IRI.create(HAS_TONGUE));
        OWLObjectProperty hasAbdominal = df.getOWLObjectProperty(IRI.create(HAS_ABDOMINAL));

        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                tbox, df, axioms, hasSymptom, patient, input.symptomIris, BASE_NS);
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                tbox, df, axioms, hasPulse, patient, input.pulseIris, BASE_NS);
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                tbox, df, axioms, hasTongue, patient, input.tongueIris, BASE_NS);
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                tbox, df, axioms, hasAbdominal, patient, input.fuzhengIris, BASE_NS);

        OntologyModuleUtils.addSynthesizedComposites(
                df, axioms, patient, input.symptomIris, hasSymptom,
                compositeSymptomMap, BASE_NS, INSTANCE_SUFFIX, log);
        OntologyModuleUtils.addSynthesizedComposites(
                df, axioms, patient, input.pulseIris, hasPulse,
                compositePulseMap, BASE_NS, INSTANCE_SUFFIX, log);
        OntologyModuleUtils.addSynthesizedComposites(
                df, axioms, patient, input.tongueIris, hasTongue,
                null, BASE_NS, INSTANCE_SUFFIX, log);
        OntologyModuleUtils.addSynthesizedComposites(
                df, axioms, patient, input.fuzhengIris, hasAbdominal,
                null, BASE_NS, INSTANCE_SUFFIX, log);

        return axioms;
    }

    // ============================================================
    // JobWorkers
    // ============================================================

    @JobWorker(type = "sizhen-input", autoComplete = false)
    public void handleSizhenInput(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patientIri = BASE_NS + "Patient_" + job.getKey();
            clearPatientCache(patientIri);

            PatientInput input = new PatientInput(
                    patientIri,
                    ObdaQueryUtils.getList(vars, "symptomIris"),
                    ObdaQueryUtils.getList(vars, "pulseIris"),
                    ObdaQueryUtils.getList(vars, "tongueIris"),
                    ObdaQueryUtils.getList(vars, "fuzhengIris"));

            boolean consistent = withLiujingReasoner(input, MiniContext::isConsistent);
            patientInputs.put(patientIri, input);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("patientIri", patientIri);
            out.put("recorded", consistent);
            out.put("inconsistent", !consistent);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("四诊录入完成: {} 一致={}", patientIri, consistent);
        } catch (Exception e) {
            log.error("sizhen-input 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("SIZHEN_INPUT_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    @JobWorker(type = "ontology-consistency-check", autoComplete = false)
    public void handleConsistencyCheck(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            boolean consistent = withLiujingReasoner(input, MiniContext::isConsistent);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("consistent", consistent);
            out.put("unsatisfiableClasses", Collections.emptyList());
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("一致性检查: {}", consistent);
        } catch (Exception e) {
            log.error("一致性检查失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("CONSISTENCY_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    /**
     * 八纲维度归属（元知识，中医基础理论定义）。
     */
    private String bagangDimensionOf(String fragment) {
        if (fragment == null || fragment.isEmpty()) return null;
        switch (fragment.toLowerCase()) {
            case "biao":
            case "li":
            case "banbiaobanli":
                return "表里";
            case "han":
            case "re":
                return "寒热";
            case "xu":
            case "shi":
                return "虚实";
            case "yin":
            case "yang":
                return "阴阳";
            default:
                return null;
        }
    }

    @JobWorker(type = "bagang-classification", autoComplete = false)
    public void handleBagangClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            Set<OWLClass> patientTypes = withLiujingReasoner(
                    input, ctx -> ctx.getTypes(input.patientIri));
            Set<OWLClass> bagangFromPatient = patientTypes.stream()
                    .filter(bagangSubclasses::contains)
                    .collect(Collectors.toSet());

            List<String> realizedFz = withFangzhengReasoner(input, patientTypes, ctx ->
                    OntologyModuleUtils.extractFragmentsByMetaClass(
                            ctx.getTypes(input.patientIri), fangzhengSubclasses));

            List<String> fzForBagang;
            if (!realizedFz.isEmpty()) {
                Set<OWLClass> realizedClasses = realizedFz.stream()
                        .map(f -> tboxDf.getOWLClass(IRI.create(BASE_NS + f)))
                        .collect(Collectors.toSet());
                Set<String> patientFrags = collectPatientFrags(input);
                List<ScoredFangzheng> ranked = rankCandidatesWithScores(
                        realizedClasses, patientFrags, 1);
                fzForBagang = ranked.isEmpty()
                        ? realizedFz
                        : List.of(ranked.get(0).fragment());
            } else {
                Set<OWLClass> cached = fangzhengCandidatesCache
                        .getOrDefault(patientIri, Collections.emptySet());
                Set<String> patientFrags = collectPatientFrags(input);
                List<ScoredFangzheng> ranked = rankCandidatesWithScores(cached, patientFrags, 1);
                if (!ranked.isEmpty()) {
                    fzForBagang = List.of(ranked.get(0).fragment());
                    log.warn("[八纲] realize 无匹配，兜底用 Top1 方证 {} 读八纲", fzForBagang.get(0));
                } else {
                    fzForBagang = Collections.emptyList();
                    log.warn("[八纲] realize 无匹配且候选集为空，无法读方证八纲");
                }
            }

            Set<OWLClass> bagangFromFz = new HashSet<>();
            for (String frag : fzForBagang) {
                OWLClass fzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + frag));
                Set<OWLClass> bs = fangzhengBagangMap.get(fzCls);
                if (bs != null && !bs.isEmpty()) {
                    bagangFromFz.addAll(bs);
                } else {
                    Set<OWLClass> live = OntologyModuleUtils.findRelatedClasses(
                            backendService.getOntologyService().gettBoxOntology(),
                            fzCls, bagangSubclasses);
                    if (!live.isEmpty()) {
                        bagangFromFz.addAll(live);
                        log.warn("[八纲兜底] {} 缓存 miss, 实时读 → {}", frag,
                                live.stream().map(c -> c.getIRI().getFragment())
                                        .sorted().collect(Collectors.toList()));
                    } else {
                        log.warn("[八纲兜底] {} 缓存 miss 且本体无八纲声明", frag);
                    }
                }
            }

            Set<OWLClass> allBagang = new HashSet<>(bagangFromPatient);
            allBagang.addAll(bagangFromFz);

            log.info("八纲来源: 患者直接={}, 命中方证={} (方证={})",
                    bagangFromPatient.stream().map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()),
                    bagangFromFz.stream().map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()),
                    fzForBagang);

            List<OWLClass> sortedBagang = allBagang.stream()
                    .sorted(Comparator.comparing(c -> c.getIRI().getFragment()))
                    .collect(Collectors.toList());

            List<String> bagangFragments = sortedBagang.stream()
                    .map(c -> c.getIRI().getFragment())
                    .collect(Collectors.toList());
            List<String> bagangTypesCn = sortedBagang.stream()
                    .map(this::resolveClassLabel)
                    .collect(Collectors.toList());

            Map<String, List<String>> grouped = new LinkedHashMap<>();
            grouped.put("表里", new ArrayList<>());
            grouped.put("寒热", new ArrayList<>());
            grouped.put("虚实", new ArrayList<>());
            grouped.put("阴阳", new ArrayList<>());

            for (OWLClass c : sortedBagang) {
                String frag = c.getIRI().getFragment();
                String dim = bagangDimensionOf(frag);
                if (dim != null) {
                    grouped.get(dim).add(resolveClassLabel(c));
                } else {
                    log.warn("[八纲分组] 未知维度 fragment: {}", frag);
                }
            }

            List<String> biaoli = grouped.get("表里");
            List<String> hanre = grouped.get("寒热");
            List<String> xushi = grouped.get("虚实");
            List<String> yinyang = grouped.get("阴阳");

            Map<String, Object> bagangResult = new LinkedHashMap<>();
            bagangResult.put("表里", biaoli);
            bagangResult.put("寒热", hanre);
            bagangResult.put("虚实", xushi);
            bagangResult.put("阴阳", yinyang);
            bagangResult.put("bagangTypes", bagangFragments);
            bagangResult.put("bagangTypesCn", bagangTypesCn);
            bagangResult.put("complete",
                    !biaoli.isEmpty() && !hanre.isEmpty()
                            && !xushi.isEmpty() && !yinyang.isEmpty());

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("bagangResult", bagangResult)).send().join();
            log.info("八纲完成: {}", bagangResult);
        } catch (Exception e) {
            log.error("八纲失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("BAGANG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    @JobWorker(type = "liujing-classification", autoComplete = false)
    public void handleLiujingClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            Set<String> fromPatient = withLiujingReasoner(input, ctx -> {
                Set<OWLClass> allTypes = ctx.getTypes(input.patientIri);
                return allTypes.stream()
                        .filter(c -> SIX_CHANNEL_WHITELIST.contains(c.getIRI().getFragment()))
                        .map(c -> c.getIRI().getFragment())
                        .collect(Collectors.toSet());
            });

            Set<OWLClass> stage1Types = withLiujingReasoner(
                    input, ctx -> ctx.getTypes(input.patientIri));
            List<String> realizedFz = withFangzhengReasoner(input, stage1Types, ctx ->
                    OntologyModuleUtils.extractFragmentsByMetaClass(
                            ctx.getTypes(input.patientIri), fangzhengSubclasses));

            List<String> fzForLiujing = realizedFz;
            if (fzForLiujing.isEmpty()) {
                Set<OWLClass> cached = fangzhengCandidatesCache
                        .getOrDefault(patientIri, Collections.emptySet());
                List<ScoredFangzheng> ranked = rankCandidatesWithScores(
                        cached, collectPatientFrags(input), 1);
                if (!ranked.isEmpty()) {
                    fzForLiujing = List.of(ranked.get(0).fragment());
                    log.warn("[六经] realize 无匹配，兜底用 Top1 方证 {} 反推六经",
                            fzForLiujing.get(0));
                }
            }

            Set<String> fromFz = new HashSet<>();
            OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
            for (String frag : fzForLiujing) {
                OWLClass fzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + frag));
                Set<OWLClass> cached = fangzhengLiujingMap.get(fzCls);
                Set<OWLClass> ljs = (cached != null && !cached.isEmpty())
                        ? cached
                        : OntologyModuleUtils.findRelatedClasses(tbox, fzCls, liujingSubclasses);
                for (OWLClass c : ljs) {
                    String frag2 = c.getIRI().getFragment();
                    if (SIX_CHANNEL_WHITELIST.contains(frag2)) {
                        fromFz.add(frag2);
                    }
                }
            }

            Set<String> allLiujing = new HashSet<>(fromPatient);
            allLiujing.addAll(fromFz);
            List<String> mergedLiujing = allLiujing.stream()
                    .sorted().collect(Collectors.toList());

            log.info("六经来源: 患者直接={}, 方证反推={} (方证={})",
                    fromPatient, fromFz, fzForLiujing);

            List<String> liujingTypes = resolveLiujingMutex(mergedLiujing);

            String sixChannel;
            String sixChannelCn;
            String combinedDiseaseMark = null;
            boolean isCombined = liujingTypes.size() > 1;
            if (liujingTypes.isEmpty()) {
                sixChannel = null;
                sixChannelCn = "六经难定";
            } else if (liujingTypes.size() == 1) {
                sixChannel = liujingTypes.get(0);
                sixChannelCn = backendService.resolveLabel(sixChannel, BASE_NS);
            } else {
                combinedDiseaseMark = buildCombinedDiseaseMark(liujingTypes);
                sixChannel = combinedDiseaseMark;
                sixChannelCn = combinedDiseaseMark;
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sixChannel", sixChannel);
            out.put("sixChannelCn", sixChannelCn);
            out.put("liujingTypes", liujingTypes);
            out.put("liujingTypesCn", backendService.resolveLabels(liujingTypes, BASE_NS));
            out.put("isCombinedChannel", isCombined);
            out.put("combinedDiseaseMark", combinedDiseaseMark);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("六经完成: {} 合病={} 标记={}", liujingTypes, isCombined, combinedDiseaseMark);
        } catch (Exception e) {
            log.error("六经失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("LIUJING_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    /**
     * 半表半里阴阳互斥消解。
     */
    private List<String> resolveLiujingMutex(List<String> liujingTypes) {
        Set<String> set = new HashSet<>(liujingTypes);
        boolean changed = false;

        for (Map.Entry<String, String> entry : LIUJING_MUTEX_PAIRS.entrySet()) {
            String yang = entry.getKey();
            String yin = entry.getValue();
            if (set.contains(yang) && set.contains(yin)) {
                set.remove(yang);
                changed = true;
                log.info("[六经消解] 半表半里互斥（阴覆盖阳）：移除 {}，保留 {}", yang, yin);
            }
        }
        if (!changed) return liujingTypes;
        return set.stream().sorted().collect(Collectors.toList());
    }

    private void buildFangzhengLiujingMap() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<OWLClass, Set<OWLClass>> map = new HashMap<>();

        for (OWLClass fz : fangzhengSubclasses) {
            Set<OWLClass> ljs = OntologyModuleUtils.findRelatedClasses(
                    tbox, fz, liujingSubclasses);
            if (!ljs.isEmpty()) map.put(fz, ljs);
        }
        fangzhengLiujingMap = map;
        log.info("[init] 方证-六经缓存: {} 个方证有六经归属", map.size());
    }

    private String buildCombinedDiseaseMark(List<String> liujingTypes) {
        if (liujingTypes == null || liujingTypes.size() < 2) return null;
        if (liujingTypes.size() == 3 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Yangmingbing", "Shaoyangbing"))) return "三阳合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Shaoyinbing"))) return "太少两感";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Yangmingbing"))) return "太阳阳明合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Shaoyangbing"))) return "太阳少阳合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Taiyinbing"))) return "太阳太阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyangbing", "Jueyinbing"))) return "太阳厥阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyangbing", "Yangmingbing"))) return "少阳阳明合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyangbing", "Taiyinbing"))) return "少阳太阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyangbing", "Shaoyinbing"))) return "少阳少阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyangbing", "Jueyinbing"))) return "少阳厥阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Yangmingbing", "Taiyinbing"))) return "阳明太阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Yangmingbing", "Shaoyinbing"))) return "阳明少阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Yangmingbing", "Jueyinbing"))) return "阳明厥阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyinbing", "Shaoyinbing"))) return "太阴少阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Taiyinbing", "Jueyinbing"))) return "太阴厥阴合病";
        if (liujingTypes.size() == 2 && liujingTypes.containsAll(
                List.of("Shaoyinbing", "Jueyinbing"))) return "少阴厥阴合病";

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
        sorted.sort(Comparator.comparingInt(t -> orderMap.getOrDefault(t, Integer.MAX_VALUE)));
        StringBuilder sb = new StringBuilder();
        for (String t : sorted) sb.append(nameMap.getOrDefault(t, t));
        sb.append("合病");
        return sb.toString();
    }

    @JobWorker(type = "fangzheng-classification", autoComplete = false)
    public void handleFangzhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            Set<OWLClass> stage1Types = withLiujingReasoner(
                    input, ctx -> ctx.getTypes(input.patientIri));
            List<String> realizedMatches = withFangzhengReasoner(input, stage1Types, ctx ->
                    OntologyModuleUtils.extractFragmentsByMetaClass(
                            ctx.getTypes(input.patientIri), fangzhengSubclasses));
            Set<String> patientFrags = collectPatientFrags(input);

            Set<OWLClass> realizedClasses = realizedMatches.stream()
                    .map(f -> tboxDf.getOWLClass(IRI.create(BASE_NS + f)))
                    .collect(Collectors.toSet());
            Set<OWLClass> cachedCandidates = fangzhengCandidatesCache
                    .getOrDefault(patientIri, Collections.emptySet());
            Set<OWLClass> displaySet = new HashSet<>(realizedClasses);
            displaySet.addAll(cachedCandidates);

            List<ScoredFangzheng> displayScored = rankCandidatesWithScores(
                    displaySet, patientFrags, CANDIDATE_DISPLAY_TOP_N);

            if (!realizedMatches.isEmpty()) {
                log.info("[阶段2] realize 命中 {} 个，候选 Top{} 打分（hits→ratio 降序）:",
                        realizedMatches.size(), displayScored.size());
            } else {
                log.warn("[阶段2] realize 无匹配，从候选 {} 个中按 hits→ratio 降序输出 Top{}:",
                        cachedCandidates.size(), displayScored.size());
            }
            for (int i = 0; i < displayScored.size(); i++) {
                ScoredFangzheng s = displayScored.get(i);
                boolean isHit = realizedClasses.contains(s.cls);
                log.info("  #{} {} {}", i + 1, s, isHit ? "[realize命中]" : "[仅候选]");
            }

            String fangzheng;
            List<String> topCandidates = new ArrayList<>();
            List<String> candidateScores = new ArrayList<>();

            if (!realizedMatches.isEmpty()) {
                List<ScoredFangzheng> realizedScored = rankCandidatesWithScores(
                        realizedClasses, patientFrags, CANDIDATE_DISPLAY_TOP_N);
                fangzheng = realizedScored.isEmpty()
                        ? realizedMatches.get(0)
                        : realizedScored.get(0).fragment();
                log.info("[阶段2] 命中路径 Top1={}（hits={}）",
                        fangzheng, realizedScored.isEmpty() ? "?" : realizedScored.get(0));
            } else {
                if (!displayScored.isEmpty()) {
                    fangzheng = displayScored.get(0).fragment();
                    log.warn("[阶段2] 兜底 Top1={}（hits={}），注意：非 realize 命中",
                            fangzheng, displayScored.get(0));
                } else {
                    fangzheng = "方证未定";
                    log.warn("[阶段2] 无候选，方证未定");
                }
            }

            topCandidates.add(fangzheng);
            ScoredFangzheng firstScore = displayScored.stream()
                    .filter(s -> s.fragment().equals(fangzheng))
                    .findFirst()
                    .orElse(null);
            candidateScores.add(firstScore != null ? firstScore.toString() : fangzheng);
            for (ScoredFangzheng s : displayScored) {
                String f = s.fragment();
                if (!f.equals(fangzheng)
                        && topCandidates.size() < CANDIDATE_DISPLAY_TOP_N) {
                    topCandidates.add(f);
                    candidateScores.add(s.toString());
                }
            }

            log.info("方证完成: {} 匹配数={} 候选Top{}={}",
                    fangzheng, realizedMatches.size(), topCandidates.size(), topCandidates);
            log.info("方证打分: {}", candidateScores);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fangzheng", fangzheng);
            out.put("fangzhengCn", backendService.resolveLabel(fangzheng, BASE_NS));
            out.put("fangzhengTypes", topCandidates);
            out.put("candidateFangzhengs", topCandidates);
            out.put("candidateFangzhengsCn",
                    backendService.resolveLabels(topCandidates, BASE_NS));
            out.put("candidateScores", candidateScores);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
        } catch (Exception e) {
            log.error("方证失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("FANGZHENG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    @JobWorker(type = "jianjiazheng-classification", autoComplete = false)
    public void handleJianJiaZhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            List<String> jianJiaTypes = withLiujingReasoner(input, ctx ->
                    OntologyModuleUtils.extractFragmentsByMetaClass(
                            ctx.getTypes(input.patientIri), jianJiaSubclasses));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jianJiaZhengs", jianJiaTypes);
            out.put("jianJiaZhengsCn", backendService.resolveLabels(jianJiaTypes, BASE_NS));
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("兼夹证完成: {}", jianJiaTypes);
        } catch (Exception e) {
            log.error("兼夹证失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("JIANJIAZHENG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    @JobWorker(type = "prescription-recommendation", autoComplete = false)
    public void handlePrescriptionRecommendation(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String fangzhengFragment = (String) vars.get("fangzheng");
            if (fangzhengFragment == null || "方证未定".equals(fangzhengFragment)) {
                client.newCompleteCommand(job.getKey()).variables(emptyRx()).send().join();
                return;
            }

            OWLClass fzClass = tboxDf.getOWLClass(IRI.create(BASE_NS + fangzhengFragment));
            String formulaIri = extractFormula(fzClass);
            if (formulaIri == null) {
                client.newCompleteCommand(job.getKey()).variables(emptyRx()).send().join();
                return;
            }
            formulaIri = ObdaQueryUtils.toFullIri(formulaIri, BASE_NS);

            List<String[]> herbPairs = queryHerbsWithLabels(formulaIri);
            List<String> herbIris = herbPairs.stream().map(p -> p[0]).collect(Collectors.toList());
            List<String> herbCn   = herbPairs.stream().map(p -> p[1]).collect(Collectors.toList());

            List<String> addHerbIris = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> jianJiaZhengs = (List<String>) vars.get("jianJiaZhengs");
            if (jianJiaZhengs != null) {
                for (String jz : jianJiaZhengs) {
                    OWLClass jzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + jz));
                    for (IRI h : getAddHerbs(jzCls)) {
                        String full = ObdaQueryUtils.toFullIri(h.toString(), BASE_NS);
                        if (!addHerbIris.contains(full)) addHerbIris.add(full);
                    }
                }
            }

            List<String> allHerbs = new ArrayList<>(herbIris);
            allHerbs.addAll(addHerbIris);
            List<String> warnings = checkIncompatibilities(allHerbs);
            List<String> addHerbCn = addHerbIris.stream()
                    .map(this::queryLabel).collect(Collectors.toList());
            String formulaCn = queryLabel(formulaIri);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("finalFormula", formulaIri);
            out.put("finalFormulaCn", formulaCn);
            out.put("candidateFormulas", List.of(formulaIri));
            out.put("herbs", herbIris);
            out.put("herbsCn", herbCn);
            out.put("addHerbs", addHerbIris);
            out.put("addHerbsCn", addHerbCn);
            out.put("warnings", warnings);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("方剂完成: {} 药物={} 加减={} 警告={}",
                    formulaIri, herbIris, addHerbIris, warnings);
        } catch (Exception e) {
            log.error("方剂失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("PRESCRIPTION_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    private Map<String, Object> emptyRx() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("finalFormula", null);
        o.put("finalFormulaCn", null);
        o.put("herbs", new ArrayList<>());
        o.put("herbsCn", new ArrayList<>());
        o.put("addHerbs", new ArrayList<>());
        o.put("addHerbsCn", new ArrayList<>());
        o.put("warnings", new ArrayList<>());
        return o;
    }

    private String extractFormula(OWLClass cls) {
        OWLOntology ont = backendService.getOntologyService().gettBoxOntology();
        for (OWLEquivalentClassesAxiom ax : ont.getEquivalentClassesAxioms(cls)) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.equals(cls)) continue;
                String f = findHasValue(e);
                if (f != null) return f;
            }
        }
        for (OWLSubClassOfAxiom ax : ont.getSubClassAxiomsForSubClass(cls)) {
            String f = findHasValue(ax.getSuperClass());
            if (f != null) return f;
        }
        return null;
    }

    private String findHasValue(OWLClassExpression expr) {
        if (expr instanceof OWLObjectHasValue hv) {
            if (hv.getProperty().asOWLObjectProperty().getIRI().toString()
                    .equals(HAS_PRESCRIPTION)) {
                OWLIndividual ind = hv.getFiller();
                if (ind.isNamed()) return ind.asOWLNamedIndividual().getIRI().toString();
            }
        } else if (expr instanceof OWLObjectIntersectionOf inter) {
            for (OWLClassExpression op : inter.getOperands()) {
                String f = findHasValue(op);
                if (f != null) return f;
            }
        }
        return null;
    }

    private Set<IRI> getAddHerbs(OWLClass cls) {
        Set<IRI> res = new HashSet<>();
        OWLOntology ont = backendService.getOntologyService().gettBoxOntology();
        for (OWLAnnotationAssertionAxiom ax : ont.getAnnotationAssertionAxioms(cls.getIRI())) {
            if (ax.getProperty().getIRI().getFragment().equals("addHerb")
                    && ax.getValue() instanceof IRI) res.add((IRI) ax.getValue());
        }
        return res;
    }

    private List<String[]> queryHerbsWithLabels(String formulaIri) {
        String sparql = """
            PREFIX : <http://www.tcm-classics.org/jingfang#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?herb ?label WHERE {
                <%s> :you_yaowu ?herb .
                OPTIONAL { ?herb rdfs:label ?label . }
            }
            ORDER BY ?herb
            """.formatted(ObdaQueryUtils.toFullIri(formulaIri, BASE_NS));

        List<String[]> result = new ArrayList<>();
        for (Map<String, String> row :
                backendService.getObdaHandler().executeAboxQuery(sparql)) {
            String herb = row.get("herb");
            if (herb == null || herb.isBlank()) continue;
            String full = ObdaQueryUtils.toFullIri(herb.trim(), BASE_NS);
            String label = row.get("label");
            if (label == null || label.isBlank()) {
                label = ObdaQueryUtils.fragmentOf(full);
            } else {
                label = label.trim();
            }
            result.add(new String[]{ full, label });
        }
        return result;
    }

    private String resolveClassLabel(OWLClass cls) {
        String fullIri = cls.getIRI().toString();
        String label = backendService.resolveLabel(fullIri, BASE_NS);
        return (label != null && !label.isBlank()) ? label : cls.getIRI().getFragment();
    }

    private String queryLabel(String iri) {
        return ObdaQueryUtils.queryLabel(
                backendService.getObdaHandler(), iri, BASE_NS, log);
    }

    private List<String> checkIncompatibilities(List<String> herbIris) {
        List<String> warnings = new ArrayList<>();
        if (herbIris == null || herbIris.size() < 2) return warnings;

        Set<String> herbs = herbIris.stream()
                .map(i -> ObdaQueryUtils.toFullIri(i, BASE_NS))
                .filter(Objects::nonNull)
                .map(ObdaQueryUtils::fragmentOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        checkPairs(herbs, shibafanMap,  "十八反", warnings);
        checkPairs(herbs, shijiuweiMap, "十九畏", warnings);
        return warnings;
    }

    private void checkPairs(Set<String> herbs,
                            Map<String, Set<String>> relMap,
                            String label,
                            List<String> warnings) {
        if (relMap == null || relMap.isEmpty()) return;
        Set<String> seen = new HashSet<>();
        for (String h : herbs) {
            Set<String> others = relMap.get(h);
            if (others == null) continue;
            for (String o : others) {
                if (!herbs.contains(o)) continue;
                String key = h.compareTo(o) <= 0 ? h + "|" + o : o + "|" + h;
                if (!seen.add(key)) continue;
                warnings.add(label + "：" + labelOf(h)
                        + (label.equals("十八反") ? " 反 " : " 畏 ")
                        + labelOf(o));
            }
        }
    }

    @JobWorker(type = "diagnosis-explanation", autoComplete = false)
    public void handleDiagnosisExplanation(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patientIri = (String) vars.get("patientIri");
            String sixChannelCn = (String) vars.get("sixChannelCn");
            String fangzhengCn = (String) vars.get("fangzhengCn");
            String finalFormulaCn = (String) vars.get("finalFormulaCn");

            String liujingDisplay = (sixChannelCn != null && !sixChannelCn.isEmpty())
                    ? sixChannelCn : "六经难定";
            String fangzhengDisplay = (fangzhengCn != null && !fangzhengCn.isEmpty())
                    ? fangzhengCn : "方证未定";
            String formulaDisplay = (finalFormulaCn != null && !finalFormulaCn.isEmpty())
                    ? finalFormulaCn : "未定";

            String explanation = String.format("六经：%s，方证：%s，推荐方剂：%s。",
                    liujingDisplay, fangzhengDisplay, formulaDisplay);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("explanation", explanation);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("诊断解释: {}", explanation);

            if (patientIri != null) {
                clearPatientCache(patientIri);
            }
        } catch (Exception e) {
            log.error("诊断解释失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("EXPLANATION_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== 工具方法 ====================

    private void clearPatientCache(String patientIri) {
        patientInputs.remove(patientIri);
        fangzhengCandidatesCache.remove(patientIri);
        miniCtxMgr.clearByPrefix(patientIri);
    }

    private String frag(String iri) {
        return ObdaQueryUtils.frag(iri, BASE_NS, INSTANCE_SUFFIX);
    }
}