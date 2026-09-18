package com.ocean.ontologyframework.tcm;

import com.ocean.ontologyframework.tcm.app.LlmClient;
import com.ocean.ontologyframework.tcm.app.SymptomCatalog;
import com.ocean.ontologyframework.tcm.app.SymptomMappingService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 经方辨证流程「端到端」内存测试（zeebe-process-test 内存引擎，无需 Camunda / MySQL）。
 *
 * <p>验证本次新增的三段逻辑在真实流程引擎中确实跑通：
 * <ol>
 *   <li>{@code Task_SymptomMapping} —— 用<b>真实的</b> {@link SymptomMappingService}
 *       （纯 L1 降级模式，无网络）把自然语言映射为症状实例个体 IRI；</li>
 *   <li>{@code Gateway_NeedConfirm} + {@code Task_ConfirmSymptoms} —— 低置信度/未匹配时
 *       进入人工确认回环，确认后重跑映射并收敛；</li>
 *   <li>{@code Task_HerbModification} —— 加减药作为独立步骤插在母方推荐之后、生成解释之前。</li>
 * </ol>
 *
 * <p>下游的八纲/六经/方证/方剂等推理步骤依赖 OBDA + MySQL，本测试以桩 Worker 替代，
 * 只驱动流程拓扑与变量契约（这些步骤的正确性由 {@code JingfangDiagnosisProcessTest} 等
 * 需要真实环境的集成测试覆盖）。
 */
@ZeebeProcessTest
class JingfangDiagnosisFlowTest {

    private static final String PROCESS_ID = "Process_Jingfang_Diagnosis";
    private static final String USER_TASK_JOB_TYPE = "io.camunda.zeebe:userTask";

    private static final String T_MAPPING = "Task_SymptomMapping";
    private static final String GW_CONFIRM = "Gateway_NeedConfirm";
    private static final String T_CONFIRM = "Task_ConfirmSymptoms";
    private static final String T_INPUT = "Task_InputSizhen";
    private static final String T_MODIFY = "Task_HerbModification";
    private static final String T_EXPLAIN = "Task_Explanation";
    private static final String END = "EndEvent_Success";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    ZeebeClient client;
    ZeebeTestEngine engine;

    private final List<JobWorker> workers = new ArrayList<>();

    /** 捕获 symptom-mapping 每轮实际输出的流程变量，供断言（不依赖引擎查询 API） */
    private final List<Map<String, Object>> mappingRounds = new ArrayList<>();
    /** 捕获确认任务被调用的次数 */
    private final int[] confirmCalls = {0};

    private static SymptomMappingService mappingService;

    @BeforeEach
    void setUp() throws Exception {
        if (mappingService == null) {
            mappingService = buildMappingService();
        }
        mappingRounds.clear();
        confirmCalls[0] = 0;

        Path bpmn = Paths.get(readConfig("bpmn-path"));
        assertThat(Files.isRegularFile(bpmn)).as("BPMN: %s", bpmn).isTrue();
        DeploymentEvent dep = client.newDeployResourceCommand()
                .addResourceFile(bpmn.toString()).send().join();
        assertThat(dep.getProcesses()).hasSize(1);

        registerWorkers();
    }

    @AfterEach
    void tearDown() {
        for (JobWorker w : workers) {
            try {
                w.close();
            } catch (Exception ignored) {
                // 引擎已随测试结束关闭，忽略
            }
        }
        workers.clear();
    }

    // ============================================================
    // 测试用例
    // ============================================================

    @Test
    @DisplayName("需确认路径：映射→确认回环→重跑收敛→加减药→完成（全程无 incident）")
    void confirmationLoopThenComplete() {
        // 「两边肋骨下面胀痛」在纯 L1 下无候选 → 未匹配 → 触发人工确认
        ProcessInstanceEvent instance = start("发热，恶寒，两边肋骨下面胀痛");
        awaitCompletion(instance);

        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                // 症状映射跑了 2 轮：首轮 + 确认后重跑；确认网关同样经过 2 次
                .hasPassedElement(T_MAPPING, 2)
                .hasPassedElement(GW_CONFIRM, 2)
                .hasPassedElement(T_CONFIRM)
                .hasPassedElement(T_MODIFY)
                .hasPassedElement(T_EXPLAIN)
                .hasVariableWithValue("herbModificationApplied", true);

        // 顺序：映射 → 确认 → 回到映射 → 录入四诊 → … → 加减药 → 解释 → 结束
        BpmnAssert.assertThat(instance).hasPassedElementsInOrder(
                T_MAPPING, T_CONFIRM, T_MAPPING, T_INPUT, T_MODIFY, T_EXPLAIN, END);

        assertThat(confirmCalls[0]).as("确认任务应被调用 1 次").isEqualTo(1);
        assertThat(mappingRounds).hasSize(2);

        // 第 0 轮：需要确认，且未匹配项被记录
        Map<String, Object> r0 = mappingRounds.get(0);
        assertThat(r0.get("needsConfirmation")).isEqualTo(true);
        assertThat(asList(r0.get("unmatchedTexts"))).contains("两边肋骨下面胀痛");
        assertThat(asList(r0.get("symptomIris")))
                .contains(SymptomCatalog.BASE_NS + "Fare_instance",
                        SymptomCatalog.BASE_NS + "Ehan_instance");

        // 第 1 轮：用户补充「口苦」→ 采纳；未匹配项不再拦截 → 收敛
        Map<String, Object> r1 = mappingRounds.get(1);
        assertThat(r1.get("needsConfirmation")).isEqualTo(false);
        assertThat(asList(r1.get("symptomIris")))
                .contains(SymptomCatalog.BASE_NS + "Fare_instance",
                        SymptomCatalog.BASE_NS + "Ehan_instance",
                        SymptomCatalog.BASE_NS + "Kouku_instance");
        // Worker 把「已完成的映射轮次数」写回 mappingRound（round + 1），第 1 轮 → 2
        assertThat(r1.get("mappingRound")).isEqualTo(2);
    }

    @Test
    @DisplayName("无需确认路径：高置信度直接命中，跳过确认任务")
    void cleanInputSkipsConfirmation() {
        ProcessInstanceEvent instance = start("发热，恶寒，下利");
        awaitCompletion(instance);

        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement(T_MAPPING, 1)
                .hasNotPassedElement(T_CONFIRM)
                .hasPassedElement(T_MODIFY)
                .hasVariableWithValue("herbModificationApplied", true);

        assertThat(confirmCalls[0]).as("不应触发人工确认").isZero();
        assertThat(mappingRounds).hasSize(1);
        Map<String, Object> r0 = mappingRounds.get(0);
        assertThat(r0.get("needsConfirmation")).isEqualTo(false);
        assertThat(asList(r0.get("symptomIris"))).containsExactlyInAnyOrder(
                SymptomCatalog.BASE_NS + "Fare_instance",
                SymptomCatalog.BASE_NS + "Ehan_instance",
                SymptomCatalog.BASE_NS + "Xiali_instance");
    }

    @Test
    @DisplayName("回环上限：确认任务始终不给出有效信息时，最多 3 轮后强制放行，不死循环")
    void confirmationLoopIsBounded() {
        // 「想呕」每轮都命中单字症状「呕」（置信度 0.75 < 0.85）→ 每轮都要求确认；
        // 确认 Worker 又不提供任何 confirmed/extra → 理论上会无限回环。
        // 网关的 `mappingRound < 3` 上限保证第 3 轮后强制走「无需确认」分支。
        ProcessInstanceEvent instance = start("想呕");
        awaitCompletion(instance);

        BpmnAssert.assertThat(instance).isCompleted().hasNoIncidents();
        // 轮次：round 0 / 1 / 2 各跑一次映射（写回 mappingRound = 1 / 2 / 3），
        // 第 3 次映射后 mappingRound = 3，网关 `mappingRound < 3` 不成立 → 放行。
        assertThat(mappingRounds).as("映射轮次应被 maxRounds 截断为 3 轮").hasSize(3);
        assertThat(confirmCalls[0]).as("确认任务最多被调用 2 次").isEqualTo(2);
        assertThat(mappingRounds.get(2).get("mappingRound")).isEqualTo(3);
    }

    // ============================================================
    // 流程驱动
    // ============================================================

    private ProcessInstanceEvent start(String userInput) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("userInput", userInput);
        vars.put("mappingRound", 0);
        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(vars)
                .send().join();
    }

    private void awaitCompletion(ProcessInstanceEvent instance) {
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
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last != null ? last : new AssertionError("流程未在 " + TIMEOUT + " 内完成");
    }

    // ============================================================
    // Worker 注册
    // ============================================================

    private void registerWorkers() {
        // ---- ① 症状映射：真实服务（纯 L1，无大模型） ----
        worker("symptom-mapping", (jc, job) -> {
            Map<String, Object> vars = job.getVariablesAsMap();
            String userInput = str(vars.get("userInput"));
            List<String> confirmed = asList(vars.get("confirmed"));
            List<String> rejected = asList(vars.get("rejected"));
            List<String> extra = asList(vars.get("extra"));
            int round = intOf(vars.get("mappingRound"));

            if (userInput == null || userInput.isBlank()) {
                userInput = String.join("，", confirmed);
            }

            SymptomMappingService.MappingResult res =
                    mappingService.map(userInput, confirmed, rejected, extra, round);
            Map<String, Object> out = res.toVariables();
            out.put("mappingRound", round + 1);
            out.put("confirmed", confirmed);
            out.put("rejected", rejected);
            out.put("extra", extra);
            synchronized (mappingRounds) {
                mappingRounds.add(new LinkedHashMap<>(out));
            }
            jc.newCompleteCommand(job.getKey()).variables(out).send().join();
        });

        // ---- ② 人工确认（userTask 在未声明 zeebe:userTask 时按内置 jobType 生成作业） ----
        worker(USER_TASK_JOB_TYPE, (jc, job) -> {
            confirmCalls[0]++;
            Map<String, Object> vars = job.getVariablesAsMap();
            List<String> unmatched = asList(vars.get("unmatchedTexts"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("confirmed", List.of());
            out.put("rejected", List.of());
            if (unmatched.contains("两边肋骨下面胀痛")) {
                // 用户只对「自己能说清」的未匹配表述做补充：补一个「口苦」
                out.put("extra", List.of("Kouku_instance"));
            } else {
                // 其余情况用户给不出有效信息（用于验证回环上限）
                out.put("extra", List.of());
            }
            jc.newCompleteCommand(job.getKey()).variables(out).send().join();
        });

        // ---- ③ 以下为下游推理步骤的桩（真实实现依赖 OBDA + MySQL） ----
        worker("sizhen-input", (jc, job) -> complete(jc, job, Map.of(
                "patientIri", SymptomCatalog.BASE_NS + "Patient_test",
                "recorded", true,
                "inconsistent", false)));

        worker("ontology-consistency-check", (jc, job) -> complete(jc, job, Map.of(
                "consistent", true,
                "unsatisfiableClasses", List.of())));

        worker("bagang-classification", (jc, job) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("bagangResult", Map.of(
                    "表里", List.of("半表半里"),
                    "寒热", List.of("寒热往来"),
                    "虚实", List.of("实证"),
                    "阴阳", List.of("阳证"),
                    "bagangTypes", List.of("BanBiaoBanLi", "Re", "Shi", "Yang")));
            out.put("bagangTypes", List.of("BanBiaoBanLi", "Re", "Shi", "Yang"));
            out.put("bagangTypesCn", List.of("半表半里", "热", "实", "阳"));
            complete(jc, job, out);
        });

        worker("liujing-classification", (jc, job) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sixChannel", "Shaoyangbing");
            out.put("sixChannelCn", "少阳病");
            out.put("liujingTypes", List.of("Shaoyangbing"));
            out.put("liujingTypesCn", List.of("少阳病"));
            out.put("combinedDiseaseMark", null);
            complete(jc, job, out);
        });

        worker("fangzheng-classification", (jc, job) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fangzheng", "XiaochaihutangZheng");
            out.put("fangzhengCn", "小柴胡汤证");
            out.put("candidateFangzhengs", List.of("XiaochaihutangZheng"));
            out.put("candidateFangzhengsCn", List.of("小柴胡汤证"));
            out.put("candidateScores", List.of(0.91));
            complete(jc, job, out);
        });

        worker("jianjiazheng-classification", (jc, job) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jianJiaZhengs", List.of());
            out.put("jianJiaZhengsCn", List.of());
            complete(jc, job, out);
        });

        worker("prescription-recommendation", (jc, job) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            String base = SymptomCatalog.BASE_NS + "Xiaochaihutang";
            out.put("baseFormula", base);
            out.put("baseFormulaCn", "小柴胡汤");
            out.put("finalFormula", base);
            out.put("finalFormulaCn", "小柴胡汤");
            out.put("baseHerbs", List.of(SymptomCatalog.BASE_NS + "Chaihu"));
            out.put("baseHerbsCn", List.of("柴胡"));
            out.put("derived", false);
            complete(jc, job, out);
        });

        worker("herb-modification", (jc, job) -> {
            Map<String, Object> in = job.getVariablesAsMap();
            String base = str(in.get("baseFormula"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("finalFormula", base);
            out.put("finalFormulaCn", "小柴胡汤加味");
            out.put("herbs", List.of(SymptomCatalog.BASE_NS + "Chaihu",
                    SymptomCatalog.BASE_NS + "Huangqin"));
            out.put("herbsCn", List.of("柴胡", "黄芩"));
            out.put("addedHerb", List.of(SymptomCatalog.BASE_NS + "Huangqin"));
            out.put("addedHerbCn", List.of("黄芩"));
            out.put("removedHerb", List.of());
            out.put("removedHerbCn", List.of());
            out.put("derived", true);
            out.put("appliedRules", List.of("若口苦者，加黄芩"));
            out.put("ruleSources", List.of("小柴胡汤方后注"));
            out.put("dosageChanges", List.of());
            out.put("warnings", List.of());
            out.put("herbModificationApplied", true);
            out.put("herbModificationSummary", "依方后注加减法派生，命中 1 条规则");
            complete(jc, job, out);
        });

        worker("diagnosis-explanation", (jc, job) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("explanation", "少阳病小柴胡汤证，依方后注加黄芩。");
            complete(jc, job, out);
        });
    }

    private interface Handler {
        void handle(io.camunda.zeebe.client.api.worker.JobClient jc, ActivatedJob job) throws Exception;
    }

    private void worker(String jobType, Handler handler) {
        workers.add(client.newWorker()
                .jobType(jobType)
                .handler(handler::handle)
                .streamEnabled(false)   // 内存引擎用轮询，避免流式作业通道
                .pollInterval(Duration.ofMillis(50))
                .open());
    }

    private static void complete(io.camunda.zeebe.client.api.worker.JobClient jc,
                                 ActivatedJob job, Map<String, Object> vars) {
        jc.newCompleteCommand(job.getKey()).variables(vars).send().join();
    }

    // ============================================================
    // 工具
    // ============================================================

    private static SymptomMappingService buildMappingService() {
        SymptomCatalog catalog = new SymptomCatalog(readConfig("abox-dir"));
        LlmClient llm = new LlmClient();
        llm.configure(false, "", "", "stub", 5);   // 大模型不可用 → 纯 L1 降级
        SymptomMappingService s = new SymptomMappingService(catalog, llm);
        s.configure(3, 0.85, 0.50, 3, 60);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static String readConfig(String key) {
        try (InputStream is = JingfangDiagnosisFlowTest.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml 必须在 classpath 上").isNotNull();
            Map<String, Object> cfg = new Yaml().load(is);
            Map<String, Object> ontology = (Map<String, Object>) cfg.get("ontology");
            String v = (String) ontology.get(key);
            if ((v == null || v.isBlank()) && "abox-dir".equals(key)) {
                v = Paths.get((String) ontology.get("main-path")).getParent().toString();
            }
            return v;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int intOf(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> asList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(o == null ? null : String.valueOf(o));
            return out;
        }
        if (v instanceof String s) {
            return s.isBlank() ? List.of() : List.of(s.split("\\s*,\\s*"));
        }
        return List.of(String.valueOf(v));
    }
}
