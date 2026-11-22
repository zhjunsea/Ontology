package com.ocean.ontologyframework.ziweidoushu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 · {@code hunt_cases.json}（150 例多命盘类型）进程内端到端：内存引擎 EngineFactory + 真实生产 Worker，
 * 计入 JaCoCo。把原先只由「真实 Camunda 引擎」用例消费的 150 条 hunt 命例改由内存引擎跑通，
 * 覆盖更多五行局/命宫/男女/深度/格局组合，提升 JobWorker 分支覆盖率（不依赖 c8run）。
 * 每例断言：流程 COMPLETED 且无 incident（数据值断言仍由真实引擎用例负责）。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@Timeout(value = 3600, unit = TimeUnit.SECONDS)
@DisplayName("紫微斗数 · hunt_cases(150) 进程内端到端（内存引擎+真实Worker）")
class ZiWeiDouShuHuntCasesInMemoryTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuHuntCasesInMemoryTest.class);
    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Duration WAIT = Duration.ofSeconds(120);

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
        registry.add("camunda.client.grpc-address", () -> address);
        registry.add("camunda.client.rest-address", () -> address);
        registry.add("camunda.client.prefer-rest-over-grpc", () -> "false");
    }

    @BeforeAll
    static void requireInfra() {
        ServiceProbe.assumeAllReachable("hunt 进程内端到端（Ontop + MySQL，真实 Worker 需要）",
                ServiceProbe.LOCALHOST, 8080, 3306);
        log.info("=== 紫微斗数 hunt_cases 进程内端到端测试 ===");
    }

    @AfterAll
    static void stopEngine() {
        if (testClient != null) { try { testClient.close(); } catch (Exception ignored) { } }
        if (engine != null) { try { engine.stop(); } catch (Exception ignored) { } }
        ZiWeiDouShuOntologyJobWorker.resetOntologyCaches();
    }

    private static String normalize(String gw) {
        if (gw == null || gw.isBlank()) throw new IllegalStateException("内存引擎未返回有效 gateway 地址");
        return gw.startsWith("http") ? gw : "http://" + gw;
    }

    @Test
    @DisplayName("hunt_cases(150)+定向造盘(14)：逐例 COMPLETED 且无 incident")
    void huntCases() throws Exception {
        List<Map<String, Object>> cases = new ArrayList<>();
        try (InputStream in = new ClassPathResource("ziweidoushu/hunt_cases.json").getInputStream()) {
            cases.addAll(new ObjectMapper().readValue(in, new TypeReference<List<Map<String, Object>>>() { }));
        }
        // 定向造盘：针对未覆盖「格」token 精选的生辰（覆盖 determinePattern/judge* 的格分支）
        try (InputStream in2 = new ClassPathResource("ziweidoushu/pattern_directed_cases.json").getInputStream()) {
            cases.addAll(new ObjectMapper().readValue(in2, new TypeReference<List<Map<String, Object>>>() { }));
        }
        List<String> failures = new ArrayList<>();
        int ok = 0;
        for (Map<String, Object> c : cases) {
            String id = str(c.getOrDefault("id", "?"));
            try {
                ProcessInstanceEvent inst = runFlow(buildVars(c));
                awaitCompleted(inst);
                BpmnAssert.assertThat(inst).isCompleted().hasNoIncidents();
                ok++;
            } catch (Throwable t) {
                failures.add(id + " 未完成/incident: " + t.getMessage());
            }
        }
        log.info("hunt_cases 进程内：{}/{} 通过", ok, cases.size());
        assertThat(failures).as("hunt 失败：\n" + String.join("\n", failures)).isEmpty();
    }

    private Map<String, Object> buildVars(Map<String, Object> c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("birth", str(c.get("birth")));
        v.put("calendar", "公历");
        v.put("gender", str(c.get("gender")));
        v.put("birthPlace", "北京 116.4E");
        v.put("inputMode", str(c.getOrDefault("mode", "直接排盘")));
        v.put("subjectType", str(c.getOrDefault("subjectType", "成人男")));
        v.put("depth", str(c.getOrDefault("depth", "本命")));
        v.put("granularity", "年");
        return v;
    }

    private ProcessInstanceEvent runFlow(Map<String, Object> vars) {
        assertThat(Files.isRegularFile(Path.of(bpmnPath))).as("BPMN 应存在: %s", bpmnPath).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(testClient, PROCESS_ID, Path.of(bpmnPath).toString()))
                .as("应成功部署流程").isTrue();
        return testClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID).latestVersion().variables(vars).send().join();
    }

    private void awaitCompleted(ProcessInstanceEvent instance) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        AssertionError last = null;
        while (System.nanoTime() < deadline) {
            try { BpmnAssert.assertThat(instance).isCompleted(); return; }
            catch (AssertionError e) { last = e; }
            try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        throw (last != null ? last : new AssertionError("流程未在 " + WAIT + " 内完成"));
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
