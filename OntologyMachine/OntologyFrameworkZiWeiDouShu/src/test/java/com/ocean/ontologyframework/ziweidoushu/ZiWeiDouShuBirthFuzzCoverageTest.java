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
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 · 生辰 Fuzz 覆盖（内存引擎 + 真实 Worker）：随机生成多样生辰（男女/成人/小儿/三种深度）逐个跑通，
 * 以真实端到端执行驱动更多格/运限/星情分支，累计 JaCoCo 分支覆盖（仅测试，无生产改动）。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@Timeout(value = 3600, unit = TimeUnit.SECONDS)
@DisplayName("紫微斗数 · 生辰 Fuzz 覆盖（内存引擎）")
class ZiWeiDouShuBirthFuzzCoverageTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuBirthFuzzCoverageTest.class);
    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Duration WAIT = Duration.ofSeconds(90);
    private static final int N = 240;

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
        ServiceProbe.assumeAllReachable("生辰 Fuzz（Ontop + MySQL，真实 Worker）", ServiceProbe.LOCALHOST, 8080, 3306);
        log.info("=== 紫微斗数 生辰 Fuzz 覆盖 ===");
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
    @DisplayName("多维随机 240 生辰：直接排盘/验盘 × 年/月/日 × 公历/农历 × 经度 × 男女/小儿")
    void fuzzBirths() {
        Random r = new Random(20261120L);
        String[] depthOpt = {"本命", "本命+大限", "本命+大限+流年"};
        String[] traits = {"相貌清秀、性情温和、已知30岁迁移", "面方、面色红、好济施、性刚果决", "身形中等、性急、早年离乡"};
        List<String> failures = new ArrayList<>();
        int ok = 0;
        for (int i = 0; i < N; i++) {
            int y = 1950 + r.nextInt(73);
            int mo = 1 + r.nextInt(12);
            int d = 1 + r.nextInt(28);
            int h = r.nextInt(24);
            int mi = r.nextInt(60);
            boolean female = r.nextBoolean();
            String gender = female ? "女" : "男";
            String subjectType = (i % 10 == 0) ? "小儿" : (female ? "成人女" : "成人男");
            String calendar = (i % 5 == 0) ? "农历" : "公历";
            int pm = i % 8;
            String place = (pm == 0) ? "" : (pm <= 2 ? "纽约 74W" : "北京 116.4E");
            String mode = (i % 4 == 0) ? "验盘" : "直接排盘";
            String granularity; String depth;
            int gi = i % 3;
            if (gi == 0) { granularity = "月"; depth = "本命+大限+流年"; }
            else if (gi == 1) { granularity = "日"; depth = "本命+大限+流年"; }
            else { granularity = "年"; depth = depthOpt[r.nextInt(depthOpt.length)]; }
            String birth = String.format("%04d-%02d-%02d %02d:%02d", y, mo, d, h, mi);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("birth", birth);
            vars.put("calendar", calendar);
            vars.put("gender", gender);
            vars.put("birthPlace", place);
            vars.put("inputMode", mode);
            vars.put("subjectType", subjectType);
            vars.put("depth", depth);
            vars.put("granularity", granularity);
            if (!"年".equals(granularity)) { vars.put("targetMonth", 1 + r.nextInt(12)); vars.put("targetDay", 1 + r.nextInt(28)); }
            if ("验盘".equals(mode)) { vars.put("subjectTraits", traits[r.nextInt(traits.length)]); }
            try {
                ProcessInstanceEvent inst = runFlow(vars);
                awaitCompleted(inst);
                BpmnAssert.assertThat(inst).isCompleted().hasNoIncidents();
                ok++;
            } catch (Throwable t) {
                failures.add(birth + "/" + subjectType + ": " + t.getMessage());
            }
        }
        log.info("生辰 Fuzz：{}/{} 通过", ok, N);
        assertThat(failures).as("Fuzz 失败:\n" + String.join("\n", failures)).isEmpty();
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
            try { Thread.sleep(120); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        throw (last != null ? last : new AssertionError("未完成"));
    }
}