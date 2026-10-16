package com.ocean.ontologyframework.neinsight.validation;

import com.ocean.openlletresolver.BackendService;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEInsight 本体合规 + 推理校验（纯 Openllet，无需外部环境）。
 *
 * <p>对应《NEInsight应用-测试设计文档》§6.1：P-1 ~ P-7、B-1、N-1。
 * 判定依据全部来自本体（TBox 约束 + SWRL 规则），不臆造。
 */
@SpringBootTest
@ActiveProfiles("NEInsightJunitTest")
@DisplayName("NEInsight 本体合规与推理校验（纯 Openllet，无环境）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NEInsightOntologyComplianceTest {

    private static final Logger log = LoggerFactory.getLogger(NEInsightOntologyComplianceTest.class);

    // ==================== 命名空间 ====================
    private static final String CORE_NS = "http://example.org/neinsight/core#";
    private static final String ENV_NS = "http://example.org/neinsight/env#";
    private static final String DEC_NS = "http://example.org/neinsight/decision#";
    private static final String ABOX_NS = "http://example.org/neinsight/abox#";
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";

    // ==================== 类 ====================
    private static final String C_INSIGHT_ELEMENT = CORE_NS + "InsightElement";
    private static final String C_INSIGHT_DOMAIN = CORE_NS + "InsightDomain";
    private static final String C_OPPORTUNITY = ENV_NS + "Opportunity";
    private static final String C_SPAN_QUADRANT = ENV_NS + "SPANQuadrant";
    private static final String C_CUSTOMER = ENV_NS + "Customer";
    private static final String C_PAIN_POINT = ENV_NS + "CustomerPainPoint";
    private static final String C_CONTROL_POINT = DEC_NS + "StrategicControlPoint";
    private static final String C_CONTROL_POINT_TYPE = DEC_NS + "ControlPointType";
    private static final String C_OBJECTIVE = DEC_NS + "Objective";
    private static final String C_STRATEGY = DEC_NS + "Strategy";

    // ==================== 对象属性 ====================
    private static final String P_BELONGS_TO_DOMAIN = CORE_NS + "belongsToDomain";
    private static final String P_HAS_ELEMENT = CORE_NS + "hasElement";
    private static final String P_HAS_PAIN_POINT = ENV_NS + "hasPainPoint";
    private static final String P_HAS_SPAN_QUADRANT = ENV_NS + "hasSpanQuadrant";
    private static final String P_HAS_CONTROL_POINT_TYPE = DEC_NS + "hasControlPointType";
    private static final String P_ADDRESSES_OPPORTUNITY = DEC_NS + "addressesOpportunity";
    private static final String P_STRATEGY_OF = DEC_NS + "strategyOf";

    // ==================== 数据属性 ====================
    private static final String D_MARKET_ATTRACTIVENESS = ENV_NS + "marketAttractiveness";
    private static final String D_COMPETITIVE_POSITION = ENV_NS + "competitivePosition";
    private static final String D_OPPORTUNITY_PRIORITY = ENV_NS + "opportunityPriority";
    private static final String D_FLAGGED_FOR_ALERT = ENV_NS + "flaggedForAlert";
    private static final String D_IS_MEASURABLE = DEC_NS + "isMeasurable";
    private static final String D_HAS_METRIC = DEC_NS + "hasMetric";
    private static final String D_IS_RELEVANT = DEC_NS + "isRelevant";
    private static final String D_HAS_TIMEFRAME = DEC_NS + "hasTimeframe";
    private static final String D_SMART_COMPLIANT = DEC_NS + "smartCompliant";

    // ==================== ABox 个体 ====================
    // 应用口径：不内置业务实例数据；合规用例仅引用保留的洞察域个体，业务相关断言一律经 withTempAxioms 临时注入。
    private static final String I_CLOUD_SPACE = ABOX_NS + "CLOUD_SPACE";
    private static final String I_Q_HIGH_HIGH = ABOX_NS + "Q_HighHigh";

    /** 互逆对象属性对（取自本体核心设计 §2）。 */
    private static final String[][] INVERSE_PAIRS = {
            {P_BELONGS_TO_DOMAIN, P_HAS_ELEMENT},
            {ENV_NS + "hasMacroFactor", ENV_NS + "macroFactorOf"},
            {ENV_NS + "targetsMarket", ENV_NS + "marketOf"},
            {ENV_NS + "hasCustomer", ENV_NS + "customerOf"},
            {ENV_NS + "hasPainPoint", ENV_NS + "painPointOf"},
            {ENV_NS + "hasNeed", ENV_NS + "needOf"},
            {ENV_NS + "involvesCompetitor", ENV_NS + "competitorIn"},
            {ENV_NS + "hasCapability", ENV_NS + "capabilityOf"},
            {ENV_NS + "hasShortBoard", ENV_NS + "shortBoardOf"},
            {ENV_NS + "hasOpportunity", ENV_NS + "opportunityOf"},
            {DEC_NS + "addressesOpportunity", DEC_NS + "opportunityAddressedBy"},
            {DEC_NS + "hasObjective", DEC_NS + "objectiveOf"},
            {DEC_NS + "hasStrategy", DEC_NS + "strategyOf"},
    };

    private static BackendService backendService;
    private static OWLOntology ontology;
    private static OWLDataFactory df;

    private Set<OWLAxiom> fullBaselineSnapshot;

    @BeforeAll
    static void setUp(@Autowired Environment env) throws Exception {
        String mainOntologyPath = env.getProperty("ontology.main-path");
        backendService = BackendService.getInstance(mainOntologyPath);
        assertNotNull(backendService, "BackendService 初始化失败");
        ontology = backendService.getTBoxOntology();
        df = ontology.getOWLOntologyManager().getOWLDataFactory();
        log.info("=== NEInsight 本体合规测试环境就绪 | ontology={} | 公理数={} ===",
                mainOntologyPath, ontology.getAxiomCount());
    }

    @BeforeEach
    void snapshotFullBaseline() {
        fullBaselineSnapshot = Collections.unmodifiableSet(new HashSet<>(ontology.getAxioms()));
        backendService.getReasonerService().getReasoner().flush();
        log.info("📸 全量快照已保存，基线公理数: {}", fullBaselineSnapshot.size());
    }

    @AfterEach
    void restoreOntology() {
        if (fullBaselineSnapshot == null) return;
        try {
            Set<OWLAxiom> current = ontology.getAxioms();
            if (current.size() == fullBaselineSnapshot.size() && current.containsAll(fullBaselineSnapshot)) {
                log.info("✅ 本体无变更，无需恢复");
                return;
            }
            log.warn("⚠️ 检测到本体漂移，正在原子恢复...");
            OWLOntologyManager manager = ontology.getOWLOntologyManager();
            assertSame(ChangeApplied.SUCCESSFULLY, manager.removeAxioms(ontology, current));
            assertSame(ChangeApplied.SUCCESSFULLY, manager.addAxioms(ontology, fullBaselineSnapshot));
            backendService.getReasonerService().getReasoner().flush();
            log.info("🧹 本体及推理器已恢复至基线，当前公理数: {}", ontology.getAxiomCount());
        } finally {
            fullBaselineSnapshot = null;
        }
    }

    // ==================== P-1 一致性 ====================
    @Test
    @Order(1)
    @DisplayName("P-1 ontologyIsConsistent：本体一致性校验通过")
    void ontologyIsConsistent() {
        boolean consistent = backendService.getReasonerService().getReasoner().isConsistent();
        assertTrue(consistent, "NEInsight 本体应一致");
    }

    // ==================== P-2 互逆闭合 ====================
    @Test
    @Order(2)
    @DisplayName("P-2 inversePropertiesClosed：互逆对象属性双向声明闭合，belongsToDomain 可逆向检索要素")
    void inversePropertiesClosed() {
        Set<String> declaredInversePairs = new HashSet<>();
        for (OWLInverseObjectPropertiesAxiom ax : ontology.axioms(AxiomType.INVERSE_OBJECT_PROPERTIES).toList()) {
            String first = ax.getFirstProperty().asOWLObjectProperty().getIRI().toString();
            String second = ax.getSecondProperty().asOWLObjectProperty().getIRI().toString();
            declaredInversePairs.add(first + "|" + second);
            declaredInversePairs.add(second + "|" + first);
        }
        for (String[] pair : INVERSE_PAIRS) {
            assertTrue(declaredInversePairs.contains(pair[0] + "|" + pair[1]),
                    "应有互逆声明：" + shortName(pair[0]) + " ⇄ " + shortName(pair[1]));
        }

        OWLObjectProperty hasElement = df.getOWLObjectProperty(IRI.create(P_HAS_ELEMENT));
        OWLNamedIndividual cloudSpace = ind(I_CLOUD_SPACE);
        // 业务实例已不内置：临时注入归属云空间的机会点，验证 hasElement ⇄ belongsToDomain 互逆可逆向检索
        OWLNamedIndividual tempOpp = ind(ABOX_NS + "Temp_Opp_ReverseLookup");
        Set<OWLAxiom> temp = new HashSet<>();
        temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OPPORTUNITY)), tempOpp));
        temp.add(objAssertion(tempOpp, P_BELONGS_TO_DOMAIN, cloudSpace));
        Set<OWLNamedIndividual> reverse = backendService.withTempAxioms(temp,
                reasoner -> backendService.getObjectPropertyAllValueOfIndividual(cloudSpace, hasElement));
        assertTrue(reverse.contains(tempOpp),
                "云空间应可经逆向（hasElement）检索到归属其下的机会点");
        log.info("✅ 互逆校验通过 | 对数={} | 云空间逆向要素数={}", INVERSE_PAIRS.length, reverse.size());
    }

    // ==================== P-3 基数约束 ====================
    @Test
    @Order(3)
    @DisplayName("P-3 cardinalityRestrictionsHold：各关键类限定基数约束齐备")
    void cardinalityRestrictionsHold() {
        assertEquals(true, hasObjectRestriction(C_OPPORTUNITY, P_HAS_SPAN_QUADRANT, "exact", 1, C_SPAN_QUADRANT),
                "Opportunity.hasSpanQuadrant 应为限定基数 1 (SPANQuadrant)");
        assertEquals(true, hasDataRestriction(C_OPPORTUNITY, D_MARKET_ATTRACTIVENESS, "max", 1, XSD_NS + "decimal"),
                "Opportunity.marketAttractiveness 应为至多 1 (xsd:decimal)");
        assertEquals(true, hasDataRestriction(C_OPPORTUNITY, D_COMPETITIVE_POSITION, "max", 1, XSD_NS + "decimal"),
                "Opportunity.competitivePosition 应为至多 1 (xsd:decimal)");

        assertEquals(true, hasObjectRestriction(C_CUSTOMER, P_HAS_PAIN_POINT, "min", 1, C_PAIN_POINT),
                "Customer.hasPainPoint 应 ≥1");

        assertEquals(true, hasObjectRestriction(C_CONTROL_POINT, P_HAS_CONTROL_POINT_TYPE, "exact", 1, C_CONTROL_POINT_TYPE),
                "StrategicControlPoint.hasControlPointType 应为限定基数 1");
        assertEquals(true, hasObjectRestriction(C_CONTROL_POINT, P_ADDRESSES_OPPORTUNITY, "min", 1, C_OPPORTUNITY),
                "StrategicControlPoint.addressesOpportunity 应 ≥1");

        assertEquals(true, hasDataRestriction(C_OBJECTIVE, D_IS_MEASURABLE, "exact", 1, XSD_NS + "boolean"),
                "Objective.isMeasurable 应为限定基数 1");
        assertEquals(true, hasDataRestriction(C_OBJECTIVE, D_HAS_METRIC, "min", 1, XSD_NS + "string"),
                "Objective.hasMetric 应 ≥1");

        assertEquals(true, hasObjectRestriction(C_STRATEGY, P_STRATEGY_OF, "min", 1, C_OBJECTIVE),
                "Strategy.strategyOf 应 ≥1");

        assertEquals(true, hasObjectRestriction(C_INSIGHT_ELEMENT, P_BELONGS_TO_DOMAIN, "min", 1, C_INSIGHT_DOMAIN),
                "InsightElement.belongsToDomain 应 ≥1");
        log.info("✅ 基数约束校验通过");
    }

    // ==================== P-4 SWRL R1 ====================
    @Test
    @Order(4)
    @DisplayName("P-4 swrlR1DerivesHighPriority：SWRL R1 推导机会点优先级 HIGH")
    void swrlR1DerivesHighPriority() {
        OWLNamedIndividual opp = ind(ABOX_NS + "Temp_Opp_High");
        Set<OWLAxiom> temp = new HashSet<>();
        temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OPPORTUNITY)), opp));
        temp.add(objAssertion(opp, P_BELONGS_TO_DOMAIN, ind(I_CLOUD_SPACE)));
        temp.add(dataAssertion(opp, D_MARKET_ATTRACTIVENESS, 4.5));
        temp.add(dataAssertion(opp, D_COMPETITIVE_POSITION, 4.0));
        temp.add(objAssertion(opp, P_HAS_SPAN_QUADRANT, ind(I_Q_HIGH_HIGH)));

        String priority = backendService.withTempAxioms(temp, reasoner -> {
            Set<OWLLiteral> vs = reasoner.getDataPropertyValues(opp,
                    df.getOWLDataProperty(IRI.create(D_OPPORTUNITY_PRIORITY)));
            return vs.isEmpty() ? null : vs.iterator().next().getLiteral();
        });
        assertEquals("HIGH", priority, "两轴均 >3.0 的机会点应被 R1 推导为 HIGH");
        assertFalse(isAxiomPresent(temp), "withTempAxioms 结束后临时公理应被清除");
    }

    // ==================== P-5 SWRL R2 ====================
    @Test
    @Order(5)
    @DisplayName("P-5 swrlR2DerivesFlaggedForAlert：SWRL R2 由 HIGH 优先级推导外发标记 true")
    void swrlR2DerivesFlaggedForAlert() {
        OWLNamedIndividual opp = ind(ABOX_NS + "Temp_Opp_High");
        Set<OWLAxiom> temp = new HashSet<>();
        temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OPPORTUNITY)), opp));
        temp.add(objAssertion(opp, P_BELONGS_TO_DOMAIN, ind(I_CLOUD_SPACE)));
        temp.add(dataAssertion(opp, D_MARKET_ATTRACTIVENESS, 4.5));
        temp.add(dataAssertion(opp, D_COMPETITIVE_POSITION, 4.0));
        temp.add(objAssertion(opp, P_HAS_SPAN_QUADRANT, ind(I_Q_HIGH_HIGH)));

        Boolean flagged = backendService.withTempAxioms(temp, reasoner -> {
            Set<OWLLiteral> vs = reasoner.getDataPropertyValues(opp,
                    df.getOWLDataProperty(IRI.create(D_FLAGGED_FOR_ALERT)));
            return vs.isEmpty() ? null : Boolean.parseBoolean(vs.iterator().next().getLiteral());
        });
        assertEquals(Boolean.TRUE, flagged, "高优先级机会点应被 R2 推导 flaggedForAlert=true");
        assertFalse(isAxiomPresent(temp), "withTempAxioms 结束后临时公理应被清除");
    }

    // ==================== P-6 SWRL R3 ====================
    @Test
    @Order(6)
    @DisplayName("P-6 swrlR3DerivesSmartCompliant：SWRL R3 推导目标 SMART 合规")
    void swrlR3DerivesSmartCompliant() {
        OWLNamedIndividual obj = ind(ABOX_NS + "Temp_Obj_Smart");
        Set<OWLAxiom> temp = new HashSet<>();
        temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OBJECTIVE)), obj));
        temp.add(objAssertion(obj, P_BELONGS_TO_DOMAIN, ind(I_CLOUD_SPACE)));
        temp.add(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty(IRI.create(D_IS_MEASURABLE)),
                obj, df.getOWLLiteral(true)));
        temp.add(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty(IRI.create(D_HAS_METRIC)),
                obj, df.getOWLLiteral("营收增长率", df.getOWLDatatype(IRI.create(XSD_NS + "string")))));
        temp.add(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty(IRI.create(D_IS_RELEVANT)),
                obj, df.getOWLLiteral(true)));
        temp.add(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty(IRI.create(D_HAS_TIMEFRAME)),
                obj, df.getOWLLiteral("2026-2030", df.getOWLDatatype(IRI.create(XSD_NS + "string")))));

        Boolean compliant = backendService.withTempAxioms(temp, reasoner -> {
            Set<OWLLiteral> vs = reasoner.getDataPropertyValues(obj,
                    df.getOWLDataProperty(IRI.create(D_SMART_COMPLIANT)));
            return vs.isEmpty() ? null : Boolean.parseBoolean(vs.iterator().next().getLiteral());
        });
        assertEquals(Boolean.TRUE, compliant, "满足 SMART 前置的目标应被 R3 推导 smartCompliant=true");
        assertFalse(isAxiomPresent(temp), "withTempAxioms 结束后临时公理应被清除");
    }

    // ==================== P-7 SWRL R1b（临时注入） ====================
    @Test
    @Order(7)
    @DisplayName("P-7 swrlR1bDerivesLowPriority：SWRL R1b 推导低分机会点优先级 LOW（withTempAxioms 隔离）")
    void swrlR1bDerivesLowPriority() {
        OWLNamedIndividual low = ind(ABOX_NS + "Temp_Opp_Low");
        Set<OWLAxiom> temp = new HashSet<>();
        temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OPPORTUNITY)), low));
        temp.add(objAssertion(low, P_BELONGS_TO_DOMAIN, ind(I_CLOUD_SPACE)));
        temp.add(dataAssertion(low, D_MARKET_ATTRACTIVENESS, 1.0));
        temp.add(dataAssertion(low, D_COMPETITIVE_POSITION, 1.0));
        temp.add(objAssertion(low, P_HAS_SPAN_QUADRANT, ind(I_Q_HIGH_HIGH)));

        OWLDataProperty prop = df.getOWLDataProperty(IRI.create(D_OPPORTUNITY_PRIORITY));
        Set<OWLLiteral> vals = backendService.withTempAxioms(temp,
                reasoner -> reasoner.getDataPropertyValues(low, prop));

        assertEquals(1, vals.size(), "R1b 应推导出唯一优先级值");
        assertEquals("LOW", vals.iterator().next().getLiteral(), "两轴均 <3.0 的机会点应被 R1b 推导为 LOW");
        assertFalse(isAxiomPresent(temp), "withTempAxioms 结束后临时公理应被清除");
    }

    // ==================== B-1 缺轴不臆造 ====================
    @Test
    @Order(8)
    @DisplayName("B-1 missingAxisNoPriority：缺一 SPAN 轴时不推断优先级（不臆造）")
    void missingAxisNoPriority() {
        OWLNamedIndividual missing = ind(ABOX_NS + "Temp_Opp_MissingAxis");
        Set<OWLAxiom> temp = new HashSet<>();
        temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OPPORTUNITY)), missing));
        temp.add(objAssertion(missing, P_BELONGS_TO_DOMAIN, ind(I_CLOUD_SPACE)));
        temp.add(dataAssertion(missing, D_MARKET_ATTRACTIVENESS, 4.5));
        temp.add(objAssertion(missing, P_HAS_SPAN_QUADRANT, ind(I_Q_HIGH_HIGH)));

        OWLDataProperty prop = df.getOWLDataProperty(IRI.create(D_OPPORTUNITY_PRIORITY));
        Set<OWLLiteral> vals = backendService.withTempAxioms(temp,
                reasoner -> reasoner.getDataPropertyValues(missing, prop));

        assertTrue(vals.isEmpty(), "缺 competitivePosition 时不应推断出 opportunityPriority");
        assertFalse(isAxiomPresent(temp), "withTempAxioms 结束后临时公理应被清除");
    }

    // ==================== N-1 基数违规可检出 ====================
    @Test
    @Order(9)
    @DisplayName("N-1 quadrantCardinalityViolationDetected：同机会点挂 2 个互异 SPAN 象限被基数校验拦截")
    void quadrantCardinalityViolationDetected() {
        OWLNamedIndividual opp = ind(ABOX_NS + "Temp_Opp_TwoQuadrants");
        OWLNamedIndividual q2 = ind(ABOX_NS + "Temp_Quad_2");
        Set<OWLAxiom> temp = new HashSet<>();
        temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_SPAN_QUADRANT)), q2));
        temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OPPORTUNITY)), opp));
        temp.add(objAssertion(opp, P_BELONGS_TO_DOMAIN, ind(I_CLOUD_SPACE)));
        temp.add(dataAssertion(opp, D_MARKET_ATTRACTIVENESS, 4.5));
        temp.add(dataAssertion(opp, D_COMPETITIVE_POSITION, 4.0));
        temp.add(objAssertion(opp, P_HAS_SPAN_QUADRANT, ind(I_Q_HIGH_HIGH)));
        temp.add(objAssertion(opp, P_HAS_SPAN_QUADRANT, q2));
        // 关键：声明两象限互异，否则 functional 属性会将两填充推断为同一个个体（一致）
        temp.add(df.getOWLDifferentIndividualsAxiom(q2, ind(I_Q_HIGH_HIGH)));

        assertThrows(InconsistentOntologyException.class,
                () -> backendService.validateAxioms(temp),
                "同一机会点挂 2 个互异 SPAN 象限应违反至多 1，校验须检出不一致");
    }

    // ==================== 辅助 ====================

    private static OWLNamedIndividual ind(String iri) {
        return df.getOWLNamedIndividual(IRI.create(iri));
    }

    private OWLAxiom objAssertion(OWLNamedIndividual s, String propIri, OWLNamedIndividual o) {
        return df.getOWLObjectPropertyAssertionAxiom(
                df.getOWLObjectProperty(IRI.create(propIri)), s, o);
    }

    private OWLAxiom dataAssertion(OWLNamedIndividual s, String propIri, double value) {
        return df.getOWLDataPropertyAssertionAxiom(
                df.getOWLDataProperty(IRI.create(propIri)), s,
                df.getOWLLiteral(String.valueOf(value), df.getOWLDatatype(IRI.create(XSD_NS + "decimal"))));
    }

    private boolean isAxiomPresent(Set<OWLAxiom> axioms) {
        return axioms.stream().anyMatch(a -> ontology.containsAxiom(a, true));
    }

    private String readStringValue(OWLNamedIndividual i, String propIri) {
        Set<OWLLiteral> vs = backendService.getDataPropertyValueOfIndividual(i, propIri);
        return vs.isEmpty() ? null : vs.iterator().next().getLiteral();
    }

    private Boolean readBooleanValue(OWLNamedIndividual i, String propIri) {
        Set<OWLLiteral> vs = backendService.getDataPropertyValueOfIndividual(i, propIri);
        return vs.isEmpty() ? null : Boolean.parseBoolean(vs.iterator().next().getLiteral());
    }

    private boolean hasObjectRestriction(String classIri, String propIri, String kind, int n, String fillerIri) {
        OWLClass cls = df.getOWLClass(IRI.create(classIri));
        OWLObjectProperty prop = df.getOWLObjectProperty(IRI.create(propIri));
        OWLClass filler = df.getOWLClass(IRI.create(fillerIri));
        for (OWLSubClassOfAxiom ax : ontology.subClassAxiomsForSubClass(cls).toList()) {
            OWLClassExpression sup = ax.getSuperClass();
            if ("exact".equals(kind) && sup instanceof OWLObjectExactCardinality c
                    && c.getProperty().asOWLObjectProperty().equals(prop)
                    && c.getCardinality() == n && c.getFiller().equals(filler)) {
                return true;
            }
            if ("min".equals(kind) && sup instanceof OWLObjectMinCardinality c
                    && c.getProperty().asOWLObjectProperty().equals(prop)
                    && c.getCardinality() == n && c.getFiller().equals(filler)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDataRestriction(String classIri, String propIri, String kind, int n, String fillerDatatypeIri) {
        OWLClass cls = df.getOWLClass(IRI.create(classIri));
        OWLDataProperty prop = df.getOWLDataProperty(IRI.create(propIri));
        OWLDatatype filler = df.getOWLDatatype(IRI.create(fillerDatatypeIri));
        for (OWLSubClassOfAxiom ax : ontology.subClassAxiomsForSubClass(cls).toList()) {
            OWLClassExpression sup = ax.getSuperClass();
            if ("exact".equals(kind) && sup instanceof OWLDataExactCardinality c
                    && c.getProperty().asOWLDataProperty().equals(prop)
                    && c.getCardinality() == n && c.getFiller().equals(filler)) {
                return true;
            }
            if ("min".equals(kind) && sup instanceof OWLDataMinCardinality c
                    && c.getProperty().asOWLDataProperty().equals(prop)
                    && c.getCardinality() == n && c.getFiller().equals(filler)) {
                return true;
            }
            if ("max".equals(kind) && sup instanceof OWLDataMaxCardinality c
                    && c.getProperty().asOWLDataProperty().equals(prop)
                    && c.getCardinality() == n && c.getFiller().equals(filler)) {
                return true;
            }
        }
        return false;
    }

    private static String shortName(String iri) {
        int i = Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
        return i < 0 ? iri : iri.substring(i + 1);
    }
}
