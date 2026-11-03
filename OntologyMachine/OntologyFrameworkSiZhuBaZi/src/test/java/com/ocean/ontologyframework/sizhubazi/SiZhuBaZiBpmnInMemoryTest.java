package com.ocean.ontologyframework.sizhubazi;

import com.ocean.utilities.ProcessOrchestrator;
import com.ocean.utilities.ServiceProbe;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import io.camunda.zeebe.process.test.filters.RecordStream;
import io.camunda.zeebe.process.test.filters.StreamFilter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
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
 * 四柱命理推演流程 · 内存引擎端到端（生产 {@code SiZhuBaZiOntologyJobWorker} 驱动）。
 *
 * <p>以 {@code zeebe-process-test} 内存引擎部署 {@code SiZhuBaZiProcess}，由生产 Worker 消费全部
 * 21 个 serviceTask，覆盖主干节点顺序、三个排他网关分支（G1 输入模式 / G2 对象类型 / G3 吉凶级别）
 * 与边界/负向场景，断言实例完成（或按预期产生 incident）、无意外 incident。
 *
 * <p><b>环境前置</b>：RabbitMQ 可达 {@code localhost:5672}（告警分支的 {@code sendAlert} 需外发；
 * 未就绪时本类整体跳过）。本体由生产 Worker 从 {@code ontology/sizhubazi-all.owl} 加载，无需数据库。
 */
@SpringBootTest(classes = SiZhuBaZiApplication.class)
@ActiveProfiles("SiZhuBaZiBPMN")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("四柱命理推演流程 · 内存引擎端到端（生产 Worker 驱动）")
class SiZhuBaZiBpmnInMemoryTest {

    private static final Logger log = LoggerFactory.getLogger(SiZhuBaZiBpmnInMemoryTest.class);

    private static final String PROCESS_ID = "SiZhuBaZiProcess";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    /** 单命主干（节点 1~14，G1「单命」分支，顺序稳定）。 */
    private static final List<String> SINGLE_MAIN = List.of(
            "Task_ParseInput", "Task_CastPillars", "Task_NayinHiddenStems", "Task_GanzhiRelations",
            "Task_TenGodsKin", "Task_Deities", "Task_Strength", "Task_Pattern", "Task_YongShen",
            "Task_ChartLevel", "Task_FortuneCycles", "Task_Timing", "Task_Synthesize",
            "Task_CategoryVerdicts");

    /** 公共尾链（G2 汇聚 → 节点 15 劝诫 → 汇合 J → 节点 16 汇总 → 级别网关 → 节点 17 落库返回）。 */
    private static final List<String> COMMON_TAIL = List.of(
            "Task_MoralAdvice", "Task_Aggregate", "Task_PersistReturn", "End_01");

    private static ZeebeTestEngine engine;
    private static ZeebeClient testClient;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @BeforeAll
    static void requireRabbitMq() {
        ServiceProbe.assumeAllReachable("SiZhuBaZi 内存引擎测试依赖（RabbitMQ，供 sendAlert）",
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

    // ============================ 正向：单命主干 ============================

    @Test
    @DisplayName("P-1 单命·男命全链完成：Parse→Cast→…→CategoryVerdicts→MoralAdvice→Aggregate→Persist，无 incident")
    void t_single_male_full_chain() {
        ProcessInstanceEvent instance = runCompleted(scenario(
                "男命示例", "1990-05-15 08:30", "男", "男命", "单命"));
        BpmnAssert.assertThat(instance)
                .hasPassedElementsInOrder(concat(SINGLE_MAIN, COMMON_TAIL));

        long key = instance.getProcessInstanceKey();
        assertThat(variableValue(key, "fourPillars")).as("四柱应非空").isNotBlank();
        assertThat(variableValue(key, "reading")).as("命书结论应非空").isNotBlank();
        assertThat(variableValue(key, "caseRecord")).as("命例记录应非空").isNotBlank();
    }

    @Test
    @DisplayName("P-2 单命·女命：经 G2「女命」分支走 Task_FemaleChart，全链完成")
    void t_single_female_subject_branch() {
        ProcessInstanceEvent instance = runCompleted(scenario(
                "女命示例", "1992-08-20 14:30", "女", "女命", "单命"));
        BpmnAssert.assertThat(instance)
                .hasPassedElement("Task_FemaleChart")
                .hasNotPassedElement("Task_ChildChart")
                .hasNotPassedElement("Task_MarriageMatch")
                .hasPassedElementsInOrder(concat(
                        List.of("Task_CategoryVerdicts", "Task_FemaleChart"), COMMON_TAIL));

        assertThat(variableValue(instance.getProcessInstanceKey(), "femaleVerdict"))
                .as("女命专论应非空").isNotBlank();
    }

    @Test
    @DisplayName("P-3 合婚模式：经 G1「合婚」分支走 Task_MarriageMatch，汇合 J 后完成")
    void t_marriage_mode_branch() {
        ProcessInstanceEvent instance = runCompleted(scenario(
                "合婚示例", "1988-03-10 06:00", "男", "男命", "合婚", "partnerBirth", "1990-07-07 18:00"));
        BpmnAssert.assertThat(instance)
                .hasPassedElement("Task_MarriageMatch")
                .hasNotPassedElement("Task_CastPillars")
                .hasPassedElementsInOrder("Task_ParseInput", "Task_MarriageMatch",
                        "Task_Aggregate", "Task_PersistReturn", "End_01");

        assertThat(variableValue(instance.getProcessInstanceKey(), "marriageConclusion"))
                .as("合婚结论应非空").isNotBlank();
    }

    @Test
    @DisplayName("B-1 单命·小儿：经 G2「小儿」分支走 Task_ChildChart，全链完成")
    void t_child_subject_branch() {
        ProcessInstanceEvent instance = runCompleted(scenario(
                "小儿示例", "2020-11-01 09:15", "男", "小儿", "单命"));
        BpmnAssert.assertThat(instance)
                .hasPassedElement("Task_ChildChart")
                .hasNotPassedElement("Task_FemaleChart")
                .hasPassedElementsInOrder(concat(
                        List.of("Task_CategoryVerdicts", "Task_ChildChart"), COMMON_TAIL));

        assertThat(variableValue(instance.getProcessInstanceKey(), "childVerdict"))
                .as("小儿关杀应非空").isNotBlank();
    }

    @Test
    @DisplayName("B-2 早晚子时（23:30）：日柱按子初换日进位，与次日 00:30 同柱")
    void t_late_zi_pillar_rolls_next_day() {
        ProcessInstanceEvent lateZi = runCompleted(scenario(
                "晚子时", "1990-05-15 23:30", "男", "男命", "单命"));
        ProcessInstanceEvent earlyNext = runCompleted(scenario(
                "次日早子时", "1990-05-16 00:30", "男", "男命", "单命"));

        String latePillars = variableValue(lateZi.getProcessInstanceKey(), "fourPillars");
        String nextPillars = variableValue(earlyNext.getProcessInstanceKey(), "fourPillars");
        assertThat(latePillars).as("晚子时四柱应非空").isNotBlank();
        assertThat(dayPillar(latePillars))
                .as("子初换日：23:30 日柱应进位于次日 00:30 的日柱")
                .isEqualTo(dayPillar(nextPillars));
    }

    @Test
    @DisplayName("B-4 结构化生辰（前端对象 {year,month,day,hourZhi,minute}）：与等价字符串生辰同柱，全链完成")
    void t_structured_birth_map_supported() {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("calendar", "公历");
        structured.put("year", 1990);
        structured.put("month", 5);
        structured.put("day", 15);
        structured.put("hourZhi", "辰");
        structured.put("minute", null);

        Map<String, Object> vars = scenario("结构化生辰", "", "男", "男命", "单命");
        vars.put("birth", structured);
        ProcessInstanceEvent structuredInst = runCompleted(vars);

        ProcessInstanceEvent stringInst = runCompleted(scenario(
                "字符串等价", "1990-05-15 07:00", "男", "男命", "单命"));

        String sp = variableValue(structuredInst.getProcessInstanceKey(), "fourPillars");
        assertThat(sp).as("结构化生辰四柱应非空").isNotBlank();
        assertThat(sp)
                .as("辰时结构化生辰（minute=null）应等价于字符串 07:00")
                .isEqualTo(variableValue(stringInst.getProcessInstanceKey(), "fourPillars"));
    }

    @Test
    @DisplayName("B-5 结构化生辰·子时映射为晚子 23:xx（子初换日）：与字符串 23:30 同柱")
    void t_structured_zi_maps_late_zi() {
        Map<String, Object> zi = new LinkedHashMap<>();
        zi.put("calendar", "公历");
        zi.put("year", 1990);
        zi.put("month", 5);
        zi.put("day", 15);
        zi.put("hourZhi", "子");
        zi.put("minute", 30);

        Map<String, Object> vars = scenario("结构化子时", "", "男", "男命", "单命");
        vars.put("birth", zi);
        ProcessInstanceEvent ziInst = runCompleted(vars);

        ProcessInstanceEvent lateZi = runCompleted(scenario(
                "字符串晚子", "1990-05-15 23:30", "男", "男命", "单命"));

        assertThat(dayPillar(variableValue(ziInst.getProcessInstanceKey(), "fourPillars")))
                .as("结构化「子」应映射晚子 23:xx，与 23:30 按子初换日同柱")
                .isEqualTo(dayPillar(variableValue(lateZi.getProcessInstanceKey(), "fourPillars")));
    }

    // ============================ 边界 / 负向 ============================

    @Test
    @DisplayName("B-3 农历输入（换算已落地）：农历 1990-05-15 → 公历 1990-06-07，不阻断，仍完成")
    void t_lunar_input_not_blocked() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", "1990-05-15 08:30");
        vars.put("calendar", "农历");
        vars.put("gender", "男");
        vars.put("subjectType", "男命");
        vars.put("inputMode", "单命");
        ProcessInstanceEvent instance = runCompleted(vars);

        assertThat(variableValue(instance.getProcessInstanceKey(), "chartBase"))
                .as("农历输入应换算为公历（1990 农历五月十五 = 1990-06-07），且不再标 LUNAR_UNCONVERTED")
                .contains("LUNAR_UNCONVERTED=false")
                .contains("SOLAR=1990-06-07T08:30");
        BpmnAssert.assertThat(instance).hasPassedElement("Task_CastPillars");
    }

    @Test
    @DisplayName("B-4 农历输入（年份越界 1899）：标【待考】、按原值登记、不阻断，仍完成")
    void t_lunar_out_of_range_not_blocked() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", "1899-05-15 08:30");
        vars.put("calendar", "农历");
        vars.put("gender", "男");
        vars.put("subjectType", "男命");
        vars.put("inputMode", "单命");
        ProcessInstanceEvent instance = runCompleted(vars);

        assertThat(variableValue(instance.getProcessInstanceKey(), "chartBase"))
                .as("年份越界（<1900）时无法换算，应标 LUNAR_UNCONVERTED=true（【待考】）")
                .contains("LUNAR_UNCONVERTED=true");
        BpmnAssert.assertThat(instance).hasPassedElement("Task_CastPillars");
    }

    @Test
    @DisplayName("N-1 缺 birth：parseBirthInput 抛 BPMN 错误 → 未完成且产生 incident")
    void t_missing_birth_raises_incident() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("gender", "男");
        vars.put("subjectType", "男命");
        vars.put("inputMode", "单命");
        ProcessInstanceEvent instance = start(vars);
        awaitIncident(instance);
        BpmnAssert.assertThat(instance)
                .hasAnyIncidents()
                .isNotCompleted();
        // 注意：任务抛异常 → 产生 incident，该元素「已激活但未完成」，故不能用 hasPassedElement
        //（其仅统计 ELEMENT_COMPLETED / SEQUENCE_FLOW_TAKEN）。改为断言 incident 落在该节点上。
        assertThat(incidentOn(instance.getProcessInstanceKey(), "Task_ParseInput"))
                .as("incident 应发生在 Task_ParseInput（缺 birth 校验失败所在节点）")
                .isTrue();
    }

    @Test
    @DisplayName("N-2 合婚缺 partnerBirth：analyzeMarriageMatch 抛 BPMN 错误 → 未完成且产生 incident")
    void t_marriage_without_partner_raises_incident() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("birth", "1988-03-10 06:00");
        vars.put("gender", "男");
        vars.put("subjectType", "男命");
        vars.put("inputMode", "合婚");
        ProcessInstanceEvent instance = start(vars);
        awaitIncident(instance);
        BpmnAssert.assertThat(instance)
                .hasAnyIncidents()
                .isNotCompleted();
        // 同上：合婚缺 partnerBirth → 在 Task_MarriageMatch 上产生 incident（非「通过」该元素）。
        assertThat(incidentOn(instance.getProcessInstanceKey(), "Task_MarriageMatch"))
                .as("incident 应发生在 Task_MarriageMatch（缺 partnerBirth 校验失败所在节点）")
                .isTrue();
    }

    // ============================ 辅助 ============================

    private ProcessInstanceEvent runCompleted(Map<String, Object> variables) {
        ProcessInstanceEvent instance = start(variables);
        awaitCompletion(instance);
        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement("Start_01")
                .hasPassedElement("End_01");
        return instance;
    }

    private ProcessInstanceEvent start(Map<String, Object> variables) {
        assertThat(Files.isRegularFile(Path.of(bpmnPath)))
                .as("BPMN 文件应存在: %s", bpmnPath).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(testClient, PROCESS_ID, Path.of(bpmnPath).toString()))
                .as("应成功部署流程").isTrue();
        return testClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(variables)
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
            sleep();
        }
        throw last != null ? last : new AssertionError("流程未在 " + TIMEOUT + " 内完成");
    }

    private static void awaitIncident(ProcessInstanceEvent instance) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                BpmnAssert.assertThat(instance).hasAnyIncidents();
                return;
            } catch (AssertionError ignored) {
                // 继续等待 incident 产生
            }
            sleep();
        }
        throw new AssertionError("流程未在 " + TIMEOUT + " 内产生 incident");
    }

    /**
     * 是否存在「发生在指定元素上」的 incident——即 incident 记录的 elementId 命中该元素。
     * <p>任务抛异常时该元素只被 ELEMENT_ACTIVATED、不会 ELEMENT_COMPLETED，
     * 故用本方法替代 {@code hasPassedElement} 来校验「流程确实走到了该节点并在此失败」。
     */
    private boolean incidentOn(long processInstanceKey, String elementId) {
        return StreamFilter.incident(RecordStream.of(engine.getRecordStreamSource()))
                .withProcessInstanceKey(processInstanceKey)
                .stream()
                .map(r -> r.getValue().getElementId())
                .anyMatch(elementId::equals);
    }

    /** 读取指定实例的流程变量最终值（来自记录流，去掉 JSON 引号）。 */
    private String variableValue(long processInstanceKey, String name) {
        String v = null;
        for (Record<VariableRecordValue> r
                : RecordStream.of(engine.getRecordStreamSource()).variableRecords()) {
            VariableRecordValue val = r.getValue();
            if (val.getProcessInstanceKey() == processInstanceKey && name.equals(val.getName())) {
                v = val.getValue();
            }
        }
        return unquote(v);
    }

    /** 以「单命/合婚 + 生辰 + 性别 + 对象类型」构建变量表。 */
    private static Map<String, Object> scenario(String subject, String birth, String gender,
                                                String subjectType, String inputMode, Object... extraKv) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject + "_" + System.nanoTime());
        vars.put("birth", birth);
        vars.put("calendar", "公历");
        vars.put("gender", gender);
        vars.put("subjectType", subjectType);
        vars.put("inputMode", inputMode);
        vars.put("granularity", "年");
        for (int i = 0; i + 1 < extraKv.length; i += 2) {
            vars.put((String) extraKv[i], extraKv[i + 1]);
        }
        return vars;
    }

    private static String dayPillar(String fourPillars) {
        String[] parts = fourPillars.trim().split("\\s+");
        return parts.length >= 3 ? parts[2] : fourPillars;
    }

    private static String[] concat(List<String> head, List<String> tail) {
        List<String> all = new ArrayList<>(head);
        all.addAll(tail);
        return all.toArray(new String[0]);
    }

    private static String unquote(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1).replace("\\\"", "\"");
        }
        return t;
    }

    private static String normalize(String gatewayAddress) {
        if (gatewayAddress == null || gatewayAddress.isBlank()) {
            throw new IllegalStateException("内存引擎未返回有效 gateway 地址");
        }
        return gatewayAddress.startsWith("http") ? gatewayAddress : "http://" + gatewayAddress;
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
