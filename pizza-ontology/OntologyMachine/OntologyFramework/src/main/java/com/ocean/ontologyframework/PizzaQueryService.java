package com.ocean.ontologyframework;

import com.ocean.openlletresolver.BackendService;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class PizzaQueryService {
    BackendService backendService;
    public PizzaQueryService(BackendService backendService) {
        this.backendService = backendService;
    }
    private static final Logger log = LoggerFactory.getLogger(PizzaQueryService.class);

    /**
     * 查询 :Crust 的所有实例及其供应商
     */
    public List<CrustSupplier> getCrustInstancesAndSuppliers() {
        // Step 1: TBox 推导
        Set<String> crustClassIris = backendService.getSubClassIris("http://example.org/pizza/components/classes/Crust");

        // Step 2: 构建查询
        String valuesClause = backendService.getOntologyService().buildValuesClause("cls", crustClassIris);
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
        return backendService.getObdaHandler().executeAndMap(sparql, row -> new CrustSupplier(
                row.get("instance"),
                row.get("supplier")
        ));
    }

    public void queryPizzaComponentTypes(OWLOntology tbox, OWLOntology abox)
            throws OWLOntologyCreationException {
        log.info("[Query] PizzaComponent 推断类型 + 供应商/价格查询");

        OWLOntology merged = backendService.getOntologyService().mergeInMemory(tbox, abox);

        //测试，打印三元组
        //OntologyService.printAboxOntology(merged);
        //OntologyService.printOntologyClasses(merged);
        //OntologyService.printOntologyIndividuals(merged);


        OWLDataFactory df = merged.getOWLOntologyManager().getOWLDataFactory();

        String ns = "http://example.org/pizza/components/classes/";
        IRI componentIri = IRI.create(ns + "PizzaComponent");
        OWLClass pizzaComponentCls = df.getOWLClass(componentIri);

        // ⭐ 新增：定义需要查询的数据属性
        OWLDataProperty supplierProp = df.getOWLDataProperty(IRI.create(ns + "supplier"));
        OWLDataProperty priceProp = df.getOWLDataProperty(IRI.create(ns + "price"));

        List<String> results;
        List<String> lines = new ArrayList<>();

        // 获取所有 PizzaComponent 实例（含推理推断出的）
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();

        Set<OWLNamedIndividual> individuals = backendService.filterRealIndividuals(
                reasoner.getInstances(pizzaComponentCls, false).getFlattened(), merged);

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
        results = lines;

        int count = 0;  //为了避免出现大量的个体打印，限制总数
        for (String line : results) {
            if (count++ >= 200) break;
            log.info("   🍕 " + line);
        }
        if (count == 0) {
            log.info("   ⚠️ 未找到 PizzaComponent 实例或相关数据属性");
        }
    }
    // 结果封装类
    public record CrustSupplier(String instanceUri, String supplier) {}
}