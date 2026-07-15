package com.ocean.ontologyframework;

import com.ocean.ontologyframework.KnowledgeService;
import com.ocean.openlletresolver.OwlOntologyBuilder;
import com.ocean.openlletresolver.ReasonerService;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.*;
import java.util.stream.Collectors;

import static com.ocean.openlletresolver.OwlReasoningService.getInferredTypes;
import static com.ocean.openlletresolver.OwlReasoningService.withReasoner;
import static com.ocean.openlletresolver.ReasonerService.explainInconsistency;

public class PizzaQueryService {
    KnowledgeService ks;

    public PizzaQueryService(KnowledgeService ks) {
        this.ks = ks;
    }

    /**
     * 查询 :Crust 的所有实例及其供应商
     */
    public List<CrustSupplier> getCrustInstancesAndSuppliers() {
        // Step 1: TBox 推导
        Set<String> crustClassIris = ks.getSubClassIris("http://example.org/pizza/components/classes/Crust");

        // Step 2: 构建查询
        String valuesClause = ks.buildValuesClause("cls", crustClassIris);
        String sparql = """
            PREFIX : <http://example.org/pizza/components/classes/>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT DISTINCT ?instance ?supplier WHERE {
                %s
                ?instance rdf:type ?cls .
                ?instance :supplier ?supplier .
            }
            """.formatted(valuesClause);

        // Step 3: 执行并映射
        return ks.executeAndMap(sparql, row -> new CrustSupplier(
                row.get("instance"),
                row.get("supplier")
        ));
    }

    public static void queryPizzaComponentTypes(OWLOntology tbox, OWLOntology abox)
            throws OWLOntologyCreationException {
        System.out.println("[Query] PizzaComponent 推断类型 + 供应商/价格查询");

        OWLOntology merged = OwlOntologyBuilder.mergeInMemory(tbox, abox);

        //测试，打印三元组
        //KnowledgeService.printAboxOntology(merged);
        //KnowledgeService.printOntologyClasses(merged);
        //KnowledgeService.printOntologyIndividuals(merged);


        OWLDataFactory df = merged.getOWLOntologyManager().getOWLDataFactory();

        String ns = "http://example.org/pizza/components/classes/";
        IRI componentIri = IRI.create(ns + "PizzaComponent");
        OWLClass pizzaComponentCls = df.getOWLClass(componentIri);

        // ⭐ 新增：定义需要查询的数据属性
        OWLDataProperty supplierProp = df.getOWLDataProperty(IRI.create(ns + "supplier"));
        OWLDataProperty priceProp = df.getOWLDataProperty(IRI.create(ns + "price"));

        List<String> results = withReasoner(merged, reasoner -> {
            List<String> lines = new ArrayList<>();
            //测试，打印推理机的某个类的实例
            //diagnoseClassHierarchy(reasoner,pizzaComponentCls,merged);
            // 获取所有 PizzaComponent 实例（含推理推断出的）
            Set<OWLNamedIndividual> individuals = reasoner.getInstances(pizzaComponentCls, false).getFlattened();

            for (OWLNamedIndividual ind : individuals) {
                String name = ind.getIRI().getFragment();

                // 获取推断类型
                String inferredType = reasoner.getTypes(ind, true).getFlattened().stream()
                        .filter(c -> !c.equals(pizzaComponentCls))
                        .map(c -> c.getIRI().getFragment())
                        .findFirst().orElse("-");

                // ⭐ 获取供应商和价格的数据属性值
                String supplier = reasoner.getDataPropertyValues(ind, supplierProp).stream()
                        .map(OWLLiteral::getLiteral)
                        .findFirst().orElse("-");

                String price = reasoner.getDataPropertyValues(ind, priceProp).stream()
                        .map(OWLLiteral::getLiteral)
                        .findFirst().orElse("-");

                lines.add(String.format("%-25s ⇒ %-20s | 供应商: %-15s | 价格: %s",
                        name, inferredType, supplier, price));
            }
            return lines;
        });

        int count = 0;  //为了避免出现大量的个体打印，限制总数
        for (String line : results) {
            if (count++ >= 200) break;
            System.out.println("   🍕 " + line);
        }
        if (count == 0) {
            System.out.println("   ⚠️ 未找到 PizzaComponent 实例或相关数据属性");
        }
        System.out.println();
    }
    /**
     * 诊断指定类在推理机中的子类层次及真实领域个体分布
     * 自动过滤 SKOS 概念、元建模/punning 伪个体、内置命名空间个体
     * 同时打印每个真实个体的 IRI
     */
    public static void diagnoseClassHierarchy(OWLReasoner reasoner, OWLClass targetCls, OWLOntology ontology) {
        List<String> excludePrefixes = List.of(
                "http://www.w3.org/2004/02/skos/core#",
                "http://www.w3.org/2008/05/skos#",
                "http://www.w3.org/2009/08/skos-simple#",
                "http://www.w3.org/2002/07/owl#",
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        );

        System.out.println("\n=== " + targetCls.getIRI().getShortForm() + " 子类及真实个体诊断 ===");
        System.out.println("目标类 IRI: " + targetCls.getIRI());

        // 1. 诊断所有子类
        NodeSet<OWLClass> subClasses = reasoner.getSubClasses(targetCls, false);
        for (OWLClass sub : subClasses.getFlattened()) {
            if (sub.isOWLNothing()) continue;
            printClassDiagnostics(reasoner, sub, ontology, excludePrefixes, "  ");
        }

        // 2. 诊断目标类自身
        System.out.println("---");
        printClassDiagnostics(reasoner, targetCls, ontology, excludePrefixes, "  ");
        System.out.println("===================================\n");
    }

    /**
     * 打印单个类的真实个体统计、IRI 列表及断言类型诊断
     */
    private static void printClassDiagnostics(OWLReasoner reasoner, OWLClass cls,
                                              OWLOntology ontology, List<String> excludePrefixes,
                                              String indent) {
        // 获取并过滤直接实例与全部实例
        Set<OWLNamedIndividual> directReal = filterRealIndividuals(
                reasoner.getInstances(cls, true).getFlattened(), ontology, excludePrefixes);
        Set<OWLNamedIndividual> allReal = filterRealIndividuals(
                reasoner.getInstances(cls, false).getFlattened(), ontology, excludePrefixes);

        // 打印统计摘要
        System.out.printf("%s%-40s | 真实直接实例: %3d | 真实全部实例: %3d%n",
                indent, cls.getIRI().getShortForm(), directReal.size(), allReal.size());

        // ⭐ 打印每个真实个体的 IRI 及断言类型分布
        if (!allReal.isEmpty()) {
            allReal.stream()
                    .sorted(Comparator.comparing(ind -> ind.getIRI().toString()))
                    .forEach(ind -> {
                        // 统计该个体的各类断言数量
                        long dataCount = ontology.getDataPropertyAssertionAxioms(ind).size();
                        long annoCount = ontology.getAnnotationAssertionAxioms(ind.getIRI()).size();
                        long classCount = ontology.getClassAssertionAxioms(ind).size();
                        long objPropCount = ontology.getObjectPropertyAssertionAxioms(ind).size();

                        // 打印个体 IRI 及断言统计
                        System.out.printf("%s    → %-60s | Data:%-3d Anno:%-3d Class:%-3d ObjProp:%-3d%n",
                                indent, ind.getIRI(), dataCount, annoCount, classCount, objPropCount);

                        // ⚠️ 若存在 AnnotationAssertion，采样打印前2条辅助排查降级问题
                        if (annoCount > 0) {
                            ontology.getAnnotationAssertionAxioms(ind.getIRI()).stream()
                                    .limit(2)
                                    .forEach(ax -> System.out.printf("%s        ⚠️ Annotation降级: %s = %s%n",
                                            indent,
                                            ax.getProperty().getIRI().getShortForm(),
                                            ax.getValue()));
                        }
                    });
        }
    }

    /**
     * 过滤出真正的领域个体
     */
    private static Set<OWLNamedIndividual> filterRealIndividuals(
            Set<OWLNamedIndividual> individuals, OWLOntology ontology, List<String> excludePrefixes) {
        return individuals.stream()
                .filter(ind -> {
                    String iri = ind.getIRI().toString();
                    if (excludePrefixes.stream().anyMatch(iri::startsWith)) return false;
                    if (ontology.containsClassInSignature(ind.getIRI())) return false;
                    return true;
                })
                .collect(Collectors.toSet());
    }
    // 结果封装类
    public record CrustSupplier(String instanceUri, String supplier) {}
}