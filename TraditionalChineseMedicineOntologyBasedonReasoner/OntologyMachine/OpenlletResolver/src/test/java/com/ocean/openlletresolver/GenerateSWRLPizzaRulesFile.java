package com.ocean.openlletresolver;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.vocab.SWRLBuiltInsVocabulary;  // 注意这里是 vocab

import java.io.File;
import java.util.List;
import java.util.Set;

public class GenerateSWRLPizzaRulesFile {

    public static void main(String[] args) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        // 规则本体的 IRI
        IRI rulesOntologyIRI = IRI.create("http://example.org/pizza/components/rulesTest");
        OWLOntology ontology = manager.createOntology(rulesOntologyIRI);

        // 添加中文注释
        OWLAnnotationProperty label = df.getRDFSLabel();
        OWLAnnotationProperty comment = df.getRDFSComment();
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(rulesOntologyIRI,
                df.getOWLAnnotation(label, df.getOWLLiteral("披萨库存预警 SWRL 规则", "zh"))));
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(rulesOntologyIRI,
                df.getOWLAnnotation(comment, df.getOWLLiteral("显式rdf:List格式，包含针对NeapolitanCrust的额外规则", "zh"))));

        // 定义命名空间 (用于变量 IRI 的简化)
        String base = rulesOntologyIRI.toString() + "#";

        // ----- 变量声明 (OWLAPI 会在序列化时自动处理变量声明) -----
        SWRLVariable varCrust = df.getSWRLVariable(IRI.create(base + "crust"));
        SWRLVariable varQtyC = df.getSWRLVariable(IRI.create(base + "qty_c"));
        SWRLVariable varNeapolitan = df.getSWRLVariable(IRI.create(base + "neapolitan"));
        SWRLVariable varQtyN = df.getSWRLVariable(IRI.create(base + "qty_n"));
        SWRLVariable varSauce = df.getSWRLVariable(IRI.create(base + "sauce"));
        SWRLVariable varQtyS = df.getSWRLVariable(IRI.create(base + "qty_s"));
        SWRLVariable varCheese = df.getSWRLVariable(IRI.create(base + "cheese"));
        SWRLVariable varQtyCh = df.getSWRLVariable(IRI.create(base + "qty_ch"));
        SWRLVariable varTopping = df.getSWRLVariable(IRI.create(base + "topping"));
        SWRLVariable varQtyT = df.getSWRLVariable(IRI.create(base + "qty_t"));

        // ----- 引用其他本体的类和属性 IRI -----
        OWLClass crustClass = df.getOWLClass(IRI.create("http://example.org/pizza/components/classes/Crust"));
        OWLClass neapolitanCrustClass = df.getOWLClass(IRI.create("http://example.org/pizza/components/classes/NeapolitanCrust"));
        OWLClass sauceClass = df.getOWLClass(IRI.create("http://example.org/pizza/components/classes/Sauce"));
        OWLClass cheeseClass = df.getOWLClass(IRI.create("http://example.org/pizza/components/classes/Cheese"));
        OWLClass toppingClass = df.getOWLClass(IRI.create("http://example.org/pizza/components/classes/Topping"));
        OWLClass lowStockCrust = df.getOWLClass(IRI.create("http://example.org/pizza/classes/LowStockCrust"));
        OWLClass lowStockSauce = df.getOWLClass(IRI.create("http://example.org/pizza/classes/LowStockSauce"));
        OWLClass lowStockCheese = df.getOWLClass(IRI.create("http://example.org/pizza/classes/LowStockCheese"));
        OWLClass lowStockTopping = df.getOWLClass(IRI.create("http://example.org/pizza/classes/LowStockTopping"));
        OWLDataProperty stockQty = df.getOWLDataProperty(IRI.create("http://example.org/pizza/components/classes/stockQuantity"));

        // ========== 规则1：饼底（Crust）库存 <20 → LowStockCrust ==========
        SWRLClassAtom crustAtom = df.getSWRLClassAtom(crustClass, varCrust);
        SWRLDataPropertyAtom crustStockAtom = df.getSWRLDataPropertyAtom(stockQty, varCrust, varQtyC);
        SWRLBuiltInAtom lessThan20 = makeLessThan(df, varQtyC, 20);
        SWRLClassAtom headCrust = df.getSWRLClassAtom(lowStockCrust, varCrust);

        SWRLRule rule1 = df.getSWRLRule(
                Set.of(crustAtom, crustStockAtom, lessThan20),
                Set.of(headCrust));
        manager.addAxiom(ontology, rule1);

        // ========== 规则1b：NeapolitanCrust 库存 <20 → LowStockCrust ==========
        SWRLClassAtom neapolitanAtom = df.getSWRLClassAtom(neapolitanCrustClass, varNeapolitan);
        SWRLDataPropertyAtom neapolitanStockAtom = df.getSWRLDataPropertyAtom(stockQty, varNeapolitan, varQtyN);
        SWRLBuiltInAtom lessThan20b = makeLessThan(df, varQtyN, 20);
        SWRLClassAtom headNeapolitan = df.getSWRLClassAtom(lowStockCrust, varNeapolitan);

        SWRLRule rule1b = df.getSWRLRule(
                Set.of(neapolitanAtom, neapolitanStockAtom, lessThan20b),
                Set.of(headNeapolitan));
        manager.addAxiom(ontology, rule1b);

        // ========== 规则2：酱汁库存 <15 → LowStockSauce ==========
        SWRLClassAtom sauceAtom = df.getSWRLClassAtom(sauceClass, varSauce);
        SWRLDataPropertyAtom sauceStockAtom = df.getSWRLDataPropertyAtom(stockQty, varSauce, varQtyS);
        SWRLBuiltInAtom lessThan15 = makeLessThan(df, varQtyS, 15);
        SWRLClassAtom headSauce = df.getSWRLClassAtom(lowStockSauce, varSauce);

        SWRLRule rule2 = df.getSWRLRule(
                Set.of(sauceAtom, sauceStockAtom, lessThan15),
                Set.of(headSauce));
        manager.addAxiom(ontology, rule2);

        // ========== 规则3：奶酪库存 <10 → LowStockCheese ==========
        SWRLClassAtom cheeseAtom = df.getSWRLClassAtom(cheeseClass, varCheese);
        SWRLDataPropertyAtom cheeseStockAtom = df.getSWRLDataPropertyAtom(stockQty, varCheese, varQtyCh);
        SWRLBuiltInAtom lessThan10 = makeLessThan(df, varQtyCh, 10);
        SWRLClassAtom headCheese = df.getSWRLClassAtom(lowStockCheese, varCheese);

        SWRLRule rule3 = df.getSWRLRule(
                Set.of(cheeseAtom, cheeseStockAtom, lessThan10),
                Set.of(headCheese));
        manager.addAxiom(ontology, rule3);

        // ========== 规则4：配料库存 <10 → LowStockTopping ==========
        SWRLClassAtom toppingAtom = df.getSWRLClassAtom(toppingClass, varTopping);
        SWRLDataPropertyAtom toppingStockAtom = df.getSWRLDataPropertyAtom(stockQty, varTopping, varQtyT);
        SWRLBuiltInAtom lessThan10b = makeLessThan(df, varQtyT, 10);
        SWRLClassAtom headTopping = df.getSWRLClassAtom(lowStockTopping, varTopping);

        SWRLRule rule4 = df.getSWRLRule(
                Set.of(toppingAtom, toppingStockAtom, lessThan10b),
                Set.of(headTopping));
        manager.addAxiom(ontology, rule4);

        // ----- 保存为 RDF/XML 文件 -----
        File outputFile = new File("pizza-rules.owl");
        ontology.saveOntology(new RDFXMLDocumentFormat(), IRI.create(outputFile.toURI()));
        System.out.println("SWRL 规则文件已生成: " + outputFile.getAbsolutePath());

        // 4. 使用 Openllet 推理
        openllet.owlapi.OpenlletReasonerFactory reasonerFactory = new openllet.owlapi.OpenlletReasonerFactory();
        OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
        reasoner.precomputeInferences();
        System.out.println("推理完成，本体一致：" + reasoner.isConsistent());

        // 5. 查询低库存类
        OWLDataFactory df2 = manager.getOWLDataFactory();
        IRI lowStockCrustIRI = IRI.create("http://example.org/pizza/classes/LowStockCrust");
        IRI lowStockSauceIRI = IRI.create("http://example.org/pizza/classes/LowStockSauce");
        IRI lowStockCheeseIRI = IRI.create("http://example.org/pizza/classes/LowStockCheese");
        IRI lowStockToppingIRI = IRI.create("http://example.org/pizza/classes/LowStockTopping");
        IRI stockQtyIRI = IRI.create("http://example.org/pizza/components/classes/stockQuantity");
        OWLDataProperty stockProperty = df2.getOWLDataProperty(stockQtyIRI);

        printLowStock(reasoner, df2.getOWLClass(lowStockCrustIRI), stockProperty, "饼底");
        printLowStock(reasoner, df2.getOWLClass(lowStockSauceIRI), stockProperty, "酱汁");
        printLowStock(reasoner, df2.getOWLClass(lowStockCheeseIRI), stockProperty, "奶酪");
        printLowStock(reasoner, df2.getOWLClass(lowStockToppingIRI), stockProperty, "配料");

        reasoner.dispose();
    }

    private static void printLowStock(OWLReasoner reasoner, OWLClass cls,
                                      OWLDataProperty stockProp, String label) {
        System.out.println("\n=== " + label + " 低库存实例 ===");
        Set<OWLNamedIndividual> individuals = reasoner.getInstances(cls, false).getFlattened();
        if (individuals.isEmpty()) {
            System.out.println("  无");
        } else {
            individuals.forEach(ind -> {
                System.out.print("  " + ind.getIRI().getShortForm());
                reasoner.getDataPropertyValues(ind, stockProp).forEach(val ->
                        System.out.print(" [库存: " + val.getLiteral() + "]"));
                System.out.println();
            });
        }
    }

    private static SWRLBuiltInAtom makeLessThan(OWLDataFactory df, SWRLVariable varQty, int threshold) {
        IRI lessThanIRI = SWRLBuiltInsVocabulary.LESS_THAN.getIRI();
        OWLLiteral thresholdLiteral = df.getOWLLiteral(threshold);
        SWRLLiteralArgument thresholdArg = df.getSWRLLiteralArgument(thresholdLiteral);
        return df.getSWRLBuiltInAtom(lessThanIRI, List.of(varQty, thresholdArg));
    }
}