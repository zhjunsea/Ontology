package com.ocean.ontologyframework.ziweidoushu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

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
 * 紫微斗数 · 全量矩阵端到端测试（连 localhost:26500 真实 Camunda，生产 Worker 消费）。
 *
 * <p>数据驱动矩阵（{@code src/test/resources/ziweidoushu/matrix_cases.json}）覆盖：
 * 三基线金标准、五行局 5/5、阴阳男女 4/4、命宫 12/12、空宫、生年四化、庙陷、
 * 告警 4 类、运限 3 档、女命/小儿专论、验盘态、非法入参，以及「财禄夹马/众水朝东」
 * 与第 33 轮修复的 11 个格局（机月同梁/日月藏辉/财与囚仇/杀破狼/紫微七杀化权/
 * 财荫夹印/财印夹禄/马头带剑/荫印拱身/金舆扶驾/君子在野；详见测试设计文档 §5/§6）。
 *
 * <p><b>维度专项（dim_ 前缀 5 例）</b>：进一步覆盖用户点名的全部推演维度——生年四化落宫
 * （{@code 四化=}）、星曜化气（{@code 化气=}）、形性賦（{@code 形性=}）、諸星所主（{@code 所主=}）、
 * 三方四正（{@code 三方四正=}）、星辰生克制化（{@code 生克=}）、命格等第（{@code 命格=}）、
 * 流派差异标注（{@code 流派=}）、四化飞星深层（{@code 飞星=}）、大限十二限（{@code 大限=}）、
 * 十二宫弱强（{@code 宫强弱=}）、空宫借星结果（{@code 借星=}）、夹宫（{@code 夹=}）。
 *
 * <p>环境不可达（Camunda 9080 / 应用 9089）时经 {@link ServiceProbe} 自动跳过，不阻断离线构建。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 1200, unit = TimeUnit.SECONDS)
@DisplayName("紫微斗数 · 全量矩阵端到端（数据驱动，真实 Camunda + 生产 Worker）")
class ZiWeiDouShuPatternMatrixTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuPatternMatrixTest.class);

    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Duration WAIT = Duration.ofMinutes(3);

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @Autowired
    private CamundaClient client;

    @BeforeAll
    static void requireInfra() {
        ServiceProbe.assumeReachable("Camunda REST 网关", ServiceProbe.LOCALHOST, 9080);
        ServiceProbe.assumeReachable("紫微斗数应用（生产 Worker）", ServiceProbe.LOCALHOST, 9089);
        log.info("=== 紫微斗数全量矩阵端到端测试 ===");
    }

    @BeforeEach
    void deploy() {
        assertThat(Files.isRegularFile(Path.of(bpmnPath))).as("BPMN 文件应存在: %s", bpmnPath).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(client, PROCESS_ID, List.of(bpmnPath)))
                .as("应成功部署流程").isTrue();
    }

    // ============================ 全量数据矩阵 ============================
    @Test
    @Order(1)
    @DisplayName("T-MATRIX 全量矩阵：51 例（基线/五行局/阴阳男女/命宫12/空宫/四化/庙陷/告警/运限/女命/小儿/验盘/修复格局/财禄夹马/众水朝东 + 维度专项5：四化·化气·形性·所主·三方四正·生克制化·命格·流派·飞星·大限十二限·宫强弱·借星·夹）")
    void t_matrix_all_cases() throws Exception {
        List<Map<String, Object>> cases = loadCases();
        assertThat(cases).as("矩阵用例不得为空").isNotEmpty();

        List<String> failures = new ArrayList<>();
        int pass = 0;
        for (Map<String, Object> c : cases) {
            String id = str(c.get("id"));
            Map<String, Object> vars = buildVars(c);
            Map<String, Object> after;
            try {
                after = runAndAwaitCompleted(vars);
            } catch (AssertionError e) {
                failures.add(id + " 流程异常: " + e.getMessage());
                log.error("❌ {} {}", id, e.getMessage());
                continue;
            }
            List<String> errs = assertCase(c, after);
            if (errs.isEmpty()) {
                pass++;
                log.info("✅ {} {} [{}]", id, str(c.get("birth")), str(c.get("note")));
            } else {
                failures.add(id + " " + String.join("；", errs));
                log.error("❌ {} => {}", id, String.join("；", errs));
            }
            // 低负载错峰：避免瞬时高并发压垮 Camunda Partition-1（见测试设计文档·引擎脆弱性）
            sleep(150);
        }
        log.info("矩阵结果：通过 {}/{}", pass, cases.size());
        assertThat(failures).as("矩阵存在失败用例：\n" + String.join("\n", failures)).isEmpty();
    }

    // ============================ 非法入参 ============================
    @Test
    @Order(2)
    @DisplayName("T-INVALID 缺 birth：节点1 抛错 → incident，流程不完成")
    void t_invalid_birth_incident() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("gender", "男");
        long key = ProcessOrchestrator.start(client, PROCESS_ID, vars);
        String status = awaitTerminalOrTimeout(key, Duration.ofSeconds(40));
        assertThat(status).as("缺 birth 应致 incident（PARSE_BIRTH_INPUT_FAILED）").isEqualTo("INCIDENT");
        log.info("✅ 非法入参命中 incident | key={} status={}", key, status);
    }

    // ============================ 断言核心 ============================
    private List<String> assertCase(Map<String, Object> c, Map<String, Object> v) {
        List<String> errs = new ArrayList<>();
        String skeleton = str(v.get("chartSkeleton"));
        String mainStars = str(v.get("mainStars"));
        String patternResult = str(v.get("patternResult"));

        if (skeleton.isBlank()) { errs.add("chartSkeleton 为空"); }
        if (mainStars.isBlank()) { errs.add("mainStars 为空"); }

        // 结构性特征（以「|」分隔多条）
        String feat = str(c.get("expectFeature"));
        if (!feat.isBlank()) {
            for (String token : feat.split("\\|")) {
                token = token.trim();
                if (token.isEmpty()) { continue; }
                if (token.startsWith("命宫=")) {
                    if (!skeleton.contains(token)) { errs.add("期望 " + token + " 未见于 chartSkeleton"); }
                } else if (token.startsWith("五行局=")) {
                    if (!skeleton.contains(token)) { errs.add("期望 " + token + " 未见于 chartSkeleton"); }
                } else if (token.startsWith("紫微=")) {
                    String zhi = token.substring("紫微=".length());
                    if (!mainStars.contains("紫微在" + zhi)) { errs.add("期望 紫微在" + zhi + " 未见于 mainStars"); }
                } else if (token.startsWith("空宫=true")) {
                    if (!str(v.get("emptyPalaceResult")).contains("宫空")) { errs.add("期望有空宫，emptyPalaceResult 未含「宫空」"); }
                } else if (token.startsWith("告警=")) {
                    String type = token.substring("告警=".length());
                    boolean triggered = Boolean.TRUE.equals(v.get("alertTriggered"));
                    String blob = str(v.get("reading")) + str(v.get("structuredOutput")) + str(v.get("result"));
                    if (!triggered) { errs.add("期望触发告警但 alertTriggered!=true"); }
                    if (!blob.contains(type)) { errs.add("告警类型「" + type + "」未见于 reading/structuredOutput"); }
                } else if (token.startsWith("四化=")) {
                    String exp = token.substring("四化=".length());
                    if (!str(v.get("fourTransform")).contains(exp)) { errs.add("期望四化「" + exp + "」未见于 fourTransform=" + str(v.get("fourTransform"))); }
                } else if (token.startsWith("化气=")) {
                    String exp = token.substring("化气=".length());
                    if (!str(v.get("transformationQi")).contains(exp)) { errs.add("期望化气「" + exp + "」未见于 transformationQi"); }
                } else if (token.startsWith("形性=")) {
                    String exp = token.substring("形性=".length());
                    if (!str(v.get("starXingXing")).contains(exp)) { errs.add("期望形性「" + exp + "」未见于 starXingXing"); }
                } else if (token.startsWith("所主=")) {
                    String exp = token.substring("所主=".length());
                    if (!str(v.get("starNature")).contains(exp)) { errs.add("期望所主「" + exp + "」未见于 starNature"); }
                } else if (token.startsWith("三方四正=")) {
                    String exp = token.substring("三方四正=".length());
                    if (!str(v.get("surroundPalaces")).contains(exp)) { errs.add("期望三方四正「" + exp + "」未见于 surroundPalaces"); }
                } else if (token.startsWith("夹=")) {
                    String exp = token.substring("夹=".length());
                    if (!str(v.get("clampRelations")).contains(exp)) { errs.add("期望夹宫「" + exp + "」未见于 clampRelations=" + str(v.get("clampRelations"))); }
                } else if (token.startsWith("借星=")) {
                    String exp = token.substring("借星=".length());
                    if (!str(v.get("emptyPalaceResult")).contains(exp)) { errs.add("期望借星「" + exp + "」未见于 emptyPalaceResult"); }
                } else if (token.startsWith("宫强弱=")) {
                    String exp = token.substring("宫强弱=".length());
                    if (!str(v.get("palaceStrength")).contains(exp)) { errs.add("期望宫强弱「" + exp + "」未见于 palaceStrength"); }
                } else if (token.startsWith("生克=")) {
                    String exp = token.substring("生克=".length());
                    if (!str(v.get("phaseRelations")).contains(exp)) { errs.add("期望生克「" + exp + "」未见于 phaseRelations"); }
                } else if (token.startsWith("命格=")) {
                    String exp = token.substring("命格=".length());
                    if (!str(v.get("patternGrade")).contains(exp)) { errs.add("期望命格「" + exp + "」未见于 patternGrade=" + str(v.get("patternGrade"))); }
                } else if (token.startsWith("流派=")) {
                    String exp = token.substring("流派=".length());
                    if (!str(v.get("schoolNote")).contains(exp)) { errs.add("期望流派「" + exp + "」未见于 schoolNote"); }
                } else if (token.startsWith("飞星=")) {
                    String exp = token.substring("飞星=".length());
                    if (!str(v.get("flyingStars")).contains(exp)) { errs.add("期望飞星「" + exp + "」未见于 flyingStars"); }
                } else if (token.startsWith("大限=")) {
                    String exp = token.substring("大限=".length());
                    if (!str(v.get("majorLimit")).contains(exp)) { errs.add("期望大限「" + exp + "」未见于 majorLimit=" + str(v.get("majorLimit"))); }
                } else if (token.startsWith("十二限=")) {
                    String exp = token.substring("十二限=".length());
                    if (!str(v.get("yunxianSequence")).contains(exp)) { errs.add("期望十二限「" + exp + "」未见于 yunxianSequence"); }
                }
            }
        }

        // 格局命中
        String pat = str(c.get("expectPattern"));
        if (!pat.isBlank() && !patternResult.contains(pat)) {
            errs.add("期望成格「" + pat + "」未见于 patternResult");
        }
        return errs;
    }

    // ============================ 工具 ============================
    private List<Map<String, Object>> loadCases() throws Exception {
        try (InputStream in = new ClassPathResource("ziweidoushu/matrix_cases.json").getInputStream()) {
            return new ObjectMapper().readValue(in, new TypeReference<List<Map<String, Object>>>() { });
        }
    }

    private Map<String, Object> buildVars(Map<String, Object> c) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", str(c.get("birth")));
        vars.put("calendar", "公历");
        vars.put("gender", str(c.get("gender")));
        vars.put("birthPlace", "北京 116.4E");
        vars.put("inputMode", str(c.get("mode")));
        vars.put("subjectType", str(c.get("subjectType")));
        vars.put("depth", str(c.get("depth")));
        vars.put("granularity", "年");
        if (c.get("subjectTraits") != null) {
            vars.put("subjectTraits", str(c.get("subjectTraits")));
        }
        return vars;
    }

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
                throw new AssertionError("status=" + status + " key=" + key + " incidents=" + incidentSignatures(key));
            }
            sleep(300);
        }
        throw new AssertionError("流程未在 " + WAIT + " 内完成 | key=" + key);
    }

    private String awaitTerminalOrTimeout(long key, Duration window) {
        long deadline = System.nanoTime() + window.toNanos();
        String last = "RUNNING";
        while (System.nanoTime() < deadline) {
            ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
            if (info != null) {
                last = ProcessOrchestrator.statusOf(info.state(), info.hasIncident());
                if (!"RUNNING".equals(last)) { return last; }
            }
            sleep(500);
        }
        return last;
    }

    private String incidentSignatures(long key) {
        try {
            List<Incident> items = client.newIncidentSearchRequest()
                    .filter(f -> f.processInstanceKey(key)).send().join().items();
            if (items == null || items.isEmpty()) { return "[]"; }
            return items.stream().map(i -> i.getElementId() + ":" + i.getErrorType() + ":" + i.getErrorMessage()).toList().toString();
        } catch (Exception e) {
            return "[incident 查询失败: " + e + "]";
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
