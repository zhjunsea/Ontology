package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.ObdaMappingParser;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.GenericAxiomBuilder;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PizzaUpdateService {
    private static final Logger log = LoggerFactory.getLogger(PizzaUpdateService.class);
    private static final String NS = "http://example.org/pizza/components/classes/";
    private static final String OBDA_PATH = "D:/work/Ontology/pizza-ontology/ontology/database/myPizza.obda";

    private final BackendService backendService;
    private final GenericAxiomBuilder axiomBuilder;

    public PizzaUpdateService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
        this.axiomBuilder = new GenericAxiomBuilder(NS);
    }

    public void updateIndividual(String individualName, String propertyIri, String newValue) throws Exception {
        // ==================== 1. 参数校验 ====================
        if (individualName == null || individualName.isBlank()) {
            throw new IllegalArgumentException("individualName 不能为空");
        }
        if (propertyIri == null || propertyIri.isBlank()) {
            throw new IllegalArgumentException("propertyIri 不能为空");
        }
        Objects.requireNonNull(backendService, "backendService 不能为null");

        log.info("🔄 开始安全更新个体 [{}] | 属性: {} | 新值: {}", individualName, propertyIri, newValue);

        String verifySparql = """
        PREFIX : <%s>
        CONSTRUCT { ?s ?p ?o }
        WHERE {
            VALUES ?s { "%s" }
            ?s a :PizzaComponent ;
               ?p ?o .
        }
        LIMIT 5000
        """.formatted(NS, individualName);

        // ==================== 2. 旧值公理验证（只读校验） ====================
        Set<OWLAxiom> oldAxioms = backendService.queryPropertyAxiom(individualName, propertyIri);

        if (!oldAxioms.isEmpty()) {
            // 仅校验旧值与TBox的一致性，不执行任何DB操作
            backendService.safeVerifyAndDBExecution(oldAxioms, NS + "PizzaComponent", null);
            log.info("✅ 旧值公理 TBox 一致性验证通过");
        } else {
            log.warn("⚠️ 个体 [{}] 当前无属性 [{}] 的值，跳过旧值验证", individualName, propertyIri);
        }

        // ==================== 3. 推导属性类型并构建新值公理 ====================
        boolean isObjectProperty;
        OWLAxiom oldAxiom = oldAxioms.iterator().next();
        if (oldAxiom != null) {
            isObjectProperty = oldAxiom.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION);
        } else {
            isObjectProperty = backendService.getOntologyService().checkIsObjectProperty(propertyIri);
        }

        List<GenericAxiomBuilder.Triple> newTriples = List.of(
                new GenericAxiomBuilder.Triple(individualName, propertyIri, newValue, isObjectProperty)
        );
        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(NS);
        Set<OWLAxiom> newAxioms = axiomBuilder.buildAxioms(newTriples);

        // ==================== 4. 校验 + DB更新 合并为单次 safeVerifyAndDBExecution ====================
        // ✅ 参照 insertPizzaComponent 模式：验证与写入在同一事务中完成
        ObdaMappingParser.load(OBDA_PATH);
        ObdaMappingParser.ColumnMapping mapping = ObdaMappingParser.resolve(propertyIri);
        Object newColumnValue = ObdaMappingParser.convertObjectValue(newValue, mapping);

        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            log.info("💾 更新数据库: pizza_components | name={} | {}={}",
                    individualName, mapping.getColumnName(), newColumnValue);
            // ✅ 使用 updateComponent 而非 addComponent
            OBDAHandler.getInstance().updateComponent(
                    "pizza_components",
                    List.of(mapping.getColumnName()),
                    List.of(newColumnValue),
                    "name",
                    individualName
            );
        };

        // 一次调用同时完成：新值本体一致性校验 + 数据库更新
        backendService.safeVerifyAndDBExecution(newAxioms, NS + "PizzaComponent", dbAction);

        log.info("✅ 个体 [{}] 安全更新完成 | 新值: {}", individualName, newValue);
    }
/*
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

        backendService.safeVerifyAndDBExecution(tempAxioms, NS + "PizzaComponent", verifySparql, dbAction);
        log.info("✅ 批量更新成功，共更新 {} 个实例", instanceNames.size());
    }*/
}