package com.ocean.ontologyframework;

import com.ocean.ontologyframework.tmsd.TmsdTestConfig;

import com.ocean.utilities.StopOnTimeoutExtension;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 塔架中段定制化设计流程「真实环境」集成测试（完全正向，S0→S8 单一路径）。
 *
 * <p>对照 {@code JingfangDiagnosisProcessTest}：本测试<b>不</b>在内存引擎中跑，
 * 而是连接<b>真实运行</b>的 Camunda 8（Zeebe Gateway，默认 {@code localhost:26500}），
 * 部署 {@code TowerMidDesign.bpmn}，由<b>真实运行的</b> {@link com.ocean.ontologyframework.tmsd.TMSDApplication}
 * （{@link com.ocean.ontologyframework.tmsd.TMSDOntologyJobWorker}）消费作业、执行设计、写出 txt。
 *
 * <p><b>前置条件</b>：
 * <ol>
 *   <li>环境已启动（Camunda / MySQL / Ontop），见 {@code EnvPrepare/scripts/start_all.py}；</li>
 *   <li>已启动 {@link com.ocean.ontologyframework.tmsd.TMSDApplication}（{@code --enable-native-access=ALL-UNNAMED}）。</li>
 * </ol>
 *
 * <p>覆盖：7 个设计输入样例（caseIndex 0..6，机型按 V12/V15/V17/V19 轮换），
 * 全部走同一条正向路径，断言流程完成、无 incident、满足约束方案数 &gt; 0、
 * 推荐方案非空、3 份 txt + 方案对比报告已写出、本体一致性校验通过。
 */
@ExtendWith(StopOnTimeoutExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class TMSDDesignProcessTest {

    private static final String PROCESS_ID = "Process_TowerMid";

    private static ZeebeClient zeebeClient;
    private static String bpmnPath;
    private static String outputDir;

    @BeforeAll
    static void setUp() {
        bpmnPath = TmsdTestConfig.ontology("bpmn-path");
        assertThat(bpmnPath).as("ontology.bpmn-path must be configured").isNotBlank();

        outputDir = TmsdTestConfig.tmsd("output-dir");
        assertThat(outputDir).as("tmsd.output-dir must be configured").isNotBlank();

        String grpcAddress = TmsdTestConfig.camundaClient("grpc-address");
        assertThat(grpcAddress).as("camunda.client.grpc-address must be configured").isNotBlank();

        boolean useTls = grpcAddress.startsWith("https://");
        String hostPort = grpcAddress.replaceFirst("^https?://", "");
        zeebeClient = useTls
                ? ZeebeClient.newClientBuilder().gatewayAddress(hostPort)
                        .defaultRequestTimeout(Duration.ofSeconds(60)).build()
                : ZeebeClient.newClientBuilder().gatewayAddress(hostPort).usePlaintext()
                        .defaultRequestTimeout(Duration.ofSeconds(60)).build();

        System.out.println("📂 Deploying TMSD BPMN from: " + bpmnPath);
        DeploymentEvent deployment = zeebeClient.newDeployResourceCommand()
                .addResourceFile(bpmnPath).send().join();
        assertThat(deployment.getProcesses()).hasSize(1);
        System.out.println("✅ 流程已部署，version=" + deployment.getProcesses().get(0).getVersion());
    }

    // ============================================================
    // 用例（同一正向路径，不同输入样例）
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("样例0 正向设计：走 S0→S8，输出 txt + 方案对比报告 + 本体一致性通过")
    void case0() {
        assertDesignCase(0, "样例0-正向设计");
    }

    @Test
    @Order(2)
    @DisplayName("样例1 正向设计：走 S0→S8，输出 txt + 方案对比报告 + 本体一致性通过")
    void case1() {
        assertDesignCase(1, "样例1-正向设计");
    }

    @Test
    @Order(3)
    @DisplayName("样例2 正向设计：走 S0→S8，输出 txt + 方案对比报告 + 本体一致性通过")
    void case2() {
        assertDesignCase(2, "样例2-正向设计");
    }

    @Test
    @Order(4)
    @DisplayName("样例3 正向设计：走 S0→S8，输出 txt + 方案对比报告 + 本体一致性通过")
    void case3() {
        assertDesignCase(3, "样例3-正向设计");
    }

    @Test
    @Order(5)
    @DisplayName("样例4 正向设计：走 S0→S8，输出 txt + 方案对比报告 + 本体一致性通过")
    void case4() {
        assertDesignCase(4, "样例4-正向设计");
    }

    @Test
    @Order(6)
    @DisplayName("样例5 正向设计：走 S0→S8，输出 txt + 方案对比报告 + 本体一致性通过")
    void case5() {
        assertDesignCase(5, "样例5-正向设计");
    }

    @Test
    @Order(7)
    @DisplayName("样例6 正向设计：走 S0→S8，输出 txt + 方案对比报告 + 本体一致性通过")
    void case6() {
        assertDesignCase(6, "样例6-正向设计");
    }

    // ============================================================
    // 断言与驱动
    // ============================================================

    private void assertDesignCase(int caseIndex, String label) {
        ProcessInstanceResult r = run(caseIndex);
        Map<String, Object> v = r.getVariablesAsMap();
        print(label, v);

        assertThat(asInt(v.get("satisfiedCount"))).as("%s 满足约束方案数", label).isGreaterThan(0);
        assertThat(v.get("recommendedVariant")).as("%s 推荐方案", label).isNotNull();

        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) v.get("outputFiles");
        assertThat(files).as("%s 输出文件", label).isNotEmpty();
        for (String f : files) {
            assertThat(Files.isRegularFile(Paths.get(f))).as("输出文件存在: %s", f).isTrue();
        }
        assertThat(files).anyMatch(f -> f.endsWith(".txt"));
        assertThat(files).anyMatch(f -> f.endsWith("方案对比报告.md"));

        // 本体一致性校验（真实环境由 Openllet 判定）
        Object consistent = v.get("ontologyConsistent");
        assertThat(consistent).as("%s 本体一致性校验结果", label).isNotNull();
        assertThat(consistent).as("%s 本体一致性应通过", label).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<String> reports = (List<String>) v.get("ontologyReports");
        assertThat(reports).as("%s 本体校验报告", label).isNotEmpty();
        System.out.println("   本体校验：" + reports);
    }

    private ProcessInstanceResult run(int caseIndex) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("caseIndex", caseIndex);
        return zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(vars)
                .withResult()
                .requestTimeout(Duration.ofSeconds(150))
                .send().join();
    }

    private static void print(String label, Map<String, Object> v) {
        System.out.println("\n===== " + label + " =====");
        System.out.println("机型：" + v.get("model") + " / 升降机：" + v.get("elevatorType")
                + " / 区域：" + v.get("region") + " / 连接：" + v.get("connectionType"));
        System.out.println("中段：" + v.get("middleSectionNumbers"));
        System.out.println("满足方案数：" + v.get("satisfiedCount") + " / " + v.get("variantCount")
                + "，否决：" + v.get("rejectedCount"));
        System.out.println("推荐方案：" + v.get("recommendedVariant"));
        System.out.println("本体一致：" + v.get("ontologyConsistent"));
        System.out.println("输出文件数：" + (v.get("outputFiles") instanceof List<?> l ? l.size() : 0));
    }

    private static int asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        return v == null ? 0 : Integer.parseInt(String.valueOf(v));
    }
}
