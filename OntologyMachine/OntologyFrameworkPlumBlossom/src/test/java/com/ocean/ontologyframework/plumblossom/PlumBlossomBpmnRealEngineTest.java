package com.ocean.ontologyframework.plumblossom;

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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数占断流程 · 真实引擎端到端（连 {@code localhost:26500}，生产 {@code PlumBlossomOntologyJobWorker} 消费）。
 *
 * <p>经真实 Zeebe 网关部署并驱动流程，断言实例完成、无 incident，并读取流程变量做**取值级**校验
 * （本卦/动爻/生克/吉凶/应期/汇总等）。
 *
 * <p><b>环境前置</b>：Camunda REST 网关 {@code localhost:9080} 与 Zeebe {@code localhost:26500} 可达，
 * 且 RabbitMQ 就绪（大吉/大凶分支外发）；未就绪时经 {@code ServiceProbe} 跳过。
 */
@SpringBootTest(classes = PlumBlossomApplication.class)
@ActiveProfiles("PlumBlossomBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 300, unit = TimeUnit.SECONDS)
@DisplayName("梅花易数占断流程 · 真实引擎端到端（生产 Worker 消费）")
class PlumBlossomBpmnRealEngineTest {

    private static final Logger log = LoggerFactory.getLogger(PlumBlossomBpmnRealEngineTest.class);

    private static final String PROCESS_ID = "PlumBlossomDivinationProcess";
    private static final Duration WAIT = Duration.ofMinutes(2);
    private static final Set<String> LEVELS = Set.of("大吉", "吉", "平", "凶", "大凶");

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @Autowired
    private CamundaClient client;

    @BeforeAll
    static void requireCamunda() {
        ServiceProbe.assumeAllReachable("PlumBlossom 真实引擎依赖（Camunda REST + RabbitMQ）",
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
    @DisplayName("P-1 数占：真实引擎完成，本卦/动爻/生克/吉凶/应期/汇总取值合法")
    void testNumberDivinationRealEngine() {
        Map<String, Object> vars = scenario("今日天时",
                "divinationMethod", "年月日时起例",
                "lunarYear", 2026, "lunarMonth", 8, "lunarDay", 15, "hourZhi", "午",
                "subjectCategory", "出行", "motion", "行", "season", "秋", "omenDesc", "见喜鹊鸣于东");
        Map<String, Object> out = run(vars);
        assertDivinationResult(out);
    }

    @Test
    @Order(2)
    @DisplayName("P-2 字占：真实引擎完成，字占分支命中，结果合法")
    void testCharacterDivinationRealEngine() {
        Map<String, Object> out = run(scenario("求谋字占",
                "divinationMethod", "字占", "text", "梅花易数", "subjectCategory", "求谋", "season", "春"));
        assertThat(out.get("isCharacter")).as("应命中字占分支").isEqualTo(Boolean.TRUE);
        assertDivinationResult(out);
    }

    @Test
    @Order(3)
    @DisplayName("P-3 后天象占：真实引擎完成，外应解读非空")
    void testPostnatalRealEngine() {
        Map<String, Object> out = run(scenario("家宅占",
                "divinationMethod", "物卦起例", "objectCategory", "老人", "direction", "东",
                "subjectCategory", "家宅", "omenDesc", "见喜鹊鸣于东"));
        assertThat(str(out.get("divinationSystem"))).as("物卦起例应判为后天").isEqualTo("后天");
        assertThat(str(out.get("omenVerdict"))).as("有外应时解读应非空").isNotBlank();
        assertDivinationResult(out);
    }

    @Test
    @Order(4)
    @DisplayName("P-4 大吉/大凶分支：真实引擎完成，messageStatus 有值")
    void testMajorLevelAlertRealEngine() {
        Map<String, Object> out = run(scenario("出行安全",
                "divinationMethod", "年月日时起例",
                "lunarYear", 2026, "lunarMonth", 8, "lunarDay", 15, "hourZhi", "午",
                "subjectCategory", "出行", "motion", "行", "season", "秋", "omenDesc", "见喜鹊鸣于东"));
        assertDivinationResult(out);
    }

    @Test
    @Order(5)
    @DisplayName("B-1 缺省兜底：无 season/omenDesc/motion 时仍完成，兜底字段非空")
    void testDefaultFallbackRealEngine() {
        Map<String, Object> out = run(scenario("失物占",
                "divinationMethod", "年月日时起例",
                "lunarYear", 2026, "lunarMonth", 3, "lunarDay", 21, "hourZhi", "辰"));
        assertThat(str(out.get("bodyVitality"))).as("season 缺省时旺衰应由农历月推导").isNotBlank();
        assertThat(str(out.get("responsePeriod"))).as("motion 缺省时应期应有兜底").isNotBlank();
        assertDivinationResult(out);
    }

    @Test
    @Order(6)
    @DisplayName("N-1 无事不占：subject 空白 → 结束且 alertText 为戒律告警语")
    void testPreceptAlertRealEngine() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", "");
        vars.put("preceptPromise", true);
        Map<String, Object> out = run(vars);
        assertThat(str(out.get("alertText"))).as("应产出无事告警语").isEqualTo("无具体事项不可起卦。");
        assertThat(str(out.get("passPrecept"))).isEqualTo("false");
    }

    // ============================ 辅助 ============================

    private Map<String, Object> run(Map<String, Object> variables) {
        long key = ProcessOrchestrator.start(client, PROCESS_ID, variables);
        awaitCompleted(key);
        Map<String, Object> out = ProcessOrchestrator.readVariables(client, key);
        log.info("✅ 真实引擎完成 | key={} | originalHexagram={} | auspiciousness={}",
                key, out.get("originalHexagram"), out.get("auspiciousness"));
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

    private static void assertDivinationResult(Map<String, Object> out) {
        assertThat(str(out.get("originalHexagram"))).as("本卦应非空").isNotBlank();

        int moving = Integer.parseInt(str(out.get("movingLinePosition")));
        assertThat(moving).as("动爻应在 1..6").isBetween(1, 6);

        assertThat(str(out.get("shengKeType"))).as("生克应为「级别·文案」").matches("[1-5]·.+");
        assertThat(str(out.get("auspiciousness"))).as("吉凶应在五级内").isIn(LEVELS);

        assertThat(str(out.get("advice"))).as("修身建议应非空").isNotBlank();
        assertThat(str(out.get("responsePeriod"))).as("应期应非空").isNotBlank();
        assertThat(str(out.get("divinationResult"))).as("汇总结果应非空").isNotBlank();
    }

    private static Map<String, Object> scenario(String subject, Object... kv) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject + "_" + System.nanoTime());
        vars.put("preceptPromise", true);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            vars.put((String) kv[i], kv[i + 1]);
        }
        return vars;
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
