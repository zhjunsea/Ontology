package com.ocean.ontologyframework.industryinsight;

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
 * 行业洞察（五看三定）流程 · 真实引擎端到端（连 {@code localhost:26500}，生产 {@code IndustryInsightOntologyJobWorker} 消费）。
 *
 * <p>经真实 Zeebe 网关部署并驱动流程，断言实例完成、无 incident，并读取流程变量做**取值级**校验。
 *
 * <p><b>环境前置</b>：Camunda REST 网关 {@code localhost:9080} 与 Zeebe {@code localhost:26500} 可达，
 * 且 RabbitMQ {@code localhost:5672} 就绪（若接入 MaaS 使优先级为「高」时会外发）；未就绪时经 {@code ServiceProbe} 跳过。
 */
@SpringBootTest(classes = IndustryInsightApplication.class)
@ActiveProfiles("IndustryInsightBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 1800, unit = TimeUnit.SECONDS)
@DisplayName("行业洞察（五看三定）流程 · 真实引擎端到端（生产 Worker 消费）")
class IndustryInsightBpmnRealEngineTest {

    private static final Logger log = LoggerFactory.getLogger(IndustryInsightBpmnRealEngineTest.class);

    private static final String PROCESS_ID = "IndustryInsightProcess";
    private static final Duration WAIT = Duration.ofMinutes(20);

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @Autowired
    private CamundaClient client;

    @BeforeAll
    static void requireCamunda() {
        ServiceProbe.assumeAllReachable("IndustryInsight 真实引擎依赖（Camunda REST + RabbitMQ）",
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
    @DisplayName("P-1 通过场景：真实引擎完成，validationResult/priorityLevel/insightSkillPath 取值合法")
    void fullScenarioRealEngine() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业洞察与增长机会分析");
        vars.put("industry", "新能源汽车");
        vars.put("region", "中国");
        vars.put("timeWindow", "未来三年");

        Map<String, Object> out = run(vars);
        assertThat(str(out.get("validationResult"))).isEqualTo("通过");
        assertThat(str(out.get("priorityLevel"))).isIn("高", "中", "低");
        assertThat(str(out.get("insightSkillPath"))).as("洞察 SKILL 应已落盘").isNotBlank();
        assertThat(str(out.get("insightSummary"))).contains("行业洞察报告");
    }

    @Test
    @Order(2)
    @DisplayName("P-2 缺洞察目标：真实引擎走澄清分支，validationResult=不通过")
    void clarifyRealEngine() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业");

        Map<String, Object> out = run(vars);
        assertThat(str(out.get("validationResult"))).isEqualTo("不通过");
        assertThat(str(out.get("clarificationText"))).as("应产出澄清提示").isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("P-3 场景解析来源记录：parseSource ∈ {MaaS, 兜底}")
    void parseSourceRecorded() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业洞察与增长机会分析");

        Map<String, Object> out = run(vars);
        assertThat(str(out.get("parseSource")))
                .as("接入 MaaS 时为 MaaS，否则为确定性兜底")
                .isIn("MaaS", "兜底");
    }

    @Test
    @Order(4)
    @DisplayName("B-1 补充字段全缺省：真实引擎仍完成，兜底框架各变量非空（含【待考】）")
    void defaultFallbackRealEngine() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业分析与增长机会");

        Map<String, Object> out = run(vars);
        for (String key : List.of("insightConclusion", "crossValidation", "industryTrend", "marketCustomer",
                "competition", "selfInsight", "opportunityList", "controlPoints", "objectives",
                "strategies", "insightSummary")) {
            assertThat(str(out.get(key))).as("变量 %s 应非空", key).isNotBlank();
        }
    }

    @Test
    @Order(5)
    @DisplayName("N-1 SKILL 结构校验：ontologyValidationStatus 含「通过」，SKILL 已落盘")
    void reportStructuralCheck() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("scenario", "新能源汽车行业洞察与增长机会分析");

        Map<String, Object> out = run(vars);
        assertThat(str(out.get("ontologyValidationStatus"))).contains("通过");
        String skillPath = str(out.get("insightSkillPath"));
        assertThat(skillPath).isNotBlank();
        assertThat(Files.isRegularFile(Path.of(skillPath))).as("SKILL 文件应存在：%s", skillPath).isTrue();
    }

    // ============================ 辅助 ============================

    private Map<String, Object> run(Map<String, Object> variables) {
        long key = ProcessOrchestrator.start(client, PROCESS_ID, variables);
        awaitCompleted(key);
        Map<String, Object> out = ProcessOrchestrator.readVariables(client, key);
        log.info("✅ 真实引擎完成 | key={} | validationResult={} | priorityLevel={}",
                key, out.get("validationResult"), out.get("priorityLevel"));
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
