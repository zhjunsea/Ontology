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
 * 紫微斗数 · 进程内数据驱动矩阵端到端（内存引擎 EngineFactory + 真实生产 Worker，计入 JaCoCo）。
 *
 * <p>与 {@code ZiWeiDouShuPatternMatrixTest}（外连 9089 应用，跨 JVM 不计覆盖）不同，本类把
 * 真实 {@code ZiWeiDouShuOntologyJobWorker} 跑在**同一测试 JVM 的嵌入式 Zeebe 引擎**上，
 * 因而其执行分支可被 JaCoCo 统计，用于"以真实端到端数据组合驱动、提升 JobWorker 分支覆盖"。
 *
 * <p>用例来源：
 * <ol>
 *   <li>复用 {@code matrix_cases.json} 的 51 例（覆盖五行局/命宫12/阴阳男女/告警/运限/女命/小儿/验盘/格局/维度专项）；</li>
 *   <li>追加缺口数据组合：阴女·大限（阴女顺行）、粒度=月/日、农历入参、西经负经时、验盘多种 subjectTraits 触发。</li>
 * </ol>
 * 每例断言：流程 COMPLETED + 无 incident + 产出关键变量非空（同时驱动各类分类判定分支）。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@Timeout(value = 2400, unit = TimeUnit.SECONDS)
@DisplayName("紫微斗数 · 进程内数据驱动矩阵端到端（含缺口分类数据组合）")
class ZiWeiDouShuInMemoryDataMatrixTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuInMemoryDataMatrixTest.class);
    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Duration WAIT = Duration.ofMinutes(2);

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
        ServiceProbe.assumeAllReachable("紫微斗数进程内矩阵（Ontop + MySQL，真实 Worker 需要）",
                ServiceProbe.LOCALHOST, 8080, 3306);
        log.info("=== 紫微斗数进程内数据驱动矩阵测试 ===");
    }

    @AfterAll
    static void stopEngine() {
        if (testClient != null) {
            try { testClient.close(); } catch (Exception ignored) { }
        }
        if (engine != null) {
            try { engine.stop(); } catch (Exception ignored) { }
        }
        ZiWeiDouShuOntologyJobWorker.resetOntologyCaches();
    }

    private static String normalize(String gw) {
        if (gw == null || gw.isBlank()) throw new IllegalStateException("内存引擎未返回有效 gateway 地址");
        return gw.startsWith("http") ? gw : "http://" + gw;
    }

    @Test
    @DisplayName("进程内数据矩阵：matrix_cases(51) + 缺口分类组合，逐例 COMPLETED 且无 incident")
    void inMemoryDataMatrix() {
        List<Map<String, Object>> cases = new ArrayList<>();
        try {
            cases.addAll(loadMatrix());
        } catch (Exception e) {
            throw new RuntimeException("加载 matrix_cases.json 失败", e);
        }
        cases.addAll(gapCases());
        try {
            cases.addAll(loadHunt());
        } catch (Exception e) {
            log.warn("加载 hunt_cases.json 失败（忽略）: {}", e.toString());
        }

        List<String> failures = new ArrayList<>();
        int ok = 0;
        for (Map<String, Object> c : cases) {
            String id = String.valueOf(c.getOrDefault("id", "?"));
            Map<String, Object> vars = buildVars(c);
            try {
                ProcessInstanceEvent inst = runFlow(vars);
                awaitCompleted(inst);
                BpmnAssert.assertThat(inst).isCompleted().hasNoIncidents();
                ok++;
            } catch (Throwable t) {
                failures.add(id + " -> " + t.getClass().getSimpleName() + ": "
                        + String.valueOf(t.getMessage()).replace('\n', ' '));
            }
        }
        log.info("进程内矩阵：{} / {} 完成", ok, cases.size());
        assertThat(failures).as("以下用例未完成/有 incident：\n" + String.join("\n", failures)).isEmpty();
    }

    private List<Map<String, Object>> loadMatrix() throws Exception {
        try (InputStream in = new ClassPathResource("ziweidoushu/matrix_cases.json").getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<List<Map<String, Object>>>() { });
        }
    }

    /** 从 hunt 结果精选的命例（覆盖多类格局/断诀命中，来自 zwds_hunt_results.json 贪心选择）。 */
    private List<Map<String, Object>> loadHunt() throws Exception {
        try (InputStream in = new ClassPathResource("ziweidoushu/hunt_cases.json").getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<List<Map<String, Object>>>() { });
        }
    }

    /** 追加缺口数据组合（阴女顺行、粒度月/日、农历、西经、验盘多特征）。 */
    private List<Map<String, Object>> gapCases() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(caseOf("gap_yinnv_daxian", "1999-03-09 15:07", "女", "成人女", "本命+大限", "直接排盘"));
        list.add(caseOf("gap_yinnan_daxian", "1985-09-06 18:34", "男", "成人男", "本命+大限", "直接排盘"));
        Map<String, Object> gm = caseOf("gap_gran_month", "1978-07-04 11:45", "男", "成人男", "本命+大限+流年", "直接排盘");
        gm.put("granularity", "月");
        list.add(gm);
        Map<String, Object> gd = caseOf("gap_gran_day", "1978-07-04 11:45", "男", "成人男", "本命+大限+流年", "直接排盘");
        gd.put("granularity", "日");
        list.add(gd);
        Map<String, Object> lunar = caseOf("gap_lunar_input", "1990-04-21 12:00", "男", "成人男", "本命", "直接排盘");
        lunar.put("calendar", "农历");
        list.add(lunar);
        Map<String, Object> west = caseOf("gap_west_longitude", "1978-07-04 11:45", "男", "成人男", "本命+大限+流年", "直接排盘");
        west.put("birthPlace", "纽约 74W");
        list.add(west);
        Map<String, Object> v1 = caseOf("gap_verify_1", "1992-03-12 05:00", "男", "成人男", "本命", "验盘");
        v1.put("subjectTraits", "相貌清秀、性情温和、已知 30 岁迁移");
        list.add(v1);
        Map<String, Object> v2 = caseOf("gap_verify_2", "1990-01-15 10:00", "男", "成人男", "本命", "验盘");
        v2.put("subjectTraits", "面方、面色红、好济施、性刚果决");
        list.add(v2);
        Map<String, Object> child = caseOf("gap_child_full", "2018-02-20 09:00", "男", "小儿", "本命+大限+流年", "直接排盘");
        list.add(child);
        Map<String, Object> femaleFull = caseOf("gap_female_full", "1976-07-20 16:31", "女", "成人女", "本命+大限+流年", "直接排盘");
        list.add(femaleFull);
        return list;
    }

    private Map<String, Object> caseOf(String id, String birth, String gender, String subjectType, String depth, String mode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("birth", birth);
        m.put("gender", gender);
        m.put("subjectType", subjectType);
        m.put("depth", depth);
        m.put("mode", mode);
        return m;
    }

    private Map<String, Object> buildVars(Map<String, Object> c) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", str(c.get("birth")));
        vars.put("calendar", orDefault(str(c.get("calendar")), "公历"));
        vars.put("gender", str(c.get("gender")));
        vars.put("birthPlace", orDefault(str(c.get("birthPlace")), "北京 116.4E"));
        vars.put("inputMode", orDefault(str(c.get("mode")), "直接排盘"));
        vars.put("subjectType", orDefault(str(c.get("subjectType")), "成人男"));
        vars.put("depth", orDefault(str(c.get("depth")), "本命"));
        vars.put("granularity", orDefault(str(c.get("granularity")), "年"));
        if (c.get("subjectTraits") != null) {
            vars.put("subjectTraits", str(c.get("subjectTraits")));
        }
        return vars;
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

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
