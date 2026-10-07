package com.ocean.ontologyframework.tmsd;

import openllet.owlapi.OpenlletReasonerFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v16.7 声明式校验层（{@code TowerMidSection.owl} 内嵌 OWL 等价类/子类公理）分类验证。
 *
 * <p>验证原 v16.6 的 4 条 SWRL 规则迁为纯 OWL 公理后，仍由 Openllet 分类/一致性正确推出：
 * <ol>
 *   <li>净距校验个体 :净距 ≤ 100 ⇒ :约束违规（等价类）；</li>
 *   <li>:塔架中段 :附件数量 = 0 ⇒ :无附件段（等价类）；</li>
 *   <li>连接方式 ⇒ 附件类型（子类公理，GCI）。</li>
 * </ol>
 *
 * <p>与 {@code TmsdSwrlSpikeTest}（能力/性能边界探针）不同，本类是产品规则的回归用例。
 */
class TmsdDeclarativeCheckTest {

    private static final String NS = "http://goldwind.com/ontology/tower-mid-section#";

    private static OWLOntology ont;
    private static OWLDataFactory df;

    @BeforeAll
    static void load() throws Exception {
        Path owlPath = Paths.get(TmsdTestConfig.ontology("main-path"));
        assertThat(owlPath).exists();
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        ont = m.loadOntologyFromOntologyDocument(owlPath.toFile());
        df = m.getOWLDataFactory();
    }

    @Test
    @DisplayName("规则1：净距 ≤ 100 ⇒ 约束违规（OWL 等价类分类，合规个体不受影响）")
    void clearanceViolationRuleFires() {
        OWLOntologyManager m = ont.getOWLOntologyManager();

        OWLNamedIndividual bad = ind("test_check_bad");
        m.addAxiom(ont, df.getOWLClassAssertionAxiom(cls("WeldClearanceCheck"), bad));
        m.addAxiom(ont, dp(bad, "clearance", 50));

        OWLNamedIndividual ok = ind("test_check_ok");
        m.addAxiom(ont, df.getOWLClassAssertionAxiom(cls("WeldClearanceCheck"), ok));
        m.addAxiom(ont, dp(ok, "clearance", 150));

        OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        try {
            r.precomputeInferences();
            assertThat(r.getInstances(cls("ConstraintViolation"), false).getFlattened())
                    .as("净距 50 ≤ 100 的净距校验个体应被判为 :约束违规").contains(bad);
            assertThat(r.getInstances(cls("ConstraintViolation"), false).getFlattened())
                    .as("净距 150 > 100 的净距校验个体不应被判为 :约束违规").doesNotContain(ok);
        } finally {
            r.dispose();
        }
    }

    @Test
    @DisplayName("规则2：附件数量 = 0 ⇒ 无附件段（OWL 等价类分类）")
    void noAccessorySectionRuleFires() {
        OWLOntologyManager m = ont.getOWLOntologyManager();

        OWLNamedIndividual empty = ind("test_mid_no_accessory");
        m.addAxiom(ont, df.getOWLClassAssertionAxiom(cls("TowerMidSection"), empty));
        m.addAxiom(ont, dp(empty, "accessoryCount", 0));

        OWLNamedIndividual filled = ind("test_mid_with_accessory");
        m.addAxiom(ont, df.getOWLClassAssertionAxiom(cls("TowerMidSection"), filled));
        m.addAxiom(ont, dp(filled, "accessoryCount", 5));

        OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        try {
            r.precomputeInferences();
            assertThat(r.getInstances(cls("NoAccessorySection"), false).getFlattened())
                    .as("附件数量 0 的塔架中段应被判为 :无附件段").contains(empty);
            assertThat(r.getInstances(cls("NoAccessorySection"), false).getFlattened())
                    .as("附件数量 5 的塔架中段不应被判为 :无附件段").doesNotContain(filled);
        } finally {
            r.dispose();
        }
    }

    @Test
    @DisplayName("规则3/4：连接方式 ⇒ 附件类型（OWL 子类公理派生）")
    void accessoryTypeRuleFires() {
        OWLOntologyManager m = ont.getOWLOntologyManager();

        OWLNamedIndividual welded = ind("test_acc_welded");
        m.addAxiom(ont, df.getOWLClassAssertionAxiom(cls("LadderSupport"), welded));
        m.addAxiom(ont, dpStr(welded, "accessoryConnectionType", "焊接式"));

        OWLNamedIndividual bonded = ind("test_acc_bonded");
        m.addAxiom(ont, df.getOWLClassAssertionAxiom(cls("LadderSupport"), bonded));
        m.addAxiom(ont, dpStr(bonded, "accessoryConnectionType", "粘贴式"));

        OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        try {
            r.precomputeInferences();
            OWLDataProperty type = df.getOWLDataProperty(IRI.create(NS + "accessoryType"));
            assertThat(r.getDataPropertyValues(welded, type))
                    .as("连接方式 = 焊接式 ⇒ :附件类型 = 焊接式附件总成")
                    .anyMatch(x -> "焊接式附件总成".equals(x.getLiteral()));
            assertThat(r.getDataPropertyValues(bonded, type))
                    .as("连接方式 = 粘贴式 ⇒ :附件类型 = 粘贴式附件总成")
                    .anyMatch(x -> "粘贴式附件总成".equals(x.getLiteral()));
        } finally {
            r.dispose();
        }
    }

    @Test
    @DisplayName("回读机制：flush 后等价类分类生效，且小数净距按十进制正确判定（服务 assessAll 同款路径）")
    void flushReadbackHonoursDecimal() {
        OWLOntologyManager m = ont.getOWLOntologyManager();
        OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        Set<OWLAxiom> added = new LinkedHashSet<>();
        OWLNamedIndividual bad = ind("test_flush_bad");
        OWLNamedIndividual ok = ind("test_flush_ok");
        try {
            r.precomputeInferences();

            added.add(df.getOWLClassAssertionAxiom(cls("WeldClearanceCheck"), bad));
            added.add(dpDec(bad, "clearance", 99.5));
            added.add(df.getOWLClassAssertionAxiom(cls("WeldClearanceCheck"), ok));
            added.add(dpDec(ok, "clearance", 100.5));

            // 与服务 assessAll 一致：注入后 flush（不重建推理器）
            m.addAxioms(ont, added);
            r.flush();

            Set<OWLNamedIndividual> flagged = r.getInstances(cls("ConstraintViolation"), false).getFlattened();
            assertThat(flagged).as("99.5 ≤ 100 的净距个体应被标记为 :约束违规（含小数）").contains(bad);
            assertThat(flagged).as("100.5 > 100 的净距个体不应被标记").doesNotContain(ok);

            // 回读后立即清除，推理器应恢复
            m.removeAxioms(ont, added);
            r.flush();
            assertThat(r.getInstances(cls("ConstraintViolation"), false).getFlattened())
                    .as("移除注入公理后不应残留本轮违规个体").doesNotContain(bad, ok);
        } finally {
            m.removeAxioms(ont, added);
            r.dispose();
        }
    }

    @Test
    @DisplayName("逐项合规类（v16.8）：合规值归入对应合规类，越限值不归入（Openllet 分类）")
    void valueCheckCompliantClassesClassify() {
        OWLOntologyManager m = ont.getOWLOntologyManager();
        Set<OWLAxiom> added = new LinkedHashSet<>();
        // 合规样本
        added.addAll(vc("ok1", "platformToTopDistance", 1250));   // =1250
        added.addAll(vc("bad1", "platformToTopDistance", 1300));  // 越限
        added.addAll(vc("ok2", "rungSpacing", 280));              // =280
        added.addAll(vc("bad2", "rungSpacing", 300));             // 越限
        added.addAll(vc("ok3", "accessoryCenterSpacing", 1680));  // ∈[1400,1960]
        added.addAll(vc("bad3", "accessoryCenterSpacing", 1260)); // 越限
        added.addAll(vc("ok4", "supportToWeldDistance", 150));    // >100
        added.addAll(vc("bad4", "supportToWeldDistance", 100));   // 不满足 >100
        added.addAll(vc("ok5", "lightToLightMinSpacing", 6000));  // ≥5000
        added.addAll(vc("bad5", "lightToLightMinSpacing", 4000)); // 越限

        OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        try {
            r.precomputeInferences();
            m.addAxioms(ont, added);
            r.flush();

            assertIn(r, "PlatformToTopCompliant", "ok1", true);
            assertIn(r, "PlatformToTopCompliant", "bad1", false);
            assertIn(r, "RungSpacingCompliant", "ok2", true);
            assertIn(r, "RungSpacingCompliant", "bad2", false);
            assertIn(r, "AccessorySpacingCompliant", "ok3", true);
            assertIn(r, "AccessorySpacingCompliant", "bad3", false);
            assertIn(r, "SupportToWeldCompliant", "ok4", true);
            assertIn(r, "SupportToWeldCompliant", "bad4", false);
            assertIn(r, "LightMinSpacingCompliant", "ok5", true);
            assertIn(r, "LightMinSpacingCompliant", "bad5", false);
        } finally {
            m.removeAxioms(ont, added);
            r.dispose();
        }
    }

    // ---- 工具 ----

    /** 构建一个「逐项校验个体」的三个公理（:ValueCheck / :checkProperty / :checkValue）。 */
    private static Set<OWLAxiom> vc(String name, String prop, double value) {
        OWLNamedIndividual c = ind("test_vc_" + name);
        Set<OWLAxiom> s = new LinkedHashSet<>();
        s.add(df.getOWLClassAssertionAxiom(cls("ValueCheck"), c));
        s.add(dpStr(c, "checkProperty", prop));
        s.add(dpDec(c, "checkValue", value));
        return s;
    }

    private static void assertIn(OWLReasoner r, String classLocal, String name, boolean expected) {
        boolean in = r.getInstances(cls(classLocal), false).getFlattened().contains(ind("test_vc_" + name));
        assertThat(in)
                .as("个体 test_vc_%s 是否归入 :%s（期望 %s）", name, classLocal, expected)
                .isEqualTo(expected);
    }

    private static OWLClass cls(String local) {
        return df.getOWLClass(IRI.create(NS + local));
    }

    private static OWLNamedIndividual ind(String local) {
        return df.getOWLNamedIndividual(IRI.create(NS + local));
    }

    private static org.semanticweb.owlapi.model.OWLAxiom dp(OWLNamedIndividual s, String propLocal, int v) {
        OWLDataProperty p = df.getOWLDataProperty(IRI.create(NS + propLocal));
        return df.getOWLDataPropertyAssertionAxiom(p, s, df.getOWLLiteral(v));
    }

    private static OWLAxiom dpDec(OWLNamedIndividual s, String propLocal, double v) {
        OWLDataProperty p = df.getOWLDataProperty(IRI.create(NS + propLocal));
        return df.getOWLDataPropertyAssertionAxiom(p, s,
                df.getOWLLiteral(BigDecimal.valueOf(v).toPlainString(),
                        df.getOWLDatatype(OWL2Datatype.XSD_DECIMAL)));
    }

    private static org.semanticweb.owlapi.model.OWLAxiom dpStr(OWLNamedIndividual s, String propLocal, String v) {
        OWLDataProperty p = df.getOWLDataProperty(IRI.create(NS + propLocal));
        return df.getOWLDataPropertyAssertionAxiom(p, s, df.getOWLLiteral(v));
    }
}
