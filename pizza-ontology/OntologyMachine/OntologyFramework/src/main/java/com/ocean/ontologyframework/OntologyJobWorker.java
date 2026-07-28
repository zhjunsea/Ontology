package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static org.semanticweb.owlapi.vocab.OWLRDFVocabulary.RDF_TYPE;

@Component
public class OntologyJobWorker {

    private static final Logger log = LoggerFactory.getLogger(OntologyJobWorker.class);

    @Value("${ontology.main-path:D:/work/Ontology/pizza-ontology/ontology/pizza-all.owl}")
    private String mainOntologyPath;

    private InsertService insertService;
    private UpdateService updateService;
    private DeleteService deleteService;
    private QueryService queryService;
    private BackendService backendService;

    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 OntologyJobWorker 依赖链...");

        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        this.backendService = BackendService.getInstance(mainOntologyPath, obdaHandler);
        if (this.backendService == null) {
            throw new IllegalStateException("BackendService 初始化失败，请检查本体路径和 OBDA 连接");
        }

        this.insertService = new InsertService(this.backendService);
        this.updateService = new UpdateService(this.backendService);
        this.deleteService = new DeleteService(this.backendService);
        this.queryService = new QueryService(this.backendService);

        log.info("✅ OntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
    }

    // ==================== INSERT ====================
    @JobWorker(type = "insert-component", autoComplete = false)
    public void handleInsertComponent(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String typeNS = (String) vars.get("typeNS");
            String indNS = (String) vars.get("indNS");
            String name = (String) vars.get("instanceName");

            // ⭐ 1. 将 Zeebe 传入的 triples 转换为与场景6一致的 Map<String, String> properties
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawTriples = (List<Map<String, Object>>) vars.get("triples");

            Map<String, String> allProperties = new LinkedHashMap<>();
            for (Map<String, Object> triple : rawTriples) {
                String predicate = (String) triple.get("predicate");
                String object = (String) triple.get("object");
                // 跳过 rdf:type 三元组，buildAxioms 会通过 "type" key 自动处理类型声明
                if (!RDF_TYPE.equals(predicate)) {
                    allProperties.put(predicate, object);
                } else {
                    // 保留类型信息供 buildAxioms 使用
                    allProperties.putIfAbsent(typeNS + "type",
                            object.substring(object.lastIndexOf('/') + 1));
                }
            }
            // 确保 name 属性存在（Worker 入参可能未显式包含）
            allProperties.putIfAbsent(typeNS + "name", name);

            // ⭐ 2. 使用 GenericAxiomBuilder 构建公理（与场景6完全一致）
            GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
            Set<OWLAxiom> axioms = axiomBuilder.buildAxioms(indNS + name, allProperties);

            // ⭐ 3. 使用已初始化的 insertService 字段写入（修复：不再重复 new）
            this.insertService.insertComponentAutoSplit(allProperties, axioms);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("createdInstanceIri", indNS + name))
                    .send().join();

            log.info("✅ insert-component 完成 | jobKey={} | instance={} | 属性数={}",
                    job.getKey(), name, allProperties.size());

        } catch (Exception e) {
            log.error("❌ insert-component 失败 | jobKey={}", job.getKey(), e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("INSERT_FAILED").errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== UPDATE ====================
    @JobWorker(type = "update-individual", autoComplete = false)
    public void handleUpdateIndividual(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String typeNS = (String) vars.get("typeNS");
            String indNS = (String) vars.get("indNS");
            String name = (String) vars.get("instanceName");

            // ⭐ 1. 将 Zeebe 传入的更新属性列表转换为 Map<String, String>（对齐场景7）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawUpdates = (List<Map<String, Object>>) vars.get("updates");

            Map<String, String> updatedProperties = new LinkedHashMap<>();
            if (rawUpdates != null) {
                for (Map<String, Object> entry : rawUpdates) {
                    String predicate = (String) entry.get("predicate");
                    String value = (String) entry.get("value");
                    if (predicate != null && value != null) {
                        updatedProperties.put(predicate, value);
                    }
                }
            }

            // 兼容旧的单属性调用方式（如果 updates 为空则回退到 propIri/newValue）
            if (updatedProperties.isEmpty()) {
                String propIri = (String) vars.get("propertyIri");
                String newValue = (String) vars.get("newValue");
                if (propIri != null && newValue != null) {
                    updatedProperties.put(propIri, newValue);
                }
            }

            // ⭐ 2. 构造标识符（与场景7完全一致）
            Map<String, String> identifierValues = new LinkedHashMap<>();
            identifierValues.put(typeNS + "name", name);

            // ⭐ 3. 使用已初始化的 updateService 字段执行更新（修复：不再重复 new）
            this.updateService.updateComponentAutoSplit(identifierValues, updatedProperties);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("updateSuccess", true))
                    .send().join();

            log.info("✅ update-individual 完成 | jobKey={} | instance={} | 更新属性数={}",
                    job.getKey(), name, updatedProperties.size());

        } catch (Exception e) {
            log.error("❌ update-individual 失败 | jobKey={}", job.getKey(), e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("UPDATE_FAILED").errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== DELETE ====================
    @JobWorker(type = "delete-component", autoComplete = false)
    public void handleDeleteComponent(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String typeNS = (String) vars.get("typeNS");
            String indNS = (String) vars.get("indNS");
            String name = (String) vars.get("instanceName");
            String instanceType = (String) vars.get("instanceType");
            String table = (String) vars.get("tableName");
            String topCls = (String) vars.get("targetTopClass");

            List<GenericAxiomBuilder.Triple> triples = List.of(
                    new GenericAxiomBuilder.Triple(name, "rdf:type", instanceType, false));
            BackendService.objectPair key = new BackendService.objectPair(name, "name");
            int rows = deleteService.deleteComponent(typeNS, indNS, key, triples, table, topCls);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("deletedRows", rows))
                    .send().join();

            log.info("✅ delete-component 完成 | jobKey={} | deletedRows={}", job.getKey(), rows);

        } catch (Exception e) {
            log.error("❌ delete-component 失败 | jobKey={}", job.getKey(), e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("DELETE_FAILED").errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== QUERY INSTANCES ====================
    @JobWorker(type = "query-instances", autoComplete = false)
    public void handleQueryInstances(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String rootClassIri = (String) vars.get("rootClassIri");
            int maxResults = vars.containsKey("maxResults")
                    ? ((Number) vars.get("maxResults")).intValue() : -1;
            boolean includeDirectType = Boolean.TRUE.equals(vars.get("includeDirectType"));

            @SuppressWarnings("unchecked")
            List<String> dataPropertyIris = vars.containsKey("dataPropertyIris")
                    ? (List<String>) vars.get("dataPropertyIris") : List.of();

            String returnKey = vars.containsKey("returnKey")
                    ? (String) vars.get("returnKey") : "strResult";

            QueryService.QueryConfig config = QueryService.QueryConfig.builder()
                    .rootClassIri(rootClassIri)
                    .dataProperties(dataPropertyIris)
                    .maxResults(maxResults)
                    .includeDirectType(includeDirectType)
                    .build();

            OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
            OWLOntology abox = backendService.getOntologyService().getaBoxOntology();

            List<QueryService.IndividualRecord> records = queryService.queryInstances(tbox, abox, config);

            String strResult = records.isEmpty() ? "" : records.stream()
                    .map(QueryService.IndividualRecord::toString)
                    .collect(Collectors.joining(","));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(returnKey, strResult);
            result.put("queryResults", records);

            client.newCompleteCommand(job.getKey()).variables(result).send().join();
            log.info("✅ query-instances 完成 | jobKey={} | count={}", job.getKey(), records.size());

        } catch (Exception e) {
            log.error("❌ query-instances 失败 | jobKey={}", job.getKey(), e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("QUERY_FAILED").errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== QUERY PROPERTY VALUE (ONTOLOGY) ====================
    @JobWorker(type = "query-property-value-ontology", autoComplete = false)
    public void handleQueryPropertyValue(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String individualIri = (String) vars.get("individualIri");
            String propertyIri = (String) vars.get("propertyIri");

            List<String> values = queryService.queryPropertyValueInOntology(individualIri, propertyIri);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("propertyValues", values))
                    .send().join();

            log.info("✅ query-property-value 完成 | jobKey={} | values={}", job.getKey(), values.size());

        } catch (Exception e) {
            log.error("❌ query-property-value 失败 | jobKey={}", job.getKey(), e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("PROPERTY_QUERY_FAILED").errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== QUERY PROPERTY VALUE (DB) ====================
    @JobWorker(type = "query-property-value-db", autoComplete = false)
    public void handleQueryPropertyValueDB(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String ns = (String) vars.get("ns");
            String individualIri = (String) vars.get("individualIri");
            String propertyIri = (String) vars.get("propertyIri");

            List<String> values = queryService.queryPropertyValueInDB(ns, individualIri, propertyIri);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("propertyValues", values))
                    .send().join();

            log.info("✅ query-property-value-db 完成 | jobKey={} | values={}", job.getKey(), values.size());

        } catch (Exception e) {
            log.error("❌ query-property-value-db 失败 | jobKey={}", job.getKey(), e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("DB_PROPERTY_QUERY_FAILED").errorMessage(e.getMessage()).send().join();
        }
    }

    // ==================== GET COMPONENT ====================
    // 第一个元素为无该组件，最后一个元素为缺省组件
    @JobWorker(type = "get-component", autoComplete = false)
    public void handleGetComponent(final ActivatedJob job, final JobClient client) {
        try {
            // 1. 获取 BPMN 流程变量
            Map<String, Object> vars = job.getVariablesAsMap();
            String pizzaType = (String) vars.get("pizzaType");
            String hasComponent = (String) vars.get("targetProperty");
            String classPrefix = (String) vars.get("classPrefix");
            String indPrefix = (String) vars.get("indPrefix");

            if (pizzaType == null || pizzaType.isBlank()) {
                throw new IllegalArgumentException("流程变量 pizzaType 不能为空");
            }

            // ⭐ 从流程变量中获取候选标签列表
            @SuppressWarnings("unchecked")
            List<String> candidateLabels = vars.containsKey("candidateLabels")
                    ? (List<String>) vars.get("candidateLabels")
                    : List.of("没有任何可选项");

            // 2. 根据披萨类型，查询该披萨所需的组件类型
            Set<String> requiredComponentTypes = queryService.getBestMatchedType(pizzaType, hasComponent);

            // ⭐ 新增：如果本体中没有该属性/组件类型，直接返回"没有该组件"，跳过 SPARQL 查询
            if (requiredComponentTypes == null || requiredComponentTypes.isEmpty()) {
                log.warn("⚠️ 未找到组件类型 | pizzaType={} | hasComponent={} | 返回默认值", pizzaType, hasComponent);

                Map<String, Object> resultVariables = Map.of(
                        "componentName", "没有该组件",
                        "componentPrice", 0.0,
                        "matchedWord", candidateLabels.get(0)
                );

                client.newCompleteCommand(job.getKey())
                        .variables(resultVariables)
                        .send()
                        .join();

                log.info("✅ get-component 完成(无组件) | jobKey={} | pizzaType={}", job.getKey(), pizzaType);
                return;
            }

            // 3. 通过 OBDA SPARQL 查询该类型的所有实例及价格
            String requiredComponentType = requiredComponentTypes.iterator().next();
            String valuesClause = backendService.getOntologyService()
                    .buildValuesClause("componentType", Set.of(requiredComponentType));

            String sparql = """
                PREFIX : <%s>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                SELECT DISTINCT ?instance ?price ?type WHERE {
                %s
                ?instance rdf:type ?componentType .
                ?instance rdf:type ?type .
                ?instance :price ?price .
            }
            """.formatted(classPrefix, valuesClause);

            List<Map<String, String>> rows = backendService.getObdaHandler().executeAboxQuery(sparql);

            double minPrice = Double.MAX_VALUE;
            String finalInstance = null;
            String indType = null;

            for (Map<String, String> row : rows) {
                String instanceIri = row.get("instance");
                String priceStr = row.get("price");
                indType = row.get("type");

                try {
                    double price = Double.parseDouble(priceStr);
                    if (price < minPrice) {
                        minPrice = price;
                        finalInstance = instanceIri;
                    }
                } catch (NumberFormatException e) {
                    log.warn("无法解析组件价格: instance={}, price={}", instanceIri, priceStr);
                }
            }

            if (finalInstance == null) {
                throw new IllegalStateException(
                        "数据库中未找到类型为 [" + requiredComponentType + "] 的有效价格组件实例");
            }

            log.info("✅ 找到最低价格组件: {} | price={}", finalInstance, minPrice);
            if (log.isDebugEnabled()) {
                rows.forEach(r -> log.debug("  instance={} | price={}", r.get("instance"), r.get("price")));
            }

            // ⭐ 调用双参数 resolveMatchedWord，传入候选标签列表
            String matchedWord;
            if (indType == null) {
                matchedWord = Utilities.resolveMatchedWord(indPrefix + finalInstance, candidateLabels, true, backendService);
            } else {
                matchedWord = Utilities.resolveMatchedWord(classPrefix + indType, candidateLabels, false, backendService);
            }

            // 4. 将获取到的组件名称、价格和匹配标签写回流程变量，并完成任务
            Map<String, Object> resultVariables = Map.of(
                    "componentName", finalInstance,
                    "componentPrice", minPrice,
                    "matchedWord", matchedWord
            );

            client.newCompleteCommand(job.getKey())
                    .variables(resultVariables)
                    .send()
                    .join();

            log.info("✅ get-component 完成 | jobKey={} | pizzaType={} | component={} | price={} | matchedWord={}",
                    job.getKey(), pizzaType, finalInstance, minPrice, matchedWord);

        } catch (Exception e) {
            log.error("❌ get-component 失败 | jobKey={}", job.getKey(), e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("GET_COMPONENT_FAILED")
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}