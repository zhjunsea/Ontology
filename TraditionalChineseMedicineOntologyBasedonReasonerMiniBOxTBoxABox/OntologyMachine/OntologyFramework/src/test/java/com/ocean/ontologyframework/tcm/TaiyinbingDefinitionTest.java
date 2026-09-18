package com.ocean.ontologyframework.tcm;

import com.ocean.openlletresolver.OntologyModuleUtils;
import com.ocean.openlletresolver.OntologyService;
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
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * <h3>被守护的缺陷</h3>
 * 太阴病（{@code #Taiyinbing}）equivalentClass 分支 1 原为
 * <pre>
 *   （腹满 | 呕吐 | 食不下） ∩ （弱脉 | 沉细脉 | 沉微脉 | 淡白舌 | <b>苔色</b>）
 * </pre>
 * 末项写的是<b>父类</b> {@code #CoatingColor}（苔色），而黄苔 {@code #YellowCoating}、
 * 灰苔 {@code #GreyCoating}、黑苔 {@code #BlackCoating} 都是它的子类。
 * 于是「腹满 + 黄苔」这种典型的阳明里实热证会被判为太阴病（里阴证），
 * 导致八纲凭空多出「阴证」、六经变成「阳明太阴合病」。
 *
 * <h3>修正</h3>
 * 按该类自身 {@code coreEvidence}「脉弱、沉细、沉微、<b>舌淡苔白</b>」，
 * 把该析取项由 {@code #CoatingColor} 收窄为 {@code #WhiteCoating}（白苔）。
 *
 * <h3>性能设计</h3>
 * Openllet 对完整 TBox 做一次分类约需数分钟，因此<b>不</b>为每个场景各建一个
 * reasoner。这里把 5 个场景一次性建成同一本体中的 5 个患者个体，
 * 只 realize 一次，再逐个体取类型。整体耗时由 5×分类降为 1×分类。
 */
class TaiyinbingDefinitionTest {

    /** 与 TCMOntologyJobWorker.BASE_NS 一致。 */
    private static final String NS = "http://www.tcm-classics.org/jingfang#";

    private static final Set<String> SIX = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing");

    private static OWLOntology tbox;
    private static OWLDataFactory df;
    private static OWLOntologyManager mgr;
    private static OWLOntology ont;
    private static OWLReasoner reasoner;

    /** 场景名 → 患者个体 IRI。 */
    private static final Map<String, String> PATIENTS = new LinkedHashMap<>();

    @BeforeAll
    static void setUp() throws Exception {
        OntologyService os = new OntologyService(readMainPath());
        tbox = os.gettBoxOntology();
        df = tbox.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLAxiom> abox = new HashSet<>();

        // 负例（回归守护）
        addPatient(abox, "fumanYellow",
                List.of("Fuman"), List.of(), List.of("YellowCoating"), List.of());
        addPatient(abox, "daChengQiTang",
                List.of("Danrebuhan", "Kouke", "Chaore", "Zaoshi",
                        "Zhanyu", "Fuman", "Futong", "Juan"),
                List.of("Chenshimai"),
                List.of("YellowCoating", "DryCoating", "TongueWithThorns"),
                List.of());

        // 正例（防止矫枉过正）
        addPatient(abox, "fumanRuoMai",
                List.of("Fuman"), List.of("Ruomai"), List.of(), List.of());
        addPatient(abox, "fumanWhite",
                List.of("Fuman"), List.of(), List.of("WhiteCoating"), List.of());
        addPatient(abox, "fumanPaleWhite",
                List.of("Fuman"), List.of(), List.of("PaleWhiteTongue"), List.of());

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
    @DisplayName("腹满 + 白苔 仍应推出太阴病（白苔是有效证据）")
    void fumanWithWhiteCoatingMustBeTaiyin() {
        assertThat(six("fumanWhite"))
                .as("腹满+白苔 应推出太阴病")
                .contains("Taiyinbing");
    }

    @Test
    @DisplayName("腹满 + 淡白舌 仍应推出太阴病（舌淡苔白之「舌淡」）")
    void fumanWithPaleWhiteTongueMustBeTaiyin() {
        assertThat(six("fumanPaleWhite"))
                .as("腹满+淡白舌 应推出太阴病")
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
     * 患者个体 + 四诊对象断言 + 被指向个体的类型复制。
     *
     * <p>与生产路径的差异：这里用<b>完整 TBox</b>（生产用模块抽取）。
     * 模块是 TBox 的子集，只能推出更少的结论，故「完整 TBox 推不出太阴病」
     * 蕴含「模块也推不出」，结论方向是安全的。
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
    }

    private static void addAssertions(Set<OWLAxiom> acc, OWLNamedIndividual patient,
                                      String property, List<String> iris) {
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                tbox, df, acc,
                df.getOWLObjectProperty(IRI.create(NS + property)),
                patient,
                iris.stream().map(TaiyinbingDefinitionTest::i).collect(Collectors.toList()),
                NS);
    }

    /** 症状/脉象/舌象/腹证实例 IRI（ABox 个体统一带 {@code _instance} 后缀）。 */
    private static String i(String fragment) {
        return NS + fragment + "_instance";
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
