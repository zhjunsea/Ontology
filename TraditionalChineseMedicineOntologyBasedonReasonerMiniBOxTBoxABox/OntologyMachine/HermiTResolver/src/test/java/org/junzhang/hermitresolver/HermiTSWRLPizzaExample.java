package org.junzhang.hermitresolver;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.SWRLBuiltInsVocabulary;
import org.semanticweb.owlapi.model.IRI;

import java.util.List;
import java.util.Set;

public class HermiTSWRLPizzaExample {

    public static void main(String[] args) throws Exception {
        // --- 1. 初始化 ---
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        String base = "http://www.example.org/pizza-ontology#";
        PrefixManager pm = new DefaultPrefixManager(base);  // 单参构造器

        // 创建本体 IRI，注意使用 org.semanticweb.owlapi.model.IRI
        IRI ontologyIRI = IRI.create("http://www.example.org/pizza-ontology");
        OWLOntology ontology = manager.createOntology(ontologyIRI);

        // --- 2. 声明类和属性 ---
        OWLClass pizzaComponent      = df.getOWLClass(":PizzaComponent", pm);
        OWLClass lowStockComponent   = df.getOWLClass(":LowStockComponent", pm);
        OWLDataProperty currentStock  = df.getOWLDataProperty(":currentStock", pm);
        OWLDataProperty reorderThreshold = df.getOWLDataProperty(":reorderThreshold", pm);

        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(pizzaComponent));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(lowStockComponent));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(currentStock));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(reorderThreshold));

        // --- 3. 创建个体（披萨原料） ---
        OWLNamedIndividual mozzarella = df.getOWLNamedIndividual(":mozzarella", pm);
        OWLNamedIndividual thinCrust  = df.getOWLNamedIndividual(":thinCrust", pm);

        // mozzarella: 库存5，阈值10 → 应归类为 LowStockComponent
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(pizzaComponent, mozzarella));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(currentStock, mozzarella, 5));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(reorderThreshold, mozzarella, 10));

        // thinCrust: 库存30，阈值15 → 不应归类
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(pizzaComponent, thinCrust));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(currentStock, thinCrust, 30));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(reorderThreshold, thinCrust, 15));

        // --- 4. 构建 DL-safe SWRL 规则 ---
        // 变量
        SWRLVariable varC = df.getSWRLVariable(IRI.create(base + "c"));
        SWRLVariable varS = df.getSWRLVariable(IRI.create(base + "s"));
        SWRLVariable varT = df.getSWRLVariable(IRI.create(base + "t"));

        // 前提原子
        SWRLDataPropertyAtom atomStock  = df.getSWRLDataPropertyAtom(currentStock, varC, varS);
        SWRLDataPropertyAtom atomThresh = df.getSWRLDataPropertyAtom(reorderThreshold, varC, varT);

        // 内置原子 swrlb:lessThan(?s, ?t)
        IRI lessThanIRI = SWRLBuiltInsVocabulary.LESS_THAN.getIRI();
        SWRLBuiltInAtom lessThanAtom = df.getSWRLBuiltInAtom(lessThanIRI, List.of(varS, varT));

        // 结论原子 LowStockComponent(?c)
        SWRLClassAtom headAtom = df.getSWRLClassAtom(lowStockComponent, varC);

        // 组装规则
        Set<SWRLAtom> body = Set.of(atomStock, atomThresh, lessThanAtom);
        Set<SWRLAtom> head = Set.of(headAtom);
        SWRLRule rule = df.getSWRLRule(body, head);
        manager.addAxiom(ontology, rule);

        // --- 5. HermiT 推理 ---
        ReasonerFactory reasonerFactory = new ReasonerFactory();
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

        boolean thinIsLow = reasoner.getTypes(thinCrust, false).containsEntity(lowStockComponent);
        System.out.println("thinCrust 是低库存吗? " + thinIsLow);

        reasoner.dispose();
    }
}