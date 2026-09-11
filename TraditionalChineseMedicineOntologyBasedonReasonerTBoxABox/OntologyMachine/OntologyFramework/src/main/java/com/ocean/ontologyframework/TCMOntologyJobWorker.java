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
 * TCM 本体推理 JobWorker（推理机 Openllet 全权负责版）
 *
 * 设计原则：
 *   1. 所有分类判断（八纲/六经/方证/兼夹证）由 Openllet 输出。
 *      应用层只按元类过滤，不做硬编码类名比对、不做手工评分。
 *
 *   2. 方证定义里不再手工挂 rdfs:subClassOf #SomeLiujing。
 *      方证的等价类直接写八纲（如 BanbiaoBanli ∩ Yang），
 *      推理器会自动推出 Xiaochaihutangzheng ⊑ Shaoyangbing 这样的层次关系。
 *
 *   3. 六经顶类用推理器过滤：是 Liujingbing 的子类，且不是 Fangzheng 的子类。
 *      这样即使推理器把方证判为六经的子类，六经列表依然干净。
 *
 *   4. phase1/phase2 共用 MiniContext：一次 realize 覆盖全程。
 */
@Component
@Profile("TCMBPMN")
public class TCMOntologyJobWorker {

    private static final Logger log = LoggerFactory.getLogger(TCMOntologyJobWorker.class);

    private static final boolean USE_MINI_REASONER = true;

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

    // ==================== 分类元数据（从 TBox 读取） ====================

    private Set<OWLClass> bagangSubclasses;
    private Set<OWLClass> liujingSubclasses;
    /** ⭐ 六经顶类：推理器过滤后（是 Liujingbing 子类，且不是 Fangzheng 子类） */
    private Set<OWLClass> liujingTopClasses;
    private Set<OWLClass> fangzhengSubclasses;
    private Set<OWLClass> jianJiaSubclasses;

    // ==================== 缓存 ====================

    private final Map<String, Set<OWLAxiom>> miniTboxCache = new ConcurrentHashMap<>();
    /** 患者 IRI → MiniContext（八纲/六经/方证/兼夹证共用） */
    private final Map<String, MiniContext> miniContextCache = new ConcurrentHashMap<>();

    private final Map<String, PatientInput> patientInputs = new ConcurrentHashMap<>();

    // ==================== 常量 ====================

    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";
    private static final String HAS_SYMPTOM = BASE_NS + "you_zhengzhuang";
    private static final String HAS_PULSE = BASE_NS + "you_maixiang";
    private static final String HAS_TONGUE = BASE_NS + "you_shexiang";
    private static final String HAS_ABDOMINAL = BASE_NS + "you_fuzheng";
    private static final String HAS_PRESCRIPTION = BASE_NS + "you_chufang";

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

        /**
         * ⭐ 一次 realize，缓存患者的所有类型。
         * 八纲、六经、方证、兼夹证都从这里出。
         */
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

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        try {
            log.info("==================== 初始化开始 ====================");
            log.info("初始化 TCMOntologyJobWorker（模式: {}）...",
                    USE_MINI_REASONER ? "迷你本体+模块抽取" : "池化全量");

            long tBackend = System.currentTimeMillis();
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
            backendService = BackendService.getInstance(mainOntologyPath, OBDAHandler.getInstance());
            queryService = new QueryService(backendService);
            tboxDf = backendService.getOntologyService().gettBoxOntology()
                    .getOWLOntologyManager().getOWLDataFactory();
            log.info("[init] BackendService 初始化完成，耗时 {} ms",
                    System.currentTimeMillis() - tBackend);

            long tMeta = System.currentTimeMillis();

            // 分类元数据（从 TBox 读取，不硬编码类名）
            bagangSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Bagang"));
            liujingSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Liujingbing"));
            fangzhengSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "Fangzheng"));
            jianJiaSubclasses = backendService.getAllNamedSubclasses(IRI.create(BASE_NS + "JianJiaZheng"));

            // ⭐ 六经顶类用推理器判断：是 Liujingbing 子类，且不是 Fangzheng 子类
            // 这样即使推理器把方证（如 Xiaochaihutangzheng）判为六经子类，也被过滤掉
            OWLReasoner globalReasoner = backendService.getReasonerService().getReasoner();
            OWLClass liujingRoot = tboxDf.getOWLClass(IRI.create(BASE_NS + "Liujingbing"));
            OWLClass fangzhengRoot = tboxDf.getOWLClass(IRI.create(BASE_NS + "Fangzheng"));

            liujingTopClasses = globalReasoner.getSubClasses(liujingRoot, true)
                    .getFlattened().stream()
                    .filter(c -> !c.isOWLNothing() && !c.isOWLThing())
                    .filter(c -> !globalReasoner.isEntailed(
                            tboxDf.getOWLSubClassOfAxiom(c, fangzhengRoot)))
                    .collect(Collectors.toSet());

            log.info("[init] 元数据扫描完成，耗时 {} ms", System.currentTimeMillis() - tMeta);
            log.info("八纲子类={}, 六经全部子树={}, 六经顶类={}, 方证={}, 兼夹证={}",
                    bagangSubclasses.size(), liujingSubclasses.size(),
                    liujingTopClasses.size(), fangzhengSubclasses.size(),
                    jianJiaSubclasses.size());
            log.info("六经顶类（推理器过滤后）: {}", liujingTopClasses.stream()
                    .map(c -> c.getIRI().getFragment()).sorted().collect(Collectors.toList()));

            log.info("==================== 初始化完成 ====================");
        } catch (Exception e) {
            log.error("初始化失败", e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    // ==================== 模块抽取 ====================

    /**
     * 抽取迷你 TBox。种子：患者症状类 + 八纲 + 六经（含全部子树）+ 兼夹证 + Huanzhe + 四诊属性。
     * STAR 抽取向依赖闭包，会把所有相关方证定义一并拉入。
     * 抽完过滤 ABox 公理，只保留 TBox。
     */
    private Set<OWLAxiom> extractModule(PatientInput input) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLEntity> seeds = new HashSet<>();

        // 1) 患者症状类
        Set<String> patientFrags = new HashSet<>();
        input.symptomIris.forEach(i -> patientFrags.add(frag(i)));
        input.pulseIris.forEach(i -> patientFrags.add(frag(i)));
        input.tongueIris.forEach(i -> patientFrags.add(frag(i)));
        input.fuzhengIris.forEach(i -> patientFrags.add(frag(i)));
        for (String f : patientFrags) {
            seeds.add(df.getOWLClass(IRI.create(BASE_NS + f)));
        }

        // 2) 分类元数据作为种子（liujingSubclasses 会拉入所有方证定义）
        bagangSubclasses.forEach(seeds::add);
        liujingSubclasses.forEach(seeds::add);
        jianJiaSubclasses.forEach(seeds::add);

        // 3) 顶层类
        seeds.add(df.getOWLClass(IRI.create(BASE_NS + "Huanzhe")));

        // 4) 四诊属性
        seeds.add(df.getOWLObjectProperty(IRI.create(HAS_SYMPTOM)));
        seeds.add(df.getOWLObjectProperty(IRI.create(HAS_PULSE)));
        seeds.add(df.getOWLObjectProperty(IRI.create(HAS_TONGUE)));
        seeds.add(df.getOWLObjectProperty(IRI.create(HAS_ABDOMINAL)));

        SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(
                backendService.getOntologyService().getManager(),
                tbox,
                MODULE_TYPE);
        Set<OWLAxiom> module = extractor.extract(seeds);

        // 只过滤 ABox 公理，保留全部 TBox
        Set<OWLAxiom> tboxOnly = module.stream()
                .filter(ax -> !(ax instanceof OWLClassAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLObjectPropertyAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLDataPropertyAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLSameIndividualAxiom))
                .filter(ax -> !(ax instanceof OWLDifferentIndividualsAxiom))
                .collect(Collectors.toSet());

        log.info("[模块抽取] 患者症状类={} 个, 种子实体={} 个 → mini 公理 {} 条（过滤前 {}，全量 {}）",
                patientFrags.size(), seeds.size(),
                tboxOnly.size(), module.size(), tbox.getAxiomCount());
        return tboxOnly;
    }

    // ==================== 迷你推理上下文（按患者复用） ====================

    private <T> T withMiniReasoner(PatientInput input, Function<MiniContext, T> action) {
        final String cacheKey = input.patientIri;

        MiniContext ctx = miniContextCache.computeIfAbsent(cacheKey, k -> {
            long t0 = System.currentTimeMillis();
            Set<OWLAxiom> miniTbox = miniTboxCache.computeIfAbsent(k, kk -> extractModule(input));

            OWLOntologyManager tmpMgr = OWLManager.createOWLOntologyManager();
            try {
                OWLOntology mini = tmpMgr.createOntology(IRI.create("urn:mini:" + System.nanoTime()));
                tmpMgr.addAxioms(mini, miniTbox);

                OWLDataFactory df = tmpMgr.getOWLDataFactory();
                tmpMgr.addAxioms(mini, buildPatientAxioms(df, input));

                OWLReasoner r = new OpenlletReasonerFactory().createReasoner(mini);
                r.precomputeInferences(InferenceType.CLASS_ASSERTIONS);

                log.info("[MiniContext] 首次创建 {} | axioms={}, realize 完成, 耗时 {} ms",
                        k, mini.getAxiomCount(), System.currentTimeMillis() - t0);
                return new MiniContext(tmpMgr, mini, df, r, input.patientIri);
            } catch (OWLOntologyCreationException e) {
                throw new RuntimeException("创建迷你本体失败", e);
            }
        });

        try {
            return action.apply(ctx);
        } catch (RuntimeException e) {
            MiniContext removed = miniContextCache.remove(cacheKey);
            if (removed != null) removed.dispose();
            throw e;
        }
    }

    private <T> T withPooledReasoner(PatientInput input,
                                     Function<ReasonerService.PooledReasonerContext, T> action) {
        ReasonerService.PooledReasonerContext ctx = null;
        try {
            ctx = backendService.borrowReasonerContext(60_000);
            ctx.addAxioms(buildPatientAxioms(tboxDf, input));
            ctx.flush();
            return action.apply(ctx);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("借用 Reasoner 上下文被中断", e);
        } finally {
            backendService.returnReasonerContext(ctx);
        }
    }

    // ==================== 患者 ABox 构建 ====================

    /**
     * 患者 ABox。
     * extractModule 过滤了全部 ABox，患者引用的个体类型断言必须在此显式补齐。
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

    // ==================== 分类提取工具（只做元类过滤，不做匹配） ====================

    /**
     * ⭐ 从推理器输出中按元类过滤。不做任何判断，只是分类。
     */
    private List<String> extractByMetaClass(Set<OWLClass> allTypes,
                                            Set<OWLClass> metaClassSet) {
        return allTypes.stream()
                .filter(metaClassSet::contains)
                .map(c -> c.getIRI().getFragment())
                .sorted()
                .collect(Collectors.toList());
    }

    // ==================== JobWorker：sizhen-input ====================

    @JobWorker(type = "sizhen-input", autoComplete = false)
    public void handleSizhenInput(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String patientIri = BASE_NS + "Patient_" + job.getKey();

            MiniContext old = miniContextCache.remove(patientIri);
            if (old != null) old.dispose();

            PatientInput input = new PatientInput(
                    patientIri,
                    getList(vars, "symptomIris"),
                    getList(vars, "pulseIris"),
                    getList(vars, "tongueIris"),
                    getList(vars, "fuzhengIris"));

            boolean consistent;
            if (USE_MINI_REASONER) {
                consistent = withMiniReasoner(input, ctx -> ctx.reasoner.isConsistent());
            } else {
                consistent = withPooledReasoner(input, ctx -> ctx.reasoner.isConsistent());
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
                Object[] result = withMiniReasoner(input, ctx -> {
                    boolean c = ctx.reasoner.isConsistent();
                    List<String> unsat = Collections.emptyList();
                    if (!c) {
                        Node<OWLClass> node = ctx.reasoner.getUnsatisfiableClasses();
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
                consistent = withPooledReasoner(input, ctx -> {
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

            // 1) 推理器输出患者所有类型
            Set<OWLClass> types = withMiniReasoner(input, ctx -> ctx.getPatientTypes());

            // 2) 用 bagangSubclasses 过滤，得到 OWLClass 列表（按 fragment 排序）
            List<OWLClass> bagangClasses = types.stream()
                    .filter(bagangSubclasses::contains)
                    .sorted(Comparator.comparing(c -> c.getIRI().getFragment()))
                    .collect(Collectors.toList());

            // 3) fragment 列表
            List<String> bagangFragments = bagangClasses.stream()
                    .map(c -> c.getIRI().getFragment())
                    .collect(Collectors.toList());

            // 4) 中文 label 列表（直接通过 backendService 读 TBox）
            List<String> bagangTypesCn = bagangClasses.stream()
                    .map(this::resolveClassLabel)
                    .collect(Collectors.toList());

            // 5) 按八纲维度分组，输出中文
            List<String> biaoli = groupByFragments(bagangFragments, bagangTypesCn, "Biao", "Li", "BanbiaoBanli");
            List<String> hanre = groupByFragments(bagangFragments, bagangTypesCn, "Han", "Re");
            List<String> xushi = groupByFragments(bagangFragments, bagangTypesCn, "Xu", "Shi");
            List<String> yinyang = groupByFragments(bagangFragments, bagangTypesCn, "Yin", "Yang");

            Map<String, Object> bagangResult = new LinkedHashMap<>();
            bagangResult.put("表里", biaoli);
            bagangResult.put("寒热", hanre);
            bagangResult.put("虚实", xushi);
            bagangResult.put("阴阳", yinyang);
            bagangResult.put("bagangTypes", bagangFragments);
            bagangResult.put("bagangTypesCn", bagangTypesCn);
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

    /**
     * ⭐ 一次 SPARQL 拿到方剂的所有组成药物 IRI 和中文 label。
     * label 为空（null、""、" "、"&#xFEFF;"）时 fallback 到 fragment。
     * 返回 List<String[]>：每个元素是 [完整IRI, 中文label]。
     */
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
        for (Map<String, String> row : backendService.getObdaHandler().executeAboxQuery(sparql)) {
            String herb = row.get("herb");
            if (herb == null || herb.isBlank()) continue;

            String full = toFullIri(herb.trim());
            String label = row.get("label");

            if (label == null || label.isBlank()) {
                // ⭐ fallback 到 fragment
                label = full.contains("#")
                        ? full.substring(full.lastIndexOf('#') + 1)
                        : full;
            } else {
                label = label.trim();
            }

            result.add(new String[]{ full, label });
        }
        return result;
    }

    /**
     * 从 TBox 读取 OWLClass 的中文 label。找不到时 fallback 到 fragment。
     */
    private String resolveClassLabel(OWLClass cls) {
        String fullIri = cls.getIRI().toString();
        String label = backendService.resolveLabel(fullIri, BASE_NS);
        return (label != null && !label.isBlank()) ? label : cls.getIRI().getFragment();
    }

    /**
     * 按 fragment 分组，返回对应位置的中文标签。
     * fragments 和 labels 索引一一对应。
     */
    private List<String> groupByFragments(List<String> fragments,
                                          List<String> labels,
                                          String... targets) {
        Set<String> set = new HashSet<>(Arrays.asList(targets));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            if (set.contains(fragments.get(i))) {
                result.add(labels.get(i));
            }
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

            // ⭐ 推理由 Openllet 完成。liujingTopClasses 已由推理器过滤，
            // 只含纯六经类（不含 Fangzheng 子类）。
            List<String> liujingTypes = withMiniReasoner(input, ctx ->
                    extractByMetaClass(ctx.getPatientTypes(), liujingTopClasses));

            String sixChannel;
            String combinedDiseaseMark = null;
            boolean isCombined = liujingTypes.size() > 1;
            if (liujingTypes.isEmpty()) {
                sixChannel = "六经难定";
            } else {
                sixChannel = liujingTypes.get(0);
                if (isCombined) combinedDiseaseMark = buildCombinedDiseaseMark(liujingTypes);
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

    /**
     * 合病名称映射：术语表，不是推理。
     * 中医里"合病"的常见名称是约定俗成的术语，无法由 OWL 推理器直接输出。
     */
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
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            // ⭐ 推理由 Openllet 完成。所有满足等价类定义的方证都会被 getTypes 输出。
            List<String> fangzhengTypes = withMiniReasoner(input, ctx ->
                    extractByMetaClass(ctx.getPatientTypes(), fangzhengSubclasses));

            String fangzheng;
            if (fangzhengTypes.isEmpty()) {
                fangzheng = "方证未定";
            } else if (fangzhengTypes.size() == 1) {
                fangzheng = fangzhengTypes.get(0);
            } else {
                // ⭐ 多个匹配：先用推理器选最具体（不是其他方证的父类），再按 clinicalPriority 排序
                fangzheng = selectByReasoning(fangzhengTypes);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fangzheng", fangzheng);
            out.put("fangzhengCn", backendService.resolveLabel(fangzheng, BASE_NS));
            out.put("fangzhengTypes", fangzhengTypes);
            out.put("candidateFangzhengs", fangzhengTypes);
            out.put("candidateFangzhengsCn",
                    backendService.resolveLabels(fangzhengTypes, BASE_NS));
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("方证分类完成: {} (匹配数: {})", fangzheng, fangzhengTypes.size());
        } catch (Exception e) {
            log.error("方证分类失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("FANGZHENG_FAILED")
                    .errorMessage(e.getMessage()).send().join();
        }
    }

    /**
     * ⭐ 多个方证匹配时选一个：
     *   1. 先用推理器判断哪些是"最具体"（不是其他方证的父类）
     *   2. 在候选里按 clinicalPriority annotation 排序（数字小优先）
     *   3. 若还没有唯一结果，fallback 到字母序
     */
    private String selectByReasoning(List<String> fangzhengFragments) {
        OWLReasoner r = backendService.getReasonerService().getReasoner();
        List<OWLClass> fzClasses = fangzhengFragments.stream()
                .map(f -> tboxDf.getOWLClass(IRI.create(BASE_NS + f)))
                .collect(Collectors.toList());

        // 步骤 1：筛选最具体（不是其他方证的父类）
        List<OWLClass> mostSpecific = fzClasses.stream()
                .filter(c -> fzClasses.stream()
                        .filter(other -> !other.equals(c))
                        .noneMatch(other -> r.isEntailed(
                                tboxDf.getOWLSubClassOfAxiom(other, c))))
                .collect(Collectors.toList());

        List<OWLClass> candidates = mostSpecific.isEmpty() ? fzClasses : mostSpecific;

        // 步骤 2：按 clinicalPriority 排序
        OWLClass chosen = candidates.stream()
                .min(Comparator.comparingInt(this::getClinicalPriority))
                .orElse(candidates.get(0));

        return chosen.getIRI().getFragment();
    }

    /**
     * ⭐ 读取方证的 clinicalPriority annotation。
     * 数字越小越优先。缺失时返回默认值 99（最低优先级）。
     * 这是元数据读取，不参与逻辑推理。
     */
    private int getClinicalPriority(OWLClass fz) {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLAnnotationProperty priorityProp = tboxDf.getOWLAnnotationProperty(
                IRI.create(BASE_NS + "clinicalPriority"));

        return tbox.annotationAssertionAxioms(fz.getIRI())
                .filter(ax -> ax.getProperty().equals(priorityProp))
                .map(ax -> ax.getValue())
                .filter(v -> v instanceof OWLLiteral)
                .map(v -> (OWLLiteral) v)
                .mapToInt(lit -> {
                    try {
                        return lit.parseInteger();
                    } catch (Exception e) {
                        return 99;
                    }
                })
                .findFirst()
                .orElse(99);
    }

    // ==================== JobWorker：jianjiazheng-classification ====================

    @JobWorker(type = "jianjiazheng-classification", autoComplete = false)
    public void handleJianJiaZhengClassification(final ActivatedJob job, final JobClient client) {
        try {
            String patientIri = (String) job.getVariablesAsMap().get("patientIri");
            PatientInput input = patientInputs.get(patientIri);
            if (input == null) throw new IllegalStateException("患者输入缓存丢失: " + patientIri);

            // ⭐ 推理由 Openllet 完成
            List<String> jianJiaTypes = withMiniReasoner(input, ctx ->
                    extractByMetaClass(ctx.getPatientTypes(), jianJiaSubclasses));

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

            // ⭐ 一次 SPARQL 拿到 IRI + label
            List<String[]> herbPairs = queryHerbsWithLabels(formulaIri);
            List<String> herbIris = herbPairs.stream().map(p -> p[0]).collect(Collectors.toList());
            List<String> herbCn   = herbPairs.stream().map(p -> p[1]).collect(Collectors.toList());

            // 加减药物（从兼夹证拿 IRI，再查 label）
            List<String> addHerbIris = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> jianJiaZhengs = (List<String>) vars.get("jianJiaZhengs");
            if (jianJiaZhengs != null) {
                for (String jz : jianJiaZhengs) {
                    OWLClass jzCls = tboxDf.getOWLClass(IRI.create(BASE_NS + jz));
                    for (IRI h : getAddHerbs(jzCls)) {
                        String full = toFullIri(h.toString());
                        if (!addHerbIris.contains(full)) {   // 去重
                            addHerbIris.add(full);
                        }
                    }
                }
            }

            // 配伍禁忌检查
            List<String> allHerbs = new ArrayList<>(herbIris);
            allHerbs.addAll(addHerbIris);
            List<String> warnings = checkIncompatibilities(allHerbs);

            // 加减药物 label（数量少，单独查）
            List<String> addHerbCn = addHerbIris.stream()
                    .map(this::queryLabel)
                    .collect(Collectors.toList());

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

    /**
     * 取个体中文标签。Ontop 返回空（null、""、" "、"&#xFEFF;"）时 fallback 到 fragment。
     */
    private String queryLabel(String iri) {
        if (iri == null || iri.isBlank()) return "";
        String full = toFullIri(iri);
        String frag = full.contains("#") ? full.substring(full.lastIndexOf('#') + 1) : full;

        try {
            String sparql = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?label WHERE { <%s> rdfs:label ?label . }
                """.formatted(full);

            for (Map<String, String> row : backendService.getObdaHandler().executeAboxQuery(sparql)) {
                String label = row.get("label");
                if (label != null && !label.isBlank()) {
                    return label.trim();
                }
            }
        } catch (Exception e) {
            log.warn("queryLabel 查询失败，iri={}", full, e);
        }

        return frag;
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

            if (patientIri != null) {
                patientInputs.remove(patientIri);
                miniTboxCache.remove(patientIri);

                MiniContext c = miniContextCache.remove(patientIri);
                if (c != null) c.dispose();

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