package com.ocean.ontologyframework.industryinsight.validation;

import openllet.owlapi.OpenlletReasonerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLRestriction;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IndustryInsight 本体合规校验（纯 OWL API + Openllet，离线，不依赖任何外部服务）。
 *
 * <p>被测对象：第 6 步单文件本体 {@code ontology/industryinsight-all.owl}
 * （第 9 步 TBox/ABox 分离后，本类改为「TBox 入口 + ABox 入口」合并视图加载）。
 *
 * <p>校验：可解析性、一致性、8 大主干域完整性、分析工具/控制点类型/典型 ABox 个体齐备、
 * {@code owl:Restriction} 与 SWRL 规则结构。
 *
 * <p>注：本体由带前缀（单一前缀 {@code http://example.org/industryinsight#}）的 OWLAPI 格式加载，
 * 断言一律用签名 IRI 的**片段包含**匹配，不硬编码完整 IRI 串。
 */
@DisplayName("行业洞察本体合规校验：解析 / 一致性 / 域知识完整性（离线）")
public class IndustryInsightOntologyComplianceTest {

    private static final Logger log = LoggerFactory.getLogger(IndustryInsightOntologyComplianceTest.class);

    private static final Path ONTOLOGY_FILE = Path.of("ontology", "industryinsight-all.owl");

    private static OWLOntology ontology;
    private static List<OWLOntology> closure;
    private static Set<String> classIris;
    private static Set<String> individualIris;

    @BeforeAll
    static void loadOntology() throws Exception {
        assertThat(Files.isRegularFile(ONTOLOGY_FILE))
                .as("主本体应存在: %s (cwd=%s)", ONTOLOGY_FILE.toAbsolutePath(), Path.of("").toAbsolutePath())
                .isTrue();

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        // 与框架 OntologyService 一致：扫描本体目录，自动解析 owl:imports
        File parentDir = ONTOLOGY_FILE.toFile().getParentFile();
        AutoIRIMapper autoMapper = new AutoIRIMapper(parentDir, true);
        autoMapper.update();
        manager.getIRIMappers().add(autoMapper);

        ontology = manager.loadOntologyFromOntologyDocument(ONTOLOGY_FILE.toFile());
        assertThat(ontology.getOntologyID().getOntologyIRI()).isPresent();

        closure = ontology.importsClosure().collect(Collectors.toList());
        classIris = closure.stream()
                .flatMap(o -> o.classesInSignature())
                .map(c -> c.getIRI().toString())
                .collect(Collectors.toSet());
        individualIris = closure.stream()
                .flatMap(o -> o.individualsInSignature())
                .map(i -> i.getIRI().toString())
                .collect(Collectors.toSet());
        log.info("本体加载成功 | closureSize={} | axioms={} | classes={} | individuals={}",
                closure.size(), closure.stream().mapToInt(OWLOntology::getAxiomCount).sum(),
                classIris.size(), individualIris.size());
    }

    @Test
    @DisplayName("P-1 本体可解析且逻辑一致（Openllet isConsistent）")
    void ontologyLoadsAndIsConsistent() {
        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        try {
            reasoner.flush();
            assertThat(reasoner.isConsistent()).as("本体应逻辑一致（一致性的必要前提）").isTrue();
        } finally {
            reasoner.dispose();
        }
    }

    @Test
    @DisplayName("P-2 根类与 8 大主干域齐备")
    void rootAndDomainsComplete() {
        assertThat(classIris).as("应含根类 IndustryInsightConcept")
                .anyMatch(i -> i.contains("IndustryInsightConcept"));
        for (String domain : List.of("ScenarioDomain", "ConclusionDomain", "AnalysisDomain", "FiveLooksInsight",
                "OpportunityDomain", "StrategyDomain", "ReportDomain", "EventDomain")) {
            assertThat(classIris).as("应含主干域 %s", domain).anyMatch(i -> i.contains(domain));
        }
    }

    @Test
    @DisplayName("P-3 分析工具完整（AnalysisTool 个体 = 23，Tool_*）")
    void analysisToolsComplete() {
        long tools = individualIris.stream().filter(i -> i.contains("Tool_")).count();
        assertThat(tools).as("分析工具个体应齐备").isEqualTo(23);
        for (String tool : List.of("Tool_PESTEL", "Tool_PorterFiveForces", "Tool_STP", "Tool_VRIO",
                "Tool_OpportunityMap", "Tool_SPAN", "Tool_FAN")) {
            assertThat(individualIris).as("应含工具 %s", tool).anyMatch(i -> i.contains(tool));
        }
    }

    @Test
    @DisplayName("P-4 战略控制点类型完整（ControlPointType 个体 = 6）")
    void controlPointTypesComplete() {
        long types = individualIris.stream().filter(i -> i.contains("ControlPointType_")).count();
        assertThat(types).as("战略控制点类型个体应齐备").isEqualTo(6);
        for (String type : List.of("ControlPointType_TechPatent", "ControlPointType_CostAdvantage",
                "ControlPointType_CustomerLockIn", "ControlPointType_Brand", "ControlPointType_ChannelBarrier",
                "ControlPointType_StandardVoice")) {
            assertThat(individualIris).as("应含控制点类型 %s", type).anyMatch(i -> i.contains(type));
        }
    }

    @Test
    @DisplayName("P-5 典型 ABox 最小闭环与优先级/SPAN 个体齐备")
    void exampleAboxMinimalLoopPresent() {
        for (String name : List.of("Scenario_Example", "ConclusionSet_Example", "IndustryTrend_Example",
                "MarketCustomer_Example", "Competition_Example", "Self_Example", "OppList_Example",
                "ControlPoint_1", "Report_Example", "Outbound_Example",
                "Priority_High", "Priority_Mid", "Priority_Low", "Span_1", "Span_2")) {
            assertThat(individualIris).as("应含典型个体 %s", name).anyMatch(i -> i.contains(name));
        }
    }

    @Test
    @DisplayName("B-1 Restriction 与 SWRL 规则结构（含 Restriction 的 SubClassOf = 22；SWRL = 4）")
    void restrictionsAndSwrlRulesPresent() {
        long restrictions = closure.stream()
                .flatMap(o -> o.axioms(AxiomType.SUBCLASS_OF))
                .filter(ax -> ax.getSuperClass() instanceof OWLRestriction)
                .count();
        assertThat(restrictions).as("owl:Restriction 子类公理应存在（= 22）").isEqualTo(22);
        long swrlRules = closure.stream()
                .mapToLong(o -> o.axioms(AxiomType.SWRL_RULE).count()).sum();
        assertThat(swrlRules).as("应存在 SWRL 规则（R1/R2/R3a/R3b = 4）").isEqualTo(4);
    }
}
