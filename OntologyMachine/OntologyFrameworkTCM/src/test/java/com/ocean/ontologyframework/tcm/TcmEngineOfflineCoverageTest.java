package com.ocean.ontologyframework.tcm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核心引擎离线单元测试（无需 Ontop/MySQL/Camunda/Worker）：
 * PulseParser（纯字符串）、DefinitionGapUtils（OWLAPI 内存本体）、JingFangInference（纯评分）。
 */
@DisplayName("核心引擎离线单元覆盖（PulseParser / DefinitionGapUtils / JingFangInference）")
class TcmEngineOfflineCoverageTest {

    @Test
    @DisplayName("PulseParser：null/空白/仅脉/复合/非汉字/分隔符")
    void pulseParser() {
        assertTrue(PulseParser.splitPulseDescriptions(null).isEmpty());
        assertTrue(PulseParser.splitPulseDescriptions("   ").isEmpty());
        assertTrue(PulseParser.splitPulseDescriptions("脉").isEmpty());
        assertEquals(List.of("浮脉", "缓脉"), PulseParser.splitPulseDescriptions("脉浮而缓"));
        assertEquals(List.of("沉脉", "细脉"), PulseParser.splitPulseDescriptions("脉沉细"));
        assertEquals(List.of("浮脉", "紧脉"), PulseParser.splitPulseDescriptions("浮紧"));
        assertEquals(List.of("弦脉", "数脉"), PulseParser.splitPulseDescriptions("脉弦，数"));
        assertEquals(List.of("滑脉", "数脉"), PulseParser.splitPulseDescriptions("滑数"));
        assertEquals(List.of("浮脉"), PulseParser.splitPulseDescriptions("脉 浮 脉 123"));
        assertEquals(List.of("代脉"), PulseParser.splitPulseDescriptions("脉、脉代"));
    }

    private static final String NS = "http://example.org/gaptest#";
    private static final IRI PROP = IRI.create(NS + "you_zhengzhuang");

    private static OWLOntology buildOntology() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create(NS));
        OWLClass A = df.getOWLClass(IRI.create(NS + "A"));
        OWLClass B = df.getOWLClass(IRI.create(NS + "B"));
        OWLClass Z = df.getOWLClass(IRI.create(NS + "Z"));
        OWLClass U = df.getOWLClass(IRI.create(NS + "U"));
        OWLClass Cy = df.getOWLClass(IRI.create(NS + "Cy"));
        OWLObjectProperty p = df.getOWLObjectProperty(PROP);
        OWLClassExpression someB = df.getOWLObjectSomeValuesFrom(p, B);
        m.addAxiom(o, df.getOWLEquivalentClassesAxiom(Z, df.getOWLObjectIntersectionOf(A, someB)));
        m.addAxiom(o, df.getOWLEquivalentClassesAxiom(U, df.getOWLObjectUnionOf(A, B)));
        m.addAxiom(o, df.getOWLEquivalentClassesAxiom(Cy,
                df.getOWLObjectIntersectionOf(Cy, df.getOWLObjectSomeValuesFrom(p, A))));
        OWLClass Nested = df.getOWLClass(IRI.create(NS + "Nested"));
        m.addAxiom(o, df.getOWLEquivalentClassesAxiom(Nested,
                df.getOWLObjectSomeValuesFrom(p, df.getOWLObjectUnionOf(A, B))));
        OWLObjectProperty q = df.getOWLObjectProperty(IRI.create(NS + "otherProp"));
        OWLClass Other = df.getOWLClass(IRI.create(NS + "Other"));
        m.addAxiom(o, df.getOWLEquivalentClassesAxiom(Other, df.getOWLObjectSomeValuesFrom(q, A)));
        OWLAnnotationProperty ps = df.getOWLAnnotationProperty(IRI.create(NS + "possibleSymptom"));
        m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(ps, Z.getIRI(), A.getIRI()));
        return o;
    }

    @Test
    @DisplayName("DefinitionGapUtils：AND/OR/someValuesFrom/类/环/属性过滤/叶子/或然症 全分支")
    void definitionGap() throws Exception {
        OWLOntology o = buildOntology();
        OWLDataFactory df = o.getOWLOntologyManager().getOWLDataFactory();
        Set<IRI> props = Set.of(PROP);
        OWLClass A = df.getOWLClass(IRI.create(NS + "A"));
        OWLClass B = df.getOWLClass(IRI.create(NS + "B"));
        OWLClass Z = df.getOWLClass(IRI.create(NS + "Z"));
        OWLClass U = df.getOWLClass(IRI.create(NS + "U"));
        OWLClass Cy = df.getOWLClass(IRI.create(NS + "Cy"));
        OWLClass Nested = df.getOWLClass(IRI.create(NS + "Nested"));
        OWLClass Other = df.getOWLClass(IRI.create(NS + "Other"));
        OWLClass thing = df.getOWLThing();

        assertEquals(2, DefinitionGapUtils.definitionGap(o, Z, Set.of(), props));
        assertEquals(1, DefinitionGapUtils.definitionGap(o, Z, Set.of("B"), props));
        assertEquals(0, DefinitionGapUtils.definitionGap(o, Z, Set.of("A", "B"), props));
        assertEquals(1, DefinitionGapUtils.definitionGap(o, U, Set.of(), props));
        assertEquals(0, DefinitionGapUtils.definitionGap(o, U, Set.of("B"), props));
        assertEquals(1, DefinitionGapUtils.definitionGap(o, Cy, Set.of(), props));
        assertEquals(1, DefinitionGapUtils.definitionGap(o, Nested, Set.of(), props));
        assertEquals(0, DefinitionGapUtils.definitionGap(o, Nested, Set.of("A"), props));
        assertEquals(0, DefinitionGapUtils.definitionGap(o, Other, Set.of(), props));
        assertEquals(0, DefinitionGapUtils.definitionGap(o, thing, Set.of(), props));
        // 顶层无 equivalentClass 定义的类：definitionGap 不累加 → 0
        assertEquals(0, DefinitionGapUtils.definitionGap(o, A, Set.of(), props));

        List<String> leaves = DefinitionGapUtils.definitionGapLeaves(o, Z, Set.of(), props)
                .stream().map(c -> c.getIRI().getFragment()).collect(Collectors.toList());
        assertTrue(leaves.contains("A") && leaves.contains("B"));
        assertEquals(1, DefinitionGapUtils.definitionGapLeaves(o, U, Set.of(), props).size());

        OWLAnnotationProperty ps = df.getOWLAnnotationProperty(IRI.create(NS + "possibleSymptom"));
        Set<OWLClass> poss = DefinitionGapUtils.collectPossibleSymptoms(o, Z, ps.getIRI());
        assertTrue(poss.stream().anyMatch(c -> c.getIRI().getFragment().equals("A")));
    }

    @Test
    @DisplayName("JingFangInference：加载权重矩阵 + 推理排序 + topN + 空输入")
    void jingFangInference() throws Exception {
        JingFangInference engine = new JingFangInference();
        engine.loadWeightMatrixFromResource();
        assertTrue(engine.infer(Collections.emptyList(), 5).isEmpty());
        List<String> feats = Arrays.asList("zz:EHan", "zz:FaRe", "zz:HanChu", "zz:EFeng",
                "zz:TouXiangQiangTong", "mx:Pulse_Huan");
        List<JingFangInference.Result> r = engine.infer(feats, 3);
        assertFalse(r.isEmpty());
        assertTrue(r.size() <= 3);
        for (int i = 1; i < r.size(); i++) {
            assertTrue(r.get(i - 1).matchRate >= r.get(i).matchRate);
        }
        assertNotNull(r.get(0).toString());
    }
}
