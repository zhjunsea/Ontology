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
import org.junit.jupiter.api.Timeout;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定向命例·进度内 E2E：为 handleDeterminePattern 中"可达但常规命例未覆盖"的格局，
 * 用 hunt 池中**实测命中该格**的真实命例驱动整盘流程，覆盖对应判定分支。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@Timeout(value = 1200, unit = TimeUnit.SECONDS)
@DisplayName("定向命例·格条件覆盖（石中隐玉/兼文武/对面朝斗/月生沧海/日出扶桑/金灿光辉/禄逢两杀/贪火相逢…）")
class ZiWeiDouShuPatternTargetedTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuPatternTargetedTest.class);
    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Duration WAIT = Duration.ofMinutes(2);

    private static ZeebeTestEngine engine;
    private static ZeebeClient testClient;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @DynamicPropertySource
    static void startEngine(DynamicPropertyRegistry registry) {
        engine = EngineFactory.create();
        engine.start();
        testClient = engine.createClient();
        BpmnAssert.initRecordStream(RecordStream.of(engine.getRecordStreamSource()));
        String addr = normalize(engine.getGatewayAddress());
        registry.add("camunda.client.grpc-address", () -> addr);
        registry.add("camunda.client.rest-address", () -> addr);
        registry.add("camunda.client.prefer-rest-over-grpc", () -> "false");
    }

    @BeforeAll
    static void requireInfra() {
        ServiceProbe.assumeAllReachable("定向命例E2E（Ontop+MySQL）", ServiceProbe.LOCALHOST, 8080, 3306);
    }

    @AfterAll
    static void stopEngine() {
        if (testClient != null) { try { testClient.close(); } catch (Exception ignored) { } }
        if (engine != null) { try { engine.stop(); } catch (Exception ignored) { } }
        ZiWeiDouShuOntologyJobWorker.resetOntologyCaches();
    }

    private static String normalize(String gw) {
        if (gw == null || gw.isBlank()) throw new IllegalStateException("无 gateway 地址");
        return gw.startsWith("http") ? gw : "http://" + gw;
    }

    /** 命中各格局的真实命例（birth -> 期望可在 patternResult 中出现的格名关键词）。 */
    private static final String[][] CASES = {
            {"1957-03-13 06:04", "权禄生逢"},
            {"1957-06-14 06:18", "贪火"},
            {"1958-01-09 19:26", "兼文武"},
            {"1965-02-01 20:43", "日出扶桑"},
            {"1967-03-03 19:05", "财居财位"},
            {"1967-11-26 10:33", "石中隐玉"},
            {"1970-09-09 05:05", "坐贵向贵"},
            {"1973-01-11 08:36", "月生沧海"},
            {"1976-07-20 16:31", "日月夹财"},
            {"1981-10-08 20:56", "生不逢时"},
            {"1985-09-06 18:34", "火贵"},
            {"1990-05-10 22:29", "金灿光辉"},
            {"1993-04-12 14:00", "紫府同宫"},
            {"1999-03-09 15:07", "紫府朝垣"},
            {"2000-05-10 15:58", "日月并明"},
            {"2002-02-13 22:11", "巨机居卯"},
            {"2008-06-06 22:31", "日月夹命"},
            {"2009-07-16 13:28", "禄逢两杀"},
            {"2017-02-16 17:09", "对面朝斗"},
            {"2019-07-03 11:34", "金舆"}
    };

    @Test
    @DisplayName("20 例定向命例：整盘流程完成、无 incident")
    void runCases() {
        List<String> failures = new ArrayList<>();
        int ok = 0;
        for (String[] c : CASES) {
            String birth = c[0];
            try {
                ProcessInstanceEvent inst = runFlow(buildVars(birth));
                awaitCompleted(inst);
                BpmnAssert.assertThat(inst).isCompleted().hasNoIncidents();
                ok++;
            } catch (Throwable t) {
                failures.add(birth + " -> " + String.valueOf(t.getMessage()).replace('\n', ' '));
            }
        }
        log.info("定向命例完成 {}/{}", ok, CASES.length);
        assertThat(failures).as("未完成/异常：\n" + String.join("\n", failures)).isEmpty();
    }

    private Map<String, Object> buildVars(String birth) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("birth", birth);
        v.put("calendar", "公历");
        v.put("gender", "男");
        v.put("birthPlace", "北京 116.4E");
        v.put("inputMode", "直接排盘");
        v.put("subjectType", "成人男");
        v.put("depth", "本命");
        v.put("granularity", "年");
        return v;
    }

    private ProcessInstanceEvent runFlow(Map<String, Object> vars) {
        assertThat(Files.isRegularFile(Path.of(bpmnPath))).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(testClient, PROCESS_ID, Path.of(bpmnPath).toString())).isTrue();
        return testClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID).latestVersion().variables(vars).send().join();
    }

    private void awaitCompleted(ProcessInstanceEvent instance) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        AssertionError last = null;
        while (System.nanoTime() < deadline) {
            try {
                BpmnAssert.assertThat(instance).isCompleted();
                return;
            } catch (AssertionError e) {
                last = e;
            }
            try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        throw last != null ? last : new AssertionError("流程未在 " + WAIT + " 内完成");
    }
}
