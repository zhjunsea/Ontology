package com.ocean.ontologyframework.ziweidoushu;

import com.ocean.utilities.ProcessOrchestrator;
import com.ocean.utilities.ServiceProbe;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Incident;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数推命流程 · 真实引擎端到端（第 8 步；连 {@code localhost:26500}，生产
 * {@link ZiWeiDouShuOntologyJobWorker} 消费）。
 *
 * <p>与内存引擎类的差异：本类经真实 Camunda 8 / Zeebe 网关部署 BPMN，由独立进程内的生产
 * Worker 消费全部 {@code serviceTask}；断言基于流程实例状态与流程变量（{@link ProcessOrchestrator}）。
 * 环境不可达时经 {@link ServiceProbe} 自动跳过。
 *
 * <p>用例清单见《ZiWeiDouShu 应用-测试设计文档》§6.3（9 项：P-1~P-5 + B-1~B-3 + N-1）。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 300, unit = TimeUnit.SECONDS)
@DisplayName("紫微斗数推命流程 · 真实引擎端到端（连 localhost:26500，生产 Worker 消费）")
class ZiWeiDouShuBpmnRealEngineTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuBpmnRealEngineTest.class);

    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Duration WAIT = Duration.ofMinutes(2);

    private static final Pattern TST_PATTERN = Pattern.compile("TST=([-0-9.]+)");

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @Autowired
    private CamundaClient client;

    @BeforeAll
    static void requireCamunda() {
        ServiceProbe.assumeReachable("Camunda REST 网关", ServiceProbe.LOCALHOST, 9080);
    }

    @BeforeEach
    void deploy() {
        assertThat(Files.isRegularFile(Path.of(bpmnPath)))
                .as("BPMN 文件应存在: %s", bpmnPath).isTrue();

        boolean deployed = ProcessOrchestrator.deployIfAbsent(client, PROCESS_ID, List.of(bpmnPath));
        assertThat(deployed).as("应成功部署流程").isTrue();
        log.info("✅ 已部署 BPMN | {}", bpmnPath);
    }

    // ============================ P-1 ============================
    @Test
    @Order(1)
    @DisplayName("P-1 主干最短路径（S1 成人男·本命）：真实引擎全流程完成，生产 Worker 消费全部 serviceTask，无 incident")
    void chartProcessCompletesMainPath() {
        Map<String, Object> vars = runAndAwaitCompleted(baseVars("1990-05-15 14:30", "男", "成人男", "本命"));
        assertThat(vars.get("chartBase")).as("节点 1 应产出 chartBase").isNotNull();
        log.info("✅ P-1 完成 | chartBase={}", vars.get("chartBase"));
    }

    // ============================ P-2 ============================
    @Test
    @Order(2)
    @DisplayName("P-2 女命·全链（S7）：W + 13→14→15 真实引擎全链完成，无 incident")
    void chartProcessCompletesFemaleFullDepth() {
        Map<String, Object> vars = runAndAwaitCompleted(scene("1988-09-09 17:30", "女", "成人女",
                "本命+大限+流年", "武汉 114.3E"));
        assertThat(vars.get("chartBase")).isNotNull();
        log.info("✅ P-2 完成 | chartBase={}", vars.get("chartBase"));
    }

    // ============================ P-3 ============================
    @Test
    @Order(3)
    @DisplayName("P-3 小儿·大限（S8）：C + 节点13 真实引擎完成，无 incident")
    void chartProcessCompletesChildMajorLimit() {
        Map<String, Object> vars = runAndAwaitCompleted(scene("2015-06-06 06:30", "女", "小儿",
                "本命+大限", "杭州 120.2E"));
        assertThat(vars.get("chartBase")).isNotNull();
        log.info("✅ P-3 完成 | chartBase={}", vars.get("chartBase"));
    }

    // ============================ P-4 ============================
    @Test
    @Order(4)
    @DisplayName("P-4 验盘模式（S5）：验盘分支 + G1b 真实引擎完成，无 incident")
    void chartProcessCompletesVerifyMode() {
        Map<String, Object> vars = scene("1992-03-12 05:00", "男", "成人男", "本命", "成都 104.1E");
        vars.put("inputMode", "验盘");
        vars.put("subjectTraits", "相貌清秀、性情温和、已知 30 岁迁移");
        Map<String, Object> after = runAndAwaitCompleted(vars);
        assertThat(after.get("chartBase")).isNotNull();
        log.info("✅ P-4 完成 | chartBase={}", after.get("chartBase"));
    }

    // ============================ P-5 ============================
    @Test
    @Order(5)
    @DisplayName("P-5 全链·告警判定（S6）：真实引擎完成；alertTriggered 与 messageStatus 一致")
    void chartProcessCompletesAlertPath() {
        Map<String, Object> after = runAndAwaitCompleted(scene("1960-01-01 12:00", "男", "成人男",
                "本命+大限+流年", "北京 116.4E"));

        boolean triggered = Boolean.TRUE.equals(after.get("alertTriggered"))
                || "true".equalsIgnoreCase(String.valueOf(after.get("alertTriggered")));
        String messageStatus = after.get("messageStatus") == null ? "" : String.valueOf(after.get("messageStatus"));
        if (triggered) {
            assertThat(messageStatus).as("alertTriggered=true 时节点 A 应外发并置 SENT").isEqualTo("SENT");
        } else {
            assertThat(messageStatus).as("alertTriggered=false 时不应外发").isNotEqualTo("SENT");
        }
        log.info("✅ P-5 完成 | alertTriggered={} | messageStatus={}", triggered, messageStatus);
    }

    // ============================ B-1 ============================
    @Test
    @Order(6)
    @DisplayName("B-1 缺省变量兜底：仅传 birth，缺省 成人男/本命/年 生效，真实引擎完成")
    void defaultVariablesFallback() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", "1990-05-15 14:30");
        Map<String, Object> after = runAndAwaitCompleted(vars);
        assertThat(String.valueOf(after.get("chartBase")))
                .as("缺省 subjectType/depth 应兜底为 成人男/本命")
                .contains("SUBJECT=成人男").contains("DEPTH=本命");
    }

    // ============================ B-2 ============================
    @Test
    @Order(7)
    @DisplayName("B-2 农历输入（表未就绪）：完成且 chartBase 含 LUNAR_UNCONVERTED=true")
    void lunarInputMarkedPending() {
        Map<String, Object> vars = new LinkedHashMap<>();
        Map<String, Object> birth = new LinkedHashMap<>();
        birth.put("year", 1990);
        birth.put("month", 4);
        birth.put("day", 21);
        birth.put("hour", 14);
        birth.put("minute", 30);
        birth.put("calendar", "农历");
        vars.put("birth", birth);
        vars.put("calendar", "农历");
        vars.put("gender", "男");
        vars.put("birthPlace", "北京 116.4E");
        vars.put("subjectType", "成人男");
        vars.put("depth", "本命");

        Map<String, Object> after = runAndAwaitCompleted(vars);
        assertThat(String.valueOf(after.get("chartBase")))
                .as("农历→公历表未就绪应标 LUNAR_UNCONVERTED=true")
                .contains("LUNAR_UNCONVERTED=true");
        log.info("✅ B-2 完成 | chartBase={}", after.get("chartBase"));
    }

    // ============================ B-3 ============================
    @Test
    @Order(8)
    @DisplayName("B-3 西经（负经度）：完成且 chartBase 的真太阳时 TST 为负")
    void westLongitudeCorrection() {
        Map<String, Object> after = runAndAwaitCompleted(scene("1990-05-15 14:30", "男", "成人男",
                "本命", "New York 74.0W"));

        String chartBase = String.valueOf(after.get("chartBase"));
        Matcher m = TST_PATTERN.matcher(chartBase);
        assertThat(m.find()).as("chartBase 应含 TST= | chartBase=%s", chartBase).isTrue();
        double tst = Double.parseDouble(m.group(1));
        assertThat(tst).as("西经 74°W 的真太阳时偏移应为负（≈-12.93h）").isNegative();
        log.info("✅ B-3 完成 | TST={}h", tst);
    }

    // ============================ N-1 ============================
    @Test
    @Order(9)
    @DisplayName("N-1 缺 birth：节点1 抛错 → incident，流程不完成")
    void invalidBirthYieldsIncident() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("gender", "男");
        long key = ProcessOrchestrator.start(client, PROCESS_ID, vars);
        String status = awaitTerminalOrTimeout(key, Duration.ofSeconds(30));
        assertThat(status).as("缺 birth 应致 incident（PARSE_BIRTH_INPUT_FAILED）").isEqualTo("INCIDENT");
        log.info("✅ N-1 命中 incident | key={} | status={}", key, status);
    }

    // ============================ 工具 ============================
    private Map<String, Object> baseVars(String birth, String gender, String subjectType, String depth) {
        return scene(birth, gender, subjectType, depth, "北京 116.4E");
    }

    private Map<String, Object> scene(String birth, String gender, String subjectType,
                                      String depth, String birthPlace) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", birth);
        vars.put("calendar", "公历");
        vars.put("gender", gender);
        vars.put("birthPlace", birthPlace);
        vars.put("inputMode", "直接排盘");
        vars.put("subjectType", subjectType);
        vars.put("depth", depth);
        vars.put("granularity", "年");
        return vars;
    }

    /** 启动流程并轮询至 COMPLETED，返回完成后的流程变量；遇 incident/terminated 立即失败。 */
    private Map<String, Object> runAndAwaitCompleted(Map<String, Object> vars) {
        long key = ProcessOrchestrator.start(client, PROCESS_ID, vars);
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
            String status = info == null ? "RUNNING"
                    : ProcessOrchestrator.statusOf(info.state(), info.hasIncident());
            if ("COMPLETED".equals(status)) {
                return ProcessOrchestrator.readVariables(client, key);
            }
            if ("INCIDENT".equals(status) || "TERMINATED".equals(status)) {
                throw new AssertionError("流程异常终止 | status=" + status + " | key=" + key
                        + " | incidents=" + incidentSignatures(key));
            }
            sleep();
        }
        throw new AssertionError("流程未在 " + WAIT + " 内完成 | key=" + key);
    }

    /** 轮询至非 RUNNING 状态或超时，返回最后一次可读状态。 */
    private String awaitTerminalOrTimeout(long key, Duration window) {
        long deadline = System.nanoTime() + window.toNanos();
        String last = "RUNNING";
        while (System.nanoTime() < deadline) {
            ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
            if (info != null) {
                last = ProcessOrchestrator.statusOf(info.state(), info.hasIncident());
                if (!"RUNNING".equals(last)) {
                    return last;
                }
            }
            sleep();
        }
        return last;
    }

    /** 读取流程实例的 incident 明细（elementId:errorType:errorMessage），便于定位异常终止根因。 */
    private String incidentSignatures(long key) {
        try {
            List<Incident> items = client.newIncidentSearchRequest()
                    .filter(f -> f.processInstanceKey(key))
                    .send().join().items();
            if (items == null || items.isEmpty()) {
                return "[]";
            }
            return items.stream()
                    .map(i -> i.getElementId() + ":" + i.getErrorType() + ":" + i.getErrorMessage())
                    .toList().toString();
        } catch (Exception e) {
            return "[incident 查询失败: " + e + "]";
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
