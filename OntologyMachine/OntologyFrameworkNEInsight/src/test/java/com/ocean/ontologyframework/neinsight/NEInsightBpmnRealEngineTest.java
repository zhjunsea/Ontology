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
 * <p>对应《NEInsight应用-测试设计文档》§6.3：P-1、P-2、P-3、B-1。
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
    // P-1: 云空间全流程（高优先级分支）
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("P-1 insightCompletesCloudSpace：云空间全流程完成，走高优先级分支，本体一致")
    void insightCompletesCloudSpace() {
        Map<String, Object> vars = runToCompletion("CLOUD_SPACE");

        assertThat(vars.get("opportunityPriority")).as("云空间机会点优先级").isEqualTo("HIGH");
        assertThat(vars.get("smartCompliant")).as("SMART 合规").isEqualTo(Boolean.TRUE);
        assertThat(vars.get("ontologyValidationStatus")).as("本体一致性状态").isEqualTo("CONSISTENT");
        assertThat(vars.get("alertDispatched")).as("应经 mark-high-priority 分支（外发标记存在）").isNotNull();
        assertThat(vars.get("insightSummary")).as("应产出洞察摘要").isNotNull();
    }

    // ============================================================
    // P-2: IT 空间全流程（非高优先级分支）
    // ============================================================
    @Test
    @Order(2)
    @DisplayName("P-2 insightCompletesItSpace：IT 空间全流程完成，走非高优先级分支直达三定")
    void insightCompletesItSpace() {
        Map<String, Object> vars = runToCompletion("IT_SPACE");

        Object priority = vars.get("opportunityPriority");
        assertThat(priority).as("IT 空间无高优先级机会点").isNotEqualTo("HIGH");
        assertThat(vars.get("ontologyValidationStatus")).isEqualTo("CONSISTENT");
    }

    // ============================================================
    // P-3: 联合空间全流程
    // ============================================================
    @Test
    @Order(3)
    @DisplayName("P-3 insightCompletesCombined：联合空间全流程完成，域基线含 IT+云联合要素")
    void insightCompletesCombined() {
        Map<String, Object> vars = runToCompletion("COMBINED");

        @SuppressWarnings("unchecked")
        Map<String, Object> baseline = (Map<String, Object>) vars.get("domainBaseline");
        assertThat(baseline).as("应产出域基线").isNotNull();
        assertThat(baseline.get("domain")).isEqualTo("COMBINED");
        assertThat(vars.get("ontologyValidationStatus")).isEqualTo("CONSISTENT");
    }

    // ============================================================
    // B-1: 非法洞察域（Controller 校验）
    // ============================================================
    @Test
    @Order(4)
    @DisplayName("B-1 invalidDomainRejected：非法洞察域被 Controller 拒绝（HTTP 400）")
    void invalidDomainRejected() {
        ResponseEntity<?> resp = controller.startInsight(Map.of("insightDomain", "X"));
        assertThat(resp.getStatusCode()).as("非法域应返回 400").isEqualTo(HttpStatus.BAD_REQUEST);
        log.info("✅ B-1 通过: HTTP {} body={}", resp.getStatusCode(), resp.getBody());
    }

    // ============================================================
    // 辅助
    // ============================================================

    private Map<String, Object> runToCompletion(String domain) {
        long key = ProcessOrchestrator.start(client, PROCESS_ID, Map.of("insightDomain", domain));
        awaitCompleted(key);
        Map<String, Object> vars = ProcessOrchestrator.readVariables(client, key);
        log.info("✅ 流程完成 | domain={} | key={} | vars={}", domain, key, vars.keySet());
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
