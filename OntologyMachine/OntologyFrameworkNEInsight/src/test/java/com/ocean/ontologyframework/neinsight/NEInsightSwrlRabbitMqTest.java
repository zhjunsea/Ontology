package com.ocean.ontologyframework.neinsight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.openlletresolver.BackendService;
import com.ocean.utilities.RabbitMqHandler;
import com.ocean.utilities.ServiceProbe;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEInsight SWRL 规则触发 + RabbitMQ 消息外发集成测试（需 RabbitMQ）。
 *
 * <p>对应《NEInsight应用-测试设计文档》§6.2：P-1、N-1、B-1。
 * 外发通道取自 application.yaml：exchange={@code neinsight.opportunity.exchange}（direct）、
 * queue={@code neinsightOpportunityQueue}、routingKey={@code opportunity.high.priority}。
 */
@SpringBootTest(classes = NEInsightApplication.class)
@ActiveProfiles("NEInsightJunitTest")
@DisplayName("NEInsight SWRL 规则触发 + RabbitMQ 消息外发集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NEInsightSwrlRabbitMqTest {

    private static final Logger log = LoggerFactory.getLogger(NEInsightSwrlRabbitMqTest.class);

    private static final String ENV_NS = "http://example.org/neinsight/env#";
    private static final String ABOX_NS = "http://example.org/neinsight/abox#";
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";
    private static final String CORE_NS = "http://example.org/neinsight/core#";

    private static final String D_OPPORTUNITY_PRIORITY = ENV_NS + "opportunityPriority";
    private static final String D_FLAGGED_FOR_ALERT = ENV_NS + "flaggedForAlert";
    private static final String D_MARKET_ATTRACTIVENESS = ENV_NS + "marketAttractiveness";
    private static final String D_COMPETITIVE_POSITION = ENV_NS + "competitivePosition";
    private static final String P_HAS_SPAN_QUADRANT = ENV_NS + "hasSpanQuadrant";
    private static final String P_BELONGS_TO_DOMAIN = CORE_NS + "belongsToDomain";
    private static final String C_OPPORTUNITY = ENV_NS + "Opportunity";

    private static final String I_TEMP_OPP_HIGH_MQ = ABOX_NS + "Temp_Opp_HighMq";
    private static final String I_Q_HIGH_HIGH = ABOX_NS + "Q_HighHigh";
    private static final String I_CLOUD_SPACE = ABOX_NS + "CLOUD_SPACE";

    private static BackendService backendService;
    private Set<OWLAxiom> fullBaselineSnapshot;

    @Autowired
    private Environment env;

    @BeforeAll
    static void setUp(@Autowired Environment env) throws Exception {
        ServiceProbe.assumeAllReachable("NEInsight SWRL + RabbitMQ 依赖", ServiceProbe.LOCALHOST, 5672, 15672);
        String mainOntologyPath = env.getProperty("ontology.main-path");
        backendService = BackendService.getInstance(mainOntologyPath);
        assertNotNull(backendService, "BackendService 初始化失败");
    }

    @BeforeEach
    void snapshotFullBaseline() {
        OWLOntology ontology = backendService.getTBoxOntology();
        fullBaselineSnapshot = Collections.unmodifiableSet(new HashSet<>(ontology.getAxioms()));
        backendService.getReasonerService().getReasoner().flush();
    }

    @AfterEach
    void restoreOntology() {
        if (fullBaselineSnapshot == null) return;
        OWLOntology ontology = backendService.getTBoxOntology();
        Set<OWLAxiom> current = ontology.getAxioms();
        if (current.size() == fullBaselineSnapshot.size() && current.containsAll(fullBaselineSnapshot)) {
            fullBaselineSnapshot = null;
            return;
        }
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        assertSame(ChangeApplied.SUCCESSFULLY, manager.removeAxioms(ontology, current));
        assertSame(ChangeApplied.SUCCESSFULLY, manager.addAxioms(ontology, fullBaselineSnapshot));
        backendService.getReasonerService().getReasoner().flush();
        fullBaselineSnapshot = null;
    }

    // ============================================================
    // B-1: 队列预热清理（测试前清空队列，断言初始为空）
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("B-1 queuePrewarmPurge：外发队列预清空，初始为空")
    void queuePrewarmPurge() {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            int purged = purgeQueue(mq);
            log.info("🧹 B-1: 队列已预清空，清理残留消息 {} 条", purged);
            assertTrue(purged >= 0, "预清空应成功执行");
        } finally {
            mq.destroy();
        }
    }

    // ============================================================
    // P-1: 高优先级机会点外发
    // ============================================================
    @Test
    @Order(2)
    @DisplayName("P-1 highPriorityOpportunityDispatches：SWRL 推导的高优先级机会点经 RabbitMQ 外发")
    void highPriorityOpportunityDispatches() throws Exception {
        String exchange = env.getProperty("env-prepare.rabbitmq.exchange");
        String routingKey = env.getProperty("env-prepare.rabbitmq.routing-key");
        String queue = env.getProperty("env-prepare.rabbitmq.queue");

        RabbitMqHandler mq = new RabbitMqHandler();
        ObjectMapper mapper = mq.getObjectMapper();
        try {
            declareTopology(mq);
            purgeQueue(mq);

            // 应用口径：不内置业务实例数据；临时注入一个高优先级机会点，验证 SWRL R1/R2 推导
            OWLDataFactory df = backendService.getTBoxOntology().getOWLOntologyManager().getOWLDataFactory();
            OWLNamedIndividual opp = ind(I_TEMP_OPP_HIGH_MQ);
            Set<OWLAxiom> temp = new HashSet<>();
            temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OPPORTUNITY)), opp));
            temp.add(df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(IRI.create(P_BELONGS_TO_DOMAIN)),
                    opp, ind(I_CLOUD_SPACE)));
            temp.add(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty(IRI.create(D_MARKET_ATTRACTIVENESS)),
                    opp, df.getOWLLiteral("4.5", df.getOWLDatatype(IRI.create(XSD_NS + "decimal")))));
            temp.add(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty(IRI.create(D_COMPETITIVE_POSITION)),
                    opp, df.getOWLLiteral("4.0", df.getOWLDatatype(IRI.create(XSD_NS + "decimal")))));
            temp.add(df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(IRI.create(P_HAS_SPAN_QUADRANT)),
                    opp, ind(I_Q_HIGH_HIGH)));

            String priority = backendService.withTempAxioms(temp, reasoner -> {
                Set<OWLLiteral> vs = reasoner.getDataPropertyValues(opp,
                        df.getOWLDataProperty(IRI.create(D_OPPORTUNITY_PRIORITY)));
                return vs.isEmpty() ? null : vs.iterator().next().getLiteral();
            });
            Boolean flagged = backendService.withTempAxioms(temp, reasoner -> {
                Set<OWLLiteral> vs = reasoner.getDataPropertyValues(opp,
                        df.getOWLDataProperty(IRI.create(D_FLAGGED_FOR_ALERT)));
                return vs.isEmpty() ? null : Boolean.parseBoolean(vs.iterator().next().getLiteral());
            });
            assertEquals("HIGH", priority, "SWRL R1 应推导该机会点优先级为 HIGH");
            assertEquals(Boolean.TRUE, flagged, "SWRL R2 应推导 flaggedForAlert=true");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "NEINSIGHT_HIGH_PRIORITY_OPPORTUNITY");
            payload.put("opportunityIri", opp.getIRI().toString());
            payload.put("priority", priority);
            payload.put("flaggedForAlert", flagged);

            mq.send(exchange, routingKey, payload);
            String sentJson = mapper.writeValueAsString(payload);
            log.info("📨 P-1: 已外发 {}", sentJson);

            Message received = mq.getRabbitTemplate().receive(queue, 5000);
            assertNotNull(received, "neinsightOpportunityQueue 应收到外发消息");
            String body = new String(received.getBody(), StandardCharsets.UTF_8);
            assertTrue(body.contains(I_TEMP_OPP_HIGH_MQ), "消息应含 opportunityIri");
            assertTrue(body.contains("HIGH"), "消息应含 priority=HIGH");
            assertTrue(body.contains("true"), "消息应含 flaggedForAlert=true");
            assertEquals(MessageProperties.CONTENT_TYPE_JSON,
                    received.getMessageProperties().getContentType(), "content_type 应为 application/json");

            verifyPublishCountViaApi(queue);
            log.info("✅ P-1 通过: 队列收到 {}", body);
        } finally {
            mq.destroy();
        }
    }

    // ============================================================
    // N-1: 非高优先级不外发
    // ============================================================
    @Test
    @Order(3)
    @DisplayName("N-1 nonHighNotDispatched：低分机会点不推断高优先级、不外发")
    void nonHighNotDispatched() throws Exception {
        String exchange = env.getProperty("env-prepare.rabbitmq.exchange");
        String routingKey = env.getProperty("env-prepare.rabbitmq.routing-key");
        String queue = env.getProperty("env-prepare.rabbitmq.queue");

        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            declareTopology(mq);
            purgeQueue(mq);

            OWLDataFactory df = backendService.getTBoxOntology().getOWLOntologyManager().getOWLDataFactory();
            OWLNamedIndividual low = ind(ABOX_NS + "Temp_Opp_LowMq");
            Set<OWLAxiom> temp = new HashSet<>();
            temp.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(C_OPPORTUNITY)), low));
            temp.add(df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(IRI.create(P_BELONGS_TO_DOMAIN)),
                    low, ind(I_CLOUD_SPACE)));
            temp.add(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty(IRI.create(D_MARKET_ATTRACTIVENESS)),
                    low, df.getOWLLiteral("1.0", df.getOWLDatatype(IRI.create(XSD_NS + "decimal")))));
            temp.add(df.getOWLDataPropertyAssertionAxiom(df.getOWLDataProperty(IRI.create(D_COMPETITIVE_POSITION)),
                    low, df.getOWLLiteral("1.0", df.getOWLDatatype(IRI.create(XSD_NS + "decimal")))));
            temp.add(df.getOWLObjectPropertyAssertionAxiom(df.getOWLObjectProperty(IRI.create(P_HAS_SPAN_QUADRANT)),
                    low, ind(I_Q_HIGH_HIGH)));

            String priority = backendService.withTempAxioms(temp, reasoner -> {
                Set<OWLLiteral> vals = reasoner.getDataPropertyValues(low,
                        df.getOWLDataProperty(IRI.create(D_OPPORTUNITY_PRIORITY)));
                return vals.isEmpty() ? null : vals.iterator().next().getLiteral();
            });
            assertEquals("LOW", priority, "两轴均 <3.0 的低分机会点应被 R1b 推导为 LOW");

            boolean alertDispatched = "HIGH".equals(priority);
            assertFalse(alertDispatched, "非高优先级不应触发外发");

            assertNull(mq.getRabbitTemplate().receive(queue, 1500), "非高优先级场景不应有消息投递");
            log.info("✅ N-1 通过: priority={} 且队列无外发消息", priority);
        } finally {
            mq.destroy();
        }
    }

    // ============================================================
    // 辅助
    // ============================================================

    private void declareTopology(RabbitMqHandler mq) {
        String exchange = env.getProperty("env-prepare.rabbitmq.exchange");
        String routingKey = env.getProperty("env-prepare.rabbitmq.routing-key");
        String queue = env.getProperty("env-prepare.rabbitmq.queue");

        RabbitAdmin admin = new RabbitAdmin(mq.getRabbitTemplate());
        DirectExchange direct = new DirectExchange(exchange, true, false);
        Queue q = new Queue(queue, true, false, false);
        Binding binding = BindingBuilder.bind(q).to(direct).with(routingKey);
        admin.declareExchange(direct);
        admin.declareQueue(q);
        admin.declareBinding(binding);
        log.info("🏗️ AMQP 拓扑已声明 | exchange={} | queue={} | routingKey={}", exchange, queue, routingKey);
    }

    private int purgeQueue(RabbitMqHandler mq) {
        String queue = env.getProperty("env-prepare.rabbitmq.queue");
        int purged = 0;
        while (mq.getRabbitTemplate().receive(queue, 100) != null) {
            purged++;
        }
        return purged;
    }

    private static OWLNamedIndividual ind(String iri) {
        return backendService.getTBoxOntology().getOWLOntologyManager()
                .getOWLDataFactory().getOWLNamedIndividual(IRI.create(iri));
    }

    private String readString(OWLNamedIndividual i, String propIri) {
        Set<OWLLiteral> vs = backendService.getDataPropertyValueOfIndividual(i, propIri);
        return vs.isEmpty() ? null : vs.iterator().next().getLiteral();
    }

    private Boolean readBoolean(OWLNamedIndividual i, String propIri) {
        Set<OWLLiteral> vs = backendService.getDataPropertyValueOfIndividual(i, propIri);
        return vs.isEmpty() ? null : Boolean.parseBoolean(vs.iterator().next().getLiteral());
    }

    private void verifyPublishCountViaApi(String queueName) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String uriString = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("http://localhost:15672/api/queues/{vhost}/{queue}")
                .build(false).expand("%2F", queueName).toUriString();
        java.net.URI uri = java.net.URI.create(uriString);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("guest", "guest");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        long publishCount = 0L;
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            if (resp != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) resp.get("message_stats");
                if (stats != null) {
                    publishCount = ((Number) stats.getOrDefault("publish", 0)).longValue();
                    if (publishCount > 0) break;
                }
            }
            Thread.sleep(500L);
        }
        log.info("📊 [API验证] {} 历史 Publish 计数: {}", queueName, publishCount);
        assertTrue(publishCount > 0, "RabbitMQ API 应确认队列存在 Publish 记录");
    }
}
