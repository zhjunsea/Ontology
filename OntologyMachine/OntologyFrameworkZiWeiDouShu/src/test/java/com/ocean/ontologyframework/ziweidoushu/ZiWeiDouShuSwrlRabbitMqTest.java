package com.ocean.ontologyframework.ziweidoushu;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.SwrlRuleTriggerListener;
import com.ocean.utilities.RabbitMqHandler;
import com.ocean.utilities.ServiceProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 · SWRL 规则触发 + RabbitMQ 告警消息外发集成测试（第 8 步）。
 *
 * <p>验证 SWRL 良构子集 R21（{@code chart:NatalChart ∧ rules:hasAlertFlag → rules:alertTriggered=true}）
 * 经 {@link SwrlRuleTriggerListener} 命中后，向 {@code ziweidoushu.alert.exchange}(direct) /
 * routing-key {@code ziweidoushu.alert} / queue {@code ziweidoushuQueue} 投递告警消息的端到端链路。
 * 环境不可达（RabbitMQ/Ontop/MySQL）时经 {@link ServiceProbe} 自动跳过。
 *
 * <p>用例清单见《ZiWeiDouShu 应用-测试设计文档》§6.4（2 项：P-1 + N-1）。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@DisplayName("紫微斗数 · SWRL 规则触发 + RabbitMQ 告警消息外发集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZiWeiDouShuSwrlRabbitMqTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuSwrlRabbitMqTest.class);

    private static final String CHART = "http://example.org/ziweidoushu/chart#";
    private static final String RULES = "http://example.org/ziweidoushu/rules#";
    private static final String ABOX = "http://example.org/ziweidoushu/test-abox#";

    private static final String EXCHANGE = "ziweidoushu.alert.exchange";
    private static final String ROUTING_KEY = "ziweidoushu.alert";
    private static final String QUEUE = "ziweidoushuQueue";

    private static BackendService backendService;
    private Set<OWLAxiom> injected;

    @BeforeAll
    static void setUp(@Autowired Environment env) throws Exception {
        ServiceProbe.assumeAllReachable("紫微斗数 SWRL + RabbitMQ 依赖（RabbitMQ/Ontop/MySQL）",
                ServiceProbe.LOCALHOST, 5672, 8080, 3306);
        log.info("=== 初始化紫微斗数 SWRL + RabbitMQ 测试环境 ===");
        String obdaPath = env.getProperty("ontology.obda-path");
        String mainPath = env.getProperty("ontology.main-path");
        String obdaPropertiesPath = env.getProperty("ontology.obda-properties-path");
        try {
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
        } catch (IllegalStateException alreadyInitialized) {
            log.warn("⚠️ OBDAHandler 已初始化，复用现有实例");
        }
        backendService = BackendService.getInstance(mainPath, OBDAHandler.getInstance());
        assertThat(backendService).as("BackendService 初始化失败").isNotNull();
    }

    @BeforeEach
    void newInjectionBuffer() {
        injected = new LinkedHashSet<>();
    }

    @AfterEach
    void rollbackInjectedAxioms() {
        if (injected != null && !injected.isEmpty()) {
            OWLOntology ontology = backendService.getTBoxOntology();
            ontology.getOWLOntologyManager().removeAxioms(ontology, injected);
            backendService.getReasonerService().getReasoner().flush();
            log.info("🧹 已回滚 {} 条临时公理", injected.size());
        }
        injected = null;
    }

    // ============================ P-1 ============================
    @Test
    @Order(1)
    @DisplayName("P-1 R21 命中：SwrlRuleTriggerListener 触发回调，告警消息投递至 ziweidoushuQueue")
    void swrlAlertTriggersRabbitMqMessage() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        declareTopology(mq);
        purgeQueue(mq);

        OWLDataFactory df = backendService.getDataFactory();
        OWLClass natalChart = df.getOWLClass(IRI.create(CHART + "NatalChart"));
        OWLClass alertFlag = df.getOWLClass(IRI.create(RULES + "AlertFlag"));
        OWLObjectProperty hasAlertFlag = df.getOWLObjectProperty(IRI.create(RULES + "hasAlertFlag"));
        OWLDataProperty alertType = df.getOWLDataProperty(IRI.create(RULES + "alertType"));
        OWLDataProperty alertTriggered = df.getOWLDataProperty(IRI.create(RULES + "alertTriggered"));

        String chartIri = ABOX + "Chart_SwrlP1_" + System.currentTimeMillis();
        String alertIri = ABOX + "Alert_SwrlP1_" + System.currentTimeMillis();
        OWLNamedIndividual chart = df.getOWLNamedIndividual(IRI.create(chartIri));
        OWLNamedIndividual alert = df.getOWLNamedIndividual(IRI.create(alertIri));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean sent = new AtomicBoolean(false);

        SwrlRuleTriggerListener<String> listener = new SwrlRuleTriggerListener<>(
                new SwrlRuleTriggerListener.Config<>(
                        RULES + "AlertFlag",
                        instanceIri -> {
                            try {
                                OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
                                reasoner.flush();
                                OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(instanceIri));
                                Set<OWLLiteral> vals = reasoner.getDataPropertyValues(ind, alertTriggered);
                                boolean hit = vals.stream().anyMatch(l -> Boolean.parseBoolean(l.getLiteral()));
                                log.info("[回调] 告警实例={} | R21 alertTriggered={}", instanceIri, hit);
                                if (hit) {
                                    Map<String, Object> payload = new LinkedHashMap<>();
                                    payload.put("alertInstance", instanceIri);
                                    payload.put("alertType", "大凶");
                                    payload.put("alertTriggered", true);
                                    mq.send(EXCHANGE, ROUTING_KEY, payload);
                                    sent.set(true);
                                    log.info("[MQ 发送成功] {}", payload);
                                }
                            } catch (Exception e) {
                                log.error("[回调异常]", e);
                            } finally {
                                latch.countDown();
                            }
                        },
                        String.class),
                backendService);

        try {
            listener.start();
            log.info("🚀 P-1 Listener 已启动，监控类={}", RULES + "AlertFlag");

            // 分两批注入：批1 先落「命盘/告警实例/告警类型」，flush 后再落批2「hasAlertFlag 关联」。
            // 原因：SwrlRuleTriggerListener 在收到变更广播时会同步重入 flush 取增量快照；若四条公理同批，
            // 该次重入可能取到推理器尚未同步的旧快照（仍为 3 个 AlertFlag 实例），导致新增告警被漏判。
            // 分两批可确保批2 变更发生时批1 的实例已在推理器中生效，从而稳定命中 R21（与生产 InsertService
            // 的「先 Add 后 flush 再 Remove」多事件模式一致）。
            Set<OWLAxiom> batch1 = new LinkedHashSet<>();
            batch1.add(df.getOWLClassAssertionAxiom(natalChart, chart));
            batch1.add(df.getOWLClassAssertionAxiom(alertFlag, alert));
            batch1.add(df.getOWLDataPropertyAssertionAxiom(alertType, alert, "大凶"));
            Set<OWLAxiom> batch2 = new LinkedHashSet<>();
            batch2.add(df.getOWLObjectPropertyAssertionAxiom(hasAlertFlag, chart, alert));

            OWLOntology ontology = backendService.getTBoxOntology();
            OWLOntologyManager manager = ontology.getOWLOntologyManager();
            manager.addAxioms(ontology, batch1);
            backendService.getReasonerService().getReasoner().flush();
            manager.addAxioms(ontology, batch2);
            backendService.getReasonerService().getReasoner().flush();
            injected.addAll(batch1);
            injected.addAll(batch2);
            log.info("📝 P-1 已注入告警命盘 | chart={} | alert={}", chartIri, alertIri);

            assertThat(latch.await(10, TimeUnit.SECONDS)).as("Listener 应在 10s 内触发回调").isTrue();
            assertThat(sent.get()).as("R21 命中应外发 MQ 消息").isTrue();

            Message received = mq.getRabbitTemplate().receive(QUEUE, 5000);
            assertThat(received).as("ziweidoushuQueue 应收到告警消息").isNotNull();
            String body = new String(received.getBody(), StandardCharsets.UTF_8);
            assertThat(body).as("消息应含告警实例 IRI").contains(alertIri);
            assertThat(body).as("消息应含告警类型").contains("大凶");
            log.info("✅ P-1 通过 | 消费到消息={}", body);
        } finally {
            listener.shutdown();
            mq.destroy();
        }
    }

    // ============================ N-1 ============================
    @Test
    @Order(2)
    @DisplayName("N-1 无 AlertFlag 命盘：R21 不命中，不触发回调、不投递消息")
    void noAlertNoMessage() throws Exception {
        RabbitMqHandler mq = new RabbitMqHandler();
        declareTopology(mq);
        purgeQueue(mq);

        OWLDataFactory df = backendService.getDataFactory();
        OWLClass natalChart = df.getOWLClass(IRI.create(CHART + "NatalChart"));
        OWLDataProperty alertTriggered = df.getOWLDataProperty(IRI.create(RULES + "alertTriggered"));

        String chartIri = ABOX + "Chart_SwrlN1_" + System.currentTimeMillis();
        OWLNamedIndividual chart = df.getOWLNamedIndividual(IRI.create(chartIri));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean sent = new AtomicBoolean(false);

        SwrlRuleTriggerListener<String> listener = new SwrlRuleTriggerListener<>(
                new SwrlRuleTriggerListener.Config<>(
                        RULES + "AlertFlag",
                        instanceIri -> {
                            sent.set(true);
                            latch.countDown();
                        },
                        String.class),
                backendService);

        try {
            listener.start();

            injected.add(df.getOWLClassAssertionAxiom(natalChart, chart));
            OWLOntology ontology = backendService.getTBoxOntology();
            ontology.getOWLOntologyManager().addAxioms(ontology, injected);
            backendService.getReasonerService().getReasoner().flush();
            log.info("📝 N-1 已注入无告警命盘 | chart={}", chartIri);

            boolean triggered = latch.await(5, TimeUnit.SECONDS);
            assertThat(triggered).as("无 AlertFlag 不应触发回调").isFalse();
            assertThat(sent.get()).as("无告警不应外发").isFalse();

            Message received = mq.getRabbitTemplate().receive(QUEUE, 1000);
            assertThat(received).as("ziweidoushuQueue 不应收到消息").isNull();

            Set<OWLLiteral> vals = backendService.getReasonerService().getReasoner()
                    .getDataPropertyValues(chart, alertTriggered);
            assertThat(vals).as("命盘不应推得 alertTriggered").isEmpty();
            log.info("✅ N-1 通过 | 无回调、无消息、无 alertTriggered");
        } finally {
            listener.shutdown();
            mq.destroy();
        }
    }

    // ============================ 工具 ============================
    private void declareTopology(RabbitMqHandler mq) {
        RabbitAdmin admin = new RabbitAdmin(mq.getRabbitTemplate());
        admin.declareExchange(new DirectExchange(EXCHANGE, true, false));
        admin.declareQueue(new Queue(QUEUE, true, false, false));
        admin.declareBinding(BindingBuilder.bind(new Queue(QUEUE, true, false, false))
                .to(new DirectExchange(EXCHANGE, true, false)).with(ROUTING_KEY));
        log.info("🏗️ AMQP 拓扑已声明 | Exchange={} | Queue={} | RoutingKey={}", EXCHANGE, QUEUE, ROUTING_KEY);
    }

    private void purgeQueue(RabbitMqHandler mq) {
        int purged = 0;
        while (mq.getRabbitTemplate().receive(QUEUE, 100) != null) {
            purged++;
        }
        if (purged > 0) {
            log.warn("🧹 测试前清理队列 [{}] 中 {} 条残留消息", QUEUE, purged);
        }
    }
}
