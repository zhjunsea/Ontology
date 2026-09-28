package com.ocean.openlletresolver;

import openllet.owlapi.OpenlletReasonerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 rules.owl v2.8 的 SWRL 方后注加减规则能被 OWLAPI 正确解析、被 Openllet 自动激发。
 *
 * <p>使用 {@link OntologyService} 加载 tcm-all.owl（含 owl:imports rules.owl），
 * 复用生产环境的 IRI 映射 / catalog 解析，避免手工 AutoIRIMapper 遗漏 .ttl 等非 OWL 文件。
 *
 * <p>覆盖四类动作（add / remove / retain / dose）与多个方证：
 * <ul>
 *   <li>add + dose：桂枝加葛根汤证 + 项强 → 加葛根（Gegen）+ 剂量「葛根四两」</li>
 *   <li>add + dose：桂枝加附子汤证 + 汗漏不止 + 小便难 → 加附子（Fuzi）+ 剂量「附子一枚，炮」</li>
 *   <li>remove：桂枝去芍药汤证 + 胸满 → 去芍药（Shaoyao）</li>
 *   <li>remove + add + dose：真武汤证 + 下利 → 去芍药 + 加干姜 + 剂量「干姜二两」</li>
 *   <li>retain：真武汤证 + 小便不利 → 留茯苓（Fuling）</li>
 *   <li>阴性对照：桂枝加葛根汤证 + 发热（无项强）→ 不加葛根</li>
 * </ul>
 *
 * <p>为控制耗时，所有患者 ABox 一次性并入同一本体，仅创建 <b>一个</b> Openllet 推理机，
 * 各测试方法只做数据属性查询。
 *
 * <p>需传入系统属性 {@code -Dswrl.verification=true} 启用（默认跳过，因加载全量本体耗时）。
 * 命令行：
 * <pre>
 *   mvn -pl OpenlletResolver -am test -Dtest=SwrlFangzhengVerificationTest -Dswrl.verification=true
 * </pre>
 */
@DisplayName("SWRL 方后注加减规则验证")
public class SwrlFangzhengVerificationTest {

    private static final String TCM_ALL_OWL =
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-all.owl";
    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";

    private static OntologyService ontologyService;
    private static OWLOntology tbox;
    private static OWLDataFactory df;
    private static OWLOntology merged;
    private static OWLReasoner reasoner;

    /** 场景名 → 患者个体。 */
    private static final Map<String, OWLNamedIndividual> PATIENTS = new LinkedHashMap<>();

    @BeforeAll
    static void loadTBoxAndBuildScenarios() throws Exception {
        ontologyService = new OntologyService(TCM_ALL_OWL);
        tbox = ontologyService.gettBoxOntology();
        df = tbox.getOWLOntologyManager().getOWLDataFactory();

        long swrlCount = tbox.axioms(AxiomType.SWRL_RULE).count();
        System.out.println("[SWRL验证] TBox 中 SWRL 规则数: " + swrlCount);
        assertThat(swrlCount).as("rules.owl v2.8 应含 SWRL 规则").isGreaterThan(0);

        Set<OWLAxiom> allAxioms = new HashSet<>();
        tbox.axioms().forEach(allAxioms::add);

        addScenario(allAxioms, "addGuizhijiagegen", "Guizhijiagegentangzheng", "Xiangqiang");
        addScenario(allAxioms, "addFuzi", "Guizhijiafuzitangzheng", "Hanloubuzhi", "Xiaobiannan");
        addScenario(allAxioms, "removeShaoyao", "Guizhiqushaoyaotangzheng", "Xinxiaman");
        addScenario(allAxioms, "zhenwuXiali", "Zhenwutangzheng", "Xiali");
        addScenario(allAxioms, "zhenwuXiaobianbuli", "Zhenwutangzheng", "Xiaobianbuli");
        addScenario(allAxioms, "negativeControl", "Guizhijiagegentangzheng", "Fare");

        merged = tbox.getOWLOntologyManager().createOntology(IRI.create(BASE_NS + "swrl-merged-all"));
        tbox.getOWLOntologyManager().addAxioms(merged, allAxioms);

        reasoner = new OpenlletReasonerFactory().createReasoner(merged);
        reasoner.precomputeInferences();
        assertThat(reasoner.isConsistent()).as("合并本体应一致").isTrue();
    }

    @AfterAll
    static void tearDown() {
        if (reasoner != null) {
            reasoner.dispose();
        }
        // 不调用 ontologyService.close()：其实现引用 OBDAHandler，测试环境无该类。
    }

    @Test
    @DisplayName("add + dose：桂枝加葛根汤证（项强）→ 加葛根 + 剂量")
    @EnabledIfSystemProperty(named = "swrl.verification", matches = "true")
    void addWithDose() {
        DerivedResult r = derive("addGuizhijiagegen");
        assertThat(r.addHerbs).as("项强应推理出加葛根（Gegen）").contains("Gegen");
        assertThat(r.addDoses).as("葛根剂量应为「葛根四两」").contains("Gegen:葛根四两");
    }

    @Test
    @DisplayName("add + dose：桂枝加附子汤证（汗漏不止+小便难）→ 加附子 + 剂量")
    @EnabledIfSystemProperty(named = "swrl.verification", matches = "true")
    void addFuziWithDose() {
        DerivedResult r = derive("addFuzi");
        assertThat(r.addHerbs).as("应推理出加附子（Fuzi）").contains("Fuzi");
        assertThat(r.addDoses).as("附子剂量应为「附子一枚，炮」").contains("Fuzi:附子一枚，炮");
    }

    @Test
    @DisplayName("remove：桂枝去芍药汤证（胸满）→ 去芍药")
    @EnabledIfSystemProperty(named = "swrl.verification", matches = "true")
    void removeHerb() {
        DerivedResult r = derive("removeShaoyao");
        assertThat(r.removeHerbs).as("胸满应推理出去芍药（Shaoyao）").contains("Shaoyao");
    }

    @Test
    @DisplayName("remove + add + dose：真武汤证（下利）→ 去芍药 + 加干姜 + 剂量")
    @EnabledIfSystemProperty(named = "swrl.verification", matches = "true")
    void removeAndAddWithDose() {
        DerivedResult r = derive("zhenwuXiali");
        assertThat(r.removeHerbs).as("下利应推理出去芍药（Shaoyao）").contains("Shaoyao");
        assertThat(r.addHerbs).as("下利应推理出加干姜（Ganjiang）").contains("Ganjiang");
        assertThat(r.addDoses).as("干姜剂量应为「干姜二两」").contains("Ganjiang:干姜二两");
    }

    @Test
    @DisplayName("retain：真武汤证（小便不利）→ 留茯苓")
    @EnabledIfSystemProperty(named = "swrl.verification", matches = "true")
    void retainHerb() {
        DerivedResult r = derive("zhenwuXiaobianbuli");
        assertThat(r.retainHerbs).as("小便不利应推理出留茯苓（Fuling）").contains("Fuling");
    }

    @Test
    @DisplayName("阴性对照：桂枝加葛根汤证 + 发热（无项强）→ 不加葛根")
    @EnabledIfSystemProperty(named = "swrl.verification", matches = "true")
    void noSymptomNoAction() {
        DerivedResult r = derive("negativeControl");
        assertThat(r.addHerbs).as("仅有发热、无项强时不应推理出加葛根").doesNotContain("Gegen");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static void addScenario(Set<OWLAxiom> axioms, String name, String fangzheng, String... symptomClasses) {
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(BASE_NS + "SwrlTestPatient_" + name));
        PATIENTS.put(name, patient);

        OWLClass huanzhe = df.getOWLClass(IRI.create(BASE_NS + "Huanzhe"));
        OWLClass fangzhengClass = df.getOWLClass(IRI.create(BASE_NS + fangzheng));
        OWLObjectProperty youZhengzhuang = df.getOWLObjectProperty(IRI.create(BASE_NS + "you_zhengzhuang"));

        axioms.add(df.getOWLClassAssertionAxiom(huanzhe, patient));
        axioms.add(df.getOWLClassAssertionAxiom(fangzhengClass, patient));

        int i = 0;
        for (String sym : symptomClasses) {
            OWLNamedIndividual symptom = df.getOWLNamedIndividual(
                    IRI.create(BASE_NS + "SwrlTestSym_" + name + "_" + (i++)));
            OWLClass symptomClass = df.getOWLClass(IRI.create(BASE_NS + sym));
            axioms.add(df.getOWLObjectPropertyAssertionAxiom(youZhengzhuang, patient, symptom));
            axioms.add(df.getOWLClassAssertionAxiom(symptomClass, symptom));
        }
    }

    private static DerivedResult derive(String scenario) {
        OWLNamedIndividual patient = PATIENTS.get(scenario);
        DerivedResult r = new DerivedResult();
        r.addHerbs = values(patient, "shouldAddHerbName");
        r.removeHerbs = values(patient, "shouldRemoveHerbName");
        r.retainHerbs = values(patient, "shouldRetainHerbName");
        r.addDoses = values(patient, "shouldAddHerbDose");
        System.out.println("[SWRL验证] " + scenario + " => add=" + r.addHerbs + " remove=" + r.removeHerbs
                + " retain=" + r.retainHerbs + " dose=" + r.addDoses);
        return r;
    }

    private static Set<String> values(OWLNamedIndividual patient, String prop) {
        OWLDataProperty p = df.getOWLDataProperty(IRI.create(BASE_NS + prop));
        return reasoner.getDataPropertyValues(patient, p).stream()
                .map(OWLLiteral::getLiteral)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static final class DerivedResult {
        Set<String> addHerbs = Set.of();
        Set<String> removeHerbs = Set.of();
        Set<String> retainHerbs = Set.of();
        Set<String> addDoses = Set.of();
    }
}
