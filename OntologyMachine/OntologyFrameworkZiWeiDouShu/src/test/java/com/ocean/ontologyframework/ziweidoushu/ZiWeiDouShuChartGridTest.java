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
 * 紫微斗数 · 全星盘网格端到端（进程内 Zeebe 引擎 + 真实生产 Worker，计入 JaCoCo）。
 *
 * <p>目的：在既有 {@code matrix_cases.json}(本命为主) 之外，补一组**推演维度**组合，把 12 个命宫命例
 * 统一提升到「本命+大限+流年」全链，并叠加「粒度=月/日」「农历双性别」「东/西经」「验盘多特征图」「小儿全链」，
 * 以驱动 {@code handleAnalyzeMajorLimit}/{@code handleAnalyzeAnnualFortune}/{@code handleAnalyzePalaceOverlay}
 * （含流月/流日四盘叠合）等剩余 handler 分支——覆盖「所有类型的星盘」在不同推演深度/命主类别/输入模式下的组合。
 *
 * <p>命例均取自 {@code matrix_cases.json} 的既证可用生日（仅改推演组合不改输入），逐例断言流程
 * COMPLETED 且无 incident。Ontop(8080)/MySQL(3306) 不可达时经 {@link ServiceProbe} 自动跳过，不阻断离线构建。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@Timeout(value = 2400, unit = TimeUnit.SECONDS)
@DisplayName("紫微斗数 · 全星盘网格端到端（全链×粒度月日×农历×经纬×验盘×小儿）")
class ZiWeiDouShuChartGridTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuChartGridTest.class);
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
        ServiceProbe.assumeAllReachable("紫微斗数全星盘网格（Ontop + MySQL，真实 Worker 需要）",
                ServiceProbe.LOCALHOST, 8080, 3306);
        log.info("=== 紫微斗数全星盘网格端到端测试 ===");
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

    private Map<String, Object> caseOf(String id, String birth, String gender, String subjectType,
                                       String depth, String mode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("birth", birth);
        m.put("gender", gender);
        m.put("subjectType", subjectType);
        m.put("depth", depth);
        m.put("mode", mode);
        return m;
    }

    private List<Map<String, Object>> buildGrid() {
        List<Map<String, Object>> list = new ArrayList<>();
        // 12 个命宫（子…亥）× 全链（本命+大限+流年）——驱动大限/流年/叠宫分支（各命宫组合各不相同）。
        String[][] mings = {
                {"cg_full_zi", "1970-03-17 05:36", "男", "成人男"},
                {"cg_full_chou", "1976-11-09 18:21", "男", "成人男"},
                {"cg_full_yin", "1993-11-22 17:39", "男", "成人男"},
                {"cg_full_mao", "1980-11-18 16:07", "男", "成人男"},
                {"cg_full_chen", "2020-12-13 15:08", "男", "成人男"},
                {"cg_full_si", "1967-03-03 19:05", "男", "成人男"},
                {"cg_full_wu", "2017-02-16 17:09", "男", "成人男"},
                {"cg_full_wei", "1999-03-09 15:07", "女", "成人女"},
                {"cg_full_shen", "2009-08-24 00:53", "男", "成人男"},
                {"cg_full_you", "2000-05-10 15:58", "男", "成人男"},
                {"cg_full_xu", "1973-01-08 06:21", "男", "成人男"},
                {"cg_full_hai", "1985-09-06 18:34", "男", "成人男"}
        };
        for (String[] m : mings) {
            list.add(caseOf(m[0], m[1], m[2], m[3], "本命+大限+流年", "直接排盘"));
        }
        // 粒度=月/日（全链，触发流月/流日与四盘叠合层）。
        Map<String, Object> gm = caseOf("cg_gran_month", "1970-03-17 05:36", "男", "成人男", "本命+大限+流年", "直接排盘");
        gm.put("granularity", "月");
        list.add(gm);
        Map<String, Object> gd = caseOf("cg_gran_day", "2017-02-16 17:09", "男", "成人男", "本命+大限+流年", "直接排盘");
        gd.put("granularity", "日");
        list.add(gd);
        // 农历入参（男/女）——双历换算分支。
        Map<String, Object> lm = caseOf("cg_lunar_m", "1990-04-21 12:00", "男", "成人男", "本命", "直接排盘");
        lm.put("calendar", "农历");
        list.add(lm);
        Map<String, Object> lf = caseOf("cg_lunar_f", "1990-04-21 12:00", "女", "成人女", "本命+大限", "直接排盘");
        lf.put("calendar", "农历");
        list.add(lf);
        // 东/西经（真太阳时校正分支）。
        Map<String, Object> west = caseOf("cg_west", "1978-07-04 11:45", "男", "成人男", "本命+大限+流年", "直接排盘");
        west.put("birthPlace", "纽约 74W");
        list.add(west);
        Map<String, Object> east = caseOf("cg_east", "1995-06-15 09:30", "女", "成人女", "本命", "直接排盘");
        east.put("birthPlace", "东京 139.7E");
        list.add(east);
        // 验盘（多种 subjectTraits → 定盘加权/四态分支）。
        Map<String, Object> va = caseOf("cg_verify_a", "1992-03-12 05:00", "男", "成人男", "本命", "验盘");
        va.put("subjectTraits", "相貌清秀、性情温和、已知30岁迁移");
        list.add(va);
        Map<String, Object> vb = caseOf("cg_verify_b", "1990-01-15 10:00", "男", "成人男", "本命", "验盘");
        vb.put("subjectTraits", "面方、面色红、好济施、性刚果决");
        list.add(vb);
        Map<String, Object> vc = caseOf("cg_verify_c", "1988-08-08 08:08", "女", "成人女", "本命+大限", "验盘");
        vc.put("subjectTraits", "七杀背厚眉宽、性刚果决");
        list.add(vc);
        // 小儿全链（大限/流年对小儿口径）。
        list.add(caseOf("cg_child_full", "2019-07-03 11:34", "女", "小儿", "本命+大限+流年", "直接排盘"));
        return list;
    }

    @Test
    @DisplayName("全星盘网格：22 例（12 命宫全链 + 粒度月/日 + 农历男女 + 经纬 + 验盘×3 + 小儿全链），逐例 COMPLETED 且无 incident")
    void chartGrid() {
        List<Map<String, Object>> cases = buildGrid();
        List<String> failures = new ArrayList<>();
        int ok = 0;
        for (Map<String, Object> c : cases) {
            String id = String.valueOf(c.getOrDefault("id", "?"));
            try {
                ProcessInstanceEvent inst = runFlow(buildVars(c));
                awaitCompleted(inst);
                BpmnAssert.assertThat(inst).isCompleted().hasNoIncidents();
                ok++;
            } catch (Throwable t) {
                failures.add(id + " -> " + t.getClass().getSimpleName() + ": "
                        + String.valueOf(t.getMessage()).replace('\n', ' '));
            }
        }
        log.info("全星盘网格：{} / {} 完成", ok, cases.size());
        assertThat(failures).as("以下用例未完成/有 incident：\n" + String.join("\n", failures)).isEmpty();
        assertThat(ok).as("全部用例应完成").isEqualTo(cases.size());
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
