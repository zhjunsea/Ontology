package com.ocean.ontologyframework.plumblossom;

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
 * PlumBlossom 告警消息外发集成测试（RabbitMQ）。
 *
 * <p>覆盖「重大吉凶外发」通道：拓扑声明（Exchange/Queue/Binding）、消息投递与报文形状。
 * 应用侧由生产 {@code PlumBlossomOntologyJobWorker.sendAlert} 在 {@code auspiciousness ∈ {大吉,大凶}}
 * 时经 {@link RabbitMqHandler} 发往 {@code plumblossom.alert.exchange}/{@code divination.alert.critical}。
 *
 * <p><b>环境前置</b>：RabbitMQ 可达 {@code localhost:5672}；未就绪时本类整体跳过。
 */
@SpringBootTest(classes = PlumBlossomApplication.class)
@ActiveProfiles("PlumBlossomJunitTest")
@DisplayName("梅花易数 告警消息外发集成测试（RabbitMQ）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlumBlossomSwrlRabbitMqTest {

    private static final Logger log = LoggerFactory.getLogger(PlumBlossomSwrlRabbitMqTest.class);

    @Value("${env-prepare.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${env-prepare.rabbitmq.queue}")
    private String queueName;

    @Value("${env-prepare.rabbitmq.routing-key}")
    private String routingKey;

    @BeforeAll
    static void requireRabbitMq() {
        ServiceProbe.assumeAllReachable("PlumBlossom 告警外发依赖（RabbitMQ）",
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
    void testAlertTopologyDeclaredAndDelivered() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            purge(mq, queueName);

            ObjectMapper mapper = mq.getObjectMapper();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("instanceKey", 2251799813710000L);
            payload.put("subject", "拓扑投递测试");
            payload.put("hexagramName", "乾为天");
            payload.put("auspiciousness", "大吉");
            payload.put("summary", "用生体，进益之喜");

            mq.send(exchangeName, routingKey, payload);
            String sentJson = mapper.writeValueAsString(payload);

            Message received = mq.getRabbitTemplate().receive(queueName, 5000);
            assertNotNull(received, "队列应在 5s 内收到告警消息");
            String body = new String(received.getBody(), StandardCharsets.UTF_8);
            log.info("📨 场景1 收到消息={}", body);

            assertEquals(sentJson, body, "队列消息应与发送 JSON 完全一致");
            assertTrue(body.contains("大吉"), "报文应含 auspiciousness");
        } finally {
            mq.destroy();
        }
    }

    @Test
    @Order(2)
    @DisplayName("P-2 告警报文形状（含 subject/hexagramName/auspiciousness/summary）")
    void testSendAlertPayloadShape() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            purge(mq, queueName);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("subject", "官讼占");
            payload.put("hexagramName", "水天需");
            payload.put("auspiciousness", "大凶");
            payload.put("summary", "用克体，诸事凶");

            mq.send(exchangeName, routingKey, payload);

            Message received = mq.getRabbitTemplate().receive(queueName, 5000);
            assertNotNull(received, "队列应收到告警消息");
            String body = new String(received.getBody(), StandardCharsets.UTF_8);

            assertTrue(body.contains("官讼占"), "应含 subject");
            assertTrue(body.contains("水天需"), "应含 hexagramName");
            assertTrue(body.contains("大凶"), "应含 auspiciousness");
            assertTrue(body.contains("用克体"), "应含 summary");
        } finally {
            mq.destroy();
        }
    }

    @Test
    @Order(3)
    @DisplayName("B-1 非告警路由键不投递到告警队列（绑定语义正确）")
    void testNonAlertRoutingKeyNotDelivered() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            purge(mq, queueName);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("subject", "不应投递");
            payload.put("auspiciousness", "平");
            mq.send(exchangeName, routingKey + ".unbound", payload);

            Message received = mq.getRabbitTemplate().receive(queueName, 1000);
            assertNull(received, "未绑定路由键的消息不应进入告警队列");
        } finally {
            mq.destroy();
        }
    }
}
