package com.ocean.ontologyframework.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import com.ocean.utilities.RabbitMqHandler;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ExampleApplication.class)
@ActiveProfiles("ExampleJunitTest")
@DisplayName("示例模块 SWRL 规则触发 + RabbitMQ 消息外发集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExampleSwrlRabbitMqTest {

    private static final Logger log = LoggerFactory.getLogger(ExampleSwrlRabbitMqTest.class);

    private static BackendService backendService;
    private Set<OWLAxiom> fullBaselineSnapshot;

    @BeforeAll
    static void setUp(@Autowired Environment env) throws Exception {
        log.info("=== 初始化示例模块 SWRL+RabbitMQ 测试环境 ===");
        String obdaPath = env.getProperty("ontology.obda-path");
        String tboxFile = env.getProperty("ontology.main-path");
        String obdaPropertiesPath = env.getProperty("ontology.obda-properties-path");
        try {
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
        } catch (IllegalStateException alreadyInitialized) {
            log.warn("⚠️ OBDAHandler 已初始化，复用现有实例");
        }
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        backendService = BackendService.getInstance(tboxFile, obdaHandler);
        assertNotNull(backendService, "BackendService 初始化失败");
    }

    @BeforeEach
    void snapshotFullBaseline() {
        OWLOntology ontology = backendService.getTBoxOntology();
        fullBaselineSnapshot = Collections.unmodifiableSet(
                new HashSet<>(ontology.getAxioms())
        );

        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        reasoner.flush();
        log.info("📸 全量快照已保存，基线公理数: {}", fullBaselineSnapshot.size());
    }

    @AfterEach
    void restoreOntologyAndReasoner() {
        if (fullBaselineSnapshot == null) return;

        try {
            OWLOntology ontology = backendService.getTBoxOntology();
            Set<OWLAxiom> currentAxioms = ontology.getAxioms();

            if (currentAxioms.size() == fullBaselineSnapshot.size()
                    && currentAxioms.containsAll(fullBaselineSnapshot)) {
                log.info("✅ 本体无变更，无需恢复");
                return;
            }

            log.warn("⚠️ 检测到本体漂移，正在原子恢复...");
            OWLOntologyManager manager = ontology.getOWLOntologyManager();

            ChangeApplied removeResult = manager.removeAxioms(ontology, currentAxioms);
            if (removeResult != ChangeApplied.SUCCESSFULLY) {
                throw new IllegalStateException("批量移除公理失败: " + removeResult);
            }

            ChangeApplied addResult = manager.addAxioms(ontology, fullBaselineSnapshot);
            if (addResult != ChangeApplied.SUCCESSFULLY) {
                throw new IllegalStateException("批量恢复基线公理失败: " + addResult);
            }

            OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
            reasoner.flush();

            log.info("🧹 本体及推理器已恢复至基线状态，当前公理数: {}", ontology.getAxiomCount());

        } catch (Exception e) {
            log.error("❌ 本体恢复失败！", e);
            throw new RuntimeException("测试隔离失败", e);
        } finally {
            fullBaselineSnapshot = null;
        }
    }

    // ============================================================
    // 场景1: SwrlRuleTriggerListener 通用框架 - 低库存自动触发RabbitMQ消息
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("场景1: SwrlRuleTriggerListener 回调触发 - 低库存自动发送RabbitMQ消息")
    void testSwrlRuleTriggerListenerCallback() throws Exception {
        RabbitMqHandler mqHandler = new RabbitMqHandler();
        ObjectMapper mapper = mqHandler.getObjectMapper();

        String exchangeName = "example.low-stock.exchange";
        String routingKey = "low.stock.alert";

        Map<String, Map<String, String>> iriToPropsCache = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMsgRef = new AtomicReference<>();

        String typeNS = "http://example.org/pizza/components/";
        String indNS = "http://example.org/pizza/components-abox/";
        String targetClassIri = typeNS + "LowStockCrust";

        SwrlRuleTriggerListener<String> listener = new SwrlRuleTriggerListener<>(
                new SwrlRuleTriggerListener.Config<>(
                        targetClassIri,
                        instanceIri -> {
                            log.info("[回调执行] 检测到低库存推导: {}", instanceIri);
                            Map<String, String> props = iriToPropsCache.get(instanceIri);
                            if (props != null) {
                                try {
                                    Map<String, Object> mqPayload = new LinkedHashMap<>();
                                    mqPayload.put("name", props.get(typeNS + "name"));
                                    mqPayload.put("type", props.get(typeNS + "type"));
                                    mqPayload.put("supplier", props.get(typeNS + "supplier"));
                                    mqPayload.put("stockQuantity", props.get(typeNS + "stockQuantity"));
                                    mqPayload.put("price", props.get(typeNS + "price"));

                                    mqHandler.send(exchangeName, routingKey, mqPayload);

                                    String jsonMessage = mapper.writeValueAsString(mqPayload);
                                    receivedMsgRef.set(jsonMessage);
                                    log.info("[MQ发送成功] {}", jsonMessage);
                                } catch (Exception e) {
                                    log.error("[MQ发送失败]", e);
                                }
                            } else {
                                log.warn("[回调警告] 未在缓存中找到IRI对应的属性: {}", instanceIri);
                            }
                            latch.countDown();
                        },
                        String.class
                ),
                backendService
        );

        try {
            listener.start();
            log.info("🚀 场景1: Listener已启动，监控目标={}", targetClassIri);

            String testName = "ListenerTriggerTest_" + System.currentTimeMillis();
            String fullIri = indNS + testName;

            Map<String, String> lowStockProperties = new LinkedHashMap<>();
            lowStockProperties.put(typeNS + "name", testName);
            lowStockProperties.put(typeNS + "type", "NeapolitanCrust");
            lowStockProperties.put(typeNS + "supplier", "ListenerTestSupplier");
            lowStockProperties.put(typeNS + "price", "8.00");
            lowStockProperties.put(typeNS + "stockQuantity", "3");

            iriToPropsCache.put(fullIri, lowStockProperties);

            GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
            Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(fullIri, lowStockProperties);

            InsertService inserter = new InsertService(backendService);
            assertDoesNotThrow(
                    () -> inserter.insertComponentAutoSplit(lowStockProperties, tempAxioms),
                    "低库存组件插入不应失败"
            );
            log.info("📝 场景1: 低库存组件已写入 name={} | stock=3", testName);

            boolean callbackExecuted = latch.await(10, TimeUnit.SECONDS);

            assertTrue(callbackExecuted, "SwrlRuleTriggerListener 应在10秒内触发回调");
            assertNotNull(receivedMsgRef.get(), "应成功生成并发送MQ消息");

            String sentMsg = receivedMsgRef.get();
            assertTrue(sentMsg.contains(testName), "消息应包含name");
            assertTrue(sentMsg.contains("NeapolitanCrust"), "消息应包含type");
            assertTrue(sentMsg.contains("ListenerTestSupplier"), "消息应包含supplier");
            assertTrue(sentMsg.contains("3"), "消息应包含stockQuantity");
            assertTrue(sentMsg.contains("8.00"), "消息应包含price");

            log.info("✅ 场景1通过: MQ消息内容={}", sentMsg);

        } finally {
            listener.shutdown();
            mqHandler.destroy();
            iriToPropsCache.clear();
            log.info("🧹 场景1: 资源已清理");
        }
    }

    // ============================================================
    // 场景2: SwrlRuleTriggerListener 端到端验证 - Exchange/Queue/Binding 完整投递
    // ============================================================
    @Test
    @Order(2)
    @DisplayName("场景2: SwrlRuleTriggerListener 端到端验证 - Exchange/Queue/Binding 完整投递")
    void testSwrlRuleTriggerEndToEndDelivery() throws Exception {
        RabbitMqHandler mqHandler = new RabbitMqHandler();
        ObjectMapper mapper = mqHandler.getObjectMapper();

        String exchangeName = "example.low-stock.exchange";
        String routingKey = "low.stock.alert";
        String queueName = "exampleQueue";

        RabbitAdmin admin = new RabbitAdmin(mqHandler.getRabbitTemplate());
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);
        Queue queue = new Queue(queueName, true, false, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);

        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(binding);
        log.info("🏗️ 场景2: AMQP 拓扑已声明 | Exchange={} | Queue={} | RoutingKey={}",
                exchangeName, queueName, routingKey);

        int purgedCount = 0;
        Message staleMsg;
        while ((staleMsg = mqHandler.getRabbitTemplate().receive(queueName, 100)) != null) {
            purgedCount++;
        }
        if (purgedCount > 0) {
            log.warn("🧹 场景2: 测试前清理队列 [{}] 中 {} 条残留消息", queueName, purgedCount);
        } else {
            log.info("✅ 场景2: 队列 [{}] 初始状态为空，无需清理", queueName);
        }

        Map<String, Map<String, String>> iriToPropsCache = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sentJsonRef = new AtomicReference<>();

        String typeNS = "http://example.org/pizza/components/";
        String indNS = "http://example.org/pizza/components-abox/";
        String targetClassIri = typeNS + "LowStockCrust";

        SwrlRuleTriggerListener<String> listener = new SwrlRuleTriggerListener<>(
                new SwrlRuleTriggerListener.Config<>(
                        targetClassIri,
                        instanceIri -> {
                            log.info("[回调执行] 检测到低库存推导: {}", instanceIri);
                            Map<String, String> props = iriToPropsCache.get(instanceIri);
                            if (props != null) {
                                try {
                                    Map<String, Object> mqPayload = new LinkedHashMap<>();
                                    mqPayload.put("name", props.get(typeNS + "name"));
                                    mqPayload.put("type", props.get(typeNS + "type"));
                                    mqPayload.put("supplier", props.get(typeNS + "supplier"));
                                    mqPayload.put("stockQuantity", props.get(typeNS + "stockQuantity"));
                                    mqPayload.put("price", props.get(typeNS + "price"));

                                    mqHandler.send(exchangeName, routingKey, mqPayload);

                                    sentJsonRef.set(mapper.writeValueAsString(mqPayload));
                                    log.info("[MQ发送成功] {}", sentJsonRef.get());
                                } catch (Exception e) {
                                    log.error("[MQ发送失败]", e);
                                }
                            } else {
                                log.warn("[回调警告] 未在缓存中找到IRI对应的属性: {}", instanceIri);
                            }
                            latch.countDown();
                        },
                        String.class
                ),
                backendService
        );

        try {
            listener.start();
            log.info("🚀 场景2: Listener已启动，监控目标={}", targetClassIri);

            String testName = "E2EDeliveryTest_" + System.currentTimeMillis();
            String fullIri = indNS + testName;

            Map<String, String> lowStockProperties = new LinkedHashMap<>();
            lowStockProperties.put(typeNS + "name", testName);
            lowStockProperties.put(typeNS + "type", "NeapolitanCrust");
            lowStockProperties.put(typeNS + "supplier", "E2ETestSupplier");
            lowStockProperties.put(typeNS + "price", "9.50");
            lowStockProperties.put(typeNS + "stockQuantity", "2");

            iriToPropsCache.put(fullIri, lowStockProperties);

            GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
            Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(fullIri, lowStockProperties);

            InsertService inserter = new InsertService(backendService);
            assertDoesNotThrow(
                    () -> inserter.insertComponentAutoSplit(lowStockProperties, tempAxioms),
                    "低库存组件插入不应失败"
            );
            log.info("📝 场景2: 低库存组件已写入 name={} | stock=2", testName);

            boolean callbackExecuted = latch.await(10, TimeUnit.SECONDS);
            assertTrue(callbackExecuted, "SwrlRuleTriggerListener 应在10秒内触发回调");
            assertNotNull(sentJsonRef.get(), "应成功生成并发送MQ消息");

            Message receivedMessage = mqHandler.getRabbitTemplate().receive(queueName, 5000);
            assertNotNull(receivedMessage, "exampleQueue 中应存在至少一条消息");

            String receivedBody = new String(receivedMessage.getBody(), StandardCharsets.UTF_8);
            log.info("📨 场景2: 从队列消费到消息={}", receivedBody);

            assertEquals(sentJsonRef.get(), receivedBody, "队列中的消息应与发送的JSON完全一致");
            assertTrue(receivedBody.contains(testName), "消息应包含name");
            assertTrue(receivedBody.contains("NeapolitanCrust"), "消息应包含type");
            assertTrue(receivedBody.contains("E2ETestSupplier"), "消息应包含supplier");
            assertTrue(receivedBody.contains("2"), "消息应包含stockQuantity");
            assertTrue(receivedBody.contains("9.50"), "消息应包含price");

            assertEquals(MessageProperties.CONTENT_TYPE_JSON,
                    receivedMessage.getMessageProperties().getContentType(),
                    "消息的 content_type 应为 application/json");

            log.info("✅ 场景2通过: 端到端投递验证成功 | Queue={} | MsgLength={}", queueName, receivedBody.length());

            verifyPublishCountViaApi(queueName);

        } finally {
            listener.shutdown();

            try {
                admin.deleteExchange(exchangeName);
                log.info("🧹 场景2: Exchange 已清理，Queue[{}] 已保留", queueName);
            } catch (Exception e) {
                log.warn("⚠️ Exchange 清理失败（可忽略）", e);
            }

            mqHandler.destroy();
            iriToPropsCache.clear();
            log.info("🧹 场景2: 本地资源已清理");
        }
    }

    private void verifyPublishCountViaApi(String queueName) throws Exception {
        RestTemplate restTemplate = new RestTemplate();

        String uriString = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("http://localhost:15672/api/queues/{vhost}/{queue}")
                .build(false)
                .expand("%2F", queueName)
                .toUriString();

        java.net.URI uri = java.net.URI.create(uriString);

        log.info("🔗 [API验证] 请求URL: {}", uri);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("guest", "guest");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Map<String, Object> response = null;
        Map<String, Object> messageStats = null;
        long publishCount = 0L;
        int attempt = 0;
        long deadline = System.currentTimeMillis() + 10_000L;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            ).getBody();

            response = resp;

            if (resp != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) resp.get("message_stats");
                if (stats != null) {
                    messageStats = stats;
                    publishCount = ((Number) stats.getOrDefault("publish", 0)).longValue();
                    if (publishCount > 0) {
                        break;
                    }
                }
            }

            log.info("⏳ [API验证] 第 {} 次查询 message_stats 尚未就绪，500ms 后重试", attempt);
            Thread.sleep(500L);
        }

        assertNotNull(response, "RabbitMQ API 应返回有效响应");
        assertNotNull(messageStats, "API 响应应包含 message_stats");

        log.info("📊 [API验证] {} 的历史 Publish 累计计数: {}（第 {} 次查询命中）", queueName, publishCount, attempt);

        assertTrue(publishCount > 0,
                String.format("RabbitMQ API 确认 %s 应有 Publish 记录，实际计数=%d", queueName, publishCount));
    }
}
