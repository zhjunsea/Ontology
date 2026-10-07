package com.ocean.ontologyframework.tmsd;

import openllet.owlapi.OpenlletReasonerFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLClassAtom;
import org.semanticweb.owlapi.model.SWRLDataPropertyAtom;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.vocab.SWRLBuiltInsVocabulary;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * 【技术验证 spike，非产品用例】验证 Openllet 的 SWRL 能力与性能边界，用于 B 档
 * 「全面声明式改造」的可行性评估。仅新建内存本体 / 在内存中追加规则，不改动任何
 * 产品源码或本体文件。
 *
 * <p>覆盖三个问题：
 * <ol>
 *   <li>比较类 built-ins（{@code lessThan}/{@code greaterThan}）作为过滤条件推出类是否生效；</li>
 *   <li>算术类 built-ins（{@code add}）能否绑定结果变量、产出新的数据属性值；</li>
 *   <li>规则数量（0/100/300）对推理（reasoner 创建 + flush）耗时的影响（真实本体）。</li>
 * </ol>
 */
class TmsdSwrlSpikeTest {

    private static final String NS = "http://goldwind.com/ontology/tower-mid-section#";
    private static final String SP = NS + "spike/";

    // ============================================================
    // spike-1：比较 built-ins 过滤
    // ============================================================

    @Test
    @DisplayName("spike-1 比较 built-ins：lessThan 过滤推出违规类")
    void comparisonBuiltinsFilter() {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology ont = newEmpty(m);

        OWLClass part = declClass(df, "SpikePart");
        OWLClass low = declClass(df, "SpikeLow");
        OWLDataProperty stock = declDp(df, "spikeStock");
        OWLDataProperty limit = declDp(df, "spikeLimit");
        declare(m, ont, part, low, stock, limit);

        OWLNamedIndividual p1 = df.getOWLNamedIndividual(IRI.create(SP + "p1"));
        m.addAxiom(ont, df.getOWLClassAssertionAxiom(part, p1));
        m.addAxiom(ont, df.getOWLDataPropertyAssertionAxiom(stock, p1, 5));
        m.addAxiom(ont, df.getOWLDataPropertyAssertionAxiom(limit, p1, 10));

        SWRLVariable x = df.getSWRLVariable(IRI.create(SP + "x"));
        SWRLVariable s = df.getSWRLVariable(IRI.create(SP + "s"));
        SWRLVariable l = df.getSWRLVariable(IRI.create(SP + "l"));
        SWRLClassAtom bodyClass = df.getSWRLClassAtom(part, x);
        SWRLDataPropertyAtom stockAtom = df.getSWRLDataPropertyAtom(stock, x, s);
        SWRLDataPropertyAtom limitAtom = df.getSWRLDataPropertyAtom(limit, x, l);
        SWRLBuiltInAtom lt = df.getSWRLBuiltInAtom(
                SWRLBuiltInsVocabulary.LESS_THAN.getIRI(), List.of(s, l));
        SWRLClassAtom head = df.getSWRLClassAtom(low, x);
        m.addAxiom(ont, df.getSWRLRule(
                Set.<SWRLAtom>of(bodyClass, stockAtom, limitAtom, lt),
                Set.<SWRLAtom>of(head)));

        OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        try {
            r.precomputeInferences();
            boolean isLow = r.getInstances(low, false).getFlattened().contains(p1);
            System.out.println("[spike-1] lessThan 过滤 → SpikeLow(p1) = " + isLow);
            org.assertj.core.api.Assertions.assertThat(isLow)
                    .as("lessThan built-in 应作为过滤条件推出 SpikeLow(p1)").isTrue();
        } finally {
            r.dispose();
        }
    }

    // ============================================================
    // spike-2：算术 built-ins 绑定结果变量
    // ============================================================

    @Test
    @DisplayName("spike-2 算术 built-ins：add 绑定结果变量（2×库存）")
    void arithmeticBuiltinBinds() {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology ont = newEmpty(m);

        OWLClass part = declClass(df, "SpikePart");
        OWLDataProperty stock = declDp(df, "spikeStock");
        OWLDataProperty doubled = declDp(df, "spikeDoubled");
        declare(m, ont, part, stock, doubled);

        OWLNamedIndividual p1 = df.getOWLNamedIndividual(IRI.create(SP + "p2"));
        m.addAxiom(ont, df.getOWLClassAssertionAxiom(part, p1));
        m.addAxiom(ont, df.getOWLDataPropertyAssertionAxiom(stock, p1, 5));

        SWRLVariable x = df.getSWRLVariable(IRI.create(SP + "x2"));
        SWRLVariable s = df.getSWRLVariable(IRI.create(SP + "s2"));
        SWRLVariable d = df.getSWRLVariable(IRI.create(SP + "d2"));
        SWRLClassAtom bodyClass = df.getSWRLClassAtom(part, x);
        SWRLDataPropertyAtom stockAtom = df.getSWRLDataPropertyAtom(stock, x, s);
        SWRLBuiltInAtom add = df.getSWRLBuiltInAtom(
                SWRLBuiltInsVocabulary.ADD.getIRI(), List.of(d, s, s));
        SWRLDataPropertyAtom headAtom = df.getSWRLDataPropertyAtom(doubled, x, d);
        m.addAxiom(ont, df.getSWRLRule(
                Set.<SWRLAtom>of(bodyClass, stockAtom, add),
                Set.<SWRLAtom>of(headAtom)));

        OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        try {
            r.precomputeInferences();
            Set<org.semanticweb.owlapi.model.OWLLiteral> vals =
                    r.getDataPropertyValues(p1, doubled);
            boolean bound = !vals.isEmpty();
            String got = vals.stream().findFirst().map(Object::toString).orElse("<无>");
            System.out.println("[spike-2] add 绑定结果 → spikeDoubled(p2) = " + got
                    + "（期望 10；绑定成功=" + bound + "）");
            // 能力探针：仅记录，不因 Openllet 不支持算术绑定而判失败。
            org.assertj.core.api.Assertions.assertThat(r.isConsistent())
                    .as("算术规则不应破坏本体一致性").isTrue();
        } finally {
            r.dispose();
        }
    }

    // ============================================================
    // spike-3：规则数量对推理耗时的影响（真实本体）
    // ============================================================

    @Test
    @DisplayName("spike-3 性能：规则数 0/100/300 对 reasoner 创建 + flush 的影响")
    void performanceScaling() throws Exception {
        Path owl = Paths.get(TmsdTestConfig.ontology("main-path"));
        org.assertj.core.api.Assertions.assertThat(owl).exists();

        for (int n : new int[]{0, 100, 300}) {
            OWLOntologyManager m = OWLManager.createOWLOntologyManager();
            OWLOntology ont = m.loadOntologyFromOntologyDocument(owl.toFile());
            OWLDataFactory df = m.getOWLDataFactory();

            OWLClass accessory = df.getOWLClass(IRI.create(NS + "Accessory"));
            // 用不受本体约束的独立数据属性，避免触发既有 allValuesFrom 导致全局不一致
            OWLDataProperty perfVal = df.getOWLDataProperty(IRI.create(SP + "perfVal"));
            m.addAxiom(ont, df.getOWLDeclarationAxiom(perfVal));
            for (int i = 0; i < n; i++) {
                OWLClass headClass = df.getOWLClass(IRI.create(SP + "Violate" + i));
                m.addAxiom(ont, df.getOWLDeclarationAxiom(headClass));
                SWRLVariable a = df.getSWRLVariable(IRI.create(SP + "a" + i));
                SWRLVariable s = df.getSWRLVariable(IRI.create(SP + "s" + i));
                SWRLClassAtom bodyClass = df.getSWRLClassAtom(accessory, a);
                SWRLDataPropertyAtom spAtom = df.getSWRLDataPropertyAtom(perfVal, a, s);
                SWRLBuiltInAtom gt = df.getSWRLBuiltInAtom(
                        SWRLBuiltInsVocabulary.GREATER_THAN.getIRI(),
                        List.of(s, df.getSWRLLiteralArgument(df.getOWLLiteral(i))));
                SWRLClassAtom head = df.getSWRLClassAtom(headClass, a);
                m.addAxiom(ont, df.getSWRLRule(
                        Set.<SWRLAtom>of(bodyClass, spAtom, gt),
                        Set.<SWRLAtom>of(head)));
            }

            // 注入一个 ABox 附件个体，使规则实际激发（否则纯 TBox 下 flush 不做规则物化）
            OWLNamedIndividual acc = df.getOWLNamedIndividual(IRI.create(SP + "acc" + n));
            m.addAxiom(ont, df.getOWLClassAssertionAxiom(accessory, acc));
            m.addAxiom(ont, df.getOWLDataPropertyAssertionAxiom(perfVal, acc, 5000));

            long t0 = System.nanoTime();
            OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
            long t1 = System.nanoTime();
            r.precomputeInferences();
            long t2 = System.nanoTime();
            int fired = r.getTypes(acc, false).getFlattened().size();
            System.out.printf("[spike-3] 规则数=%d  create=%d ms  flush=%d ms  一致=%s  推断类型数=%d%n",
                    n, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, r.isConsistent(), fired);
            r.dispose();
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    private static OWLOntology newEmpty(OWLOntologyManager m) {
        try {
            return m.createOntology(IRI.create(NS + "spike"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static OWLClass declClass(OWLDataFactory df, String local) {
        return df.getOWLClass(IRI.create(SP + local));
    }

    private static OWLDataProperty declDp(OWLDataFactory df, String local) {
        return df.getOWLDataProperty(IRI.create(SP + local));
    }

    private static void declare(OWLOntologyManager m, OWLOntology ont,
                                OWLClass c1, OWLClass c2, OWLDataProperty p1, OWLDataProperty p2) {
        m.addAxiom(ont, m.getOWLDataFactory().getOWLDeclarationAxiom(c1));
        m.addAxiom(ont, m.getOWLDataFactory().getOWLDeclarationAxiom(c2));
        m.addAxiom(ont, m.getOWLDataFactory().getOWLDeclarationAxiom(p1));
        m.addAxiom(ont, m.getOWLDataFactory().getOWLDeclarationAxiom(p2));
    }

    private static void declare(OWLOntologyManager m, OWLOntology ont,
                                OWLClass c1, OWLDataProperty p1, OWLDataProperty p2) {
        m.addAxiom(ont, m.getOWLDataFactory().getOWLDeclarationAxiom(c1));
        m.addAxiom(ont, m.getOWLDataFactory().getOWLDeclarationAxiom(p1));
        m.addAxiom(ont, m.getOWLDataFactory().getOWLDeclarationAxiom(p2));
    }
}
