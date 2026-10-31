package com.ocean.ontologyframework.industryinsight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.utilities.ChatAgent;
import com.ocean.utilities.ProcessOrchestrator;
import com.ocean.utilities.RabbitMqHandler;
import com.ocean.utilities.ServiceProbe;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import io.camunda.zeebe.process.test.filters.RecordStream;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行业洞察 · 关键机会/风险消息外发集成测试（节点 9 + G2「高」分支，需 RabbitMQ）。
 *
 * <p><b>唯一使用测试替身的用例</b>：以内存引擎部署同一生产 BPMN，经 {@link ChatAgent#setOverride}
 * 注入测试桩，令节点 8（{@code locate-opportunity-span}）返回「优先级：高」，从而驱动 G2「高」分支
 * （离线兜底恒为「中」，该分支否则不可达）。生产 Worker 与 BPMN 不变，仅替换**外部 LLM 依赖**。
 *
 * <p><b>环境前置</b>：RabbitMQ 可达 {@code localhost:5672}；未就绪时本类整体跳过。
 */
@SpringBootTest(classes = IndustryInsightApplication.class)
@ActiveProfiles("IndustryInsightBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("行业洞察 关键机会/风险消息外发集成测试（G2「高」分支 + RabbitMQ）")
class IndustryInsightKeyInsightMessageTest {

    private static final Logger log = LoggerFactory.getLogger(IndustryInsightKeyInsightMessageTest.class);

    private static final String PROCESS_ID = "IndustryInsightProcess";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 测试桩统一返回文本：既含「优先级：高」（驱动 G2 高分支），又含五看三定各环节标题
     * （使节点 14 的结构校验通过）。不含「不通过」，故节点 1 判为通过。
     */
    private static final String STUB_TEXT =
            "### 分析结论\n- 优先级：高\n\n看行业\n看市场\n看竞争\n看自己\n看机会\n定战略控制点\n定目标\n定策略\n";

    private static ZeebeTestEngine engine;
    private static ZeebeClient testClient;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @Value("${env-prepare.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${env-prepare.rabbitmq.queue}")
    private String queueName;

    @Value("${env-prepare.rabbitmq.routing-key}")
    private String routingKey;

    @Autowired
    private ChatAgent chatAgent;

    @Autowired
    private IndustryInsightOntologyJobWorker worker;

    @BeforeAll
    static void requireRabbitMq() {
        ServiceProbe.assumeAllReachable("IndustryInsight 消息外发依赖（RabbitMQ）",
                ServiceProbe.LOCALHOST, 5672);
    }

    @DynamicPropertySource
    static void startInMemoryEngine(DynamicPropertyRegistry registry) {
        engine = EngineFactory.create();
        engine.start();
        testClient = engine.createClient();
        BpmnAssert.initRecordStream(RecordStream.of(engine.getRecordStreamSource()));

        String address = normalize(engine.getGatewayAddress());
        log.info("内存引擎已启动 | normalized={}", address);
        registry.add("camunda.client.grpc-address", () -> address);
        registry.add("camunda.client.rest-address", () -> address);
        registry.add("camunda.client.prefer-rest-over-grpc", () -> "false");
    }

    @AfterEach
    void clearStub() {
        chatAgent.setOverride(null);
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

    @Test
    @Order(1)
    @DisplayName("P-1 高优先级触发外发：经 Task_SendMessage，priorityLevel=高，RabbitMQ 收到消息")
    void highPriorityTriggersOutbound() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            purge(mq, queueName);
            chatAgent.setOverride((sys, user) -> STUB_TEXT);

            ProcessInstanceEvent instance = run(passingScenario());
            BpmnAssert.assertThat(instance)
                    .hasPassedElement("Task_SendMessage")
                    .isCompleted()
                    .hasNoIncidents();

            Map<String, Object> out = variables(instance.getProcessInstanceKey());
            assertThat(str(out.get("priorityLevel"))).isEqualTo("高");

            Message received = mq.getRabbitTemplate().receive(queueName, 5000);
            assertThat(received).as("队列应在 5s 内收到关键洞察外发消息").isNotNull();
            String body = new String(received.getBody(), StandardCharsets.UTF_8);
            log.info("📨 收到外发消息={}", body);
            assertThat(body).contains("高").contains("scenarioIri").contains("Scenario_");
        } finally {
            chatAgent.setOverride(null);
            mq.destroy();
        }
    }

    @Test
    @Order(2)
    @DisplayName("B-1 非高优先级不外发：不注入替身（兜底「中」），经 Task_CrossConsensus 且未经过 Task_SendMessage")
    void nonHighSkipsOutbound() {
        chatAgent.setOverride(null);

        ProcessInstanceEvent instance = run(passingScenario());
        BpmnAssert.assertThat(instance)
                .hasPassedElement("Task_CrossConsensus")
                .hasNotPassedElement("Task_SendMessage")
                .isCompleted()
                .hasNoIncidents();

        Map<String, Object> out = variables(instance.getProcessInstanceKey());
        assertThat(str(out.get("priorityLevel"))).isEqualTo("中");
    }

    @Test
    @Order(3)
    @DisplayName("N-1 MQ 不可达降级：投递失败不阻断，outboundMessage 含降级说明，流程仍完成")
    void mqUnreachableDegradesGracefully() throws Exception {
        chatAgent.setOverride((sys, user) -> STUB_TEXT);
        Field field = IndustryInsightOntologyJobWorker.class.getDeclaredField("rabbitMqHandler");
        field.setAccessible(true);
        field.set(worker, new FailingRabbitMqHandler());
        try {
            ProcessInstanceEvent instance = run(passingScenario());
            BpmnAssert.assertThat(instance)
                    .hasPassedElement("Task_SendMessage")
                    .isCompleted()
                    .hasNoIncidents();

            Map<String, Object> out = variables(instance.getProcessInstanceKey());
            assertThat(str(out.get("outboundMessage"))).contains("投递失败（降级，不阻断）");
        } finally {
            field.set(worker, null);
            chatAgent.setOverride(null);
        }
    }

    // ============================ 辅助 ============================

    /** 模拟 MQ 不可达的外发处理器：发送即抛异常，触发 Worker 的降级分支。 */
    static class FailingRabbitMqHandler extends RabbitMqHandler {
        @Override
        public void send(String exchange, String routingKey, Object payload) {
            throw new RuntimeException("simulated MQ down");
        }
    }

    private static Map<String, Object> passingScenario() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业洞察与增长机会分析");
        vars.put("industry", "新能源汽车");
        vars.put("region", "中国");
        vars.put("timeWindow", "未来三年");
        return vars;
    }

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

    private void declareTopology(RabbitMqHandler mq) {
        RabbitAdmin admin = new RabbitAdmin(mq.getRabbitTemplate());
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);
        Queue queue = new Queue(queueName, true, false, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(binding);
        log.info("🏗️ AMQP 拓扑已声明 | Exchange={} | Queue={} | RoutingKey={}", exchangeName, queueName, routingKey);
    }

    private static void purge(RabbitMqHandler mq, String queue) {
        int n = 0;
        while (mq.getRabbitTemplate().receive(queue, 100) != null) {
            n++;
        }
        if (n > 0) {
            log.warn("🧹 测试前清理队列 [{}] 中 {} 条残留消息", queue, n);
        }
    }

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
