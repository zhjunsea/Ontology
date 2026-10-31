package com.ocean.ontologyframework.ziweidoushu;

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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数推命流程 · 内存引擎端到端（第 8 步；生产 ZiWeiDouShuOntologyJobWorker 驱动）。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@DisplayName("紫微斗数推命流程 · 内存引擎端到端（生产 Worker 驱动）")
class ZiWeiDouShuBpmnInMemoryTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuBpmnInMemoryTest.class);

    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    /** 主干最短路径（成人男·本命·直接排盘）经过的 serviceTask 顺序。 */
    private static final String[] MAIN_TASKS = {
            "Task_ParseInput", "Task_LifePalaces", "Task_MainStars", "Task_AuxStars", "Task_MiscStars",
            "Task_FourTransform", "Task_Brightness", "Task_StarNature", "Task_EmptyPalace", "Task_Surround",
            "Task_Pattern", "Task_Natal", "Task_Aggregate", "Task_PersistReturn"
    };

    private static ZeebeTestEngine engine;
    private static ZeebeClient testClient;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @DynamicPropertySource
    static void startInMemoryEngine(DynamicPropertyRegistry registry) {
        engine = EngineFactory.create();
        engine.start();
        testClient = engine.createClient();
        BpmnAssert.initRecordStream(RecordStream.of(engine.getRecordStreamSource()));
        String address = normalize(engine.getGatewayAddress());
        log.info("内存引擎已启动 | normalized={}", address);
        registry.add("camunda.client.grpc-address", () -> address);
        registry.add("camunda.client.rest-address", () -> address);
        registry.add("camunda.client.prefer-rest-over-grpc", () -> "false");
    }

    @AfterAll
    static void stopEngine() {
        if (testClient != null) {
            try {
                testClient.close();
            } catch (Exception ignored) {
            }
        }
        if (engine != null) {
            try {
                engine.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @BeforeAll
    static void requireOntologyServices() {
        ServiceProbe.assumeAllReachable("紫微斗数内存引擎端到端依赖（Ontop + MySQL，生产 Worker 需要）",
                ServiceProbe.LOCALHOST, 8080, 3306);
        log.info("=== 紫微斗数内存引擎端到端测试 ===");
    }

    // ============================ P-1 ============================
    @Test
    @DisplayName("P-1 主干最短路径（成人男·本命）：无 incident 完成，主干节点顺序通过")
    void chartCompletesOnMainPath() {
        ProcessInstanceEvent instance = runFlow(baseVars("1990-05-15 14:30", "男", "成人男", "本命"));
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement("Start_01")
                .hasPassedElement("End_01")
                .hasPassedElementsInOrder(MAIN_TASKS);
    }

    // ============================ P-2 ============================
    @Test
    @DisplayName("P-2 女命分支：经过节点 W，femaleReading 非空，完成")
    void femaleBranchCompletes() {
        ProcessInstanceEvent instance = runFlow(baseVars("1985-11-08 03:20", "女", "成人女", "本命"));
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance).isCompleted().hasNoIncidents().hasPassedElement("Task_FemaleReading");
    }

    // ============================ P-3 ============================
    @Test
    @DisplayName("P-3 小儿分支：经过节点 C，childReading 非空，完成")
    void childBranchCompletes() {
        ProcessInstanceEvent instance = runFlow(baseVars("2018-02-20 09:00", "男", "小儿", "本命"));
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance).isCompleted().hasNoIncidents().hasPassedElement("Task_ChildReading");
    }

    // ============================ P-4 ============================
    @Test
    @DisplayName("P-4 深度=本命+大限：经过节点 13，完成")
    void majorLimitBranchCompletes() {
        ProcessInstanceEvent instance = runFlow(baseVars("1985-11-08 03:20", "女", "成人女", "本命+大限"));
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance).isCompleted().hasNoIncidents().hasPassedElement("Task_MajorLimit");
    }

    // ============================ P-5 ============================
    @Test
    @DisplayName("P-5 深度=本命+大限+流年：经过 13→14→15，完成")
    void fullDepthBranchCompletes() {
        ProcessInstanceEvent instance = runFlow(baseVars("1978-07-01 22:10", "男", "成人男", "本命+大限+流年"));
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance).isCompleted().hasNoIncidents()
                .hasPassedElementsInOrder("Task_MajorLimit", "Task_AnnualFortune", "Task_Overlay")
                .hasPassedElement("Task_Aggregate");
    }

    // ============================ P-6 ============================
    @Test
    @DisplayName("P-6 全链 S6：完成并经过综合汇总（G4 判定）")
    void alertPathCompletes() {
        ProcessInstanceEvent instance = runFlow(baseVars("1960-01-01 12:00", "男", "成人男", "本命+大限+流年"));
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance).isCompleted().hasNoIncidents().hasPassedElement("Task_Aggregate");
    }

    // ============================ B-1 ============================
    @Test
    @DisplayName("B-1 缺省变量兜底：仅传 birth，缺省 成人男/本命/年 生效，完成")
    void defaultVariablesFallback() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", "1990-05-15 14:30");
        ProcessInstanceEvent instance = runFlow(vars);
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance).isCompleted().hasNoIncidents().hasPassedElement("Task_PersistReturn");
    }

    // ============================ B-2 ============================
    @Test
    @DisplayName("B-2 验盘模式（无需校正）：经过节点 V，G1b 走「无需校正」到 J1，完成")
    void verifyingModeLoopsBack() {
        Map<String, Object> vars = baseVars("1992-03-12 05:00", "男", "成人男", "本命");
        vars.put("inputMode", "验盘");
        vars.put("subjectTraits", "相貌清秀、性情温和、已知 30 岁迁移");
        ProcessInstanceEvent instance = runFlow(vars);
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance).isCompleted().hasNoIncidents().hasPassedElement("Task_VerifyChart");
    }

    // ============================ N-1 ============================
    @Test
    @DisplayName("N-1 缺 birth：节点1 fail → incident，流程不完成")
    void invalidBirthCausesIncident() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("gender", "男");
        ProcessInstanceEvent instance = runFlow(vars);
        assertThat(completedWithin(instance, Duration.ofSeconds(10))).as("缺 birth 不应完成").isFalse();
    }

    // ============================ 工具 ============================
    private Map<String, Object> baseVars(String birth, String gender, String subjectType, String depth) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", birth);
        vars.put("calendar", "公历");
        vars.put("gender", gender);
        vars.put("birthPlace", "北京 116.4E");
        vars.put("inputMode", "直接排盘");
        vars.put("subjectType", subjectType);
        vars.put("depth", depth);
        vars.put("granularity", "年");
        return vars;
    }

    private ProcessInstanceEvent runFlow(Map<String, Object> vars) {
        assertThat(Files.isRegularFile(Path.of(bpmnPath))).as("BPMN 文件应存在: %s", bpmnPath).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(testClient, PROCESS_ID, Path.of(bpmnPath).toString()))
                .as("应成功部署流程").isTrue();
        return testClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(vars)
                .send()
                .join();
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
            sleep(200);
        }
        throw last != null ? last : new AssertionError("流程未在 " + TIMEOUT + " 内完成");
    }

    private static boolean completedWithin(ProcessInstanceEvent instance, Duration window) {
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                BpmnAssert.assertThat(instance).isCompleted();
                return true;
            } catch (AssertionError ignored) {
            }
            sleep(200);
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String normalize(String gatewayAddress) {
        if (gatewayAddress == null || gatewayAddress.isBlank()) {
            throw new IllegalStateException("内存引擎未返回有效 gateway 地址");
        }
        return gatewayAddress.startsWith("http") ? gatewayAddress : "http://" + gatewayAddress;
    }
}
