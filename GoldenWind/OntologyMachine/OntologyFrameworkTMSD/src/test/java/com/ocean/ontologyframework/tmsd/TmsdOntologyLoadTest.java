package com.ocean.ontologyframework.tmsd;

import openllet.owlapi.OpenlletReasonerFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataHasValue;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLObjectCardinalityRestriction;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 塔架中段本体（{@code TowerMidSection.owl}）加载与约束校验测试。
 *
 * <p>不依赖 Camunda / Ontop / MySQL：直接用 OWL API 加载本体文件，并用 Openllet 判定一致性，
 * 同时核对 v16.0 新增/修正的类、属性与 Restriction 确实写入文件。
 */
class TmsdOntologyLoadTest {

    private static final String NS = "http://goldwind.com/ontology/tower-mid-section#";

    private static OWLOntology ont;
    private static OWLDataFactory df;
    private static Path owlPath;

    @BeforeAll
    static void load() throws Exception {
        owlPath = Paths.get(TmsdTestConfig.ontology("main-path"));
        assertThat(owlPath).as("本体文件应存在: %s", owlPath).exists();

        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        ont = m.loadOntologyFromOntologyDocument(owlPath.toFile());
        df = m.getOWLDataFactory();

        TmsdVocabulary.init(owlPath);
    }

    @Test
    @DisplayName("本体可被 OWL API 加载，versionInfo = v16.10")
    void versionIsV1610() throws Exception {
        assertThat(ont).isNotNull();
        String raw = Files.readString(owlPath, StandardCharsets.UTF_8);
        assertThat(raw).contains("<owl:versionInfo>v16.10</owl:versionInfo>");
    }

    @Test
    @DisplayName("Openllet 判定本体一致（isConsistent）")
    void consistentByOpenllet() {
        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        try {
            assertThat(reasoner.isConsistent()).as("本体应一致").isTrue();
        } finally {
            reasoner.dispose();
        }
    }

    @Test
    @DisplayName("v16.0 新增类/属性已写入签名")
    void newEntitiesPresent() {
        assertThat(ont.containsClassInSignature(IRI.create(NS + "LightningGroundingStud"))).isTrue();
        assertThat(ont.containsObjectPropertyInSignature(IRI.create(NS + "hasLightningGroundingStud"))).isTrue();
        assertThat(ont.containsObjectPropertyInSignature(IRI.create(NS + "lightningGroundingStudBelongsToFlange"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "rungSpacing"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "firstRungToBottomFlange"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "lightningStudInstallAngle"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "lightningStudFlangeDistance"))).isTrue();
    }

    @Test
    @DisplayName("v16.0 新增约束：法兰 =3 防雷螺柱、爬梯梯档 280/首踏棍 140、防雷螺柱距法兰面 50")
    void newRestrictionsPresent() {
        OWLObjectProperty onStud = df.getOWLObjectProperty(IRI.create(NS + "hasLightningGroundingStud"));
        OWLClass lgStud = cls("LightningGroundingStud");
        boolean flangeCard = supers(cls("Flange")).stream()
                .filter(OWLObjectCardinalityRestriction.class::isInstance)
                .map(OWLObjectCardinalityRestriction.class::cast)
                .anyMatch(q -> q.getProperty().equals(onStud) && q.getCardinality() == 3
                        && q.getFiller().equals(lgStud));
        assertThat(flangeCard).as("Flange ⊑ =3 hasLightningGroundingStud").isTrue();

        assertThat(hasIntegerValueOn(cls("Ladder"), "rungSpacing", 280))
                .as("Ladder ⊑ rungSpacing = 280").isTrue();
        assertThat(hasIntegerValueOn(cls("Ladder"), "firstRungToBottomFlange", 140))
                .as("Ladder ⊑ firstRungToBottomFlange = 140").isTrue();
        assertThat(hasIntegerValueOn(cls("LightningGroundingStud"), "lightningStudFlangeDistance", 50))
                .as("LightningGroundingStud ⊑ lightningStudFlangeDistance = 50").isTrue();
    }

    @Test
    @DisplayName("运行时解析：版本/数值约束/基数约束与本体一致")
    void vocabularyParsedFromOntology() {
        assertThat(TmsdVocabulary.ontologyVersion()).isEqualTo("v16.10");
        assertThat(TmsdVocabulary.numericConstraints()).hasSize(19);
        assertThat(TmsdVocabulary.cardinalityConstraints()).hasSize(9);
    }

    @Test
    @DisplayName("v16.10：输出规范（行模板/文本块）已迁入本体，变量名/注释不再是 Java 字面量")
    void v1610OutputSpec() {
        assertThat(ont.containsClassInSignature(IRI.create(NS + "OutputLine"))).isTrue();
        assertThat(ont.containsClassInSignature(IRI.create(NS + "OutputBlock"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "outputTemplate"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "outputText"))).isTrue();
        // 抽样：固定行、索引行、原样块
        assertThat(TmsdVocabulary.outputTemplate("towerSecH")).isEqualTo("SEC_H_TOTAL={{value}}/*筒段总高\n");
        assertThat(TmsdVocabulary.outputTemplate("towerCyT")).isEqualTo("cy{k}_t={{value}}/*筒节{k}壁厚\n");
        assertThat(TmsdVocabulary.outputBlock("skelName"))
                .isEqualTo("/*---------------------| 中文名称 |------------------------------*/\nPART_NAME=PTC_COMMON_NAME\n\n");
        assertThat(TmsdVocabulary.outputBlock("weightInfo")).startsWith("/**设置显示模型中文名称项**/");
    }

    @Test
    @DisplayName("v16.3：档位/角度值集、灯型、机型映射可由本体解析得到")
    void v163MachineReadable() {
        assertThat(TmsdVocabulary.valueSet("accessorySpacingMultiple")).containsExactly(5.0, 6.0, 7.0);
        assertThat(TmsdVocabulary.valueSet("lightningStudInstallAngle")).containsExactly(70.0, 190.0, 310.0);
        assertThat(TmsdVocabulary.stringValue("lightType")).isEqualTo("焊接灯");
        assertThat(TmsdVocabulary.modelParams("V17")).containsExactly(800.0, 650.0, 650.0);
        assertThat(TmsdVocabulary.num("lastBracketToTopFlange")).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("v16.4：平台内径匹配容差 = 6（由配置迁移至本体）")
    void v164PlatformInnerDiameterTolerance() {
        assertThat(hasIntegerValueOn(cls("Platform"), "platformInnerDiameterTolerance", 6))
                .as("Platform ⊑ platformInnerDiameterTolerance = 6").isTrue();
        assertThat(TmsdVocabulary.num("platformInnerDiameterTolerance")).isEqualTo(6.0);
    }

    @Test
    @DisplayName("v16.7：声明式校验实体已写入签名，且迁为 OWL 等价类（无 SWRL、无 :净距下限）")
    void v167DeclarativeVerificationEntities() {
        assertThat(ont.containsClassInSignature(IRI.create(NS + "WeldClearanceCheck"))).isTrue();
        assertThat(ont.containsClassInSignature(IRI.create(NS + "ConstraintViolation"))).isTrue();
        assertThat(ont.containsClassInSignature(IRI.create(NS + "NoAccessorySection"))).isTrue();
        assertThat(ont.containsObjectPropertyInSignature(IRI.create(NS + "checksAccessory"))).isTrue();
        assertThat(ont.containsObjectPropertyInSignature(IRI.create(NS + "checksStud"))).isTrue();
        assertThat(ont.containsObjectPropertyInSignature(IRI.create(NS + "checksWeldSeam"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "clearance"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "accessoryCount"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "clearanceLimit")))
                .as("v16.7 已删除 :净距下限（下限常量化进等价类）").isFalse();

        boolean noAccEq = ont.equivalentClassesAxioms(cls("NoAccessorySection"))
                .anyMatch(a -> a.getClassExpressions().stream()
                        .anyMatch(ce -> ce instanceof OWLObjectIntersectionOf io
                                && io.getOperands().contains(cls("TowerMidSection"))));
        assertThat(noAccEq).as("NoAccessorySection ≡ (… ⊓ :塔架中段 …)").isTrue();

        boolean cvEq = ont.equivalentClassesAxioms(cls("ConstraintViolation"))
                .anyMatch(a -> a.getClassExpressions().stream()
                        .anyMatch(ce -> ce instanceof OWLObjectIntersectionOf io
                                && io.getOperands().contains(cls("WeldClearanceCheck"))));
        assertThat(cvEq).as("ConstraintViolation ≡ (… ⊓ :焊缝净距校验 …)").isTrue();
    }

    @Test
    @DisplayName("v16.8：逐项声明式校验实体（:数值约束校验 / :校验值 / :校验属性名）与 14 个合规等价类已写入本体")
    void v168ValueCheckEntities() {
        assertThat(ont.containsClassInSignature(IRI.create(NS + "ValueCheck"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "checkValue"))).isTrue();
        assertThat(ont.containsDataPropertyInSignature(IRI.create(NS + "checkProperty"))).isTrue();

        String[] compliant = {
                "PlatformToTopCompliant", "AccessorySpacingCompliant", "FirstAccessoryToBottomCompliant",
                "SecondLastToPlatformCompliant", "LastBracketToPlatformCompliant", "FirstLightHeightCompliant",
                "LightMinSpacingCompliant", "LightMaxSpacingCompliant", "LightStudSpacingCompliant",
                "SupportToWeldCompliant", "RungSpacingCompliant", "FirstRungToBottomCompliant",
                "LastBracketToTopCompliant", "LightningStudCompliant"
        };
        for (String local : compliant) {
            assertThat(ont.containsClassInSignature(IRI.create(NS + local)))
                    .as("合规类 :%s 应存在", local).isTrue();
            boolean eq = ont.equivalentClassesAxioms(cls(local))
                    .anyMatch(a -> a.getClassExpressions().stream()
                            .anyMatch(ce -> ce instanceof OWLObjectIntersectionOf io
                                    && io.getOperands().contains(cls("ValueCheck"))));
            assertThat(eq).as(":%s 应等价于 (… ⊓ :数值约束校验 …)", local).isTrue();
        }
    }

    @Test
    @DisplayName("v16.9：抽象基类 :校验（Check）已建立，两个顶级校验类并列为其子类")
    void v169CheckBaseClass() {
        assertThat(ont.containsClassInSignature(IRI.create(NS + "Check"))).isTrue();
        assertThat(supers(cls("WeldClearanceCheck"))).contains(cls("Check"));
        assertThat(supers(cls("ValueCheck"))).contains(cls("Check"));
    }

    // ---- 工具 ----

    private static OWLClass cls(String local) {
        return df.getOWLClass(IRI.create(NS + local));
    }

    private static List<OWLClassExpression> supers(OWLClass c) {
        return ont.subClassAxiomsForSubClass(c)
                .map(OWLSubClassOfAxiom::getSuperClass)
                .toList();
    }

    /** 类 c 的 subClassOf 中是否存在 {@code prop = value}（整数 hasValue）的 Restriction。 */
    private static boolean hasIntegerValueOn(OWLClass c, String propLocal, int value) {
        OWLDataProperty prop = df.getOWLDataProperty(IRI.create(NS + propLocal));
        return supers(c).stream()
                .filter(OWLDataHasValue.class::isInstance)
                .map(OWLDataHasValue.class::cast)
                .anyMatch(dv -> dv.getProperty().equals(prop)
                        && dv.getFiller().isInteger()
                        && dv.getFiller().parseInteger() == value);
    }
}
