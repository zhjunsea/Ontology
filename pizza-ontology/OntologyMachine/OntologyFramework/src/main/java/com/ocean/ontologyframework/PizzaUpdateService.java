package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.ObdaMappingParser;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.GenericAxiomBuilder;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class PizzaUpdateService {
    private static final Logger log = LoggerFactory.getLogger(PizzaUpdateService.class);
    private static final String NS = "http://example.org/pizza/components/classes/";
    private static final String OBDA_PATH = "D:/work/Ontology/pizza-ontology/ontology/database/myPizza.obda";

    // ✅ 移除 DB_WRITER 字段
    private final BackendService backendService;
    private final GenericAxiomBuilder axiomBuilder;

    public PizzaUpdateService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
        this.axiomBuilder = new GenericAxiomBuilder(NS);
    }

    public void updateIndividual(String individualName, String propertyIri, String value) throws Exception {
        if (individualName == null || individualName.isBlank()) {
            throw new IllegalArgumentException("individualName 不能为空");
        }
        if (propertyIri == null || propertyIri.isBlank()) {
            throw new IllegalArgumentException("propertyIri 不能为空");
        }

        log.info("开始更新个体 [{}]，属性: {}，值: {}", individualName, propertyIri, value);

        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(individualName, propertyIri, value, false)
        );
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(triples);
        ObdaMappingParser.load(OBDA_PATH);

        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            ObdaMappingParser.ColumnMapping mapping = ObdaMappingParser.resolve(propertyIri);
            Object columnValue = ObdaMappingParser.convertObjectValue(value, mapping);

            log.info("更新 pizza_components: name={} | 列: {}={}", individualName, mapping.getColumnName(), columnValue);
            // ✅ 使用 OBDAHandler.updateComponent
            OBDAHandler.getInstance().updateComponent(
                    "pizza_components",
                    List.of(mapping.getColumnName()),
                    List.of(columnValue),
                    "name",
                    individualName
            );
        };

        String verifySparql = """
                PREFIX : <%s>
                CONSTRUCT { ?s ?p ?o }
                WHERE { ?s a :PizzaComponent ; ?p ?o } LIMIT 5000
                """.formatted(NS);

        backendService.safeInsertAndVerify(tempAxioms, NS + "PizzaComponent", verifySparql, dbAction);
        log.info("✅ 个体 [{}] 更新完成", individualName);
    }

    public void batchUpdateByClass(String classIri, String propertyIri, String value) throws Exception {
        if (classIri == null || classIri.isBlank()) {
            throw new IllegalArgumentException("classIri 不能为空");
        }

        log.info("开始批量更新类 [{}] 下所有实例，属性: {}，值: {}", classIri, propertyIri, value);

        String queryInstancesSparql = """
                PREFIX : <%s>
                SELECT DISTINCT ?ind WHERE { ?ind a/rdfs:subClassOf* <%s> }
                """.formatted(NS, classIri);

        // 假设 BackendService 有查询个体名称的方法
        List<String> instanceNames = backendService.queryIndividualNames(queryInstancesSparql);
        if (instanceNames.isEmpty()) {
            log.warn("⚠️ 类 [{}] 下未找到任何实例，跳过批量更新", classIri);
            return;
        }
        log.info("📋 共发现 {} 个待更新实例", instanceNames.size());

        List<GenericAxiomBuilder.Triple> triples = instanceNames.stream()
                .map(name -> new GenericAxiomBuilder.Triple(name, propertyIri, value, false))
                .collect(Collectors.toList());

        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(triples);
        ObdaMappingParser.load(OBDA_PATH);

        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            ObdaMappingParser.ColumnMapping mapping = ObdaMappingParser.resolve(propertyIri);
            Object columnValue = ObdaMappingParser.convertObjectValue(value, mapping);

            log.info("批量更新 pizza_components: 实例数={} | 列: {}={}", instanceNames.size(), mapping.getColumnName(), columnValue);
            // ✅ 逐个调用 OBDAHandler.updateComponent 实现批量更新
            for (String name : instanceNames) {
                OBDAHandler.getInstance().updateComponent(
                        "pizza_components",
                        List.of(mapping.getColumnName()),
                        List.of(columnValue),
                        "name",
                        name
                );
            }
        };

        String verifySparql = """
                PREFIX : <%s>
                CONSTRUCT { ?s ?p ?o }
                WHERE { ?s a :PizzaComponent ; ?p ?o } LIMIT 5000
                """.formatted(NS);

        backendService.safeInsertAndVerify(tempAxioms, NS + "PizzaComponent", verifySparql, dbAction);
        log.info("✅ 批量更新成功，共更新 {} 个实例", instanceNames.size());
    }
}