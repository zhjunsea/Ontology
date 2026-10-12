package com.ocean.ontologyframework.neinsight;

import com.ocean.ontologyframework.neinsight.web.NEInsightController;
import com.ocean.utilities.ProcessOrchestrator;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NEInsight 「五看三定」BPMN · 真实引擎端到端测试
 * （连 Zeebe localhost:26500，生产 {@link NEInsightOntologyJobWorker} 消费全部 serviceTask）。
 *
 * <p>输入为「洞察场景」（{@code insightScenario}），五看三定围绕该场景展开；检索根域固定为联合域。
 * 对应《NEInsight应用-测试设计文档》§6.3：P-1、P-2、B-1。
 * 前置：Zeebe/Ontop/MySQL/RabbitMQ 就绪，激活 {@code NEInsightBPMN} Profile 装配生产 Worker。
 */
@SpringBootTest(classes = NEInsightApplication.class)
@ActiveProfiles("NEInsightBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 300, unit = TimeUnit.SECONDS)
@DisplayName("NEInsight BPMN · 真实引擎端到端（连 localhost:26500，生产 Worker 消费）")
class NEInsightBpmnRealEngineTest {

    private static final Logger log = LoggerFactory.getLogger(NEInsightBpmnRealEngineTest.class);

    private static final String PROCESS_ID = "NewEnergyInsightProcess";
    private static final Duration WAIT = Duration.ofMinutes(2);

    private static final String SCENARIO = "面向双碳目标的新能源企业 IT 与云协同数据平台建设";

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @Autowired
    private CamundaClient client;

    @Autowired
    private NEInsightController controller;

    @BeforeEach
    void deploy() {
        assertThat(Files.isRegularFile(Path.of(bpmnPath)))
                .as("BPMN 文件应存在: %s", bpmnPath).isTrue();
        boolean deployed = ProcessOrchestrator.deployIfAbsent(client, PROCESS_ID, List.of(bpmnPath));
        assertThat(deployed).as("应成功部署流程").isTrue();
        log.info("✅ 已部署 BPMN 资源 | {}", bpmnPath);
    }

    // ============================================================
    // P-1: 场景驱动全流程（高优先级分支）
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("P-1 insightCompletesForScenario：给定洞察场景，流程走完并命中高优先级分支，本体一致")
    void insightCompletesForScenario() {
        Map<String, Object> vars = runToCompletion(SCENARIO);

        assertThat(vars.get("opportunityPriority")).as("联合检索下机会点最高优先级").isEqualTo("HIGH");
        assertThat(vars.get("smartCompliant")).as("SMART 合规").isEqualTo(Boolean.TRUE);
        assertThat(vars.get("ontologyValidationStatus")).as("本体一致性状态").isEqualTo("CONSISTENT");
        assertThat(vars.get("alertDispatched")).as("应经 mark-high-priority 分支（外发标记存在）").isNotNull();
        assertThat(vars.get("insightSummary")).as("应产出洞察摘要").isNotNull();
    }

    // ============================================================
    // P-2: 场景驱动全流程（产出洞察 SKILL）
    // ============================================================
    @Test
    @Order(2)
    @DisplayName("P-2 insightProducesSkill：流程完成并产出「给大模型生成洞察报告」的 SKILL 路径")
    void insightProducesSkill() {
        Map<String, Object> vars = runToCompletion(SCENARIO);

        assertThat(vars.get("ontologyValidationStatus")).isEqualTo("CONSISTENT");
        assertThat(vars.get("instanceIri")).as("应产出实例 IRI").isNotNull();
        Object skillPath = vars.get("insightSkillPath");
        assertThat(skillPath).as("应产出洞察 SKILL 落盘路径").isNotNull();
        assertThat(String.valueOf(skillPath)).as("SKILL 文件应实际落盘").isNotBlank();
        assertThat(Files.isRegularFile(Path.of(String.valueOf(skillPath))))
                .as("洞察 SKILL 文件应存在: %s", skillPath).isTrue();
    }

    // ============================================================
    // B-1: 空洞察场景（Controller 校验）
    // ============================================================
    @Test
    @Order(3)
    @DisplayName("B-1 blankScenarioRejected：空洞察场景被 Controller 拒绝（HTTP 400）")
    void blankScenarioRejected() {
        ResponseEntity<?> blank = controller.startInsight(Map.of("insightScenario", "   "));
        assertThat(blank.getStatusCode()).as("空场景应返回 400").isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<?> missing = controller.startInsight(Map.of());
        assertThat(missing.getStatusCode()).as("缺场景应返回 400").isEqualTo(HttpStatus.BAD_REQUEST);

        log.info("✅ B-1 通过: blank={} missing={}", blank.getStatusCode(), missing.getStatusCode());
    }

    // ============================================================
    // 辅助
    // ============================================================

    private Map<String, Object> runToCompletion(String scenario) {
        long key = ProcessOrchestrator.start(client, PROCESS_ID, Map.of("insightScenario", scenario));
        awaitCompleted(key);
        Map<String, Object> vars = ProcessOrchestrator.readVariables(client, key);
        log.info("✅ 流程完成 | scenario={} | key={} | vars={}", scenario, key, vars.keySet());
        assertThat(vars).as("流程变量不应为空").isNotEmpty();
        return vars;
    }

    private void awaitCompleted(long key) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
            if (info != null) {
                String status = ProcessOrchestrator.statusOf(info.state(), info.hasIncident());
                assertThat(status).as("流程不应进入 INCIDENT 态 | key=%s", key).isNotEqualTo("INCIDENT");
                if ("COMPLETED".equals(status)) {
                    return;
                }
            }
            sleep();
        }
        throw new AssertionError("流程未在 " + WAIT + " 内完成 | key=" + key);
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
