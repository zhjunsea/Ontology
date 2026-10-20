package com.ocean.ontologyframework.plumblossom;

import com.ocean.utilities.ProcessOrchestrator;
import com.ocean.utilities.ServiceProbe;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import io.camunda.zeebe.process.test.filters.RecordStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数占断流程 · 内存引擎端到端（生产 {@code PlumBlossomOntologyJobWorker} 驱动）。
 *
 * <p>以 {@code zeebe-process-test} 内存引擎部署 {@code PlumBlossomDivinationProcess}，
 * 由生产 Worker 消费全部 18 个 serviceTask，覆盖主干节点顺序、三个排他网关分支与负向戒律分支，
 * 断言实例完成、无 incident。变量级断言见 {@link PlumBlossomBpmnRealEngineTest}。
 *
 * <p><b>环境前置</b>：RabbitMQ 可达 {@code localhost:5672}（大吉/大凶分支的 {@code sendAlert} 需外发；
 * 未就绪时本类整体跳过）。本体由生产 Worker 从 {@code ontology/plumblossom-all.owl} 加载，无需数据库。
 */
@SpringBootTest(classes = PlumBlossomApplication.class)
@ActiveProfiles("PlumBlossomBPMN")
@DisplayName("梅花易数占断流程 · 内存引擎端到端（生产 Worker 驱动）")
class PlumBlossomBpmnInMemoryTest {

    private static final Logger log = LoggerFactory.getLogger(PlumBlossomBpmnInMemoryTest.class);

    private static final String PROCESS_ID = "PlumBlossomDivinationProcess";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    /** 与分支无关、任何成功路径都会经过的公共尾链（顺序稳定，可作子序列断言）。 */
    private static final List<String> COMMON_TAIL = List.of(
            "Task_BodyUse", "Task_Mutual", "Task_Changed", "Task_ShengKe", "Task_Vitality",
            "Task_ImageSymbol", "Task_Categorize", "Task_Omen", "Task_Judge",
            "Task_Response", "Task_Advice", "Task_Summary");

    private static ZeebeTestEngine engine;
    private static ZeebeClient testClient;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @BeforeAll
    static void requireRabbitMq() {
        ServiceProbe.assumeAllReachable("PlumBlossom 内存引擎测试依赖（RabbitMQ，供 sendAlert）",
                ServiceProbe.LOCALHOST, 5672);
    }

    @DynamicPropertySource
    static void startInMemoryEngine(DynamicPropertyRegistry registry) {
        engine = EngineFactory.create();
        engine.start();
        testClient = engine.createClient();
        BpmnAssert.initRecordStream(RecordStream.of(engine.getRecordStreamSource()));

        String address = normalize(engine.getGatewayAddress());
        log.info("内存引擎已启动 | rawGatewayAddress={} | normalized={}", engine.getGatewayAddress(), address);
        registry.add("camunda.client.grpc-address", () -> address);
        registry.add("camunda.client.rest-address", () -> address);
        registry.add("camunda.client.prefer-rest-over-grpc", () -> "false");
    }

    @AfterAll
    static void stopEngine() {
        if (testClient != null) {
            try { testClient.close(); } catch (Exception ignored) { }
        }
        if (engine != null) {
            try { engine.stop(); } catch (Exception ignored) { }
        }
    }

    // ============================ 正向：起卦链路 ============================

    @Test
    @DisplayName("P-1 数占（年月日时起例）全链完成：Precept→Route→Cast→…→Summary，无 incident")
    void testNumberDivinationCompletes() {
        ProcessInstanceEvent instance = run(scenario("今日天时",
                "divinationMethod", "年月日时起例",
                "lunarYear", 2026, "lunarMonth", 8, "lunarDay", 15, "hourZhi", "午",
                "subjectCategory", "出行", "motion", "行", "season", "秋", "omenDesc", "见喜鹊鸣于东"));
        BpmnAssert.assertThat(instance)
                .hasPassedElementsInOrder(concat(List.of("Task_PreceptCheck", "Task_Route", "Task_Cast"), COMMON_TAIL));
    }

    @Test
    @DisplayName("P-2 字占：经 Gateway_Char 走 Task_CharSplit 分支，全链完成")
    void testCharacterDivinationTakesSplitBranch() {
        ProcessInstanceEvent instance = run(scenario("求谋字占",
                "divinationMethod", "字占", "text", "梅花易数",
                "subjectCategory", "求谋", "season", "春"));
        BpmnAssert.assertThat(instance)
                .hasPassedElement("Task_CharSplit")
                .hasPassedElementsInOrder(concat(
                        List.of("Task_PreceptCheck", "Task_Route", "Task_CharSplit", "Task_Cast"), COMMON_TAIL));
    }

    @Test
    @DisplayName("P-3 后天象占（物卦起例）：完成且经外应节点")
    void testPostnatalSymbolDivinationCompletes() {
        ProcessInstanceEvent instance = run(scenario("家宅占",
                "divinationMethod", "物卦起例", "objectCategory", "老人", "direction", "东",
                "subjectCategory", "家宅", "omenDesc", "见喜鹊鸣于东"));
        BpmnAssert.assertThat(instance)
                .hasPassedElementsInOrder(concat(List.of("Task_PreceptCheck", "Task_Route", "Task_Cast"), COMMON_TAIL));
    }

    @Test
    @DisplayName("P-4 一撮金法：全链完成")
    void testYicuoJinCompletes() {
        ProcessInstanceEvent instance = run(scenario("官讼占",
                "divinationMethod", "一撮金法", "yicuoText", "乾坤", "subjectCategory", "官讼"));
        BpmnAssert.assertThat(instance)
                .hasPassedElementsInOrder(concat(List.of("Task_PreceptCheck", "Task_Route", "Task_Cast"), COMMON_TAIL));
    }

    @Test
    @DisplayName("B-1 缺省兜底（无 season/omenDesc/motion）：仍完成，无 incident")
    void testDefaultFallbacks() {
        ProcessInstanceEvent instance = run(scenario("失物占",
                "divinationMethod", "年月日时起例",
                "lunarYear", 2026, "lunarMonth", 3, "lunarDay", 21, "hourZhi", "辰"));
        BpmnAssert.assertThat(instance)
                .hasPassedElementsInOrder(concat(List.of("Task_PreceptCheck", "Task_Route", "Task_Cast"), COMMON_TAIL));
    }

    @Test
    @DisplayName("B-2 大吉/大凶分支仍收敛：经 Task_SendAlert 后必达 Task_Summary → End_01")
    void testMajorLevelStillConverges() {
        ProcessInstanceEvent instance = run(scenario("出行安全",
                "divinationMethod", "年月日时起例",
                "lunarYear", 2026, "lunarMonth", 8, "lunarDay", 15, "hourZhi", "午",
                "subjectCategory", "出行", "motion", "行", "season", "秋", "omenDesc", "见喜鹊鸣于东"));
        BpmnAssert.assertThat(instance)
                .hasPassedElement("Task_Advice")
                .hasPassedElement("Task_Summary")
                .hasPassedElement("End_01");
    }

    // ============================ 负向：四不占 ============================

    @Test
    @DisplayName("N-1 无事不占：subject 空白 → 走 Task_PreceptAlert 分支后结束，不进起卦链")
    void testNoThingPreceptAlertsAndTerminates() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", "");
        vars.put("preceptPromise", true);
        ProcessInstanceEvent instance = run(vars);
        BpmnAssert.assertThat(instance)
                .hasPassedElementsInOrder("Task_PreceptCheck", "Task_PreceptAlert", "End_01");
    }

    @Test
    @DisplayName("N-2 不诚不占：preceptPromise=false → 走 Task_PreceptAlert 分支")
    void testInsincerePreceptAlerts() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", "试探占_" + System.nanoTime());
        vars.put("preceptPromise", false);
        ProcessInstanceEvent instance = run(vars);
        BpmnAssert.assertThat(instance)
                .hasPassedElementsInOrder("Task_PreceptCheck", "Task_PreceptAlert", "End_01");
    }

    @Test
    @DisplayName("N-3 重卦不占：同会话同 subject 二次起卦 → 第 2 次走 Task_PreceptAlert 分支")
    void testRepeatedSubjectTriggersChongGua() {
        Map<String, Object> vars = scenario("重卦占测试",
                "divinationMethod", "年月日时起例",
                "lunarYear", 2026, "lunarMonth", 8, "lunarDay", 15, "hourZhi", "午");
        ProcessInstanceEvent firstInstance = run(vars);
        BpmnAssert.assertThat(firstInstance).hasPassedElement("Task_Route");

        ProcessInstanceEvent secondInstance = run(vars);
        BpmnAssert.assertThat(secondInstance)
                .hasPassedElementsInOrder("Task_PreceptCheck", "Task_PreceptAlert", "End_01");
    }

    // ============================ 辅助 ============================

    private ProcessInstanceEvent run(Map<String, Object> variables) {
        assertThat(Files.isRegularFile(Path.of(bpmnPath)))
                .as("BPMN 文件应存在: %s", bpmnPath).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(testClient, PROCESS_ID, Path.of(bpmnPath).toString()))
                .as("应成功部署流程").isTrue();

        ProcessInstanceEvent instance = testClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        awaitCompletion(instance);
        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement("Start_01")
                .hasPassedElement("End_01");
        return instance;
    }

    private static void awaitCompletion(ProcessInstanceEvent instance) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        AssertionError last = null;
        while (System.nanoTime() < deadline) {
            try {
                BpmnAssert.assertThat(instance).isCompleted();
                return;
            } catch (AssertionError e) {
                last = e;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last != null ? last : new AssertionError("流程未在 " + TIMEOUT + " 内完成");
    }

    /** 以「subject + 唯一时间戳 + 通过前置」为基，追加 k/v 对构建变量（k/v 成对出现）。 */
    private static Map<String, Object> scenario(String subject, Object... kv) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject + "_" + System.nanoTime());
        vars.put("preceptPromise", true);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            vars.put((String) kv[i], kv[i + 1]);
        }
        return vars;
    }

    private static String[] concat(List<String> head, List<String> tail) {
        List<String> all = new ArrayList<>(head);
        all.addAll(tail);
        return all.toArray(new String[0]);
    }

    private static String normalize(String gatewayAddress) {
        if (gatewayAddress == null || gatewayAddress.isBlank()) {
            throw new IllegalStateException("内存引擎未返回有效 gateway 地址");
        }
        return gatewayAddress.startsWith("http") ? gatewayAddress : "http://" + gatewayAddress;
    }
}
