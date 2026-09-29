package com.ocean.openlletresolver;

import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.SWRLBuiltInsVocabulary;

import java.util.Set;

public class OpenlletExample {

    public static void main(String[] args) throws Exception {
        // ---------- 1. 初始化 OWL API 核心对象 ----------
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        String base = "http://www.example.org/pizza-ontology#";
        // 使用单参构造器，避免 DefaultPrefixManager("", base) 问题
        PrefixManager pm = new DefaultPrefixManager(base);

        // 使用完全限定名创建 IRI，防止与 java.net.IRI 冲突
        org.semanticweb.owlapi.model.IRI ontologyIRI =
                org.semanticweb.owlapi.model.IRI.create("http://www.example.org/pizza-ontology");
        OWLOntology ontology = manager.createOntology(ontologyIRI);

        // ---------- 2. 定义类和属性 ----------
        OWLClass pizzaComponent      = df.getOWLClass(":PizzaComponent", pm);
        OWLClass lowStockComponent   = df.getOWLClass(":LowStockComponent", pm);
        OWLDataProperty currentStock  = df.getOWLDataProperty(":currentStock", pm);
        OWLDataProperty reorderThreshold = df.getOWLDataProperty(":reorderThreshold", pm);

        // 声明这些实体（可选但推荐）
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(pizzaComponent));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(lowStockComponent));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(currentStock));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(reorderThreshold));

        // ---------- 3. 创建个体并赋予库存数据 ----------
        OWLNamedIndividual mozzarella = df.getOWLNamedIndividual(":mozzarella", pm);
        OWLNamedIndividual thinCrust  = df.getOWLNamedIndividual(":thinCrust", pm);

        // mozzarella: 类型 PizzaComponent，库存5，阈值10 → 应被推为 LowStockComponent
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(pizzaComponent, mozzarella));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(currentStock, mozzarella, 5));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(reorderThreshold, mozzarella, 10));

        // thinCrust: 类型 PizzaComponent，库存30，阈值15 → 不应被推为 LowStockComponent
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(pizzaComponent, thinCrust));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(currentStock, thinCrust, 30));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(reorderThreshold, thinCrust, 15));

        // ---------- 4. 构建 DL‑safe SWRL 规则 ----------
        // 变量
        SWRLVariable varC = df.getSWRLVariable(
                org.semanticweb.owlapi.model.IRI.create(base + "c"));
        SWRLVariable varS = df.getSWRLVariable(
                org.semanticweb.owlapi.model.IRI.create(base + "s"));
        SWRLVariable varT = df.getSWRLVariable(
                org.semanticweb.owlapi.model.IRI.create(base + "t"));

        // 前提原子：currentStock(?c, ?s) 和 reorderThreshold(?c, ?t)
        SWRLDataPropertyAtom atomStock  = df.getSWRLDataPropertyAtom(currentStock, varC, varS);
        SWRLDataPropertyAtom atomThresh = df.getSWRLDataPropertyAtom(reorderThreshold, varC, varT);

        // 内置原子：swrlb:lessThan(?s, ?t)
        org.semanticweb.owlapi.model.IRI lessThanIRI = SWRLBuiltInsVocabulary.LESS_THAN.getIRI();
        SWRLBuiltInAtom lessThanAtom = df.getSWRLBuiltInAtom(lessThanIRI,
                java.util.List.of(varS, varT));

        // 结论原子：LowStockComponent(?c)
        SWRLClassAtom headAtom = df.getSWRLClassAtom(lowStockComponent, varC);

        // 组装规则
        Set<SWRLAtom> body = Set.of(atomStock, atomThresh, lessThanAtom);
        Set<SWRLAtom> head = Set.of(headAtom);
        SWRLRule rule = df.getSWRLRule(body, head);
        manager.addAxiom(ontology, rule);

        // ---------- 5. 使用 Openllet 执行推理 ----------
        OWLReasonerFactory reasonerFactory = new OpenlletReasonerFactory();
        OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
        // precomputeInferences 会执行分类并触发 SWRL 规则
        reasoner.precomputeInferences();

        // ---------- 6. 查询结果 ----------
        System.out.println("=== 低库存组件 (LowStockComponent) ===");
        reasoner.getInstances(lowStockComponent, false).getFlattened()
                .forEach(ind -> System.out.println("  " + ind.getIRI().getShortForm()));

        // 单独检查 thinCrust
        boolean thinIsLow = reasoner.getTypes(thinCrust, false)
                .containsEntity(lowStockComponent);
        System.out.println("thinCrust 是低库存吗? " + thinIsLow);

        // 输出当前库存值（可选）
        System.out.println("\n当前库存:");
        reasoner.getInstances(pizzaComponent, false).getFlattened().forEach(ind -> {
            int stock = reasoner.getDataPropertyValues(ind, currentStock)
                    .stream().findFirst().map(lit -> lit.parseInteger()).orElse(-1);
            System.out.println("  " + ind.getIRI().getShortForm() + " : " + stock);
        });

        // 7. 释放资源
        reasoner.dispose();
    }
}