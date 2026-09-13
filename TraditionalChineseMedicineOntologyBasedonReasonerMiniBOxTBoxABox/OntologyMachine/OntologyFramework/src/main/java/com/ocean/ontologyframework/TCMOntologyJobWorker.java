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
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

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

    /**
     * 【新增】方证 → 八纲集合的缓存。
     * init() 时从本体注解一次性构建，运行时 O(1) 查表。
     * Java 代码不硬编码任何方证名或八纲名，全部由本体提供。
     */
    private Map<OWLClass, Set<OWLClass>> fangzhengBagangMap = new HashMap<>();

    /** 症状（fragment）→ 包含该症状限制的方证集合 */
    private Map<String, Set<OWLClass>> symptomToFangzhengIndex;

    /** 方证 → 该方证等价类/子类要求的症状/脉象/舌象/腹证 fragment 总数 */
    private Map<OWLClass, Integer> fangzhengRequiredCount = new HashMap<>();

    /** 复合脉类 → 组成原子脉类集合（从本体 equivalentClass 读取） */
    private Map<OWLClass, Set<OWLClass>> compositePulseMap = new HashMap<>();

    /** 复合症状类 → 组成原子症状类集合（同上） */
    private Map<OWLClass, Set<OWLClass>> compositeSymptomMap = new HashMap<>();

    /** 十八反：药物 fragment → 与之相反的药物 fragment 集合（OBDA 加载） */
    private Map<String, Set<String>> shibafanMap = new HashMap<>();

    /** 十九畏：药物 fragment → 与之相畏的药物 fragment 集合（OBDA 加载） */
    private Map<String, Set<String>> shijiuweiMap = new HashMap<>();

    /** 药物 fragment → 中文 label，用于警告文本 */
    private Map<String, String> yaowuLabelMap = new HashMap<>();

    /** 回退到 TopN 的默认上限 */
    private static final int FALLBACK_TOP_N = 20;

    /** 候选方证展示上限（Top5） */
    private static final int CANDIDATE_DISPLAY_TOP_N = 5;

    /** 基础六经白名单（不含合病类） */
    private static final Set<String> SIX_CHANNEL_WHITELIST = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing"
    );

    /** 顶层声明类，mini 里需要保留 */
    private static final Set<String> TOP_LEVEL_CLASSES = Set.of(
            "Huanzhe", "SizhenXinxi", "Zhengzhuang", "Maixiang",
            "Shexiang", "Fuzheng", "Tizhi", "Bagang", "Liujingbing",
            "Fangzheng", "Fangji", "Yaowu", "Yaozheng", "JianJiaZheng"
    );

    // ==================== 缓存 ====================
    private final Map<String, Set<OWLAxiom>> miniTboxCache = new ConcurrentHashMap<>();
    private final Map<String, MiniContext> miniContextCache = new ConcurrentHashMap<>();
    private final Map<String, PatientInput> patientInputs = new ConcurrentHashMap<>();

    /** 患者 → 阶段2 筛选出的候选集（realize 0 匹配时做 fallback 排序用） */
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

    /** 方证 → 六经集合（从本体注解 sixChannelAttr 读取） */
    private Map<OWLClass, Set<OWLClass>> fangzhengLiujingMap = new HashMap<>();

    /**
     * 半表半里阴阳互斥：key = 阳证（被移除），value = 阴证（保留）。
     * 医理依据：半表半里是三焦枢机，只能阳化或阴化，不能兼得。
     */
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

    private static class MiniContext {
        final OWLOntologyManager manager;
        final OWLOntology ontology;
        final OWLDataFactory df;
        final OWLReasoner reasoner;
        final String patientIri;

        private volatile Set<OWLClass> patientTypesCache = null;

        MiniContext(OWLOntologyManager m, OWLOntology o, OWLDataFactory df,
                    OWLReasoner r, String patientIri) {
            this.manager = m;
            this.ontology = o;
            this.df = df;
            this.reasoner = r;
            this.patientIri = patientIri;
        }

        Set<OWLClass> getPatientTypes() {
            Set<OWLClass> cached = patientTypesCache;
            if (cached != null) return cached;
            OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(patientIri));
            Set<OWLClass> types = reasoner.getTypes(patient, false).getFlattened();
            patientTypesCache = types;
            return types;
        }

        void dispose() {
            try { reasoner.dispose(); } catch (Exception ignored) {}
            try { manager.removeOntology(ontology); } catch (Exception ignored) {}
        }
    }

    /** 带症状覆盖分数的方证 */
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
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
            backendService = BackendService.getInstance(mainOntologyPath, OBDAHandler.getInstance());
            queryService = new QueryService(backendService);
            tboxDf = backendService.getOntologyService().gettBoxOntology()
                    .getOWLOntologyManager().getOWLDataFactory();
            log.info("[init] BackendService 初始化完成，耗时 {} ms",
                    System.currentTimeMillis() - tBackend);

            long tMeta = System.currentTimeMillis();

            // 【修正】先初始化六经/方证/兼夹证，再初始化八纲并过滤掉它们的间接子类。
            // 虽然方证八纲已改为注解，但为了健壮性（六经仍 subClassOf 八纲），仍做过滤。
            liujingSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Fangzheng"));
            jianJiaSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "JianJiaZheng"));
            // 【修正】只取 Bagang 的"直接"子类，避免 Weimai/Weiximai/Wuhan 等间接子类被误算入八纲
            OWLClass bagangCls = tboxDf.getOWLClass(IRI.create(BASE_NS + "Bagang"));
            OWLOntology tboxForBagang = backendService.getOntologyService().gettBoxOntology();
            bagangSubclasses = tboxForBagang.subClassAxiomsForSuperClass(bagangCls)
                    .map(OWLSubClassOfAxiom::getSubClass)
                    .filter(OWLClassExpression::isOWLClass)
                    .map(OWLClassExpression::asOWLClass)
                    .collect(Collectors.toSet());

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

            // 【新增】构建"方证 → 八纲"缓存（一次性 O(221) 遍历注解）
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
    // 【新增】方证-八纲缓存构建（绝对本体驱动）
    // ============================================================

    /**
     * 遍历所有方证，从本体的注解断言里读取八纲归属。
     *
     * 关键：**Java 里不写任何注解属性名**。
     * 逻辑是"凡注解值指向 Bagang 子类的，都视为该方证的八纲"，
     * 至于这个注解叫什么名字（bagangAttr / hasCategory / ...），
     * 由本体自己决定。
     *
     * 也兼容 subClassOf 形式（万一未来又加回来）。
     */
    private void buildFangzhengBagangMap() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<OWLClass, Set<OWLClass>> map = new HashMap<>();

        int annotationHits = 0;
        int subClassHits = 0;

        for (OWLClass fz : fangzhengSubclasses) {
            Set<OWLClass> bagangs = new HashSet<>();

            // === 路径 1：注解断言（推荐） ===
            // 不写 "bagangAttr" 常量，遍历所有注解，值指向 Bagang 子类的就收
            for (OWLAnnotationAssertionAxiom ax :
                    tbox.annotationAssertionAxioms(fz.getIRI()).collect(Collectors.toList())) {
                if (!(ax.getValue() instanceof IRI valueIri)) continue;
                OWLClass valueCls = tboxDf.getOWLClass(valueIri);
                if (bagangSubclasses.contains(valueCls)) {
                    bagangs.add(valueCls);
                    annotationHits++;
                }
            }

            // === 路径 2：subClassOf（兼容旧写法） ===
            for (OWLSubClassOfAxiom ax :
                    tbox.subClassAxiomsForSubClass(fz).collect(Collectors.toList())) {
                OWLClassExpression sc = ax.getSuperClass();
                if (sc.isOWLClass()) {
                    OWLClass scCls = sc.asOWLClass();
                    if (bagangSubclasses.contains(scCls)) {
                        bagangs.add(scCls);
                        subClassHits++;
                    }
                }
            }

            if (!bagangs.isEmpty()) {
                map.put(fz, bagangs);
            }
        }

        fangzhengBagangMap = map;
        log.info("[init] 方证-八纲缓存: {} 个方证有八纲, (注解命中 {} 次, subClassOf 命中 {} 次)",
                map.size(), annotationHits, subClassHits);

        // 抽样打印几个方证的八纲归属（用于确认本体正确加载）
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
            Set<String> syms = collectRequiredSymptoms(tbox, fz, propIris);
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

    private Set<String> collectRequiredSymptoms(OWLOntology tbox,
                                                OWLClass fz,
                                                Set<IRI> propIris) {
        Set<String> result = new HashSet<>();
        for (OWLEquivalentClassesAxiom ax :
                tbox.equivalentClassesAxioms(fz).collect(Collectors.toList())) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.isOWLClass() && e.asOWLClass().equals(fz)) continue;
                collectRestrictions(e, propIris, result);
            }
        }
        for (OWLSubClassOfAxiom ax :
                tbox.subClassAxiomsForSubClass(fz).collect(Collectors.toList())) {
            collectRestrictions(ax.getSuperClass(), propIris, result);
        }
        return result;
    }

    private void collectRestrictions(OWLClassExpression expr,
                                     Set<IRI> propIris,
                                     Set<String> acc) {
        if (expr instanceof OWLObjectSomeValuesFrom svf) {
            IRI propIri = svf.getProperty().getNamedProperty().getIRI();
            if (propIris.contains(propIri)) {
                OWLClassExpression filler = svf.getFiller();
                if (filler.isOWLClass()) {
                    acc.add(filler.asOWLClass().getIRI().getFragment());
                }
            }
        } else if (expr instanceof OWLObjectIntersectionOf inter) {
            for (OWLClassExpression op : inter.getOperands()) {
                collectRestrictions(op, propIris, acc);
            }
        } else if (expr instanceof OWLObjectUnionOf union) {
            for (OWLClassExpression op : union.getOperands()) {
                collectRestrictions(op, propIris, acc);
            }
        }
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
    // 复合-原子映射
    // ============================================================

    private void buildCompositeMaps() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        compositePulseMap = scanComposites(tbox, IRI.create(BASE_NS + "Maixiang"));
        compositeSymptomMap = scanComposites(tbox, IRI.create(BASE_NS + "Zhengzhuang"));

        log.info("[init] 复合脉映射: {} 个", compositePulseMap.size());
        log.info("[init] 复合症状映射: {} 个", compositeSymptomMap.size());
    }

    private Map<OWLClass, Set<OWLClass>> scanComposites(OWLOntology tbox, IRI topClassIri) {
        Map<OWLClass, Set<OWLClass>> result = new HashMap<>();
        Set<OWLClass> allSubs = backendService.getAllNamedSubclasses(topClassIri);
        OWLClass top = tbox.getOWLOntologyManager().getOWLDataFactory()
                .getOWLClass(topClassIri);

        for (OWLClass cls : allSubs) {
            for (OWLEquivalentClassesAxiom ax :
                    tbox.equivalentClassesAxioms(cls).collect(Collectors.toList())) {
                for (OWLClassExpression e : ax.getClassExpressions()) {
                    if (e.isOWLClass() && e.asOWLClass().equals(cls)) continue;
                    Set<OWLClass> comps = extractAtomicComponents(e, allSubs, top, cls);
                    if (comps != null) {
                        result.put(cls, comps);
                    }
                }
            }
        }
        return result;
    }

    private Set<OWLClass> extractAtomicComponents(OWLClassExpression e,
                                                  Set<OWLClass> allSubs,
                                                  OWLClass top,
                                                  OWLClass self) {
        if (!(e instanceof OWLObjectIntersectionOf inter)) return null;
        Set<OWLClass> comps = new HashSet<>();
        for (OWLClassExpression op : inter.getOperands()) {
            if (!op.isOWLClass()) return null;
            OWLClass oc = op.asOWLClass();
            if (oc.equals(self) || oc.equals(top)) return null;
            if (!allSubs.contains(oc)) return null;
            comps.add(oc);
        }
        return comps.isEmpty() ? null : comps;
    }

    // ============================================================
    // 十八反/十九畏
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
                String frag = fragmentOf(iri);
                if (frag == null) continue;
                yaowuLabelMap.put(frag,
                        (label != null && !label.isBlank()) ? label.trim() : frag);
            }

            String antQ = """
                PREFIX : <http://www.tcm-classics.org/jingfang#>
                SELECT ?a ?b WHERE { ?a :antagonistic ?b . }
                """;
            shibafanMap = loadRelationFromObda(antQ);

            String fearQ = """
                PREFIX : <http://www.tcm-classics.org/jingfang#>
                SELECT ?a ?b WHERE { ?a :fearing ?b . }
                """;
            shijiuweiMap = loadRelationFromObda(fearQ);

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

    private Map<String, Set<String>> loadRelationFromObda(String sparql) {
        Map<String, Set<String>> map = new HashMap<>();
        for (Map<String, String> row :
                backendService.getObdaHandler().executeAboxQuery(sparql)) {
            String a = fragmentOf(row.get("a"));
            String b = fragmentOf(row.get("b"));
            if (a == null || b == null) continue;
            map.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            map.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        }
        return map;
    }

    private String fragmentOf(String iri) {
        if (iri == null) return null;
        String s = iri.trim();
        if (s.isEmpty()) return null;
        int idx = s.lastIndexOf('#');
        if (idx >= 0) return s.substring(idx + 1);
        idx = s.lastIndexOf('/');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    private String labelOf(String fragment) {
        return yaowuLabelMap.getOrDefault(fragment, fragment);
    }

    // ============================================================
    // 精确抽取
    // ============================================================

    private Set<OWLAxiom> extractPrecise(OWLOntology tbox, Set<OWLClass> keepClasses) {
        return tbox.axioms()
                .filter(ax -> !(ax instanceof OWLClassAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLObjectPropertyAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLDataPropertyAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLSameIndividualAxiom))
                .filter(ax -> !(ax instanceof OWLDifferentIndividualsAxiom))
                .filter(ax -> isRelevantAxiom(ax, keepClasses))
                .collect(Collectors.toSet());
    }

    private boolean isRelevantAxiom(OWLAxiom ax, Set<OWLClass> keep) {
        if (ax instanceof OWLSubClassOfAxiom sub) {
            return sub.getSubClass().isOWLClass()
                    && keep.contains(sub.getSubClass().asOWLClass());
        }
        if (ax instanceof OWLEquivalentClassesAxiom eq) {
            return eq.getClassExpressions().stream()
                    .anyMatch(e -> e.isOWLClass() && keep.contains(e.asOWLClass()));
        }
        if (ax instanceof OWLDeclarationAxiom decl) {
            if (!decl.getEntity().isOWLClass()) return false;
            return keep.contains(decl.getEntity().asOWLClass());
        }
        if (ax instanceof OWLObjectPropertyDomainAxiom
                || ax instanceof OWLObjectPropertyRangeAxiom
                || ax instanceof OWLDataPropertyDomainAxiom
                || ax instanceof OWLDataPropertyRangeAxiom
                || ax instanceof OWLFunctionalObjectPropertyAxiom
                || ax instanceof OWLInverseFunctionalObjectPropertyAxiom
                || ax instanceof OWLSymmetricObjectPropertyAxiom
                || ax instanceof OWLTransitiveObjectPropertyAxiom
                || ax instanceof OWLReflexiveObjectPropertyAxiom
                || ax instanceof OWLIrreflexiveObjectPropertyAxiom
                || ax instanceof OWLAsymmetricObjectPropertyAxiom
                || ax instanceof OWLFunctionalDataPropertyAxiom
                || ax instanceof OWLInverseObjectPropertiesAxiom
                || ax instanceof OWLSubObjectPropertyOfAxiom
                || ax instanceof OWLSubDataPropertyOfAxiom) {
            return true;
        }
        if (ax instanceof OWLDisjointClassesAxiom) return true;
        return false;
    }

    // ============================================================
    // 递归闭包收集
    // ============================================================

    private Set<OWLClass> collectClosure(OWLOntology tbox,
                                         Set<OWLClass> initial,
                                         PatientInput input) {
        Set<OWLClass> closure = new HashSet<>(initial);
        Deque<OWLClass> queue = new ArrayDeque<>(initial);

        if (input != null) {
            addInstanceTypes(tbox, closure, queue, input);
        }

        while (!queue.isEmpty()) {
            OWLClass c = queue.poll();

            for (OWLEquivalentClassesAxiom ax :
                    tbox.equivalentClassesAxioms(c).collect(Collectors.toList())) {
                for (OWLClassExpression e : ax.getClassExpressions()) {
                    if (e.isOWLClass() && e.asOWLClass().equals(c)) continue;
                    for (OWLClass ref :
                            e.classesInSignature().collect(Collectors.toList())) {
                        if (!ref.isOWLThing() && !ref.isOWLNothing()
                                && closure.add(ref)) {
                            queue.add(ref);
                        }
                    }
                }
            }

            for (OWLSubClassOfAxiom ax :
                    tbox.subClassAxiomsForSubClass(c).collect(Collectors.toList())) {
                for (OWLClass ref :
                        ax.getSuperClass().classesInSignature().collect(Collectors.toList())) {
                    if (!ref.isOWLThing() && !ref.isOWLNothing()
                            && closure.add(ref)) {
                        queue.add(ref);
                    }
                }
            }
        }
        return closure;
    }

    private void addInstanceTypes(OWLOntology tbox,
                                  Set<OWLClass> closure,
                                  Deque<OWLClass> queue,
                                  PatientInput input) {
        List<String> allIris = new ArrayList<>();
        allIris.addAll(input.symptomIris);
        allIris.addAll(input.pulseIris);
        allIris.addAll(input.tongueIris);
        allIris.addAll(input.fuzhengIris);

        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();
        for (String iri : allIris) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(toFullIri(iri)));
            for (OWLClassAssertionAxiom ax :
                    tbox.classAssertionAxioms(ind).collect(Collectors.toList())) {
                OWLClassExpression ce = ax.getClassExpression();
                if (ce.isOWLClass()) {
                    OWLClass cls = ce.asOWLClass();
                    if (closure.add(cls)) {
                        queue.add(cls);
                    }
                }
            }
        }
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

        Set<OWLClass> keepClasses = collectClosure(tbox, initial, input);

        Set<OWLAxiom> tboxOnly = extractPrecise(tbox, keepClasses);

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

        Set<OWLClass> relevantFangzheng =
                filterFangzheng(tbox, stage1Types, patientFrags);
        fangzhengCandidatesCache.put(input.patientIri, relevantFangzheng);
        initial.addAll(relevantFangzheng);

        for (String t : TOP_LEVEL_CLASSES) {
            initial.add(df.getOWLClass(IRI.create(BASE_NS + t)));
        }

        Set<OWLClass> keepClasses = collectClosure(tbox, initial, input);
        Set<OWLAxiom> tboxOnly = extractPrecise(tbox, keepClasses);

        log.info("[阶段2] 筛选方证={}/{}, 初始={}, 闭包={}, mini 公理={}",
                relevantFangzheng.size(), fangzhengSubclasses.size(),
                initial.size(), keepClasses.size(), tboxOnly.size());
        return tboxOnly;
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
            Set<OWLClass> direct =
                    topBySymptomOverlap(patientSymptomFrags, FALLBACK_TOP_N);
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
    // 上下文构建与缓存
    // ============================================================

    private MiniContext getOrCreateContext(String cacheKey, Set<OWLAxiom> miniTbox,
                                           PatientInput input) {
        return miniContextCache.computeIfAbsent(cacheKey, k -> {
            long t0 = System.currentTimeMillis();
            OWLOntologyManager tmpMgr = OWLManager.createOWLOntologyManager();
            try {
                OWLOntology mini = tmpMgr.createOntology(
                        IRI.create("urn:mini:" + System.nanoTime()));
                tmpMgr.addAxioms(mini, miniTbox);

                OWLDataFactory df = tmpMgr.getOWLDataFactory();
                tmpMgr.addAxioms(mini, buildPatientAxioms(df, input));

                OWLReasoner r = new OpenlletReasonerFactory().createReasoner(mini);
                r.precomputeInferences(InferenceType.CLASS_ASSERTIONS);

                log.info("[MiniContext:{}] axioms={}, realize 耗时 {} ms",
                        k, mini.getAxiomCount(), System.currentTimeMillis() - t0);
                return new MiniContext(tmpMgr, mini, df, r, input.patientIri);
            } catch (OWLOntologyCreationException e) {
                throw new RuntimeException("创建迷你本体失败", e);
            }
        });
    }

    private <T> T withLiujingReasoner(PatientInput input, Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri + STAGE_LJ;
        Set<OWLAxiom> miniTbox = miniTboxCache.computeIfAbsent(cacheKey,
                k -> extractLiujingModule(input));
        MiniContext ctx = getOrCreateContext(cacheKey, miniTbox, input);
        try {
            return action.apply(ctx);
        } catch (RuntimeException e) {
            MiniContext removed = miniContextCache.remove(cacheKey);
            if (removed != null) removed.dispose();
            throw e;
        }
    }

    private <T> T withFangzhengReasoner(PatientInput input,
                                        Set<OWLClass> stage1Types,
                                        Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri + STAGE_FZ;
        Set<OWLAxiom> miniTbox = miniTboxCache.computeIfAbsent(cacheKey,
                k -> extractFangzhengModule(input, stage1Types));
        MiniContext ctx = getOrCreateContext(cacheKey, miniTbox, input);
        try {
            return action.apply(ctx);
        } catch (RuntimeException e) {
            MiniContext removed = miniContextCache.remove(cacheKey);
            if (removed != null) removed.dispose();
            throw e;
        }
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

        addAssertionsAndTypes(df, axioms, hasSymptom, patient, input.symptomIris);
        addAssertionsAndTypes(df, axioms, hasPulse, patient, input.pulseIris);
        addAssertionsAndTypes(df, axioms, hasTongue, patient, input.tongueIris);
        addAssertionsAndTypes(df, axioms, hasAbdominal, patient, input.fuzhengIris);

        addSynthesizedComposites(df, axioms, patient,
                input.symptomIris, hasSymptom, compositeSymptomMap);
        addSynthesizedComposites(df, axioms, patient,
                input.pulseIris, hasPulse, compositePulseMap);
        addSynthesizedComposites(df, axioms, patient,
                input.tongueIris, hasTongue, null);
        addSynthesizedComposites(df, axioms, patient,
                input.fuzhengIris, hasAbdominal, null);

        return axioms;
    }

    private void addAssertionsAndTypes(OWLDataFactory df, Set<OWLAxiom> acc,
                                       OWLObjectProperty prop, OWLNamedIndividual subj,
                                       List<String> objects) {
        if (objects == null || objects.isEmpty()) return;
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        for (String iri : objects) {
            OWLNamedIndividual obj = df.getOWLNamedIndividual(IRI.create(toFullIri(iri)));
            acc.add(df.getOWLObjectPropertyAssertionAxiom(prop, subj, obj));
            tbox.classAssertionAxioms(obj).forEach(ca -> {
                OWLClassExpression ce = ca.getClassExpression();
                if (ce.isOWLClass()) {
                    acc.add(df.getOWLClassAssertionAxiom(ce.asOWLClass(), obj));
                }
            });
        }
    }

    private void addSynthesizedComposites(OWLDataFactory df,
                                          Set<OWLAxiom> acc,
                                          OWLNamedIndividual patient,
                                          List<String> providedIris,
                                          OWLObjectProperty prop,
                                          Map<OWLClass, Set<OWLClass>> compositeMap) {
        if (providedIris == null || providedIris.isEmpty()) return;
        if (compositeMap == null || compositeMap.isEmpty()) return;

        Set<String> patientFrags = providedIris.stream()
                .map(this::frag)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> alreadyComposite = new HashSet<>();
        for (String p : providedIris) {
            String f = frag(p);
            if (f == null) continue;
            OWLClass c = df.getOWLClass(IRI.create(BASE_NS + f));
            if (compositeMap.containsKey(c)) alreadyComposite.add(f);
        }

        for (Map.Entry<OWLClass, Set<OWLClass>> entry : compositeMap.entrySet()) {
            OWLClass composite = entry.getKey();
            Set<OWLClass> components = entry.getValue();
            String compFrag = composite.getIRI().getFragment();

            if (alreadyComposite.contains(compFrag)) continue;

            boolean allPresent = true;
            for (OWLClass comp : components) {
                if (!patientFrags.contains(comp.getIRI().getFragment())) {
                    allPresent = false;
                    break;
                }
            }
            if (!allPresent) continue;

            OWLNamedIndividual synth = df.getOWLNamedIndividual(
                    IRI.create("urn:synth:" + compFrag + ":"
                            + patient.getIRI().getFragment()));
            acc.add(df.getOWLClassAssertionAxiom(composite, synth));
            for (OWLClass comp : components) {
                acc.add(df.getOWLClassAssertionAxiom(comp, synth));
            }
            acc.add(df.getOWLObjectPropertyAssertionAxiom(prop, patient, synth));

            log.info("[合成复合] {} 具备 {} 的成分 {}，合成",
                    patient.getIRI().getFragment(), compFrag,
                    components.stream().map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()));
        }
    }

    private List<String> extractByMetaClass(Set<OWLClass> allTypes,
                                            Set<OWLClass> metaClassSet) {
        return allTypes.stream()
                .filter(metaClassSet::contains)
                .map(c -> c.getIRI().getFragment())
                .sorted()
                .collect(Collectors.toList());
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
                    getList(vars, "symptomIris"),
                    getList(vars, "pulseIris"),
                    getList(vars, "tongueIris"),
                    getList(vars, "fuzhengIris"));

            boolean consistent = withLiujingReasoner(input, ctx -> ctx.reasoner.isConsistent());
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

            boolean consistent = withLiujingReasoner(input, ctx -> ctx.reasoner.isConsistent());

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
     * 八纲分类：
     *  1) 患者直接推的八纲（Openllet 从症状+六经推）
     *  2) 命中方证的八纲（从 init 时构建的 fangzhengBagangMap 查表）
     *  3) 取并集
     *
     * 全部 O(1) 查表，无运行时本体遍历。
     */
    /**
     * 八纲维度归属（元知识，中医基础理论定义，不属于医学数据）。
     * 逻辑：把 fragment 归一化为小写后匹配维度。
     * 无论本体 fragment 用 `Li` / `li` / `LI` 哪种写法，都能命中。
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

            // 1) 患者直接推的八纲
            Set<OWLClass> patientTypes = withLiujingReasoner(input, MiniContext::getPatientTypes);
            Set<OWLClass> bagangFromPatient = patientTypes.stream()
                    .filter(bagangSubclasses::contains)
                    .collect(Collectors.toSet());

            // 2) 决定"用于读八纲的方证"——与 handleFangzhengClassification 的决策一致：
            //    命中优先；无命中时从候选集按 hits 兜底 Top1
            List<String> realizedFz = withFangzhengReasoner(input, patientTypes, ctx ->
                    extractByMetaClass(ctx.getPatientTypes(), fangzhengSubclasses));

            List<String> fzForBagang;
            if (!realizedFz.isEmpty()) {
                // 命中路径：取命中者中 hits 最高的
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
                // 兜底路径：从缓存候选集按 hits 取 Top1
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

            // 3) 从最终方证读八纲（缓存优先，miss 时实时读）
            Set<OWLClass> bagangFromFz = new HashSet<>();
            for (String frag : fzForBagang) {
                OWLClass fzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + frag));
                Set<OWLClass> bs = fangzhengBagangMap.get(fzCls);
                if (bs != null && !bs.isEmpty()) {
                    bagangFromFz.addAll(bs);
                } else {
                    Set<OWLClass> live = readBagangFromFangzheng(fzCls);
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

            // 4) 并集
            Set<OWLClass> allBagang = new HashSet<>(bagangFromPatient);
            allBagang.addAll(bagangFromFz);

            log.info("八纲来源: 患者直接={}, 命中方证={} (方证={})",
                    bagangFromPatient.stream().map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()),
                    bagangFromFz.stream().map(c -> c.getIRI().getFragment())
                            .sorted().collect(Collectors.toList()),
                    fzForBagang);

            // 5) 排序 + 分组输出
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

    /** 从本体实时读方证的八纲（注解 + subClassOf + equivalentClass 三路兜底） */
    private Set<OWLClass> readBagangFromFangzheng(OWLClass fz) {
        Set<OWLClass> result = new HashSet<>();
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(fz.getIRI()).collect(Collectors.toList())) {
            if (!(ax.getValue() instanceof IRI valueIri)) continue;
            OWLClass valueCls = tboxDf.getOWLClass(valueIri);
            if (bagangSubclasses.contains(valueCls)) result.add(valueCls);
        }

        for (OWLSubClassOfAxiom ax :
                tbox.subClassAxiomsForSubClass(fz).collect(Collectors.toList())) {
            OWLClassExpression sc = ax.getSuperClass();
            if (sc.isOWLClass() && bagangSubclasses.contains(sc.asOWLClass())) {
                result.add(sc.asOWLClass());
            }
        }

        for (OWLEquivalentClassesAxiom ax :
                tbox.equivalentClassesAxioms(fz).collect(Collectors.toList())) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.isOWLClass() && !e.asOWLClass().equals(fz)
                        && bagangSubclasses.contains(e.asOWLClass())) {
                    result.add(e.asOWLClass());
                }
            }
        }
        return result;
    }

    @JobWorker(type = "liujing-classification", autoComplete = false)
    public void handleLiujingClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            // 1) 患者直接推的六经
            Set<String> fromPatient = withLiujingReasoner(input, ctx -> {
                Set<OWLClass> allTypes = ctx.getPatientTypes();
                return allTypes.stream()
                        .filter(c -> SIX_CHANNEL_WHITELIST.contains(c.getIRI().getFragment()))
                        .map(c -> c.getIRI().getFragment())
                        .collect(Collectors.toSet());
            });

            // 2) 命中方证反推六经（缓存优先 + miss 实时读）
            Set<OWLClass> stage1Types = withLiujingReasoner(input, MiniContext::getPatientTypes);
            List<String> realizedFz = withFangzhengReasoner(input, stage1Types, ctx ->
                    extractByMetaClass(ctx.getPatientTypes(), fangzhengSubclasses));

            // 若 realize 无匹配，走候选 Top1 兜底（与 fangzheng/bagang 保持一致）
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
            for (String frag : fzForLiujing) {
                OWLClass fzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + frag));
                Set<OWLClass> cached = fangzhengLiujingMap.get(fzCls);
                Set<OWLClass> ljs;
                if (cached != null && !cached.isEmpty()) {
                    ljs = cached;
                } else {
                    ljs = readLiujingFromFangzheng(fzCls);
                }
                for (OWLClass c : ljs) {
                    String frag2 = c.getIRI().getFragment();
                    if (SIX_CHANNEL_WHITELIST.contains(frag2)) {
                        fromFz.add(frag2);
                    }
                }
            }

            // 3) 并集
            Set<String> allLiujing = new HashSet<>(fromPatient);
            allLiujing.addAll(fromFz);
            List<String> mergedLiujing = allLiujing.stream()
                    .sorted().collect(Collectors.toList());

            log.info("六经来源: 患者直接={}, 方证反推={} (方证={})",
                    fromPatient, fromFz, fzForLiujing);

            // 4) 【新增】半表半里阴阳互斥消解
            List<String> liujingTypes = resolveLiujingMutex(mergedLiujing);

            // 5) 输出（原逻辑不变）
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
     * 规则：少阳（半表半里阳）与厥阴（半表半里阴）同时命中时，保留厥阴。
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
                log.info("[六经消解] 半表半里互斥（阴覆盖阳）：移除 {}，保留 {}",
                        yang, yin);
            }
        }
        if (!changed) return liujingTypes;
        return set.stream().sorted().collect(Collectors.toList());
    }

    /**
     * 遍历所有方证，从本体注解里读取六经归属。
     * Java 里不写任何注解属性名，逻辑是"凡注解值指向 Liujingbing 子类的，都视为该方证的六经"。
     */
    private void buildFangzhengLiujingMap() {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        Map<OWLClass, Set<OWLClass>> map = new HashMap<>();

        for (OWLClass fz : fangzhengSubclasses) {
            Set<OWLClass> liujings = new HashSet<>();

            // 路径 1：注解
            for (OWLAnnotationAssertionAxiom ax :
                    tbox.annotationAssertionAxioms(fz.getIRI()).collect(Collectors.toList())) {
                if (!(ax.getValue() instanceof IRI valueIri)) continue;
                OWLClass valueCls = tboxDf.getOWLClass(valueIri);
                if (liujingSubclasses.contains(valueCls)) {
                    liujings.add(valueCls);
                }
            }
            // 路径 2：subClassOf 父类
            for (OWLSubClassOfAxiom ax :
                    tbox.subClassAxiomsForSubClass(fz).collect(Collectors.toList())) {
                OWLClassExpression sc = ax.getSuperClass();
                if (sc.isOWLClass() && liujingSubclasses.contains(sc.asOWLClass())) {
                    liujings.add(sc.asOWLClass());
                }
            }

            if (!liujings.isEmpty()) {
                map.put(fz, liujings);
            }
        }
        fangzhengLiujingMap = map;
        log.info("[init] 方证-六经缓存: {} 个方证有六经归属", map.size());
    }

    /** 从本体实时读方证的六经（缓存 miss 时兜底） */
    private Set<OWLClass> readLiujingFromFangzheng(OWLClass fz) {
        Set<OWLClass> result = new HashSet<>();
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(fz.getIRI()).collect(Collectors.toList())) {
            if (!(ax.getValue() instanceof IRI valueIri)) continue;
            OWLClass valueCls = tboxDf.getOWLClass(valueIri);
            if (liujingSubclasses.contains(valueCls)) result.add(valueCls);
        }
        for (OWLSubClassOfAxiom ax :
                tbox.subClassAxiomsForSubClass(fz).collect(Collectors.toList())) {
            OWLClassExpression sc = ax.getSuperClass();
            if (sc.isOWLClass() && liujingSubclasses.contains(sc.asOWLClass())) {
                result.add(sc.asOWLClass());
            }
        }
        return result;
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

            Set<OWLClass> stage1Types = withLiujingReasoner(input, MiniContext::getPatientTypes);
            List<String> realizedMatches = withFangzhengReasoner(input, stage1Types, ctx ->
                    extractByMetaClass(ctx.getPatientTypes(), fangzhengSubclasses));
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
                    extractByMetaClass(ctx.getPatientTypes(), jianJiaSubclasses));

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
            formulaIri = toFullIri(formulaIri);

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
                        String full = toFullIri(h.toString());
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
            """.formatted(toFullIri(formulaIri));

        List<String[]> result = new ArrayList<>();
        for (Map<String, String> row :
                backendService.getObdaHandler().executeAboxQuery(sparql)) {
            String herb = row.get("herb");
            if (herb == null || herb.isBlank()) continue;
            String full = toFullIri(herb.trim());
            String label = row.get("label");
            if (label == null || label.isBlank()) {
                label = full.contains("#")
                        ? full.substring(full.lastIndexOf('#') + 1) : full;
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

    /**
     * 按八纲类名分组输出中文标签。
     * 直接遍历 OWLClass 集合，不依赖"两个 List 索引对齐"的脆弱前提。
     */
    private List<String> groupByFragments(Collection<OWLClass> bagangClasses, String... targets) {
        Set<String> targetSet = new HashSet<>(Arrays.asList(targets));
        List<String> result = new ArrayList<>();
        for (OWLClass c : bagangClasses) {
            String frag = c.getIRI().getFragment();
            if (targetSet.contains(frag)) {
                result.add(resolveClassLabel(c));
            }
        }
        return result;
    }

    private String queryLabel(String iri) {
        if (iri == null || iri.isBlank()) return "";
        String full = toFullIri(iri);
        String frag = full.contains("#")
                ? full.substring(full.lastIndexOf('#') + 1) : full;
        try {
            String sparql = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?label WHERE { <%s> rdfs:label ?label . }
                """.formatted(full);
            for (Map<String, String> row :
                    backendService.getObdaHandler().executeAboxQuery(sparql)) {
                String label = row.get("label");
                if (label != null && !label.isBlank()) return label.trim();
            }
        } catch (Exception e) {
            log.warn("queryLabel 失败: {}", full, e);
        }
        return frag;
    }

    private List<String> checkIncompatibilities(List<String> herbIris) {
        List<String> warnings = new ArrayList<>();
        if (herbIris == null || herbIris.size() < 2) return warnings;

        Set<String> herbs = herbIris.stream()
                .map(this::toFullIri)
                .filter(Objects::nonNull)
                .map(this::fragmentOf)
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
        miniTboxCache.keySet().removeIf(k -> k.startsWith(patientIri));
        miniContextCache.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(patientIri)) {
                try { e.getValue().dispose(); } catch (Exception ignored) {}
                return true;
            }
            return false;
        });
    }

    private String toFullIri(String iri) {
        if (iri == null || iri.isBlank()) return iri;
        if (iri.startsWith("http://") || iri.startsWith("https://")) return iri;
        return BASE_NS + iri;
    }

    private String frag(String iri) {
        String full = toFullIri(iri);
        String f = full.substring(BASE_NS.length());
        if (f.endsWith("_instance")) {
            f = f.substring(0, f.length() - "_instance".length());
        }
        return f;
    }

    private List<String> getList(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        if (v instanceof List<?> l)
            return l.stream().map(Object::toString).collect(Collectors.toList());
        return Collections.emptyList();
    }
}