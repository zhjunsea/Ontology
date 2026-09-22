package com.ocean.ontologyframework.tcm;

import com.ocean.openlletresolver.OntologyModuleUtils;
import openllet.owlapi.OpenlletReasonerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 太阴病 {@code equivalentClass} 语义回归测试。
 *
 * <p><b>完全离线</b>：只用 OWLAPI + Openllet 直接对 {@code tcm-all.owl} 推理，
 * 不依赖 MySQL / Ontop / Zeebe / Worker，因此可在任意环境复跑。
 *
 * <h3>被守护的语义（D1 重构后）</h3>
 * 太阴病（{@code #Taiyinbing}）的 {@code equivalentClass} 为
 * <pre>
 *   #Li ∩ #Yin            （里证 ∩ 阴证）
 * </pre>
 * 即「六经 ≡ 病位 ⊓ 病性」。八纲本身保持原子类、不写 {@code ≡}；
 * 病位/病性由<b>判据层</b>传导：
 * <ul>
 *   <li>病位 {@code #Li}：合取判据 {@code #Panju_B1..B8} ⊑ {@code #Li}，
 *       或单症状白名单 {@code #Dabianying} ⊑ {@code #Li}；</li>
 *   <li>病性 {@code #Yin}：{@code #Han} ⊑ {@code #Yin}、{@code #Xu} ⊑ {@code #Yin}，
 *       而 {@code #Ruomai} ⊑ {@code #Xu}、{@code #Chenchimai} ⊑ {@code #Han}、
 *       {@code #PaleWhiteTongue} ⊑ {@code #Han} ⊓ {@code #Xu} 等。</li>
 * </ul>
 * 因此「腹满 + 弱脉」经 {@code #Panju_B8}（腹满+弱/微/虚/沉/迟脉）得「里」，
 * 经「弱脉 ⊑ 虚 ⊑ 阴」得「阴」，合得太阴病。
 *
 * <h3>被守护的缺陷</h3>
 * 历史上太阴病曾以「症状组合」直接定义（末项误写为父类 {@code #CoatingColor}），
 * 使「腹满 + 黄苔」这种里实热证被误判为太阴病。D1 重构后改由「判据 → 八纲 → 六经」
 * 传导，黄苔（{@code #YellowCoating} ⊑ {@code #Re} ⊑ {@code #Yang}）只会得「阳」，
 * 不再可能误入太阴。本测试同时守护：<b>里热（黄苔 / 大承气汤证）不得推出太阴病</b>。
 *
 * <h3>本体加载（2026-09-20 起）</h3>
 * 生产路径把「TBox 入口」与「ABox 查表本体」分开加载：{@code tcm-all.owl} 不再
 * {@code owl:imports} ABox（避免 ABox 拖累 Openllet 全局分类：345s → 47s），
 * ABox 改由 {@code tcm-all-abox.owl} 单独加载。本测试与生产保持一致，显式加载
 * {@code tcm-all.owl} + {@code tcm-all-abox.owl} 再合并，否则
 * {@link OntologyModuleUtils#addObjectAssertionsAndCopyTypes} 复制不到症状实例的类型，
 * 推理结果会全空。
 *
 * <h3>性能设计</h3>
 * Openllet 对完整 TBox 做一次分类约需数分钟，因此<b>不</b>为每个场景各建一个
 * reasoner。这里把 5 个场景一次性建成同一本体中的 5 个患者个体，
 * 只 realize 一次，再逐个体取类型。整体耗时由 5×分类降为 1×分类。
 */
class TaiyinbingDefinitionTest {

    /** 与 TCMOntologyJobWorker.BASE_NS 一致。 */
    private static final String NS = "http://www.tcm-classics.org/jingfang#";

    /** ABox 查表本体（与 TBox 入口分开加载）。 */
    private static final String ABOX_ENTRY = "tcm-all-abox.owl";

    /** 与 TCMOntologyJobWorker.INSTANCE_SUFFIX 一致。 */
    private static final String INSTANCE_SUFFIX = "_instance";

    private static final Set<String> SIX = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing");

    /** TBox 闭包（tcm-all.owl 及其 imports），仅用于构建推理本体，不含 ABox。 */
    private static OWLOntology tbox;
    /** TBox + ABox 查表本体，仅用于复制个体类型，不参与推理（避免 ABox 拖累分类）。 */
    private static OWLOntology lookup;
    private static OWLDataFactory df;
    private static OWLOntologyManager mgr;
    private static OWLOntology ont;
    private static OWLReasoner reasoner;

    /** 场景名 → 患者个体 IRI。 */
    private static final Map<String, String> PATIENTS = new LinkedHashMap<>();

    @BeforeAll
    static void setUp() throws Exception {
        // 与生产 TCMOntologyJobWorker 一致：TBox 入口与 ABox 查表本体分开加载。
        // 注意 OWLOntology.axioms() 只返回「该文档自身」的公理，不含 imports 闭包，
        // 故必须遍历 manager 中所有已加载本体取并集，否则拿到的是近乎空的本体。
        Path mainPath = Paths.get(readMainPath());
        Path ontologyDir = mainPath.getParent();

        OWLOntologyManager loadMgr = OWLManager.createOWLOntologyManager();
        loadMgr.getIRIMappers().add(new AutoIRIMapper(ontologyDir.toFile(), true));
        loadMgr.loadOntologyFromOntologyDocument(mainPath.toFile());

        // TBox 闭包（tcm-all.owl 及其 imports；2026-09-20 起 tcm-all.owl 不再 import ABox）
        Set<OWLAxiom> tboxAxioms = new LinkedHashSet<>();
        for (OWLOntology o : loadMgr.getOntologies()) tboxAxioms.addAll(o.getAxioms());

        // ABox 查表本体（症状/脉象/舌象个体类型），单独加载
        File aboxEntry = ontologyDir.resolve(ABOX_ENTRY).toFile();
        if (aboxEntry.isFile()) {
            loadMgr.loadOntologyFromOntologyDocument(aboxEntry);
        }
        Set<OWLAxiom> lookupAxioms = new LinkedHashSet<>();
        for (OWLOntology o : loadMgr.getOntologies()) lookupAxioms.addAll(o.getAxioms());

        // lookup：TBox + ABox，仅用于复制个体类型（不参与推理）
        lookup = loadMgr.createOntology(lookupAxioms);
        // tbox：仅 TBox 闭包，用于构建推理本体
        tbox = loadMgr.createOntology(tboxAxioms);
        df = loadMgr.getOWLDataFactory();

        Set<OWLAxiom> abox = new HashSet<>();

        // ==================== 负例（回归守护） ====================

        // 腹满 + 黄苔：黄苔 ⊑ 热 ⊑ 阳，无「里」证据、无「阴」证据，不得推出太阴病。
        addPatient(abox, "fumanYellow",
                List.of("Fuman"), List.of(), List.of("YellowCoating"), List.of());

        // 大承气汤证（阳明腑实）：大便硬 ⊑ 里；谵语 ⊑ 热、沉实脉 ⊑ 实 → 里阳。
        // 应推出阳明病，且不得推出太阴病。
        addPatient(abox, "daChengQiTang",
                List.of("Danrebuhan", "Kouke", "Chaore", "Zaoshi",
                        "Zhanyu", "Fuman", "Futong", "Juan"),
                List.of("Chenshimai"),
                List.of("YellowCoating", "DryCoating", "TongueWithThorns"),
                List.of());

        // ==================== 正例（防止矫枉过正） ====================

        // 太阴提纲：腹满 + 弱脉。
        //   里：Panju_B8（腹满 + 弱/微/虚/沉/迟脉）⊑ 里；
        //   阴：弱脉 ⊑ 虚 ⊑ 阴。
        addPatient(abox, "fumanRuoMai",
                List.of("Fuman"), List.of("Ruomai"), List.of(), List.of());

        // 腹满 + 白苔 + 弱脉：白苔（主里寒湿）不破坏太阴判定，仍应推出太阴病。
        //   里：Panju_B8（腹满 + 弱脉）；阴：弱脉 ⊑ 虚 ⊑ 阴。
        addPatient(abox, "fumanWhite",
                List.of("Fuman"), List.of("Ruomai"), List.of("WhiteCoating"), List.of());

        // 腹满 + 淡白舌 + 沉迟脉：太阴里虚寒之典型舌脉。
        //   里：Panju_B8（腹满 + 沉迟脉）；
        //   阴：淡白舌 ⊑ 寒 ⊓ 虚 → 阴；沉迟脉 ⊑ 寒 → 阴。
        addPatient(abox, "fumanPaleWhite",
                List.of("Fuman"), List.of("Chenchimai"), List.of("PaleWhiteTongue"), List.of());

        mgr = OWLManager.createOWLOntologyManager();
        ont = mgr.createOntology(IRI.create("urn:verify:taiyinbing"));
        mgr.addAxioms(ont, tbox.axioms());
        mgr.addAxioms(ont, abox);

        reasoner = new OpenlletReasonerFactory().createReasoner(ont);
        reasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
    }

    @AfterAll
    static void tearDown() {
        if (reasoner != null) {
            reasoner.dispose();
        }
        if (mgr != null && ont != null) {
            mgr.removeOntology(ont);
        }
    }

    // ==================== 负例（回归守护） ====================

    @Test
    @DisplayName("腹满 + 黄苔 不得推出太阴病（黄苔主里热，与太阴里虚寒相悖）")
    void fumanWithYellowCoatingMustNotBeTaiyin() {
        assertThat(six("fumanYellow"))
                .as("腹满+黄苔 不应推出太阴病")
                .doesNotContain("Taiyinbing");
    }

    @Test
    @DisplayName("大承气汤证（腹满+黄苔+沉实脉）不得推出太阴病，且应推出阳明病")
    void daChengQiTangMustNotBeTaiyin() {
        Set<String> six = six("daChengQiTang");
        assertThat(six).as("大承气汤证不应推出太阴病").doesNotContain("Taiyinbing");
        assertThat(six).as("大承气汤证应推出阳明病").contains("Yangmingbing");
    }

    // ==================== 正例（防止矫枉过正） ====================

    @Test
    @DisplayName("太阴提纲（腹满 + 弱脉）必须推出太阴病")
    void taiyinOutlineWithRuoMaiMustBeTaiyin() {
        assertThat(six("fumanRuoMai"))
                .as("腹满+弱脉 应推出太阴病")
                .contains("Taiyinbing");
    }

    @Test
    @DisplayName("腹满 + 白苔 + 弱脉 仍应推出太阴病（白苔主里寒湿，不破坏太阴判定）")
    void fumanWithWhiteCoatingMustBeTaiyin() {
        assertThat(six("fumanWhite"))
                .as("腹满+白苔+弱脉 应推出太阴病")
                .contains("Taiyinbing");
    }

    @Test
    @DisplayName("腹满 + 淡白舌 + 沉迟脉 仍应推出太阴病（舌淡脉迟为里虚寒之确证）")
    void fumanWithPaleWhiteTongueMustBeTaiyin() {
        assertThat(six("fumanPaleWhite"))
                .as("腹满+淡白舌+沉迟脉 应推出太阴病")
                .contains("Taiyinbing");
    }

    // ==================== 基础设施 ====================

    /** 取某患者被推出的六经片段集合。 */
    private static Set<String> six(String scenario) {
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(PATIENTS.get(scenario)));
        return reasoner.getTypes(patient, false).getFlattened().stream()
                .map(c -> c.getIRI().getFragment())
                .filter(SIX::contains)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * 复刻 {@code TCMOntologyJobWorker.buildPatientAxioms} 的最小形式：
     * 患者个体 + 四诊对象断言 + 被指向个体的类型复制 + <b>发现类直接断言到患者</b>。
     *
     * <p>与生产路径的差异：这里用<b>完整 TBox</b>（生产用模块抽取）。
     * 模块是 TBox 的子集，只能推出更少的结论，故「完整 TBox 推不出太阴病」
     * 蕴含「模块也推不出」，结论方向是安全的。
     *
     * <p><b>关键</b>：{@link OntologyModuleUtils#addObjectAssertionsAndCopyTypes}
     * 只把类型拷到「被指向的个体 obj」（如 {@code Fuman_instance : Fuman}），
     * <b>不</b>把发现类断言到患者自身；而「单症状白名单 / 复合脉 ⊑ 八纲」的传导
     * （如 {@code Ruomai ⊑ Xu ⊑ Yin}）要求患者自身持有发现类。
     * 故此处补上生产路径的 {@code assertFindingTypes} 步骤，否则患者永远拿不到
     * 「阴」，太阴病推理结果会全空。
     */
    private static void addPatient(Set<OWLAxiom> acc, String scenario,
                                   List<String> syms, List<String> pulses,
                                   List<String> tongues, List<String> fuzhengs) {
        String iri = NS + "VerifyPatient_" + scenario;
        PATIENTS.put(scenario, iri);
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(iri));

        acc.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + "Huanzhe")), patient));
        addAssertions(acc, patient, "you_zhengzhuang", syms);
        addAssertions(acc, patient, "you_maixiang", pulses);
        addAssertions(acc, patient, "you_shexiang", tongues);
        addAssertions(acc, patient, "you_fuzheng", fuzhengs);

        // 复刻生产路径 assertFindingTypes：把四诊发现类直接断言到患者自身，
        // 使「发现类 ⊑ 八纲」可经患者传导（弱脉 ⊑ 虚 ⊑ 阴 等）。
        assertFindingTypes(acc, patient, syms);
        assertFindingTypes(acc, patient, pulses);
        assertFindingTypes(acc, patient, tongues);
        assertFindingTypes(acc, patient, fuzhengs);
    }

    /** 把发现类（个体 IRI 去 {@code _instance} 后缀）直接断言到患者个体。 */
    private static void assertFindingTypes(Set<OWLAxiom> acc, OWLNamedIndividual patient,
                                           List<String> iris) {
        if (iris == null) return;
        for (String fragment : iris) {
            if (fragment == null || fragment.isBlank()) continue;
            acc.add(df.getOWLClassAssertionAxiom(
                    df.getOWLClass(IRI.create(NS + fragment)), patient));
        }
    }

    private static void addAssertions(Set<OWLAxiom> acc, OWLNamedIndividual patient,
                                      String property, List<String> iris) {
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                lookup, df, acc,
                df.getOWLObjectProperty(IRI.create(NS + property)),
                patient,
                iris.stream().map(TaiyinbingDefinitionTest::i).collect(Collectors.toList()),
                NS);
    }

    /** 症状/脉象/舌象/腹证实例 IRI（ABox 个体统一带 {@code _instance} 后缀）。 */
    private static String i(String fragment) {
        return NS + fragment + INSTANCE_SUFFIX;
    }

    @SuppressWarnings("unchecked")
    private static String readMainPath() {
        try (InputStream is = TaiyinbingDefinitionTest.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml 必须在 classpath 上").isNotNull();
            Map<String, Object> cfg = new Yaml().load(is);
            Map<String, Object> ontology = (Map<String, Object>) cfg.get("ontology");
            String main = (String) ontology.get("main-path");
            assertThat(main).as("ontology.main-path 必须已配置").isNotBlank();
            return main;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
