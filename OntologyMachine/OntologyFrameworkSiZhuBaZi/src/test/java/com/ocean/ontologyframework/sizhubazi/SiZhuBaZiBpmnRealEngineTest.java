package com.ocean.ontologyframework.sizhubazi;

import com.ocean.utilities.ProcessOrchestrator;
import com.ocean.utilities.ServiceProbe;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四柱命理推演流程 · 真实引擎端到端（连 {@code localhost:26500}，生产 {@code SiZhuBaZiOntologyJobWorker} 消费）。
 *
 * <p>经真实 Zeebe 网关部署并驱动流程，断言实例完成、无 incident，并读取流程变量做**取值级**校验
 * （四柱 / 命书结论 / 层次 / 命例记录等）。
 *
 * <p><b>环境前置</b>：Camunda REST 网关 {@code localhost:9080} 与 Zeebe {@code localhost:26500} 可达，
 * 且 RabbitMQ 就绪（告警分支外发）；未就绪时经 {@code ServiceProbe} 跳过。
 */
@SpringBootTest(classes = SiZhuBaZiApplication.class)
@ActiveProfiles("SiZhuBaZiBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 300, unit = TimeUnit.SECONDS)
@DisplayName("四柱命理推演流程 · 真实引擎端到端（生产 Worker 消费）")
class SiZhuBaZiBpmnRealEngineTest {

    private static final Logger log = LoggerFactory.getLogger(SiZhuBaZiBpmnRealEngineTest.class);

    private static final String PROCESS_ID = "SiZhuBaZiProcess";
    private static final Duration WAIT = Duration.ofMinutes(2);

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @Autowired
    private CamundaClient client;

    @BeforeAll
    static void requireCamunda() {
        ServiceProbe.assumeAllReachable("SiZhuBaZi 真实引擎依赖（Camunda REST + RabbitMQ）",
                ServiceProbe.LOCALHOST, 9080, 5672);
    }

    @BeforeEach
    void deploy() {
        assertThat(Files.isRegularFile(Path.of(bpmnPath)))
                .as("BPMN 文件应存在: %s", bpmnPath).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(client, PROCESS_ID, List.of(bpmnPath)))
                .as("应成功部署流程").isTrue();
        log.info("✅ 已部署 BPMN 资源 | {}", bpmnPath);
    }

    @Test
    @Order(1)
    @DisplayName("P-1 单命·男命端到端：真实引擎完成，四柱/结论/层次/命例取值合法")
    void t_real_engine_single_male_end_to_end() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", "1990-05-15 08:30");
        vars.put("calendar", "公历");
        vars.put("gender", "男");
        vars.put("subjectType", "男命");
        vars.put("inputMode", "单命");
        vars.put("granularity", "年");

        Map<String, Object> out = run(vars);
        assertThat(str(out.get("fourPillars"))).as("四柱应非空").isNotBlank();
        assertThat(str(out.get("reading"))).as("命书结论应非空").isNotBlank();
        assertThat(str(out.get("chartLevel"))).as("命局层次应非空").isNotBlank();
        assertThat(str(out.get("caseRecord"))).as("命例记录应非空").isNotBlank();
        assertThat(out).as("应产出告警标志变量").containsKey("alertTriggered");
        assertThat(out).as("应产出外发状态变量").containsKey("messageStatus");
    }

    // ============================ 辅助 ============================

    private Map<String, Object> run(Map<String, Object> variables) {
        long key = ProcessOrchestrator.start(client, PROCESS_ID, variables);
        awaitCompleted(key);
        Map<String, Object> out = ProcessOrchestrator.readVariables(client, key);
        log.info("✅ 真实引擎完成 | key={} | fourPillars={} | chartLevel={}",
                key, out.get("fourPillars"), out.get("chartLevel"));
        return out;
    }

    private void awaitCompleted(long key) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (isCompleted(key)) {
                return;
            }
            sleep();
        }
        throw new AssertionError("流程未在 " + WAIT + " 内完成 | key=" + key);
    }

    private boolean isCompleted(long key) {
        ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
        return info != null && "COMPLETED".equals(ProcessOrchestrator.statusOf(info.state(), info.hasIncident()));
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
