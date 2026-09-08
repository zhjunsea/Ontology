package com.ocean.ontologyframework;

import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.vocab.XSDVocabulary;

import java.util.Set;
import java.util.stream.Collectors;

public class ConsistencyTest {
    public static void main(String[] args) throws Exception {
        // 1. 创建独立的 manager
        OWLOntologyManager testManager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = testManager.getOWLDataFactory();

        // 2. 创建空本体
        OWLOntology testOnt = testManager.createOntology();

        // 3. IRI 定义
        IRI indIRI = IRI.create("http://example.org/pizza/components/individuals/testInd");
        IRI pizzaComponentIRI = IRI.create("http://example.org/pizza/components/classes/PizzaComponent");
        IRI crustIRI = IRI.create("http://example.org/pizza/components/classes/Crust");
        IRI neapolitanCrustIRI = IRI.create("http://example.org/pizza/components/classes/NeapolitanCrust");
        IRI priceIRI = IRI.create("http://example.org/pizza/components/classes/price");

        // 4. 构建个体和属性
        OWLNamedIndividual ind = df.getOWLNamedIndividual(indIRI);
        OWLClass pizzaComponent = df.getOWLClass(pizzaComponentIRI);
        OWLClass crust = df.getOWLClass(crustIRI);
        OWLClass neapolitanCrust = df.getOWLClass(neapolitanCrustIRI);
        OWLDataProperty price = df.getOWLDataProperty(priceIRI);

        // 5. 添加公理
        testManager.addAxiom(testOnt, df.getOWLClassAssertionAxiom(pizzaComponent, ind));
        testManager.addAxiom(testOnt, df.getOWLClassAssertionAxiom(crust, ind));
        testManager.addAxiom(testOnt, df.getOWLClassAssertionAxiom(neapolitanCrust, ind));
        // 正确构建 xsd:decimal 字面量
        OWLDatatype decimalType = df.getOWLDatatype(XSDVocabulary.DECIMAL.getIRI());
        OWLLiteral priceLiteral = df.getOWLLiteral("9.99", decimalType);
        testManager.addAxiom(testOnt, df.getOWLDataPropertyAssertionAxiom(price, ind, priceLiteral));

        // 6. 推理并检查一致性
        OWLReasoner testReasoner = OpenlletReasonerFactory.getInstance().createReasoner(testOnt);
        testReasoner.flush();
        boolean consistent = testReasoner.isConsistent();
        System.out.println("独立测试一致性: " + consistent);

        // 7. 查看实例类型
        if (consistent) {
            String types = testReasoner.getTypes(ind, false)
                    .entities()
                    .map(OWLClass::toStringID)
                    .collect(Collectors.joining(", "));
            System.out.println("实例类型: " + types);
        }

        // 获取本体中所有的逻辑公理（ClassAssertion, DataPropertyAssertion 等）
        Set<OWLLogicalAxiom> logicalAxioms = testOnt.getLogicalAxioms();
        System.out.println("=== 所有逻辑公理 (" + logicalAxioms.size() + "条) ===");
        logicalAxioms.forEach(System.out::println);

        testReasoner.dispose();
    }
}