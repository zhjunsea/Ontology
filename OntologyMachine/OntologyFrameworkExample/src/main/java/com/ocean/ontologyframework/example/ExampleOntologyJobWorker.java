package com.ocean.ontologyframework.example;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import com.ocean.utilities.OntologyLabelMatcher;
import com.ocean.utilities.OntologyWorkerSupport;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

import java.util.*;

@Component
@Profile("ExampleBPMN")
public class ExampleOntologyJobWorker extends OntologyWorkerSupport {

    private static final Logger log = LoggerFactory.getLogger(ExampleOntologyJobWorker.class);

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    @Value("${ontology.main-path}")
    private String TBOX_FILE;

    // ✅ 改为实例字段注入（更可靠）
    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;


    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 ExampleOntologyJobWorker 依赖链...");
        initOntologyPipeline();
        log.info("✅ ExampleOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
    }

    @Override
    protected BackendService createBackendService() throws Exception {
        boolean reusedObdaHandler = false;
        try {
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
        } catch (IllegalStateException alreadyInitialized) {
            reusedObdaHandler = true;
            log.warn("⚠️ OBDAHandler 已初始化，复用现有实例（同一 JVM 内多 Spring 上下文场景）");
        }
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        if (reusedObdaHandler) {
            // 复用 OBDAHandler 连接池，但为当前上下文重建 BackendService，
            // 避免复用前一个上下文已 dispose 的 reasoner
            BackendService.setInstance(null);
        }
        return BackendService.getInstance(mainOntologyPath, obdaHandler);
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

            // 3. 依次尝试所有候选组件类型，取首个能查到有效价格实例的类型
            //    （跳过无实例的候选，如 abox 个体 filler；全部无实例则降级，不抛错）
            double minPrice = Double.MAX_VALUE;
            String finalInstance = null;
            String indType = null;
            String matchedComponentType = null;

            for (String candidateType : requiredComponentTypes) {
                String valuesClause = backendService.getOntologyService()
                        .buildValuesClause("componentType", Set.of(candidateType));

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

                if (finalInstance != null) {
                    matchedComponentType = candidateType;
                    break;
                }
                log.warn("⚠️ 候选组件类型无有效价格实例，尝试下一候选 | pizzaType={} | hasComponent={} | candidate={}",
                        pizzaType, hasComponent, candidateType);
            }

            if (finalInstance == null) {
                // 降级：所有候选均无有效价格实例时，与"无组件类型"分支保持一致，返回默认值而非抛错
                log.warn("⚠️ 所有候选组件类型均无有效价格实例，降级返回默认值 | pizzaType={} | hasComponent={} | candidates={}",
                        pizzaType, hasComponent, requiredComponentTypes);

                Map<String, Object> resultVariables = Map.of(
                        "componentName", "没有该组件",
                        "componentPrice", 0.0,
                        "matchedWord", candidateLabels.get(0)
                );

                client.newCompleteCommand(job.getKey())
                        .variables(resultVariables)
                        .send()
                        .join();

                log.info("✅ get-component 完成(降级:无可用实例) | jobKey={} | pizzaType={}", job.getKey(), pizzaType);
                return;
            }

            log.info("✅ 找到最低价格组件: {} | price={} | componentType={}", finalInstance, minPrice, matchedComponentType);

            // ⭐ 调用双参数 resolveMatchedWord，传入候选标签列表
            String matchedWord;
            if (indType == null) {
                matchedWord = OntologyLabelMatcher.resolveMatchedWord(indPrefix + finalInstance, candidateLabels, true, backendService);
            } else {
                matchedWord = OntologyLabelMatcher.resolveMatchedWord(classPrefix + indType, candidateLabels, false, backendService);
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
