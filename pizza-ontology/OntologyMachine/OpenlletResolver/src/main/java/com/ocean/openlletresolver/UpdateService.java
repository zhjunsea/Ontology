package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.ObdaMappingParser;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UpdateService {
    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    private final BackendService backendService;

    public UpdateService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
    }

    //NS是Type类的名字空间，比如NeapolitanCrust的是http://example.org/pizza/components/classes/
    //targetTopClass是为了校验用，一般设为顶级父类
    public void updateIndividual(String typeNS, String indNS, BackendService.objectPair objectPair, String propertyIri, String newValue,String tableName,String targetTopClass) throws Exception {
        // ==================== 1. 参数校验 ====================
        if (objectPair.objectName() == null || objectPair.objectName().isBlank()) {
            throw new IllegalArgumentException("individualName 不能为空");
        }
        if (propertyIri == null || propertyIri.isBlank()) {
            throw new IllegalArgumentException("propertyIri 不能为空");
        }
        Objects.requireNonNull(backendService, "backendService 不能为null");

        log.info("🔄 开始安全更新个体 [{}] | 属性: {} | 新值: {}", objectPair.objectName(), propertyIri, newValue);

        // ==================== 2. 旧值公理验证（只读校验） ====================
        Set<OWLAxiom> oldAxioms = backendService.queryPropertyAxiom(typeNS,indNS,objectPair.objectName(), propertyIri);

        if (!oldAxioms.isEmpty()) {
            // 仅校验旧值与TBox的一致性，不执行任何DB操作
            backendService.safeVerifyAndDBExecution(oldAxioms, targetTopClass, null);
            log.info("✅ 旧值公理 TBox 一致性验证通过");
        } else {
            log.warn("⚠️ 个体 [{}] 当前无属性 [{}] 的值，跳过旧值验证", objectPair.objectName(), propertyIri);
        }

        // ==================== 3. 获得属性类型并构建新值公理 ====================
        // ========== 1. 初始化收集容器 ==========
        Set<OWLClass> directTypes = new HashSet<>();
        Boolean isObjectProperty = null; // 用 null 表示尚未从 oldAxioms 中找到匹配的属性公理

        OWLOntology tBoxOntology = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory dataFactory = tBoxOntology.getOWLOntologyManager().getOWLDataFactory();
        IRI individualIri = IRI.create(objectPair.objectName());

        // ========== 2. 单次遍历 oldAxioms，按公理类型分别提取 ==========
        for (OWLAxiom ax : oldAxioms) {
            // --- 分支 A: 属性断言公理 → 判断 isObjectProperty ---
            if (ax instanceof OWLPropertyAssertionAxiom propAx) {
                // 仅处理主语匹配的公理
                if (!propAx.getSubject().asOWLNamedIndividual().getIRI().equals(individualIri)) {
                    continue;
                }

                IRI propIRI;
                if (propAx instanceof OWLObjectPropertyAssertionAxiom opAx) {
                    propIRI = opAx.getProperty().asOWLObjectProperty().getIRI();
                } else if (propAx instanceof OWLDataPropertyAssertionAxiom dpAx) {
                    propIRI = dpAx.getProperty().asOWLDataProperty().getIRI();
                } else {
                    continue; // AnnotationProperty 等无关类型跳过
                }

                // 找到目标属性后记录类型（取第一个匹配即可）
                if (propIRI.equals(propertyIri) && isObjectProperty == null) {
                    isObjectProperty = ax.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION);
                }

                // --- 分支 B: 类断言公理 → 收集到 directTypes ---
            } else if (ax instanceof OWLClassAssertionAxiom classAx) {
                // 仅处理主语匹配的公理
                if (!classAx.getIndividual().asOWLNamedIndividual().getIRI().equals(IRI.create(indNS + individualIri))){
                    continue;
                }

                OWLClassExpression ce = classAx.getClassExpression();
                // 只收集命名类（Named Class），排除匿名类表达式（如 ObjectSomeValuesFrom）
                if (!ce.isAnonymous()) {
                    directTypes.add(ce.asOWLClass());
                }
            }
            // 其他公理类型（Annotation、SubClassOf 等）自动忽略
        }

        // ========== 3. Fallback：oldAxioms 中未找到属性公理时走后端查询 ==========
        if (isObjectProperty == null) {
            isObjectProperty = backendService.getOntologyService().checkIsObjectProperty(propertyIri);
        }

        // ========== 4. 查找最具体类并组装三元组 ==========
        String rdfTypeIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

        GenericAxiomBuilder.Triple propertyTriple = new GenericAxiomBuilder.Triple(
                objectPair.objectName(), propertyIri, newValue, isObjectProperty
        );

        List<GenericAxiomBuilder.Triple> newTriples;
        if (!directTypes.isEmpty()) {
            String mostSpecificClass = backendService.findMostSpecificClass(directTypes, tBoxOntology);
            if (mostSpecificClass != null) {
                GenericAxiomBuilder.Triple typeTriple = new GenericAxiomBuilder.Triple(
                        objectPair.objectName(), rdfTypeIri, mostSpecificClass, true
                );
                newTriples = List.of(propertyTriple, typeTriple);
            } else {
                newTriples = List.of(propertyTriple);
            }
        } else {
            newTriples = List.of(propertyTriple);
        }

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(typeNS,indNS);
        Set<OWLAxiom> newAxioms = axiomBuilder.buildAxioms(newTriples);

        // ==================== 4. 校验 + DB更新 合并为单次 safeVerifyAndDBExecution ====================
        // ✅ 参照 insertPizzaComponent 模式：验证与写入在同一事务中完成
        ObdaMappingParser.load(backendService.getObdaHandler().getObdaPath());;
        ObdaMappingParser.ColumnMapping mapping = ObdaMappingParser.resolve(propertyIri);
        Object newColumnValue = ObdaMappingParser.convertObjectValue(newValue, mapping);

        String individualNameInDB = OntologyService.getLocalName(objectPair.objectName());

        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            log.info("💾 更新数据库: {} | name={} | {}={}",
                    tableName, individualNameInDB, mapping.getColumnName(), newColumnValue);
            // ✅ 使用 updateComponent 而非 addComponent
            OBDAHandler.getInstance().updateComponent(
                    "pizza_components",
                    List.of(mapping.getColumnName()),
                    List.of(newColumnValue),
                    objectPair.columnName(),
                    individualNameInDB
            );
        };

        // 一次调用同时完成：新值本体一致性校验 + 数据库更新
        backendService.safeVerifyAndDBExecution(newAxioms, targetTopClass, dbAction);

        log.info("✅ 个体 [{}] 安全更新完成 | 新值: {}", objectPair.objectName(), newValue);
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