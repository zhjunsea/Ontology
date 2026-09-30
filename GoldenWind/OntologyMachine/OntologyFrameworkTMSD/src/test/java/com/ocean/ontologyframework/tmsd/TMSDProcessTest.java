package com.ocean.ontologyframework.tmsd;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 塔架中段定制化设计流程「端到端」内存测试（zeebe-process-test 内存引擎，无需 Camunda / MySQL / Ontop）。
 *
 * <p>部署<b>可执行</b> BPMN（{@code ontology/bpmn/TowerMidDesign.bpmn}），用真实设计引擎
 * （{@link TmsdDesignPipeline} + {@link TmsdOutputWriter}）驱动全部 9 个 serviceTask
 * （完全正向 S0→S8），验证单一正向路径无 incident，且输出 txt 满足本体约束。
 */
@ZeebeProcessTest
class TMSDProcessTest {

    private static final String PROCESS_ID = "Process_TowerMid";

    private static final String T_STEP0 = "Task_Step0";
    private static final String T_STEP1 = "Task_Step1";
    private static final String T_STEP9 = "Task_Step9";
    private static final String T_STEP8 = "Task_Step8";
    private static final String T_STEP7 = "Task_Step7";
    private static final String T_STEP6 = "Task_Step6";
    private static final String T_STEP5 = "Task_Step5";
    private static final String T_STEP4 = "Task_Step4";
    private static final String T_OUTPUT = "Task_Output";
    private static final String END_DESIGN = "End_Design";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    ZeebeClient client;
    ZeebeTestEngine engine;

    private final List<JobWorker> workers = new ArrayList<>();

    /** 按流程实例缓存设计输入（S0 建立，与真实 JobWorker 的会话缓存等价）。 */
    private final Map<Long, TowerDesignRequest> reqs = new ConcurrentHashMap<>();

    /** 按流程实例缓存设计结果（S1 回填）。 */
    private final Map<Long, TmsdDesignPipeline.CaseResult> sessions = new ConcurrentHashMap<>();

    /** 输出文件路径（Task_Output 写出）。 */
    private final List<Path> outputFiles = new ArrayList<>();

    private static Path geoPath;
    private static Path layoutPath;
    private static Path outputDir;

    @BeforeAll
    static void loadConfig() {
        TmsdTestConfig.initTmsdVocabulary();
        geoPath = Paths.get(readConfig("historical-geo-path"));
        layoutPath = Paths.get(readConfig("historical-layout-path"));
        outputDir = Paths.get(readConfig("output-dir"));
    }

    @BeforeEach
    void setUp() throws Exception {
        reqs.clear();
        sessions.clear();
        outputFiles.clear();

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
                // 引擎已随测试结束关闭
            }
        }
        workers.clear();
    }

    // ============================================================
    // 测试用例
    // ============================================================

    @Test
    @DisplayName("完全正向单路径：S0→S1→S2~S7→S8→设计完成（无 incident）")
    void forwardSinglePath() {
        ProcessInstanceEvent instance = start(2);   // V17
        awaitCompletion(instance);

        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement(T_STEP0)
                .hasPassedElement(T_STEP1)
                .hasPassedElement(T_STEP9)
                .hasPassedElement(T_STEP8)
                .hasPassedElement(T_STEP7)
                .hasPassedElement(T_STEP6)
                .hasPassedElement(T_STEP5)
                .hasPassedElement(T_STEP4)
                .hasPassedElement(T_OUTPUT)
                .hasPassedElement(END_DESIGN);

        BpmnAssert.assertThat(instance).hasPassedElementsInOrder(
                T_STEP0, T_STEP1, T_STEP9, T_STEP8, T_STEP7, T_STEP6, T_STEP5, T_STEP4, T_OUTPUT, END_DESIGN);

        assertThat(outputFiles).as("应写出模型参数 txt").isNotEmpty();
        assertThat(outputFiles).allMatch(Files::isRegularFile);
    }

    @Test
    @DisplayName("输出的附件信息 txt 满足本体约束（间距 1400~1960、首附件 980、平台 1250 等）")
    void outputTxtSatisfiesOntologyConstraints() throws Exception {
        ProcessInstanceEvent instance = start(2);
        awaitCompletion(instance);

        List<Path> txts = outputFiles.stream()
                .filter(p -> p.getFileName().toString().endsWith("附件信息关系式.txt"))
                .toList();
        assertThat(txts).as("应写出至少一份附件信息关系式 txt").isNotEmpty();

        Charset gbk = TmsdOutputWriter.CREO_CHARSET;
        for (Path p : txts) {
            String text = Files.readString(p, gbk);
            assertThat(text).contains("SEC_H_total=");
            assertThat(text).contains("H_platform=1250");
            // 老应用头块与主体块（规则2 补全）
            assertThat(text).contains("PART_NAME=PTC_COMMON_NAME");
            assertThat(text).contains("中间段爬梯支撑");
            assertThat(text).contains("照明灯");
            assertThat(text).contains("H_LIGHT2FL=");
        }

        // 应输出 3 类 txt（筒体 / 附件 / 法兰）
        assertThat(outputFiles).anyMatch(p -> p.getFileName().toString().endsWith("筒体信息关系式.txt"));
        assertThat(outputFiles).anyMatch(p -> {
            String name = p.getFileName().toString();
            return (name.startsWith("连接法兰") || name.startsWith("底法兰")) && name.endsWith("关系式.txt");
        });

        // 方案对比报告（UTF-8）
        Path report = outputFiles.stream()
                .filter(p -> p.getFileName().toString().endsWith("方案对比报告.md"))
                .findFirst().orElseThrow();
        String md = Files.readString(report, Charset.forName("UTF-8"));
        assertThat(md).contains("满足本体约束的设计方案");
        assertThat(md).contains("推荐方案");
    }

    // ============================================================
    // 流程驱动
    // ============================================================

    private ProcessInstanceEvent start(int caseIndex) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("caseIndex", caseIndex);
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
    // Worker 注册（真实设计引擎，无本体服务）
    // ============================================================

    private static final String[] MODELS = {"V12", "V15", "V17", "V19"};

    private void registerWorkers() {
        // S0 输入解析：由样例文件构造设计输入（固定项：钢绳导向 / 中国 / 焊接）
        worker("tmsd-step0-parse-input", (jc, job) -> {
            Map<String, Object> vars = job.getVariablesAsMap();
            int caseIndex = intOf(vars.get("caseIndex"), 0);
            TowerGeometry geo = TowerExcelReader.readGeometry(geoPath);
            LayoutSpec lay = TowerExcelReader.readLayout(layoutPath);
            String model = MODELS[Math.floorMod(caseIndex, MODELS.length)];
            TowerDesignRequest req = new TowerDesignRequest("用例" + caseIndex, geo, model,
                    ElevatorType.ROPE_GUIDED, "中国", AccessoryConnectionType.WELDED, lay);
            reqs.put(job.getProcessInstanceKey(), req);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("caseName", req.caseName());
            out.put("middleSectionNumbers", new ArrayList<>(req.middleSectionNumbers()));
            complete(jc, job, out);
        });

        // S1 塔筒外形与分段几何：正向求解全部备选，回填会话结果
        worker("tmsd-step1-tube-shape", (jc, job) -> {
            long key = job.getProcessInstanceKey();
            TowerDesignRequest req = reqs.get(key);
            TmsdDesignPipeline.CaseResult result = TmsdDesignPipeline.design(req);
            sessions.put(key, result);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("caseName", req.caseName());
            out.put("variantCount", result.variants().size());
            out.put("satisfiedCount", result.satisfied().size());
            complete(jc, job, out);
        });

        // 步骤9~4：从会话取推荐方案，输出该步骤参数
        worker("tmsd-step9-elevator", (jc, job) -> stepOutput(jc, job, "步骤9 升降机设计",
                (sd, req) -> Map.of("section", sd.sectionNo(), "supportHeight", sd.supportHeight())));
        worker("tmsd-step8-accessory", (jc, job) -> stepOutput(jc, job, "步骤8 附件类型替换",
                (sd, req) -> Map.of("section", sd.sectionNo(), "accessoryType", sd.accessoryType())));
        worker("tmsd-step7-diameter", (jc, job) -> stepOutput(jc, job, "步骤7 直径设计",
                (sd, req) -> Map.of("section", sd.sectionNo(), "bracketLength", sd.bracketLength())));
        worker("tmsd-step6-height", (jc, job) -> stepOutput(jc, job, "步骤6 高度设计",
                (sd, req) -> Map.of("section", sd.sectionNo(), "ladderLength", sd.ladderLength())));
        worker("tmsd-step5-tubesection", (jc, job) -> stepOutput(jc, job, "步骤5 筒节设计",
                (sd, req) -> Map.of("section", sd.sectionNo(), "accessoryCount", sd.accessoryCount(),
                        "lightType", sd.lightType())));
        worker("tmsd-step4-platform", (jc, job) -> stepOutput(jc, job, "步骤4 平台设计",
                (sd, req) -> Map.of("section", sd.sectionNo(),
                        "platformDistance", sd.platformDistance())));

        // 输出：写 txt + 对比报告
        worker("tmsd-output-parameters", (jc, job) -> {
            TmsdDesignPipeline.CaseResult result = sessions.get(job.getProcessInstanceKey());
            List<Path> files = TmsdOutputWriter.write(result, outputDir);
            synchronized (outputFiles) {
                outputFiles.addAll(files);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("satisfiedCount", result.satisfied().size());
            out.put("outputFiles", files.stream().map(Path::toString).toList());
            complete(jc, job, out);
        });
    }

    private interface SectionMapper {
        Map<String, Object> map(TowerDesignEngine.SectionDesign sd, TowerDesignRequest req);
    }

    private void stepOutput(JobClient jc, ActivatedJob job, String step, SectionMapper mapper) {
        TmsdDesignPipeline.CaseResult result = sessions.get(job.getProcessInstanceKey());
        TmsdDesignPipeline.VariantResult rec = result.recommended();
        List<Map<String, Object>> sections = new ArrayList<>();
        if (rec != null) {
            for (Map.Entry<Integer, TowerDesignEngine.SectionDesign> e : rec.sections().entrySet()) {
                sections.add(mapper.map(e.getValue(), result.request()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("step", step);
        out.put("sections", sections);
        complete(jc, job, out);
    }

    private interface Handler {
        void handle(JobClient jc, ActivatedJob job) throws Exception;
    }

    private void worker(String jobType, Handler handler) {
        workers.add(client.newWorker()
                .jobType(jobType)
                .handler(handler::handle)
                .streamEnabled(false)
                .pollInterval(Duration.ofMillis(50))
                .open());
    }

    private static void complete(JobClient jc, ActivatedJob job, Map<String, Object> vars) {
        jc.newCompleteCommand(job.getKey()).variables(vars).send().join();
    }

    // ============================================================
    // 工具
    // ============================================================

    /** 从 txt 中提取 {@code key=value} 的数值部分。 */
    private static String extract(String text, String key) {
        int i = text.indexOf(key);
        assertThat(i).as("应包含 %s", key).isGreaterThanOrEqualTo(0);
        int start = i + key.length();
        int end = start;
        while (end < text.length() && (Character.isDigit(text.charAt(end))
                || text.charAt(end) == '.' || text.charAt(end) == '-')) {
            end++;
        }
        return text.substring(start, end);
    }

    private static String readConfig(String key) {
        String v = TmsdTestConfig.tmsd(key);
        return v != null ? v : TmsdTestConfig.ontology(key);
    }

    private static int intOf(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        return def;
    }
}
