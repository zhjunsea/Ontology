package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.QueryService;
import com.ocean.openlletresolver.ReasonerService;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

import openllet.owlapi.OpenlletReasonerFactory;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;

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

/**
 * TCM 本体推理 JobWorker（稳定基线版）
 *
 * 特征：
 *   - ModuleType.STAR
 *   - extractModule 只过滤 ABox 公理，不做 Fangzheng 过滤
 *   - buildPatientAxioms 补齐个体类型断言
 *   - ⭐ MiniContext 按 (患者, phase) 复用：同一阶段 realize 只做一次
 *
 * 性能：
 *   - 启动约 45~50 秒（不动）
 *   - 一次诊断约 10 秒（phase1 realize 一次 + phase2 realize 一次）
 */
@Component
@Profile("TCMBPMN")
public class TCMOntologyJobWorker {

    private static final Logger log = LoggerFactory.getLogger(TCMOntologyJobWorker.class);

    private static final boolean USE_MINI_REASONER = true;

    /** STAR 模块更安全 */
    private static final ModuleType MODULE_TYPE = ModuleType.STAR;

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    @Value("${ontology.phase1-path}")
    private String phase1OntologyPath;

    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;

    private BackendService backendService;
    private QueryService queryService;
    private OWLDataFactory tboxDf;

    // ==================== 元数据子类缓存 ====================

    private Set<OWLClass> bagangSubclasses;
    private Set<OWLClass> liujingSubclasses;
    private Set<OWLClass> fangzhengSubclasses;
    private Set<OWLClass> singleLiujingSubclasses;
    private Set<OWLClass> jianJiaSubclasses;

    // ==================== 模块抽取相关缓存 ====================

    /** 类 fragment → 自身 + 所有祖先 fragment（SubClassOf 传递闭包） */
    private Map<String, Set<String>> classAncestors;

    /** (患者 IRI + phase) → 迷你 TBox 公理集（跨调用复用） */
    private final Map<String, Set<OWLAxiom>> miniTboxCache = new ConcurrentHashMap<>();

    /** ⭐ (患者 IRI + phase) → MiniContext（含推理器），按阶段复用 realize 结果 */
    private final Map<String, MiniContext> miniContextCache = new ConcurrentHashMap<>();

    // ==================== 患者轻量缓存 ====================

    private final Map<String, PatientInput> patientInputs = new ConcurrentHashMap<>();
    private final Map<String, List<String>> stage1LiujingResults = new ConcurrentHashMap<>();

    // ==================== 常量 ====================

    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";
    private static final String HAS_SYMPTOM = BASE_NS + "you_zhengzhuang";
    private static final String HAS_PULSE = BASE_NS + "you_maixiang";
    private static final String HAS_TONGUE = BASE_NS + "you_shexiang";
    private static final String HAS_ABDOMINAL = BASE_NS + "you_fuzheng";
    private static final String HAS_PRESCRIPTION = BASE_NS + "you_chufang";

    // ==================== 内部类 ====================

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

    /** 迷你推理上下文。复用后由 diagnosis-explanation 清理。 */
    private record MiniContext(OWLOntologyManager manager,
                               OWLOntology ontology,
                               OWLDataFactory df,
                               OWLReasoner reasoner) {
        void dispose() {
            try { reasoner.dispose(); } catch (Exception ignored) {}
            try { manager.removeOntology(ontology); } catch (Exception ignored) {}
        }
    }

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        try {
            log.info("==================== 初始化开始 ====================");
            log.info("初始化 TCMOntologyJobWorker（模式: {}）...",
                    USE_MINI_REASONER ? "迷你本体+模块抽取" : "池化全量");
            log.info("  main-path:   {}", mainOntologyPath);
            log.info("  phase1-path: {}", phase1OntologyPath);

            long tBackend = System.currentTimeMillis();
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
            backendService = BackendService.getInstance(mainOntologyPath, OBDAHandler.getInstance());
            queryService = new QueryService(backendService);
            tboxDf = backendService.getOntologyService().gettBoxOntology()
                    .getOWLOntologyManager().getOWLDataFactory();
            log.info("[init] BackendService 初始化完成，耗时 {} ms",
                    System.currentTimeMillis() - tBackend);

            long tMeta = System.currentTimeMillis();
            bagangSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Bagang"));
            liujingSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Fangzheng"));
            singleLiujingSubclasses = new HashSet<>(liujingSubclasses);
            jianJiaSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "JianJiaZheng"));
            log.info("[init] 元数据扫描完成，耗时 {} ms", System.currentTimeMillis() - tMeta);
            log.info("八纲子类数: {}, 六经子类数: {}, 方证子类数: {}, 兼夹证子类数: {}",
                    bagangSubclasses.size(), liujingSubclasses.size(),
                    fangzhengSubclasses.size(), jianJiaSubclasses.size());

            long tAnc = System.currentTimeMillis();
            buildAncestorIndex();
            log.info("[init] 祖先索引构建完成，耗时 {} ms, 覆盖 {} 个类",
                    System.currentTimeMillis() - tAnc, classAncestors.size());

            log.info("==================== 初始化完成 ====================");
        } catch (Exception e) {
            log.error("初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    // ==================== 祖先闭包索引 ====================

    private void buildAncestorIndex() {
        classAncestors = new HashMap<>();

        Map<IRI, Set<OWLClass>> idx = backendService.getSubclassIndex();

        Map<String, Set<String>> direct = new HashMap<>();
        idx.forEach((parentIri, children) -> {
            String parent = parentIri.getFragment();
            for (OWLClass c : children) {
                direct.computeIfAbsent(c.getIRI().getFragment(), k -> new HashSet<>())
                        .add(parent);
            }
        });

        for (String frag : direct.keySet()) {
            Set<String> anc = new HashSet<>();
            Deque<String> stack = new ArrayDeque<>(direct.getOrDefault(frag, Set.of()));
            while (!stack.isEmpty()) {
                String p = stack.pop();
                if (!anc.add(p)) continue;
                stack.addAll(direct.getOrDefault(p, Set.of()));
            }
            anc.add(frag);
            classAncestors.put(frag, anc);
        }

        backendService.getOntologyService().gettBoxOntology().classesInSignature()
                .forEach(c -> classAncestors.computeIfAbsent(
                        c.getIRI().getFragment(), k -> Set.of(k)));
    }

    private Set<String> ancestorsOf(String frag) {
        return classAncestors.getOrDefault(frag, Set.of(frag));
    }

    // ==================== 模块抽取 ====================

    /**
     * 抽取迷你 TBox。STAR 抽取 + 只做 ABox 过滤。
     */
    private Set<OWLAxiom> extractModule(PatientInput input,
                                        List<String> liujingTypes,
                                        boolean includeFangzheng) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLEntity> seeds = new HashSet<>();

        Set<String> patientFrags = new HashSet<>();
        input.symptomIris.forEach(i -> patientFrags.add(frag(i)));
        input.pulseIris.forEach(i -> patientFrags.add(frag(i)));
        input.tongueIris.forEach(i -> patientFrags.add(frag(i)));
        input.fuzhengIris.forEach(i -> patientFrags.add(frag(i)));
        for (String f : patientFrags) {
            seeds.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }

        bagangSubclasses.forEach(seeds::add);
        liujingSubclasses.forEach(seeds::add);

        seeds.add(df.getOWLClass(IRI.create(BASE_NS + "Huanzhe")));

        seeds.add(df.getOWLObjectProperty(IRI.create(HAS_SYMPTOM)));
        seeds.add(df.getOWLObjectProperty(IRI.create(HAS_PULSE)));
        seeds.add(df.getOWLObjectProperty(IRI.create(HAS_TONGUE)));
        seeds.add(df.getOWLObjectProperty(IRI.create(HAS_ABDOMINAL)));

        int prefilteredCount = 0;
        if (includeFangzheng) {
            jianJiaSubclasses.forEach(seeds::add);
            Set<String> cand = prefilterFangzheng(patientFrags, liujingTypes);
            prefilteredCount = cand.size();
            for (String f : cand) {
                seeds.add(df.getOWLClass(IRI.create(BASE_NS + f)));
            }
        }

        SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(
                backendService.getOntologyService().getManager(),
                tbox,
                MODULE_TYPE);
        Set<OWLAxiom> module = extractor.extract(seeds);

        // 只过滤 ABox 公理
        Set<OWLAxiom> tboxOnly = module.stream()
                .filter(ax -> !(ax instanceof OWLClassAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLObjectPropertyAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLDataPropertyAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLSameIndividualAxiom))
                .filter(ax -> !(ax instanceof OWLDifferentIndividualsAxiom))
                .collect(Collectors.toSet());

        log.info("[模块抽取] 阶段={}, 患者症状类={} 个, 种子实体={} 个, 候选方证={} 个 → " +
                        "mini 公理 {} 条（过滤前 {}，全量 {}）",
                includeFangzheng ? "二" : "一",
                patientFrags.size(), seeds.size(), prefilteredCount,
                tboxOnly.size(), module.size(), tbox.getAxiomCount());
        return tboxOnly;
    }

    /**
     * 预筛方证：221 → 5~30。宽松策略：六经匹配 + 至少一个症状类在祖先集上有交集。
     */
    private Set<String> prefilterFangzheng(Set<String> patientFrags, List<String> liujingTypes) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        Set<String> ptAncestors = new HashSet<>();
        for (String f : patientFrags) {
            ptAncestors.addAll(ancestorsOf(f));
        }
        Set<String> ptLj = (liujingTypes == null) ? Set.of() : new HashSet<>(liujingTypes);

        Set<String> candidates = new HashSet<>();
        for (OWLClass fz : fangzhengSubclasses) {
            boolean ljOk = false;
            boolean symptomOk = false;

            for (OWLEquivalentClassesAxiom eq : tbox.equivalentClassesAxioms(fz)
                    .collect(Collectors.toList())) {
                for (OWLClassExpression e : eq.getClassExpressions()) {
                    if (e.equals(fz)) continue;

                    for (OWLClass c : e.classesInSignature().collect(Collectors.toList())) {
                        if (ptLj.contains(c.getIRI().getFragment())) {
                            ljOk = true;
                        }
                    }
                    for (OWLClass c : e.classesInSignature().collect(Collectors.toList())) {
                        String cf = c.getIRI().getFragment();
                        Set<String> ca = ancestorsOf(cf);
                        if (!Collections.disjoint(ca, ptAncestors)) {
                            symptomOk = true;
                            break;
                        }
                    }
                }
                if (ljOk && symptomOk) break;
            }

            if (ljOk && symptomOk) {
                candidates.add(fz.getIRI().getFragment());
            }
        }

        log.info("[预筛] 六经={}, 症状祖先集 {} 个 → 候选方证 {} / {} 个",
                ptLj, ptAncestors.size(), candidates.size(), fangzhengSubclasses.size());
        return candidates;
    }

    // ==================== 迷你推理上下文（按阶段复用） ====================

    /**
     * ⭐ 按 (患者, phase) 复用 MiniContext。
     * 首次调用创建本体 + 推理器 + realize；后续同一阶段直接复用。
     * 用完由 handleDiagnosisExplanation 统一 dispose。
     */
    private <T> T withMiniReasoner(PatientInput input,
                                   List<String> liujingTypes,
                                   boolean includeFangzheng,
                                   Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri + (includeFangzheng ? ":phase2" : ":phase1");

        MiniContext ctx = miniContextCache.computeIfAbsent(cacheKey, k -> {
            long t0 = System.currentTimeMillis();
            Set<OWLAxiom> miniTbox = miniTboxCache.computeIfAbsent(k,
                    kk -> extractModule(input, liujingTypes, includeFangzheng));

            OWLOntologyManager tmpMgr = OWLManager.createOWLOntologyManager();
            try {
                OWLOntology mini = tmpMgr.createOntology(IRI.create("urn:mini:" + System.nanoTime()));
                tmpMgr.addAxioms(mini, miniTbox);

                OWLDataFactory df = tmpMgr.getOWLDataFactory();
                tmpMgr.addAxioms(mini, buildPatientAxioms(df, input));
                if (includeFangzheng && liujingTypes != null && !liujingTypes.isEmpty()) {
                    tmpMgr.addAxioms(mini, materializeLiujing(df, input.patientIri, liujingTypes));
                }

                OWLReasoner r = new OpenlletReasonerFactory().createReasoner(mini);
                r.precomputeInferences(InferenceType.CLASS_ASSERTIONS);

                log.info("[MiniContext] 首次创建 {} | axioms={}, realize 完成, 耗时 {} ms",
                        k, mini.getAxiomCount(), System.currentTimeMillis() - t0);
                return new MiniContext(tmpMgr, mini, df, r);
            } catch (OWLOntologyCreationException e) {
                throw new RuntimeException("创建迷你本体失败", e);
            }
        });

        try {
            return action.apply(ctx);
        } catch (RuntimeException e) {
            // 失败时移除缓存，防止污染
            MiniContext removed = miniContextCache.remove(cacheKey);
            if (removed != null) removed.dispose();
            throw e;
        }
    }

    /**
     * 兼容旧模式：走池化推理器。USE_MINI_REASONER=false 时使用。
     */
    private <T> T withPooledReasoner(PatientInput input,
                                     List<String> liujingTypes,
                                     boolean includeFangzheng,
                                     Function<ReasonerService.PooledReasonerContext, T> action) {
        ReasonerService.PooledReasonerContext ctx = null;
        try {
            Set<OWLAxiom> axioms = new HashSet<>(buildPatientAxioms(tboxDf, input));
            if (includeFangzheng && liujingTypes != null && !liujingTypes.isEmpty()) {
                axioms.addAll(materializeLiujing(tboxDf, input.patientIri, liujingTypes));
            }
            ctx = backendService.borrowReasonerContext(60_000);
            ctx.addAxioms(axioms);
            ctx.flush();
            return action.apply(ctx);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("借用 Reasoner 上下文被中断", e);
        } finally {
            backendService.returnReasonerContext(ctx);
        }
    }

    // ==================== 公理构建工具 ====================

    /**
     * 构建患者 ABox 公理。
     * ⭐ extractModule 过滤掉了 ABox，患者引用的个体类型断言必须在此显式补齐。
     */
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
        return axioms;
    }

    /**
     * ⭐ 添加对象属性断言，同时补齐个体类型断言。
     */
    private void addAssertionsAndTypes(OWLDataFactory df, Set<OWLAxiom> acc,
                                       OWLObjectProperty prop, OWLNamedIndividual subj,
                                       List<String> objects) {
        if (objects == null || objects.isEmpty()) return;

        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        for (String iri : objects) {
            String fullIri = toFullIri(iri);
            OWLNamedIndividual obj = df.getOWLNamedIndividual(IRI.create(fullIri));

            acc.add(df.getOWLObjectPropertyAssertionAxiom(prop, subj, obj));

            tbox.classAssertionAxioms(obj).forEach(ca -> {
                OWLClassExpression ce = ca.getClassExpression();
                if (ce.isOWLClass()) {
                    acc.add(df.getOWLClassAssertionAxiom(ce.asOWLClass(), obj));
                }
            });
        }
    }

    private Set<OWLAxiom> materializeLiujing(OWLDataFactory df, String patientIri,
                                             List<String> liujingTypes) {
        Set<OWLAxiom> axioms = new HashSet<>();
        if (liujingTypes == null || liujingTypes.isEmpty()) return axioms;
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(patientIri));
        for (String fragment : liujingTypes) {
            OWLClass cls = df.getOWLClass(IRI.create(BASE_NS + fragment));
            axioms.add(df.getOWLClassAssertionAxiom(cls, patient));
        }
        return axioms;
    }

    // ==================== JobWorker：sizhen-input ====================

    @JobWorker(type = "sizhen-input", autoComplete = false)
    public void handleSizhenInput(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patientIri = BASE_NS + "Patient_" + job.getKey();

            // ⭐ 防御性清理：同 IRI 重入时先释放旧的 MiniContext
            MiniContext old1 = miniContextCache.remove(patientIri + ":phase1");
            if (old1 != null) old1.dispose();
            MiniContext old2 = miniContextCache.remove(patientIri + ":phase2");
            if (old2 != null) old2.dispose();

            PatientInput input = new PatientInput(
                    patientIri,
                    getList(vars, "symptomIris"),
                    getList(vars, "pulseIris"),
                    getList(vars, "tongueIris"),
                    getList(vars, "fuzhengIris"));

            boolean consistent;
            if (USE_MINI_REASONER) {
                consistent = withMiniReasoner(input, null, false,
                        ctx -> ctx.reasoner().isConsistent());
            } else {
                consistent = withPooledReasoner(input, null, false,
                        ctx -> ctx.reasoner.isConsistent());
            }

            patientInputs.put(patientIri, input);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("patientIri", patientIri);
            out.put("recorded", consistent);
            out.put("inconsistent", !consistent);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("四诊信息录入完成，患者: {}, 一致: {}", patientIri, consistent);
        } catch (Exception e) {
            log.error("sizhen-input 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("SIZHEN_INPUT_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== JobWorker：ontology-consistency-check ====================

    @JobWorker(type = "ontology-consistency-check", autoComplete = false)
    public void handleConsistencyCheck(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            boolean consistent;
            List<String> unsatisfiableClasses = Collections.emptyList();

            if (USE_MINI_REASONER) {
                Object[] result = withMiniReasoner(input, null, false, ctx -> {
                    boolean c = ctx.reasoner().isConsistent();
                    List<String> unsat = Collections.emptyList();
                    if (!c) {
                        Node<OWLClass> node = ctx.reasoner().getUnsatisfiableClasses();
                        unsat = node.getEntities().stream()
                                .map(x -> x.getIRI().toString())
                                .collect(Collectors.toList());
                    }
                    return new Object[]{c, unsat};
                });
                consistent = (Boolean) result[0];
                @SuppressWarnings("unchecked")
                List<String> unsat = (List<String>) result[1];
                unsatisfiableClasses = unsat;
            } else {
                consistent = withPooledReasoner(input, null, false, ctx -> {
                    boolean c = ctx.reasoner.isConsistent();
                    if (!c) {
                        Node<OWLClass> unsat = ctx.reasoner.getUnsatisfiableClasses();
                        log.warn("不可满足类: {}", unsat.getEntities());
                    }
                    return c;
                });
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("consistent", consistent);
            out.put("unsatisfiableClasses", unsatisfiableClasses);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("一致性检查完成，consistent={}", consistent);
        } catch (Exception e) {
            log.error("一致性检查失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("CONSISTENCY_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== JobWorker：bagang-classification ====================

    @JobWorker(type = "bagang-classification", autoComplete = false)
    public void handleBagangClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            List<String> bagangTypes;

            if (USE_MINI_REASONER) {
                bagangTypes = withMiniReasoner(input, null, false, ctx -> {
                    OWLNamedIndividual patient = ctx.df()
                            .getOWLNamedIndividual(IRI.create(patientIri));
                    long t0 = System.currentTimeMillis();
                    Set<OWLClass> types = ctx.reasoner().getTypes(patient, false).getFlattened();
                    log.info("[阶段一/迷你] 八纲 getTypes 耗时 {} ms, mini公理={}, 类型数={}",
                            System.currentTimeMillis() - t0,
                            ctx.ontology().getAxiomCount(), types.size());
                    return types.stream()
                            .filter(bagangSubclasses::contains)
                            .map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                });
            } else {
                bagangTypes = withPooledReasoner(input, null, false, ctx -> {
                    OWLNamedIndividual patient = tboxDf
                            .getOWLNamedIndividual(IRI.create(patientIri));
                    Set<OWLClass> types = ctx.reasoner.getTypes(patient, false).getFlattened();
                    return types.stream()
                            .filter(bagangSubclasses::contains)
                            .map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                });
            }

            List<String> biaoli = extractBagang(bagangTypes, "Biao", "Li", "BanbiaoBanli");
            List<String> hanre = extractBagang(bagangTypes, "Han", "Re");
            List<String> xushi = extractBagang(bagangTypes, "Xu", "Shi");
            List<String> yinyang = extractBagang(bagangTypes, "Yin", "Yang");

            Map<String, Object> bagangResult = new LinkedHashMap<>();
            bagangResult.put("表里", biaoli);
            bagangResult.put("寒热", hanre);
            bagangResult.put("虚实", xushi);
            bagangResult.put("阴阳", yinyang);
            bagangResult.put("bagangTypes", bagangTypes);
            bagangResult.put("bagangTypesCn", backendService.resolveLabels(bagangTypes, BASE_NS));
            bagangResult.put("complete",
                    !biaoli.isEmpty() && !hanre.isEmpty() && !xushi.isEmpty() && !yinyang.isEmpty());

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("bagangResult", bagangResult)).send().join();
            log.info("八纲分类完成: {}", bagangResult);
        } catch (Exception e) {
            log.error("八纲分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("BAGANG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    private List<String> extractBagang(List<String> types, String... candidates) {
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
        Set<String> set = new HashSet<>(Arrays.asList(candidates));
        List<String> result = new ArrayList<>();
        for (String t : types) {
            if (set.contains(t) && map.containsKey(t)) result.add(map.get(t));
        }
        return result;
    }

    // ==================== JobWorker：liujing-classification ====================

    @JobWorker(type = "liujing-classification", autoComplete = false)
    public void handleLiujingClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            List<String> liujingTypes;

            if (USE_MINI_REASONER) {
                liujingTypes = withMiniReasoner(input, null, false, ctx -> {
                    OWLNamedIndividual patient = ctx.df()
                            .getOWLNamedIndividual(IRI.create(patientIri));
                    Set<OWLClass> types = ctx.reasoner().getTypes(patient, false).getFlattened();
                    return types.stream()
                            .filter(singleLiujingSubclasses::contains)
                            .map(c -> c.getIRI().getFragment())
                            .sorted()
                            .collect(Collectors.toList());
                });
            } else {
                liujingTypes = withPooledReasoner(input, null, false, ctx -> {
                    OWLNamedIndividual patient = tboxDf
                            .getOWLNamedIndividual(IRI.create(patientIri));
                    Set<OWLClass> types = ctx.reasoner.getTypes(patient, false).getFlattened();
                    return types.stream()
                            .filter(singleLiujingSubclasses::contains)
                            .map(c -> c.getIRI().getFragment())
                            .sorted()
                            .collect(Collectors.toList());
                });
            }

            String sixChannel;
            String combinedDiseaseMark = null;
            boolean isCombined = liujingTypes.size() > 1;
            if (liujingTypes.isEmpty()) {
                sixChannel = "六经难定";
            } else {
                sixChannel = liujingTypes.get(0);
                if (isCombined) combinedDiseaseMark = buildCombinedDiseaseMark(liujingTypes);
            }

            stage1LiujingResults.put(patientIri, liujingTypes);

            if (liujingTypes.isEmpty()) {
                log.warn("[阶段二] 六经难定，后续方证分类将使用降级模式");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sixChannel", sixChannel);
            out.put("sixChannelCn", backendService.resolveLabel(sixChannel, BASE_NS));
            out.put("liujingTypes", liujingTypes);
            out.put("liujingTypesCn", backendService.resolveLabels(liujingTypes, BASE_NS));
            out.put("isCombinedChannel", isCombined);
            out.put("combinedDiseaseMark", combinedDiseaseMark);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("六经分类完成: {} (合病: {}, 标记: {})",
                    liujingTypes, isCombined, combinedDiseaseMark);
        } catch (Exception e) {
            log.error("六经分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("LIUJING_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
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
                List.of("Shaoyangbing", "Yangmingbing"))) return "少阳阳明合病";

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

    // ==================== JobWorker：fangzheng-classification ====================

    @JobWorker(type = "fangzheng-classification", autoComplete = false)
    public void handleFangzhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patientIri = (String) vars.get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            List<String> liujingTypes = stage1LiujingResults.get(patientIri);
            if (liujingTypes == null) throw new IllegalStateException("六经结果缓存丢失: " + patientIri);

            Set<String> patientFacts = new HashSet<>();
            patientFacts.addAll(getList(vars, "symptomIris"));
            patientFacts.addAll(getList(vars, "pulseIris"));
            patientFacts.addAll(getList(vars, "tongueIris"));
            patientFacts.addAll(getList(vars, "fuzhengIris"));

            String fangzheng;
            List<String> fangzhengTypes;
            List<String> candidateFragments;
            Map<String, Integer> necMap = new LinkedHashMap<>();
            Map<String, Integer> posMap = new LinkedHashMap<>();

            if (USE_MINI_REASONER) {
                Object[] r = withMiniReasoner(input, liujingTypes, true, ctx -> {
                    OWLNamedIndividual patient = ctx.df()
                            .getOWLNamedIndividual(IRI.create(patientIri));
                    long t0 = System.currentTimeMillis();
                    Set<OWLClass> types = ctx.reasoner().getTypes(patient, false).getFlattened();
                    log.info("[阶段二/迷你] 方证 getTypes 耗时 {} ms, mini公理={}, 类型数={}",
                            System.currentTimeMillis() - t0,
                            ctx.ontology().getAxiomCount(), types.size());

                    List<OWLClass> fzClasses = types.stream()
                            .filter(fangzhengSubclasses::contains)
                            .collect(Collectors.toList());

                    return new Object[]{types, fzClasses, ctx.ontology()};
                });

                @SuppressWarnings("unchecked")
                Set<OWLClass> types = (Set<OWLClass>) r[0];
                @SuppressWarnings("unchecked")
                List<OWLClass> fangzhengClasses = (List<OWLClass>) r[1];
                OWLOntology mini = (OWLOntology) r[2];

                if (fangzhengClasses.isEmpty()) {
                    fangzheng = "方证未定";
                    List<OWLClass> all = new ArrayList<>(fangzhengSubclasses);
                    for (OWLClass c : all) {
                        necMap.put(c.getIRI().getFragment(), countNec(mini, c, patientFacts));
                        posMap.put(c.getIRI().getFragment(), countPos(mini, c, patientFacts));
                    }
                    List<OWLClass> sorted = sortCandidates(all, necMap, posMap);
                    candidateFragments = sorted.stream()
                            .map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                    necMap = buildSortedScoreMap(sorted, necMap);
                    posMap = buildSortedScoreMap(sorted, posMap);
                    fangzhengTypes = new ArrayList<>();
                } else {
                    for (OWLClass c : fangzhengClasses) {
                        necMap.put(c.getIRI().getFragment(), countNec(mini, c, patientFacts));
                        posMap.put(c.getIRI().getFragment(), countPos(mini, c, patientFacts));
                    }
                    List<OWLClass> sorted = sortCandidates(fangzhengClasses, necMap, posMap);
                    final Map<String, Integer> fNec = necMap;
                    final Map<String, Integer> fPos = posMap;
                    int maxNec = fNec.get(sorted.get(0).getIRI().getFragment());
                    int maxPos = fPos.get(sorted.get(0).getIRI().getFragment());
                    List<OWLClass> top = sorted.stream()
                            .filter(c -> fNec.get(c.getIRI().getFragment()) == maxNec
                                    && fPos.get(c.getIRI().getFragment()) == maxPos)
                            .collect(Collectors.toList());
                    fangzheng = top.get(0).getIRI().getFragment();
                    fangzhengTypes = top.stream().map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                    candidateFragments = sorted.stream().map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                    necMap = buildSortedScoreMap(sorted, necMap);
                    posMap = buildSortedScoreMap(sorted, posMap);
                }
            } else {
                Object[] r = withPooledReasoner(input, liujingTypes, true, ctx -> {
                    OWLNamedIndividual patient = tboxDf
                            .getOWLNamedIndividual(IRI.create(patientIri));
                    Set<OWLClass> types = ctx.reasoner.getTypes(patient, false).getFlattened();
                    List<OWLClass> fzClasses = types.stream()
                            .filter(fangzhengSubclasses::contains)
                            .collect(Collectors.toList());
                    return new Object[]{fzClasses, ctx.ontology};
                });
                @SuppressWarnings("unchecked")
                List<OWLClass> fzClasses = (List<OWLClass>) r[0];
                OWLOntology ont = (OWLOntology) r[1];

                if (fzClasses.isEmpty()) {
                    fangzheng = "方证未定";
                    List<OWLClass> all = new ArrayList<>(fangzhengSubclasses);
                    for (OWLClass c : all) {
                        necMap.put(c.getIRI().getFragment(), countNec(ont, c, patientFacts));
                        posMap.put(c.getIRI().getFragment(), countPos(ont, c, patientFacts));
                    }
                    List<OWLClass> sorted = sortCandidates(all, necMap, posMap);
                    candidateFragments = sorted.stream().map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                    necMap = buildSortedScoreMap(sorted, necMap);
                    posMap = buildSortedScoreMap(sorted, posMap);
                    fangzhengTypes = new ArrayList<>();
                } else {
                    for (OWLClass c : fzClasses) {
                        necMap.put(c.getIRI().getFragment(), countNec(ont, c, patientFacts));
                        posMap.put(c.getIRI().getFragment(), countPos(ont, c, patientFacts));
                    }
                    List<OWLClass> sorted = sortCandidates(fzClasses, necMap, posMap);
                    final Map<String, Integer> fNec = necMap;
                    final Map<String, Integer> fPos = posMap;
                    int maxNec = fNec.get(sorted.get(0).getIRI().getFragment());
                    int maxPos = fPos.get(sorted.get(0).getIRI().getFragment());
                    List<OWLClass> top = sorted.stream()
                            .filter(c -> fNec.get(c.getIRI().getFragment()) == maxNec
                                    && fPos.get(c.getIRI().getFragment()) == maxPos)
                            .collect(Collectors.toList());
                    fangzheng = top.get(0).getIRI().getFragment();
                    fangzhengTypes = top.stream().map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                    candidateFragments = sorted.stream().map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                    necMap = buildSortedScoreMap(sorted, necMap);
                    posMap = buildSortedScoreMap(sorted, posMap);
                }
            }

            Map<String, Integer> necCn = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : necMap.entrySet())
                necCn.put(backendService.resolveLabel(e.getKey(), BASE_NS), e.getValue());
            Map<String, Integer> posCn = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : posMap.entrySet())
                posCn.put(backendService.resolveLabel(e.getKey(), BASE_NS), e.getValue());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fangzheng", fangzheng);
            out.put("fangzhengCn", backendService.resolveLabel(fangzheng, BASE_NS));
            out.put("fangzhengTypes", fangzhengTypes);
            out.put("candidateFangzhengs", candidateFragments);
            out.put("candidateFangzhengsCn", backendService.resolveLabels(candidateFragments, BASE_NS));
            out.put("candidateNecessaryScores", necMap);
            out.put("candidateNecessaryScoresCn", necCn);
            out.put("candidateScores", posMap);
            out.put("candidateScoresCn", posCn);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("方证分类完成: {} (候选数: {}, 六经: {})",
                    fangzheng, candidateFragments.size(),
                    liujingTypes.isEmpty() ? "难定" : liujingTypes);
        } catch (Exception e) {
            log.error("方证分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("FANGZHENG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    private List<OWLClass> sortCandidates(List<OWLClass> classes,
                                          Map<String, Integer> nec, Map<String, Integer> pos) {
        return classes.stream().sorted((c1, c2) -> {
            int n = nec.get(c2.getIRI().getFragment()).compareTo(nec.get(c1.getIRI().getFragment()));
            if (n != 0) return n;
            return pos.get(c2.getIRI().getFragment()).compareTo(pos.get(c1.getIRI().getFragment()));
        }).collect(Collectors.toList());
    }

    private Map<String, Integer> buildSortedScoreMap(List<OWLClass> sorted,
                                                     Map<String, Integer> orig) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (OWLClass c : sorted) {
            String name = c.getIRI().getFragment();
            m.put(name, orig.get(name));
        }
        return m;
    }

    private int countNec(OWLOntology ont, OWLClass cls, Set<String> facts) {
        Set<OWLClassExpression> fillers = new HashSet<>();
        for (OWLEquivalentClassesAxiom ax : ont.getEquivalentClassesAxioms(cls)) {
            for (OWLClassExpression expr : ax.getClassExpressions()) {
                if (expr.equals(cls)) continue;
                collectSomeFillers(expr, fillers);
            }
        }
        int c = 0;
        for (OWLClassExpression f : fillers) {
            if (f instanceof OWLClass fc) {
                if (!fc.isOWLThing() && !fc.isOWLNothing()
                        && facts.contains(fc.getIRI().toString() + "_instance")) c++;
            }
        }
        return c;
    }

    private void collectSomeFillers(OWLClassExpression expr, Set<OWLClassExpression> acc) {
        if (expr instanceof OWLObjectSomeValuesFrom svf) {
            acc.add(svf.getFiller());
        } else if (expr instanceof OWLObjectIntersectionOf inter) {
            for (OWLClassExpression op : inter.getOperands())
                collectSomeFillers(op, acc);
        }
    }

    private int countPos(OWLOntology ont, OWLClass cls, Set<String> facts) {
        int c = 0;
        for (OWLAnnotationAssertionAxiom ax : ont.getAnnotationAssertionAxioms(cls.getIRI())) {
            if (ax.getProperty().getIRI().getFragment().equals("possibleSymptom")
                    && ax.getValue() instanceof IRI) {
                String s = ((IRI) ax.getValue()).toString();
                String inst = s.endsWith("_instance") ? s : s + "_instance";
                if (facts.contains(inst)) c++;
            }
        }
        return c;
    }

    // ==================== JobWorker：jianjiazheng-classification ====================

    @JobWorker(type = "jianjiazheng-classification", autoComplete = false)
    public void handleJianJiaZhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            List<String> liujingTypes = stage1LiujingResults.get(patientIri);
            if (liujingTypes == null) throw new IllegalStateException("六经结果缓存丢失: " + patientIri);

            List<String> jianJiaTypes;

            if (USE_MINI_REASONER) {
                jianJiaTypes = withMiniReasoner(input, liujingTypes, true, ctx -> {
                    OWLNamedIndividual patient = ctx.df()
                            .getOWLNamedIndividual(IRI.create(patientIri));
                    long t0 = System.currentTimeMillis();
                    Set<OWLClass> types = ctx.reasoner().getTypes(patient, false).getFlattened();
                    log.info("[阶段二/迷你] 兼夹证 getTypes 耗时 {} ms, mini公理={}",
                            System.currentTimeMillis() - t0, ctx.ontology().getAxiomCount());
                    return types.stream()
                            .filter(jianJiaSubclasses::contains)
                            .map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                });
            } else {
                jianJiaTypes = withPooledReasoner(input, liujingTypes, true, ctx -> {
                    OWLNamedIndividual patient = tboxDf
                            .getOWLNamedIndividual(IRI.create(patientIri));
                    Set<OWLClass> types = ctx.reasoner.getTypes(patient, false).getFlattened();
                    return types.stream()
                            .filter(jianJiaSubclasses::contains)
                            .map(c -> c.getIRI().getFragment())
                            .collect(Collectors.toList());
                });
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jianJiaZhengs", jianJiaTypes);
            out.put("jianJiaZhengsCn", backendService.resolveLabels(jianJiaTypes, BASE_NS));
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("兼夹证分类完成: {}", jianJiaTypes);
        } catch (Exception e) {
            log.error("兼夹证分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("JIANJIAZHENG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== JobWorker：prescription-recommendation ====================

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
            List<String> herbIris = queryHerbsForFormula(formulaIri);

            List<String> addHerbIris = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> jianJiaZhengs = (List<String>) vars.get("jianJiaZhengs");
            if (jianJiaZhengs != null) {
                for (String jz : jianJiaZhengs) {
                    OWLClass jzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + jz));
                    for (IRI h : getAddHerbs(jzCls))
                        addHerbIris.add(toFullIri(h.toString()));
                }
            }

            List<String> allHerbs = new ArrayList<>(herbIris);
            allHerbs.addAll(addHerbIris);
            List<String> warnings = checkIncompatibilities(allHerbs);

            List<String> herbCn = herbIris.stream().map(this::queryLabel).collect(Collectors.toList());
            List<String> addHerbCn = addHerbIris.stream().map(this::queryLabel).collect(Collectors.toList());
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
            log.info("方剂推荐完成: {}，药物: {}, 加减: {}, 警告: {}",
                    formulaIri, herbIris, addHerbIris, warnings);
        } catch (Exception e) {
            log.error("方剂推荐失败", e);
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
            if (hv.getProperty().asOWLObjectProperty().getIRI().toString().equals(HAS_PRESCRIPTION)) {
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

    private List<String> queryHerbsForFormula(String formulaIri) {
        String sparql = """
            PREFIX : <http://www.tcm-classics.org/jingfang#>
            SELECT ?herb WHERE { <%s> :you_yaowu ?herb . }
            """.formatted(toFullIri(formulaIri));
        return backendService.getObdaHandler().executeAboxQuery(sparql).stream()
                .map(r -> r.get("herb")).filter(Objects::nonNull)
                .map(this::toFullIri).collect(Collectors.toList());
    }

    private String queryLabel(String iri) {
        String full = toFullIri(iri);
        String sparql = """
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?label WHERE { <%s> rdfs:label ?label . }
            """.formatted(full);
        List<Map<String, String>> rows = backendService.getObdaHandler().executeAboxQuery(sparql);
        if (rows.isEmpty() || rows.get(0).get("label") == null)
            return full.contains("#") ? full.substring(full.lastIndexOf('#') + 1) : full;
        return rows.get(0).get("label");
    }

    private List<String> checkIncompatibilities(List<String> herbIris) {
        List<String> warnings = new ArrayList<>();
        if (herbIris == null || herbIris.size() < 2) return warnings;
        Set<String> set = herbIris.stream().map(this::toFullIri).collect(Collectors.toSet());

        String antagQ = """
            PREFIX : <http://www.tcm-classics.org/jingfang#>
            SELECT ?a ?b WHERE { ?a :antagonistic ?b . }
            """;
        for (Map<String, String> r : backendService.getObdaHandler().executeAboxQuery(antagQ)) {
            String a = toFullIri(r.get("a")), b = toFullIri(r.get("b"));
            if (set.contains(a) && set.contains(b))
                warnings.add("十八反：" + queryLabel(a) + " 反 " + queryLabel(b));
        }
        String fearQ = """
            PREFIX : <http://www.tcm-classics.org/jingfang#>
            SELECT ?a ?b WHERE { ?a :fearing ?b . }
            """;
        for (Map<String, String> r : backendService.getObdaHandler().executeAboxQuery(fearQ)) {
            String a = toFullIri(r.get("a")), b = toFullIri(r.get("b"));
            if (set.contains(a) && set.contains(b))
                warnings.add("十九畏：" + queryLabel(a) + " 畏 " + queryLabel(b));
        }
        return warnings;
    }

    // ==================== JobWorker：diagnosis-explanation ====================

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

            String explanation = String.format("六经：%s，方证：%s，推荐方剂：%s。",
                    liujingDisplay,
                    fangzhengDisplay != null ? fangzhengDisplay : "未定",
                    formulaDisplay != null ? formulaDisplay : "未定");

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("explanation", explanation);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("诊断解释完成：{}", explanation);

            // ⭐ 清理患者缓存 + MiniContext（释放推理器和临时本体）
            if (patientIri != null) {
                patientInputs.remove(patientIri);
                stage1LiujingResults.remove(patientIri);
                miniTboxCache.remove(patientIri + ":phase1");
                miniTboxCache.remove(patientIri + ":phase2");

                MiniContext c1 = miniContextCache.remove(patientIri + ":phase1");
                if (c1 != null) c1.dispose();
                MiniContext c2 = miniContextCache.remove(patientIri + ":phase2");
                if (c2 != null) c2.dispose();

                log.info("患者缓存 + MiniContext 已清理: {}", patientIri);
            }
        } catch (Exception e) {
            log.error("诊断解释失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("EXPLANATION_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== 工具方法 ====================

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