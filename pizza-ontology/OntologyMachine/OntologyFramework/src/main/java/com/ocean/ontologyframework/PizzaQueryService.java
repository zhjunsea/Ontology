package com.ocean.ontologyframework;

import com.ocean.ontologyframework.KnowledgeService;
import com.ocean.openlletresolver.OwlOntologyBuilder;
import com.ocean.openlletresolver.ReasonerService;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.semanticweb.owlapi.model.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.ocean.openlletresolver.OwlReasoningService.getInferredTypes;
import static com.ocean.openlletresolver.OwlReasoningService.withReasoner;

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
        OWLDataFactory df = merged.getOWLOntologyManager().getOWLDataFactory();

        String ns = "http://example.org/pizza/components/classes/";
        IRI componentIri = IRI.create(ns + "PizzaComponent");
        OWLClass pizzaComponentCls = df.getOWLClass(componentIri);

        // ⭐ 新增：定义需要查询的数据属性
        OWLDataProperty supplierProp = df.getOWLDataProperty(IRI.create(ns + "supplier"));
        OWLDataProperty priceProp = df.getOWLDataProperty(IRI.create(ns + "price"));

        List<String> results = withReasoner(merged, reasoner -> {
            List<String> lines = new ArrayList<>();
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

        int count = 0;
        for (String line : results) {
            if (count++ >= 15) break;
            System.out.println("   🍕 " + line);
        }
        if (count == 0) {
            System.out.println("   ⚠️ 未找到 PizzaComponent 实例或相关数据属性");
        }
        System.out.println();
    }

    // 结果封装类
    public record CrustSupplier(String instanceUri, String supplier) {}
}