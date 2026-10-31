package com.ocean.ontologyframework.sizhubazi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.utilities.RabbitMqHandler;
import com.ocean.utilities.ServiceProbe;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SiZhuBaZi 告警消息外发集成测试（RabbitMQ）。
 *
 * <p>覆盖「重大判定外发」通道：拓扑声明（Exchange/Queue/Binding）、消息投递与报文形状。
 * 应用侧由生产 {@code SiZhuBaZiOntologyJobWorker.sendAlert} 在 {@code alertTriggered=true}
 * （综合判据：大凶 / 寿元关 / 重大疾病，均无数值阈值，㉒）时，经 {@link RabbitMqHandler} 发往
 * {@code sizhubazi.alert.exchange} / routing-key {@code fortune.alert.critical}。
 *
 * <p>SWRL 侧（R19 大凶 / R21 疾病 / R22 外发）经 {@code rules/hasAlertFlag} →
 * {@code AlertDaXiong/AlertShouYuanGuan/AlertJiBing} 落在本体（见本体合规用例），
 * 其触发标志由 {@code aggregateReading} 写回 {@code alertTriggered} 供 G3 网关判定；
 * 本类验证承载该告警的 AMQP 通道端到端可用、报文形状与生产契约一致。
 *
 * <p><b>环境前置</b>：RabbitMQ 可达 {@code localhost:5672}；未就绪时本类整体跳过。
 */
@SpringBootTest(classes = SiZhuBaZiApplication.class)
@ActiveProfiles("SiZhuBaZiJunitTest")
@DisplayName("四柱命理 告警消息外发集成测试（RabbitMQ）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SiZhuBaZiSwrlRabbitMqTest {

    private static final Logger log = LoggerFactory.getLogger(SiZhuBaZiSwrlRabbitMqTest.class);

    @Value("${env-prepare.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${env-prepare.rabbitmq.queue}")
    private String queueName;

    @Value("${env-prepare.rabbitmq.routing-key}")
    private String routingKey;

    @BeforeAll
    static void requireRabbitMq() {
        ServiceProbe.assumeAllReachable("SiZhuBaZi 告警外发依赖（RabbitMQ）",
                ServiceProbe.LOCALHOST, 5672);
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

    @Test
    @Order(1)
    @DisplayName("P-1 告警拓扑声明 + 消息端到端投递（Exchange/Queue/Binding 完整）")
    void t_alert_topology_declared_and_delivered() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            purge(mq, queueName);

            ObjectMapper mapper = mq.getObjectMapper();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("instanceKey", 2251799813710000L);
            payload.put("chartLevel", "贫贱格（格破身弱，用神受制）");
            payload.put("reading", "【四柱命理命书】命中大凶判据");
            payload.put("chartBase", "SOLAR=1990-05-15T08:30;GENDER=男;SUBJECT=男命;MODE=单命");
            payload.put("timestamp", "2026-10-29T00:00:00Z");

            mq.send(exchangeName, routingKey, payload);
            String sentJson = mapper.writeValueAsString(payload);

            Message received = mq.getRabbitTemplate().receive(queueName, 5000);
            assertNotNull(received, "队列应在 5s 内收到告警消息");
            String body = new String(received.getBody(), StandardCharsets.UTF_8);
            log.info("📨 场景1 收到消息={}", body);

            assertEquals(sentJson, body, "队列消息应与发送 JSON 完全一致");
            assertTrue(body.contains("贫贱"), "报文应含 chartLevel 中的告警判据");
        } finally {
            mq.destroy();
        }
    }

    @Test
    @Order(2)
    @DisplayName("P-2 告警报文形状（含 chartLevel/reading/chartBase/timestamp）")
    void t_send_alert_payload_shape() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            purge(mq, queueName);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chartLevel", "贫贱格（格破身弱，用神受制）");
            payload.put("reading", "【四柱命理命书】寿元关提示");
            payload.put("chartBase", "SOLAR=1970-01-01T23:30;GENDER=女;SUBJECT=女命;MODE=单命");
            payload.put("timestamp", "2026-10-29T00:00:01Z");

            mq.send(exchangeName, routingKey, payload);

            Message received = mq.getRabbitTemplate().receive(queueName, 5000);
            assertNotNull(received, "队列应收到告警消息");
            String body = new String(received.getBody(), StandardCharsets.UTF_8);

            assertTrue(body.contains("chartLevel"), "应含 chartLevel");
            assertTrue(body.contains("reading"), "应含 reading");
            assertTrue(body.contains("chartBase"), "应含 chartBase");
            assertTrue(body.contains("timestamp"), "应含 timestamp");
        } finally {
            mq.destroy();
        }
    }

    @Test
    @Order(3)
    @DisplayName("B-1 非告警路由键不投递到告警队列（绑定语义正确）")
    void t_non_alert_routing_key_not_delivered() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            purge(mq, queueName);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chartLevel", "平常格");
            payload.put("reading", "不应投递");
            mq.send(exchangeName, routingKey + ".unbound", payload);

            Message received = mq.getRabbitTemplate().receive(queueName, 1000);
            assertNull(received, "未绑定路由键的消息不应进入告警队列");
        } finally {
            mq.destroy();
        }
    }
}
