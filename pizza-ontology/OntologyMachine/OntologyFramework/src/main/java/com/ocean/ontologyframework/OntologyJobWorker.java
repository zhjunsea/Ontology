package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

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
            String table = (String) vars.get("tableName");
            String topCls = (String) vars.get("targetTopClass");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawTriples = (List<Map<String, Object>>) vars.get("triples");
            List<GenericAxiomBuilder.Triple> triples = rawTriples.stream()
                    .map(m -> new GenericAxiomBuilder.Triple(
                            (String) m.get("subject"),
                            (String) m.get("predicate"),
                            (String) m.get("object"),
                            (Boolean) m.getOrDefault("isObjectProperty", false)))
                    .toList();

            BackendService.objectPair key = new BackendService.objectPair(name, "name");
            insertService.insertComponent(typeNS, indNS, key, triples, table, topCls);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("createdInstanceIri", indNS + name))
                    .send().join();

            log.info("✅ insert-component 完成 | jobKey={} | instance={}", job.getKey(), name);

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
            String propIri = (String) vars.get("propertyIri");
            String newValue = (String) vars.get("newValue");
            String table = (String) vars.get("tableName");
            String topCls = (String) vars.get("targetTopClass");

            BackendService.objectPair key = new BackendService.objectPair(name, "name");
            updateService.updateIndividual(typeNS, indNS, key, propIri, newValue, table, topCls);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("updateSuccess", true))
                    .send().join();

            log.info("✅ update-individual 完成 | jobKey={} | instance={}", job.getKey(), name);

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
/*
    @JobWorker(type = "get-component", autoComplete = false)
    public void handleGetComponent(final ActivatedJob job, final JobClient client) {
        try {
            // 1. 获取 BPMN 流程变量
            Map<String, Object> vars = job.getVariablesAsMap();
            String pizzaType = (String) vars.get("pizzaType");
            String hasComponent = (String) vars.get("targetProperty");
            String prefix = (String) vars.get("prefix");

            if (pizzaType == null || pizzaType.isBlank()) {
                throw new IllegalArgumentException("流程变量 pizzaType 不能为空");
            }

            // 2. 根据披萨类型，查询该披萨所需的酱汁类型 (如: "番茄红酱", "白酱")
            Set<String> requiredComponentTypes = queryService.getBestMatchedType(pizzaType,hasComponent);

            // 3. 通过 OBDA SPARQL 查询该类型的所有酱汁实例及价格
            String requiredSauceType = requiredComponentTypes.iterator().next(); // 取最近/最具体的酱汁类型
            String valuesClause = backendService.getOntologyService()
                    .buildValuesClause("sauceType", Set.of(requiredSauceType));

            String sparql = """
                    PREFIX : <%s>
                    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    SELECT DISTINCT ?instance ?price WHERE {
                        %s
                        ?instance rdf:type ?sauceType .
                        ?instance :price ?price .
                    }
                    """.formatted(prefix, valuesClause);

            List<Map<String, String>> rows = backendService.getObdaHandler().executeAboxQuery(sparql);

            double minPrice = Double.MAX_VALUE;
            String finalInstance = null;


            for (Map<String, String> row : rows) {
                String instanceIri = row.get("instance");
                String priceStr = row.get("price");

                try {
                    double price = Double.parseDouble(priceStr);
                    if (price < minPrice) {
                        minPrice = price;
                        finalInstance = instanceIri;
                    }
                } catch (NumberFormatException e) {
                    log.warn("无法解析组件价格: instance={}, price={}", finalInstance, priceStr);
                }
            }

            if (finalInstance == null) {
                throw new IllegalStateException(
                        "数据库中未找到类型为 [" + requiredSauceType + "] 的有效价格组件实例");
            }

            log.info("✅ 找到最低价格组件: {} | price={}",
                    finalInstance, minPrice);
            if (log.isDebugEnabled()) {
                rows.forEach(r -> log.debug("  instance={} | price={}",
                        r.get("instance"), r.get("price")));
            }

            // 4. 将获取到的酱汁名称和价格写回流程变量，并完成任务
            Map<String, Object> resultVariables = Map.of(
                    "componentName", finalInstance,
                    "componentPrice", minPrice,
                    "matchedWord", resolveMatchedWord(finalInstance)
            );

            client.newCompleteCommand(job.getKey())
                    .variables(resultVariables)
                    .send()
                    .join();

            log.info("✅ get-component 完成 | jobKey={} | pizzaType={} | component={} | price={}",
                    job.getKey(), pizzaType, finalInstance, minPrice);

        } catch (Exception e) {
            log.error("❌ get-component 失败 | jobKey={}", job.getKey(), e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("GET_COMPONENT_FAILED")
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    private static final String TOMATO_SAUCE_IRI = "http://example.org/pizza/components/classes/TomatoSauce";
    private static final String WHITE_SAUCE_IRI  = "http://example.org/pizza/components/classes/WhiteSauce";
    private static final String DEFAULT_MATCHED_WORD = "番茄红酱";
*/
    /**
     * 根据个体的推理类型层级匹配酱汁中文名称（简化版）
     * <p>
     * 复用 BackendService 已有的 getIndividualAllTypes()，
     * 无需外部传入 OWLReasoner，签名仅需 individualIri。
     *
     * //@param individualIri 目标个体的完整 IRI 字符串
     * @return "番茄红酱" | "白酱" | 缺省 "番茄红酱"

    public String resolveMatchedWord(String individualIri) {
        if (individualIri == null || individualIri.isBlank()) {
            log.warn("resolveMatchedWord: individualIri 为空, 返回缺省值");
            return DEFAULT_MATCHED_WORD;
        }

        try {
            // 1. 复用已有方法：获取个体 + 完整类型闭包（含所有父类）
            OWLNamedIndividual individual = backendService.getIndividual(individualIri);
            Set<OWLClass> allTypes = backendService.getIndividualAllTypes(individual);

            // 2. 遍历匹配
            for (OWLClass cls : allTypes) {
                String iri = cls.getIRI().toString();
                if (TOMATO_SAUCE_IRI.equals(iri)) {
                    return "番茄红酱";
                }
                if (WHITE_SAUCE_IRI.equals(iri)) {
                    return "白酱";
                }
            }

            log.debug("未匹配到目标酱汁类型, 返回缺省值. individual={}, types={}",
                    individualIri, allTypes.size());
            return DEFAULT_MATCHED_WORD;

        } catch (IllegalArgumentException e) {
            // getIndividual() 在个体不存在时抛出此异常
            log.warn("resolveMatchedWord: 个体不存在, 返回缺省值. iri={}, msg={}",
                    individualIri, e.getMessage());
            return DEFAULT_MATCHED_WORD;
        } catch (Exception e) {
            log.error("resolveMatchedWord 异常, 返回缺省值. iri={}, error={}",
                    individualIri, e.getMessage(), e);
            return DEFAULT_MATCHED_WORD;
        }
    }*/
    //第一个元素为无该组件，最后一个元素为缺省组件
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
            String matchedWord = null;
            if(indType == null)
                matchedWord = resolveMatchedWord(indPrefix +finalInstance, candidateLabels, true);
            else
                matchedWord = resolveMatchedWord(classPrefix+indType, candidateLabels, false);
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
    /**
     * 根据个体的推理类型闭包，从候选标签列表中匹配并返回第一个命中的 rdfs:label
     * <p>
     * 匹配规则：遍历个体所有推理类型，读取其 rdfs:label 注解值，
     *           优先匹配 xml:lang="zh" 的标签，首个存在于 candidateLabels 中的即返回。
     * 缺省规则：若全部未命中，返回 candidateLabels 的最后一个元素作为缺省值。
     *
     * @param entityIri   目标个体或者类的完整 IRI 字符串
     * @param candidateLabels 候选标签有序列表，最后一个元素为缺省值；不能为空
     * @return 匹配到的标签，或列表末尾的缺省值
     */
    public String resolveMatchedWord(String entityIri, List<String> candidateLabels, boolean byInd) {
        // 1. 参数防御
        if (candidateLabels == null || candidateLabels.isEmpty()) {
            log.warn("resolveMatchedWord: candidateLabels 为空, 返回空字符串");
            return "";
        }
        String defaultValue = candidateLabels.get(candidateLabels.size() - 1);

        if (entityIri == null || entityIri.isBlank()) {
            log.warn("resolveMatchedWord: individualIri 为空, 返回缺省值={}", defaultValue);
            return defaultValue;
        }

        try {
            Set<OWLClass> allTypes = null;
            if(byInd) {
                // 2. 获取个体完整类型闭包
                OWLNamedIndividual individual = backendService.getIndividual(entityIri);
                allTypes = backendService.getIndividualAllTypes(individual);
            }
            else
                allTypes = backendService.getReasonerService().getSuperClassesIncludingSelf(entityIri);

            // 3. 预构建候选集合用于 O(1) 查找
            Set<String> candidateSet = new LinkedHashSet<>(candidateLabels);

            OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
            var dataFactory = backendService.getOntologyService().getDataFactory();

            // ⭐ 使用 OWL API 标准常量，无需自定义 DISPLAY_LABEL_ANNOTATION_IRI
            var labelProperty = dataFactory.getRDFSLabel();

            // 4. 遍历类型闭包，查找第一个在候选列表中的 rdfs:label
            for (OWLClass cls : allTypes) {
                Optional<String> labelOpt = tbox.annotationAssertionAxioms(cls.getIRI())
                        .filter(ax -> ax.getProperty().equals(labelProperty))
                        .map(ax -> ax.getValue().asLiteral().orElse(null))
                        .filter(lit -> lit != null && !lit.getLiteral().isBlank())
                        // ⭐ 优先匹配中文标签，避免取到英文 label
                        .sorted((a, b) -> {
                            boolean aZh = "zh".equals(a.getLang());
                            boolean bZh = "zh".equals(b.getLang());
                            return Boolean.compare(bZh, aZh);
                        })
                        .map(lit -> lit.getLiteral())
                        .filter(candidateSet::contains)
                        .findFirst();

                if (labelOpt.isPresent()) {
                    log.debug("resolveMatchedWord 命中 | Entities={} | class={} | label={}",
                            entityIri, cls.getIRI().getShortForm(), labelOpt.get());
                    return labelOpt.get();
                }
            }

            log.debug("resolveMatchedWord 未命中, 返回缺省值 | Entities={} | types={} | default={}",
                    entityIri, allTypes.size(), defaultValue);
            return defaultValue;

        } catch (IllegalArgumentException e) {
            log.warn("resolveMatchedWord: 类/个体不存在, 返回缺省值 | iri={}, msg={}, default={}",
                    entityIri, e.getMessage(), defaultValue);
            return defaultValue;
        } catch (Exception e) {
            log.error("resolveMatchedWord 异常, 返回缺省值 | iri={}, error={}, default={}",
                    entityIri, e.getMessage(), defaultValue);
            return defaultValue;
        }
    }
}