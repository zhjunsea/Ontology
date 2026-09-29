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
 * <p><b>2026-09-20 契约变更</b>：流程改为「全自动版」——流程内不得出现任何人工任务
 * （userTask），症状映射后直接进入四诊录入，不再有「人工确认症状映射」回环；
 * 一致性检查不通过 / 无方证推荐时直接落到 {@code EndEvent_NoResult}；
 * 结果形态网关 {@code Gateway_HasRecommendation} 按 {@code fangzhengRealized} +
 * {@code fangzhengCandidates} 分「完全命中 / 仅有候选 / 无候选」三支。
 * 本测试据此验证：
 * <ol>
 *   <li>{@code Task_SymptomMapping} —— 用<b>真实的</b> {@link SymptomMappingService}
 *       （纯 L1 降级模式，无网络）把自然语言映射为症状实例个体 IRI；</li>
 *   <li>全自动主链路一路跑到 {@code EndEvent_Success}（无 incident）；</li>
 *   <li>「仅有候选」走 {@code EndEvent_Candidates}、「无候选 / 不一致」走
 *       {@code EndEvent_NoResult}，且不再回到任何人工任务。</li>
 * </ol>
 *
 * <p>下游的八纲/六经/方证/方剂等推理步骤依赖 OBDA + MySQL，本测试以桩 Worker 替代，
 * 只驱动流程拓扑与变量契约（这些步骤的正确性由 {@code JingfangDiagnosisProcessTest} 等
 * 需要真实环境的集成测试覆盖）。
 */
@ZeebeProcessTest
class JingfangDiagnosisFlowTest {

    private static final String PROCESS_ID = "Process_Jingfang_Diagnosis";

    private static final String T_MAPPING = "Task_SymptomMapping";
    private static final String T_INPUT = "Task_InputSizhen";
    private static final String T_CHECK = "Task_ConsistencyCheck";
    private static final String T_BAGANG = "Task_Bagang";
    private static final String T_MODIFY = "Task_HerbModification";
    private static final String T_EXPLAIN = "Task_Explanation";
    private static final String END = "EndEvent_Success";
    private static final String END_CANDIDATES = "EndEvent_Candidates";
    private static final String END_NO_RESULT = "EndEvent_NoResult";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    ZeebeClient client;
    ZeebeTestEngine engine;

    private final List<JobWorker> workers = new ArrayList<>();

    /** 捕获 symptom-mapping 每轮实际输出的流程变量，供断言（不依赖引擎查询 API） */
    private final List<Map<String, Object>> mappingRounds = new ArrayList<>();

    // ---- 下游推理桩的可调开关（每个用例在 start() 前覆写） ----
    /** 本体一致性检查结果（决定是否短路到 EndEvent_NoResult）。 */
    private boolean stubConsistent = true;
    /** 方证是否被推理机「完全命中」(realize)。 */
    private boolean stubRealized = true;
    /** 可展示的候选方证列表。 */
    private List<String> stubCandidates = List.of("XiaochaihutangZheng");

    private static SymptomMappingService mappingService;

    @BeforeEach
    void setUp() throws Exception {
        if (mappingService == null) {
            mappingService = buildMappingService();
        }
        mappingRounds.clear();
        stubConsistent = true;
        stubRealized = true;
        stubCandidates = List.of("XiaochaihutangZheng");

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
    @DisplayName("全自动主链路：映射→四诊→一致性→八纲→…→加减药→解释→诊断完成（全程无 incident）")
    void fullFlowToSuccess() {
        ProcessInstanceEvent instance = start("发热，恶寒，下利");
        awaitCompletion(instance);

        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                // 症状映射只跑 1 轮（不再有确认回环）
                .hasPassedElement(T_MAPPING, 1)
                .hasPassedElement(T_MODIFY)
                .hasPassedElement(T_EXPLAIN)
                .hasPassedElement(END)
                .hasVariableWithValue("herbModificationApplied", true);

        // 顺序：映射 → 四诊 → 一致性 → 八纲 → … → 加减药 → 解释 → 完成
        BpmnAssert.assertThat(instance).hasPassedElementsInOrder(
                T_MAPPING, T_INPUT, T_CHECK, T_BAGANG, T_MODIFY, T_EXPLAIN, END);

        assertThat(mappingRounds).as("症状映射应只跑 1 轮").hasSize(1);
        Map<String, Object> r0 = mappingRounds.get(0);
        assertThat(asList(r0.get("symptomIris"))).containsExactlyInAnyOrder(
                SymptomCatalog.BASE_NS + "Fare_instance",
                SymptomCatalog.BASE_NS + "Ehan_instance",
                SymptomCatalog.BASE_NS + "Xiali_instance");
    }

    @Test
    @DisplayName("仅有候选路径：方证未完全命中但有候选 → 终止于 EndEvent_Candidates")
    void candidatesEndsAtCandidates() {
        stubRealized = false;
        stubCandidates = List.of("XiaochaihutangZheng", "DachaihutangZheng");

        ProcessInstanceEvent instance = start("发热，恶寒，下利");
        awaitCompletion(instance);

        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement(T_MODIFY)
                .hasPassedElement(END_CANDIDATES)
                .hasNotPassedElement(END);
    }

    @Test
    @DisplayName("无候选路径：方证未命中且候选为空 → 终止于 EndEvent_NoResult")
    void noCandidatesEndsAtNoResult() {
        stubRealized = false;
        stubCandidates = List.of();

        ProcessInstanceEvent instance = start("发热，恶寒，下利");
        awaitCompletion(instance);

        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement(T_MODIFY)
                .hasPassedElement(END_NO_RESULT)
                .hasNotPassedElement(END_CANDIDATES);
    }

    @Test
    @DisplayName("不一致短路：一致性检查不通过 → 直接终止于 EndEvent_NoResult，不再回到人工修改四诊")
    void inconsistentEndsAtNoResult() {
        stubConsistent = false;

        ProcessInstanceEvent instance = start("发热，恶寒，下利");
        awaitCompletion(instance);

        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement(T_CHECK)
                .hasPassedElement(END_NO_RESULT)
                // 未进入八纲及之后任何推理步骤，也无人工作业
                .hasNotPassedElement(T_BAGANG)
                .hasNotPassedElement(T_MODIFY);
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

        // ---- ② 以下为下游推理步骤的桩（真实实现依赖 OBDA + MySQL） ----
        worker("sizhen-input", (jc, job) -> complete(jc, job, Map.of(
                "patientIri", SymptomCatalog.BASE_NS + "Patient_test",
                "recorded", true,
                "inconsistent", false)));

        // Gateway_Consistent 无 default，必须始终给出 consistent，否则会因无分支命中产生 incident
        worker("ontology-consistency-check", (jc, job) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("consistent", stubConsistent);
            out.put("unsatisfiableClasses", List.of());
            complete(jc, job, out);
        });

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

        // 结果形态网关按 fangzhengRealized + fangzhengCandidates 路由（见 BPMN 注释）
        worker("fangzheng-classification", (jc, job) -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fangzheng", stubRealized ? "XiaochaihutangZheng" : null);
            out.put("fangzhengCn", stubRealized ? "小柴胡汤证" : null);
            out.put("fangzhengRealized", stubRealized);
            out.put("fangzhengCandidates", stubCandidates);
            out.put("candidateFangzhengs", stubCandidates);
            out.put("candidateFangzhengsCn", stubCandidates);
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
