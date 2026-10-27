package com.ocean.ontologyframework.industryinsight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.utilities.ProcessOrchestrator;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import io.camunda.zeebe.process.test.filters.RecordStream;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行业洞察（五看三定）流程 · 内存引擎端到端（生产 {@code IndustryInsightOntologyJobWorker} 驱动，离线）。
 *
 * <p>以 {@code zeebe-process-test} 内存引擎部署 {@code IndustryInsightProcess}，由生产 Worker 消费
 * 全部 15 个 serviceTask，覆盖主干节点顺序、G1「场景校验」两分支与 G2「中/低」分支，断言实例完成、无 incident。
 *
 * <p><b>离线可全绿</b>：未装配 LLM 时节点 8 兜底分档恒为「中」，G2 走「中/低」分支、不触发节点 9
 * （{@code send-key-insight-message}），故本类不依赖 RabbitMQ 等任何外部服务。
 * 「高」分支由 {@link IndustryInsightKeyInsightMessageTest} 以测试替身覆盖。
 */
@SpringBootTest(classes = IndustryInsightApplication.class)
@ActiveProfiles("IndustryInsightBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("行业洞察（五看三定）流程 · 内存引擎端到端（生产 Worker 驱动，离线）")
class IndustryInsightBpmnInMemoryTest {

    private static final Logger log = LoggerFactory.getLogger(IndustryInsightBpmnInMemoryTest.class);

    private static final String PROCESS_ID = "IndustryInsightProcess";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 主干（校验通过且优先级非「高」）经过的 13 个 serviceTask，顺序稳定。 */
    private static final List<String> MAIN_CHAIN = List.of(
            "Task_ScenarioParse", "Task_HypothesisTree", "Task_IndustryTrend", "Task_MarketCustomer",
            "Task_Competition", "Task_Self", "Task_Opportunity", "Task_SpanLocate",
            "Task_CrossValidate", "Task_ControlPoint", "Task_Objective", "Task_Strategy", "Task_Report");

    /** 校验不通过的澄清分支。 */
    private static final List<String> CLARIFY_BRANCH = List.of(
            "Task_ScenarioParse", "Gateway_ScenarioCheck", "Task_ScenarioClarify", "End_01");

    private static ZeebeTestEngine engine;
    private static ZeebeClient testClient;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @DynamicPropertySource
    static void startInMemoryEngine(DynamicPropertyRegistry registry) {
        engine = EngineFactory.create();
        engine.start();
        testClient = engine.createClient();
        BpmnAssert.initRecordStream(RecordStream.of(engine.getRecordStreamSource()));

        String address = normalize(engine.getGatewayAddress());
        log.info("内存引擎已启动 | rawGatewayAddress={} | normalized={}", engine.getGatewayAddress(), address);
        registry.add("camunda.client.grpc-address", () -> address);
        registry.add("camunda.client.rest-address", () -> address);
        registry.add("camunda.client.prefer-rest-over-grpc", () -> "false");
    }

    @AfterAll
    static void stopEngine() {
        if (testClient != null) {
            try { testClient.close(); } catch (Exception ignored) { }
        }
        if (engine != null) {
            try { engine.stop(); } catch (Exception ignored) { }
        }
    }

    // ============================ 正向 ============================

    @Test
    @Order(1)
    @DisplayName("P-1 通过场景全链完成：MAIN_CHAIN 有序经过，validationResult=通过、priorityLevel=中、报告落盘")
    void fullScenarioCompletes() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业洞察与增长机会分析");
        vars.put("industry", "新能源汽车");
        vars.put("region", "中国");
        vars.put("timeWindow", "未来三年");

        ProcessInstanceEvent instance = run(vars);
        BpmnAssert.assertThat(instance).hasPassedElementsInOrder(MAIN_CHAIN.toArray(new String[0]));

        Map<String, Object> out = variables(instance.getProcessInstanceKey());
        assertThat(str(out.get("validationResult"))).isEqualTo("通过");
        assertThat(str(out.get("priorityLevel"))).isEqualTo("中");
        assertThat(str(out.get("insightSkillPath"))).as("洞察 SKILL 应已落盘").isNotBlank();
        assertThat(str(out.get("ontologyValidationStatus"))).contains("通过");
        assertThat(str(out.get("insightSummary"))).contains("行业洞察报告");
    }

    @Test
    @Order(2)
    @DisplayName("P-2 缺洞察目标：G1 不通过走澄清分支，不进主链")
    void clarificationBranchOnMissingGoal() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业");

        ProcessInstanceEvent instance = run(vars);
        BpmnAssert.assertThat(instance)
                .hasPassedElementsInOrder(CLARIFY_BRANCH.toArray(new String[0]))
                .hasNotPassedElement("Task_HypothesisTree");

        Map<String, Object> out = variables(instance.getProcessInstanceKey());
        assertThat(str(out.get("validationResult"))).isEqualTo("不通过");
        assertThat(str(out.get("missingElement"))).contains("洞察目标");
        assertThat(str(out.get("clarificationText"))).isNotBlank();
    }

    // ============================ 边界 / 负向 ============================

    @Test
    @Order(3)
    @DisplayName("B-1 补充字段全缺省：兜底抽取，MAIN_CHAIN 完成，parseSource=兜底")
    void defaultFallbackCompletes() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业分析与增长机会");

        ProcessInstanceEvent instance = run(vars);
        BpmnAssert.assertThat(instance).hasPassedElementsInOrder(MAIN_CHAIN.toArray(new String[0]));

        Map<String, Object> out = variables(instance.getProcessInstanceKey());
        assertThat(str(out.get("parseSource"))).as("未装配 LLM 时应为确定性兜底").isEqualTo("兜底");
        assertThat(str(out.get("validationResult"))).isEqualTo("通过");
    }

    @Test
    @Order(4)
    @DisplayName("B-2 场景文本过简（长度<6）：澄清分支，missingElement 含「文本过简」")
    void tooShortScenarioClarifies() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源");

        ProcessInstanceEvent instance = run(vars);
        BpmnAssert.assertThat(instance).hasPassedElementsInOrder(CLARIFY_BRANCH.toArray(new String[0]));

        Map<String, Object> out = variables(instance.getProcessInstanceKey());
        assertThat(str(out.get("missingElement"))).contains("文本过简");
    }

    @Test
    @Order(5)
    @DisplayName("N-1 缺行业要素：澄清分支，missingElement 含「行业/领域」")
    void missingIndustryClarifies() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "洞察增长机会");

        ProcessInstanceEvent instance = run(vars);
        BpmnAssert.assertThat(instance).hasPassedElementsInOrder(CLARIFY_BRANCH.toArray(new String[0]));

        Map<String, Object> out = variables(instance.getProcessInstanceKey());
        assertThat(str(out.get("validationResult"))).isEqualTo("不通过");
        assertThat(str(out.get("missingElement"))).contains("行业/领域");
    }

    @Test
    @Order(6)
    @DisplayName("N-2 空场景：澄清分支，missingElement 同时含「行业/领域」「洞察目标」")
    void emptyScenarioClarifies() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "");

        ProcessInstanceEvent instance = run(vars);
        BpmnAssert.assertThat(instance).hasPassedElementsInOrder(CLARIFY_BRANCH.toArray(new String[0]));

        Map<String, Object> out = variables(instance.getProcessInstanceKey());
        assertThat(str(out.get("missingElement"))).contains("行业/领域").contains("洞察目标");
    }

    // ============================ 辅助 ============================

    private ProcessInstanceEvent run(Map<String, Object> variables) {
        assertThat(Files.isRegularFile(Path.of(bpmnPath)))
                .as("BPMN 文件应存在: %s", bpmnPath).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(testClient, PROCESS_ID, Path.of(bpmnPath).toString()))
                .as("应成功部署流程").isTrue();

        ProcessInstanceEvent instance = testClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        awaitCompletion(instance);
        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement("Start_01")
                .hasPassedElement("End_01");
        return instance;
    }

    private static void awaitCompletion(ProcessInstanceEvent instance) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        AssertionError last = null;
        while (System.nanoTime() < deadline) {
            try {
                BpmnAssert.assertThat(instance).isCompleted();
                return;
            } catch (AssertionError e) {
                last = e;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last != null ? last : new AssertionError("流程未在 " + TIMEOUT + " 内完成");
    }

    /** 从内存引擎记录流中读取指定实例的顶层变量（JSON 值解码，后写覆盖先写）。 */
    private static Map<String, Object> variables(long processInstanceKey) {
        Map<String, Object> vars = new LinkedHashMap<>();
        for (Record<VariableRecordValue> r : RecordStream.of(engine.getRecordStreamSource()).variableRecords()) {
            VariableRecordValue v = r.getValue();
            if (v.getProcessInstanceKey() == processInstanceKey) {
                vars.put(v.getName(), decode(v.getValue()));
            }
        }
        return vars;
    }

    private static Object decode(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String normalize(String gatewayAddress) {
        if (gatewayAddress == null || gatewayAddress.isBlank()) {
            throw new IllegalStateException("内存引擎未返回有效 gateway 地址");
        }
        return gatewayAddress.startsWith("http") ? gatewayAddress : "http://" + gatewayAddress;
    }
}
