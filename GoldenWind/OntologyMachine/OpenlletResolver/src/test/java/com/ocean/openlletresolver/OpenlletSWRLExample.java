package com.ocean.openlletresolver;

import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.SWRLBuiltInsVocabulary;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenlletSWRLExample {

    public static void main(String[] args) throws Exception {
        // --- 1. 初始化 ---
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        String base = "http://www.example.org/pizza-ontology#";
        PrefixManager pm = new DefaultPrefixManager(base);

        IRI ontologyIRI = IRI.create("http://www.example.org/pizza-ontology");
        OWLOntology ontology = manager.createOntology(ontologyIRI);

        // --- 2. 声明类和属性 ---
        OWLClass pizzaComponent      = df.getOWLClass(":PizzaComponent", pm);
        OWLClass lowStockComponent   = df.getOWLClass(":LowStockComponent", pm);
        OWLClass abnormalComponent   = df.getOWLClass(":AbnormalComponent", pm);   // 新增
        OWLDataProperty currentStock  = df.getOWLDataProperty(":currentStock", pm);
        OWLDataProperty reorderThreshold = df.getOWLDataProperty(":reorderThreshold", pm);

        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(pizzaComponent));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(lowStockComponent));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(abnormalComponent));  // 新增
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(currentStock));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(reorderThreshold));

        // --- 3. 创建个体（披萨原料） ---
        OWLNamedIndividual mozzarella = df.getOWLNamedIndividual(":mozzarella", pm);
        OWLNamedIndividual thinCrust  = df.getOWLNamedIndividual(":thinCrust", pm);
        OWLNamedIndividual expiredCheese = df.getOWLNamedIndividual(":expiredCheese", pm); // 新增

        // mozzarella: 库存5，阈值10 → 应归类为 LowStockComponent
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(pizzaComponent, mozzarella));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(currentStock, mozzarella, 5));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(reorderThreshold, mozzarella, 10));

        // thinCrust: 库存30，阈值15 → 不应归类
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(pizzaComponent, thinCrust));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(currentStock, thinCrust, 30));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(reorderThreshold, thinCrust, 15));

        // expiredCheese: 库存-3，无阈值（或任意），应被新规则归类为 AbnormalComponent
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(pizzaComponent, expiredCheese));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(currentStock, expiredCheese, -3));

        // --- 4. 构建 DL-safe SWRL 规则 ---
        // 变量（两个规则可共享变量IRI，但为清晰分别创建）
        SWRLVariable varC  = df.getSWRLVariable(IRI.create(base + "c"));
        SWRLVariable varS  = df.getSWRLVariable(IRI.create(base + "s"));
        SWRLVariable varT  = df.getSWRLVariable(IRI.create(base + "t"));

        // 规则1：库存低于阈值 → LowStockComponent
        SWRLDataPropertyAtom atomStock  = df.getSWRLDataPropertyAtom(currentStock, varC, varS);
        SWRLDataPropertyAtom atomThresh = df.getSWRLDataPropertyAtom(reorderThreshold, varC, varT);
        IRI lessThanIRI = SWRLBuiltInsVocabulary.LESS_THAN.getIRI();
        SWRLBuiltInAtom lessThanAtom = df.getSWRLBuiltInAtom(lessThanIRI, List.of(varS, varT));
        SWRLClassAtom headAtom = df.getSWRLClassAtom(lowStockComponent, varC);

        Set<SWRLAtom> body = Set.of(atomStock, atomThresh, lessThanAtom);
        Set<SWRLAtom> head = Set.of(headAtom);
        SWRLRule rule1 = df.getSWRLRule(body, head);
        manager.addAxiom(ontology, rule1);

        // 规则2：库存 < 0 → AbnormalComponent（新增）
        SWRLVariable varC2 = df.getSWRLVariable(IRI.create(base + "c2"));
        SWRLVariable varS2 = df.getSWRLVariable(IRI.create(base + "s2"));
        SWRLDataPropertyAtom atomStock2 = df.getSWRLDataPropertyAtom(currentStock, varC2, varS2);
        // 常量 0
        OWLLiteral zeroLiteral = df.getOWLLiteral(0);
        SWRLLiteralArgument zeroArg = df.getSWRLLiteralArgument(zeroLiteral);
        SWRLBuiltInAtom lessThanZeroAtom = df.getSWRLBuiltInAtom(lessThanIRI, List.of(varS2, zeroArg));
        SWRLClassAtom abnormalHeadAtom = df.getSWRLClassAtom(abnormalComponent, varC2);

        Set<SWRLAtom> body2 = Set.of(atomStock2, lessThanZeroAtom);
        Set<SWRLAtom> head2 = Set.of(abnormalHeadAtom);
        SWRLRule rule2 = df.getSWRLRule(body2, head2);
        manager.addAxiom(ontology, rule2);

        System.out.println("----- 检查各本体中的 SWRL 规则数量 -----");
        for (OWLOntology ont : manager.ontologies().collect(Collectors.toList())) {
            long count = ont.axioms(AxiomType.SWRL_RULE).count();
            System.out.println("本体 " + ont.getOntologyID() + " -> SWRL规则: " + count);
        }
        ontology.axioms().forEach(ax -> {
            if (ax.toString().contains("swrl")) {
                System.out.println(ax);
            }
        });

        // --- 5. Openllet 推理 ---
        OWLReasonerFactory reasonerFactory = new OpenlletReasonerFactory();
        OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
        reasoner.precomputeInferences();   // 执行分类 + SWRL 规则

        // --- 6. 查询结果 ---
        System.out.println("=== 低库存组件 (LowStockComponent) ===");
        reasoner.getInstances(lowStockComponent, false).getFlattened().forEach(ind -> {
            System.out.println("  " + ind.getIRI().getShortForm());
            reasoner.getDataPropertyValues(ind, currentStock)
                    .stream().findFirst()
                    .ifPresent(val -> System.out.println("    当前库存: " + val.parseInteger()));
        });

        System.out.println("\n=== 异常库存组件 (AbnormalComponent) ===");
        reasoner.getInstances(abnormalComponent, false).getFlattened().forEach(ind -> {
            System.out.println("  " + ind.getIRI().getShortForm());
            reasoner.getDataPropertyValues(ind, currentStock)
                    .stream().findFirst()
                    .ifPresent(val -> System.out.println("    当前库存: " + val.parseInteger()));
        });

        boolean thinIsLow = reasoner.getTypes(thinCrust, false).containsEntity(lowStockComponent);
        System.out.println("\nthinCrust 是低库存吗? " + thinIsLow);

        manager.saveOntology(ontology, new RDFXMLDocumentFormat(),
                IRI.create(new File("output_fixed.owl").toURI()));

        reasoner.dispose();
    }
}